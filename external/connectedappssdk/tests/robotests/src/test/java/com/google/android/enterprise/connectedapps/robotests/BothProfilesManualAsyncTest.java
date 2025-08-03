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
import com.google.android.enterprise.connectedapps.Profile;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerMultiImpl;
import com.google.android.enterprise.connectedapps.TestVoidCallbackListenerMultiImpl;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

@LooperMode(LEGACY)
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class BothProfilesManualAsyncTest {
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

  private final TestStringCallbackListenerMultiImpl stringCallback =
      new TestStringCallbackListenerMultiImpl();
  private final TestVoidCallbackListenerMultiImpl voidCallback =
      new TestVoidCallbackListenerMultiImpl();

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
    testUtilities.addDefaultConnectionHolderAndWait();
  }

  @Test
  public void both_async_manualConnection_isBound_calledOnBothProfiles() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType.both().asyncVoidMethod(voidCallback);

    // This calls on the same profile because of robolectric
    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(2);
  }

  @Test
  public void both_async_manualConnection_isBound_resultContainsBothProfilesResults() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType.both().asyncIdentityStringMethod(STRING, stringCallback);

    assertThat(stringCallback.stringCallbackValues.get(currentProfileIdentifier)).isEqualTo(STRING);
    assertThat(stringCallback.stringCallbackValues.get(otherProfileIdentifier)).isEqualTo(STRING);
  }

  @Test // This behaviour is expected right now but will change
  public void both_async_manualConnection_isBound_blockingMethod_blocks() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType
        .both()
        .asyncVoidMethodWithDelay(voidCallback, /* secondsDelay= */ 5);

    assertThat(voidCallback.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void both_async_manualConnection_isBound_nonblockingMethod_doesNotBlock() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType
        .both()
        .asyncVoidMethodWithNonBlockingDelay(voidCallback, /* secondsDelay= */ 5);

    assertThat(voidCallback.callbackMethodCalls).isEqualTo(0);
  }

  @Test
  public void both_async_manualConnection_isBound_nonblockingMethod_doesCallback() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType
        .both()
        .asyncVoidMethodWithNonBlockingDelay(voidCallback, /* secondsDelay= */ 5);
    testUtilities.advanceTimeBySeconds(10);

    assertThat(voidCallback.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void both_async_manualConnection_isNotBound_calledOnOnlyCurrentProfile() {
    testUtilities.addDefaultConnectionHolderAndWait();
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType.both().asyncVoidMethod(voidCallback);

    // This calls on the same profile because of robolectric
    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  @Ignore // Will be supported when async methods are supported with exceptions
  public void both_async_manualConnection_isNotBound_resultContainsOnlyCurrentProfilesResult() {
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType.both().asyncIdentityStringMethod(STRING, stringCallback);

    assertThat(stringCallback.stringCallbackValues.get(currentProfileIdentifier)).isEqualTo(STRING);
    assertThat(stringCallback.stringCallbackValues.get(otherProfileIdentifier)).isEqualTo(null);
  }

  @Test
  public void both_async_manualConnection_isBound_becomesUnbound_calledOnBothProfiles() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();
    profileTestCrossProfileType
        .both()
        .asyncVoidMethodWithNonBlockingDelay(voidCallback, /* secondsDelay= */ 5);

    // Because of the way Robolectric currently works - the method is guaranteed to have executed
    //  before the work profile is turned off. This may change with later changes to the SDK so
    //  this test will be updated.
    testUtilities.turnOffWorkProfile();
    testUtilities.advanceTimeBySeconds(5); // Complete local call

    // This calls on the same profile because of robolectric
    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(2);
  }

  @Test
  public void both_async_manualConnection_isBound_becomesUnbound_callbackFires() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();
    profileTestCrossProfileType
        .both()
        .asyncVoidMethodWithNonBlockingDelay(voidCallback, /* secondsDelay= */ 5);

    testUtilities.turnOffWorkProfile();
    testUtilities.advanceTimeBySeconds(5); // Complete local call

    assertThat(voidCallback.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void
      both_async_manualConnection_connectionDropsDuringCall_resultContainsOnlyCurrentProfilesResult() {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();
    profileTestCrossProfileType
        .both()
        .asyncIdentityStringMethodWithNonBlockingDelay(
            STRING, stringCallback, /* secondsDelay= */ 5);

    testUtilities.turnOffWorkProfile();
    testUtilities.advanceTimeBySeconds(5); // Complete local call

    assertThat(stringCallback.stringCallbackValues).containsKey(currentProfileIdentifier);
    assertThat(stringCallback.stringCallbackValues).doesNotContainKey(otherProfileIdentifier);
  }
}
