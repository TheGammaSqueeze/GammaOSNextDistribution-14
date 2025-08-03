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

/** Prints annotations as they would appear in source code. */
public interface AnnotationPrinter {

  String crossProfileAsAnnotation();

  String crossProfileAsAnnotation(String content);

  String crossProfileQualifiedName();

  String crossProfileCallbackAsAnnotation();

  String crossProfileCallbackAsAnnotation(String content);

  String crossProfileCallbackQualifiedName();

  String crossProfileConfigurationAsAnnotation();

  String crossProfileConfigurationAsAnnotation(String content);

  String crossProfileConfigurationQualifiedName();

  String crossProfileConfigurationsAsAnnotation(String content);

  String crossProfileConfigurationsQualifiedName();

  String crossProfileProviderAsAnnotation();

  String crossProfileProviderAsAnnotation(String content);

  String crossProfileProviderQualifiedName();

  String crossProfileTestAsAnnotation(String content);

  String crossProfileTestQualifiedName();
}
