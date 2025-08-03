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

package com.android.cts.mockime;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

public final class MockImeSessionCrashTest {

    private Instrumentation mInstrumentation;

    private Context mContext;

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getTargetContext();
    }

    @Test
    public void testRetrieveExitReasonsWhenMockImeSessionCrashes() throws Exception {
        try (var mockImeSession = MockImeSession.create(mContext)) {
            assertThat(mockImeSession.retrieveExitReasonIfMockImeCrashed()).isNull();

            runShellCommandOrThrow("am force-stop " + mockImeSession.getMockImePackageName());
            assertThat(mockImeSession.retrieveExitReasonIfMockImeCrashed()).matches(
                    "MockIme crashed and exited with code: \\d+;"
                            + " session create time: \\d+;"
                            + " process exit time: \\d+;"
                            + " see android.app.ApplicationExitInfo for more info on the exit code "
                            + "\\(exit Description: \\[FORCE STOP\\] stop com.android.cts.mockime "
                            + "due to from pid \\d+\\)");
        }
    }
}
