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

import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.ElementKind.METHOD;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileCallback;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfigurations;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileProvider;
import com.google.android.enterprise.connectedapps.annotations.CrossUser;
import com.google.android.enterprise.connectedapps.annotations.CrossUserCallback;
import com.google.android.enterprise.connectedapps.annotations.CrossUserConfiguration;
import com.google.android.enterprise.connectedapps.annotations.CrossUserConfigurations;
import com.google.android.enterprise.connectedapps.annotations.CrossUserProvider;
import com.google.android.enterprise.connectedapps.processor.ValidationMessageFormatter;
import com.google.android.enterprise.connectedapps.processor.containers.Context;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileCallbackAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileConfigurationsAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileProviderAnnotationInfo;
import com.google.android.enterprise.connectedapps.processor.containers.CrossProfileTestAnnotationInfo;
import com.google.android.enterprise.connectedapps.testing.annotations.CrossProfileTest;
import com.google.android.enterprise.connectedapps.testing.annotations.CrossUserTest;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Helper methods to discover all cross-profile annotations of a specific type on elements. */
public final class AnnotationFinder {

  private static final AnnotationStrings CROSS_PROFILE_ANNOTATION_STRINGS =
      AnnotationStrings.builder()
          .setCrossProfileAnnotationClass(CrossProfile.class)
          .setCrossProfileCallbackAnnotationClass(CrossProfileCallback.class)
          .setCrossProfileConfigurationAnnotationClass(CrossProfileConfiguration.class)
          .setCrossProfileConfigurationsAnnotationClass(CrossProfileConfigurations.class)
          .setCrossProfileProviderAnnotationClass(CrossProfileProvider.class)
          .setCrossProfileTestAnnotationClass(CrossProfileTest.class)
          .build();

  private static final AnnotationStrings CROSS_USER_ANNOTATION_STRINGS =
      AnnotationStrings.builder()
          .setCrossProfileAnnotationClass(CrossUser.class)
          .setCrossProfileCallbackAnnotationClass(CrossUserCallback.class)
          .setCrossProfileProviderAnnotationClass(CrossUserProvider.class)
          .setCrossProfileConfigurationAnnotationClass(CrossUserConfiguration.class)
          .setCrossProfileConfigurationsAnnotationClass(CrossUserConfigurations.class)
          .setCrossProfileTestAnnotationClass(CrossUserTest.class)
          .build();

  private static final ImmutableList<AnnotationStrings> SUPPORTED_ANNOTATIONS =
      ImmutableList.of(CROSS_PROFILE_ANNOTATION_STRINGS, CROSS_USER_ANNOTATION_STRINGS);

  private static final AnnotationStrings DEFAULT_ANNOTATIONS = CROSS_PROFILE_ANNOTATION_STRINGS;

  private static final Set<Class<? extends Annotation>> crossProfileAnnotations =
      annotationsOfType(AnnotationClasses::crossProfileAnnotationClass);

  private static final Set<Class<? extends Annotation>> crossProfileCallbackAnnotations =
      annotationsOfType(AnnotationClasses::crossProfileCallbackAnnotationClass);

  private static final Set<Class<? extends Annotation>> crossProfileConfigurationAnnotations =
      annotationsOfType(AnnotationClasses::crossProfileConfigurationAnnotationClass);

  private static final Set<Class<? extends Annotation>> crossProfileConfigurationsAnnotations =
      annotationsOfType(AnnotationClasses::crossProfileConfigurationsAnnotationClass);

  private static final Set<Class<? extends Annotation>> crossProfileProviderAnnotations =
      annotationsOfType(AnnotationClasses::crossProfileProviderAnnotationClass);

  private static final Set<Class<? extends Annotation>> crossProfileTestAnnotations =
      annotationsOfType(AnnotationClasses::crossProfileTestAnnotationClass);

  public static Iterable<AnnotationStrings> annotationStrings() {
    return SUPPORTED_ANNOTATIONS;
  }

  public static AnnotationNames crossProfileAnnotationNames() {
    return CROSS_PROFILE_ANNOTATION_STRINGS;
  }

  public static AnnotationNames crossUserAnnotationNames() {
    return CROSS_USER_ANNOTATION_STRINGS;
  }

  public static ValidationMessageFormatter validationMessageFormatterFor(Element element) {
    return ValidationMessageFormatter.forAnnotations(annotationNamesFor(element));
  }

  private static AnnotationNames annotationNamesFor(Element element) {
    for (AnnotationStrings annotationStrings : SUPPORTED_ANNOTATIONS) {
      if (hasAnyAnnotationsOfClass(element, annotationStrings)) {
        return annotationStrings;
      }
    }

    return DEFAULT_ANNOTATIONS;
  }

  public static ValidationMessageFormatter validationMessageFormatterForClass(
      TypeElement typeElement) {
    return ValidationMessageFormatter.forAnnotations(annotationNamesForClass(typeElement));
  }

  public static AnnotationNames annotationNamesForClass(TypeElement typeElement) {
    for (AnnotationStrings annotationStrings : SUPPORTED_ANNOTATIONS) {
      if (hasAnyAnnotationsOfClass(typeElement, annotationStrings)) {
        return annotationStrings;
      }

      for (ExecutableElement method :
          typeElement.getEnclosedElements().stream()
              .filter(element -> element.getKind() == METHOD)
              .map(element -> (ExecutableElement) element)
              .collect(toSet())) {
        if (hasAnyAnnotationsOfClass(method, annotationStrings)) {
          return annotationStrings;
        }
      }
    }

    return DEFAULT_ANNOTATIONS;
  }

