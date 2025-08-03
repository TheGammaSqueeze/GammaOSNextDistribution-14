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

import com.google.android.enterprise.connectedapps.annotations.CrossUser;
import com.google.android.enterprise.connectedapps.annotations.CrossUserCallback;
import com.google.android.enterprise.connectedapps.annotations.CrossUserProvider;
import java.lang.annotation.Annotation;

/**
 * A set of parallel annotation classes.
 *
 * <p>For example, a valid instance could return {@link CrossUser}, {@link CrossUserCallback},
 * {@link CrossUserProvider}, etc.
 */
public interface AnnotationClasses {

  Class<? extends Annotation> crossProfileAnnotationClass();

  Class<? extends Annotation> crossProfileCallbackAnnotationClass();

  Class<? extends Annotation> crossProfileConfigurationAnnotationClass();

  Class<? extends Annotation> crossProfileConfigurationsAnnotationClass();

  Class<? extends Annotation> crossProfileProviderAnnotationClass();

  Class<? extends Annotation> crossProfileTestAnnotationClass();
}
