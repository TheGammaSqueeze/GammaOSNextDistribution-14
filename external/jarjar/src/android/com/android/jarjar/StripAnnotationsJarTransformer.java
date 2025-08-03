/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.jarjar;

import com.tonicsystems.jarjar.util.JarTransformer;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** A transformer that strips annotations from all classes based on custom rules. */
public final class StripAnnotationsJarTransformer extends JarTransformer {

  private static int ASM_VERSION = Opcodes.ASM9;

  private final List<String> stripAnnotationList;

  public StripAnnotationsJarTransformer(List<StripAnnotation> stripAnnotationList) {
    this.stripAnnotationList = getAnnotationList(stripAnnotationList);
  }

  private static List<String> getAnnotationList(List<StripAnnotation> stripAnnotationList) {
    return stripAnnotationList.stream().map(el -> getClassName(el)).collect(Collectors.toList());
  }

  private static String getClassName(StripAnnotation element) {
    return "L" + element.getPattern().replace('.', '/') + ";";
  }

  @Override
  protected ClassVisitor transform(ClassVisitor classVisitor) {
    return new AnnotationRemover(classVisitor);
  }

  private class AnnotationRemover extends ClassVisitor {

    AnnotationRemover(ClassVisitor cv) {
      super(ASM_VERSION, cv);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      return visitAnnotationCommon(descriptor, () -> super.visitAnnotation(descriptor, visible));
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String descriptor, String signature, Object value) {
      FieldVisitor superVisitor = super.visitField(access, name, descriptor, signature, value);
      return new FieldVisitor(ASM_VERSION, superVisitor) {
        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
          return visitAnnotationCommon(
              descriptor, () -> super.visitAnnotation(descriptor, visible));
        }
      };
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodVisitor superVisitor =
          super.visitMethod(access, name, descriptor, signature, exceptions);
      return new MethodVisitor(ASM_VERSION, superVisitor) {
        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
          return visitAnnotationCommon(
              descriptor, () -> super.visitAnnotation(descriptor, visible));
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(
            int parameter, String descriptor, boolean visible) {
          return visitAnnotationCommon(
              descriptor, () -> super.visitParameterAnnotation(parameter, descriptor, visible));
        }
      };
    }

    /**
     * Create an {@link AnnotationVisitor} that removes any annotations from {@link
     * #stripAnnotationList}.
     */
    private AnnotationVisitor visitAnnotationCommon(
        String annotation, Supplier<AnnotationVisitor> defaultVisitorSupplier) {
      if (stripAnnotationList.contains(annotation)) {
        return null;
      }
      // Only get() the default AnnotationVisitor if the annotation is to be included.
      // Invoking super.visitAnnotation(descriptor, visible) causes the annotation to be
      // included in the output even if the resulting AnnotationVisitor is not returned or
      // used.
      return defaultVisitorSupplier.get();
    }
  }
}
