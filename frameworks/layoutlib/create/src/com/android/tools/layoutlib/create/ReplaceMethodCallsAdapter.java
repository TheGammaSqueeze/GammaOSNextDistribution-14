/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.layoutlib.create.ICreateInfo.MethodInformation;
import com.android.tools.layoutlib.create.ICreateInfo.MethodReplacer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Set;

/**
 * Replaces calls to certain methods that do not exist in the Desktop VM. Useful for methods in the
 * "java" package.
 */
public class ReplaceMethodCallsAdapter extends ClassVisitor {

    private Set<MethodReplacer> mMethodReplacers;
    private final String mOriginalClassName;

    public ReplaceMethodCallsAdapter(Set<MethodReplacer> methodReplacers, ClassVisitor cv, String originalClassName) {
        super(Main.ASM_VERSION, cv);
        mMethodReplacers = methodReplacers;
        mOriginalClassName = originalClassName;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
        return new MyMethodVisitor(super.visitMethod(access, name, desc, signature, exceptions));
    }

    private class MyMethodVisitor extends MethodVisitor {

        public MyMethodVisitor(MethodVisitor mv) {
            super(Main.ASM_VERSION, mv);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {
            for (MethodReplacer replacer : mMethodReplacers) {
                if (replacer.isNeeded(owner, name, desc, mOriginalClassName)) {
                    MethodInformation mi = new MethodInformation(opcode, owner, name, desc);
                    replacer.replace(mi);
                    opcode = mi.opcode;
                    owner = mi.owner;
                    name = mi.name;
                    desc = mi.desc;
                    break;
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }

}
