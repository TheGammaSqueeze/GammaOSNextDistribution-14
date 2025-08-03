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
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedDifferentCrossProfileType;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedDifferentProvider;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesCrossProfileType;
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
public class ProcessorCrossProfileConfigurationTest {

  private static final String NOT_A_PROVIDER_CLASS_ERROR =
      "All classes specified in 'providers' must be provider classes";
  private static final String CONFIGURATION_DIFFERENT_CONNECTOR_ERROR =
      "All @CROSS_PROFILE_ANNOTATION types specified in the same configuration must use the same"
          + " ProfileConnector";
  private static final String INCORRECT_SERVICE_CLASS =
      "The class specified by serviceClass must match the serviceClassName given by the"
          + " ProfileConnector";
  private static final String CONNECTOR_MUST_BE_INTERFACE = "Connectors must be interfaces";
  private static final String CONNECTOR_MUST_EXTEND_CONNECTOR =
      "Interfaces specified as a connector must extend ProfileConnector or UserConnector";

  private final AnnotationStrings annotationStrings;

  public ProcessorCrossProfileConfigurationTest(AnnotationStrings annotationStrings) {
    this.annotationStrings = annotationStrings;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void multipleConfigurations_compiles() {
    final JavaFileObject configuration1 =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "connector=CrossProfileConnector.class"),
            "public abstract class NotesConfiguration {",
            "}");
    final JavaFileObject configuration2 =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration2",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "connector=NotesConnector.class"),
            "public abstract class NotesConfiguration2 {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(configuration1, configuration2, ANNOTATED_NOTES_CONNECTOR);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void providersContainsNoProviders_compiles() {
    final JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "connector=CrossProfileConnector.class"),
            "public abstract class NotesConfiguration {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(configuration);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void providersContainsNoProvidersAndNoConnector_generatesCrossProfileConnectorService() {
    final JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            annotationStrings.crossProfileConfigurationAsAnnotation(),
            "public abstract class NotesConfiguration {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(configuration);

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.connectedapps.CrossProfileConnector_Service");
  }

  @Test
  public void
      providersContainsProviderWithoutConnectorAndNoConnector_generatesCrossProfileConnectorService() {
    JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "providers=NotesProvider.class"),
            "public abstract class NotesConfiguration {",
            "}");
    JavaFileObject noConnectorProvider =
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
    JavaFileObject notesTypeWithoutConnector =
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
            .compile(configuration, noConnectorProvider, notesTypeWithoutConnector);

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.connectedapps.CrossProfileConnector_Service");
  }

  @Test
  public void providersContainsSingleValidProvider_compiles() {
    JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "providers=NotesProvider.class"),
            "public abstract class NotesConfiguration {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                configuration,
                annotatedNotesProvider(annotationStrings),
                annotatedNotesCrossProfileType(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void providersContainsMultipleValidProviders_compiles() {
    JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "providers={NotesProvider.class, DifferentProvider.class}"),
            "public abstract class NotesConfiguration {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                configuration,
                annotatedNotesProvider(annotationStrings),
                annotatedNotesCrossProfileType(annotationStrings),
                annotatedDifferentCrossProfileType(annotationStrings),
                annotatedDifferentProvider(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void providersContainsNonProvider_hasError() {
    JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            annotationStrings.crossProfileConfigurationAsAnnotation("providers={String.class}"),
            "public abstract class NotesConfiguration {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(configuration);

    assertThat(compilation).hadErrorContaining(NOT_A_PROVIDER_CLASS_ERROR).inFile(configuration);
  }

  @Test
  public void isNotAbstract_compiles() {
    JavaFileObject notAbstractConfiguration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "connector=CrossProfileConnector.class"),
            "public class NotesConfiguration {",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(notAbstractConfiguration);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void hasCrossProfileTypeWithDifferentConnectors_hasError() {
    JavaFileObject configurationClassWithDifferentConnectors =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "providers={NotesProvider.class, NotesProvider2.class}"),
            "abstract class NotesConfiguration {",
            "}");
    JavaFileObject providerClassWithDefaultConnector =
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
    JavaFileObject providerClassWithNotesConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider2",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider2 {",
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
                configurationClassWithDifferentConnectors,
                providerClassWithDefaultConnector,
                providerClassWithNotesConnector,
                notesTypeWithCrossProfileConnector,
                notesType2WithNotesConnector,
                ANNOTATED_NOTES_CONNECTOR);

    assertThat(compilation)
        .hadErrorContaining(
            formatErrorMessage(CONFIGURATION_DIFFERENT_CONNECTOR_ERROR, annotationStrings))
        .inFile(configurationClassWithDifferentConnectors);
  }

  @Test
  public void correctServiceClass_compiles() {
    JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "serviceClass=com.google.android.enterprise.connectedapps.CrossProfileConnector_Service.class,"
                    + " providers=NotesProvider.class, connector=CrossProfileConnector.class"),
            "public abstract class NotesConfiguration {",
            "}");
    JavaFileObject serviceClass =
        JavaFileObjects.forSourceLines(
            "com.google.android.enterprise.connectedapps.CrossProfileConnector_Service",
            "package com.google.android.enterprise.connectedapps;",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            "import android.app.Service;",
            "import android.content.Intent;",
            "import android.os.Binder;",
            "public final class CrossProfileConnector_Service extends Service {",
            "  @Override",
            "  public Binder onBind(Intent intent) {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                configuration,
                serviceClass,
                annotatedNotesProvider(annotationStrings),
                notesTypeWithDefaultConnector(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void incorrectlyNamedServiceClass_hasError() {
    JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "serviceClass=com.google.android.enterprise.connectedapps.CrossProfileConnector_Service2.class,"
                    + " providers=NotesProvider.class, connector=CrossProfileConnector.class"),
            "public abstract class NotesConfiguration {",
            "}");
    JavaFileObject serviceClass =
        JavaFileObjects.forSourceLines(
            "com.google.android.enterprise.connectedapps.CrossProfileConnector_Service2",
            "package com.google.android.enterprise.connectedapps;",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            "import android.app.Service;",
            "import android.content.Intent;",
            "import android.os.Binder;",
            "public final class CrossProfileConnector_Service2 extends Service {",
            "  @Override",
            "  public Binder onBind(Intent intent) {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                configuration,
                serviceClass,
                annotatedNotesProvider(annotationStrings),
                notesTypeWithDefaultConnector(annotationStrings));

    assertThat(compilation).hadErrorContaining(INCORRECT_SERVICE_CLASS).inFile(configuration);
  }

  @Test
  public void specifiesNotInterfaceConnector_hasError() {
    final JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "connector=NotesConnector.class"),
            "public abstract class NotesConfiguration {",
            "}");

    JavaFileObject notesConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "public class NotesConnector {",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(configuration, notesConnector);

    assertThat(compilation).hadErrorContaining(CONNECTOR_MUST_BE_INTERFACE).inFile(configuration);
  }

  @Test
  public void specifiesConnectorNotExtendingConnectorInterface_hasError() {
    final JavaFileObject configuration =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConfiguration",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileConfigurationQualifiedName() + ";",
            annotationStrings.crossProfileConfigurationAsAnnotation(
                "connector=NotesConnector.class"),
            "public abstract class NotesConfiguration {",
            "}");

    JavaFileObject notesConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "public interface NotesConnector {",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(configuration, notesConnector);

    assertThat(compilation)
        .hadErrorContaining(CONNECTOR_MUST_EXTEND_CONNECTOR)
        .inFile(configuration);
  }
}
