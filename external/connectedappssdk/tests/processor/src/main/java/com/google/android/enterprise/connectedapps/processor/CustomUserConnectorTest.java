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

import static com.google.android.enterprise.connectedapps.processor.TestUtilities.CUSTOM_USER_CONNECTOR_QUALIFIED_NAME;
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
public class CustomUserConnectorTest {

  private static final String NOT_INTERFACE_ERROR =
      "@CustomUserConnector must only be applied to interfaces";
  private static final String NOT_IMPLEMENTING_CONNECTOR_ERROR =
      "Interfaces annotated with @CustomUserConnector must extend UserConnector";
  private static final String PARCELABLE_WRAPPER_ANNOTATION_ERROR =
      "Parcelable Wrappers must be annotated @CustomParcelableWrapper";
  private static final String FUTURE_WRAPPER_ANNOTATION_ERROR =
      "Future Wrappers must be annotated @CustomFutureWrapper";
  private static final String IMPORTS_NOT_CONNECTOR_ERROR =
      "Classes included in includes= must be annotated @CustomUserConnector";

  @Test
  public void isNotInterface_hasError() {
    final JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + CUSTOM_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomUserConnector",
            "public class NotesConnector {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation).hadErrorContaining(NOT_INTERFACE_ERROR).inFile(connector);
  }

  @Test
  public void doesNotExtendUserConnector_hasError() {
    final JavaFileObject notImplementingBaseConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + CUSTOM_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomUserConnector",
            "public interface NotesConnector {",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(notImplementingBaseConnector);

    assertThat(compilation)
        .hadErrorContaining(NOT_IMPLEMENTING_CONNECTOR_ERROR)
        .inFile(notImplementingBaseConnector);
  }

  @Test
  public void includesNonParcelableWrapperInParcelableWrappers_hasError() {
    final JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + CUSTOM_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomUserConnector(parcelableWrappers=String.class)",
            "public interface NotesConnector extends UserConnector {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation)
        .hadErrorContaining(PARCELABLE_WRAPPER_ANNOTATION_ERROR)
        .inFile(connector);
  }

  @Test
  public void includesNonFutureWrapperInFutureWrappers_hasError() {
    final JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + CUSTOM_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomUserConnector(futureWrappers=String.class)",
            "public interface NotesConnector extends UserConnector {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation).hadErrorContaining(FUTURE_WRAPPER_ANNOTATION_ERROR).inFile(connector);
  }

  @Test
  public void imports_containsNonUserConnector_hasError() {
    final JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + CUSTOM_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomUserConnector(imports=String.class)",
            "public interface NotesConnector extends UserConnector {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(connector);

    assertThat(compilation).hadErrorContaining(IMPORTS_NOT_CONNECTOR_ERROR).inFile(connector);
  }

  @Test
  public void imports_containsUserConnector_compiles() {
    final JavaFileObject connector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + CUSTOM_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomUserConnector(imports=NotesConnector2.class)",
            "public interface NotesConnector extends UserConnector {",
            "}");
    final JavaFileObject connector2 =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector2",
            "package " + NOTES_PACKAGE + ";",
            "import " + CUSTOM_USER_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + USER_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomUserConnector",
            "public interface NotesConnector2 extends UserConnector {",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(connector, connector2);

    assertThat(compilation).succeededWithoutWarnings();
  }
}
