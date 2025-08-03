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

import com.google.android.enterprise.connectedapps.annotations.CrossProfileProvider;
import com.google.android.enterprise.connectedapps.annotations.CrossUserProvider;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.interfaces.CrossProfileProviderAnnotation;
import com.google.android.enterprise.connectedapps.processor.containers.Context;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * An extractor which generates {@link AnnotationInfoT} for elements annotated with annotations that
 * conform to {@link AnnotationInterfaceT}.
 */
abstract class AnnotationInfoExtractor<AnnotationInfoT, AnnotationInterfaceT> {

  private final Class<AnnotationInterfaceT> annotationInterfaceClass;

  AnnotationInfoExtractor(Class<AnnotationInterfaceT> annotationInterfaceClass) {
    this.annotationInterfaceClass = annotationInterfaceClass;
  }

  /**
   * Returns the {@link AnnotationInfoT} that can be extracted from the first supported annotation
   * on {@code annotatedElement}, or a default instance otherwise.
   */
  AnnotationInfoT extractAnnotationInfo(
      Iterable<? extends AnnotationClasses> availableAnnotations,
      Context context,
      Element annotatedElement) {
    for (AnnotationClasses annotationClasses : availableAnnotations) {
      Annotation annotation =
          annotatedElement.getAnnotation(supportedAnnotationClass(annotationClasses));

      if (annotation != null) {
        return annotationInfoFromAnnotation(
            wrapAnnotationWithInterface(annotationInterfaceClass, annotation), context.types());
      }
    }

    return emptyAnnotationInfo(context.elements());
  }

  /**
   * Returns the class of the annotation type that this extractor generates {@link AnnotationInfoT}
   * for.
   *
   * <p>For example, if supporting {@link CrossProfileProvider} and {@link CrossUserProvider}
   * annotations, return the value of {@link
   * AnnotationClasses#crossProfileProviderAnnotationClass()}.
   */
  protected abstract Class<? extends Annotation> supportedAnnotationClass(
      AnnotationClasses annotationClasses);

  protected abstract AnnotationInfoT annotationInfoFromAnnotation(
      AnnotationInterfaceT annotation, Types types);

  protected abstract AnnotationInfoT emptyAnnotationInfo(Elements elements);

  /**
   * Wraps any annotation of a specific type (e.g. {@link CrossProfileProvider} and {@link
   * CrossUserProvider}) with its interface (in that case {@link CrossProfileProviderAnnotation}).
   *
   * <p>Java does not allow annotation subclassing so we use Java proxies to treat these different
   * annotations with identical interfaces polymorphically.
   */
  protected static <T> T wrapAnnotationWithInterface(
      Class<T> annotationInterfaceClass, Annotation annotation) {
    return annotationInterfaceClass.cast(
        Proxy.newProxyInstance(
            annotationInterfaceClass.getClassLoader(),
            new Class<?>[] {annotationInterfaceClass},
            new AnnotationInvocationHandler(annotation)));
  }
}
