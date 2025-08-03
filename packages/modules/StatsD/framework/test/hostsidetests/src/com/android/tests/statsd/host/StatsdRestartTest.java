/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tests.statsd.host;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Host side tests for tests that restart statsd.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class StatsdRestartTest extends BaseHostJUnit4Test {
  private static final String STATSD_TEST_APP_PACKAGE = "com.android.tests.statsd.framework.app";

  private void stopStatsd() throws Exception {
    getDevice().executeShellCommand("stop statsd");
  }

  private void startStatsd() throws Exception {
    getDevice().executeShellCommand("start statsd");
  }

  @After
  public void ensureStatsdRunning() throws Exception {
    startStatsd();
  }

  /**
   * Ensures that StatsManager APIs return the appropriate exception when statsd
   * is not alive.
   */
  @Test
  public void statsManagerApisThrowException() throws Exception {
    stopStatsd();
    // Run device tests
    runStatsdDeviceTest("StatsdRestartTest", "testApisThrowExceptionWhenStatsdNotAlive");
  }

  private void runStatsdDeviceTest(String className, String methodName)
      throws Exception {
    String fullClassName = STATSD_TEST_APP_PACKAGE + "." + className;
    String fullTestName = fullClassName + "$" + methodName;
    boolean result =
        runDeviceTests(STATSD_TEST_APP_PACKAGE, fullClassName, methodName);
    assertWithMessage(fullTestName + " failed.").that(result).isTrue();
  }
}
