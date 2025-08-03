/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.portraitlauncher.homeactivities.test;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

/** An empty activity to be used for testing. */
public class TestActivity extends Activity {
    private static final String TAG = TestActivity.class.getSimpleName();
    protected ViewGroup mTestContainer;
    protected List<MotionEvent> mMotionEventLog;
    protected MotionEvent mMotionEvent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.empty_test_activity);
        mTestContainer = findViewById(R.id.test_container);
        mMotionEventLog = new ArrayList<>();
        mTestContainer.setTag(TAG);
        mTestContainer.setOnTouchListener((v, event) -> {
            Log.d(TAG, "event = " + event);
            mMotionEventLog.add(event);
            mMotionEvent = event;
            return false;
        });
    }

    @VisibleForTesting
    List<MotionEvent> getMotionEventLog() {
        return mMotionEventLog;
    }

    @VisibleForTesting
    MotionEvent getMotionEvent() {
        return mMotionEvent;
    }
}
