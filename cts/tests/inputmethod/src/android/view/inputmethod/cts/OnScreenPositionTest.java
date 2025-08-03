/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class OnScreenPositionTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.OnScreenPositionTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();

    /**
     * Regression test for Bug 33308065.
     *
     * <p>Verify that {@link com.android.cts.mockime.Watermark} is visible even if it's placed
     * at the bottom of the IME content area.</p>
     */
    @Test
    public void testImeIsNotBehindNavBar() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder()
                        .setWatermarkGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            TestActivity.startSync(activity -> {
                activity.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.setHint("editText");
                editText.requestFocus();

                layout.addView(editText);
                return layout;
            });

            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);

            expectImeVisible(TIMEOUT);
        }
    }
}
