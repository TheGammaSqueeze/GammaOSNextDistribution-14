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

import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class ProfileTest {

  private final Application context = ApplicationProvider.getApplicationContext();
  private final ConnectedAppsUtils connectedAppsUtils = new ConnectedAppsUtilsImpl(context);

  @Test
  public void isCurrent_currentProfile_returnsTrue() {
    Profile identifier = connectedAppsUtils.getCurrentProfile();

    assertThat(identifier.isCurrent()).isTrue();
  }

  @Test
  public void isCurrent_notCurrent_returnsFalse() {
    Profile identifier = connectedAppsUtils.getOtherProfile();

    assertThat(identifier.isCurrent()).isFalse();
  }

  @Test
  public void isOther_otherProfile_returnsTrue() {
    Profile identifier = connectedAppsUtils.getOtherProfile();

    assertThat(identifier.isOther()).isTrue();
  }

  @Test
  public void isOther_notOtherProfile_returnsFalse() {
    Profile identifier = connectedAppsUtils.getCurrentProfile();

    assertThat(identifier.isOther()).isFalse();
  }

  @Test
  public void fromInt_intFromCurrentProfile_equalsCurrentProfile() {
    Profile identifier = connectedAppsUtils.getCurrentProfile();

    assertThat(Profile.fromInt(identifier.asInt())).isEqualTo(identifier);
  }

  @Test
  public void fromInt_intFromOtherProfile_equalsOtherProfile() {
    Profile identifier = connectedAppsUtils.getCurrentProfile();

    assertThat(Profile.fromInt(identifier.asInt())).isEqualTo(identifier);
  }
}
