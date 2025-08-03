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
import com.google.android.enterprise.connectedapps.AvailabilityListener;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testing.InstrumentedTestUtilities;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link AvailabilityListener}. */
@RunWith(JUnit4.class)
public class InstrumentedTestUtilitiesTest {

  private static final Application context = ApplicationProvider.getApplicationContext();

  private final static TestProfileConnector connector = TestProfileConnector.create(context);
  private final static InstrumentedTestUtilities utilities =
      new InstrumentedTestUtilities(context, connector);

  @AfterClass
  public static void teardownClass() {
    utilities.ensureNoWorkProfile();
  }

  @After
  public void teardown() {
    connector.clearConnectionHolders();
  }

  @Test
  public void isAvailable_ensureReadyForCrossProfileCalls_isTrue() {
    utilities.ensureReadyForCrossProfileCalls();

    assertThat(connector.isAvailable()).isTrue();
  }

  @Test
  public void isAvailable_ensureNoWorkProfile_isFalse() {
    utilities.ensureReadyForCrossProfileCalls();

    utilities.ensureNoWorkProfile();

    assertThat(connector.isAvailable()).isFalse();
  }

  @Test
  public void hasWorkProfile_createdWorkProfile_isTrue() {
    utilities.ensureWorkProfileExists();

    assertThat(utilities.hasWorkProfile()).isTrue();
  }

  @Test
  public void hasWorkProfile_removedWorkProfile_isFalse() {
    utilities.ensureNoWorkProfile();

    assertThat(utilities.hasWorkProfile()).isFalse();
  }

  @Test
  public void getWorkProfileUserId_createdWorkProfile_isNotZero() {
    utilities.ensureWorkProfileExists();

    assertThat(utilities.getWorkProfileUserId()).isNotEqualTo(0);
  }

  @Test
  public void isConnected_waitForConnected_isTrue() {
    utilities.ensureReadyForCrossProfileCalls();

    connector.addConnectionHolder(this);
    utilities.waitForConnected();

    assertThat(connector.isConnected()).isTrue();
  }

  @Test
  public void isConnected_waitForDisconnected_isFalse() {
    utilities.ensureReadyForCrossProfileCalls();
    connector.addConnectionHolder(this);
    utilities.waitForConnected();

    connector.clearConnectionHolders();
    utilities.waitForDisconnected();

    assertThat(connector.isConnected()).isFalse();
  }
}
