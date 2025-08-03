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
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.TestVoidCallbackListenerImpl;
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

/** Tests for _IfAvailable classes */
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class IfAvailableTest {

  private static final String STRING1 = "String1";
  private static final String STRING2 = "String2";

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);
  private final TestStringCallbackListenerImpl stringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestVoidCallbackListenerImpl voidCallbackListener =
      new TestVoidCallbackListenerImpl();

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

  // We cannot test synchronous calls when not connected as robolectric automatically connects
  // if we add a connection holder

  @Test
  public void synchronous_connected_makesCall() throws Exception {
    testUtilities.addDefaultConnectionHolderAndWait();

    assertThat(profileTestCrossProfileType.other().identityStringMethod(STRING1))
        .isEqualTo(STRING1);
  }

  @Test
  public void synchronousVoid_connected_doesNotThrowException() {
    testUtilities.addDefaultConnectionHolderAndWait();

    profileTestCrossProfileType.other().ifAvailable().voidMethod();
  }

  @Test
  public void callback_notAvailable_returnsDefaultValue() {
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType
        .other()
        .ifAvailable()
        .asyncIdentityStringMethod(STRING1, stringCallbackListener, /* defaultValue= */ STRING2);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING2);
  }

  @Test
  public void callback_available_makesCall() {
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType
        .other()
        .ifAvailable()
        .asyncIdentityStringMethod(STRING1, stringCallbackListener, /* defaultValue= */ STRING2);
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(STRING1);
  }

  @Test
  public void voidCallback_notAvailable_callsBack() {
    testUtilities.turnOffWorkProfile();

    profileTestCrossProfileType.other().ifAvailable().asyncVoidMethod(voidCallbackListener);
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void voidCallback_available_callsBack() {
    testUtilities.turnOnWorkProfile();

    profileTestCrossProfileType.other().ifAvailable().asyncVoidMethod(voidCallbackListener);
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(voidCallbackListener.callbackMethodCalls).isEqualTo(1);
  }

  @Test
  public void future_notAvailable_setsDefaultValue()
      throws ExecutionException, InterruptedException {
    testUtilities.turnOffWorkProfile();

    ListenableFuture<String> future =
        profileTestCrossProfileType
            .other()
            .ifAvailable()
            .listenableFutureIdentityStringMethod(STRING1, STRING2);
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(future.get()).isEqualTo(STRING2);
  }

  @Test
  public void future_available_setsCorrectValue() throws ExecutionException, InterruptedException {
    testUtilities.turnOnWorkProfile();

    ListenableFuture<String> future =
        profileTestCrossProfileType
            .other()
            .ifAvailable()
            .listenableFutureIdentityStringMethod(STRING1, STRING2);
    shadowOf(Looper.getMainLooper()).idle();

    assertThat(future.get()).isEqualTo(STRING1);
  }
}
