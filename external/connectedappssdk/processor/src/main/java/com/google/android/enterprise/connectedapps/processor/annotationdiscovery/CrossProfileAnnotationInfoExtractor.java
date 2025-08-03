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
package com.google.android.enterprise.connectedapps.processor.annotationdiscovery;

import com.google.android.enterprise.connectedapps.processor.GeneratorUtilities;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.interfaces.CrossProfileAnnotation;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileAnnotationInfo;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class CrossProfileAnnotationInfoExtractor
    extends AnnotationInfoExtractor<CrossProfileAnnotationInfo, CrossProfileAnnotation> {

  CrossProfileAnnotationInfoExtractor() {
    super(CrossProfileAnnotation.class);
  }

  @Override
  protected Class<? extends Annotation> supportedAnnotationClass(
      AnnotationClasses annotationClasses) {
    return annotationClasses.crossProfileAnnotationClass();
  }

  @Override
  protected CrossProfileAnnotationInfo annotationInfoFromAnnotation(
      CrossProfileAnnotation annotation, Types types) {
    CrossProfileAnnotationInfo.Builder builder =
        CrossProfileAnnotationInfo.builder()
            .setConnectorClass(
                GeneratorUtilities.extractClassFromAnnotation(types, annotation::connector))
            .setParcelableWrapperClasses(
                ImmutableSet.copyOf(
                    GeneratorUtilities.extractClassesFromAnnotation(
                        types, annotation::parcelableWrappers)))
            .setFutureWrapperClasses(
                ImmutableSet.copyOf(
                    GeneratorUtilities.extractClassesFromAnnotation(
                        types, annotation::futureWrappers)))
            .setIsStatic(annotation.isStatic());

    return builder.build();
  }

  @Override
  protected CrossProfileAnnotationInfo emptyAnnotationInfo(Elements elements) {
    return CrossProfileAnnotationInfo.builder()
        .setConnectorClass(
            elements.getTypeElement(
                "com.google.android.enterprise.connectedapps.annotations.CrossProfile"))
        .setParcelableWrapperClasses(ImmutableSet.of())
        .setFutureWrapperClasses(ImmutableSet.of())
        .setIsStatic(false)
        .build();
  }
}
