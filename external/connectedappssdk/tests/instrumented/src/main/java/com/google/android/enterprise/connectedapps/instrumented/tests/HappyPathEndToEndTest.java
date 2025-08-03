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

import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingStringCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.InstrumentedTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileTypeWhichNeedsContext;
import com.google.android.enterprise.connectedapps.testing.BlockingPoll;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.AfterClass;
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
public class HappyPathEndToEndTest {
  private static final Application context = ApplicationProvider.getApplicationContext();

  private static final String STRING = "String";

  private static final TestProfileConnector connector = TestProfileConnector.create(context);
  private static final InstrumentedTestUtilities utilities =
      new InstrumentedTestUtilities(context, connector);
  private final ProfileTestCrossProfileType type = ProfileTestCrossProfileType.create(connector);
  private final ProfileTestCrossProfileTypeWhichNeedsContext typeWithContext =
      ProfileTestCrossProfileTypeWhichNeedsContext.create(connector);

  @Before
  public void setup() {
    utilities.ensureReadyForCrossProfileCalls();
  }

  @After
  public void teardown() {
    connector.clearConnectionHolders();
    utilities.waitForDisconnected();
  }

  @AfterClass
  public static void teardownClass() {
    utilities.ensureNoWorkProfile();
  }

  @Test
  public void isAvailable_isTrue() {
    assertThat(connector.isAvailable()).isTrue();
  }

  @Test
  public void isConnected_isFalse() {
    connector.clearConnectionHolders();
    utilities.waitForDisconnected();

    assertThat(connector.isConnected()).isFalse();
  }

  @Test
  public void isConnected_hasConnected_isTrue() {
    utilities.addConnectionHolderAndWait(this);

    assertThat(connector.isConnected()).isTrue();
  }

  @Test
  public void synchronousMethod_resultIsCorrect() throws UnavailableProfileException {
    utilities.addConnectionHolderAndWait(this);

    assertThat(type.other().identityStringMethod(STRING)).isEqualTo(STRING);
  }

  @Test
  public void futureMethod_resultIsCorrect() throws InterruptedException, ExecutionException {
    assertThat(type.other().listenableFutureIdentityStringMethod(STRING).get()).isEqualTo(STRING);
  }

  @Test
  public void asyncMethod_resultIsCorrect() throws InterruptedException {
    BlockingStringCallbackListener stringCallbackListener = new BlockingStringCallbackListener();

    type.other()
        .asyncIdentityStringMethod(
            STRING, stringCallbackListener, new BlockingExceptionCallbackListener());

    assertThat(stringCallbackListener.await()).isEqualTo(STRING);
  }

  @Test
  public void synchronousMethod_fromOtherProfile_resultIsCorrect()
      throws UnavailableProfileException {
    utilities.addConnectionHolderAndWait(this);
    typeWithContext.other().connectToOtherProfile();
    BlockingPoll.poll(
        () -> {
          try {
            return typeWithContext.other().isConnectedToOtherProfile();
          } catch (UnavailableProfileException e) {
            return false;
          }
        },
        /* pollFrequency= */ 100,
        /* timeoutMillis= */ 10000);

    assertThat(typeWithContext.other().methodWhichCallsIdentityStringMethodOnOtherProfile(STRING))
        .isEqualTo(STRING);
  }

  @Test
  public void asyncMethod_fromOtherProfile_resultIsCorrect() throws InterruptedException {
    BlockingStringCallbackListener stringCallbackListener = new BlockingStringCallbackListener();

    typeWithContext
        .other()
        .asyncMethodWhichCallsIdentityStringMethodOnOtherProfile(
            STRING, stringCallbackListener, new BlockingExceptionCallbackListener());

    assertThat(stringCallbackListener.await()).isEqualTo(STRING);
  }

  @Test
  public void futureMethod_fromOtherProfile_resultIsCorrect()
      throws ExecutionException, InterruptedException {
    assertThat(
            typeWithContext
                .other()
                .listenableFutureMethodWhichCallsIdentityStringMethodOnOtherProfile(STRING)
                .get())
        .isEqualTo(STRING);
  }
}
