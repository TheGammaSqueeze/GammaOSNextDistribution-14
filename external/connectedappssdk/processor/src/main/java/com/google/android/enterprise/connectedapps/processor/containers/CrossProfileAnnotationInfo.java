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
package com.google.android.enterprise.connectedapps.processor.containers;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableCollection;
import javax.lang.model.element.TypeElement;

/** Wrapper around information contained in an annotation of type {@link CrossProfile}. */
@AutoValue
public abstract class CrossProfileAnnotationInfo {

  public static final String DEFAULT_CONNECTOR_NAME =
      "com.google.android.enterprise.connectedapps.annotations.CrossProfile";

  public abstract TypeElement connectorClass();

  public abstract ImmutableCollection<TypeElement> parcelableWrapperClasses();

  public abstract ImmutableCollection<TypeElement> futureWrapperClasses();

  public abstract boolean isStatic();

  public boolean connectorIsDefault() {
    return connectorClass().asType().toString().equals(DEFAULT_CONNECTOR_NAME);
  }

  public static Builder builder() {
    return new AutoValue_CrossProfileAnnotationInfo.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setConnectorClass(TypeElement value);

    public abstract Builder setParcelableWrapperClasses(ImmutableCollection<TypeElement> value);

    public abstract Builder setFutureWrapperClasses(ImmutableCollection<TypeElement> value);

    public abstract Builder setIsStatic(boolean value);

    public abstract CrossProfileAnnotationInfo build();
  }
}
