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

package com.android.car.debuggingrestrictioncontroller.ui.login;

import androidx.annotation.Nullable;

/** Authentication result : success (user details) or error message. */
class LoginResult {

  @Nullable private LoggedInUserView success;
  @Nullable private Integer error;

  LoginResult(@Nullable Integer error) {
    this.error = error;
  }

  LoginResult(@Nullable LoggedInUserView success) {
    this.success = success;
  }

  @Nullable
  LoggedInUserView getSuccess() {
    return success;
  }

  @Nullable
  Integer getError() {
    return error;
  }
}
