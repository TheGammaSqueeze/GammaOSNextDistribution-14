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

/**
 * Describes the UID type for a package.
 */
@Backing(type="int")
enum UidType {
  /**
   * Unknown UID type.
   */
  UNKNOWN,

  /**
   * UID belongs to a native service.
   */
  NATIVE,

  /**
   * UID belongs to an application.
   */
  APPLICATION,
}
