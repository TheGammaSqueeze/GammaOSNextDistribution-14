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
public class InternalProviderClassTest {

  private final AnnotationPrinter annotationPrinter;

  public InternalProviderClassTest(AnnotationPrinter annotationPrinter) {
    this.annotationPrinter = annotationPrinter;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void generatesInternalProviderClass() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesProvider(annotationPrinter),
                annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation).generatedSourceFile(NOTES_PACKAGE + ".Profile_NotesProvider_Internal");
  }

  @Test
  public void internalProviderClassHasPrivateConstructor() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesProvider(annotationPrinter),
                annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".Profile_NotesProvider_Internal")
        .contentsAsUtf8String()
        .contains("private Profile_NotesProvider_Internal() {");
  }

  @Test
  public void internalProviderClassHasPublicCallMethod() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesProvider(annotationPrinter),
                annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".Profile_NotesProvider_Internal")
        .contentsAsUtf8String()
        .contains(
            "public Bundle call(Context context, long crossProfileTypeIdentifier, int"
                + " methodIdentifier,");
  }

  @Test
  public void internalProviderClassHasInstanceMethod() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesProvider(annotationPrinter),
                annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".Profile_NotesProvider_Internal")
        .contentsAsUtf8String()
        .contains("public static Profile_NotesProvider_Internal instance()");
  }

  @Test
  public void internalProviderClassHasProviderClassGetter() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                annotatedNotesProvider(annotationPrinter),
                annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation)
        .generatedSourceFile(NOTES_PACKAGE + ".Profile_NotesProvider_Internal")
        .contentsAsUtf8String()
        .contains("public NotesProvider providerClass(Context context)");
  }

  @Test
  public void providerClassHasField_compiles() {
    final JavaFileObject providerClass =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesProvider",
            "package " + NOTES_PACKAGE + ";",
            "import " + annotationPrinter.crossProfileProviderQualifiedName() + ";",
            "public final class NotesProvider {",
            "  int test = 3;",
            annotationPrinter.crossProfileProviderAsAnnotation(),
            "  public NotesType provideNotesType() {",
            "    return new NotesType();",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(providerClass, annotatedNotesCrossProfileType(annotationPrinter));

    assertThat(compilation).succeededWithoutWarnings();
  }
}
