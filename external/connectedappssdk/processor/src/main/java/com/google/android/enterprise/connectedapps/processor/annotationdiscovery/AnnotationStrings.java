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

import com.google.auto.value.AutoValue;
import java.lang.annotation.Annotation;

/** Provides the raw string names for cross-profile annotations. */
@AutoValue
public abstract class AnnotationStrings
    implements AnnotationNames, AnnotationPrinter, AnnotationClasses {

  @Override
  public String crossProfile() {
    return crossProfileAnnotationClass().getSimpleName();
  }

  @Override
  public String crossProfileAsAnnotation() {
    return asAnnotation(crossProfile());
  }

  @Override
  public String crossProfileAsAnnotation(String content) {
    return asAnnotationWithContent(crossProfile(), content);
  }

  @Override
  public String crossProfileQualifiedName() {
    return crossProfileAnnotationClass().getCanonicalName();
  }

  @Override
  public String crossProfileCallback() {
    return crossProfileCallbackAnnotationClass().getSimpleName();
  }

  @Override
  public String crossProfileCallbackAsAnnotation() {
    return asAnnotation(crossProfileCallback());
  }

  @Override
  public String crossProfileCallbackAsAnnotation(String content) {
    return crossProfileCallbackAsAnnotation() + "(" + content + ")";
  }

  @Override
  public String crossProfileCallbackQualifiedName() {
    return crossProfileCallbackAnnotationClass().getCanonicalName();
  }

  @Override
  public String crossProfileConfiguration() {
    return crossProfileConfigurationAnnotationClass().getSimpleName();
  }

  @Override
  public String crossProfileConfigurationAsAnnotation() {
    return asAnnotation(crossProfileConfiguration());
  }

  @Override
  public String crossProfileConfigurationAsAnnotation(String content) {
    return asAnnotationWithContent(crossProfileConfiguration(), content);
  }

  @Override
  public String crossProfileConfigurationQualifiedName() {
    return crossProfileConfigurationAnnotationClass().getCanonicalName();
  }

  @Override
  public String crossProfileConfigurations() {
    return crossProfileConfigurationsAnnotationClass().getSimpleName();
  }

  @Override
  public String crossProfileConfigurationsAsAnnotation(String content) {
    return asAnnotationWithContent(crossProfileProvider(), content);
  }

  @Override
  public String crossProfileConfigurationsQualifiedName() {
    return crossProfileConfigurationsAnnotationClass().getCanonicalName();
  }

  @Override
  public String crossProfileProvider() {
    return crossProfileProviderAnnotationClass().getSimpleName();
  }

  @Override
  public String crossProfileProviderAsAnnotation() {
    return asAnnotation(crossProfileProvider());
  }

  @Override
  public String crossProfileProviderAsAnnotation(String content) {
    return asAnnotationWithContent(crossProfileProvider(), content);
  }

  @Override
  public String crossProfileProviderQualifiedName() {
    return crossProfileProviderAnnotationClass().getCanonicalName();
  }

  @Override
  public String crossProfileTest() {
    return crossProfileTestAnnotationClass().getSimpleName();
  }

  @Override
  public String crossProfileTestAsAnnotation(String content) {
    return asAnnotationWithContent(crossProfileTest(), content);
  }

  @Override
  public String crossProfileTestQualifiedName() {
    return crossProfileTestAnnotationClass().getCanonicalName();
  }

  @Override
  public final String toString() {
    return crossProfile() + " AnnotationStrings";
  }

  private static String asAnnotation(String annotationName) {
    return "@" + annotationName;
  }

  private static String asAnnotationWithContent(String annotationName, String content) {
    return "@" + annotationName + "(" + content + ")";
  }

  @Override
  public abstract Class<? extends Annotation> crossProfileAnnotationClass();

  @Override
  public abstract Class<? extends Annotation> crossProfileCallbackAnnotationClass();

  @Override
  public abstract Class<? extends Annotation> crossProfileConfigurationAnnotationClass();

  @Override
  public abstract Class<? extends Annotation> crossProfileConfigurationsAnnotationClass();

  @Override
  public abstract Class<? extends Annotation> crossProfileProviderAnnotationClass();

  @Override
  public abstract Class<? extends Annotation> crossProfileTestAnnotationClass();

  static Builder builder() {
    return new AutoValue_AnnotationStrings.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setCrossProfileAnnotationClass(Class<? extends Annotation> value);

    abstract Builder setCrossProfileCallbackAnnotationClass(Class<? extends Annotation> value);

    abstract Builder setCrossProfileConfigurationAnnotationClass(Class<? extends Annotation> value);

    abstract Builder setCrossProfileConfigurationsAnnotationClass(
        Class<? extends Annotation> value);

    abstract Builder setCrossProfileProviderAnnotationClass(Class<? extends Annotation> value);

    abstract Builder setCrossProfileTestAnnotationClass(Class<? extends Annotation> value);

    abstract AnnotationStrings build();
  }
}
