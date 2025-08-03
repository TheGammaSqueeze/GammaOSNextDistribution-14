/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.frameworks.automotive.powerpolicy.internal;

/**
 * Structure to store the current policy state.
 *
 * @deprecated This is not used any more.
 */
@VintfStability
parcelable PolicyState {
  /**
   * The current power policy ID.
   */
  @utf8InCpp String policyId;

  /**
   * The current power policy group ID.
   */
  @utf8InCpp String policyGroupId;
}
