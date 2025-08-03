/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.layoutlib.create;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static com.android.tools.layoutlib.create.DelegateMethodAdapter.DELEGATE_SUFFIX;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

class StaticInitMethodAdapter extends MethodVisitor {
    /** static initializer delegate name. */
    private static final String DELEGATE_STATIC_NAME = "staticInit";

    /** The internal class name (e.g. <code>com/android/SomeClass$InnerClass</code>.) */
    private final String mClassName;

    /** Logger object. */
    private final Log mLog;

    /** The method writer to copy of the original method. */
    private final MethodVisitor mRenamedMethodWriter;

    /** The method writer to generate the original static { SomeClass_Delegate.staticInit} block */
    private final MethodVisitor mOriginalMethodWriter;

    /** Array used to capture the first line number information from the original method
     *  and duplicate it in the delegate. */
    private Object[] mDelegateLineNumber;

    public StaticInitMethodAdapter(Log log, MethodVisitor renamedMethodWriter,
            MethodVisitor originalMethodWriter,
            String className) {
        super(Main.ASM_VERSION);
        mLog = log;
        mRenamedMethodWriter = renamedMethodWriter;
        mOriginalMethodWriter = originalMethodWriter;
        mClassName = className;
    }



    /**
     * Generate the new code for the method.
     *
     * This will be a call to className_Delegate#staticInit
     */
    private void generateDelegateCode() {
        AnnotationVisitor aw = mOriginalMethodWriter.visitAnnotation(
                Type.getObjectType(Type.getInternalName(LayoutlibDelegate.class)).toString(),
                true); // visible at runtime
        if (aw != null) {
            aw.visitEnd();
        }

        mOriginalMethodWriter.visitCode();

        if (mDelegateLineNumber != null) {
            Object[] p = mDelegateLineNumber;
            mOriginalMethodWriter.visitLineNumber((Integer) p[0], (Label) p[1]);
        }

        String delegateClassName = mClassName + DELEGATE_SUFFIX;
        delegateClassName = delegateClassName.replace('$', '_');

        // generate the SomeClass_Delegate.staticInit call.
        mOriginalMethodWriter.visitMethodInsn(Opcodes.INVOKESTATIC, delegateClassName,
                DELEGATE_STATIC_NAME,
                Type.getMethodDescriptor(Type.VOID_TYPE),
                false);
        mOriginalMethodWriter.visitInsn(Type.VOID_TYPE.getOpcode(Opcodes.IRETURN));
        mOriginalMethodWriter.visitMaxs(0, 0);

        mOriginalMethodWriter.visitEnd();

        mLog.debug("static initializer call for class %s delegated to %s#%s",
                mClassName,
                delegateClassName, DELEGATE_STATIC_NAME);
    }

    /* Pass down to visitor writer. In this implementation, either do nothing. */
    @Override
    public void visitCode() {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitCode();
        }
    }

    /*
     * visitMaxs is called just before visitEnd if there was any code to rewrite.
     */
    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitMaxs(maxStack, maxLocals);
        }
    }

    /** End of visiting. Generate the delegating code. */
    @Override
    public void visitEnd() {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitEnd();
        }
        generateDelegateCode();
    }

    /* Writes all annotation from the original method. */
    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (mRenamedMethodWriter != null) {
            return mRenamedMethodWriter.visitAnnotation(desc, visible);
        } else {
            return null;
        }
    }

    /* Writes all annotation default values from the original method. */
    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        if (mRenamedMethodWriter != null) {
            return mRenamedMethodWriter.visitAnnotationDefault();
        } else {
            return null;
        }
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc,
            boolean visible) {
        if (mRenamedMethodWriter != null) {
            return mRenamedMethodWriter.visitParameterAnnotation(parameter, desc, visible);
        } else {
            return null;
        }
    }

    /* Writes all attributes from the original method. */
    @Override
    public void visitAttribute(Attribute attr) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitAttribute(attr);
        }
    }

    /*
     * Only writes the first line number present in the original code so that source
     * viewers can direct to the correct method, even if the content doesn't match.
     */
    @Override
    public void visitLineNumber(int line, Label start) {
        // Capture the first line values for the new delegate method
        if (mDelegateLineNumber == null) {
            mDelegateLineNumber = new Object[] { line, start };
        }
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitLineNumber(line, start);
        }
    }

    @Override
    public void visitInsn(int opcode) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitInsn(opcode);
        }
    }

    @Override
    public void visitLabel(Label label) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitLabel(label);
        }
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitTryCatchBlock(start, end, handler, type);
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String desc) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitFieldInsn(opcode, owner, name, desc);
        }
    }

    @Override
    public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitFrame(type, nLocal, local, nStack, stack);
        }
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitIincInsn(var, increment);
        }
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitIntInsn(opcode, operand);
        }
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitJumpInsn(opcode, label);
        }
    }

    @Override
    public void visitLdcInsn(Object cst) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitLdcInsn(cst);
        }
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature,
            Label start, Label end, int index) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitLocalVariable(name, desc, signature, start, end, index);
        }
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitLookupSwitchInsn(dflt, keys, labels);
        }
    }

    @Override
    public void visitMultiANewArrayInsn(String desc, int dims) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitMultiANewArrayInsn(desc, dims);
        }
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label[] labels) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitTableSwitchInsn(min, max, dflt, labels);
        }
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitTypeInsn(opcode, type);
        }
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        if (mRenamedMethodWriter != null) {
            mRenamedMethodWriter.visitVarInsn(opcode, var);
        }
    }
}
