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

import com.google.android.enterprise.connectedapps.annotations.CrossProfileCallback;
import com.google.auto.value.AutoValue;

/** Wrapper around information contained in an annotation of type {@link CrossProfileCallback}. */
@AutoValue
public abstract class CrossProfileCallbackAnnotationInfo {

  public abstract boolean simple();

  public static Builder builder() {
    return new AutoValue_CrossProfileCallbackAnnotationInfo.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setSimple(boolean value);

    public abstract CrossProfileCallbackAnnotationInfo build();
  }
}
