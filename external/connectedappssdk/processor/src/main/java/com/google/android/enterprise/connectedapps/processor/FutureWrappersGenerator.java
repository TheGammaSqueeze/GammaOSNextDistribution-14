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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTypeInfo;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.FutureWrapper.WrapperType;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.Type;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Optional;
import javax.tools.JavaFileObject;

/**
 * Generate the wrapper classes for every used future type.
 *
 * <p>This is intended to be initialised and used once, which will generate all needed code.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class FutureWrappersGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;

  FutureWrappersGenerator(GeneratorContext generatorContext) {
    this.generatorContext = checkNotNull(generatorContext);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException("FutureWrappersGenerator#generate can only be called once");
    }
    generated = true;

    generateFutureWrappers();
  }

  private void generateFutureWrappers() {
    Collection<FutureWrapper> futureWrappersToGenerate =
        generatorContext.crossProfileTypes().stream()
            .map(CrossProfileTypeInfo::supportedTypes)
            .flatMap(s -> s.usableTypes().stream())
            .filter(s -> s.getFutureWrapper().isPresent())
            .map(Type::getFutureWrapper)
            .map(Optional::get)
            .filter(w -> w.wrapperType().equals(WrapperType.DEFAULT))
            .collect(toSet());

    for (FutureWrapper futureWrapper : futureWrappersToGenerate) {
      generateFutureWrapper(futureWrapper);
    }
  }

  private void generateFutureWrapper(FutureWrapper futureWrapper) {
    String futureWrapperSimpleName = futureWrapper.defaultWrapperClassName().simpleName();

    String contents;
    InputStream in =
        ParcelableWrappersGenerator.class.getResourceAsStream(
            "/futurewrappers/" + futureWrapperSimpleName + ".java");

    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()))) {
      contents = br.lines().collect(joining(System.lineSeparator()));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not read futurewrapper file for " + futureWrapperSimpleName, e);
    }

    contents =
        contents.replace(
            futureWrapper.defaultWrapperClassName().packageName(),
            futureWrapper.wrapperClassName().packageName());
    contents =
        contents.replace(
            futureWrapper.defaultWrapperClassName().simpleName(),
            futureWrapper.wrapperClassName().simpleName());

    JavaFileObject builderFile;
    try {
      builderFile =
          generatorContext
              .processingEnv()
              .getFiler()
              .createSourceFile(
                  futureWrapper.wrapperClassName().packageName()
                      + "."
                      + futureWrapper.wrapperClassName().simpleName());
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not write futurewrapper for " + futureWrapperSimpleName, e);
    }

    try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
      out.write(contents);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not write futurewrapper for " + futureWrapperSimpleName, e);
    }
  }
}
