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
import org.checkerframework.checker.nullness.qual.Nullable;

/** Default implementation of {@link ConnectedAppsUtils}. */
class ConnectedAppsUtilsImpl implements ConnectedAppsUtils {

  private static final Profile CURRENT_PROFILE_IDENTIFIER = Profile.fromInt(0);
  private static final Profile OTHER_PROFILE_IDENTIFIER = Profile.fromInt(1);

  private final Context context;
  @Nullable private final Profile primaryProfileIdentifier;

  ConnectedAppsUtilsImpl(Context context) {
    this(context, null);
  }

  ConnectedAppsUtilsImpl(Context context, Profile primaryProfileIdentifier) {
    if (context == null) {
      throw new NullPointerException();
    }
    this.context = context;
    this.primaryProfileIdentifier = primaryProfileIdentifier;
  }

  @Override
  public Profile getCurrentProfile() {
    return CURRENT_PROFILE_IDENTIFIER;
  }

  @Override
  public Profile getOtherProfile() {
    return OTHER_PROFILE_IDENTIFIER;
  }

  @Override
  @Nullable
  public Profile getPrimaryProfile() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return null;
    }
    return primaryProfileIdentifier;
  }

  @Override
  @Nullable
  public Profile getSecondaryProfile() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return null;
    }
    if (primaryProfileIdentifier == null) {
      return null;
    }
    return primaryProfileIdentifier.isCurrent()
        ? OTHER_PROFILE_IDENTIFIER
        : CURRENT_PROFILE_IDENTIFIER;
  }

  @Override
  public Profile getWorkProfile() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return getCurrentProfile();
    }
    if (runningOnWork()) {
      return getCurrentProfile();
    }
    return getOtherProfile();
  }

  @Override
  public Profile getPersonalProfile() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return getCurrentProfile();
    }
    if (runningOnPersonal()) {
      return getCurrentProfile();
    }
    return getOtherProfile();
  }

  @Override
  public boolean runningOnWork() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return false;
    }
    return CrossProfileSDKUtilities.isRunningOnWorkProfile(context);
  }

  @Override
  public boolean runningOnPersonal() {
    if (VERSION.SDK_INT < VERSION_CODES.O) {
      return false;
    }
    return CrossProfileSDKUtilities.isRunningOnPersonalProfile(context);
  }
}
