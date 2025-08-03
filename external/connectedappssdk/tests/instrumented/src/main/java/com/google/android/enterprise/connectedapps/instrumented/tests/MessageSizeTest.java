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

import static com.google.android.enterprise.connectedapps.StringUtilities.randomString;
import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.TestExceptionCallbackListener;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.instrumented.utils.BlockingStringCallbackListener;
import com.google.android.enterprise.connectedapps.instrumented.utils.InstrumentedTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import java.util.concurrent.ExecutionException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for passing large messages across profiles. */
@RunWith(JUnit4.class)
public class MessageSizeTest {
  private static final Application context = ApplicationProvider.getApplicationContext();

  private static final String SMALL_STRING = "String";
  private static final String LARGE_STRING = randomString(1500000); // 3Mb

  private static final TestProfileConnector connector = TestProfileConnector.create(context);
  private static final InstrumentedTestUtilities utilities =
      new InstrumentedTestUtilities(context, connector);
  private final ProfileTestCrossProfileType type = ProfileTestCrossProfileType.create(connector);

  private final BlockingStringCallbackListener stringCallbackListener =
      new BlockingStringCallbackListener();
  private final TestExceptionCallbackListener exceptionCallbackListener =
      new TestExceptionCallbackListener();

  @Before
  public void setup() {
    utilities.ensureReadyForCrossProfileCalls();
  }

  @AfterClass
  public static void teardownClass() {
    utilities.ensureNoWorkProfile();
  }

  @Test
  public void synchronous_smallMessage_sends() throws UnavailableProfileException {
    utilities.addConnectionHolderAndWait(this);

    assertThat(type.other().identityStringMethod(SMALL_STRING)).isEqualTo(SMALL_STRING);
  }

  @Test
  public void synchronous_largeMessage_sends() throws UnavailableProfileException {
    utilities.addConnectionHolderAndWait(this);

    assertThat(type.other().identityStringMethod(LARGE_STRING)).isEqualTo(LARGE_STRING);
  }

  @Test
  public void async_smallMessage_sends() throws InterruptedException {
    type.other()
        .asyncIdentityStringMethod(SMALL_STRING, stringCallbackListener, exceptionCallbackListener);

    assertThat(stringCallbackListener.await()).isEqualTo(SMALL_STRING);
  }

  @Test
  public void async_largeMessage_sends() throws InterruptedException {
    type.other()
        .asyncIdentityStringMethod(LARGE_STRING, stringCallbackListener, exceptionCallbackListener);

    assertThat(stringCallbackListener.await()).isEqualTo(LARGE_STRING);
  }

  @Test
  public void future_smallMessage_sends() throws ExecutionException, InterruptedException {
    String result = type.other().listenableFutureIdentityStringMethod(SMALL_STRING).get();

    assertThat(result).isEqualTo(SMALL_STRING);
  }

  @Test
  public void future_largeMessage_sends() throws ExecutionException, InterruptedException {
    String result = type.other().listenableFutureIdentityStringMethod(LARGE_STRING).get();

    assertThat(result).isEqualTo(LARGE_STRING);
  }
}
