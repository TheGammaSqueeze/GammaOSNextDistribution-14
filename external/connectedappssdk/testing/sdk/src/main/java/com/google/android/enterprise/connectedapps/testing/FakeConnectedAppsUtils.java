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
package com.google.android.enterprise.connectedapps.testing;

import com.google.android.enterprise.connectedapps.ConnectedAppsUtils;
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;

class FakeConnectedAppsUtils implements ConnectedAppsUtils {

  private static final Profile CURRENT_PROFILE_IDENTIFIER = Profile.fromInt(0);
  private static final Profile OTHER_PROFILE_IDENTIFIER = Profile.fromInt(1);

  private final AbstractFakeProfileConnector fakeProfileConnector;
  private final ProfileType primaryProfileType;

  FakeConnectedAppsUtils(
      AbstractFakeProfileConnector fakeProfileConnector, ProfileType primaryProfileType) {
    if (fakeProfileConnector == null || primaryProfileType == null) {
      throw new NullPointerException();
    }
    this.fakeProfileConnector = fakeProfileConnector;
    this.primaryProfileType = primaryProfileType;
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
  public Profile getPrimaryProfile() {
    if (primaryProfileType != ProfileType.WORK && primaryProfileType != ProfileType.PERSONAL) {
      throw new IllegalStateException("No primary profile set");
    }

    return (primaryProfileType == fakeProfileConnector.runningOnProfile())
        ? CURRENT_PROFILE_IDENTIFIER
        : OTHER_PROFILE_IDENTIFIER;
  }

  @Override
  public Profile getSecondaryProfile() {
    if (primaryProfileType != ProfileType.WORK && primaryProfileType != ProfileType.PERSONAL) {
      throw new IllegalStateException("No primary profile set");
    }

    return (primaryProfileType == fakeProfileConnector.runningOnProfile())
        ? OTHER_PROFILE_IDENTIFIER
        : CURRENT_PROFILE_IDENTIFIER;
  }

  @Override
  public Profile getWorkProfile() {
    if (fakeProfileConnector.runningOnProfile() == ProfileType.WORK) {
      return getCurrentProfile();
    }
    return getOtherProfile();
  }

  @Override
  public Profile getPersonalProfile() {
    if (fakeProfileConnector.runningOnProfile() == ProfileType.WORK) {
      return getOtherProfile();
    }
    return getCurrentProfile();
  }

  @Override
  public boolean runningOnWork() {
    return fakeProfileConnector.runningOnProfile() == ProfileType.WORK;
  }

  @Override
  public boolean runningOnPersonal() {
    return fakeProfileConnector.runningOnProfile() == ProfileType.PERSONAL;
  }
}
