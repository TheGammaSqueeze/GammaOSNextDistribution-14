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

import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationNames;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationPrinter;
import com.google.testing.compile.JavaFileObjects;
import java.util.ArrayList;
import java.util.List;
import javax.tools.JavaFileObject;

final class TestUtilities {

  public static final String CROSS_PROFILE_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.annotations.CrossProfile";
  public static final String CUSTOM_PROFILE_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector";
  public static final String CUSTOM_USER_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.annotations.CustomUserConnector";
  public static final String GENERATED_PROFILE_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.annotations.GeneratedProfileConnector";
  public static final String UNCAUGHT_EXCEPTIONS_POLICY_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.annotations.UncaughtExceptionsPolicy";
  public static final String GENERATED_USER_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.annotations.GeneratedUserConnector";
  public static final String PROFILE_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.ProfileConnector";
  public static final String USER_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.UserConnector";
  public static final String NOTES_PACKAGE = "com.google.android.enterprise.notes";

  public static final String UNSUPPORTED_TYPE_NAME = "UnsupportedType";
  public static final JavaFileObject UNSUPPORTED_TYPE =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".UnsupportedType",
          "package " + NOTES_PACKAGE + ";",
          "public final class UnsupportedType {}");

  public static final JavaFileObject ANNOTATED_NOTES_CONNECTOR =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".NotesConnector",
          "package " + NOTES_PACKAGE + ";",
          "import " + PROFILE_CONNECTOR_QUALIFIED_NAME + ";",
          "public interface NotesConnector extends ProfileConnector {",
          "}");

  public static final String INSTALLATION_LISTENER_NAME = "InstallationListener";

  public static final JavaFileObject PARCELABLE_OBJECT =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableObject",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import java.util.Objects;",
          "public final class ParcelableObject implements Parcelable {",
          "@SuppressWarnings(\"rawtypes\")",
          "public static final Parcelable.Creator CREATOR =",
          "new Parcelable.Creator() {",
          "@Override",
          "public ParcelableObject createFromParcel(Parcel in) {",
          "return new ParcelableObject(in);",
          "}",
          "@Override",
          "public ParcelableObject[] newArray(int size) {",
          "return new ParcelableObject[size];",
          "}",
          "};",
          "private String value;",
          "public String value() {",
          "return value;",
          "}",
          "public ParcelableObject(Parcel in) {",
          "this(in.readString());",
          "}",
          "public ParcelableObject(String value) {",
          "this.value = value;",
          "}",
          "@Override",
          "public int describeContents() {",
          "return 0;",
          "}",
          "@Override",
          "public void writeToParcel(Parcel dest, int flags) {",
          "dest.writeString(value);",
          "}",
          "@Override",
          "public boolean equals(Object o) {",
          "if (this == o) {",
          "return true;",
          "}",
          "if (o == null || getClass() != o.getClass()) {",
          "return false;",
          "}",
          "ParcelableObject that = (ParcelableObject) o;",
          "return value.equals(that.value);",
          "}",
          "@Override",
          "public int hashCode() {",
          "return Objects.hash(value);",
          "}",
          "}");

  public static final JavaFileObject GENERIC_PARCELABLE_OBJECT =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".GenericParcelableObject",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import java.util.Objects;",
          "public final class GenericParcelableObject<E> implements Parcelable {",
          "@SuppressWarnings(\"rawtypes\")",
          "public static final Parcelable.Creator CREATOR =",
          "new Parcelable.Creator() {",
          "@Override",
          "public GenericParcelableObject createFromParcel(Parcel in) {",
          "return new GenericParcelableObject(in);",
          "}",
          "@Override",
          "public GenericParcelableObject[] newArray(int size) {",
          "return new GenericParcelableObject[size];",
          "}",
          "};",
          "private String value;",
          "public String value() {",
          "return value;",
          "}",
          "public GenericParcelableObject(Parcel in) {",
          "this(in.readString());",
          "}",
          "public GenericParcelableObject(String value) {",
          "this.value = value;",
          "}",
          "@Override",
          "public int describeContents() {",
          "return 0;",
          "}",
          "@Override",
          "public void writeToParcel(Parcel dest, int flags) {",
          "dest.writeString(value);",
          "}",
          "@Override",
          "public boolean equals(Object o) {",
          "if (this == o) {",
          "return true;",
          "}",
          "if (o == null || getClass() != o.getClass()) {",
          "return false;",
          "}",
          "GenericParcelableObject that = (GenericParcelableObject) o;",
          "return value.equals(that.value);",
          "}",
          "@Override",
          "public int hashCode() {",
          "return Objects.hash(value);",
          "}",
          "}");

  public static final JavaFileObject SERIALIZABLE_OBJECT =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".SerializableObject",
          "package " + NOTES_PACKAGE + ";",
          "import java.io.Serializable;",
          "import java.util.Objects;",
          "public final class SerializableObject implements Serializable {",
          "private final String value;",
          "public String value() {",
          "return value;",
          "}",
          "public SerializableObject(String value) {",
          "this.value = value;",
          "}",
          "@Override",
          "public boolean equals(Object o) {",
          "if (this == o) {",
          "return true;",
          "}",
          "if (o == null || getClass() != o.getClass()) {",
          "return false;",
          "}",
          "SerializableObject that = (SerializableObject) o;",
          "return Objects.equals(value, that.value);",
          "}",
          "@Override",
          "public int hashCode() {",
          "return Objects.hash(value);",
          "}",
          "}");

  public static final JavaFileObject GENERIC_SERIALIZABLE_OBJECT =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".GenericSerializableObject",
          "package " + NOTES_PACKAGE + ";",
          "import java.io.Serializable;",
          "import java.util.Objects;",
          "public class GenericSerializableObject<R> implements Serializable {",
          "private final String value;",
          "public String value() {",
          "return value;",
          "}",
          "public GenericSerializableObject(String value) {",
          "this.value = value;",
          "}",
          "@Override",
          "public boolean equals(Object o) {",
          "if (this == o) {",
          "return true;",
          "}",
          "if (o == null || getClass() != o.getClass()) {",
          "return false;",
          "}",
          "GenericSerializableObject that = (GenericSerializableObject) o;",
          "return Objects.equals(value, that.value);",
          "}",
          "@Override",
          "public int hashCode() {",
          "return Objects.hash(value);",
          "}",
          "}");

  static final JavaFileObject CUSTOM_WRAPPER =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".CustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import java.util.Objects;",
          "public class CustomWrapper<F> {",
          "private F value;",
          "public CustomWrapper(F value) {",
          "this.value = value;",
          "}",
          "public F value() {",
          "return value;",
          "}",
          "@Override",
          "public boolean equals(Object o) {",
          "if (this == o) {",
          "return true;",
          "}",
          "if (o == null || getClass() != o.getClass()) {",
          "return false;",
          "}",
          "CustomWrapper that = (CustomWrapper) o;",
          "return Objects.equals(value, that.value);",
          "}",
          "@Override",
          "public int hashCode() {",
          "return Objects.hash(value);",
          "}",
          "}");

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "@CustomParcelableWrapper(originalType = CustomWrapper.class)",
          "public class ParcelableCustomWrapper<E> implements Parcelable {",
          "private static final int NULL = -1;",
          "private static final int NOT_NULL = 1;",
          "private final Bundler bundler;",
          "private final BundlerType type;",
          "private final CustomWrapper<E> customWrapper;",
          "public static <F> ParcelableCustomWrapper<F> of(",
          "Bundler bundler, BundlerType type, CustomWrapper<F> customWrapper) {",
          "return new ParcelableCustomWrapper<>(bundler, type, customWrapper);",
          "}",
          "public CustomWrapper<E> get() {",
          "return customWrapper;",
          "}",
          "private ParcelableCustomWrapper(Bundler bundler, BundlerType type, CustomWrapper<E>"
              + " customWrapper) {",
          "if (bundler == null || type == null) {",
          "throw new NullPointerException();",
          "}",
          "this.bundler = bundler;",
          "this.type = type;",
          "this.customWrapper = customWrapper;",
          "}",
          "private ParcelableCustomWrapper(Parcel in) {",
          "bundler = in.readParcelable(Bundler.class.getClassLoader());",
          "int presentValue = in.readInt();",
          "if (presentValue == NULL) {",
          "type = null;",
          "customWrapper = null;",
          "return;",
          "}",
          "type = (BundlerType) in.readParcelable(Bundler.class.getClassLoader());",
          "BundlerType valueType = type.typeArguments().get(0);",
          "@SuppressWarnings(\"unchecked\")",
          "E value = (E) bundler.readFromParcel(in, valueType);",
          "customWrapper = new CustomWrapper<>(value);",
          "}",
          "@Override",
          "public void writeToParcel(Parcel dest, int flags) {",
          "dest.writeParcelable(bundler, flags);",
          "if (customWrapper == null) {",
          "dest.writeInt(NULL);",
          "return;",
          "}",
          "dest.writeInt(NOT_NULL);",
          "dest.writeParcelable(type, flags);",
          "BundlerType valueType = type.typeArguments().get(0);",
          "bundler.writeToParcel(dest, customWrapper.value(), valueType, flags);",
          "}",
          "@Override",
          "public int describeContents() {",
          "return 0;",
          "}",
          "@SuppressWarnings(\"rawtypes\")",
          "public static final Creator<ParcelableCustomWrapper> CREATOR =",
          "new Creator<ParcelableCustomWrapper>() {",
          "@Override",
          "public ParcelableCustomWrapper createFromParcel(Parcel in) {",
          "return new ParcelableCustomWrapper(in);",
          "}",
          "@Override",
          "public ParcelableCustomWrapper[] newArray(int size) {",
          "return new ParcelableCustomWrapper[size];",
          "}",
          "};",
          "}");

  static final JavaFileObject SIMPLE_FUTURE =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".SimpleFuture",
          "package " + NOTES_PACKAGE + ";",
          "import java.util.concurrent.CountDownLatch;",
          "import java.util.function.Consumer;",
          "public class SimpleFuture<E> {",
          "private E value;",
          "private Throwable thrown;",
          "private final CountDownLatch countDownLatch = new CountDownLatch(1);",
          "private Consumer<E> callback;",
          "private Consumer<Throwable> exceptionCallback;",
          "public void set(E value) {",
          "this.value = value;",
          "countDownLatch.countDown();",
          "if (callback != null) {",
          "callback.accept(value);",
          "}",
          "}",
          "public void setException(Throwable t) {",
          "this.thrown = t;",
          "countDownLatch.countDown();",
          "if (exceptionCallback != null) {",
          "exceptionCallback.accept(thrown);",
          "}",
          "}",
          "public E get() {",
          "try {",
          "countDownLatch.await();",
          "} catch (InterruptedException e) {",
          "return null;",
          "}",
          "if (thrown != null) {",
          "throw new RuntimeException(thrown);",
          "}",
          "return value;",
          "}",
          "public void setCallback(Consumer<E> callback, Consumer<Throwable> exceptionCallback) {",
          "if (value != null) {",
          "callback.accept(value);",
          "} else if (thrown != null) {",
          "exceptionCallback.accept(thrown);",
          "} else {",
          "this.callback = callback;",
          "this.exceptionCallback = exceptionCallback;",
          "}",
          "}",
          "}");

  static final JavaFileObject SIMPLE_FUTURE_WRAPPER =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".SimpleFutureWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import com.google.android.enterprise.connectedapps.FutureWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.FutureResultWriter;",
          "import com.google.android.enterprise.connectedapps.Profile;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "import"
              + " com.google.android.enterprise.connectedapps.internal.CrossProfileCallbackMultiMerger;",
          "import java.util.Map;",
          "@com.google.android.enterprise.connectedapps.annotations.CustomFutureWrapper(",
          "originalType = SimpleFuture.class)",
          "public final class SimpleFutureWrapper<E> extends FutureWrapper<E> {",
          "private final SimpleFuture<E> future = new SimpleFuture<>();",
          "public static <E> SimpleFutureWrapper<E> create(Bundler bundler, BundlerType"
              + " bundlerType) {",
          "return new SimpleFutureWrapper<>(bundler, bundlerType);",
          "}",
          "private SimpleFutureWrapper(Bundler bundler, BundlerType bundlerType) {",
          "super(bundler, bundlerType);",
          "}",
          "public SimpleFuture<E> getFuture() {",
          "return future;",
          "}",
          "@Override",
          "public void onResult(E result) {",
          "future.set(result);",
          "}",
          "@Override",
          "public void onException(Throwable throwable) {",
          "future.setException(throwable);",
          "}",
          "public static <E> void writeFutureResult(",
          "SimpleFuture<E> future,",
          "FutureResultWriter<E> resultWriter) {",
          "future.setCallback(",
          "(value) -> {",
          "resultWriter.onSuccess(value);",
          "},",
          "(exception) -> {",
          "resultWriter.onFailure(exception);",
          "});",
          "}",
          "public static <E> SimpleFuture<Map<Profile, E>> groupResults(",
          "Map<Profile, SimpleFuture<E>> results) {",
          "SimpleFuture<Map<Profile, E>> m = new SimpleFuture<>();",
          "CrossProfileCallbackMultiMerger<E> merger =",
          "new CrossProfileCallbackMultiMerger<>(results.size(), m::set);",
          "for (Map.Entry<Profile, SimpleFuture<E>> result : results.entrySet()) {",
          "result",
          ".getValue()",
          ".setCallback(",
          "(value) -> {",
          "merger.onResult(result.getKey(), value);",
          "},",
          "(throwable) -> {",
          "merger.missingResult(result.getKey());",
          "});",
          "}",
          "return m;",
          "}",
          "}");

  public static JavaFileObject annotatedNotesCrossProfileType(AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".NotesType",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileQualifiedName() + ";",
        "public final class NotesType {",
        annotationPrinter.crossProfileAsAnnotation(),
        "  public void refreshNotes() {",
        "  }",
        "}");
  }

  public static JavaFileObject notesCrossProfileTypeWhichUsesInstallationListener(
      AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".NotesType",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileQualifiedName() + ";",
        "public final class NotesType {",
        annotationPrinter.crossProfileAsAnnotation(),
        "  public void install(InstallationListener l) {",
        "  }",
        "}");
  }

  public static JavaFileObject annotatedNotesProvider(AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".NotesProvider",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileProviderQualifiedName() + ";",
        "public final class NotesProvider {",
        annotationPrinter.crossProfileProviderAsAnnotation(),
        "  public NotesType provideNotesType() {",
        "    return new NotesType();",
        "  }",
        "}");
  }

  public static JavaFileObject installationListener(AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".InstallationListener",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileCallbackQualifiedName() + ";",
        annotationPrinter.crossProfileCallbackAsAnnotation(),
        "public interface InstallationListener {",
        "  void installationComplete();",
        "}");
  }

  public static JavaFileObject installationListenerSimple(AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".InstallationListener",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileCallbackQualifiedName() + ";",
        annotationPrinter.crossProfileCallbackAsAnnotation("simple=true"),
        "public interface InstallationListener {",
        "  void installationComplete();",
        "}");
  }

  public static JavaFileObject installationListenerSimpleWithStringParam(
      AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".InstallationListener",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileCallbackQualifiedName() + ";",
        annotationPrinter.crossProfileCallbackAsAnnotation("simple=true"),
        "public interface InstallationListener {",
        "  void installationComplete(String s);",
        "}");
  }

  public static JavaFileObject installationListenerSimpleWithIntentParam(
      AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".InstallationListener",
        "package " + NOTES_PACKAGE + ";",
        "import android.content.Intent;",
        "import " + annotationPrinter.crossProfileCallbackQualifiedName() + ";",
        annotationPrinter.crossProfileCallbackAsAnnotation("simple=true"),
        "public interface InstallationListener {",
        "  void installationComplete(Intent i);",
        "}");
  }

  public static JavaFileObject installationListenerWithStringParam(
      AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".InstallationListener",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileCallbackQualifiedName() + ";",
        annotationPrinter.crossProfileCallbackAsAnnotation(),
        "public interface InstallationListener {",
        "  void installationComplete(String s);",
        "}");
  }

  public static JavaFileObject installationListenerWithListStringParam(
      AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".InstallationListener",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileCallbackQualifiedName() + ";",
        annotationPrinter.crossProfileCallbackAsAnnotation(),
        "public interface InstallationListener {",
        "  void installationComplete(java.util.List<String> s);",
        "}");
  }

  public static JavaFileObject staticType(AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".StaticType",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileQualifiedName() + ";",
        "public final class StaticType {",
        annotationPrinter.crossProfileAsAnnotation(),
        "  public static void refreshNotes() {",
        "  }",
        "}");
  }

  public static JavaFileObject annotatedDifferentCrossProfileType(
      AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".DifferentType",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileQualifiedName() + ";",
        "public final class DifferentType {",
        annotationPrinter.crossProfileAsAnnotation(),
        "  public void differentMethod() {",
        "  }",
        "}");
  }

  public static JavaFileObject annotatedDifferentProvider(AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".DifferentProvider",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileProviderQualifiedName() + ";",
        "public final class DifferentProvider {",
        annotationPrinter.crossProfileProviderAsAnnotation(),
        "  public DifferentType provideDifferentType() {",
        "    return new DifferentType();",
        "  }",
        "}");
  }

  public static JavaFileObject notesTypeWithDefaultConnector(AnnotationPrinter annotationPrinter) {
    return annotatedNotesCrossProfileType(annotationPrinter);
  }

  public static JavaFileObject annotatedNotesConfigurationWithNotesProvider(
      AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".NotesConfiguration",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileConfigurationQualifiedName() + ";",
        "import com.google.android.enterprise.connectedapps.CrossProfileConnector;",
        annotationPrinter.crossProfileConfigurationAsAnnotation(
            "providers=NotesProvider.class, connector=CrossProfileConnector.class"),
        "public abstract class NotesConfiguration {",
        "}");
  }

  public static JavaFileObject annotatedNotesConfigurationWithNotesProviderAndCrossUserConnector(
      AnnotationPrinter annotationPrinter) {
    return JavaFileObjects.forSourceLines(
        NOTES_PACKAGE + ".NotesConfiguration",
        "package " + NOTES_PACKAGE + ";",
        "import " + annotationPrinter.crossProfileConfigurationQualifiedName() + ";",
        "import com.google.android.enterprise.connectedapps.CrossUserConnector;",
        annotationPrinter.crossProfileConfigurationAsAnnotation(
            "providers=NotesProvider.class, connector=CrossUserConnector.class"),
        "public abstract class NotesConfiguration {",
        "}");
  }

  /** Combines two iterables into an iterable of all possible pairs. */
  public static Iterable<Object[]> combineParameters(
      Iterable<?> parameters1, Iterable<?> parameters2) {
    List<Object[]> testParameters = new ArrayList<>();

    for (Object parameter1 : parameters1) {
      for (Object parameter2 : parameters2) {
        testParameters.add(new Object[] {parameter1, parameter2});
      }
    }

    return testParameters;
  }

  public static String formatErrorMessage(String errorMessage, AnnotationNames annotationNames) {
    return ValidationMessageFormatter.forAnnotations(annotationNames).format(errorMessage);
  }

  private TestUtilities() {}
}
