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
import static org.junit.Assert.assertThrows;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestBooleanCallbackListenerMultiImpl;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerMultiImpl;
import com.google.android.enterprise.connectedapps.TestVoidCallbackListenerMultiImpl;
import com.google.android.enterprise.connectedapps.testapp.CustomError;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import java.util.concurrent.ExecutionException;
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
public class BothProfilesAsyncTest {
  private static final String STRING = "String";

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);
  private final ProfileTestCrossProfileType profileTestCrossProfileType =
      ProfileTestCrossProfileType.create(testProfileConnector);

  private final Profile currentProfileIdentifier = testProfileConnector.utils().getCurrentProfile();
  private final Profile otherProfileIdentifier = testProfileConnector.utils().getOtherProfile();

  private final TestVoidCallbackListenerMultiImpl voidCallback =
      new TestVoidCallbackListenerMultiImpl();
  private final TestStringCallbackListenerMultiImpl stringCallback =
      new TestStringCallbackListenerMultiImpl();
  private final TestBooleanCallbackListenerMultiImpl booleanCallback =
      new TestBooleanCallbackListenerMultiImpl();

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
    testProfileConnector.clearConnectionHolders();
  }

  @Test
  public void both_async_canBind_calledOnBothProfiles()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType.both().asyncVoidMethod(voidCallback);

    // This calls on the same profile because of robolectric
    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(2);
  }

  @Test
  public void both_async_canBind_resultContainsBothProfilesResults()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType.both().asyncIdentityStringMethod(STRING, stringCallback);

    assertThat(stringCallback.stringCallbackValues.get(currentProfileIdentifier)).isEqualTo(STRING);
    assertThat(stringCallback.stringCallbackValues.get(otherProfileIdentifier)).isEqualTo(STRING);
  }

  @Test // This behaviour is expected right now but will change
  public void both_async_blockingMethod_blocks() {
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType
        .both()
        .asyncVoidMethodWithDelay(voidCallback, /* secondsDelay= */ 5);

    assertThat(voidCallback.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void both_async_nonblockingMethod_doesNotBlock() {
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType
        .both()
        .asyncVoidMethodWithNonBlockingDelay(voidCallback, /* secondsDelay= */ 5);

    assertThat(voidCallback.callbackMethodCalls).isEqualTo(0);
  }

  @Test
  public void both_async_nonblockingMethod_doesCallback() {
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType
        .both()
        .asyncVoidMethodWithNonBlockingDelay(voidCallback, /* secondsDelay= */ 5);
    testUtilities.advanceTimeBySeconds(10);

    assertThat(voidCallback.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void both_async_canNotBind_calledOnOnlyCurrentProfile() {
    testUtilities.turnOffWorkProfile();
    profileTestCrossProfileType.both().asyncVoidMethod(voidCallback);

    // This calls on the same profile because of robolectric
    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void both_async_canNotBind_resultContainsOnlyCurrentProfilesResult() {
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType.both().asyncIdentityStringMethod(STRING, stringCallback);

    assertThat(stringCallback.stringCallbackValues.get(currentProfileIdentifier)).isEqualTo(STRING);
    assertThat(stringCallback.stringCallbackValues.get(otherProfileIdentifier)).isEqualTo(null);
  }

  @Test
  public void both_async_connectionDropsDuringCall_resultContainsOnlyCurrentProfilesResult() {
    testUtilities.turnOnWorkProfile();
    profileTestCrossProfileType
        .both()
        .asyncIdentityStringMethodWithNonBlockingDelay(
            STRING, stringCallback, /* secondsDelay= */ 5);
    testUtilities.advanceTimeBySeconds(2);

    testUtilities.turnOffWorkProfile();
    testUtilities.advanceTimeBySeconds(3);

    assertThat(stringCallback.stringCallbackValues).containsKey(currentProfileIdentifier);
    assertThat(stringCallback.stringCallbackValues).doesNotContainKey(otherProfileIdentifier);
  }

  @Test
  public void both_async_doesNotTimeout() {
    profileTestCrossProfileType
        .both()
        .asyncIdentityStringMethodWithNonBlockingDelay(
            STRING, stringCallback, /* secondsDelay= */ 100);

    testUtilities.advanceTimeBySeconds(99);

    assertThat(stringCallback.stringCallbackValues).isNull();
  }

  @Test
  public void both_async_throwsRuntimeException_exceptionThrownOnCurrentProfileIsThrown() {
    assertThrows(
        Exception.class,
        () ->
            profileTestCrossProfileType
                .both()
                .asyncStringMethodWhichThrowsRuntimeException(stringCallback));
  }

  @Test
  public void both_async_throwsError_errorThrownOnCurrentProfileIsThrown() {
    assertThrows(
        CustomError.class,
        () ->
            profileTestCrossProfileType
                .both()
                .asyncStringMethodWhichThrowsError(stringCallback));
  }

  @Test
  public void both_async_contextArgument_works() {
    profileTestCrossProfileType.both().asyncIsContextArgumentPassed(booleanCallback);

    assertThat(booleanCallback.booleanCallbackValues.get(currentProfileIdentifier)).isTrue();
    assertThat(booleanCallback.booleanCallbackValues.get(otherProfileIdentifier)).isTrue();
  }

  @Test
  public void both_async_passesMultipleValues_onlyReceivesFirstValues() {
    stringCallback.numberOfCallbacks = 0;

    profileTestCrossProfileType.both()
        .asyncIdentityStringMethodWhichCallsBackTwice(STRING, stringCallback);

    assertThat(stringCallback.numberOfCallbacks).isEqualTo(1);
  }
}
