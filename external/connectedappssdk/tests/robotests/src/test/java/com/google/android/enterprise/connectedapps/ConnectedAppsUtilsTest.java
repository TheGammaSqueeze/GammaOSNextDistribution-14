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
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class ConnectedAppsUtilsTest {

  private final Application context = ApplicationProvider.getApplicationContext();
  private final ConnectedAppsUtils connectedAppsUtils = new ConnectedAppsUtilsImpl(context);
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);

  @Before
  public void setUp() {
    Service profileAwareService = Robolectric.setupService(TestApplication.getService());
    testUtilities.initTests();
    IBinder binder = profileAwareService.onBind(/* intent= */ null);
    testUtilities.setBinding(binder, CrossProfileConnector.class.getName());
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.addDefaultConnectionHolderAndWait();
  }

  @Test
  public void getPersonalProfile_runningOnPersonalProfile_returnsSameAsCurrentProfile() {
    testUtilities.setRunningOnPersonalProfile();

    assertThat(connectedAppsUtils.getPersonalProfile())
        .isEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void getPersonalProfile_runningOnWorkProfile_returnsDifferentToCurrentProfile() {
    testUtilities.setRunningOnWorkProfile();

    assertThat(connectedAppsUtils.getPersonalProfile())
        .isNotEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void getWorkProfile_runningOnWorkProfile_returnsSameAsCurrentProfile() {
    testUtilities.setRunningOnWorkProfile();

    assertThat(connectedAppsUtils.getWorkProfile())
        .isEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void getWorkProfile_runningOnPersonalProfile_returnsDifferentToCurrentProfile() {
    testUtilities.setRunningOnPersonalProfile();

    assertThat(connectedAppsUtils.getWorkProfile())
        .isNotEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void getPrimaryProfile_noPrimaryProfileSet_returnsNull() {
    assertThat(connectedAppsUtils.getPrimaryProfile()).isNull();
  }

  @Test
  public void getSecondaryProfile_noPrimaryProfileSet_returnsNull() {
    assertThat(connectedAppsUtils.getSecondaryProfile()).isNull();
  }

  @Test
  public void getPrimaryProfile_primaryProfileIsPersonal_runningOnPersonal_returnsCurrent() {
    testUtilities.setRunningOnPersonalProfile();
    ConnectedAppsUtils utils =
        new ConnectedAppsUtilsImpl(context, connectedAppsUtils.getPersonalProfile());

    assertThat(utils.getPrimaryProfile()).isEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void
      getSecondaryProfile_primaryProfileIsPersonal_runningOnPersonal_returnsDifferentToCurrent() {
    testUtilities.setRunningOnPersonalProfile();
    ConnectedAppsUtils utils =
        new ConnectedAppsUtilsImpl(context, connectedAppsUtils.getPersonalProfile());

    assertThat(utils.getSecondaryProfile()).isNotEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void getPrimaryProfile_primaryProfileIsWork_runningOnPersonal_returnsDifferentToCurrent() {
    testUtilities.setRunningOnPersonalProfile();
    ConnectedAppsUtils utils =
        new ConnectedAppsUtilsImpl(context, connectedAppsUtils.getWorkProfile());

    assertThat(utils.getPrimaryProfile()).isNotEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void getSecondaryProfile_primaryProfileIsWork_runningOnPersonal_returnsCurrent() {
    ConnectedAppsUtils utils =
        new ConnectedAppsUtilsImpl(context, connectedAppsUtils.getWorkProfile());
    testUtilities.setRunningOnPersonalProfile();

    assertThat(utils.getSecondaryProfile()).isEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void getPrimaryProfile_primaryProfileIsWork_runningOnWork_returnsCurrent() {
    testUtilities.setRunningOnWorkProfile();
    ConnectedAppsUtils utils =
        new ConnectedAppsUtilsImpl(context, connectedAppsUtils.getWorkProfile());

    assertThat(utils.getPrimaryProfile()).isEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void getSecondaryProfile_primaryProfileIsWork_runningOnWork_returnsDifferentToCurrent() {
    testUtilities.setRunningOnWorkProfile();
    ConnectedAppsUtils utils =
        new ConnectedAppsUtilsImpl(context, connectedAppsUtils.getWorkProfile());

    assertThat(utils.getSecondaryProfile()).isNotEqualTo(connectedAppsUtils.getCurrentProfile());
  }

  @Test
  public void runningOnWork_runningOnPersonal_returnsFalse() {
    testUtilities.setRunningOnPersonalProfile();

    assertThat(connectedAppsUtils.runningOnWork()).isFalse();
  }

  @Test
  public void runningOnWork_runningOnWork_returnsTrue() {
    testUtilities.setRunningOnWorkProfile();

    assertThat(connectedAppsUtils.runningOnWork()).isTrue();
  }

  @Test
  public void runningOnPersonal_runningOnPersonal_returnsTrue() {
    testUtilities.setRunningOnPersonalProfile();

    assertThat(connectedAppsUtils.runningOnPersonal()).isTrue();
  }

  @Test
  public void runningOnPersonal_runningOnWork_returnsFalse() {
    testUtilities.setRunningOnWorkProfile();

    assertThat(connectedAppsUtils.runningOnPersonal()).isFalse();
  }
}
