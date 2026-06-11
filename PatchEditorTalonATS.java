import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;

public class PatchEditorTalonATS {
    static final String CLASS_IN_JAR = "ec/gob/sri/dimm/ats/ui/editores/EditorTalonATS.class";
    static final String INNER_CLASS_IN_JAR = "ec/gob/sri/dimm/ats/ui/editores/EditorTalonATS$1.class";
    static final String FORMATTER_CLASS = "ec/gob/sri/dimm/ats/ui/editores/TalonFormatter.class";
    static final String FORMATTER_PATH = "ec/gob/sri/dimm/ats/ui/editores/TalonFormatter.class";
    static final String FORMATTER_OWNER = "ec/gob/sri/dimm/ats/ui/editores/TalonFormatter";
    static final String BROWSER = "org/eclipse/swt/browser/Browser";
    static final String STYLED_TEXT = "org/eclipse/swt/custom/StyledText";
    static final String FILE = "java/io/File";

    public static void main(String[] args) throws Exception {
        String path = args[0];
        if (path.endsWith(".jar")) {
            patchJar(path);
        } else if (path.endsWith(".class")) {
            File f = new File(path);
            byte[] data = readFile(f);
            if (f.getName().contains("EditorTalonATS$1")) {
                writeFile(f, patchInnerClass(data));
                System.out.println("Patched " + f.getName());
            } else {
                writeFile(f, patchMainClass(data));
                System.out.println("Patched " + f.getName());
            }
        }
    }

