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
public class ProfileInterfaceTest {

  private final AnnotationPrinter annotationPrinter;

  public ProfileInterfaceTest(AnnotationPrinter annotationPrinter) {
    this.annotationPrinter = annotationPrinter;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void compile_generatesProfileInterface() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesProvider(annotationPrinter),
                annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation).generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType");
  }

  @Test
  public void compile_profileInterfaceHasCreateMethod() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesProvider(annotationPrinter),
                annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("static ProfileNotesType create(ProfileConnector connector)");
  }

  @Test
  public void compile_profileInterfaceContainsExpectedMethods() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesProvider(annotationPrinter),
                annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_SingleSender current()");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_SingleSenderCanThrow other()");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        // We ignore the "profile" argument as it gets moved onto another line by the processor
        .contains("NotesType_SingleSenderCanThrow profile(");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_MultipleSender both()");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_MultipleSender profiles(");
  }

  @Test
  public void
      compile_withoutPrimaryProfile_profileInterfaceDoesNotContainPrimarySecondaryMethods() {
    JavaFileObject notesType =
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
    JavaFileObject notesConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "public interface NotesConnector extends ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter), notesConnector);

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .doesNotContain("primary()");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .doesNotContain("secondary()");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .doesNotContain("suppliers()");
  }

  @Test
  public void compile_withPrimaryProfile_profileInterfaceDoesContainPrimarySecondaryMethods() {
    JavaFileObject notesType =
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
    JavaFileObject notesConnector =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesConnector",
            "package " + NOTES_PACKAGE + ";",
            "import " + CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
            "@CustomProfileConnector(primaryProfile=CustomProfileConnector.ProfileType.WORK)",
            "public interface NotesConnector extends ProfileConnector {",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(notesType, annotatedNotesProvider(annotationPrinter), notesConnector);

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_SingleSenderCanThrow primary()");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_SingleSenderCanThrow secondary()");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_MultipleSender suppliers()");
  }

  @Test
  public void compile_withoutConnector_profileInterfaceDoesContainPrimarySecondaryMethods() {
    JavaFileObject notesType =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileQualifiedName() + ";",
            annotationPrinter.crossProfileAsAnnotation(),
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
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_SingleSenderCanThrow primary()");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_SingleSenderCanThrow secondary()");
    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".ProfileNotesType")
        .contentsAsUtf8String()
        .contains("NotesType_MultipleSender suppliers()");
  }
}
