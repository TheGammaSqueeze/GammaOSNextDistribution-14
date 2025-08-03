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
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.interfaces.CrossProfileTestAnnotation;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTestAnnotationInfo;
import java.lang.annotation.Annotation;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class CrossProfileTestAnnotationInfoExtractor
    extends AnnotationInfoExtractor<CrossProfileTestAnnotationInfo, CrossProfileTestAnnotation> {

  CrossProfileTestAnnotationInfoExtractor() {
    super(CrossProfileTestAnnotation.class);
  }

  @Override
  protected Class<? extends Annotation> supportedAnnotationClass(
      AnnotationClasses annotationClasses) {
    return annotationClasses.crossProfileTestAnnotationClass();
  }

  @Override
  protected CrossProfileTestAnnotationInfo annotationInfoFromAnnotation(
      CrossProfileTestAnnotation annotation, Types types) {
    return CrossProfileTestAnnotationInfo.builder()
        .setConfiguration(
            GeneratorUtilities.extractClassFromAnnotation(types, annotation::configuration))
        .build();
  }

  @Override
  protected CrossProfileTestAnnotationInfo emptyAnnotationInfo(Elements elements) {
    throw new UnsupportedOperationException("Annotations of type CrossProfileTest cannot be empty");
  }
}
