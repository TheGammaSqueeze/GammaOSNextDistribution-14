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

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.UserHandle;
import android.os.UserManager;
import com.google.android.enterprise.connectedapps.annotations.AvailabilityRestrictions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utility methods for acting on profiles. These methods should only be used by the SDK. */
class CrossProfileSDKUtilities {
  private static boolean isRunningOnWorkProfileCached = false;
  private static boolean isRunningOnWorkProfile = false;

  static boolean isRunningOnWorkProfile(Context context) {
    if (!isRunningOnWorkProfileCached) {
      calculateIsRunningOnWorkProfile(context);
    }
    return isRunningOnWorkProfile;
  }

  /**
   * Set the {@code isRunningOnWorkProfile} field and return whether or not we can cache this value.
   */
  private static void calculateIsRunningOnWorkProfile(Context context) {
    UserManager userManager = context.getSystemService(UserManager.class);
    isRunningOnWorkProfileCached = true; // By default we cache the result of this calculation

    if (VERSION.SDK_INT >= VERSION_CODES.R) {
      isRunningOnWorkProfile = userManager.isManagedProfile();
      return;
    }
    if (userManager.getUserProfiles().size() < 2) {
      // This accounts for situations where a personal profile has management.
      isRunningOnWorkProfile = false;
      // we can't cache it as this case is also entered if we are on the work profile but it's not
      // fully configured
      isRunningOnWorkProfileCached = false;
      return;
    }

    DevicePolicyManager devicePolicyManager = context.getSystemService(DevicePolicyManager.class);
    PackageManager packageManager = context.getPackageManager();

    isRunningOnWorkProfile = false;
    for (PackageInfo pkg : packageManager.getInstalledPackages(/* flags= */ 0)) {
      if (devicePolicyManager.isProfileOwnerApp(pkg.packageName)) {
        isRunningOnWorkProfile = true;
        return;
      }
    }
  }

  static boolean isRunningOnPersonalProfile(Context context) {
    return !isRunningOnWorkProfile(context);
  }

  /**
   * Deterministically select the user to bind to.
   *
   * <p>This will ensure that on a device with multiple profiles, we bind to the same one
   * consistently.
   */
  @Nullable
  static UserHandle selectUserHandleToBind(Context context, List<UserHandle> userHandles) {
    if (userHandles.isEmpty()) {
      return null;
    }

    UserManager userManager = context.getSystemService(UserManager.class);

    return Collections.min(
        userHandles,
        (o1, o2) ->
            (int)
                (userManager.getSerialNumberForUser(o1) - userManager.getSerialNumberForUser(o2)));
  }

  /** Filter out users according to the passed {@link AvailabilityRestrictions}. */
  static List<UserHandle> filterUsersByAvailabilityRestrictions(
      Context context,
      List<UserHandle> userHandles,
      AvailabilityRestrictions availabilityRestrictions) {
    List<UserHandle> filteredUserHandles = new ArrayList<>();
    UserManager userManager = context.getSystemService(UserManager.class);

    for (UserHandle userHandle : userHandles) {
      if (!userManager.isUserRunning(userHandle)) {
        continue;
      }
      if (userManager.isQuietModeEnabled(userHandle)) {
        continue;
      }
      if (availabilityRestrictions.requireUnlocked && !userManager.isUserUnlocked(userHandle)) {
        continue;
      }

      filteredUserHandles.add(userHandle);
    }

    return filteredUserHandles;
  }

  /** Should only be used during tests where the profile state may change during a single run. */
  static void clearCache() {
    isRunningOnWorkProfileCached = false;
  }

  private CrossProfileSDKUtilities() {}
}
