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
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.interfaces.CrossProfileConfigurationAnnotation;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationAnnotationInfo;
import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Annotation;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class CrossProfileConfigurationAnnotationInfoExtractor
    extends AnnotationInfoExtractor<
        CrossProfileConfigurationAnnotationInfo, CrossProfileConfigurationAnnotation> {

  CrossProfileConfigurationAnnotationInfoExtractor() {
    super(CrossProfileConfigurationAnnotation.class);
  }

  @Override
  protected Class<? extends Annotation> supportedAnnotationClass(
      AnnotationClasses annotationClasses) {
    return annotationClasses.crossProfileConfigurationAnnotationClass();
  }

  @Override
  protected CrossProfileConfigurationAnnotationInfo annotationInfoFromAnnotation(
      CrossProfileConfigurationAnnotation annotation, Types types) {
    return CrossProfileConfigurationAnnotationInfo.builder()
        .setConnector(GeneratorUtilities.extractClassFromAnnotation(types, annotation::connector))
        .setProviderClasses(
            ImmutableSet.copyOf(
                GeneratorUtilities.extractClassesFromAnnotation(types, annotation::providers)))
        .setServiceClass(
            GeneratorUtilities.extractClassFromAnnotation(types, annotation::serviceClass))
        .setServiceSuperclass(
            GeneratorUtilities.extractClassFromAnnotation(types, annotation::serviceSuperclass))
        .build();
  }

  @Override
  protected CrossProfileConfigurationAnnotationInfo emptyAnnotationInfo(Elements elements) {
    TypeElement crossProfileConfiguration =
        elements.getTypeElement(
            "com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration");

    return CrossProfileConfigurationAnnotationInfo.builder()
        .setConnector(crossProfileConfiguration)
        .setProviderClasses(ImmutableSet.of())
        .setServiceClass(crossProfileConfiguration)
        .setServiceSuperclass(crossProfileConfiguration)
        .build();
  }
}
