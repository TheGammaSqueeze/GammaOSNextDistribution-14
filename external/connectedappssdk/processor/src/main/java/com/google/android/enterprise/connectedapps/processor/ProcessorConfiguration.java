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

/** General configuration. */
public final class ProcessorConfiguration {
  private ProcessorConfiguration() {}

  /**
   * When {@code true}, will generate a copy of each parcelable and future wrapper for each type
   * which requires it.
   *
   * <p>This is required to ensure that there are no conflicts due to duplicate classes being
   * generated in separate targets.
   */
  public static final boolean GENERATE_TYPE_SPECIFIC_WRAPPERS = true;
}
