/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.uibench.microbenchmark;

import android.Manifest;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.platform.helpers.HelperAccessor;
import android.platform.test.microbenchmark.Microbenchmark;
import android.platform.test.rule.Dex2oatPressureRule;
import android.platform.test.rule.NaturalOrientationRule;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Microbenchmark.class)
public class UiBenchEditTextTypingMicrobenchmark {
    @ClassRule public static NaturalOrientationRule orientationRule = new NaturalOrientationRule();

    private static HelperAccessor<IUiBenchJankHelper> sHelper =
            new HelperAccessor<>(IUiBenchJankHelper.class);

    /**
     * Broadcast action: Sent by EditTextTypeActivity in UiBench test app to cancel typing
     * benchmark when the test activity was paused.
     */
    private static final String ACTION_CANCEL_TYPING_CALLBACK =
            "com.android.uibench.action.CANCEL_TYPING_CALLBACK";
    private boolean mShouldStop = false;
    private final Object mLock = new Object();

    private BroadcastReceiver mReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(ACTION_CANCEL_TYPING_CALLBACK)) {
                        synchronized (mLock) {
                            mShouldStop = true;
                        }
                    }
                }
            };

    @Rule
    public Dex2oatPressureRule dex2oatPressureRule = new Dex2oatPressureRule();

    @BeforeClass
    public static void openApp() {
        sHelper.get().openEditTextTyping();
    }

    // Measure jank metrics for EditText Typing
    // Note: Disable/comment the test if it is flaky (see b/62917134).
    @Test
    public void testEditTextTyping() {
        int codes[] = {
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_SPACE
        };
        int i = 0;
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mShouldStop = false;
        try {
            InstrumentationRegistry.getInstrumentation()
                    .getContext()
                    .registerReceiver(mReceiver, new IntentFilter(ACTION_CANCEL_TYPING_CALLBACK),
                            Context.RECEIVER_EXPORTED);
            uiAutomation.adoptShellPermissionIdentity(Manifest.permission.INJECT_EVENTS);
            final long startTime = SystemClock.uptimeMillis();
            do {
                int code = codes[i % codes.length];
                if (i % 100 == 99) code = KeyEvent.KEYCODE_ENTER;

                synchronized (mLock) {
                    if (mShouldStop) break;
                }

                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code);
                i++;
            } while (SystemClock.uptimeMillis() - startTime
                    <= UiBenchJankHelper.FULL_TEST_DURATION);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
            if (mReceiver != null) {
                InstrumentationRegistry.getInstrumentation()
                        .getContext()
                        .unregisterReceiver(mReceiver);
            }
        }
    }

    @AfterClass
    public static void closeApp() {
        sHelper.get().exit();
    }
}
