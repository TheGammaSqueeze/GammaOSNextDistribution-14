package com.google.android.enterprise.connectedapps.processor;

import static com.google.android.enterprise.connectedapps.processor.TestUtilities.INSTALLATION_LISTENER_NAME;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.NOTES_PACKAGE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.SERIALIZABLE_OBJECT;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.installationListener;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.installationListenerSimple;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.installationListenerSimpleWithIntentParam;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.installationListenerSimpleWithStringParam;
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
public class CacheableTest {
  private static final String CACHEABLE_METHOD_RETURNS_VOID_ERROR =
      "Methods annotated with @Cacheable must return a non-void type";
  private static final String CACHEABLE_ANNOTATION_ON_NON_METHOD_ERROR =
      "annotation type not applicable to this kind of declaration";
  private static final String CACHEABLE_METHOD_RETURNS_NON_SERIALIZABLE_ERROR =
      "Methods annotated with @Cacheable must return a type which implements Serializable, return"
          + " a future with a Serializable result or return void with a simple callback parameter.";
  private static final String CACHEABLE_METHOD_NON_SIMPLE_CALLBACK_ERROR =
      "Methods annotated with @Cacheable may only have a callback parameter which is simple.";
  private static final String CACHEABLE_METHOD_USES_INVALID_PARAMETERS_ERROR =
      "Methods annotated with @Cacheable may only use callbacks that take a single Serializable"
          + " parameter.";

  private final AnnotationStrings annotationStrings;

  public CacheableTest(AnnotationStrings annotationStrings) {
    this.annotationStrings = annotationStrings;
  }

  @Parameters(name = "{0}")
  public static Iterable<AnnotationStrings> getAnnotationPrinters() {
    return AnnotationFinder.annotationStrings();
  }

  @Test
  public void validCacheableAnnotation_compiles() {
    JavaFileObject validCacheableMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public int countNotes() {",
            "return 1;",
            "  }",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(validCacheableMethod);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void cacheableAnnotationOnClass_hasError() {
    JavaFileObject validCacheableMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public final class NotesType {",
            "public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(validCacheableMethod);

    assertThat(compilation)
        .hadErrorContaining(CACHEABLE_ANNOTATION_ON_NON_METHOD_ERROR)
        .inFile(validCacheableMethod);
  }

  @Test
  public void cacheableMethodReturnsVoid_hasError() {
    JavaFileObject voidMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public void refreshNotes() {",
            "  }",
            "}");

    Compilation compilation = javac().withProcessors(new Processor()).compile(voidMethod);

    assertThat(compilation)
        .hadErrorContaining(CACHEABLE_METHOD_RETURNS_VOID_ERROR)
        .inFile(voidMethod);
  }

  @Test
  public void cacheableMethodReturnsNonSerializable_hasError() {
    JavaFileObject nonSerializableMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import android.content.Intent;",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public Intent refreshNotes() {",
            "return new Intent();",
            "  }",
            "}");

    Compilation compilation =
        javac().withProcessors(new Processor()).compile(nonSerializableMethod);

    assertThat(compilation)
        .hadErrorContaining(CACHEABLE_METHOD_RETURNS_NON_SERIALIZABLE_ERROR)
        .inFile(nonSerializableMethod);
  }

  @Test
  public void cacheableMethodReturnSerializable_compiles() {
    JavaFileObject serializableReturnMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import android.content.Intent;",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public SerializableObject refreshNotes() {",
            "return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(serializableReturnMethod, SERIALIZABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void cacheableMethodReturnsFutureWithSerializableResult_compiles() {
    JavaFileObject futureWithSerializableReturnMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public ListenableFuture<SerializableObject> refreshNotes() {",
            "return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(futureWithSerializableReturnMethod, SERIALIZABLE_OBJECT);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void cacheableMethodReturnsFutureWithNonSerializableResult_hasError() {
    JavaFileObject futureWithNonSerializableReturnMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import android.content.Intent;",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import com.google.common.util.concurrent.ListenableFuture;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public ListenableFuture<Intent> refreshNotes() {",
            "return null;",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(futureWithNonSerializableReturnMethod, SERIALIZABLE_OBJECT);

    assertThat(compilation)
        .hadErrorContaining(CACHEABLE_METHOD_RETURNS_NON_SERIALIZABLE_ERROR)
        .inFile(futureWithNonSerializableReturnMethod);
  }

  @Test
  public void cacheableMethodHasSimpleCallbackParameter_compiles() {
    JavaFileObject simpleCallbackParameterMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import android.content.Intent;",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public void refreshNotes(" + INSTALLATION_LISTENER_NAME + " a) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                simpleCallbackParameterMethod,
                installationListenerSimpleWithStringParam(annotationStrings));

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void cacheableMethodHasNonSimpleCallbackParameter_hasError() {
    JavaFileObject nonSimpleCallbackParameterMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public void refreshNotes(" + INSTALLATION_LISTENER_NAME + " a) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(nonSimpleCallbackParameterMethod, installationListener(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(CACHEABLE_METHOD_NON_SIMPLE_CALLBACK_ERROR)
        .inFile(nonSimpleCallbackParameterMethod);
  }

  @Test
  public void cacheableMethodHasCallbackWithNonSerializableParameter_hasError() {
    JavaFileObject simpleCallbackParameterMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public void refreshNotes(" + INSTALLATION_LISTENER_NAME + " a) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(
                simpleCallbackParameterMethod,
                installationListenerSimpleWithIntentParam(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(CACHEABLE_METHOD_USES_INVALID_PARAMETERS_ERROR)
        .inFile(simpleCallbackParameterMethod);
  }

  @Test
  public void cacheableMethodHasCallbackWithNoParameters_hasError() {
    JavaFileObject simpleCallbackParameterMethod =
        JavaFileObjects.forSourceLines(
            NOTES_PACKAGE + ".NotesType",
            "package " + NOTES_PACKAGE + ";",
            "import com.google.android.enterprise.connectedapps.annotations.Cacheable;",
            "import " + annotationStrings.crossProfileQualifiedName() + ";",
            "public final class NotesType {",
            annotationStrings.crossProfileAsAnnotation(),
            "@Cacheable",
            "public void refreshNotes(" + INSTALLATION_LISTENER_NAME + " a) {",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(simpleCallbackParameterMethod, installationListenerSimple(annotationStrings));

    assertThat(compilation)
        .hadErrorContaining(CACHEABLE_METHOD_USES_INVALID_PARAMETERS_ERROR)
        .inFile(simpleCallbackParameterMethod);
  }
}
