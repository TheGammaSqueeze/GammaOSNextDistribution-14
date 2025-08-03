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

import static com.google.android.enterprise.connectedapps.processor.TestUtilities.CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.NOTES_PACKAGE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.PROFILE_CONNECTOR_QUALIFIED_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.UNCAUGHT_EXCEPTIONS_POLICY_QUALIFIED_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesConfigurationWithNotesProvider;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesCrossProfileType;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesProvider;
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
public class DispatcherTest {

  private final AnnotationPrinter annotationPrinter;
  private final JavaFileObject notesConfiguration;
  private final JavaFileObject notesCrossProfileType;

  public DispatcherTest(AnnotationPrinter annotationPrinter) {
    this.annotationPrinter = annotationPrinter;
    this.notesConfiguration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileConfigurationQualifiedName() + ";",
            annotationPrinter.crossProfileConfigurationAsAnnotation(
                "providers=NotesProvider.class"),
            "public abstract class NotesConfiguration {",
            "}");
    this.notesCrossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            annotationPrinter.crossProfileAsAnnotation("connector=NotesConnector.class"),
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void generatesDispatcherClass() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesConfigurationWithNotesProvider(annotationPrinter),
                annotatedNotesProvider(annotationPrinter),
                annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.connectedapps.CrossProfileConnector_Service_Dispatcher");
  }

  @Test
  public void specifiedClassName_generatesSpecifiedClassNameDispatcher() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesConfiguration,
                annotatedNotesProvider(annotationPrinter),
                buildNotesConnector("serviceClassName=\"com.google.android.CustomConnector\""),
                notesCrossProfileType);

    assertThat(compilation).generatedSourceFile("com.google.android.CustomConnector_Dispatcher");
  }

  @Test
  public void whenExceptionsPolicyIsUnspecified_generatesClassWithRethrowCode() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesConfiguration,
                annotatedNotesProvider(annotationPrinter),
                buildNotesConnector(""),
                notesCrossProfileType);

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.NotesConnector_Service_Dispatcher")
        .contentsAsUtf8String()
        .contains("delayThrow");
  }

  @Test
  public void whenExceptionsPolicyIsRethrow_generatesClassWithRethrowCode() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesConfiguration,
                annotatedNotesProvider(annotationPrinter),
                buildNotesConnector(
                    "uncaughtExceptionsPolicy=UncaughtExceptionsPolicy.NOTIFY_RETHROW"),
                notesCrossProfileType);

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.NotesConnector_Service_Dispatcher")
        .contentsAsUtf8String()
        .contains("delayThrow");
  }

  @Test
  public void whenExceptionsPolicyIsSuppress_generatesClassWithoutRethrowCode() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesConfiguration,
                annotatedNotesProvider(annotationPrinter),
                buildNotesConnector(
                    "uncaughtExceptionsPolicy=UncaughtExceptionsPolicy.NOTIFY_SUPPRESS"),
                notesCrossProfileType);

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.NotesConnector_Service_Dispatcher")
        .contentsAsUtf8String()
        .doesNotContain("delayThrow");
  }

  private static JavaFileObject buildNotesConnector(String customProfileConnectorParams) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".NotesConnector",
        "package " + NOTES_PACKAGE + ";",
        "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
        "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
        "import " + UNCAUGHT_EXCEPTIONS_POLICY_QUALIFIED_NAME + ";",
        String.format("@CustomProfileConnector(%s)", customProfileConnectorParams),
        "public interface NotesConnector extends ProfileConnector {",
        "}");
  }
}
