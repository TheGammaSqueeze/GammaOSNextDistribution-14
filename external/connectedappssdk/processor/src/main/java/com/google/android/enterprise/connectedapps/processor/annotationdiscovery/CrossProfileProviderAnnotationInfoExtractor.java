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
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.interfaces.CrossProfileProviderAnnotation;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileProviderAnnotationInfo;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class CrossProfileProviderAnnotationInfoExtractor
    extends AnnotationInfoExtractor<
        CrossProfileProviderAnnotationInfo, CrossProfileProviderAnnotation> {

  CrossProfileProviderAnnotationInfoExtractor() {
    super(CrossProfileProviderAnnotation.class);
  }

  @Override
  protected Class<? extends Annotation> supportedAnnotationClass(
      AnnotationClasses annotationClasses) {
    return annotationClasses.crossProfileProviderAnnotationClass();
  }

  @Override
  protected CrossProfileProviderAnnotationInfo annotationInfoFromAnnotation(
      CrossProfileProviderAnnotation annotation, Types types) {
    return CrossProfileProviderAnnotationInfo.create(
        ImmutableSet.copyOf(
            GeneratorUtilities.extractClassesFromAnnotation(types, annotation::staticTypes)));
  }

  @Override
  protected CrossProfileProviderAnnotationInfo emptyAnnotationInfo(Elements elements) {
    return CrossProfileProviderAnnotationInfo.create(ImmutableSet.of());
  }
}
