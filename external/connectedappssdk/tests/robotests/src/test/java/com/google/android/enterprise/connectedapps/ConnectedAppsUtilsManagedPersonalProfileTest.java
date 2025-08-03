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
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link ConnectedAppsUtils} when running on a personal profile which has management.
 *
 * <p>This is on a device which has only one profile.
 *
 * <p>This should behave as if running on a personal profile, not a work profile.
 */
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class ConnectedAppsUtilsManagedPersonalProfileTest {

  private final Application context = ApplicationProvider.getApplicationContext();
  private final ConnectedAppsUtils connectedAppsUtils = new ConnectedAppsUtilsImpl(context);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(context, /* scheduledExecutorService= */ null);

  @Before
  public void setUp() {
    Service profileAwareService = Robolectric.setupService(TestApplication.getService());
    testUtilities.initTests();
    IBinder binder = profileAwareService.onBind(/* intent= */ null);
    testUtilities.setBinding(binder, CrossProfileConnector.class.getName());
  }

  @Test
  public void getPersonalProfile_runningOnPersonalProfileWithManagement_returnsCurrentProfile() {
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setHasProfileOwner();

    assertThat(connectedAppsUtils.getPersonalProfile())
        .isEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void getWorkProfile_runningOnPersonalProfileWithManagement_returnsDifferentToCurrent() {
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setHasProfileOwner();

    assertThat(connectedAppsUtils.getWorkProfile())
        .isNotEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void runningOnWork_runningOnPersonalProfileWithManagement_returnsFalse() {
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setHasProfileOwner();

    assertThat(connectedAppsUtils.runningOnWork()).isFalse();
  }

  @Test
  public void runningOnPersonal_runningOnPersonalProfileWithManagement_returnsTrue() {
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setHasProfileOwner();

    assertThat(connectedAppsUtils.runningOnPersonal()).isTrue();
  }
}