  private static boolean hasAnyAnnotationsOfClass(
      Element element, AnnotationClasses annotationClasses) {
    return hasAnnotationOfClass(element, annotationClasses.crossProfileAnnotationClass())
        || hasAnnotationOfClass(element, annotationClasses.crossProfileCallbackAnnotationClass())
        || hasAnnotationOfClass(element, annotationClasses.crossProfileProviderAnnotationClass())
        || hasAnnotationOfClass(
            element, annotationClasses.crossProfileConfigurationAnnotationClass())
        || hasAnnotationOfClass(
            element, annotationClasses.crossProfileConfigurationsAnnotationClass())
        || hasAnnotationOfClass(element, annotationClasses.crossProfileTestAnnotationClass());
  }

  private static boolean hasAnnotationOfClass(
      Element element, Class<? extends Annotation> annotationClass) {
    return element.getAnnotation(annotationClass) != null;
  }

  public static CrossProfileAnnotationInfo extractCrossProfileAnnotationInfo(
      Context context, Element annotatedElement) {
    return new CrossProfileAnnotationInfoExtractor()
        .extractAnnotationInfo(SUPPORTED_ANNOTATIONS, context, annotatedElement);
  }

  public static CrossProfileCallbackAnnotationInfo extractCrossProfileCallbackAnnotationInfo(
      Context context, Element annotatedElement) {
    return new CrossProfileCallbackAnnotationInfoExtractor()
        .extractAnnotationInfo(SUPPORTED_ANNOTATIONS, context, annotatedElement);
  }

  public static CrossProfileConfigurationAnnotationInfo
      extractCrossProfileConfigurationAnnotationInfo(Context context, Element annotatedElement) {
    return new CrossProfileConfigurationAnnotationInfoExtractor()
        .extractAnnotationInfo(SUPPORTED_ANNOTATIONS, context, annotatedElement);
  }

  public static CrossProfileConfigurationsAnnotationInfo
      extractCrossProfileConfigurationsAnnotationInfo(Context context, Element annotatedElement) {
    return new CrossProfileConfigurationsAnnotationInfoExtractor()
        .extractAnnotationInfo(SUPPORTED_ANNOTATIONS, context, annotatedElement);
  }

  public static CrossProfileProviderAnnotationInfo extractCrossProfileProviderAnnotationInfo(
      Context context, Element annotatedElement) {
    return new CrossProfileProviderAnnotationInfoExtractor()
        .extractAnnotationInfo(SUPPORTED_ANNOTATIONS, context, annotatedElement);
  }

  public static CrossProfileTestAnnotationInfo extractCrossProfileTestAnnotationInfo(
      Context context, Element annotatedElement) {
    return new CrossProfileTestAnnotationInfoExtractor()
        .extractAnnotationInfo(SUPPORTED_ANNOTATIONS, context, annotatedElement);
  }

  public static boolean hasCrossProfileAnnotation(Element element) {
    return hasAnyAnnotations(element, crossProfileAnnotations);
  }

  public static boolean hasCrossProfileCallbackAnnotation(Element element) {
    return hasAnyAnnotations(element, crossProfileCallbackAnnotations);
  }

  public static boolean hasCrossProfileConfigurationAnnotation(Element element) {
    return hasAnyAnnotations(element, crossProfileConfigurationAnnotations);
  }

  public static boolean hasCrossProfileConfigurationsAnnotation(Element element) {
    return hasAnyAnnotations(element, crossProfileConfigurationsAnnotations);
  }

  public static boolean hasCrossProfileProviderAnnotation(Element element) {
    return hasAnyAnnotations(element, crossProfileProviderAnnotations);
  }

  private static boolean hasAnyAnnotations(
      Element element, Set<Class<? extends Annotation>> annotations) {
    return annotations.stream().anyMatch(annotation -> element.getAnnotation(annotation) != null);
  }

  public static Stream<? extends Element> elementsAnnotatedWithCrossProfile(
      RoundEnvironment roundEnv) {
    return findElementsContainingAnnotations(roundEnv, crossProfileAnnotations);
  }

  public static Stream<? extends Element> elementsAnnotatedWithCrossProfileCallback(
      RoundEnvironment roundEnv) {
    return findElementsContainingAnnotations(roundEnv, crossProfileCallbackAnnotations);
  }

  public static Stream<? extends Element> elementsAnnotatedWithCrossProfileConfiguration(
      RoundEnvironment roundEnv) {
    return findElementsContainingAnnotations(roundEnv, crossProfileConfigurationAnnotations);
  }

  public static Stream<? extends Element> elementsAnnotatedWithCrossProfileConfigurations(
      RoundEnvironment roundEnv) {
    return findElementsContainingAnnotations(roundEnv, crossProfileConfigurationsAnnotations);
  }

  public static Stream<? extends Element> elementsAnnotatedWithCrossProfileProvider(
      RoundEnvironment roundEnv) {
    return findElementsContainingAnnotations(roundEnv, crossProfileProviderAnnotations);
  }

  public static Stream<? extends Element> elementsAnnotatedWithCrossProfileTest(
      RoundEnvironment roundEnv) {
    return findElementsContainingAnnotations(roundEnv, crossProfileTestAnnotations);
  }

  private static Stream<? extends Element> findElementsContainingAnnotations(
      RoundEnvironment roundEnv, Set<Class<? extends Annotation>> annotations) {
    return annotations.stream()
        .flatMap(annotation -> roundEnv.getElementsAnnotatedWith(annotation).stream());
  }

  private static Set<Class<? extends Annotation>> annotationsOfType(
      Function<AnnotationClasses, Class<? extends Annotation>> annotationClassGetter) {
    return SUPPORTED_ANNOTATIONS.stream().map(annotationClassGetter).collect(toSet());
  }

  private AnnotationFinder() {}
}
