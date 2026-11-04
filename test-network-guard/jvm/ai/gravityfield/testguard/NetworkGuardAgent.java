package ai.gravityfield.testguard;

import java.lang.instrument.*;
import java.security.*;
import java.net.InetAddress;
import org.objectweb.asm.*;

/** Committed JVM-only agent; never packaged into the SDK. */
public final class NetworkGuardAgent {
    public static void premain(String arguments, Instrumentation instrumentation) {
        NetworkGuard.install();
        System.setSecurityManager(new SecurityManager() {
            @Override public void checkPermission(Permission permission) {}
            @Override public void checkPermission(Permission permission, Object context) {}
            @Override public void checkConnect(String host, int port) {
                if (!NetworkGuard.loopback(host)) {
                    NetworkGuard.directDenied(host, port);
                    throw new SecurityException("Test network guard denied non-loopback host: " + host);
                }
            }
            @Override public void checkConnect(String host, int port, Object context) { checkConnect(host, port); }
            @Override public void checkMulticast(InetAddress address) { checkConnect(address.getHostAddress(), -1); }
            @Override public void checkExec(String command) {
                // File.canExecute uses this hook for a permission query, not process creation.
                for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                    if (frame.getClassName().equals("java.io.File") && frame.getMethodName().equals("canExecute")) return;
                }
                throw new SecurityException("Test network guard forbids child process: " + command);
            }
        });
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (name != null && name.startsWith("ai/gravityfield/") && name.endsWith("Test")) NetworkGuard.noteTestClass(name.replace('/', '.'));
                boolean loggerClass = "ai/gravityfield/gravity_sdk/utils/Logger".equals(name);
                boolean testClass = name != null && name.startsWith("ai/gravityfield/") && name.endsWith("Test");
                if (!loggerClass && !testClass) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
                        MethodVisitor parent = super.visitMethod(access, method, desc, signature, exceptions);
                        return new MethodVisitor(Opcodes.ASM9, parent) {
                            @Override public void visitCode() {
                                super.visitCode();
                                if (loggerClass && method.equals("e") && desc.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;Z)V")) {
                                    super.visitVarInsn(Opcodes.ALOAD, 0); super.visitVarInsn(Opcodes.ALOAD, 1);
                                    super.visitVarInsn(Opcodes.ALOAD, 2); super.visitVarInsn(Opcodes.ALOAD, 3); super.visitVarInsn(Opcodes.ILOAD, 4);
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "ai/gravityfield/testguard/NetworkGuard", "beforeLogger",
                                        "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;Z)V", false);
                                }
                            }
                            @Override public void visitInsn(int opcode) {
                                if (testClass && method.equals("<init>") && opcode == Opcodes.RETURN) {
                                    super.visitLdcInsn(Type.getObjectType(name));
                                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "ai/gravityfield/testguard/NetworkGuard", "beforeTest", "(Ljava/lang/Class;)V", false);
                                }
                                super.visitInsn(opcode);
                            }
                            @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                                if (loggerClass && !NetworkGuard.audit() && method.equals("<clinit>") && opcode == Opcodes.PUTSTATIC && field.equals("errorReportingEnabled") && descriptor.equals("Z")) {
                                    super.visitInsn(Opcodes.POP); super.visitInsn(Opcodes.ICONST_0);
                                }
                                super.visitFieldInsn(opcode, owner, field, descriptor);
                            }
                        };
                    }
                }, 0);
                return writer.toByteArray();
            }
        });
    }
}
