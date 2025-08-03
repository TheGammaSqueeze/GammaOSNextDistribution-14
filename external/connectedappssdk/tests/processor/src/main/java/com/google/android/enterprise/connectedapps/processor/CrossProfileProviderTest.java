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
public class CrossProfileProviderTest {

  private static final String MULTIPLE_PROVIDERS_ERROR = "has been provided more than once";
  private static final String PROVIDING_NON_CROSS_PROFILE_TYPE_ERROR =
      "Methods annotated @CROSS_PROFILE_PROVIDER_ANNOTATION must only return"
          + " @CROSS_PROFILE_ANNOTATION annotated types";
  private static final String PROVIDER_INCORRECT_ARGS_ERROR =
      "Methods annotated @CROSS_PROFILE_PROVIDER_ANNOTATION can only take a single Context"
          + " argument, or no-args";
  private static final String STATIC_PROVIDER_ERROR =
      "Methods annotated @CROSS_PROFILE_PROVIDER_ANNOTATION can not be static";
  private static final String METHOD_STATICTYPES_ERROR =
      "@CROSS_PROFILE_PROVIDER_ANNOTATION annotations on methods can not specify staticTypes";

  private final AnnotationStrings annotationStrings;

  public CrossProfileProviderTest(AnnotationStrings annotationStrings) {
    this.annotationStrings = annotationStrings;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void providesAValidCrossProfileType_compiles() {
    final JavaFileObject validNotesProvider =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(validNotesProvider, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void providesANotCrossProfileType_hasError() {
    final JavaFileObject stringProvider =
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
            "  public String provideString() {",
            "    return \"Test\";",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(stringProvider, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(
            formatErrorMessage(PROVIDING_NON_CROSS_PROFILE_TYPE_ERROR, annotationStrings))
        .inFile(stringProvider);
  }

  @Test
  public void takesContextArgument_compiles() {
    final JavaFileObject validNotesProvider =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import android.content.Context;",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType(Context context) {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(validNotesProvider, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void takesNonContextArgument_hasError() {
    final JavaFileObject notesProviderTakesNonContextArgument =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType(String s) {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesProviderTakesNonContextArgument,
                annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(PROVIDER_INCORRECT_ARGS_ERROR, annotationStrings))
        .inFile(notesProviderTakesNonContextArgument);
  }

  @Test
  public void takesMultipleContextArguments_hasError() {
    final JavaFileObject notesProviderTakesMultipleContextArguments =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType(Context c1, Context c2) {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesProviderTakesMultipleContextArguments,
                annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(PROVIDER_INCORRECT_ARGS_ERROR, annotationStrings))
        .inFile(notesProviderTakesMultipleContextArguments);
  }

  @Test
  public void isStaticMethod_hasError() {
    final JavaFileObject staticNotesProvider =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public static NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(staticNotesProvider, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(STATIC_PROVIDER_ERROR, annotationStrings))
        .inFile(staticNotesProvider);
  }

  @Test
  public void providesSameTypeTwice_hasError() {
    final JavaFileObject staticNotesProvider =
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
            "  public NotesType provideNotesType2() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(staticNotesProvider, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(MULTIPLE_PROVIDERS_ERROR, annotationStrings))
        .inFile(staticNotesProvider);
  }

  @Test
  public void providesSameTypeTwiceInDifferentProviders_hasError() {
    final JavaFileObject notesProvider1 =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider1",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider1 {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");
    final JavaFileObject notesProvider2 =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider2",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider2 {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesProvider1, notesProvider2, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(MULTIPLE_PROVIDERS_ERROR, annotationStrings))
        .inFile(notesProvider1);
    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(MULTIPLE_PROVIDERS_ERROR, annotationStrings))
        .inFile(notesProvider2);
  }

  @Test
  public void providesSameTypeTwiceInStaticAndNonStaticProviders_hasError() {
    final JavaFileObject staticNotesProvider =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".StaticNotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            annotationStrings.crossProfileProviderAsAnnotation("staticTypes=StaticType.class"),
            "public final class StaticNotesProvider {",
            "}");
    final JavaFileObject nonStaticNotesProvider =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NonStaticNotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NonStaticNotesProvider {",
            annotationStrings.crossProfileProviderAsAnnotation(),
            "  public StaticType provideNotesType() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(staticNotesProvider, nonStaticNotesProvider, staticType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(MULTIPLE_PROVIDERS_ERROR, annotationStrings))
        .inFile(staticNotesProvider);
    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(MULTIPLE_PROVIDERS_ERROR, annotationStrings))
        .inFile(nonStaticNotesProvider);
  }

  @Test
  public void specifyStaticTypesOnMethodAnnotation_hasError() {
    JavaFileObject notesProvider =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            "public final class NotesProvider {",
            annotationStrings.crossProfileProviderAsAnnotation("staticTypes=NotesType.class"),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesProvider, annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(METHOD_STATICTYPES_ERROR, annotationStrings))
        .inFile(notesProvider);
  }
}
