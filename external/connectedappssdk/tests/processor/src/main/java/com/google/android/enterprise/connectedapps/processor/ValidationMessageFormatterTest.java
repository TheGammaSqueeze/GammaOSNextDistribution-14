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
package com.google.android.enterprise.connectedapps.processor;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationFinder;
import com.google.android.enterprise.connectedapps.processor.annotationdiscovery.AnnotationNames;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ValidationMessageFormatterTest {

  private static final String ERROR_MESSAGE =
      "Methods annotated @CROSS_PROFILE_ANNOTATION should also be annotated"
          + " @CROSS_PROFILE_CALLBACK_ANNOTATION(simple=true) and"
          + " @CROSS_PROFILE_PROVIDER_ANNOTATION and @CROSS_PROFILE_CONFIGURATION_ANNOTATION and"
          + " @CROSS_PROFILE_CONFIGURATIONS_ANNOTATION and @CROSS_PROFILE_TEST_ANNOTATION";

  @Test
  public void crossProfileAnnotationNames_formatsCorrectly() {
    AnnotationNames annotationNames = AnnotationFinder.crossProfileAnnotationNames();

    assertThat(ValidationMessageFormatter.forAnnotations(annotationNames).format(ERROR_MESSAGE))
        .isEqualTo(
            "Methods annotated @CrossProfile should also be annotated"
                + " @CrossProfileCallback(simple=true) and @CrossProfileProvider and"
                + " @CrossProfileConfiguration and @CrossProfileConfigurations and"
                + " @CrossProfileTest");
  }

  @Test
  public void crossUserAnnotationNames_formatsCorrectly() {
    AnnotationNames annotationNames = AnnotationFinder.crossUserAnnotationNames();

    assertThat(ValidationMessageFormatter.forAnnotations(annotationNames).format(ERROR_MESSAGE))
        .isEqualTo(
            "Methods annotated @CrossUser should also be annotated @CrossUserCallback(simple=true)"
                + " and @CrossUserProvider and @CrossUserConfiguration and"
                + " @CrossUserConfigurations and @CrossUserTest");
  }
}
