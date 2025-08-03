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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Given an annotation, forwards method calls from a mock instance of its interface to its actual
 * instance.
 *
 * <p>This allows us to treat separate annotations with identical interfaces polymorphically.
 */
class AnnotationInvocationHandler implements InvocationHandler {

  private final Annotation annotation;

  AnnotationInvocationHandler(Annotation annotation) {
    this.annotation = annotation;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return invokeAndUnwrapExceptions(annotation.annotationType().getMethod(method.getName()));
  }

  private Object invokeAndUnwrapExceptions(Method method) throws Throwable {
    try {
      return method.invoke(annotation);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }
}
