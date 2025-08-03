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

import android.automotive.watchdog.internal.PerStateIoOveruseThreshold;
import android.automotive.watchdog.internal.IoOveruseAlertThreshold;

/**
 * Structure that describes the disk I/O overuse configuration defined for system, vendor, or
 * third-party packages.
 */
parcelable IoOveruseConfiguration {
  /**
   * Component-level I/O overuse thresholds. These thresholds are used when a package isn't
   * covered by the category specific and package specific thresholds.
   */
  PerStateIoOveruseThreshold componentLevelThresholds;

  /**
   * Package specific I/O overuse thresholds for system and vendor packages. Each component must
   * provide package specific thresholds only for the packages in the current component.
   * Third-party component must define only component-level thresholds.
   */
  List<PerStateIoOveruseThreshold> packageSpecificThresholds;

  /**
   * Category specific I/O overuse thresholds for all packages that are not covered by package
   * specific thresholds and identified by one of the application category types. This field
   * must be defined only by the vendor component.
   */
  List<PerStateIoOveruseThreshold> categorySpecificThresholds;

  /**
   * Array of system-wide I/O overuse thresholds that triggers the system-wide disk I/O overuse
   * alert. This must be defined only by the system component.
   */
  List<IoOveruseAlertThreshold> systemWideThresholds;
}
