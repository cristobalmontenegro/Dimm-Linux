import org.objectweb.asm.*;
import java.io.*;

public class PatchButtonPressed {
    static final String CLASS_PATH = "plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/formas/ActualizacionDialog.class";
    static final String TARGET_METHOD = "buttonPressed";
    static final String TARGET_DESC = "(I)V";

    public static void main(String[] args) throws Exception {
        ClassReader cr = new ClassReader(new FileInputStream(CLASS_PATH));
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                if (name.equals(TARGET_METHOD) && desc.equals(TARGET_DESC)) {
                    System.out.println("Found " + name + ", patching...");
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        boolean afterGetSelection = false;
                        boolean inInternetPath = false;
                        boolean replacementDone = false;
                        Label internetGotoTarget = null;

                        // ----- visitMethodInsn -----
                        @Override
                        public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                            if (replacementDone) { super.visitMethodInsn(op, owner, name, desc, itf); return; }

                            // Detect getSelection() — marks entry to internet/local branch
                            if (op == Opcodes.INVOKEVIRTUAL && name.equals("getSelection")) {
                                afterGetSelection = true;
                                super.visitMethodInsn(op, owner, name, desc, itf);
                                return;
                            }

                            // In internet path: suppress getShell, getDisplay, $4.<init>
                            if (inInternetPath) {
                                if (name.equals("getShell") || name.equals("getDisplay")
                                    || (op == Opcodes.INVOKESPECIAL && name.equals("<init")
                                        && owner.contains("ActualizacionDialog$4"))) {
                                    return; // suppress
                                }
                                // Replace BusyIndicator.showWhile with direct call
                                if (op == Opcodes.INVOKESTATIC
                                    && owner.equals("org/eclipse/swt/custom/BusyIndicator")
                                    && name.equals("showWhile")) {
                                    System.out.println("  Replaced BusyIndicator.showWhile -> InternetDownloader.downloadAndInstall");
                                    super.visitVarInsn(Opcodes.ALOAD, 0);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "ec/gov/sri/dimm/principal/formas/InternetDownloader",
                                        "downloadAndInstall",
                                        "(Lec/gov/sri/dimm/principal/formas/ActualizacionDialog;)V",
                                        false);
                                    if (internetGotoTarget != null) {
                                        super.visitJumpInsn(Opcodes.GOTO, internetGotoTarget);
                                    }
                                    inInternetPath = false;
                                    replacementDone = true;
                                    return;
                                }
                                return; // suppress any other method calls in internet path
                            }

                            super.visitMethodInsn(op, owner, name, desc, itf);
                        }

                        // ----- visitJumpInsn -----
                        @Override
                        public void visitJumpInsn(int op, Label label) {
                            if (replacementDone) { super.visitJumpInsn(op, label); return; }

                            // ifeq right after getSelection → internet path follows in visitor order
                            if (afterGetSelection && op == Opcodes.IFEQ) {
                                afterGetSelection = false;
                                inInternetPath = true;
                                // Don't emit the ifeq — it's part of the original control flow
                                // but we're going to remove the entire internet path.
                                // Actually, WE NEED to emit the ifeq because the local file path
                                // still needs to be reachable via this branch.
                                // Wait — the ifeq jumps to LOCAL_PATH. If we suppress the ifeq,
                                // the local path becomes unreachable (always falls through to our code).
                                // We MUST keep the ifeq.
                                super.visitJumpInsn(op, label);
                                return;
                            }

                            // Goto at end of internet path — capture target but suppress
                            if (inInternetPath && op == Opcodes.GOTO) {
                                internetGotoTarget = label;
                                return; // suppress, will emit after our replacement
                            }

                            super.visitJumpInsn(op, label);
                        }

                        // ----- visitVarInsn -----
                        @Override
                        public void visitVarInsn(int op, int var) {
                            if (replacementDone) { super.visitVarInsn(op, var); return; }
                            // Suppress aload_0 instructions in the internet path
                            if (inInternetPath && op == Opcodes.ALOAD && var == 0) {
                                return;
                            }
                            super.visitVarInsn(op, var);
                        }

                        // ----- visitInsn -----
                        @Override
                        public void visitInsn(int op) {
                            if (replacementDone) { super.visitInsn(op); return; }
                            // Suppress dup in internet path
                            if (inInternetPath && op == Opcodes.DUP) {
                                return;
                            }
                            super.visitInsn(op);
                        }

                        // ----- visitTypeInsn -----
                        @Override
                        public void visitTypeInsn(int op, String type) {
                            if (replacementDone) { super.visitTypeInsn(op, type); return; }
                            // Suppress new $4 in internet path
                            if (inInternetPath && op == Opcodes.NEW && type.contains("ActualizacionDialog$4")) {
                                return;
                            }
                            super.visitTypeInsn(op, type);
                        }

                        // ----- visitMaxs -----
                        @Override
                        public void visitMaxs(int s, int l) {
                            super.visitMaxs(s, l);
                        }
                    };
                }
                return mv;
            }
        }, 0);

        byte[] modified = cw.toByteArray();
        try (FileOutputStream fos = new FileOutputStream(CLASS_PATH)) {
            fos.write(modified);
        }
        System.out.println("Patched: " + CLASS_PATH);
    }
}
