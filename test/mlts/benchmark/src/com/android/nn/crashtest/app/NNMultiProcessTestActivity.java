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
import com.android.nn.crashtest.core.test.RunModelsInMultipleProcesses;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class NNMultiProcessTestActivity extends NNCrashTestActivity {
  private static final String TAG = "NNMultiProcessTest";

  @Override
  protected String getTag() {
    return TAG;
  }

  @Override
  protected String getTestName(Intent intent) {
    return intent.getStringExtra(RunModelsInMultipleProcesses.TEST_NAME);
  }

  @Override
  protected long getTestDurationMillis(Intent intent) {
    return intent.getLongExtra(
            RunModelsInMultipleProcesses.TEST_DURATION, RunModelsInMultipleProcesses.DEFAULT_TEST_DURATION);
  }

  @Override
  protected CrashTestCoordinator.CrashTestIntentInitializer getIntentInitializer(Intent intent) {
    return RunModelsInMultipleProcesses.intentInitializer(intent);
  }

  @Override
  protected Class<? extends CrashTest> getTestClass() {
    return RunModelsInMultipleProcesses.class;
  }
}