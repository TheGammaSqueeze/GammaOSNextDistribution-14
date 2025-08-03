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
public class InterfaceTest {

  private final AnnotationPrinter annotationPrinter;

  public InterfaceTest(AnnotationPrinter annotationPrinter) {
    this.annotationPrinter = annotationPrinter;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void compile_generatesSingleSenderInterface() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesCrossProfileType(annotationPrinter),
                annotatedNotesProvider(annotationPrinter));

    assertThat(compilation).generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender");
  }

  @Test
  public void compile_singleAnnotatedMethod_singleSenderInterfaceHasMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("void refreshNotes()");
  }

  @Test
  public void compile_multipleAnnotatedMethods_singleSenderInterfaceHasAllMethods() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public int anotherMethod(String s) {",
            "    return 0;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("void refreshNotes()");

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("int anotherMethod(String s)");
  }

  @Test
  public void compile_multipleMethods_singleSenderInterfaceDoesNotHaveUnannotatedMethods() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "  public int anotherMethod(String s) {",
            "    return 0;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .doesNotContain("anotherMethod");
  }

  @Test
  public void compile_generatesSingleSenderCanThrowInterface() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesCrossProfileType(annotationPrinter),
                annotatedNotesProvider(annotationPrinter));

    assertThat(compilation).generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSenderCanThrow");
  }

  @Test
  public void compile_singleAnnotatedMethod_singleSenderCanThrowInterfaceHasMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSenderCanThrow")
        .contentsAsUtf8String()
        .contains("void refreshNotes() throws UnavailableProfileException");
  }

  @Test
  public void compile_multipleAnnotatedMethods_singleSenderCanThrowInterfaceHasAllMethods() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public int anotherMethod(String s) {",
            "    return 0;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSenderCanThrow")
        .contentsAsUtf8String()
        .contains("void refreshNotes() throws UnavailableProfileException");

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSenderCanThrow")
        .contentsAsUtf8String()
        .contains("int anotherMethod(String s) throws UnavailableProfileException");
  }

  @Test
  public void compile_multipleMethods_singleSenderCanThrowInterfaceDoesNotHaveUnannotatedMethods() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "  public int anotherMethod(String s) {",
            "    return 0;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSenderCanThrow")
        .contentsAsUtf8String()
        .doesNotContain("anotherMethod");
  }

  @Test
  public void compile_singleSenderCanThrowInterfaceHasIfAvailableMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSenderCanThrow")
        .contentsAsUtf8String()
        .contains("NotesType_IfAvailable ifAvailable()");
  }

  @Test
  public void compile_generatesMultipleSenderInterface() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesCrossProfileType(annotationPrinter),
                annotatedNotesProvider(annotationPrinter));

    assertThat(compilation).generatedSourceFile(NOTES_PACKAGE + ".NotesType_MultipleSender");
  }

  @Test
  public void compile_singleVoidAnnotatedMethod_multipleSenderInterfaceHasMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_MultipleSender")
        .contentsAsUtf8String()
        .contains("void refreshNotes()");
  }

  @Test
  public void compile_singlePrimitiveAnnotatedMethod_multipleSenderInterfaceHasMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public int refreshNotes() {",
            "    return 0;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_MultipleSender")
        .contentsAsUtf8String()
        .contains("Map<Profile, Integer> refreshNotes()");
  }

  @Test
  public void compile_singleObjectAnnotatedMethod_multipleSenderInterfaceHasMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public String refreshNotes() {",
            "    return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_MultipleSender")
        .contentsAsUtf8String()
        .contains("Map<Profile, String> refreshNotes()");
  }

  @Test
  public void compile_multipleAnnotatedMethods_multipleSenderInterfaceHasAllMethods() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public int anotherMethod(String s) {",
            "    return 0;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_MultipleSender")
        .contentsAsUtf8String()
        .contains("void refreshNotes()");

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_MultipleSender")
        .contentsAsUtf8String()
        .contains("Map<Profile, Integer> anotherMethod(String s)");
  }

  @Test
  public void compile_multipleMethods_multipleSenderInterfaceDoesNotHaveUnannotatedMethods() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() {",
            "  }",
            "  public int anotherMethod(String s) {",
            "    return 0;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_MultipleSender")
        .contentsAsUtf8String()
        .doesNotContain("anotherMethod");
  }

  @Test
  public void compile_synchronousMethodWithDeclaredException_singleSenderInterfaceHasMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "import java.io.IOException;",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() throws IOException {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("refreshNotes() throws IOException");
  }

  @Test
  public void
      compile_synchronousMethodWithMultipleDeclaredExceptions_singleSenderInterfaceHasMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "import java.io.IOException;",
            "import java.sql.SQLException;",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() throws IOException, SQLException {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    // Order is not predictable
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("refreshNotes() throws ");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("SQLException");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("IOException");
  }

  @Test
  public void
      compile_synchronousMethodWithDeclaredException_singleSenderCanThrowInterfaceHasMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "import java.io.IOException;",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() throws IOException {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("refreshNotes() throws IOException");
  }

  @Test
  public void
      compile_synchronousMethodWithMultipleDeclaredExceptions_singleSenderCanThrowInterfaceHasMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "import java.io.IOException;",
            "import java.sql.SQLException;",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() throws IOException, SQLException {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    // Order is not predictable
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("refreshNotes() throws ");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("SQLException");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_SingleSender")
        .contentsAsUtf8String()
        .contains("IOException");
  }

  @Test
  public void
      compile_synchronousMethodWithDeclaredException_multipleSenderInterfaceDoesNotHaveMethod() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            "import java.io.IOException;",
            "public final class NotesType {",
            annotationPrinter.crossProfileAsAnnotation(),
            "  public void refreshNotes() throws IOException {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".NotesType_MultipleSender")
        .contentsAsUtf8String()
        .doesNotContain("refreshNotes()");
  }
}
