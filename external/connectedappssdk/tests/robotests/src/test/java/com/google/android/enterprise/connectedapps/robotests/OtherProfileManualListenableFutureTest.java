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
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
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
public class OtherProfileManualListenableFutureTest {

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
  public void
      other_listenableFuture_manualConnection_workProfileIsTurnedOff_doesSetUnavailableProfileExceptionImmediately() {
    testUtilities.addDefaultConnectionHolderAndWait();
    testUtilities.turnOffWorkProfile();

    testUtilities.assertFutureHasException(
        profileTestCrossProfileType.other().listenableFutureVoidMethod(),
        UnavailableProfileException.class);
  }

  @Test
  public void other_listenableFuture_manualConnection_isBound_callsMethod()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOnWorkProfile();
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType.other().listenableFutureVoidMethod().get();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void
      other_listenableFuture_manualConnection_isNotBound_doesNotThrowUnavailableProfileException() {
    testUtilities.turnOffWorkProfile();

    ListenableFuture<Void> unusedFuture =
        profileTestCrossProfileType.other().listenableFutureVoidMethod();
  }

  @Test
  public void
      other_listenableFuture_manualConnection_isNotBound_returnsThrowUnavailableProfileException() {
    testUtilities.turnOffWorkProfile();

    ListenableFuture<Void> future =
        profileTestCrossProfileType.other().listenableFutureVoidMethod();

    testUtilities.assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test // This behaviour is expected right now but will change
  public void other_listenableFuture_manualConnection_blockingMethod_blocks() {
    ListenableFuture<Void> voidFuture =
        profileTestCrossProfileType
            .other()
            .listenableFutureVoidMethodWithDelay(/* secondsDelay= */ 5);

    assertThat(voidFuture.isDone()).isTrue();
  }

  @Test
  public void other_listenableFuture_manualConnection_setsFuture()
      throws ExecutionException, InterruptedException {

    // This would throw an exception if it wasn't set
    assertThat(profileTestCrossProfileType.other().listenableFutureVoidMethod().get()).isNull();
  }

  @Test
  public void other_listenableFuture_manualConnection_setsException_isSet() {
    testUtilities.assertFutureHasException(
        profileTestCrossProfileType
            .other()
            .listenableFutureVoidMethodWhichSetsIllegalStateException(),
        IllegalStateException.class);
  }

  @Test
  public void other_listenableFuture_manualConnection_passesParametersCorrectly()
      throws ExecutionException, InterruptedException {
    ListenableFuture<String> future =
        profileTestCrossProfileType.other().listenableFutureIdentityStringMethod(STRING);

    assertThat(future.get()).isEqualTo(STRING);
  }

  @Test
  public void other_listenableFuture_manualConnection_nonblockingMethod_doesNotBlock() {
    ListenableFuture<Void> future =
        profileTestCrossProfileType
            .other()
            .listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);

    assertThat(future.isDone()).isFalse();
  }

  @Test
  public void other_listenableFuture_manualConnection_nonblockingMethod_doesCallback() {
    ListenableFuture<Void> future =
        profileTestCrossProfileType
            .other()
            .listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);
    testUtilities.advanceTimeBySeconds(10);

    assertThat(future.isDone()).isTrue();
  }

  @Test
  public void
      other_listenableFuture_manualConnection_connectionIsDroppedDuringCall_setUnavailableProfileException() {
    ListenableFuture<Void> future =
        profileTestCrossProfileType.other().listenableFutureMethodWhichNeverSetsTheValue();
    testUtilities.advanceTimeBySeconds(5);

    testUtilities.turnOffWorkProfile();

    testUtilities.assertFutureHasException(future, UnavailableProfileException.class);
  }
}
