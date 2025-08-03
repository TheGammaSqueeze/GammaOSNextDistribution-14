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
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.CUSTOM_WRAPPER;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.GENERIC_PARCELABLE_OBJECT;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.GENERIC_SERIALIZABLE_OBJECT;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.NOTES_PACKAGE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.PARCELABLE_CUSTOM_WRAPPER;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.PROFILE_CONNECTOR_QUALIFIED_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.UNSUPPORTED_TYPE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.UNSUPPORTED_TYPE_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.annotatedNotesProvider;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.formatErrorMessage;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.installationListener;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.installationListenerWithListStringParam;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.installationListenerWithStringParam;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.notesCrossProfileTypeWhichUsesInstallationListener;
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
public class CrossProfileCallbackTest {

  private static final String UNSUPPORTED_PARAMETER_TYPE_ERROR =
      "cannot be used by parameters of methods on interfaces annotated"
          + " @CROSS_PROFILE_CALLBACK_ANNOTATION";
  private static final String CALLBACK_INTERFACE_DEFAULT_PACKAGE_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must not be in the default package";
  private static final String NOT_INTERFACE_ERROR =
      "Only interfaces may be annotated @CROSS_PROFILE_CALLBACK_ANNOTATION";
  private static final String NO_METHODS_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must have at least one method";
  private static final String NOT_ONE_METHOD_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION(simple=true) must have exactly one"
          + " method";
  private static final String DEFAULT_METHOD_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must have no default methods";
  private static final String STATIC_METHOD_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must have no static methods";
  private static final String NOT_VOID_ERROR =
      "Methods on interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION must return void";
  private static final String GENERIC_CALLBACK_INTERFACE_ERROR =
      "Interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION can not be generic";
  private static final String MORE_THAN_ONE_PARAMETER_ERROR =
      "Methods on interfaces annotated @CROSS_PROFILE_CALLBACK_ANNOTATION(simple=true) can only"
          + " take a single parameter";

  private final AnnotationStrings annotationStrings;

