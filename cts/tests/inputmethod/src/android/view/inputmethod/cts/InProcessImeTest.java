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

package android.view.inputmethod.cts;

import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.inprocime.InProcIme;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.NoOpInputConnection;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InProcessImeTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.InProcessImeTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private void enableInProcIme() {
        final String inProcImeId = new ComponentName(
                InstrumentationRegistry.getInstrumentation().getContext().getPackageName(),
                InProcIme.class.getName()).flattenToShortString();
        SystemUtil.runShellCommandOrThrow("ime enable " + inProcImeId);
        SystemUtil.runShellCommandOrThrow("ime set " + inProcImeId);
    }

    @After
    public final void resetIme() {
        SystemUtil.runShellCommandOrThrow("ime reset");
    }

    /**
     * A mostly-minimum implementation of {@link View} that can be used to test custom
     * implementations of {@link View#onCreateInputConnection(EditorInfo)}.
     */
    static class TestEditor extends View {
        TestEditor(@NonNull Context context) {
            super(context);
            setBackgroundColor(Color.YELLOW);
            setFocusableInTouchMode(true);
            setFocusable(true);
            setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 10 /* height */));
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }
    }

    /**
     * Verifies that calling {@link InputMethodManager#updateSelection(View, int, int, int, int)}
     * does not directly invoke
     * {@link android.inputmethodservice.InputMethodService#onUpdateSelection(int, int, int, int,
     * int, int)}, which can cause an infinite-loop.
     */
    @AppModeFull(reason = "No need to consider instant apps that host IME")
    @Test
    public void testOnUpdateSelectionForInProcessIme() throws Exception {
        PollingCheck.check("Make sure InProcIme is not running in the initial state.", TIMEOUT,
                () -> InProcIme.getInstance() == null);

        final String marker = getTestMarker();
        final AtomicReference<TestEditor> testEditorRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final TestEditor testEditor = new TestEditor(activity) {
                @Override
                public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                    outAttrs.privateImeOptions = marker;
                    return new NoOpInputConnection();
                }
            };

            layout.addView(testEditor);
            testEditor.requestFocus();
            testEditorRef.set(testEditor);
            return layout;
        });

        enableInProcIme();

        PollingCheck.check("Wait until InProcIme becomes ready.", TIMEOUT,
                () -> InProcIme.getInstance() != null);
        final InProcIme inProcIme = InProcIme.getInstance();

        PollingCheck.check("Wait until InProcIme#onStartInput() gets called.", TIMEOUT,
                () -> getOnMainSync(() -> {
                    final EditorInfo editorInfo = inProcIme.getCurrentInputEditorInfo();
                    if (editorInfo == null) {
                        return false;
                    }
                    return TextUtils.equals(editorInfo.privateImeOptions, marker);
                }));

        final ThreadLocal<Boolean> invocationOnGoing = new ThreadLocal<>();
        final AtomicBoolean directInvocationDetected = new AtomicBoolean(false);
        final CountDownLatch onUpdateSelectionLatch = new CountDownLatch(1);
        final int expectedNewSelStart = 123;
        final int expectedNewSelEnd = 321;
        runOnMainSync(() -> {
            inProcIme.setOnUpdateSelectionListener((oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                    candidatesStart, candidatesEnd) -> {
                if (newSelStart == expectedNewSelStart && newSelEnd == expectedNewSelEnd) {
                    directInvocationDetected.set(invocationOnGoing.get());
                    onUpdateSelectionLatch.countDown();
                }
            });

            final TestEditor testEditor = testEditorRef.get();
            final InputMethodManager imm =
                    testEditor.getContext().getSystemService(InputMethodManager.class);
            invocationOnGoing.set(true);
            try {
                imm.updateSelection(testEditor, expectedNewSelStart, expectedNewSelEnd, -1, -1);
            } finally {
                invocationOnGoing.set(false);
            }
        });
        assertTrue(onUpdateSelectionLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));

        assertFalse("InputMethodManager#updateSelection must not directly invoke"
                + " InputMethodService#onUpdateSelection", directInvocationDetected.get());
    }
}