    static void patchJar(String jarPath) throws Exception {
        File jarFile = new File(jarPath);
        byte[] jarData = readFile(jarFile);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jarData));
        ZipOutputStream zos = new ZipOutputStream(bout);
        ZipEntry entry;
        boolean patched = false;
        while ((entry = zis.getNextEntry()) != null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = zis.read(buf)) > 0) baos.write(buf, 0, n);
            byte[] data = baos.toByteArray();
            String name = entry.getName();
            if (name.equals(FORMATTER_PATH)) {
                continue; // skip old TalonFormatter, will inject fresh below
            } else if (name.equals(CLASS_IN_JAR)) {
                if (isAlreadyPatched(data)) {
                    System.out.println("Already patched, skipping");
                } else {
                    data = patchMainClass(data);
                    patched = true;
                    System.out.println("Patched EditorTalonATS");
                }
            } else if (name.equals(INNER_CLASS_IN_JAR)) {
                data = patchInnerClass(data);
                patched = true;
                System.out.println("Patched EditorTalonATS$1");
            }
            zos.putNextEntry(new ZipEntry(name));
            zos.write(data);
            zos.closeEntry();
        }
        // Always inject TalonFormatter.class (fresh source)
        File formatterFile = new File(new File(jarPath).getParentFile().getParentFile(),
            FORMATTER_PATH);
        if (!formatterFile.exists()) {
            formatterFile = new File(FORMATTER_PATH);
        }
        if (formatterFile.exists()) {
            byte[] formatterData = readFile(formatterFile);
            zos.putNextEntry(new ZipEntry(FORMATTER_PATH));
            zos.write(formatterData);
            zos.closeEntry();
            patched = true;
            System.out.println("Injected TalonFormatter.class");
        } else {
            System.out.println("TalonFormatter.class not found at " + formatterFile.getAbsolutePath());
        }
        zis.close();
        zos.close();
        if (patched) {
            writeFile(jarFile, bout.toByteArray());
        }
    }

    static boolean isAlreadyPatched(byte[] data) {
        return new String(data).contains("setMonospaceFont");
    }

    static byte[] patchMainClass(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            boolean hasReadFileContent = false;

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
                if (name.equals("brwTalonResumen")) {
                    return super.visitField(access, name, "L" + STYLED_TEXT + ";", sig, value);
                }
                return super.visitField(access, name, desc, sig, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                              String signature, String[] exceptions) {
                if ("readFileContent".equals(name)
                    && "(Ljava/io/File;)Ljava/lang/String;".equals(desc)) {
                    hasReadFileContent = true;
                }
                if ("access$0".equals(name)) {
                    String newDesc = "(Lec/gob/sri/dimm/ats/ui/editores/EditorTalonATS;)L" + STYLED_TEXT + ";";
                    MethodVisitor mv = super.visitMethod(access, name, newDesc, signature, exceptions);
                    return new Access0Patcher(mv);
                }
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if ("createPartControl".equals(name)) {
                    return new CreatePartControlPatcher(mv);
                }
                if ("setFocus".equals(name)) {
                    return new SetFocusPatcher(mv);
                }
                return mv;
            }

            @Override
            public void visitEnd() {
                if (!hasReadFileContent) {
                    MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        "readFileContent",
                        "(Ljava/io/File;)Ljava/lang/String;",
                        null, null);
                    mv.visitCode();
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, FORMATTER_OWNER,
                        "readFileContent", "(Ljava/io/File;)Ljava/lang/String;", false);
                    mv.visitInsn(Opcodes.ARETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }
            }
        }, ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }

    static byte[] patchInnerClass(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if ("widgetSelected".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode == Opcodes.RETURN) {
                                super.visitInsn(opcode);
                            }
                        }
                        @Override
                        public void visitVarInsn(int opcode, int var) { }
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String desc) { }
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) { }
                        @Override
                        public void visitLdcInsn(Object value) { }
                        @Override
                        public void visitJumpInsn(int opcode, Label label) { }
                        @Override
                        public void visitLabel(Label label) { }
                    };
                }
                return mv;
            }
        }, ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }

    static class CreatePartControlPatcher extends MethodVisitor {
        boolean skipPop = false;

        CreatePartControlPatcher(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && type.equals(BROWSER)) {
                super.visitTypeInsn(Opcodes.NEW, STYLED_TEXT);
                return;
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            if (opcode == Opcodes.SIPUSH && operand == 2048) {
                super.visitIntInsn(Opcodes.SIPUSH, 776);
                return;
            }
            super.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if ("brwTalonResumen".equals(name) && (desc.contains("Browser") || desc.contains("StyledText"))) {
                if (opcode == Opcodes.PUTFIELD) {
                    mv.visitInsn(Opcodes.DUP);
                    super.visitFieldInsn(opcode, owner, name, "L" + STYLED_TEXT + ";");
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, FORMATTER_OWNER,
                        "setMonospaceFont", "(Ljava/lang/Object;)V", false);
                    return;
                }
                super.visitFieldInsn(opcode, owner, name, "L" + STYLED_TEXT + ";");
                return;
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (opcode == Opcodes.INVOKESPECIAL && owner.equals(BROWSER) && "<init>".equals(name)) {
                super.visitMethodInsn(Opcodes.INVOKESPECIAL, STYLED_TEXT, "<init>", desc, false);
                return;
            }
            if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals("java/io/File") && name.equals("getAbsolutePath")) {
                return;
            }
            if (opcode == Opcodes.INVOKEVIRTUAL && owner.equals(BROWSER) && name.equals("setUrl")) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC,
                    "ec/gob/sri/dimm/ats/ui/editores/EditorTalonATS",
                    "readFileContent", "(Ljava/io/File;)Ljava/lang/String;", false);
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STYLED_TEXT,
                    "setText", "(Ljava/lang/String;)V", false);
                skipPop = true;
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        public void visitInsn(int opcode) {
            if (skipPop && opcode == Opcodes.POP) {
                skipPop = false;
                return;
            }
            super.visitInsn(opcode);
        }
    }

    static class SetFocusPatcher extends MethodVisitor {
        SetFocusPatcher(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitVarInsn(int opcode, int var) { }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) { }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) { }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.RETURN) {
                mv.visitVarInsn(Opcodes.ALOAD, 0);
                mv.visitFieldInsn(Opcodes.GETFIELD,
                    "ec/gob/sri/dimm/ats/ui/editores/EditorTalonATS",
                    "brwTalonResumen", "L" + STYLED_TEXT + ";");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, STYLED_TEXT,
                    "setFocus", "()Z", false);
                mv.visitInsn(Opcodes.POP);
                super.visitInsn(opcode);
            }
        }
    }

    static class Access0Patcher extends MethodVisitor {
        Access0Patcher(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.GETFIELD && name.equals("brwTalonResumen")) {
                super.visitFieldInsn(opcode, owner, name, "L" + STYLED_TEXT + ";");
            } else {
                super.visitFieldInsn(opcode, owner, name, desc);
            }
        }
    }

    static byte[] readFile(File f) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(f);
        byte[] buf = new byte[8192];
        int n;
        while ((n = fis.read(buf)) > 0) baos.write(buf, 0, n);
        fis.close();
        return baos.toByteArray();
    }

    static void writeFile(File f, byte[] data) throws IOException {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(data);
        fos.close();
    }
}
