import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;

public class GenericBrowserPatcher {
    static final String FORMATTER_OWNER = "ec/gob/sri/dimm/ats/ui/editores/TalonFormatter";
    static final String FORMATTER_PATH = "ec/gob/sri/dimm/ats/ui/editores/TalonFormatter.class";
    static final String BROWSER = "org/eclipse/swt/browser/Browser";
    static final String STYLED_TEXT = "org/eclipse/swt/custom/StyledText";
    static final String BROWSER_DESC = "L" + BROWSER + ";";
    static final String STYLED_TEXT_DESC = "L" + STYLED_TEXT + ";";

    public static void main(String[] args) throws Exception {
        boolean dryRun = false;
        String[] targets;
        if (args.length == 0) {
            File pluginsDir = new File("plugins");
            targets = pluginsDir.list((d, n) -> n.endsWith(".jar"));
            if (targets == null || targets.length == 0) {
                System.out.println("  No JARs found in plugins/");
                return;
            }
        } else if ("--dry-run".equals(args[0])) {
            dryRun = true;
            targets = Arrays.copyOfRange(args, 1, args.length);
        } else {
            targets = args;
        }
        for (String t : targets) {
            File f = new File(t);
            if (!f.isAbsolute()) f = new File("plugins", t);
            if (!f.exists()) f = new File(t);
            if (!f.exists() || !f.getName().endsWith(".jar")) continue;
            byte[] data = readFile(f);
            if (!hasBrowserRef(data)) continue;
            System.out.println("  Scanning " + f.getName());
            byte[] patched = patchJar(data, f);
            if (patched != null) {
                if (dryRun) {
                    System.out.println("  Would patch: " + f.getName());
                } else {
                    writeFile(f, patched);
                }
            }
        }
    }

    static boolean hasBrowserRef(byte[] data) {
        return new String(data, java.nio.charset.StandardCharsets.ISO_8859_1)
            .contains("org/eclipse/swt/browser/Browser");
    }

