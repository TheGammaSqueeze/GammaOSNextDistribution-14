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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Modifier;

/**
 * Renames the static initializer to a public deferredStaticInitializer method.
 */
public class DeferStaticInitializerClassAdapter extends ClassVisitor {

    public DeferStaticInitializerClassAdapter(ClassVisitor cv) {
        super(Main.ASM_VERSION, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
            String[] exceptions) {
       if (name.equals("<clinit>")) {
           name = "deferredStaticInitializer";
           access |= Modifier.PUBLIC;
        }
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
            Object value) {
        // Java 9 does not allow static final field to be modified outside of <clinit>.
        // So if a field is static, it has to be non-final.
        if ((access & Opcodes.ACC_STATIC) != 0 ) {
            access = access & ~Opcodes.ACC_FINAL;
        }
        return super.visitField(access, name, desc, signature, value);
    }
}
