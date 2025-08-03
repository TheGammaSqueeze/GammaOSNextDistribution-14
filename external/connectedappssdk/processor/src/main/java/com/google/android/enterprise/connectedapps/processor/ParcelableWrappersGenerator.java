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
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ParcelableWrapper;
import com.google.android.enterprise.connectedapps.processor.containers.ParcelableWrapper.WrapperType;
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
 * Generate the {@code Parcelable*} classes for every used compatible type.
 *
 * <p>This is intended to be initialised and used once, which will generate all needed code.
 *
 * <p>This must only be used once. It should be used after {@link EarlyValidator} has been used to
 * validate that the annotated code is correct.
 */
final class ParcelableWrappersGenerator {

  private boolean generated = false;
  private final GeneratorContext generatorContext;

  ParcelableWrappersGenerator(GeneratorContext generatorContext) {
    this.generatorContext = checkNotNull(generatorContext);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "ParcelableWrappersGenerator#generate can only be called once");
    }
    generated = true;

    generateParcelableWrappers();
  }

  private void generateParcelableWrappers() {
    Collection<ParcelableWrapper> parcelableWrappersToGenerate =
        generatorContext.crossProfileTypes().stream()
            .map(CrossProfileTypeInfo::supportedTypes)
            .flatMap(s -> s.usableTypes().stream())
            .filter(s -> s.getParcelableWrapper().isPresent())
            .map(Type::getParcelableWrapper)
            .map(Optional::get)
            .collect(toSet());

    generateDefaultParcelableWrappers(parcelableWrappersToGenerate);
    generateProtoParcelableWrappers(parcelableWrappersToGenerate);
  }

  private void generateDefaultParcelableWrappers(Collection<ParcelableWrapper> parcelableWrappers) {
    Collection<ParcelableWrapper> defaultParcelableWrappersToGenerate =
        parcelableWrappers.stream()
            .filter(f -> f.wrapperType() == WrapperType.DEFAULT)
            .collect(toSet());

    for (ParcelableWrapper parcelableWrapper : defaultParcelableWrappersToGenerate) {
      if (generatorContext
              .elements()
              .getTypeElement(parcelableWrapper.wrapperClassName().toString())
          != null) {
        // We don't generate things which already exist
        return;
      }
      generateDefaultParcelableWrapper(parcelableWrapper);
    }
  }

  private void generateDefaultParcelableWrapper(ParcelableWrapper parcelableWrapper) {
    String parcelableWrapperSimpleName = parcelableWrapper.defaultWrapperClassName().simpleName();

    String contents;
    InputStream in =
        ParcelableWrappersGenerator.class.getResourceAsStream(
            "/parcelablewrappers/" + parcelableWrapperSimpleName + ".java");

    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()))) {
      contents = br.lines().collect(joining(System.lineSeparator()));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not read parcelablewrapper file for " + parcelableWrapperSimpleName, e);
    }

    contents =
        contents.replace(
            parcelableWrapper.defaultWrapperClassName().packageName(),
            parcelableWrapper.wrapperClassName().packageName());
    contents =
        contents.replace(
            parcelableWrapper.defaultWrapperClassName().simpleName(),
            parcelableWrapper.wrapperClassName().simpleName());

    JavaFileObject builderFile;
    try {
      builderFile =
          generatorContext
              .processingEnv()
              .getFiler()
              .createSourceFile(
                  parcelableWrapper.wrapperClassName().packageName()
                      + "."
                      + parcelableWrapper.wrapperClassName().simpleName());
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not write parcelablewrapper for " + parcelableWrapperSimpleName, e);
    }

    try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
      out.write(contents);
    } catch (IOException e) {
      throw new IllegalStateException(
          "Could not write parcelablewrapper for " + parcelableWrapperSimpleName, e);
    }
  }

  private void generateProtoParcelableWrappers(Collection<ParcelableWrapper> parcelableWrappers) {
    Collection<ParcelableWrapper> protoParcelableWrappersToGenerate =
        parcelableWrappers.stream()
            .filter(f -> f.wrapperType() == WrapperType.PROTO)
            .collect(toSet());

    for (ParcelableWrapper parcelableWrapper : protoParcelableWrappersToGenerate) {
      new ProtoParcelableWrapperGenerator(generatorContext, parcelableWrapper).generate();
    }
  }
}
