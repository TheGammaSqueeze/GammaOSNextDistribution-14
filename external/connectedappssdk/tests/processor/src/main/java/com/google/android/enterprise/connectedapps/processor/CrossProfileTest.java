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

import static com.google.android.enterprise.connectedapps.processor.TestUtilities.CROSS_PROFILE_QUALIFIED_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.CUSTOM_WRAPPER;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.GENERIC_PARCELABLE_OBJECT;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.GENERIC_SERIALIZABLE_OBJECT;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.INSTALLATION_LISTENER_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.NOTES_PACKAGE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.PARCELABLE_CUSTOM_WRAPPER;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.PROFILE_CONNECTOR_QUALIFIED_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.SIMPLE_FUTURE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.SIMPLE_FUTURE_WRAPPER;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.UNSUPPORTED_TYPE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.UNSUPPORTED_TYPE_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesCrossProfileType;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesProvider;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.formatErrorMessage;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.installationListener;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationStrings;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CrossProfileTest {

  private static final String UNSUPPORTED_RETURN_TYPE_ERROR =
      "cannot be returned by methods annotated @CROSS_PROFILE_ANNOTATION";
  private static final String UNSUPPORTED_PARAMETER_TYPE_ERROR =
      "cannot be used by parameters of methods annotated @CROSS_PROFILE_ANNOTATION";
  private static final String MULTIPLE_ASYNC_CALLBACK_PARAMETERS_ERROR =
      "Methods annotated @CROSS_PROFILE_ANNOTATION can have a maximum of one parameter of a type"
          + " annotated @CROSS_PROFILE_CALLBACK_ANNOTATION";
  private static final String NON_VOID_CALLBACK_ERROR =
      "Methods annotated @CROSS_PROFILE_ANNOTATION which take a parameter type annotated"
          + " @CROSS_PROFILE_CALLBACK_ANNOTATION must return void";
  private static final String METHOD_ISSTATIC_ERROR =
      "@CROSS_PROFILE_ANNOTATION annotations on methods can not specify isStatic";
  private static final String METHOD_CONNECTOR_ERROR =
      "@CROSS_PROFILE_ANNOTATION annotations on methods can not specify a connector";
  private static final String METHOD_PARCELABLE_WRAPPERS_ERROR =
      "@CROSS_PROFILE_ANNOTATION annotations on methods can not specify parcelable wrappers";
  private static final String ASYNC_DECLARED_EXCEPTION_ERROR =
      "Asynchronous methods annotated @CROSS_PROFILE_ANNOTATION cannot declare exceptions";
  private static final String PARCELABLE_WRAPPER_ANNOTATION_ERROR =
      "Parcelable Wrappers must be annotated @CustomParcelableWrapper";
  private static final String FUTURE_WRAPPER_ANNOTATION_ERROR =
      "Future Wrappers must be annotated @CustomFutureWrapper";

  private final AnnotationStrings annotationStrings;

  public CrossProfileTest(AnnotationStrings annotationStrings) {
    this.annotationStrings = annotationStrings;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void validCrossProfileAnnotation_compiles() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesProvider(annotationStrings),
                annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void staticCrossProfileMethod_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public static void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithUnsupportedReturnType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public " + UNSUPPORTED_TYPE_NAME + " refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), UNSUPPORTED_TYPE);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithContextReturnType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import android.content.Context;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public Context refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), UNSUPPORTED_TYPE);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithUnsupportedReturnTypeArray_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public " + UNSUPPORTED_TYPE_NAME + "[] refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), UNSUPPORTED_TYPE);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithUnsupportedReturnTypeInGeneric_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public java.util.List<" + UNSUPPORTED_TYPE_NAME + "> refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), UNSUPPORTED_TYPE);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithContextReturnTypeInGeneric_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import android.content.Context;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public java.util.List<Context> refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), UNSUPPORTED_TYPE);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithUnsupportedReturnTypeInGenericParcelable_compiles() {
    // Parcelables take responsibility for their generics so we don't validate them
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public GenericParcelableObject<" + UNSUPPORTED_TYPE_NAME + "> refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                UNSUPPORTED_TYPE,
                GENERIC_PARCELABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithUnsupportedReturnTypeInGenericSerializable_compiles() {
    // Serializables take responsibility for their generics so we don't validate them
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public GenericSerializableObject<" + UNSUPPORTED_TYPE_NAME + "> refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                UNSUPPORTED_TYPE,
                GENERIC_SERIALIZABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithVoidReturnType_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithCrossProfileCallbackInterfaceReturnType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public " + INSTALLATION_LISTENER_NAME + " refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithCrossProfileCallbackInterfaceArrayReturnType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public " + INSTALLATION_LISTENER_NAME + "[] refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithMultipleSupportedParameters_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(String s, String t) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithUnsupportedParameterType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(" + UNSUPPORTED_TYPE_NAME + " s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), UNSUPPORTED_TYPE);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithUnsupportedArrayParameterType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(" + UNSUPPORTED_TYPE_NAME + "[] s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), UNSUPPORTED_TYPE);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithUnsupportedParameterTypeInGeneric_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(java.util.List<" + UNSUPPORTED_TYPE_NAME + "> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), UNSUPPORTED_TYPE);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithCrossProfileCallbackListenerInGeneric_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(java.util.List<" + INSTALLATION_LISTENER_NAME + "> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithCrossProfileCallbackListenerInArray_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(" + INSTALLATION_LISTENER_NAME + "[] s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithMultipleCrossProfileCallbackListenerParameters_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes("
                + INSTALLATION_LISTENER_NAME
                + " a, "
                + INSTALLATION_LISTENER_NAME
                + " b) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(
            formatErrorMessage(MULTIPLE_ASYNC_CALLBACK_PARAMETERS_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithUnsupportedParameterTypeInGenericParcelable_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(GenericParcelableObject<"
                + UNSUPPORTED_TYPE_NAME
                + "> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                UNSUPPORTED_TYPE,
                GENERIC_PARCELABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithUnsupportedParameterTypeInGenericSerializable_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(GenericSerializableObject<"
                + UNSUPPORTED_TYPE_NAME
                + "> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                UNSUPPORTED_TYPE,
                GENERIC_SERIALIZABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithCrossProfileCallbackListenerInGenericParcelable_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(GenericParcelableObject<"
                + INSTALLATION_LISTENER_NAME
                + "> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListener(annotationStrings),
                GENERIC_PARCELABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithCrossProfileCallbackListenerInGenericSerializable_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(GenericSerializableObject<"
                + INSTALLATION_LISTENER_NAME
                + "> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListener(annotationStrings),
                GENERIC_SERIALIZABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithListenableFutureParameter_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(ListenableFuture<String> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithListenableFutureArrayParameter_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(ListenableFuture<String>[] s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithListenableFutureInParcelableWrapperParameter_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(java.util.List<ListenableFuture<String>> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithListenableFutureInParcelableWrapperReturnType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public java.util.List<ListenableFuture<String>> refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithListenableFutureReturnWithUnsupportedGenericType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public ListenableFuture<" + UNSUPPORTED_TYPE_NAME + "> refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), UNSUPPORTED_TYPE);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithNonVoidReturnAndCrossProfileCallbackParameter_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public String refreshNotes(" + INSTALLATION_LISTENER_NAME + " a) {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(NON_VOID_CALLBACK_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithListenableFutureInGenericParcelable_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(GenericParcelableObject<ListenableFuture<String>> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType, annotatedNotesProvider(annotationStrings), GENERIC_PARCELABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithListenableFutureInGenericSerializable_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(GenericSerializableObject<ListenableFuture<String>> s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType, annotatedNotesProvider(annotationStrings), GENERIC_SERIALIZABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethodWithGenericArrayParameter_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(java.util.Collection<String>[] s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithGenericArrayReturnType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public java.util.Collection<String>[] refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithMultiDimensionalArrayParameterType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(java.lang.String[][] s) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileMethodWithMultiDimensionalArrayReturnType_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public java.lang.String[][] refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void specifyConnectorOnMethodAnnotation_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation("connector=CrossProfileConnector.class"),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(METHOD_CONNECTOR_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void specifyParcelableWrappersOnMethodAnnotation_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(
                "parcelableWrappers=ParcelableCustomWrapper.class"),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                PARCELABLE_CUSTOM_WRAPPER,
                CUSTOM_WRAPPER);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(METHOD_PARCELABLE_WRAPPERS_ERROR, annotationStrings))
        .inFile(notesType);
  }

  @Test
  public void crossProfileInterface_works() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesInterface",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public interface NotesInterface {",
            annotationStrings.crossProfileAsAnnotation(),
            "  void refreshNotes();",
            "}");
    JavaFileObject providerClass =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesInterface provideNotesInterface() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(notesType, providerClass);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethod_synchronous_declaresException_compiles() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import java.io.IOException;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() throws IOException {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileType, installationListener(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethod_async_declaresException_hasError() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import java.io.IOException;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes("
                + INSTALLATION_LISTENER_NAME
                + " callback) throws IOException {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileType, installationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(ASYNC_DECLARED_EXCEPTION_ERROR, annotationStrings))
        .inFile(crossProfileType);
  }

  @Test
  public void crossProfileMethod_future_declaresException_hasError() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import java.io.IOException;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public ListenableFuture<String> refreshNotes() throws IOException {",
            "     return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileType, installationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(ASYNC_DECLARED_EXCEPTION_ERROR, annotationStrings))
        .inFile(crossProfileType);
  }

  @Test
  public void crossProfileMethod_returnsCustomParcelableType_compiles() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(
                "connector=CrossProfileConnectorWhichSupportsCustomWrapper.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public CustomWrapper<String> refreshNotes() {",
            "      return null;",
            "  }",
            "}");

    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWhichSupportsCustomWrapper",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(parcelableWrappers={ParcelableCustomWrapper.class})",
            "public interface CrossProfileConnectorWhichSupportsCustomWrapper extends"
                + " ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileType, connector, CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethod_takesCustomParcelableTypeArgument_compiles() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(
                "connector=CrossProfileConnectorWhichSupportsCustomWrapper.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(CustomWrapper<String> a) {",
            "  }",
            "}");

    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWhichSupportsCustomWrapper",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(parcelableWrappers={ParcelableCustomWrapper.class})",
            "public interface CrossProfileConnectorWhichSupportsCustomWrapper extends"
                + " ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileType, connector, CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethod_usesCustomParcelableTypeFromDifferentConnector_hasError() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(
                "connector=CrossProfileConnectorWhichSupportsCustomWrapper.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public CustomWrapper<String> refreshNotes() {",
            "      return null;",
            "  }",
            "}");

    JavaFileObject secondCrossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType2",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType2 {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public CustomWrapper<String> refreshNotes() {",
            "      return null;",
            "  }",
            "}");

    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWhichSupportsCustomWrapper",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(parcelableWrappers={ParcelableCustomWrapper.class})",
            "public interface CrossProfileConnectorWhichSupportsCustomWrapper extends"
                + " ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileType,
                secondCrossProfileType,
                connector,
                CUSTOM_WRAPPER,
                PARCELABLE_CUSTOM_WRAPPER);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(secondCrossProfileType);
  }

  @Test
  public void crossProfileMethod_returnsCustomParcelableTypeForCrossProfileType_compiles() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(
                "parcelableWrappers=ParcelableCustomWrapper.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public CustomWrapper<String> refreshNotes() {",
            "      return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileType, CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethod_takesCustomParcelableTypeForCrossProfileTypeAsArgument_compiles() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(
                "parcelableWrappers=ParcelableCustomWrapper.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes(CustomWrapper<String> a) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileType, CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileType_includesNonParcelableWrapperInParcelableWrappers_hasError() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation("parcelableWrappers=String.class"),
            "public final class NotesType {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(crossProfileType);

    assertThat(compilation)
        .hadErrorContaining(
            formatErrorMessage(PARCELABLE_WRAPPER_ANNOTATION_ERROR, annotationStrings))
        .inFile(crossProfileType);
  }

  @Test
  public void crossProfileMethod_returnsCustomFutureType_compiles() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(
                "connector=CrossProfileConnectorWhichSupportsSimpleFuture.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public SimpleFuture<String> refreshNotes() {",
            "      return null;",
            "  }",
            "}");

    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWhichSupportsSimpleFuture",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(futureWrappers={SimpleFutureWrapper.class})",
            "public interface CrossProfileConnectorWhichSupportsSimpleFuture extends"
                + " ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileType, connector, SIMPLE_FUTURE, SIMPLE_FUTURE_WRAPPER);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethod_usesCustomFutureTypeFromDifferentConnector_hasError() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(
                "connector=CrossProfileConnectorWhichSupportsSimpleFuture.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public SimpleFuture<String> refreshNotes() {",
            "      return null;",
            "  }",
            "}");

    JavaFileObject secondCrossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType2",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType2 {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public SimpleFuture<String> refreshNotes() {",
            "      return null;",
            "  }",
            "}");

    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWhichSupportsSimpleFuture",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(futureWrappers={SimpleFutureWrapper.class})",
            "public interface CrossProfileConnectorWhichSupportsSimpleFuture extends"
                + " ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileType,
                secondCrossProfileType,
                connector,
                SIMPLE_FUTURE,
                SIMPLE_FUTURE_WRAPPER);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_RETURN_TYPE_ERROR, annotationStrings))
        .inFile(secondCrossProfileType);
  }

  @Test
  public void crossProfileMethod_usesCustomFutureTypeImportedFromDifferentConnector_compiles() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + CROSS_PROFILE_QUALIFIED_NAME + ";",
            "@CrossProfile(connector=CrossProfileConnectorWithImport.class)",
            "public final class NotesType {",
            "  @CrossProfile",
            "  public SimpleFuture<String> refreshNotes() {",
            "      return null;",
            "  }",
            "}");
    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWhichSupportsSimpleFuture",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(futureWrappers={SimpleFutureWrapper.class})",
            "public interface CrossProfileConnectorWhichSupportsSimpleFuture extends"
                + " ProfileConnector {",
            "}");
    JavaFileObject connectorWithImport =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWithImport",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(imports=CrossProfileConnectorWhichSupportsSimpleFuture.class)",
            "public interface CrossProfileConnectorWithImport extends"
                + " ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileType,
                connector,
                connectorWithImport,
                SIMPLE_FUTURE,
                SIMPLE_FUTURE_WRAPPER);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethod_usesCustomFutureTypeImportedIndirectly_compiles() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + CROSS_PROFILE_QUALIFIED_NAME + ";",
            "@CrossProfile(connector=CrossProfileConnectorWithImportOfImport.class)",
            "public final class NotesType {",
            "  @CrossProfile",
            "  public SimpleFuture<String> refreshNotes() {",
            "      return null;",
            "  }",
            "}");
    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWhichSupportsSimpleFuture",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(futureWrappers={SimpleFutureWrapper.class})",
            "public interface CrossProfileConnectorWhichSupportsSimpleFuture extends"
                + " ProfileConnector {",
            "}");
    JavaFileObject connectorWithImport =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWithImport",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(imports=CrossProfileConnectorWhichSupportsSimpleFuture.class)",
            "public interface CrossProfileConnectorWithImport extends"
                + " ProfileConnector {",
            "}");
    JavaFileObject connectorWithImportOfImport =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".CrossProfileConnectorWithImportOfImport",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(imports=CrossProfileConnectorWithImport.class)",
            "public interface CrossProfileConnectorWithImportOfImport extends"
                + " ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileType,
                connector,
                connectorWithImport,
                connectorWithImportOfImport,
                SIMPLE_FUTURE,
                SIMPLE_FUTURE_WRAPPER);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileMethod_usesCustomFutureTypeForCrossProfileType_compiles() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation("futureWrappers=SimpleFutureWrapper.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public SimpleFuture<String> refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileType,
                SIMPLE_FUTURE,
                SIMPLE_FUTURE_WRAPPER,
                annotatedNotesProvider(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileType_includesNonFutureWrapperInFutureWrappers_hasError() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation("futureWrappers=String.class"),
            "public final class NotesType {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(crossProfileType);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(FUTURE_WRAPPER_ANNOTATION_ERROR, annotationStrings))
        .inFile(crossProfileType);
  }

  @Test
  public void specifyIsStaticOnMethodAnnotation_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation("isStatic=true"),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(METHOD_ISSTATIC_ERROR, annotationStrings))
        .inFile(notesType);
  }
}
