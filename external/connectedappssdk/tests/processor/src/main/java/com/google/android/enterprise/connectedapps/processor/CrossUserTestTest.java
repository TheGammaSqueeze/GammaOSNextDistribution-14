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
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesConfigurationWithNotesProviderAndCrossUserConnector;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesProvider;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.notesTypeWithDefaultConnector;
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
public class CrossUserTestTest {

  private final AnnotationPrinter annotationPrinter;

  public CrossUserTestTest(AnnotationPrinter annotationPrinter) {
    this.annotationPrinter = annotationPrinter;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void generatesFakeCrossUserConnector() {
    JavaFileObject crossUserTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileTestQualifiedName() + ";",
            annotationPrinter.crossProfileTestAsAnnotation(
                "configuration=NotesConfiguration.class"),
            "public final class NotesTest {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossUserTest,
                annotatedNotesConfigurationWithNotesProviderAndCrossUserConnector(
                    annotationPrinter),
                annotatedNotesProvider(annotationPrinter),
                notesTypeWithDefaultConnector(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.connectedapps.FakeCrossUserConnector");
  }

  @Test
  public void fakeCrossUserConnector_extendsAbstractFakeUserConnector() {
    JavaFileObject crossUserTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileTestQualifiedName() + ";",
            annotationPrinter.crossProfileTestAsAnnotation(
                "configuration=NotesConfiguration.class"),
            "public final class NotesTest {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossUserTest,
                annotatedNotesConfigurationWithNotesProviderAndCrossUserConnector(
                    annotationPrinter),
                annotatedNotesProvider(annotationPrinter),
                notesTypeWithDefaultConnector(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.connectedapps.FakeCrossUserConnector")
        .contentsAsUtf8String()
        .contains("extends AbstractFakeUserConnector");
  }

  @Test
  public void fakeCrossUserConnector_implementsConnector() {
    JavaFileObject crossUserTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileTestQualifiedName() + ";",
            annotationPrinter.crossProfileTestAsAnnotation(
                "configuration=NotesConfiguration.class"),
            "public final class NotesTest {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossUserTest,
                annotatedNotesConfigurationWithNotesProviderAndCrossUserConnector(
                    annotationPrinter),
                annotatedNotesProvider(annotationPrinter),
                notesTypeWithDefaultConnector(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.connectedapps.FakeCrossUserConnector")
        .contentsAsUtf8String()
        .contains("implements CrossUserConnector");
  }

  @Test
  public void generatesFakeCrossUserClass() {
    JavaFileObject crossUserTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileTestQualifiedName() + ";",
            annotationPrinter.crossProfileTestAsAnnotation(
                "configuration=NotesConfiguration.class"),
            "public final class NotesTest {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossUserTest,
                annotatedNotesConfigurationWithNotesProviderAndCrossUserConnector(
                    annotationPrinter),
                annotatedNotesProvider(annotationPrinter),
                notesTypeWithDefaultConnector(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".FakeUserNotesType")
        .contentsAsUtf8String()
        .contains("class FakeUserNotesType implements UserNotesType");
  }
}
