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
package com.google.android.enterprise.connectedapps.robotests;

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.INTERACT_ACROSS_USERS;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.assertFutureDoesNotHaveException;
import static com.google.android.enterprise.connectedapps.SharedTestUtilities.assertFutureHasException;
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.ProfileConnectionHolder;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestVoidCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class AutomaticConnectionManagementTest {

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestVoidCallbackListenerImpl testVoidCallbackListener =
      new TestVoidCallbackListenerImpl();
  private final TestStringCallbackListenerImpl testStringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestExceptionCallbackListener testExceptionCallbackListener =
      new TestExceptionCallbackListener();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);
  private final ProfileTestCrossProfileType profileTestCrossProfileType =
      ProfileTestCrossProfileType.create(testProfileConnector);
  private final Object connectionHolder = new Object();

  @Before
  public void setUp() {
    Service profileAwareService = Robolectric.setupService(TestApplication.getService());
    testUtilities.initTests();
    IBinder binder = profileAwareService.onBind(/* intent= */ null);
    testUtilities.setBinding(binder, RobolectricTestUtilities.TEST_CONNECTOR_CLASS_NAME);
    testUtilities.createWorkUser();
    testUtilities.turnOnWorkProfile();
    testUtilities.setRunningOnPersonalProfile();
    testUtilities.setRequestsPermissions(INTERACT_ACROSS_USERS);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS);
  }

  @After
  public void teardown() {
    testProfileConnector.clearConnectionHolders();
  }

  @Test
  public void lessThanThirtySecondsWithNoCalls_doesNotDisconnect() {
    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(testVoidCallbackListener, testExceptionCallbackListener);

    testUtilities.advanceTimeBySeconds(29);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void thirtySecondsWithNoCalls_disconnects() {
    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(testVoidCallbackListener, testExceptionCallbackListener);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void moreThanThirtySecondsWithNoCalls_manualManagementStarted_doesNotDisconnect() {
    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(testVoidCallbackListener, testExceptionCallbackListener);
    testUtilities.advanceTimeBySeconds(29);
    testUtilities.addDefaultConnectionHolderAndWait();

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void lessThanThirtySecondsWithNoCalls_previousCallsWereChained_doesNotDisconnect() {
    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(testVoidCallbackListener, testExceptionCallbackListener);
    testUtilities.advanceTimeBySeconds(29);
    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(testVoidCallbackListener, testExceptionCallbackListener);

    testUtilities.advanceTimeBySeconds(29);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void thirtySecondsWithNoCalls_previousCallsWereChained_disconnects() {
    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(testVoidCallbackListener, testExceptionCallbackListener);
    testUtilities.advanceTimeBySeconds(29);
    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(testVoidCallbackListener, testExceptionCallbackListener);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void callWhichTakesALongTime_doesNotDisconnectDuringCall() {
    profileTestCrossProfileType
        .other()
        .asyncVoidMethodWithNonBlockingDelay(
            testVoidCallbackListener, /* secondsDelay= */ 40, testExceptionCallbackListener);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void lessThanThirtySecondsAfterCallWhichTakesALongTime_doesNotDisconnect() {
    profileTestCrossProfileType
        .other()
        .asyncVoidMethodWithNonBlockingDelay(
            testVoidCallbackListener, /* secondsDelay= */ 40, testExceptionCallbackListener);

    testUtilities.advanceTimeBySeconds(69);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void thirtySecondsAfterCallWhichTakesALongTime_disconnects() {
    profileTestCrossProfileType
        .other()
        .asyncVoidMethodWithNonBlockingDelay(
            testVoidCallbackListener, /* secondsDelay= */ 40, testExceptionCallbackListener);

    testUtilities.advanceTimeBySeconds(70);

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void newCall_afterDisconnection_reconnects() {
    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(testVoidCallbackListener, testExceptionCallbackListener);
    testUtilities.advanceTimeBySeconds(31);

    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(testVoidCallbackListener, testExceptionCallbackListener);
    testUtilities.advanceTimeBySeconds(1);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void clearConnectionHolders_lessThan30SecondsLater_doesNotDisconnect() {
    testUtilities.addDefaultConnectionHolderAndWait();
    testProfileConnector.clearConnectionHolders();

    testUtilities.advanceTimeBySeconds(29);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void clearConnectionHolders_moreThan30SecondsLater_disconnects() {
    testUtilities.addDefaultConnectionHolderAndWait();
    testProfileConnector.clearConnectionHolders();

    testUtilities.advanceTimeBySeconds(29);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void addConnectionHolder_moreThan30SecondsLater_doesNotDisconnect() {
    testProfileConnector.addConnectionHolder(this);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void removeConnectionHolder_lessThan30SecondsLater_doesNotDisconnect() {
    testProfileConnector.addConnectionHolder(this);
    testProfileConnector.removeConnectionHolder(this);

    testUtilities.advanceTimeBySeconds(29);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void removeConnectionHolder_moreThan30SecondsLater_disconnects() {
    testProfileConnector.addConnectionHolder(this);
    testProfileConnector.removeConnectionHolder(this);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void removeConnectionHolder_removingAlias_moreThan30SecondsLater_disconnects() {
    testProfileConnector.addConnectionHolder(this);
    testProfileConnector.addConnectionHolderAlias(connectionHolder, this);
    testProfileConnector.removeConnectionHolder(connectionHolder);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void removeConnectionHolder_removingWrapper_moreThan30SecondsLater_disconnects() {
    ProfileConnectionHolder connectionHolder = testProfileConnector.addConnectionHolder(this);
    testProfileConnector.removeConnectionHolder(connectionHolder);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void
      removeConnectionHolder_stillAnotherConnectionHolder_moreThan30SecondsLater_doesNotDisconnect() {
    testProfileConnector.addConnectionHolder(this);
    testProfileConnector.addConnectionHolder(connectionHolder);
    testProfileConnector.removeConnectionHolder(this);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void removeConnectionHolder_removingCallback_noResultOnCallback_moreThan30SecondsLater_disconnects() {
    profileTestCrossProfileType.other()
        .asyncMethodWhichNeverCallsBack(testStringCallbackListener, testExceptionCallbackListener);
    testProfileConnector.removeConnectionHolder(testStringCallbackListener);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void removeConnectionHolder_removingFuture_noResultOnFuture_moreThan30SecondsLater_disconnects() {
    ListenableFuture<Void> future = profileTestCrossProfileType.other()
        .listenableFutureMethodWhichNeverSetsTheValue();
    testProfileConnector.removeConnectionHolder(future);

    testUtilities.advanceTimeBySeconds(31);

    assertThat(testProfileConnector.isConnected()).isFalse();
  }

  @Test
  public void asyncCall_doesNotHavePermission_failsImmediately() {
    testUtilities.denyPermissions(INTERACT_ACROSS_USERS);

    ListenableFuture<Void> future =
        profileTestCrossProfileType.other().listenableFutureVoidMethod();
    testUtilities.advanceTimeBySeconds(1);

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void asyncCall_getsPermissionAfterPreviousFailure_doesNotFail() {
    testUtilities.denyPermissions(INTERACT_ACROSS_USERS);
    ListenableFuture<Void> unusedFuture =
        profileTestCrossProfileType.other().listenableFutureVoidMethod();
    testUtilities.advanceTimeBySeconds(5);
    testUtilities.grantPermissions(INTERACT_ACROSS_USERS);

    ListenableFuture<Void> future =
        profileTestCrossProfileType.other().listenableFutureVoidMethod();

    assertFutureDoesNotHaveException(future, UnavailableProfileException.class);
  }
}
