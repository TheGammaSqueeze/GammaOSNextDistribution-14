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
import static org.junit.Assert.assertThrows;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.enterprise.connectedapps.exceptions.UnavailableProfileException;
import com.google.android.enterprise.connectedapps.instrumented.utils.InstrumentedTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests how the SDK behaves when running on a device with a work profile but without the
 * app installed.
 */
@RunWith(JUnit4.class)
public class NotInstalledInOtherUserTest {

  private static final String STRING = "String";

  private static final Application context = ApplicationProvider.getApplicationContext();

  private static final TestProfileConnector connector = TestProfileConnector.create(context);
  private final ProfileTestCrossProfileType type = ProfileTestCrossProfileType.create(connector);
  private static final InstrumentedTestUtilities utilities =
      new InstrumentedTestUtilities(context, connector);

  @AfterClass
  public static void teardownClass() {
    utilities.ensureNoWorkProfile();
  }

  @Test
  public void asyncCall_notInstalledInOtherProfile_failsFast() {
    utilities.ensureWorkProfileExistsWithoutTestApp();

    ListenableFuture<String> future = type.other().listenableFutureIdentityStringMethod(STRING);

    assertFutureHasException(future, UnavailableProfileException.class);
  }

  @Test
  public void connect_notInstalledInOtherProfile_failsFast() {
    utilities.ensureWorkProfileExistsWithoutTestApp();

    assertThrows(UnavailableProfileException.class, connector::connect);
  }
}
