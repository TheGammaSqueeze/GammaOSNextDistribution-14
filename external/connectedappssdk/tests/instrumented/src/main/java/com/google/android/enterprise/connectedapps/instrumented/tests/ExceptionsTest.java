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
import static org.junit.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.Application;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.enterprise.connectedapps.exceptions.ProfileRuntimeException;
import com.google.android.enterprise.connectedapps.instrumented.utils.InstrumentedTestUtilities;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.connector.ExceptionsSuppressingConnector;
import com.google.android.enterprise.connectedapps.testapp.connector.TestProfileConnector;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileExceptionsSuppressingCrossProfileType;
import com.google.android.enterprise.connectedapps.testapp.types.ProfileTestCrossProfileType;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for exception handling in cross-profile methods. */
@RunWith(JUnit4.class)
public class ExceptionsTest {
  private static final Application context = ApplicationProvider.getApplicationContext();

  @Test
  public void remoteException_throwsLocallyAndCrashes() throws IOException, InterruptedException {
    TestProfileConnector connector = TestProfileConnector.create(context);
    InstrumentedTestUtilities utilities = new InstrumentedTestUtilities(context, connector);
    ProfileTestCrossProfileType type = ProfileTestCrossProfileType.create(connector);
    utilities.ensureReadyForCrossProfileCalls();
    utilities.addConnectionHolderAndWait(this);
    String pidsBefore = getProcessIdsOfThisPackage();

    ProfileRuntimeException exception = assertThrows(
        ProfileRuntimeException.class,
        () -> type.other().methodWhichThrowsRuntimeException());
    assertThat(exception).hasCauseThat().isInstanceOf(CustomRuntimeException.class);

    TimeUnit.SECONDS.sleep(5);

    assertThat(getProcessIdsOfThisPackage()).isNotEqualTo(pidsBefore);
  }

  @Test
  public void remoteException_throwsLocallyAndSuppresses() throws IOException, InterruptedException {
    ExceptionsSuppressingConnector connector = ExceptionsSuppressingConnector.create(context);
    InstrumentedTestUtilities utilities = new InstrumentedTestUtilities(context, connector);
    ProfileExceptionsSuppressingCrossProfileType type =
        ProfileExceptionsSuppressingCrossProfileType.create(connector);
    utilities.ensureReadyForCrossProfileCalls();
    utilities.addConnectionHolderAndWait(this);
    String pidsBefore = getProcessIdsOfThisPackage();

    ProfileRuntimeException exception = assertThrows(
        ProfileRuntimeException.class,
        () -> type.other().methodWhichThrowsRuntimeException());
    assertThat(exception).hasCauseThat().isInstanceOf(CustomRuntimeException.class);

    TimeUnit.SECONDS.sleep(5);

    assertThat(getProcessIdsOfThisPackage()).isEqualTo(pidsBefore);
  }

  private static String getProcessIdsOfThisPackage() throws IOException {
    ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
        .getUiAutomation()
        .executeShellCommand("pidof " + context.getApplicationInfo().processName);
    InputStream inputStream = new FileInputStream(fd.getFileDescriptor());
    StringBuilder sb = new StringBuilder();
    for (int ch; (ch = inputStream.read()) != -1; ) {
      sb.append((char) ch);
    }
    return sb.toString();
  }
}
