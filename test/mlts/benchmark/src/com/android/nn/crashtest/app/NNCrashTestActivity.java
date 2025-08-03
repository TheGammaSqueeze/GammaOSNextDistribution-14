/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.nn.crashtest.app;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.android.nn.crashtest.core.CrashTest;
import com.android.nn.crashtest.core.CrashTestCoordinator;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public abstract class NNCrashTestActivity extends Activity {
  public static final Duration MAX_TEST_DELAY_BEFORE_HANG = Duration.ofSeconds(30);

  private final CrashTestStatus mTestStatus = new CrashTestStatus(this::logMessage);
  private final CrashTestCoordinator mCoordinator = new CrashTestCoordinator(this);
  private Duration mDuration;

  protected void logMessage(String msg) {
    Log.i(getTag(), msg);
  }

  protected abstract String getTag();

  protected abstract String getTestName(Intent intent);

  protected abstract long getTestDurationMillis(Intent intent);

  protected abstract CrashTestCoordinator.CrashTestIntentInitializer getIntentInitializer(Intent intent);

  protected abstract Class<? extends CrashTest> getTestClass();


  @Override
  protected void onResume() {
    super.onResume();

    final Intent intent = getIntent();

    mDuration = Duration.ofMillis(getTestDurationMillis(intent));
    mCoordinator.startTest(getTestClass(),
        getIntentInitializer(intent),
        mTestStatus,
        /*separateProcess=*/true, getTestName(intent));
  }

  // This method blocks until the tests complete and returns true if all tests completed
  // successfully
  public CrashTestStatus.TestResult testResult() {
    try {
      final Duration testTimeout = mDuration.plus(MAX_TEST_DELAY_BEFORE_HANG);
      boolean completed =
              mTestStatus.waitForCompletion(testTimeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!completed) {
        Log.w(getTag(), String.format("Test didn't complete within %s. Returning HANG", testTimeout));
        return CrashTestStatus.TestResult.HANG;
      }
      return mTestStatus.result();
    } catch (InterruptedException e) {
      Log.w(getTag(), "Interrupted while waiting for test completion. Returning HANG");
      return CrashTestStatus.TestResult.HANG;
    }
  }
}