  public CrossProfileCallbackTest(AnnotationStrings annotationStrings) {
    this.annotationStrings = annotationStrings;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void crossProfileCallbackInterface_compiles() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "    void installationComplete(int state);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileCallbackInterface_defaultPackage_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            "InstallationListener",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "    void installationComplete(int state);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation)
        .hadErrorContaining(
            formatErrorMessage(CALLBACK_INTERFACE_DEFAULT_PACKAGE_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void crossProfileCallbackInterface_notInterface_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public abstract class InstallationListener {",
            "    abstract void installationComplete(int state);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(NOT_INTERFACE_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void crossProfileCallbackInterface_noMethods_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(NO_METHODS_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void crossProfileCallbackInterface_simple_moreThanOneMethod_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation("simple=true"),
            "public interface InstallationListener {",
            "  abstract void installationComplete(int state);",
            "  abstract void secondMethod(String s);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(NOT_ONE_METHOD_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void crossProfileCallbackInterface_moreThanOneMethod_compiles() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  abstract void installationComplete(int state);",
            "  abstract void secondMethod(String s);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileCallbackInterface_defaultMethod_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "default void defaultMethod() {};",
            "    void installationComplete(int state);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(DEFAULT_METHOD_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void crossProfileCallbackInterface_staticMethod_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "static void staticMethod() {};",
            "    void installationComplete(int state);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(STATIC_METHOD_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void crossProfileCallbackInterface_nonVoidReturnType_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "    int installationComplete(int state);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(NOT_VOID_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void multipleSupportedParameters_simple_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation("simple=true"),
            "public interface InstallationListener {",
            "    void installationComplete(String s, String t);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(MORE_THAN_ONE_PARAMETER_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void multipleSupportedParameters_notSimple_compiles() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "    void installationComplete(String s, String t);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileCallbackInterfaceMethodWithUnsupportedParameterType_notUsed_compiles() {
    // Cross-profile callbacks are only evaluated in the context of a Cross-profile Type
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete(" + UNSUPPORTED_TYPE_NAME + " s);",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(callbackInterface, UNSUPPORTED_TYPE);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void
      crossProfileCallbackInterfaceMethodWithUnsupportedParameterTypeInGeneric_notUsed_compiles() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete(java.util.List<" + UNSUPPORTED_TYPE_NAME + "> s);",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(callbackInterface, UNSUPPORTED_TYPE);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void
      crossProfileCallbackInterfaceMethodWithUnsupportedParameterTypeInGeneric_isUsed_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete(java.util.List<" + UNSUPPORTED_TYPE_NAME + "> s);",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                callbackInterface,
                UNSUPPORTED_TYPE,
                notesCrossProfileTypeWhichUsesInstallationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void crossProfileCallbackInterfaceMethodWithContextParameterTypeInGeneric_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            "import android.content.Context;",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete(java.util.List<Context> s);",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                callbackInterface,
                UNSUPPORTED_TYPE,
                notesCrossProfileTypeWhichUsesInstallationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void genericCrossProfileCallbackMethod_hasError() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener<R> {",
            "  void installationComplete(String s);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(GENERIC_CALLBACK_INTERFACE_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void
      crossProfileCallbackInterfaceWithUnsupportedParameterTypeInGenericParcelable_compiles() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  public void installationComplete(GenericParcelableObject<"
                + UNSUPPORTED_TYPE_NAME
                + "> s);",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(callbackInterface, UNSUPPORTED_TYPE, GENERIC_PARCELABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void
      crossProfileCallbackInterfaceWithUnsupportedParameterTypeInGenericSerializable_compiles() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  public void installationComplete(GenericSerializableObject<"
                + UNSUPPORTED_TYPE_NAME
                + "> s);",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(callbackInterface, UNSUPPORTED_TYPE, GENERIC_SERIALIZABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void
      crossProfileCallbackInterface_hasCrossProfileCallbackInterfaceParameter_notUsed_compiles() {
    JavaFileObject otherCallbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".OtherCallbackInterface",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface OtherCallbackInterface {",
            "  void installationComplete(String s);",
            "}");

    JavaFileObject installationListener =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete(OtherCallbackInterface s);",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(installationListener, otherCallbackInterface);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void
      crossProfileCallbackInterface_hasCrossProfileCallbackInterfaceParameter_isUsed_hasError() {
    JavaFileObject otherCallbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".OtherCallbackInterface",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface OtherCallbackInterface {",
            "  void installationComplete(String s);",
            "}");

    JavaFileObject installationListener =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete(OtherCallbackInterface s);",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                installationListener,
                otherCallbackInterface,
                notesCrossProfileTypeWhichUsesInstallationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(installationListener);
  }

  @Test
  public void crossProfileCallbackInterface_generatesMultiClass() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(installationListener(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.InstallationListener_Multi");
  }

  @Test
  public void crossProfileCallbackInterface_multiClassIncludesNoArgsMethod() {
    JavaFileObject installationListener =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete();",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(installationListener);

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.InstallationListener_Multi")
        .contentsAsUtf8String()
        .contains("void installationComplete();");
  }

  @Test
  public void crossProfileCallbackInterface_multiClassIncludesSingleArgMethod() {
    JavaFileObject installationListener =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete(String s);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(installationListener);

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.InstallationListener_Multi")
        .contentsAsUtf8String()
        .contains("void installationComplete(Map<Profile, String> s);");
  }

  @Test
  public void crossProfileCallbackInterface_generatesReceiverClass() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(installationListener(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.Profile_InstallationListener_Receiver");
  }

  @Test
  public void crossProfileCallbackInterface_receiverClassImplementsOriginalInterface() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(installationListener(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.Profile_InstallationListener_Receiver")
        .contentsAsUtf8String()
        .contains("implements InstallationListener");
  }

  @Test
  public void crossProfileCallbackInterface_generatesSenderClass() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(installationListener(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.Profile_InstallationListener_Sender");
  }

  @Test
  public void crossProfileCallbackInterface_senderClassImplementsLocalCallback() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(installationListener(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.Profile_InstallationListener_Sender")
        .contentsAsUtf8String()
        .contains("implements LocalCallback");
  }

  @Test
  public void crossProfileCallbackInterface_generatesMultiMergerInputClass() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(installationListener(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.Profile_InstallationListener_MultiMergerInput");
  }

  @Test
  public void crossProfileCallbackInterface_multiMergerInputClassImplementsInterface() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(installationListener(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.Profile_InstallationListener_MultiMergerInput")
        .contentsAsUtf8String()
        .contains("implements InstallationListener");
  }

  @Test
  public void crossProfileCallbackInterface_generatesMultiMergerResultClass() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(installationListener(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.Profile_InstallationListener_MultiMergerResult");
  }

  @Test
  public void
      crossProfileCallbackInterface_multiMergerResultClassImplementsCompleteListenerInterface() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(installationListener(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile(
            "com.google.android.enterprise.notes.Profile_InstallationListener_MultiMergerResult")
        .contentsAsUtf8String()
        .contains("implements CrossProfileCallbackMultiMergerCompleteListener<Void>");
  }

  @Test
  public void crossProfileCallbackInterface_withArgumentCompilesSuccessfully() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void doInstall(InstallationListener l) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListener(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void crossProfileCallbackInterface_argumentTypeIsIncludedInBundler() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void doInstall(InstallationListener l) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListenerWithStringParam(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.NotesType_Bundler")
        .contentsAsUtf8String()
        .contains(".writeString");
  }

  @Test
  public void
      crossProfileCallbackInterfaceWithMultipleMethods_allArgumentTypesAreIncludedInBundler() {
    JavaFileObject installationListener =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete(String s, Float f);",
            "  void installationFailed(Boolean b, Byte p);",
            "}");

    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void doInstall(InstallationListener l) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationStrings), installationListener);

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.NotesType_Bundler")
        .contentsAsUtf8String()
        .contains(".writeString");

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.NotesType_Bundler")
        .contentsAsUtf8String()
        .contains(".writeFloat");

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.NotesType_Bundler")
        .contentsAsUtf8String()
        .contains(".writeInt"); // used for Boolean

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.NotesType_Bundler")
        .contentsAsUtf8String()
        .contains(".writeByte");
  }

  @Test
  public void crossProfileCallbackInterface_wrappedArgumentTypeIsIncludedInBundler() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void doInstall(InstallationListener l) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                notesType,
                annotatedNotesProvider(annotationStrings),
                installationListenerWithListStringParam(annotationStrings));

    assertThat(compilation)
        .generatedSourceFile("com.google.android.enterprise.notes.NotesType_Bundler")
        .contentsAsUtf8String()
        .contains("ParcelableList");
  }

  @Test
  public void
      crossProfileCallbackInterfaceMethodWithCustomParcelableWrapperParameterType_compiles() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation(),
            "public interface InstallationListener {",
            "  void installationComplete(CustomWrapper s);",
            "}");

    JavaFileObject crossProfileType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(
                "connector=CrossProfileConnectorWhichSupportsCustomWrapper.class"),
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "  public void install(InstallationListener l) {",
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
                callbackInterface,
                CUSTOM_WRAPPER,
                PARCELABLE_CUSTOM_WRAPPER,
                crossProfileType,
                connector);

    assertThat(compilation)
        .hadErrorContaining(formatErrorMessage(UNSUPPORTED_PARAMETER_TYPE_ERROR, annotationStrings))
        .inFile(callbackInterface);
  }

  @Test
  public void interfaceMarkedSimple_isSimple_compiles() {
    JavaFileObject callbackInterface =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".InstallationListener",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationStrings.crossProfileCallbackQualifiedName() + ";",
            annotationStrings.crossProfileCallbackAsAnnotation("simple=true"),
            "public interface InstallationListener {",
            "  void installationComplete(String s);",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(callbackInterface);

    assertThat(compilation).succeededWithoutWarnings();
  }
}
