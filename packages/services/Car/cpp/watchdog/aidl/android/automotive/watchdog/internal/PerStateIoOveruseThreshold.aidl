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

package android.automotive.watchdog.internal;

import android.automotive.watchdog.PerStateBytes;

/**
 * Structure that describes the disk I/O overuse threshold for each application and system state of
 * a package or packages that fall under a component or category.
 */
parcelable PerStateIoOveruseThreshold {
  /**
   * Contains
   * 1. string equivalent of ComponentType enum for component-level thresholds.
   * 2. package name for package specific thresholds.
   * 3. string equivalent of ApplicationCategoryType enum for category specific thresholds.
   */
  @utf8InCpp String name;

  /**
   * Defines the I/O overuse thresholds for a package. The thresholds are defined in terms of
   * the number of bytes written to disk during each application and system states.
   */
  PerStateBytes perStateWriteBytes;
}
