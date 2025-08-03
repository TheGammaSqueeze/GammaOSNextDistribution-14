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

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Arrays.stream;

import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.interfaces.CrossProfileConfigurationAnnotation;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.interfaces.CrossProfileConfigurationsAnnotation;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationsAnnotationInfo;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import java.util.Set;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class CrossProfileConfigurationsAnnotationInfoExtractor
    extends AnnotationInfoExtractor<
        CrossProfileConfigurationsAnnotationInfo, CrossProfileConfigurationsAnnotation> {

  CrossProfileConfigurationsAnnotationInfoExtractor() {
    super(CrossProfileConfigurationsAnnotation.class);
  }

  @Override
  protected Class<? extends Annotation> supportedAnnotationClass(
      AnnotationClasses annotationClasses) {
    return annotationClasses.crossProfileConfigurationsAnnotationClass();
  }

  @Override
  protected CrossProfileConfigurationsAnnotationInfo annotationInfoFromAnnotation(
      CrossProfileConfigurationsAnnotation annotation, Types types) {
    CrossProfileConfigurationAnnotationInfoExtractor innerExtractor =
        new CrossProfileConfigurationAnnotationInfoExtractor();

    Set<CrossProfileConfigurationAnnotationInfo> annotationInfos =
        stream(annotation.value())
            .map(
                configurationAnnotation ->
                    wrapAnnotationWithInterface(
                        CrossProfileConfigurationAnnotation.class, configurationAnnotation))
            .map(
                configurationAnnotation ->
                    innerExtractor.annotationInfoFromAnnotation(configurationAnnotation, types))
            .collect(toImmutableSet());

    return CrossProfileConfigurationsAnnotationInfo.create(ImmutableSet.copyOf(annotationInfos));
  }

  @Override
  protected CrossProfileConfigurationsAnnotationInfo emptyAnnotationInfo(Elements elements) {
    return CrossProfileConfigurationsAnnotationInfo.create(ImmutableSet.of());
  }
}
