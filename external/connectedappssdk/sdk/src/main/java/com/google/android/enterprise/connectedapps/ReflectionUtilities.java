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
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.UserHandle;
import android.util.Log;
import com.google.android.enterprise.connectedapps.exceptions.MissingApiException;
import java.lang.reflect.InvocationTargetException;

/**
 * Utility functions when using reflection to access APIs.
 */
public class ReflectionUtilities {

  private static final String LOG_TAG = "ReflectionUtilities";
  private static final String BIND_SERVICE_AS_USER_METHOD_NAME = "bindServiceAsUser";

  private static boolean canUseReflectedApisIsCached = false;
  private static boolean canUseReflectedApis = false;

  /**
   * Check that needed APIs are available and can be run.
   *
   * <p>This should be checked before calling any other methods on this class.
   */
  static boolean canUseReflectedApis() {
    if (canUseReflectedApisIsCached) {
      return canUseReflectedApis;
    }

    try {
      Context.class.getMethod(
          BIND_SERVICE_AS_USER_METHOD_NAME,
          Intent.class,
          ServiceConnection.class,
          int.class,
          UserHandle.class);
      canUseReflectedApis = true;
      canUseReflectedApisIsCached = true;
      return true;
    } catch (NoSuchMethodException e) {
      // One of the methods cannot be called
      Log.e(LOG_TAG, "canUseReflectedApis is false", e);
      canUseReflectedApis = false;
      canUseReflectedApisIsCached = true;
      return false;
    }
  }

  /**
   * Bind a {@link ServiceConnection} to a different running user.
   *
   * <p>Reflection is required for this call because {@code Context#bindServiceAsUser} was not
   * made public until Android R.
   *
   * <p>This must only be called if {@link #canUseReflectedApis()} has returned true.
   */
  public static boolean bindServiceAsUser(
      Context context, Intent bindIntent, ServiceConnection connection, UserHandle otherUserHandle)
      throws MissingApiException {
    try {
      return (Boolean)
          context
              .getClass()
              .getMethod(
                  BIND_SERVICE_AS_USER_METHOD_NAME,
                  Intent.class,
                  ServiceConnection.class,
                  int.class,
                  UserHandle.class)
              .invoke(context, bindIntent, connection, Context.BIND_AUTO_CREATE, otherUserHandle);
    } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      // This indicates that this version of framework doesn't support the APIs needed - binding
      // will not be possible
      throw new MissingApiException("Error binding to other profile", e);
    }
  }

  private ReflectionUtilities() {}
}
