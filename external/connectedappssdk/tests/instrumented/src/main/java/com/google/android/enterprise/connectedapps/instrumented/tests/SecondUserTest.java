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
import com.google.android.enterprise.connectedapps.instrumented.utils.InstrumentedTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileTypeWhichNeedsContext;
import com.google.android.enterprise.connectedapps.testing.BlockingPoll;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests regarding how the SDK behaves when running on a device with a second user. */
@RunWith(JUnit4.class)
public class SecondUserTest {
  private static final Application context = ApplicationProvider.getApplicationContext();

  private final TestProfileConnector connector = TestProfileConnector.create(context);
  private final InstrumentedTestUtilities utilities =
      new InstrumentedTestUtilities(context, connector);

  private final ProfileTestCrossProfileTypeWhichNeedsContext type =
      ProfileTestCrossProfileTypeWhichNeedsContext.create(connector);

  @After
  public void teardown() {
    utilities.ensureNoWorkProfile();
  }

  @Test
  public void isAvailable_noWorkProfile_hasSecondUser_isFalse() {
    int secondUserId = utilities.createUser("SecondUser");

    try {
      utilities.startUser(secondUserId);
      utilities.installInUser(secondUserId);
      utilities.grantInteractAcrossUsers();

      assertThat(connector.isAvailable()).isFalse();
    } finally {
      utilities.removeUser(secondUserId);
    }
  }

  @Test
  public void call_hasWorkProfile_hasSecondUser_executesOnWorkProfile()
      throws UnavailableProfileException {
    utilities.ensureReadyForCrossProfileCalls();
    utilities.addConnectionHolderAndWait(this);
    int secondUserId = utilities.createUser("SecondUser");

    try {
      utilities.startUser(secondUserId);
      utilities.installInUser(secondUserId);
      utilities.grantInteractAcrossUsers();

      assertThat(type.other().getUserId()).isEqualTo(utilities.getWorkProfileUserId());
    } finally {
      utilities.removeUser(secondUserId);
    }
  }

  @Test
  public void call_hasWorkProfile_hasSecondUser_fromWorkProfile_executesOnThisUser()
      throws UnavailableProfileException {
    utilities.ensureReadyForCrossProfileCalls();
    utilities.addConnectionHolderAndWait(this);
    int secondUserId = utilities.createUser("SecondUser");

    try {
      utilities.startUser(secondUserId);
      utilities.installInUser(secondUserId);
      utilities.grantInteractAcrossUsers();

      type.other().connectToOtherProfile();
      BlockingPoll.poll(
          () -> {
            try {
              return type.other().isConnectedToOtherProfile();
            } catch (UnavailableProfileException e) {
              return false;
            }
          },
          /* pollFrequency= */ 100,
          /* timeoutMillis= */ 10000);

      assertThat(type.other().getOtherUserId()).isEqualTo(0);
    } finally {
      utilities.removeUser(secondUserId);
    }
  }
}
