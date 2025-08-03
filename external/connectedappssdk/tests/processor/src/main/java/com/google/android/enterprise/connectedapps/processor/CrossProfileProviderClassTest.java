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

import static com.google.android.enterprise.connectedapps.processor.TestUtilities.ANNOTATED_NOTES_CONNECTOR;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.NOTES_PACKAGE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesCrossProfileType;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.formatErrorMessage;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.staticType;
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
public class CrossProfileProviderClassTest {

  private static final String INVALID_CONSTRUCTORS_ERROR =
      "Provider classes must have a single public constructor which takes either a single Context"
          + " argument or no arguments";
  private static final String PROVIDER_CLASS_DIFFERENT_CONNECTOR_ERROR =
      "All @CROSS_PROFILE_ANNOTATION types provided by a provider class must use the same"
          + " ProfileConnector";
  private static final String STATICTYPES_ERROR =
      "@CROSS_PROFILE_ANNOTATION classes referenced in @CROSS_PROFILE_PROVIDER_ANNOTATION"
          + " staticTypes annotations must not have non-static @CROSS_PROFILE_ANNOTATION annotated"
          + " methods";

  private final AnnotationStrings annotationStrings;

  public CrossProfileProviderClassTest(AnnotationStrings annotationStrings) {
    this.annotationStrings = annotationStrings;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void hasACustomNoArgsConstructor_compiles() {
    JavaFileObject providerClass =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            "  public NotesProvider() {",
            "  }",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(providerClass, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void hasNonPublicNoArgsConstructor_hasError() {
    JavaFileObject providerClass =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            "  NotesProvider() {",
            "  }",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(providerClass, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(INVALID_CONSTRUCTORS_ERROR, annotationStrings))
        .inFile(providerClass);
  }

  @Test
  public void hasNoNoArgsConstructor_hasError() {
    JavaFileObject providerClass =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            "  public NotesProvider(String p) {",
            "  }",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(providerClass, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(INVALID_CONSTRUCTORS_ERROR, annotationStrings))
        .inFile(providerClass);
  }

  @Test
  public void hasPublicConstructorTakingContext_compiles() {
    JavaFileObject providerClass =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import android.content.Context;",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            "  public NotesProvider(Context c) {",
            "  }",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(providerClass, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void hasMoreThanOnePublicConstructor_errors() {
    JavaFileObject providerClass =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import android.content.Context;",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            "  public NotesProvider(Context c) {",
            "  }",
            "  public NotesProvider() {",
            "  }",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(providerClass, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(INVALID_CONSTRUCTORS_ERROR, annotationStrings))
        .inFile(providerClass);
  }

  @Test
  public void hasCrossProfileTypesWithDifferentConnectors_hasError() {
    JavaFileObject providerClass =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType2 provideNotesType2() {",
            "    return new NotesType2();",
            "  }",
            "}");

    JavaFileObject notesTypeWithCrossProfileConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileAsAnnotation("connector=CrossProfileConnector.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    JavaFileObject notesType2WithNotesConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType2",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation("connector=NotesConnector.class"),
            "public final class NotesType2 {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                providerClass,
                notesTypeWithCrossProfileConnector,
                notesType2WithNotesConnector,
                ANNOTATED_NOTES_CONNECTOR);

    assertThat(compilation)
        .hadErrorContaining(
            formatErrorMessage(PROVIDER_CLASS_DIFFERENT_CONNECTOR_ERROR, annotationStrings))
        .inFile(providerClass);
  }

  @Test
  public void staticTypes_onlyReferencesStaticTypes_compiles() {
    JavaFileObject notesProvider =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileProviderAsAnnotation("staticTypes={StaticType.class}"),
            "public final class NotesProvider {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesProvider, staticType(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void staticTypes_referencesNonStaticType_hasError() {
    JavaFileObject notesProvider =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileProviderAsAnnotation("staticTypes={NotesType.class}"),
            "public final class NotesProvider {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesProvider, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(STATICTYPES_ERROR, annotationStrings))
        .inFile(notesProvider);
  }

  @Test
  public void staticProvidedClass_usedTypeIsIncludedInBundler() {
    JavaFileObject notesProvider =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileProviderAsAnnotation("staticTypes={StaticType.class}"),
            "public final class NotesProvider {",
            "}");
    JavaFileObject staticType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".StaticType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class StaticType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public static void refreshNotes(String param) {",
            "  }",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(notesProvider, staticType);

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.StaticType_Bundler")
        .contentsAsUtf8String()
        .contains("parcel.writeString(");
  }
}
