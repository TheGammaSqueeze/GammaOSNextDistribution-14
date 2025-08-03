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

import static com.google.android.enterprise.connectedapps.SharedTestUtilities.assertFutureHasException;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.app.Application;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.AvailabilityListener;
import com.google.android.enterprise.connectedapps.TestAvailabilityListener;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.instrumented.utils.InstrumentedTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AvailabilityListener}. */
@RunWith(JUnit4.class)
public class AvailabilityListenerTest {
  private static final Application context = ApplicationProvider.getApplicationContext();

  private final TestProfileConnector connector = TestProfileConnector.create(context);
  private final InstrumentedTestUtilities utilities =
      new InstrumentedTestUtilities(context, connector);
  private final ProfileTestCrossProfileType type = ProfileTestCrossProfileType.create(connector);

  @Before
  public void setup() {
    utilities.ensureReadyForCrossProfileCalls();
  }

  @After
  public void teardown() {
    utilities.ensureNoWorkProfile();
  }

  @Test
  public void workProfileTurnedOff_availabilityListenerFires() throws InterruptedException {
    assumeTrue(
        "Tests can only turn work profile on/off after O", VERSION.SDK_INT >= VERSION_CODES.P);

    utilities.ensureWorkProfileTurnedOn();
    TestAvailabilityListener availabilityListener = new TestAvailabilityListener();
    connector.addAvailabilityListener(availabilityListener);

    utilities.turnOffWorkProfileAndWait();

    assertThat(availabilityListener.awaitAvailabilityChange()).isGreaterThan(0);
    assertThat(connector.isAvailable()).isFalse();
  }

  @Test
  public void workProfileTurnedOn_availabilityListenerFires() throws InterruptedException {
    assumeTrue(
        "Tests can only turn work profile on/off after O", VERSION.SDK_INT >= VERSION_CODES.P);

    utilities.ensureWorkProfileTurnedOff();
    TestAvailabilityListener availabilityListener = new TestAvailabilityListener();
    connector.addAvailabilityListener(availabilityListener);

    utilities.turnOnWorkProfileAndWait();

    assertThat(availabilityListener.awaitAvailabilityChange()).isGreaterThan(0);
    assertThat(connector.isAvailable()).isTrue();
  }

  @Test
  public void temporaryConnectionError_inProgressCall_availabilityListenerFires()
      throws InterruptedException {
    utilities.ensureWorkProfileTurnedOn();
    TestAvailabilityListener availabilityListener = new TestAvailabilityListener();
    connector.addAvailabilityListener(availabilityListener);

    ListenableFuture<Void> unusedFuture = type.other().killApp();

    assertFutureHasException(unusedFuture, UnavailableProfileException.class);
    assertThat(availabilityListener.awaitAvailabilityChange()).isGreaterThan(0);
    assertThat(connector.isAvailable()).isTrue();
  }
}
