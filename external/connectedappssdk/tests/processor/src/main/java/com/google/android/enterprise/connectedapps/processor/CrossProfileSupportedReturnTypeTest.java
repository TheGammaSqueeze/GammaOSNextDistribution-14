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
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesProvider;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.combineParameters;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
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
 * Test supported return types for {@link CrossProfile} annotated methods.
 *
 * <p>This does not test the {@code void} return type as that does not return anything. This is
 * tested in {@link CrossProfileTest}.
 */
@RunWith(Parameterized.class)
public class CrossProfileSupportedReturnTypeTest {

  @Parameters(name = "{0} for {1}")
  public static Iterable<Object[]> data() {
    TypeWithReturnValue[] typesWithReturnValues =
        new TypeWithReturnValue[] {
          TypeWithReturnValue.referenceType("Void"),
          TypeWithReturnValue.referenceType("String"),
          TypeWithReturnValue.referenceType("CharSequence"),
          TypeWithReturnValue.referenceType("String[]"),
          TypeWithReturnValue.primitiveType("byte", "0"),
          TypeWithReturnValue.referenceType("byte[]"),
          TypeWithReturnValue.referenceType("byte[][][]"),
          TypeWithReturnValue.referenceType("Byte"),
          TypeWithReturnValue.primitiveType("short", "0"),
          TypeWithReturnValue.referenceType("short[]"),
          TypeWithReturnValue.referenceType("short[][][]"),
          TypeWithReturnValue.referenceType("Short"),
          TypeWithReturnValue.primitiveType("int", "0"),
          TypeWithReturnValue.referenceType("int[]"),
          TypeWithReturnValue.referenceType("int[][][]"),
          TypeWithReturnValue.referenceType("Integer"),
          TypeWithReturnValue.primitiveType("long", "0"),
          TypeWithReturnValue.referenceType("long[]"),
          TypeWithReturnValue.referenceType("long[][][]"),
          TypeWithReturnValue.referenceType("Long"),
          TypeWithReturnValue.primitiveType("float", "0"),
          TypeWithReturnValue.referenceType("float[]"),
          TypeWithReturnValue.referenceType("float[][][]"),
          TypeWithReturnValue.referenceType("Float"),
          TypeWithReturnValue.primitiveType("double", "0"),
          TypeWithReturnValue.referenceType("double[]"),
          TypeWithReturnValue.referenceType("double[][][]"),
          TypeWithReturnValue.referenceType("Double"),
          TypeWithReturnValue.primitiveType("char", "'a'"),
          TypeWithReturnValue.referenceType("char[]"),
          TypeWithReturnValue.referenceType("char[][][]"),
          TypeWithReturnValue.referenceType("Character"),
          TypeWithReturnValue.primitiveType("boolean", "false"),
          TypeWithReturnValue.referenceType("boolean[]"),
          TypeWithReturnValue.referenceType("boolean[][][]"),
          TypeWithReturnValue.referenceType("Boolean"),
          TypeWithReturnValue.referenceType("ParcelableObject"),
          TypeWithReturnValue.referenceType("ParcelableObject[]"),
          TypeWithReturnValue.referenceType("java.util.List<ParcelableObject>"),
          TypeWithReturnValue.referenceType("java.util.List<ParcelableObject[]>"),
          TypeWithReturnValue.referenceType("java.util.List<java.util.List<ParcelableObject>>"),
          TypeWithReturnValue.referenceType("SerializableObject"),
          TypeWithReturnValue.referenceType("SerializableObject[]"),
          TypeWithReturnValue.referenceType("java.util.List<SerializableObject>"),
          TypeWithReturnValue.referenceType("java.util.List<SerializableObject[]>"),
          TypeWithReturnValue.referenceType("java.util.List<java.util.List<SerializableObject>>"),
          TypeWithReturnValue.referenceType("java.util.List<String>"),
          TypeWithReturnValue.referenceType("java.util.List<String[]>"),
          TypeWithReturnValue.referenceType("java.util.Map<String, String>"),
          TypeWithReturnValue.referenceType("java.util.Set<String>"),
          TypeWithReturnValue.referenceType("java.util.Collection<String>"),
          TypeWithReturnValue.referenceType("java.util.Optional<String>"),
          TypeWithReturnValue.referenceType(
              "com.google.protos.connectedappssdk.TestProtoOuterClass.TestProto"),
          TypeWithReturnValue.referenceType(
              "com.google.protos.connectedappssdk.TestProtoOuterClass.TestProto[]"),
          TypeWithReturnValue.referenceType(
              "java.util.List<com.google.protos.connectedappssdk.TestProtoOuterClass.TestProto>"),
          TypeWithReturnValue.referenceType(
              "com.google.common.util.concurrent.ListenableFuture<String>"),
          TypeWithReturnValue.referenceType(
              "com.google.common.util.concurrent.ListenableFuture<String[]>"),
          TypeWithReturnValue.referenceType(
              "com.google.common.util.concurrent.ListenableFuture<com.google.protos.connectedappssdk.TestProtoOuterClass.TestProto>"),
          TypeWithReturnValue.referenceType(
              "com.google.common.util.concurrent.ListenableFuture<java.util.List<String>>"),
          TypeWithReturnValue.referenceType(
              "com.google.common.collect.ImmutableMap<String, String>"),
          TypeWithReturnValue.referenceType("android.util.Pair<String, Integer>"),
          TypeWithReturnValue.referenceType("com.google.common.base.Optional<ParcelableObject>"),
          TypeWithReturnValue.referenceType("android.graphics.Bitmap"),
          TypeWithReturnValue.referenceType("android.os.Parcelable"),
          TypeWithReturnValue.referenceType("android.graphics.drawable.Drawable")
        };
    return combineParameters(
        AnnotationFinder.annotationStrings(), Arrays.asList(typesWithReturnValues));
  }

  private final AnnotationPrinter annotationPrinter;

  private final TypeWithReturnValue supportedReturnType;

  public CrossProfileSupportedReturnTypeTest(
      AnnotationPrinter annotationPrinter, TypeWithReturnValue supportedReturnType) {
    this.annotationPrinter = annotationPrinter;
    this.supportedReturnType = supportedReturnType;
  }

  @Test
  public void crossProfileMethodWithSupportedReturnType_compiles() {
    JavaFileObject crossProfileMethodWithSupportedReturnType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public " + supportedReturnType.type + " refreshNotes() {",
            "    return " + supportedReturnType.returnValue + ";",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileMethodWithSupportedReturnType,
                annotatedNotesProvider(annotationPrinter),
                PARCELABLE_OBJECT,
                SERIALIZABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  private static class TypeWithReturnValue {

    final String type;

    final String returnValue;

    static TypeWithReturnValue primitiveType(String type, String returnValue) {
      return new TypeWithReturnValue(type, returnValue);
    }

    static TypeWithReturnValue referenceType(String type) {
      return new TypeWithReturnValue(type, "null");
    }

    private TypeWithReturnValue(String type, String returnValue) {
      this.type = type;
      this.returnValue = returnValue;
    }

    @Override
    public String toString() {
      return type;
    }
  }
}
