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

import android.automotive.watchdog.IoOveruseStats;
import android.automotive.watchdog.PerStateBytes;

/**
 * Structure that describes the I/O overuse stats for a package.
 */
parcelable PackageIoOveruseStats {
  /**
   * UID of the package whose stats are stored in the below fields.
   */
  int uid;

  /**
   * Indicates whether or not to notify the overuse listeners for the package.
   */
  boolean shouldNotify;

  /**
   * Written bytes that are forgiven as they are accounted towards overuse.
   */
  PerStateBytes forgivenWriteBytes;

  /**
   * I/O overuse stats for the package.
   */
  IoOveruseStats ioOveruseStats;
}
