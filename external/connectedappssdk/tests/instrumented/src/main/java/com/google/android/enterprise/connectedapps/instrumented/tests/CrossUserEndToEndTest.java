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
package com.google.android.enterprise.connectedapps.instrumented.tests;

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.getUserHandleForUserId;
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.os.UserHandle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingStringCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.UserConnectorTestUtilities;
import com.google.android.enterprise.connectedapps.instrumented.utils.UserManagementTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.crossuser.AppCrossUserConnector;
import com.google.android.enterprise.connectedapps.testapp.crossuser.UserTestCrossUserType;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for high level behaviour running on a correctly configured device (with a managed profile
 * with the app installed in both sides, granted INTERACT_ACROSS_USERS).
 *
 * <p>This tests that each type of call works in both directions.
 */
@RunWith(JUnit4.class)
public class CrossUserEndToEndTest {

  private static final Application context = ApplicationProvider.getApplicationContext();

  private static final String STRING = "String";

  private final UserManagementTestUtilities userUtilities =
      new UserManagementTestUtilities(context);

  private int otherUserId;
  private UserHandle other;
  private UserHandle current;
  private AppCrossUserConnector connector;
  private UserConnectorTestUtilities connectorUtilities;
  private UserTestCrossUserType testCrossUserType;

  @Before
  public void setup() {
    otherUserId = userUtilities.ensureUserReadyForCrossUserCalls();
    other = getUserHandleForUserId(otherUserId);
    current = getUserHandleForUserId(0);
    connector = AppCrossUserConnector.create(context);
    connectorUtilities = new UserConnectorTestUtilities(connector);
    testCrossUserType = UserTestCrossUserType.create(connector);
  }

  @After
  public void teardown() {
    connector.clearConnectionHolders(other);
    connectorUtilities.waitForDisconnected(other);
  }

  @Test
  public void isAvailable_isTrue() {
    assertThat(connector.isAvailable(other)).isTrue();
  }

  @Test
  public void isConnected_isFalse() {
    connector.removeConnectionHolder(other, this);
    connectorUtilities.waitForDisconnected(other);

    assertThat(connector.isConnected(other)).isFalse();
  }

  @Test
  public void hasConnected_isConnectedIsTrue() {
    connectorUtilities.addConnectionHolderAndWait(other, this);

    assertThat(connector.isConnected(other)).isTrue();
  }

  @Test
  public void hasConnected_userStopped_isConnectedIsFalse() throws UnavailableProfileException {
    connector.connect(other);

    userUtilities.stopUser(otherUserId);

    assertThat(connector.isConnected(other)).isFalse();
  }

  @Test
  public void hasConnected_synchronousConnection_isConnectedIsTrue()
      throws UnavailableProfileException {
    connector.connect(other);

    assertThat(connector.isConnected(other)).isTrue();
  }

  @Test
  public void callOnCurrent_resultIsCorrect() {
    assertThat(testCrossUserType.current().identityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void callsUser_givesCurrentUserHandle_resultIsCorrect()
      throws UnavailableProfileException {
    assertThat(testCrossUserType.user(current).identityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void synchronousMethod_resultIsCorrect() throws UnavailableProfileException {
    connector.connect(other);

    assertThat(testCrossUserType.user(other).identityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void futureMethod_resultIsCorrect() throws InterruptedException, ExecutionException {
    assertThat(testCrossUserType.user(other).listenableFutureIdentityStringMethod(STRING).get())
        .isEqualTo(STRING);
  }

  @Test
  public void asyncMethod_resultIsCorrect() throws InterruptedException {
    BlockingStringCallbackListener stringCallbackListener = new BlockingStringCallbackListener();

    testCrossUserType
        .user(other)
        .asyncIdentityStringMethod(
            STRING, stringCallbackListener, new BlockingExceptionCallbackListener());

    assertThat(stringCallbackListener.await()).isEqualTo(STRING);
  }

  // No round-trip tests which call into the other user and the other user calls back into the
  // current user, as there is currently no way to grant INTERACT_ACROSS_USERS_FULL on a
  // non-instrumented user in Android (and it is unlikely that this will never be added).
}
