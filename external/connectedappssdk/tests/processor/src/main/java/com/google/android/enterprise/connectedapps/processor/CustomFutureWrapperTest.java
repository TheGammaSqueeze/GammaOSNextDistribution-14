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
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.SIMPLE_FUTURE;
import static com.google.android.enterprise.connectedapps.processor.TestUtilities.SIMPLE_FUTURE_WRAPPER;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CustomFutureWrapperTest {

  private static final String DOES_NOT_EXTEND_FUTURE_WRAPPER_ERROR =
      "Classes annotated @CustomFutureWrapper must extend FutureWrapper";
  private static final String INCORRECT_CREATE_METHOD_ERROR =
      "Classes annotated @CustomFutureWrapper must have a create method which returns an instance"
          + " of the class and takes a Bundler and BundlerType argument";
  private static final String INCORRECT_GET_FUTURE_METHOD_ERROR =
      "Classes annotated @CustomFutureWrapper must have a getFuture method which returns an"
          + " instance of the wrapped future and takes no arguments";
  private static final String INCORRECT_RESOLVE_CALLBACK_WHEN_FUTURE_IS_SET_METHOD_ERROR =
      "Classes annotated @CustomFutureWrapper must have a writeFutureResult method"
          + " which returns void and takes as arguments an instance of the wrapped future and a"
          + " FutureResultWriter";
  private static final String INCORRECT_GROUP_RESULTS_METHOD_ERROR =
      "Classes annotated @CustomFutureWrapper must have a groupResults method which returns an"
          + " instance of the wrapped future containing a map from Profile to the wrapped future"
          + " type, and takes as an argument a map from Profile to an instance of the wrapped"
          + " future";
  private static final String MUST_HAVE_ONE_TYPE_PARAMETER_ERROR =
      "Classes annotated @CustomFutureWrapper must have a single type parameter";

  static final JavaFileObject SIMPLE_FUTURE_WRAPPER_IS_NOT_GENERIC =
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
          "public final class SimpleFutureWrapper extends FutureWrapper<String> {",
          "private final SimpleFuture<String> future = new SimpleFuture<>();",
          "public static SimpleFutureWrapper create(Bundler bundler, BundlerType"
              + " bundlerType) {",
          "return new SimpleFutureWrapper(bundler, bundlerType);",
          "}",
          "private SimpleFutureWrapper(Bundler bundler, BundlerType bundlerType) {",
          "super(bundler, bundlerType);",
          "}",
          "public SimpleFuture<String> getFuture() {",
          "return future;",
          "}",
          "@Override",
          "public void onResult(String result) {",
          "future.set(result);",
          "}",
          "@Override",
          "public void onException(Throwable throwable) {",
          "future.setException(throwable);",
          "}",
          "public static void writeFutureResult(",
          "SimpleFuture<String> future,",
          "FutureResultWriter<E> resultWriter) {",
          "future.setCallback(",
          "(value) -> {",
          "resultWriter.onSuccess(value);",
          "},",
          "(exception) -> {",
          "resultWriter.onFailure(exception);",
          "});",
          "}",
          "public static SimpleFuture<Map<Profile, String>> groupResults(",
          "Map<Profile, SimpleFuture<String>> results) {",
          "SimpleFuture<Map<Profile, String>> m = new SimpleFuture<>();",
          "CrossProfileCallbackMultiMerger<String> merger =",
          "new CrossProfileCallbackMultiMerger<>(results.size(), m::set);",
          "for (Map.Entry<Profile, SimpleFuture<String>> result : results.entrySet()) {",
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

  static final JavaFileObject SIMPLE_FUTURE_WRAPPER_MULTIPLE_TYPE_PARAMETERS =
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
          "public final class SimpleFutureWrapper<E, R> extends FutureWrapper<E> {",
          "private final SimpleFuture<E> future = new SimpleFuture<>();",
          "public static <E> SimpleFutureWrapper<E, String> create(Bundler bundler, BundlerType"
              + " bundlerType) {",
          "return new SimpleFutureWrapper<E, String>(bundler, bundlerType);",
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

  private static final JavaFileObject SIMPLE_FUTURE_WRAPPER_DOES_NOT_EXTEND_FUTURE_WRAPPER =
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
          "public final class SimpleFutureWrapper<E> {",
          "private final SimpleFuture<E> future = new SimpleFuture<>();",
          "public static <E> SimpleFutureWrapper<E> create(Bundler bundler, BundlerType"
              + " bundlerType) {",
          "return new SimpleFutureWrapper<>(bundler, bundlerType);",
          "}",
          "private SimpleFutureWrapper(Bundler bundler, BundlerType bundlerType) {",
          "}",
          "public SimpleFuture<E> getFuture() {",
          "return future;",
          "}",
          "public void onResult(E result) {",
          "future.set(result);",
          "}",
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

  private static final JavaFileObject SIMPLE_FUTURE_WRAPPER_NO_CREATE_METHOD =
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

  private static final JavaFileObject SIMPLE_FUTURE_WRAPPER_INCORRECT_CREATE_METHOD =
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
          "public static <E> SimpleFutureWrapper<E> create(BundlerType bundlerType) {",
          "return null;",
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

  private static final JavaFileObject SIMPLE_FUTURE_WRAPPER_NO_GET_FUTURE_METHOD =
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

  private static final JavaFileObject SIMPLE_FUTURE_WRAPPER_INCORRECT_GET_FUTURE_METHOD =
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
          "public SimpleFuture<E> getFuture(String s) {",
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

  private static final JavaFileObject SIMPLE_FUTURE_WRAPPER_NO_RESOLVE_METHOD =
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

  private static final JavaFileObject SIMPLE_FUTURE_WRAPPER_INCORRECT_RESOLVE_METHOD =
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
          "String s,",
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

  private static final JavaFileObject SIMPLE_FUTURE_WRAPPER_NO_GROUP_METHOD =
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
          "}");

  private static final JavaFileObject SIMPLE_FUTURE_WRAPPER_INCORRECT_GROUP_METHOD =
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
          "Map<String, SimpleFuture<E>> results) {",
          "return null;",
          "}",
          "}");

  @Test
  public void validFutureWrapperAnnotation_compiles() {
    Compilation compilation =
        javac().withProcessors(new Processor()).compile(SIMPLE_FUTURE_WRAPPER, SIMPLE_FUTURE);

    assertThat(compilation).succeededWithoutWarnings();
  }

  @Test
  public void futureWrapperAnnotation_isNotGeneric_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_IS_NOT_GENERIC, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(MUST_HAVE_ONE_TYPE_PARAMETER_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_IS_NOT_GENERIC);
  }

  @Test
  public void futureWrapperAnnotation_hasMultipleTypeParameters_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_MULTIPLE_TYPE_PARAMETERS, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(MUST_HAVE_ONE_TYPE_PARAMETER_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_MULTIPLE_TYPE_PARAMETERS);
  }

  @Test
  public void futureWrapperAnnotation_doesNotExtendFutureWrapper_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_DOES_NOT_EXTEND_FUTURE_WRAPPER, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(DOES_NOT_EXTEND_FUTURE_WRAPPER_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_DOES_NOT_EXTEND_FUTURE_WRAPPER);
  }

  @Test
  public void futureWrapperAnnotation_noCreateMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_NO_CREATE_METHOD, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_CREATE_METHOD_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_NO_CREATE_METHOD);
  }

  @Test
  public void futureWrapperAnnotation_incorrectCreateMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_INCORRECT_CREATE_METHOD, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_CREATE_METHOD_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_INCORRECT_CREATE_METHOD);
  }

  @Test
  public void futureWrapperAnnotation_noGetFutureMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_NO_GET_FUTURE_METHOD, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_GET_FUTURE_METHOD_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_NO_GET_FUTURE_METHOD);
  }

  @Test
  public void futureWrapperAnnotation_incorrectGetFutureMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_INCORRECT_GET_FUTURE_METHOD, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_GET_FUTURE_METHOD_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_INCORRECT_GET_FUTURE_METHOD);
  }

  @Test
  public void futureWrapperAnnotation_noResolveFutureMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_NO_RESOLVE_METHOD, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_RESOLVE_CALLBACK_WHEN_FUTURE_IS_SET_METHOD_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_NO_RESOLVE_METHOD);
  }

  @Test
  public void futureWrapperAnnotation_incorrectResolveFutureMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_INCORRECT_RESOLVE_METHOD, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_RESOLVE_CALLBACK_WHEN_FUTURE_IS_SET_METHOD_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_INCORRECT_RESOLVE_METHOD);
  }

  @Test
  public void futureWrapperAnnotation_noGroupResultsMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_NO_GROUP_METHOD, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_GROUP_RESULTS_METHOD_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_NO_GROUP_METHOD);
  }

  @Test
  public void futureWrapperAnnotation_incorrectGroupResultsMethod_hasError() {
    Compilation compilation =
        javac()
            .withProcessors(new Processor())
            .compile(SIMPLE_FUTURE_WRAPPER_INCORRECT_GROUP_METHOD, SIMPLE_FUTURE);

    assertThat(compilation)
        .hadErrorContaining(INCORRECT_GROUP_RESULTS_METHOD_ERROR)
        .inFile(SIMPLE_FUTURE_WRAPPER_INCORRECT_GROUP_METHOD);
  }
}
