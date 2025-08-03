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
import static org.junit.Assert.fail;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.NonSimpleCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestBooleanCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestVoidCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileTypeWhichNeedsContext;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import java.util.concurrent.TimeUnit;
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
public class OtherProfileAsyncTest {

  private static final String STRING = "String";
  private static final String STRING2 = "String2";

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestVoidCallbackListenerImpl voidCallbackListener =
      new TestVoidCallbackListenerImpl();
  private final TestStringCallbackListenerImpl stringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestBooleanCallbackListenerImpl booleanCallbackListener =
      new TestBooleanCallbackListenerImpl();
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
  private final ProfileTestCrossProfileTypeWhichNeedsContext
      profileTestCrossProfileTypeWhichNeedsContext =
          ProfileTestCrossProfileTypeWhichNeedsContext.create(testProfileConnector);
  private final NonSimpleCallbackListenerImpl nonSimpleCallbackListener =
      new NonSimpleCallbackListenerImpl();

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
  public void other_async_callbackTriggeredMultipleTimes_isReceivedTwice() {
    profileTestCrossProfileType
        .other()
        .asyncIdentityStringMethodWhichCallsBackTwice(
            STRING, stringCallbackListener, exceptionCallbackListener);

    assertThat(stringCallbackListener.callbackMethodCalls).isEqualTo(2);
  }

  @Test
  public void
      other_async_automaticConnection_workProfileIsTurnedOff_doesReceiveUnavailableProfileExceptionImmediately() {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(voidCallbackListener, exceptionCallbackListener);

    assertThat(exceptionCallbackListener.lastException)
        .isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void
      other_async_automaticConnection_workProfileIsTurnedOn_doesNotSetUnavailableProfileException() {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethodWithNonBlockingDelay(
            voidCallbackListener, /* secondsDelay= */ 5, exceptionCallbackListener);
    testUtilities.advanceTimeBySeconds(5);

    assertThat(exceptionCallbackListener.lastException).isNull();
  }

  @Test
  public void other_async_automaticConnection_callsMethod() {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType
        .other()
        .asyncVoidMethod(voidCallbackListener, exceptionCallbackListener);

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void other_async_automaticConnection_resultIsSet() {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType
        .other()
        .asyncIdentityStringMethod(STRING, stringCallbackListener, exceptionCallbackListener);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING);
  }

  @Test
  public void
      other_async_automaticConnection_connectionIsDroppedDuringCall_setUnavailableProfileException() {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOnWorkProfile();
    profileTestCrossProfileType
        .other()
        .asyncMethodWhichNeverCallsBack(stringCallbackListener, exceptionCallbackListener);
    testUtilities.advanceTimeBySeconds(5);

    testUtilities.turnOffWorkProfile();

    assertThat(exceptionCallbackListener.lastException)
        .isInstanceOf(UnavailableProfileException.class);
  }

  @Test
  public void other_async_doesNotTimeOut() throws Exception {
    profileTestCrossProfileTypeWhichNeedsContext
        .other()
        .asyncMethodWhichNeverCallsBack(stringCallbackListener, exceptionCallbackListener);

    scheduledExecutorService.advanceTimeBy(10, TimeUnit.MINUTES);

    assertThat(exceptionCallbackListener.lastException).isNull();
  }

  @Test
  public void other_async_throwsException_exceptionIsWrapped() {
    // The exception is only catchable when the connection is already established.
    testUtilities.addDefaultConnectionHolderAndWait();

    try {
      profileTestCrossProfileType
          .other()
          .asyncStringMethodWhichThrowsRuntimeException(
              stringCallbackListener, exceptionCallbackListener);
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void other_async_contextArgument_works() {
    profileTestCrossProfileType
        .other()
        .asyncIsContextArgumentPassed(booleanCallbackListener, exceptionCallbackListener);

    assertThat(booleanCallbackListener.booleanCallbackValue).isTrue();
  }

  @Test
  public void other_async_nonSimpleCallback_works() {
    nonSimpleCallbackListener.callbackMethodCalls = 0;
    profileTestCrossProfileType
        .other()
        .asyncMethodWithNonSimpleCallback(
            nonSimpleCallbackListener, STRING, STRING2, exceptionCallbackListener);

    assertThat(nonSimpleCallbackListener.callbackMethodCalls).isEqualTo(1);
    assertThat(nonSimpleCallbackListener.string1CallbackValue).isEqualTo(STRING);
    assertThat(nonSimpleCallbackListener.string2CallbackValue).isEqualTo(STRING2);
  }

  @Test
  public void other_async_nonSimpleCallback_secondMethod_works() {
    profileTestCrossProfileType
        .other()
        .asyncMethodWithNonSimpleCallbackCallsSecondMethod(
            nonSimpleCallbackListener, STRING, STRING2, exceptionCallbackListener);

    assertThat(nonSimpleCallbackListener.string3CallbackValue).isEqualTo(STRING);
    assertThat(nonSimpleCallbackListener.string4CallbackValue).isEqualTo(STRING2);
  }
}
