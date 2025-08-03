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
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.testapp.CustomError;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;
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
public class BothProfilesListenableFutureTest {
  private static final String STRING = "String";

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);

  private final Profile currentProfileIdentifier = testProfileConnector.utils().getCurrentProfile();
  private final Profile otherProfileIdentifier = testProfileConnector.utils().getOtherProfile();

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
    testProfileConnector.clearConnectionHolders();
  }

  @Test
  public void both_listenableFuture_canBind_calledOnBothProfiles()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType.both().listenableFutureVoidMethod().get();

    // This calls on the same profile because of robolectric
    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(2);
  }

  @Test
  public void both_listenableFuture_canBind_resultContainsBothProfilesResults()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOnWorkProfile();

    Map<Profile, String> results =
        profileTestCrossProfileType.both().listenableFutureIdentityStringMethod(STRING).get();

    assertThat(results.get(currentProfileIdentifier)).isEqualTo(STRING);
    assertThat(results.get(otherProfileIdentifier)).isEqualTo(STRING);
  }

  @Test // This behaviour is expected right now but will change
  public void both_listenableFuture_blockingMethod_blocks() {
    testUtilities.turnOnWorkProfile();

    ListenableFuture<Map<Profile, Void>> future =
        profileTestCrossProfileType
            .both()
            .listenableFutureVoidMethodWithDelay(/* secondsDelay= */ 5);

    assertThat(future.isDone()).isTrue();
  }

  @Test
  public void both_listenableFuture_nonblockingMethod_doesNotBlock() {
    testUtilities.turnOnWorkProfile();

    ListenableFuture<Map<Profile, Void>> future =
        profileTestCrossProfileType
            .both()
            .listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);

    assertThat(future.isDone()).isFalse();
  }

  @Test
  public void both_listenableFuture_nonblockingMethod_doesCallback() {
    testUtilities.turnOnWorkProfile();

    ListenableFuture<Map<Profile, Void>> future =
        profileTestCrossProfileType
            .both()
            .listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);
    testUtilities.advanceTimeBySeconds(10);

    assertThat(future.isDone()).isTrue();
  }

  @Test
  public void both_listenableFuture_canNotBind_calledOnOnlyCurrentProfile()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType.both().listenableFutureVoidMethod().get();

    // This calls on the same profile because of robolectric
    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void both_listenableFuture_canNotBind_resultContainsOnlyCurrentProfilesResult()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOffWorkProfile();

    Map<Profile, String> results =
        profileTestCrossProfileType.both().listenableFutureIdentityStringMethod(STRING).get();

    assertThat(results.get(currentProfileIdentifier)).isEqualTo(STRING);
    assertThat(results.get(otherProfileIdentifier)).isEqualTo(null);
  }

  @Test
  public void both_listenableFuture_isBound_becomesUnbound_calledOnBothProfiles() throws Exception {
    testUtilities.turnOnWorkProfile();
    ListenableFuture<Map<Profile, Void>> unusedFuture =
        profileTestCrossProfileType
            .both()
            .listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);

    // Because of the way Robolectric currently works - the method is guaranteed to have executed
    //  before the work profile is turned off. This may change with later changes to the SDK so
    //  this test will be updated.
    testUtilities.turnOffWorkProfile();

    // This calls on the same profile because of robolectric
    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(2);
  }

  @Test
  public void both_listenableFuture_isBound_becomesUnbound_callbackFires() {
    testUtilities.turnOnWorkProfile();
    ListenableFuture<Map<Profile, Void>> future =
        profileTestCrossProfileType
            .both()
            .listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);

    testUtilities.turnOffWorkProfile();

    assertThat(future.isDone()).isTrue();
  }

  @Test
  public void both_listenableFuture_profilesWithExceptionsAreNotIncludedInResults()
      throws ExecutionException, InterruptedException {
    ListenableFuture<Map<Profile, Void>> future =
        profileTestCrossProfileType
            .both()
            .listenableFutureVoidMethodWhichSetsIllegalStateException();

    assertThat(future.get()).hasSize(0);
  }

  @Test
  public void
      both_listenableFuture_connectionDropsDuringCall_resultContainsOnlyCurrentProfilesResult()
          throws ExecutionException, InterruptedException {
    ListenableFuture<Map<Profile, String>> future =
        profileTestCrossProfileType
            .both()
            .listenableFutureIdentityStringMethodWithNonBlockingDelay(
                STRING, /* secondsDelay= */ 5);
    testUtilities.advanceTimeBySeconds(2);

    testUtilities.turnOffWorkProfile();

    Map<Profile, String> results = future.get();

    assertThat(results).containsKey(currentProfileIdentifier);
    assertThat(results).doesNotContainKey(otherProfileIdentifier);
  }

  @Test
  public void both_listenableFuture_doesNotTimeout()
      throws ExecutionException, InterruptedException {
    ListenableFuture<Map<Profile, String>> future =
        profileTestCrossProfileType
            .both()
            .listenableFutureIdentityStringMethodWithNonBlockingDelay(
                STRING, /* secondsDelay= */ 100);

    testUtilities.advanceTimeBySeconds(99);

    assertThat(future.isDone()).isFalse();
  }

  @Test
  public void
      both_listenableFuture_throwsRuntimeException_exceptionThrownOnCurrentProfileIsThrown() {
    assertThrows(
        Exception.class,
        () ->
            profileTestCrossProfileType
                .both()
                .listenableFutureVoidMethodWhichThrowsRuntimeException());
  }

  @Test
  public void
  both_listenableFuture_throwsError_errorThrownOnCurrentProfileIsThrown() {
    assertThrows(
        CustomError.class,
        () ->
            profileTestCrossProfileType
                .both()
                .listenableFutureVoidMethodWhichThrowsError());
  }

  @Test
  public void both_listenableFuture_contextArgument_works() throws Exception {
    ListenableFuture<Map<Profile, Boolean>> resultFuture =
        profileTestCrossProfileType.both().futureIsContextArgumentPassed();

    Map<Profile, Boolean> result = resultFuture.get();

    assertThat(result.get(currentProfileIdentifier)).isTrue();
    assertThat(result.get(otherProfileIdentifier)).isTrue();
  }
}
