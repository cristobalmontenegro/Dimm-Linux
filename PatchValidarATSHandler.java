import java.io.*;
import java.util.zip.*;
import org.objectweb.asm.*;

public class PatchValidarATSHandler {
    static final String CLASS_IN_JAR = "ec/gob/sri/dimm/ats/ui/menu/ValidarATSHandler.class";

    public static void main(String[] args) throws Exception {
        String path = args[0];
        if (path.endsWith(".jar")) {
            patchJar(path);
        } else {
            File f = new File(path);
            byte[] patched = doPatch(readFile(f));
            writeFile(f, patched);
            System.out.println("Patched " + path);
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
            if (entry.getName().equals(CLASS_IN_JAR)) {
                if (isAlreadyPatched(data)) {
                    System.out.println("Already patched, skipping");
                } else {
                    data = doPatch(data);
                    patched = true;
                    System.out.println("Patched " + CLASS_IN_JAR + " in " + jarPath);
                }
            }
            zos.putNextEntry(new ZipEntry(entry.getName()));
            zos.write(data);
            zos.closeEntry();
        }
        zis.close();
        zos.close();
        if (patched) {
            writeFile(jarFile, bout.toByteArray());
        }
    }

    static boolean isAlreadyPatched(byte[] data) {
        // Look for DUP; ASTORE 12 (0x59 0x3A 0x0C) which only exists in patched version
        for (int i = 0; i < data.length - 2; i++) {
            if (data[i] == (byte)0x59 && data[i+1] == (byte)0x3A && data[i+2] == (byte)0x0C)
                return true;
        }
        return false;
    }

    static byte[] doPatch(byte[] classBytes) throws Exception {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                if ("execute".equals(name) && desc.startsWith("(Lorg/eclipse/core/commands/ExecutionEvent;)")) {
                    return new Patcher(mv);
                }
                return mv;
            }
        }, ClassReader.SKIP_FRAMES);
        return cw.toByteArray();
    }

    static class Patcher extends MethodVisitor {
        int state = 0;
        int windowVar = 12;
        boolean hasPendingAload1 = false;

        Patcher(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            if (opcode == Opcodes.ALOAD && var == 1) {
                if (state == 0) {
                    hasPendingAload1 = true;
                    super.visitVarInsn(opcode, var);
                } else if (state == 1) {
                    hasPendingAload1 = true;
                } else {
                    hasPendingAload1 = false;
                    super.visitVarInsn(opcode, var);
                }
            } else {
                hasPendingAload1 = false;
                super.visitVarInsn(opcode, var);
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                     String desc, boolean itf) {
            boolean isGetWindow = (opcode == Opcodes.INVOKESTATIC
                    && owner.equals("org/eclipse/ui/handlers/HandlerUtil")
                    && name.equals("getActiveWorkbenchWindow")
                    && desc.equals("(Lorg/eclipse/core/commands/ExecutionEvent;)Lorg/eclipse/ui/IWorkbenchWindow;"));

            if (isGetWindow && state == 0) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                super.visitInsn(Opcodes.DUP);
                super.visitVarInsn(Opcodes.ASTORE, windowVar);
                state = 1;
                hasPendingAload1 = false;
            } else if (isGetWindow && state == 1) {
                hasPendingAload1 = false;
                super.visitVarInsn(Opcodes.ALOAD, windowVar);
            } else {
                hasPendingAload1 = false;
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }

        @Override public void visitInsn(int opcode) { hasPendingAload1 = false; super.visitInsn(opcode); }
        @Override public void visitJumpInsn(int opcode, Label label) { hasPendingAload1 = false; super.visitJumpInsn(opcode, label); }
        @Override public void visitLdcInsn(Object value) { hasPendingAload1 = false; super.visitLdcInsn(value); }
        @Override public void visitTypeInsn(int opcode, String type) { hasPendingAload1 = false; super.visitTypeInsn(opcode, type); }
        @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) { hasPendingAload1 = false; super.visitFieldInsn(opcode, owner, name, desc); }
        @Override public void visitIntInsn(int opcode, int operand) { hasPendingAload1 = false; super.visitIntInsn(opcode, operand); }
        @Override public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) { hasPendingAload1 = false; super.visitInvokeDynamicInsn(name, desc, bsm, bsmArgs); }
        @Override public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) { hasPendingAload1 = false; super.visitFrame(type, nLocal, local, nStack, stack); }
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
