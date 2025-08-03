/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.automotive.watchdog.internal.ComponentType;
import android.automotive.watchdog.internal.PackageMetadata;
import android.automotive.watchdog.internal.ResourceSpecificConfiguration;

/**
 * Structure that describes the resource overuse configuration for a component.
 */
parcelable ResourceOveruseConfiguration {
  /**
   * Type of the component.
   */
  ComponentType componentType;

  /**
   * List of only non-critical system and vendor packages that are safe to kill on disk I/O
   * overuse. All third-party packages are considered safe to kill.
   */
  @utf8InCpp List<String> safeToKillPackages;

  /**
   * Defines the list of vendor package prefixes. Any pre-installed package name starting with one
   * of these prefixes will be identified as a vendor package in addition to packages under the
   * vendor partition. This must be defined only by the vendor component.
   */
  @utf8InCpp List<String> vendorPackagePrefixes;

  /**
   * Defines the package metadata.
   */
  List<PackageMetadata> packageMetadata;

  /**
   * Defines resource specific overuse configurations for the current component.
   */
  List<ResourceSpecificConfiguration> resourceSpecificConfigurations;
}
