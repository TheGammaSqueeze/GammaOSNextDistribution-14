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
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.PROFILE_CONNECTOR_QUALIFIED_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesCrossProfileType;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesProvider;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.formatErrorMessage;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationPrinter;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationStrings;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CrossProfileTypeTest {

  private static JavaFileObject secondAnnotatedNotesProvider(AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".NotesProvider2",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileProviderQualifiedName() + ";",
        "public final class NotesProvider2 {",
        annotationPrinter.crossProfileProviderAsAnnotation(),
        "  public NotesType provideNotesType() {",
        "    return new NotesType();",
        "  }",
        "}");
  }

  private static final String MULTIPLE_PROVIDERS_ERROR = "has been provided more than once";
  private static final String DEFAULT_PACKAGE_ERROR =
      "@CROSS_PROFILE_ANNOTATION types must not be in the default package";
  private static final String NON_PUBLIC_CLASS_ERROR =
      "@CROSS_PROFILE_ANNOTATION types must be public";
  private static final String CONNECTOR_MUST_EXTEND_CONNECTOR =
      "Interfaces specified as a connector must extend ProfileConnector or UserConnector";
  private static final String CONNECTOR_MUST_BE_INTERFACE = "Connectors must be interfaces";
  private static final String NOT_STATIC_ERROR =
      "Types annotated @CROSS_PROFILE_ANNOTATION(isStatic=true) must not contain any non-static"
          + " methods annotated @CROSS_PROFILE_ANNOTATION";

  private final AnnotationStrings annotationStrings;

  public CrossProfileTypeTest(AnnotationStrings annotationStrings) {
    this.annotationStrings = annotationStrings;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationNames() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void crossProfileType_inDefaultPackage_hasError() {
    JavaFileObject crossProfileTypeInDefaultPackage =
        JavaFileObjects.forSourceLines(
            "NotesType",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(crossProfileTypeInDefaultPackage);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(DEFAULT_PACKAGE_ERROR, annotationStrings))
        .inFile(crossProfileTypeInDefaultPackage);
  }

  @Test
  public void crossProfileType_inNonPublicClass_hasError() {
    JavaFileObject crossProfileTypeInNonPublicClass =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(crossProfileTypeInNonPublicClass);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(NON_PUBLIC_CLASS_ERROR, annotationStrings))
        .inFile(crossProfileTypeInNonPublicClass);
  }

  @Test
  public void crossProfileType_doesNotSpecifyConnector_compiles() {
    JavaFileObject crossProfileTypeWithoutConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(crossProfileTypeWithoutConnector);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileType_specifiesValidConnector_compiles() {
    JavaFileObject crossProfileTypeWithValidConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation("connector=NotesConnector.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileTypeWithValidConnector, ANNOTATED_NOTES_CONNECTOR);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileType_specifiesNotInterfaceConnector_hasError() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation("connector=NotesConnector.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");
    JavaFileObject notesConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "public class NotesConnector {",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(crossProfileType, notesConnector);

    assertThat(compilation)
        .hadErrorContaining(CONNECTOR_MUST_BE_INTERFACE)
        .inFile(crossProfileType);
  }

  @Test
  public void crossProfileType_specifiesConnectorNotExtendingConnectorInterface_hasError() {
    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation("connector=NotesConnector.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");
    JavaFileObject notesConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "public interface NotesConnector {",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(crossProfileType, notesConnector);

    assertThat(compilation)
        .hadErrorContaining(CONNECTOR_MUST_EXTEND_CONNECTOR)
        .inFile(crossProfileType);
  }

  @Test
  public void multipleCrossProfileProviders_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesCrossProfileType(annotationStrings),
                annotatedNotesProvider(annotationStrings),
                secondAnnotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(MULTIPLE_PROVIDERS_ERROR)
        .inFile(annotatedNotesProvider(annotationStrings));
    assertThat(compilation)
        .hadErrorContaining(MULTIPLE_PROVIDERS_ERROR)
        .inFile(secondAnnotatedNotesProvider(annotationStrings));
  }

  @Test
  public void isStaticContainsNoNonStaticMethods_compiles() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileAsAnnotation("isStatic=true"),
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
  public void isStaticContainsNonStaticMethods_hasError() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileAsAnnotation("isStatic=true"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(NOT_STATIC_ERROR, annotationStrings))
        .inFile(notesType);
  }
}
