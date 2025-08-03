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

import static android.Manifest.permission.INTERACT_ACROSS_PROFILES;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.UserHandle;
import android.util.Log;
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import com.google.android.enterprise.connectedapps.exceptions.MissingApiException;

/**
 * Abstract {@link ConnectionBinder} which allows subclasses to define the {@link Intent} to bind
 * to.
 *
 * <p>Methods expect that the app has INTERACT_ACROSS_USERS or INTERACT_ACROSS_PROFILES permission.
 */
public abstract class AbstractProfileBinder implements ConnectionBinder {

  private boolean hasCachedPermissionRequests = false;
  private boolean requestsInteractAcrossProfiles = false;
  private boolean requestsInteractAcrossUsers = false;
  private boolean requestsInteractAcrossUsersFull = false;

  private static final String INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS";
  private static final String INTERACT_ACROSS_USERS_FULL =
      "android.permission.INTERACT_ACROSS_USERS_FULL";

  protected abstract Intent createIntent(Context context, ComponentName bindToService);

  @Override
  public boolean tryBind(
      Context context,
      ComponentName bindToService,
      ServiceConnection connection,
      AvailabilityRestrictions availabilityRestrictions)
      throws MissingApiException {
    UserHandle otherUserHandle =
        CrossProfileSender.getOtherUserHandle(context, availabilityRestrictions);

    if (otherUserHandle == null) {
      // There is no user to bind to but there might be later
      return false;
    }

    Intent bindIntent = createIntent(context, bindToService);

    boolean hasBound =
        ReflectionUtilities.bindServiceAsUser(context, bindIntent, connection, otherUserHandle);
    if (!hasBound) {
      context.unbindService(connection);
    }
    return hasBound;
  }

  @Override
  public boolean bindingIsPossible(
      Context context, AvailabilityRestrictions availabilityRestrictions) {
    UserHandle otherUserHandle =
        CrossProfileSender.getOtherUserHandle(context, availabilityRestrictions);
    return otherUserHandle != null;
  }

  @Override
  public boolean hasPermissionToBind(Context context) {
    cachePermissionRequests(context);

    if (VERSION.SDK_INT >= VERSION_CODES.R
        && requestsInteractAcrossProfiles
        && context.getSystemService(CrossProfileApps.class).canInteractAcrossProfiles()) {
      return true;
    }
    if (requestsInteractAcrossUsersFull
        && context.checkSelfPermission(INTERACT_ACROSS_USERS_FULL)
            == PackageManager.PERMISSION_GRANTED) {
      return true;
    }
    if (requestsInteractAcrossUsers
        && context.checkSelfPermission(INTERACT_ACROSS_USERS)
            == PackageManager.PERMISSION_GRANTED) {
      return true;
    }

    return false;
  }

  private void cachePermissionRequests(Context context) {
    if (hasCachedPermissionRequests) {
      return;
    }

    PackageManager packageManager = context.getPackageManager();
    try {
      PackageInfo packageInfo =
          packageManager.getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);

      if (packageInfo == null || packageInfo.requestedPermissions == null) {
        hasCachedPermissionRequests = true;
        return;
      }

      for (String permission : packageInfo.requestedPermissions) {
        if (permission.equals(INTERACT_ACROSS_PROFILES)) {
          requestsInteractAcrossProfiles = true;
        } else if (permission.equals(INTERACT_ACROSS_USERS)) {
          requestsInteractAcrossUsers = true;
        } else if (permission.equals(INTERACT_ACROSS_USERS_FULL)) {
          requestsInteractAcrossUsersFull = true;
        }
      }
    } catch (NameNotFoundException e) {
      Log.e("AbstractProfileBinder", "Could not find package.", e);
      requestsInteractAcrossProfiles = false;
      requestsInteractAcrossUsers = false;
      requestsInteractAcrossUsersFull = false;
    }

    hasCachedPermissionRequests = true;
  }
}
