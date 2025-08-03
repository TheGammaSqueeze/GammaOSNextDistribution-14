package com.android.tools.layoutlib.create;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

public class PromoteMethodClassAdapter extends ClassVisitor {

    private final Set<String> mMethodNames;
    private static final int CLEAR_PRIVATE_MASK = ~(ACC_PRIVATE | ACC_PROTECTED);

    public PromoteMethodClassAdapter(ClassVisitor cv, Set<String> methodNames) {
        super(Main.ASM_VERSION, cv);
        mMethodNames = methodNames;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        if (mMethodNames.contains(name)) {
            if ((access & ACC_PUBLIC) == 0) {
                access = (access & CLEAR_PRIVATE_MASK) | ACC_PUBLIC;
            }
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }
}
