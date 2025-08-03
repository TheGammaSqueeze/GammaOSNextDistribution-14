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
package com.google.android.enterprise.connectedapps;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

class PermissionsImpl implements Permissions {

  private final Context context;
  private final ConnectionBinder binder;

  PermissionsImpl(Context context, ConnectionBinder binder) {
    if (context == null || binder == null) {
      throw new NullPointerException();
    }

    this.context = context;
    this.binder = binder;
  }

  @Override
  public boolean canMakeCrossProfileCalls() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return false;
    }
    return binder.hasPermissionToBind(context);
  }
}
