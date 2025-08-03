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

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestAvailabilityListener;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class AvailabilityListenerTest {

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);
  private final ProfileTestCrossProfileType type =
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
  public void successfulCall_availabilityListenerDoesNotFire()
      throws InterruptedException, ExecutionException {
    testUtilities.turnOnWorkProfile();
    TestAvailabilityListener availabilityListener = new TestAvailabilityListener();
    testProfileConnector.addAvailabilityListener(availabilityListener);

    ListenableFuture<Void> unusedFuture = type.other().listenableFutureVoidMethod();
    testUtilities.advanceTimeBySeconds(1);
    unusedFuture.get();

    assertThat(availabilityListener.availabilityChangedCount()).isEqualTo(0);
    assertThat(testProfileConnector.isAvailable()).isTrue();
  }

  @Test
  public void temporaryConnectionError_inProgressCall_availabilityListenerFires() {
    testUtilities.turnOnWorkProfile();
    TestAvailabilityListener availabilityListener = new TestAvailabilityListener();
    testProfileConnector.addAvailabilityListener(availabilityListener);

    ListenableFuture<Void> unusedFuture =
        type.other().listenableFutureVoidMethodWithNonBlockingDelay(/* secondsDelay= */ 5);
    testUtilities.simulateDisconnectingServiceConnection();
    testUtilities.advanceTimeBySeconds(1);

    assertThat(availabilityListener.availabilityChangedCount()).isGreaterThan(0);
    assertThat(testProfileConnector.isAvailable()).isTrue();
  }
}
