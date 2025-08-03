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

import static com.google.android.enterprise.connectedapps.RobolectricTestUtilities.TEST_CONNECTOR_CLASS_NAME;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.DirectBootAwareConnector;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnectorWithCustomServiceClass;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Tests for the {@link CustomProfileConnector} class. */
@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class ProfileConnectorTest {

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestConnectionListener connectionListener = new TestConnectionListener();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);
  private final DirectBootAwareConnector directBootAwareConnector =
      DirectBootAwareConnector.create(context);

  @Before
  public void setUp() {
    Service profileAwareService = Robolectric.setupService(TestApplication.getService());
    testUtilities.initTests();
    IBinder binder = profileAwareService.onBind(/* intent= */ null);
    testUtilities.setBinding(binder, TEST_CONNECTOR_CLASS_NAME);
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS);
  }

  @After
  public void teardown() {
    testProfileConnector.clearConnectionHolders();
  }

  @Test
  public void construct_nullConnector_throwsNullPointerException() {
    assertThrows(NullPointerException.class, () -> TestProfileConnector.create(null));
  }

  // Other connect tests are covered in Instrumented ConnectTest because Robolectric doesn't
  // handle the multiple threads very well
  @Test
  public void connect_callingFromUIThread_throwsIllegalStateException() {
    assertThrows(IllegalStateException.class, () -> testProfileConnector.connect(this));
  }

  @Test
  public void addConnectionHolder_null_throwsException() {
    assertThrows(NullPointerException.class, () -> testProfileConnector.addConnectionHolder(null));
  }

  @Test
  public void removeConnectionHolder_null_throwsException() {
    assertThrows(NullPointerException.class,
        () -> testProfileConnector.removeConnectionHolder(null));
  }

  @Test
  public void addConnectionHolder_fromPersonalProfile_binds() {
    testUtilities.setRunningOnPersonalProfile();

    testProfileConnector.addConnectionHolder(this);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void addConnectionHolder_fromWorkProfile_binds() {
    testUtilities.setRunningOnWorkProfile();

    testProfileConnector.addConnectionHolder(this);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void disconnect_fromPersonalProfile_doesNotBind() {
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.disconnect();

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void disconnect_fromWorkProfile_doesNotBind() {
    testUtilities.setRunningOnWorkProfile();
    testUtilities.disconnect();

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void disconnect_isBound_unbinds() {
    testUtilities.addDefaultConnectionHolderAndWait();

    testUtilities.disconnect();

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void addConnectionHolder_callsConnectionListener() {
    testProfileConnector.addConnectionListener(connectionListener);
    testUtilities.addDefaultConnectionHolderAndWait();

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void addConnectionHolder_doesNotCallUnregisteredConnectionListener() {
    testProfileConnector.addConnectionListener(connectionListener);
    testProfileConnector.removeConnectionListener(connectionListener);
    testUtilities.addDefaultConnectionHolderAndWait();

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(0);
  }

  @Test
  public void disconnect_callsConnectionListener() {
    testProfileConnector.addConnectionListener(connectionListener);
    testUtilities.addDefaultConnectionHolderAndWait();
    connectionListener.resetConnectionChangedCount();

    testUtilities.disconnect();

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void bindingDies_callsConnectionListener() {
    testProfileConnector.addConnectionListener(connectionListener);
    testUtilities.addDefaultConnectionHolderAndWait();
    connectionListener.resetConnectionChangedCount();

    testUtilities.turnOffWorkProfile();

    assertThat(connectionListener.connectionChangedCount()).isEqualTo(1);
  }

  @Test
  public void addConnectionHolder_profileConnectorWithCustomServiceClass() {
    TestProfileConnectorWithCustomServiceClass.create(context, scheduledExecutorService)
        .addConnectionHolder(this);
    testUtilities.advanceTimeBySeconds(1); // Allow connection

    assertThat(shadowOf(context).getNextStartedService().getComponent().getClassName())
        .isEqualTo("com.google.CustomServiceClass");
  }

  @Test
  public void isAvailable_workProfileIsTurnedOn_returnsTrue() {
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();

    assertThat(testProfileConnector.isAvailable()).isTrue();
  }

  @Test
  public void isAvailable_workProfileIsTurnedOff_returnsFalse() {
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.createWorkUser();
    testUtilities.turnOffWorkProfile();

    assertThat(testProfileConnector.isAvailable()).isFalse();
  }

  @Test
  public void isAvailable_runningOnWorkProfile_returnsTrue() {
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRunningOnWorkProfile();

    assertThat(testProfileConnector.isAvailable()).isTrue();
  }

  @Test
  public void isAvailable_defaultAvailabilityRestrictions_isNotUnlocked_returnsFalse() {
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfileWithoutUnlocking();

    assertThat(testProfileConnector.isAvailable()).isFalse();
  }

  @Test
  public void isAvailable_defaultAvailabilityRestrictions_isUnlocked_returnsTrue() {
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();

    assertThat(testProfileConnector.isAvailable()).isTrue();
  }

  @Test
  public void isAvailable_directBootAwareAvailabilityRestrictions_isNotUnlocked_returnsTrue() {
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfileWithoutUnlocking();

    assertThat(directBootAwareConnector.isAvailable()).isTrue();
  }

  @Test
  public void isAvailable_directBootAwareAvailabilityRestrictions_isUnlocked_returnsTrue() {
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();

    assertThat(directBootAwareConnector.isAvailable()).isTrue();
  }

  @Test
  public void addConnectionHolder_autocloseReturnedConnectionHolder_unbinds() {
    try (ProfileConnectionHolder p = testProfileConnector.addConnectionHolder(this)) {
      // Intentionally empty
    }

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isFalse();
  }
}
