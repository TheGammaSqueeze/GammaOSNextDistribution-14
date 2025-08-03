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

import android.automotive.watchdog.internal.ApplicationCategoryType;
import android.automotive.watchdog.internal.ComponentType;
import android.automotive.watchdog.internal.PackageIdentifier;
import android.automotive.watchdog.internal.UidType;

/**
 * Describes package information.
 */
parcelable PackageInfo {
  /**
   * Identifier for the package whose information is stored in the below fields.
   */
  PackageIdentifier packageIdentifier;

  /**
   * UID type.
   */
  UidType uidType;

  /**
   * List of packages owned by the package. This list is empty when the UID is not a shared UID.
   */
  @utf8InCpp List<String> sharedUidPackages;

  /**
   * Component type of the package and the owned packages.
   */
  ComponentType componentType;

  /**
   * Application category type of the package and the owned packages.
   */
  ApplicationCategoryType appCategoryType = ApplicationCategoryType.OTHERS;
}
