/*
 * Copyright (C) 2024 The Android Open Source Project
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
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import android.os.SystemClock;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImePackageNames;
import com.android.cts.mockime.MockImeSession;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public final class ImeSwitchingTest extends EndToEndImeTestBase {
    static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.FocusHandlingTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    @Test
    public void testSwitchingIme() throws Exception {
        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        try (var session1 = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(), new ImeSettings.Builder());
                var session2 = MockImeSession.create(
                        instrumentation.getContext(),
                        instrumentation.getUiAutomation(),
                        new ImeSettings.Builder()
                                .setMockImePackageName(MockImePackageNames.MockIme2)
                                .setSuppressResetIme(true))) {
            var stream1 = session1.openEventStream();
            var stream2 = session2.openEventStream();
            final String marker = getTestMarker();
            TestActivity.startSync(activity-> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                final EditText editText = new EditText(activity);
                editText.setPrivateImeOptions(marker);
                editText.setHint("editText");
                editText.requestFocus();
                layout.addView(editText);
                return layout;
            });

            expectEvent(stream2, editorMatcher("onStartInput", marker), TIMEOUT);
            stream1.skipAll();

            expectCommand(stream2, session2.callSwitchInputMethod(session1.getImeId()), TIMEOUT);
            expectEvent(stream2, event -> "onDestroy".equals(event.getEventName()), TIMEOUT);

            expectEvent(stream1, event -> "onCreate".equals(event.getEventName()), TIMEOUT);
            expectEvent(stream1, editorMatcher("onStartInput", marker), TIMEOUT);
        }
    }
}
