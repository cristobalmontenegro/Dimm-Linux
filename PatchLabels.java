import org.objectweb.asm.*;
import java.io.*;

public class PatchLabels {
    static final String CLASS_PATH = "plugins/ec.gov.sri.dimm.principal_1.0.1/ec/gov/sri/dimm/principal/formas/ActualizacionDialog.class";
    static final String OWNER = "ec/gov/sri/dimm/principal/formas/ActualizacionDialog";
    static final String BUTTON_DESC = "Lorg/eclipse/swt/widgets/Button;";

    public static void main(String[] args) throws Exception {
        ClassReader cr = new ClassReader(new FileInputStream(CLASS_PATH));
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                if (name.equals("createDialogArea")
                    && desc.equals("(Lorg/eclipse/swt/widgets/Composite;)Lorg/eclipse/swt/widgets/Control;")) {
                    System.out.println("Found " + name + ", patching labels...");
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        boolean patched = false;

                        @Override
                        public void visitInsn(int op) {
                            // Before ARETURN, inject conditional button text
                            if (op == Opcodes.ARETURN && !patched) {
                                patched = true;
                                System.out.println("  Injecting conditional button text");
                                Label skipLabel = new Label();

                                // if (nuevaExtension) {
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(Opcodes.GETFIELD, OWNER, "nuevaExtension", "Z");
                                super.visitJumpInsn(Opcodes.IFEQ, skipLabel);

                                //   actualizaciButton.setText("Instalación por Internet");
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(Opcodes.GETFIELD, OWNER, "actualizaciButton", BUTTON_DESC);
                                super.visitLdcInsn("Instalaci\u00f3n por Internet");
                                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                    "org/eclipse/swt/widgets/Button", "setText",
                                    "(Ljava/lang/String;)V", false);

                                //   button_local.setText("Instalación por archivo");
                                super.visitVarInsn(Opcodes.ALOAD, 0);
                                super.visitFieldInsn(Opcodes.GETFIELD, OWNER, "button_local", BUTTON_DESC);
                                super.visitLdcInsn("Instalaci\u00f3n por archivo");
                                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                                    "org/eclipse/swt/widgets/Button", "setText",
                                    "(Ljava/lang/String;)V", false);

                                // }
                                super.visitLabel(skipLabel);
                            }
                            // Now emit the ARETURN
                            super.visitInsn(op);
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
        System.out.println("Patched labels in: " + CLASS_PATH);
    }
}
