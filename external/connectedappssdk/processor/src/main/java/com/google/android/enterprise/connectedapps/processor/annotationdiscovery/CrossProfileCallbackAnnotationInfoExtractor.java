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

import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.interfaces.CrossProfileCallbackAnnotation;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackAnnotationInfo;
import java.lang.annotation.Annotation;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

final class CrossProfileCallbackAnnotationInfoExtractor
    extends AnnotationInfoExtractor<
        CrossProfileCallbackAnnotationInfo, CrossProfileCallbackAnnotation> {

  CrossProfileCallbackAnnotationInfoExtractor() {
    super(CrossProfileCallbackAnnotation.class);
  }

  @Override
  protected Class<? extends Annotation> supportedAnnotationClass(
      AnnotationClasses annotationClasses) {
    return annotationClasses.crossProfileCallbackAnnotationClass();
  }

  @Override
  protected CrossProfileCallbackAnnotationInfo annotationInfoFromAnnotation(
      CrossProfileCallbackAnnotation annotation, Types types) {
    return CrossProfileCallbackAnnotationInfo.builder().setSimple(annotation.simple()).build();
  }

  @Override
  protected CrossProfileCallbackAnnotationInfo emptyAnnotationInfo(Elements elements) {
    return CrossProfileCallbackAnnotationInfo.builder().setSimple(false).build();
  }
}
