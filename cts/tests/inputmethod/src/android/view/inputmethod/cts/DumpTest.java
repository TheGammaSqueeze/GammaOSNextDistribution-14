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

package android.view.inputmethod.cts;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/** Test for 'dumpsys input_method'. */
@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public final class DumpTest {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @Test
    public void test_dumpDoesNotContainEditorText() throws Exception {
        final String marker = "TEST_MARKER/" + SystemClock.elapsedRealtimeNanos();
        final String text = "TEST_TEXT/" + SystemClock.elapsedRealtimeNanos();
        final String extraKey = "EXTRAS_KEY/" + SystemClock.elapsedRealtimeNanos();
        final String extraValue = "EXTRAS_VALUE/" + SystemClock.elapsedRealtimeNanos();
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText focusedEditText = new EditText(activity);
                focusedEditText.setPrivateImeOptions(marker);
                focusedEditText.setText(text);
                focusedEditText.getInputExtras(true /* create */).putString(extraKey, extraValue);
                focusedEditText.requestFocus();
                layout.addView(focusedEditText);
                return layout;
            });
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final String output = SystemUtil.runShellCommand("dumpsys input_method");
            assertThat(output).contains("Input method client state");
            assertThat(output).contains("Input method service state");
            assertThat(output).doesNotContain(text);
            assertThat(output).doesNotContain(extraKey);
            assertThat(output).doesNotContain(extraValue);
        }
    }
}
