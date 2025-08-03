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
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesConfigurationWithNotesProvider;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesProvider;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.formatErrorMessage;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.notesTypeWithDefaultConnector;
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
public class CrossProfileTestTest {

  private static final String NOT_A_CONFIGURATION_ERROR =
      "Configurations referenced in a @CROSS_PROFILE_TEST_ANNOTATION annotation must be annotated"
          + " @CROSS_PROFILE_CONFIGURATION_ANNOTATION or @CROSS_PROFILE_CONFIGURATIONS_ANNOTATION";

  private final AnnotationStrings annotationStrings;

  public CrossProfileTestTest(AnnotationStrings annotationStrings) {
    this.annotationStrings = annotationStrings;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void crossProfileTest_compiles() {
    JavaFileObject crossProfileTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileTestQualifiedName() + ";",
            annotationStrings.crossProfileTestAsAnnotation(
                "configuration=NotesConfiguration.class"),
            "public final class NotesTest {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileTest,
                annotatedNotesConfigurationWithNotesProvider(annotationStrings),
                annotatedNotesProvider(annotationStrings),
                notesTypeWithDefaultConnector(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileTest_referencesNonConfigurationClass_hasError() {
    JavaFileObject crossProfileTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileTestQualifiedName() + ";",
            annotationStrings.crossProfileTestAsAnnotation("configuration=String.class"),
            "public final class NotesTest {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(crossProfileTest);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(NOT_A_CONFIGURATION_ERROR, annotationStrings))
        .inFile(crossProfileTest);
  }

  @Test
  public void crossProfileTest_generatesFakeConnector() {
    JavaFileObject crossProfileTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileTestQualifiedName() + ";",
            annotationStrings.crossProfileTestAsAnnotation(
                "configuration=NotesConfiguration.class"),
            "public final class NotesTest {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileTest,
                annotatedNotesConfigurationWithNotesProvider(annotationStrings),
                annotatedNotesProvider(annotationStrings),
                notesTypeWithDefaultConnector(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.connectedapps.FakeCrossProfileConnector");
  }

  @Test
  public void crossProfileTest_fakeConnectorImplementsConnector() {
    JavaFileObject crossProfileTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileTestQualifiedName() + ";",
            annotationStrings.crossProfileTestAsAnnotation(
                "configuration=NotesConfiguration.class"),
            "public final class NotesTest {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileTest,
                annotatedNotesConfigurationWithNotesProvider(annotationStrings),
                annotatedNotesProvider(annotationStrings),
                notesTypeWithDefaultConnector(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.connectedapps.FakeCrossProfileConnector")
        .contentsAsUtf8String()
        .contains("implements CrossProfileConnector");
  }

  @Test
  public void
      crossProfileTest_connectorDoesNotSpecifyPrimaryProfile_fakeConnectorHasConstructorToSpecifyProfile() {
    JavaFileObject crossProfileTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileTestQualifiedName() + ";",
            annotationStrings.crossProfileTestAsAnnotation(
                "configuration=NotesConfiguration.class"),
            "public final class NotesTest {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                crossProfileTest,
                annotatedNotesConfigurationWithNotesProvider(annotationStrings),
                annotatedNotesProvider(annotationStrings),
                notesTypeWithDefaultConnector(annotationStrings));

    // We can't assert multi-line methods, so we check just that we have a constructer with an
    // additional param
    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.connectedapps.FakeCrossProfileConnector")
        .contentsAsUtf8String()
        .contains("public FakeCrossProfileConnector(Context context,");
  }

  @Test
  public void
      crossProfileTest_connectorSpecifiesPrimaryProfile_fakeConnectorDoesNotHaveConstructorToSpecifyProfile() {
    JavaFileObject crossProfileTest =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesTest",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileTestQualifiedName() + ";",
            annotationStrings.crossProfileTestAsAnnotation(
                "configuration=NotesConfiguration.class"),
            "public final class NotesTest {",
            "}");
    JavaFileObject notesConfiguration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "connector=NotesConnector.class"),
            "public abstract class NotesConfiguration {",
            "}");
    JavaFileObject notesConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(primaryProfile=CustomProfileConnector.ProfileType.WORK)",
            "public interface NotesConnector extends ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(crossProfileTest, notesConfiguration, notesConnector);

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.FakeNotesConnector")
        .contentsAsUtf8String()
        .doesNotContain("CustomProfileConnector.ProfileType primaryProfile");
  }
}
