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
import static com.google.android.enterprise.connectedapps.StringUtilities.randomString;
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.app.Service;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.RobolectricTestUtilities;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.TestScheduledExecutorService;
import com.google.android.enterprise.connectedapps.TestStringCallbackListenerImpl;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.testapp.configuration.TestApplication;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for large messages */
@RunWith(RobolectricTestRunner.class)
@Config(minSdk = VERSION_CODES.O)
public class MessageSizeTest {

  private static final String SMALL_STRING = "String";
  private static final String LARGE_STRING = randomString(1500000); // 3Mb

  private final Application context = ApplicationProvider.getApplicationContext();
  private final TestScheduledExecutorService scheduledExecutorService =
      new TestScheduledExecutorService();
  private final TestProfileConnector testProfileConnector =
      TestProfileConnector.create(context, scheduledExecutorService);
  private final RobolectricTestUtilities testUtilities =
      new RobolectricTestUtilities(testProfileConnector, scheduledExecutorService);

  private final ProfileTestCrossProfileType profileTestCrossProfileType =
      ProfileTestCrossProfileType.create(testProfileConnector);

  private final TestStringCallbackListenerImpl stringCallbackListener =
      new TestStringCallbackListenerImpl();
  private final TestExceptionCallbackListener exceptionCallbackListener =
      new TestExceptionCallbackListener();

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
  public void synchronous_smallMessage_sends() throws UnavailableProfileException {
    assertThat(profileTestCrossProfileType.other().identityStringMethod(SMALL_STRING))
        .isEqualTo(SMALL_STRING);
  }

  @Test
  public void synchronous_largeMessage_sends() throws UnavailableProfileException {
    assertThat(profileTestCrossProfileType.other().identityStringMethod(LARGE_STRING))
        .isEqualTo(LARGE_STRING);
  }

  @Test
  public void async_smallMessage_sends() {
    profileTestCrossProfileType
        .other()
        .asyncIdentityStringMethod(SMALL_STRING, stringCallbackListener, exceptionCallbackListener);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(SMALL_STRING);
  }

  @Test
  public void async_largeMessage_sends() {
    profileTestCrossProfileType
        .other()
        .asyncIdentityStringMethod(LARGE_STRING, stringCallbackListener, exceptionCallbackListener);

    assertThat(stringCallbackListener.stringCallbackValue).isEqualTo(LARGE_STRING);
  }

  @Test
  public void future_smallMessage_sends() throws ExecutionException, InterruptedException {
    assertThat(
            profileTestCrossProfileType
                .other()
                .listenableFutureIdentityStringMethod(SMALL_STRING)
                .get())
        .isEqualTo(SMALL_STRING);
  }

  @Test
  public void future_largeMessage_sends() throws ExecutionException, InterruptedException {
    assertThat(
            profileTestCrossProfileType
                .other()
                .listenableFutureIdentityStringMethod(LARGE_STRING)
                .get())
        .isEqualTo(LARGE_STRING);
  }
}
