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
import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestVoidCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class OtherProfileManualAsyncTest {

  private static final String STRING = "String";

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestStringCallbackListenerImpl stringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestVoidCallbackListenerImpl voidCallbackListener =
      new TestVoidCallbackListenerImpl();
  private final TestExceptionCallbackListener exceptionCallbackListener =
      new TestExceptionCallbackListener();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);

  private final ProfileTestCrossProfileType profileTestCrossProfileType =
      ProfileTestCrossProfileType.create(testProfileConnector);

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

  @Test
  public void other_async_manualConnection_isBound_callsMethod() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(voidCallbackListener, exceptionCallbackListener);

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void other_async_manualConnection_isBound_firesCallback() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(voidCallbackListener, exceptionCallbackListener);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void other_async_manualConnection_isBound_unbundlesCorrectly() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType
        .other()
        .asyncIdentityStringMethod(STRING, stringCallbackListener, exceptionCallbackListener);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING);
  }

  @Test // This behaviour is expected right now but will change
  public void other_async_manualConnection_isBound_blockingMethod_blocks() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethodWithDelay(
            voidCallbackListener, /* secondsDelay= */ 5, exceptionCallbackListener);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void other_async_manualConnection_isBound_nonBlockingMethod_doesNotBlock() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethodWithNonBlockingDelay(
            voidCallbackListener, /* secondsDelay= */ 5, exceptionCallbackListener);

    assertThat(stringCallbackListener.callbackMethodCalls).isEqualTo(0);
  }

  @Test
  public void other_async_manualConnection_isBound_nonBlockingMethod_doesCallback() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethodWithNonBlockingDelay(
            voidCallbackListener, /* secondsDelay= */ 5, exceptionCallbackListener);
    testUtilities.advanceTimeBySeconds(10);

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void other_async_manualConnection_isNotBound_doesReturnUnavailableProfileException() {
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(voidCallbackListener, exceptionCallbackListener);

    assertThat(exceptionCallbackListener.lastException)
        .isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void other_asyncMethod_manualConnection_isNotBound_binds() {
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(voidCallbackListener, exceptionCallbackListener);

    assertThat(testProfileConnector.isConnected()).isTrue();
  }

  @Test
  public void
      other_async_manualConnection_connectionIsDroppedDuringCall_setUnavailableProfileException() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();
    profileTestCrossProfileType
        .other()
        .asyncMethodWhichNeverCallsBack(stringCallbackListener, exceptionCallbackListener);
    testUtilities.advanceTimeBySeconds(5);

    testUtilities.turnOffWorkProfile();

    assertThat(exceptionCallbackListener.lastException)
        .isInstanceOf(UnavailableProfileException.class);
  }
}
