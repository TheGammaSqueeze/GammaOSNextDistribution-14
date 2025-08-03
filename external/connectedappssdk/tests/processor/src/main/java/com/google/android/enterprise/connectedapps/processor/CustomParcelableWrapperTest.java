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

import static com.google.android.enterprise.connectedapps.processor.TestUtilities.CUSTOM_WRAPPER;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.NOTES_PACKAGE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.PARCELABLE_CUSTOM_WRAPPER;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CustomParcelableWrapperTest {

  private static final String NOT_PARCELABLE_ERROR =
      "Classes annotated @CustomParcelableWrapper must implement Parcelable";
  private static final String INCORRECT_OF_METHOD =
      "Classes annotated @CustomParcelableWrapper must have a static 'of' method which takes a"
          + " Bundler, a BundlerType, and an instance of the wrapped type as arguments and returns"
          + " an instance of the parcelable wrapper";
  private static final String INCORRECT_GET_METHOD =
      "Classes annotated @CustomParcelableWrapper must have a static 'get' method which takes no"
          + " arguments and returns an instance of the wrapped class";
  private static final String INCORRECT_PARCELABLE_IMPLEMENTATION =
      "Classes annotated @CustomParcelableWrapper must correctly implement Parcelable";

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER_NOT_GENERIC =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "@CustomParcelableWrapper(originalType = CustomWrapper.class)",
          "public class ParcelableCustomWrapper implements Parcelable {",
          "private static final int NULL = -1;",
          "private static final int NOT_NULL = 1;",
          "public static ParcelableCustomWrapper of(",
          "Bundler bundler, BundlerType type, CustomWrapper customWrapper) {",
          "return null;",
          "}",
          "public CustomWrapper get() {",
          "return null;",
          "}",
          "private ParcelableCustomWrapper(Parcel in) {",
          "}",
          "@Override",
          "public void writeToParcel(Parcel dest, int flags) {",
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

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER_DOES_NOT_IMPLEMENT_PARCELABLE =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;",
          "@CustomParcelableWrapper(originalType = CustomWrapper.class)",
          "public class ParcelableCustomWrapper<E> {",
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

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER_NO_OF_METHOD =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;",
          "@CustomParcelableWrapper(originalType = CustomWrapper.class)",
          "public class ParcelableCustomWrapper<E> implements Parcelable {",
          "private static final int NULL = -1;",
          "private static final int NOT_NULL = 1;",
          "private final Bundler bundler;",
          "private final BundlerType type;",
          "private final CustomWrapper<E> customWrapper;",
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

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER_OF_METHOD_WRONG_ARGUMENTS =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;",
          "@CustomParcelableWrapper(originalType = CustomWrapper.class)",
          "public class ParcelableCustomWrapper<E> implements Parcelable {",
          "private static final int NULL = -1;",
          "private static final int NOT_NULL = 1;",
          "private final Bundler bundler;",
          "private final BundlerType type;",
          "private final CustomWrapper<E> customWrapper;",
          "public static <F> ParcelableCustomWrapper<F> of(",
          "Bundler bundler, CustomWrapper<F> customWrapper) {",
          "return null;",
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

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER_OF_METHOD_WRONG_RETURN_TYPE =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;",
          "@CustomParcelableWrapper(originalType = CustomWrapper.class)",
          "public class ParcelableCustomWrapper<E> implements Parcelable {",
          "private static final int NULL = -1;",
          "private static final int NOT_NULL = 1;",
          "private final Bundler bundler;",
          "private final BundlerType type;",
          "private final CustomWrapper<E> customWrapper;",
          "public static<F> String of(",
          "Bundler bundler, BundlerType bundlerType, CustomWrapper<F> customWrapper) {",
          "return null;",
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

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER_NO_GET_METHOD =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;",
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

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER_GET_METHOD_WRONG_RETURN_TYPE =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;",
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
          "public String get() {",
          "return null;",
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

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER_NO_CREATOR =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;",
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
          "}");

  static final JavaFileObject PARCELABLE_CUSTOM_WRAPPER_INCORRECT_CREATOR =
      JavaFileObjects.forSourceLines(
          NOTES_PACKAGE + ".ParcelableCustomWrapper",
          "package " + NOTES_PACKAGE + ";",
          "import android.os.Parcel;",
          "import android.os.Parcelable;",
          "import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;",
          "import com.google.android.enterprise.connectedapps.internal.Bundler;",
          "import com.google.android.enterprise.connectedapps.internal.BundlerType;",
          "import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;",
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
          "public static final Creator<String> CREATOR =",
          "new Creator<String>() {",
          "@Override",
          "public String createFromParcel(Parcel in) {",
          "return null;",
          "}",
          "@Override",
          "public String[] newArray(int size) {",
          "return new String[size];",
          "}",
          "};",
          "}");

  @Test
  public void validParcelableWrapperAnnotation_compiles() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void validParcelableWrapperAnnotation_notGeneric_compiles() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER_NOT_GENERIC);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void parcelableWrapper_doesNotImplementParcelable_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER_DOES_NOT_IMPLEMENT_PARCELABLE);

    assertThat(compilation)
        .hadErrorContaining(NOT_PARCELABLE_ERROR)
        .inFile(PARCELABLE_CUSTOM_WRAPPER_DOES_NOT_IMPLEMENT_PARCELABLE);
  }

  @Test
  public void parcelableWrapper_hasNoOfMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER_NO_OF_METHOD);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_OF_METHOD)
        .inFile(PARCELABLE_CUSTOM_WRAPPER_NO_OF_METHOD);
  }

  @Test
  public void parcelableWrapper_ofMethodWrongReturnType_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER_OF_METHOD_WRONG_RETURN_TYPE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_OF_METHOD)
        .inFile(PARCELABLE_CUSTOM_WRAPPER_OF_METHOD_WRONG_RETURN_TYPE);
  }

  @Test
  public void parcelableWrapper_ofMethodWrongArguments_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER_OF_METHOD_WRONG_ARGUMENTS);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_OF_METHOD)
        .inFile(PARCELABLE_CUSTOM_WRAPPER_OF_METHOD_WRONG_ARGUMENTS);
  }

  @Test
  public void parcelableWrapper_hasNoGetMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER_NO_GET_METHOD);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_GET_METHOD)
        .inFile(PARCELABLE_CUSTOM_WRAPPER_NO_GET_METHOD);
  }

  @Test
  public void parcelableWrapper_getMethodWrongReturnType_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER_GET_METHOD_WRONG_RETURN_TYPE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_GET_METHOD)
        .inFile(PARCELABLE_CUSTOM_WRAPPER_GET_METHOD_WRONG_RETURN_TYPE);
  }

  @Test
  public void parcelableWrapper_noCreator_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER_NO_CREATOR);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_PARCELABLE_IMPLEMENTATION)
        .inFile(PARCELABLE_CUSTOM_WRAPPER_NO_CREATOR);
  }

  @Test
  public void parcelableWrapper_incorrectCreator_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(CUSTOM_WRAPPER, PARCELABLE_CUSTOM_WRAPPER_INCORRECT_CREATOR);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_PARCELABLE_IMPLEMENTATION)
        .inFile(PARCELABLE_CUSTOM_WRAPPER_INCORRECT_CREATOR);
  }
}