    static byte[] patchJar(byte[] jarData, File jarFile) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        boolean patched = false;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jarData))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                String name = ze.getName();
                byte[] data = readAll(zis);
                if (name.equals(FORMATTER_PATH)) continue;
                if (name.endsWith(".class") && hasBrowserRef(data)) {
                    byte[] nd = patchClass(data);
                    if (nd != data) {
                        System.out.println("    Patched: " + name.replace('/', '.'));
                        patched = true;
                    }
                    entries.put(name, nd);
                } else {
                    entries.put(name, data);
                }
            }
        }
        if (!patched) return null;
        File ff = new File(jarFile.getParentFile().getParentFile(), FORMATTER_PATH);
        if (!ff.exists()) ff = new File(FORMATTER_PATH);
        if (ff.exists()) {
            entries.put(FORMATTER_PATH, readFile(ff));
            System.out.println("    Injected TalonFormatter.class");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        System.out.println("  Patched: " + jarFile.getName());
        return bos.toByteArray();
    }

    static byte[] patchClass(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassPatcher(cw), ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }

    // --- Class-level visitor ---
    static class ClassPatcher extends ClassVisitor {
        String className;
        boolean hasStringRfc;
        boolean hasFileRfc;

        ClassPatcher(ClassVisitor cv) {
            super(Opcodes.ASM9, cv);
        }

        @Override
        public void visit(int v, int a, String name, String sig, String sup, String[] itfs) {
            this.className = name;
            super.visit(v, a, name, sig, sup, itfs);
        }

        @Override
        public void visitEnd() {
            if (!hasStringRfc) {
                MethodVisitor mv = super.visitMethod(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    "readFileContent",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitTypeInsn(Opcodes.NEW, "java/io/File");
                mv.visitInsn(Opcodes.DUP);
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "java/io/File", "<init>", "(Ljava/lang/String;)V", false);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    FORMATTER_OWNER,
                    "readFileContent", "(Ljava/io/File;)Ljava/lang/String;", false);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
            if (!hasFileRfc) {
                MethodVisitor mv = super.visitMethod(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    "readFileContent",
                    "(Ljava/io/File;)Ljava/lang/String;",
                    null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    FORMATTER_OWNER,
                    "readFileContent", "(Ljava/io/File;)Ljava/lang/String;", false);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }
        }

        @Override
        public MethodVisitor visitMethod(int a, String name, String desc, String sig, String[] ex) {
            if ("readFileContent".equals(name)) {
                if ("(Ljava/lang/String;)Ljava/lang/String;".equals(desc)) hasStringRfc = true;
                if ("(Ljava/io/File;)Ljava/lang/String;".equals(desc)) hasFileRfc = true;
            }
            MethodVisitor mv = super.visitMethod(a, name, desc, sig, ex);
            if ("setFocus".equals(name) && "()Z".equals(desc)) {
                return new SetFocusPatcher(mv);
            }
            return new MethodPatcher(mv, this);
        }

        @Override
        public FieldVisitor visitField(int a, String name, String desc, String sig, Object v) {
            if (desc.equals(BROWSER_DESC)) {
                return super.visitField(a, name, STYLED_TEXT_DESC, sig, v);
            }
            return super.visitField(a, name, desc, sig, v);
        }
    }

    // --- Method-level visitor for Browser -> StyledText replacement ---
    static class MethodPatcher extends MethodVisitor {
        final ClassPatcher owner;
        boolean lastGap;
        boolean skipPop;
        boolean afterInit;
        int maxVar = -1;

        MethodPatcher(MethodVisitor mv, ClassPatcher owner) {
            super(Opcodes.ASM9, mv);
            this.owner = owner;
        }

        void resetGap() { lastGap = false; }

        @Override
        public void visitVarInsn(int op, int v) {
            resetGap();
            if (v > maxVar) maxVar = v;
            super.visitVarInsn(op, v);
        }

        @Override
        public void visitTypeInsn(int op, String t) {
            resetGap();
            if (op == Opcodes.NEW && t.equals(BROWSER)) {
                super.visitTypeInsn(Opcodes.NEW, STYLED_TEXT);
                return;
            }
            if (op == Opcodes.CHECKCAST && t.equals(BROWSER)) {
                super.visitTypeInsn(Opcodes.CHECKCAST, STYLED_TEXT);
                return;
            }
            if (op == Opcodes.INSTANCEOF && t.equals(BROWSER)) {
                super.visitTypeInsn(Opcodes.INSTANCEOF, STYLED_TEXT);
                return;
            }
            super.visitTypeInsn(op, t);
        }

        @Override
        public void visitIntInsn(int op, int v) {
            resetGap();
            if (op == Opcodes.SIPUSH && v == 2048) {
                super.visitIntInsn(Opcodes.SIPUSH, 792);
                return;
            }
            super.visitIntInsn(op, v);
        }

        @Override
        public void visitFieldInsn(int op, String o, String n, String d) {
            resetGap();
            if (d.equals(BROWSER_DESC)) {
                super.visitFieldInsn(op, o, n, STYLED_TEXT_DESC);
                return;
            }
            super.visitFieldInsn(op, o, n, d);
        }

        @Override
        public void visitMethodInsn(int op, String o, String n, String d, boolean itf) {
            resetGap();
            if (op == Opcodes.INVOKESPECIAL && "<init>".equals(n)
                && (o.equals(BROWSER) || o.equals(STYLED_TEXT))) {
                super.visitMethodInsn(Opcodes.INVOKESPECIAL, STYLED_TEXT, "<init>", d, false);
                afterInit = true;
                return;
            }
            if (op == Opcodes.INVOKEVIRTUAL && o.equals("java/io/File")
                && "getAbsolutePath".equals(n)) {
                lastGap = true;
                return;
            }
            if (op == Opcodes.INVOKEVIRTUAL && o.equals(BROWSER) && "setUrl".equals(n)) {
                if (lastGap) {
                    // Stack: [..., stRef, fileObj]  (File.getAbsolutePath was removed)
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        owner.className, "readFileContent",
                        "(Ljava/io/File;)Ljava/lang/String;", false);
                } else {
                    // Stack: [..., stRef, stringPath]
                    int tv = ++maxVar;
                    super.visitVarInsn(Opcodes.ASTORE, tv);
                    super.visitTypeInsn(Opcodes.NEW, "java/io/File");
                    super.visitInsn(Opcodes.DUP);
                    super.visitVarInsn(Opcodes.ALOAD, tv);
                    super.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        "java/io/File", "<init>", "(Ljava/lang/String;)V", false);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                        owner.className, "readFileContent",
                        "(Ljava/io/File;)Ljava/lang/String;", false);
                }
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    STYLED_TEXT, "setText", "(Ljava/lang/String;)V", false);
                skipPop = true;
                lastGap = false;
                return;
            }
            if (op == Opcodes.INVOKEVIRTUAL && o.equals(BROWSER)) {
                if ("setFocus".equals(n)) {
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STYLED_TEXT, n, d, false);
                    return;
                }
                if ("getUrl".equals(n)) {
                    super.visitLdcInsn("");
                    return;
                }
                if (n.startsWith("add") && n.endsWith("Listener")) {
                    return;
                }
                if (n.equals("back") || n.equals("forward")
                    || n.equals("refresh") || n.equals("stop")) {
                    return;
                }
                if (n.startsWith("is") && n.endsWith("Enabled")) {
                    super.visitInsn(Opcodes.ICONST_0);
                    return;
                }
                return;
            }
            super.visitMethodInsn(op, o, n, d, itf);
        }

        @Override
        public void visitInsn(int op) {
            if (skipPop && op == Opcodes.POP) { skipPop = false; return; }
            if (afterInit && (op == Opcodes.DUP || op == Opcodes.DUP_X1
                || op == Opcodes.DUP_X2 || op == Opcodes.SWAP)) {
                afterInit = false;
                return;
            }
            resetGap();
            super.visitInsn(op);
        }

        @Override
        public void visitJumpInsn(int op, Label l) { resetGap(); super.visitJumpInsn(op, l); }

        @Override
        public void visitLabel(Label l) { super.visitLabel(l); }

        @Override
        public void visitLdcInsn(Object v) { resetGap(); super.visitLdcInsn(v); }
    }

    // --- SetFocus method patcher: redirects Browser.setFocus -> StyledText.setFocus ---
    static class SetFocusPatcher extends MethodVisitor {
        SetFocusPatcher(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitMethodInsn(int op, String o, String n, String d, boolean itf) {
            if (op == Opcodes.INVOKEVIRTUAL && o.equals(BROWSER) && "setFocus".equals(n)) {
                super.visitMethodInsn(op, STYLED_TEXT, n, d, false);
            } else {
                super.visitMethodInsn(op, o, n, d, itf);
            }
        }

        @Override
        public void visitFieldInsn(int op, String o, String n, String d) {
            if (d.equals(BROWSER_DESC)) {
                super.visitFieldInsn(op, o, n, STYLED_TEXT_DESC);
            } else {
                super.visitFieldInsn(op, o, n, d);
            }
        }
    }

    // --- I/O utilities ---
    static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    static byte[] readFile(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            return readAll(fis);
        }
    }

    static void writeFile(File f, byte[] data) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
        }
    }
}
