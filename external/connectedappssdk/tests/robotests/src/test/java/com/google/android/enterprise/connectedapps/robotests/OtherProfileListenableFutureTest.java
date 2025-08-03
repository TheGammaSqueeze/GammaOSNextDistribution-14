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
import static org.junit.Assert.fail;
import static org.robolectric.annotation.LooperMode.Mode.LEGACY;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileTypeWhichNeedsContext;
import com.google.android.enterprise.connectedapps.testapp.types.TestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
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
public class OtherProfileListenableFutureTest {

  private final Application context = ApplicationProvider.getApplicationContext();
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
      other_listenableFuture_automaticConnection_workProfileIsTurnedOff_doesSetUnavailableProfileExceptionImmediately() {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOffWorkProfile();

    ListenableFuture<Void> future =
        profileTestCrossProfileType.other().listenableFutureVoidMethod();

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void
      other_listenableFuture_automaticConnection_workProfileIsTurnedOn_doesNotSetUnavailableProfileException() {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOnWorkProfile();

    ListenableFuture<Void> future =
        profileTestCrossProfileType
            .other()
            .listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);
    Robolectric.getForegroundThreadScheduler().advanceBy(5, TimeUnit.SECONDS);

    assertFutureDoesNotHaveException(future, UnavailableProfileException.class);
  }

  @Test
  public void other_listenableFuture_automaticConnection_callsMethod()
      throws ExecutionException, InterruptedException {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType.other().listenableFutureVoidMethod().get();

    assertThat(TestCrossProfileType.voidMethodCalls).isEqualTo(1);
  }

  @Test
  public void other_listenableFuture_automaticConnection_setsFuture()
      throws ExecutionException, InterruptedException {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOnWorkProfile();

    // This would throw an exception if it wasn't set
    assertThat(profileTestCrossProfileType.other().listenableFutureVoidMethod().get()).isNull();
  }

  @Test
  public void
      other_listenableFuture_automaticConnection_connectionIsDroppedDuringCall_setUnavailableProfileException() {
    testProfileConnector.clearConnectionHolders();
    testUtilities.turnOnWorkProfile();
    ListenableFuture<Void> future =
        profileTestCrossProfileType.other().listenableFutureMethodWhichNeverSetsTheValue();
    Robolectric.getForegroundThreadScheduler().advanceBy(5, TimeUnit.SECONDS);

    testUtilities.turnOffWorkProfile();

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void other_listenableFuture_doesNotTimeout() throws Exception {
    ListenableFuture<Void> listenableFuture =
        profileTestCrossProfileTypeWhichNeedsContext
            .other()
            .listenableFutureMethodWhichNeverSetsTheValue();

    scheduledExecutorService.advanceTimeBy(10, TimeUnit.MINUTES);

    assertFutureDoesNotHaveException(listenableFuture, UnavailableProfileException.class);
  }

  @Test
  public void other_listenableFuture_throwsException_exceptionIsWrapped() {
    // The exception is only catchable when the connection is already established.
    testUtilities.addDefaultConnectionHolderAndWait();

    try {
      ListenableFuture<Void> unusedFuture =
          profileTestCrossProfileType
              .other()
              .listenableFutureVoidMethodWhichThrowsRuntimeException();
      fail();
    } catch (ProfileRuntimeException expected) {
      assertThat(expected).hasCauseThat().isInstanceOf(CustomRuntimeException.class);
    }
  }

  @Test
  public void other_listenableFuture_contextArgument_works() throws Exception {
    ListenableFuture<Boolean> result =
        profileTestCrossProfileType.other().futureIsContextArgumentPassed();

    assertThat(result.get()).isTrue();
  }
}
