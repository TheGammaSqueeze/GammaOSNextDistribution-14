package com.android.tools.layoutlib.create;

import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;


public class DelegateToNativeAdapter extends ClassVisitor {
    private final Log mLog;
    private final ClassWriter mDelegateWriter;
    private final String mDelegateName;
    private final String mClassName;
    private final Set<String> mDelegateMethods;

    public DelegateToNativeAdapter(Log logger, ClassVisitor cv, String className,
            Map<String, ClassWriter> delegates, Set<String> delegateMethods) {
        super(Main.ASM_VERSION, cv);
        mLog = logger;
        mDelegateWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        mClassName = className;
        mDelegateName = (className + "_NativeDelegate").replace('$', '_');
        mDelegateMethods = delegateMethods;
        delegates.put(mDelegateName, mDelegateWriter);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName,
            String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);

        mDelegateWriter.visit(version, Opcodes.ACC_PUBLIC, mDelegateName, null, "java/lang/Object",
                null);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        boolean isStaticMethod = (access & Opcodes.ACC_STATIC) != 0;
        boolean isNative = (access & Opcodes.ACC_NATIVE) != 0;

        if (isNative) {
            mDelegateWriter.visitMethod(access, name + "_Original", desc, signature, exceptions);
            generateDelegateMethod(name, desc, signature, exceptions);

            if (mDelegateMethods == null || !mDelegateMethods.contains(name)) {
                // Remove native flag
                access = access & ~Opcodes.ACC_NATIVE;
                MethodVisitor mwDelegate =
                        super.visitMethod(access, name, desc, signature, exceptions);

                DelegateMethodAdapter a =
                        new DelegateMethodAdapter(mLog, null, mwDelegate, mClassName, mDelegateName,
                                name, desc, isStaticMethod, false);

                // A native has no code to visit, so we need to generate it directly.
                a.generateDelegateCode();

                return mwDelegate;
            }
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    private void generateDelegateMethod(String name, String desc, String signature,
            String[] exceptions) {
        MethodVisitor delegateVisitor =
                mDelegateWriter.visitMethod(Opcodes.ACC_STATIC, name, desc,
                        signature,
                        exceptions);
        AnnotationVisitor aw = delegateVisitor.visitAnnotation(
                Type.getObjectType(Type.getInternalName(LayoutlibDelegate.class)).toString(),
                true); // visible at runtime
        if (aw != null) {
            aw.visitEnd();
        }
        delegateVisitor.visitCode();
        int maxStack = 0;
        int maxLocals = 0;
        Type[] argTypes = Type.getArgumentTypes(desc);
        for (Type t : argTypes) {
            int size = t.getSize();
            delegateVisitor.visitVarInsn(t.getOpcode(Opcodes.ILOAD), maxLocals);
            maxLocals += size;
            maxStack += size;
        }
        delegateVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, mDelegateName,
                name + "_Original", desc, false);

        Type returnType = Type.getReturnType(desc);
        delegateVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));

        delegateVisitor.visitMaxs(maxStack, maxLocals);
        delegateVisitor.visitEnd();
    }
}
