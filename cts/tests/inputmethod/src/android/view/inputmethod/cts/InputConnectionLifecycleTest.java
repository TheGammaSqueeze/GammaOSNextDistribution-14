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

package android.view.inputmethod.cts;

import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Color;
import android.os.SystemClock;
import android.system.Os;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.view.inputmethod.cts.util.NoOpInputConnection;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides basic tests for lifecycle of {@link InputConnection}.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public final class InputConnectionLifecycleTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private static final int TEST_VIEW_HEIGHT = 10;
    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.InputConnectionLifecycleTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    /**
     * A mostly-minimum implementation of {@link View} that can be used to test custom
     * implementations of {@link View#onCreateInputConnection(EditorInfo)}.
     */
    private static class TestEditor extends View {
        TestEditor(@NonNull Context context) {
            super(context);
            setBackgroundColor(Color.YELLOW);
            setFocusableInTouchMode(true);
            setFocusable(true);
            setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, TEST_VIEW_HEIGHT));
        }
    }


    /**
     * Test {@link InputConnection#closeConnection()} gets called on the associated thread after
     * {@link InputMethodManager#restartInput(View)}.
     *
     * @see InputConnectionHandlerTest#testCloseConnectionWithRestartInput()
     */
    @Test
    public void testCloseConnectionWithRestartInput() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger callingThreadId = new AtomicInteger(0);

            final int mainThreadId = getOnMainSync(Os::gettid);

            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();

            final AtomicReference<TestEditor> testEditorRef = new AtomicReference<>();

            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Just to be conservative, we explicitly check MockImeSession#isActive() here when
                // injecting our custom InputConnection implementation.
                final TestEditor testEditor = new TestEditor(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return imeSession.isActive();
                    }

                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        if (!imeSession.isActive()) {
                            return null;
                        }
                        outAttrs.privateImeOptions = marker;
                        return new NoOpInputConnection() {
                            @Override
                            public void closeConnection() {
                                if (callingThreadId.compareAndExchange(0, Os.gettid()) == 0) {
                                    latch.countDown();
                                }
                                super.closeConnection();
                            }
                        };
                    }
                };
                testEditorRef.set(testEditor);

                testEditor.requestFocus();
                layout.addView(testEditor);

                return layout;
            });

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            assertEquals(1, latch.getCount());

            runOnMainSync(() -> {
                final TestEditor testEditor = testEditorRef.get();
                final InputMethodManager imm = Objects.requireNonNull(
                        testEditor.getContext().getSystemService(InputMethodManager.class));
                imm.restartInput(testEditor);
            });

            assertTrue("closeConnection() must be called",
                    latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
            assertEquals("closeConnection() must happen on the main thread",
                    mainThreadId, callingThreadId.get());
        }
    }

    /**
     * Test {@link InputConnection#closeConnection()} gets called on the associated thread after
     * losing the {@link View} focus.
     *
     * @see InputConnectionHandlerTest#testCloseConnectionWithLosingViewFocus()
     */
    @Test
    public void testCloseConnectionWithLosingViewFocus() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger callingThreadId = new AtomicInteger(0);

            final int mainThreadId = getOnMainSync(Os::gettid);

            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();

            final AtomicReference<EditText> anotherEditTextRef = new AtomicReference<>();

            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Just to be conservative, we explicitly check MockImeSession#isActive() here when
                // injecting our custom InputConnection implementation.
                final TestEditor testEditor = new TestEditor(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return imeSession.isActive();
                    }

                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        if (!imeSession.isActive()) {
                            return null;
                        }
                        outAttrs.privateImeOptions = marker;
                        return new NoOpInputConnection() {
                            @Override
                            public void closeConnection() {
                                if (callingThreadId.compareAndExchange(0, Os.gettid()) == 0) {
                                    latch.countDown();
                                }
                                super.closeConnection();
                            }
                        };
                    }
                };

                testEditor.requestFocus();
                layout.addView(testEditor);

                final EditText editText = new EditText(activity);
                layout.addView(editText);

                anotherEditTextRef.set(editText);

                return layout;
            });

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            assertEquals(1, latch.getCount());

            runOnMainSync(() -> anotherEditTextRef.get().requestFocus());

            assertTrue("closeConnection() must be called",
                    latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
            assertEquals("closeConnection() must happen on the main thread",
                    mainThreadId, callingThreadId.get());
        }
    }

    /**
     * Test {@link InputConnection#closeConnection()} gets called on the associated thread after
     * losing the {@link android.view.Window} focus.
     *
     * @see InputConnectionHandlerTest#testCloseConnectionWithLosingWindowFocus()
     */
    @Test
    public void testCloseConnectionWithLosingWindowFocus() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(
                     instrumentation.getContext(),
                     instrumentation.getUiAutomation(),
                     new ImeSettings.Builder())) {

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger callingThreadId = new AtomicInteger(0);

            final int mainThreadId = getOnMainSync(Os::gettid);

            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();

            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Just to be conservative, we explicitly check MockImeSession#isActive() here when
                // injecting our custom InputConnection implementation.
                final TestEditor testEditor = new TestEditor(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return imeSession.isActive();
                    }

                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        if (!imeSession.isActive()) {
                            return null;
                        }
                        outAttrs.privateImeOptions = marker;
                        return new NoOpInputConnection() {
                            @Override
                            public void closeConnection() {
                                if (callingThreadId.compareAndExchange(0, Os.gettid()) == 0) {
                                    latch.countDown();
                                }
                                super.closeConnection();
                            }
                        };
                    }
                };

                testEditor.requestFocus();
                layout.addView(testEditor);

                return layout;
            });

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            assertEquals(1, latch.getCount());

            // Launch a new Activity in a different process.
            final boolean instant =
                    instrumentation.getTargetContext().getPackageManager().isInstantApp();
            try (AutoCloseable unused = MockTestActivityUtil.launchSync(instant, TIMEOUT)) {
                assertTrue("closeConnection() must be called",
                        latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
                assertEquals("closeConnection() must happen on the main thread",
                        mainThreadId, callingThreadId.get());
            }
        }
    }
}
