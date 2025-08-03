/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.processor;

import static com.google.android.enterprise.connectedapps.processor.TestUtilities.NOTES_PACKAGE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.PARCELABLE_OBJECT;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.SERIALIZABLE_OBJECT;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.combineParameters;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.android.enterprise.connectedapps.annotations.CrossProfileCallback;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationPrinter;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test supported parameter types for methods on an {@link CrossProfileCallback} annotated
 * interface.
 *
 * <p>This tests a single parameter of each supported type. Multiple parameters and unsupported
 * types are tested in {@link CrossProfileCallbackTest}.
 */
@RunWith(Parameterized.class)
public class CrossProfileCallbackSupportedParameterTypeTest {

  @Parameters(name = "{0} for {1}")
  public static Iterable<Object[]> data() {
    String[] types = {
      "String",
      "String[]",
      "byte",
      "Byte",
      "short",
      "Short",
      "int",
      "Integer",
      "long",
      "Long",
      "float",
      "Float",
      "double",
      "Double",
      "char",
      "Character",
      "boolean",
      "Boolean",
      "ParcelableObject",
      "ParcelableObject[]",
      "java.util.List<ParcelableObject>",
      "java.util.List<ParcelableObject[]>",
      "java.util.List<java.util.List<ParcelableObject>>",
      "java.util.List<SerializableObject>",
      "java.util.List<SerializableObject[]>",
      "java.util.List<java.util.List<SerializableObject>>",
      "java.util.List<String>",
      "java.util.List<String[]>",
      "java.util.Map<String, String>",
      "java.util.Set<String>",
      "java.util.Collection<String>",
      "com.google.protos.connectedappssdk.TestProtoOuterClass.TestProto",
      "com.google.protos.connectedappssdk.TestProtoOuterClass.TestProto[]",
      "java.util.List<com.google.protos.connectedappssdk.TestProtoOuterClass.TestProto>",
      "com.google.common.collect.ImmutableMap<String, String>",
      "android.util.Pair<String, Integer>",
      "com.google.common.base.Optional<ParcelableObject>",
      "android.graphics.drawable.Drawable"
    };
    return combineParameters(AnnotationFinder.annotationStrings(), Arrays.asList(types));
  }

  private final AnnotationPrinter annotationPrinter;

  private final String type;

  public CrossProfileCallbackSupportedParameterTypeTest(
      AnnotationPrinter annotationPrinter, String type) {
    this.annotationPrinter = annotationPrinter;
    this.type = type;
  }

  @Test
  public void crossProfileCallbackInterfaceWithSupportedParameterType_compiles() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileCallbackQualifiedName() + ";",
            annotationPrinter.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "    void installationComplete(" + type + " param);",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(callbackInterface, PARCELABLE_OBJECT, SERIALIZABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }
}
