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

import static com.google.android.enterprise.connectedapps.processor.TestUtilities.GENERATED_USER_CONNECTOR_QUALIFIED_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.NOTES_PACKAGE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.USER_CONNECTOR_QUALIFIED_NAME;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GeneratedUserConnectorTest {

  private static final String NOT_INTERFACE_ERROR =
      "@GeneratedUserConnector must only be applied to interfaces";
  private static final String NOT_IMPLEMENTING_CONNECTOR_ERROR =
      "Interfaces annotated with @GeneratedUserConnector must extend UserConnector";
  private static final String ADDITIONAL_METHODS_ERROR =
      "Interfaces annotated with @GeneratedUserConnector can not declare non-static methods";

  @Test
  public void isNotInterface_hasError() {
    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + GENERATED_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@GeneratedUserConnector",
            "public class NotesConnector implements UserConnector {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation).hadErrorContaining(NOT_INTERFACE_ERROR).inFile(connector);
  }

  @Test
  public void doesNotExtendProfileConnector_hasError() {
    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + GENERATED_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@GeneratedUserConnector",
            "public interface NotesConnector {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation)
        .hadErrorContaining(NOT_IMPLEMENTING_CONNECTOR_ERROR)
        .inFile(connector);
  }

  @Test
  public void addsAdditionalMethod_hasError() {
    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + GENERATED_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@GeneratedUserConnector",
            "public interface NotesConnector extends UserConnector {",
            "    String getString();",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation)
        .hadErrorContaining(ADDITIONAL_METHODS_ERROR)
        .inFile(connector);
  }

  @Test
  public void generatedUserConnector_compiles() {
    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + GENERATED_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@GeneratedUserConnector",
            "public interface NotesConnector extends UserConnector {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation).generatedSourceFile(NOTES_PACKAGE + ".GeneratedNotesConnector");
  }

  @Test
  public void generatedUserConnector_extendsAbstractUserConnector() {
    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + GENERATED_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@GeneratedUserConnector",
            "public interface NotesConnector extends UserConnector {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".GeneratedNotesConnector")
        .contentsAsUtf8String()
        .contains("final class GeneratedNotesConnector extends AbstractUserConnector");
  }

  @Test
  public void generatedUserConnector_implementsConnector() {
    JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + GENERATED_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@GeneratedUserConnector",
            "public interface NotesConnector extends UserConnector {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".GeneratedNotesConnector")
        .contentsAsUtf8String()
        .contains("implements NotesConnector");
  }
}
