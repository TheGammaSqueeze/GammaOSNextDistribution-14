/*
 * Copyright (C) 2021 The Android Open Source Project
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
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSyncWithRethrowing;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.system.Os;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.HandlerInputConnection;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEvent;
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
 * Tests the thread-affinity in {@link InputConnection} callbacks provided by
 * {@link InputConnection#getHandler()}.
 *
 * <p>TODO: Add more tests.</p>
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputConnectionHandlerTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    /**
     * The value used in android.inputmethodservice.RemoteInputConnection#MAX_WAIT_TIME_MILLIS.
     *
     * <p>Although this is not a strictly-enforced timeout for all the Android devices, hopefully
     * it'd be acceptable to assume that IMEs can receive result within 2 second even on slower
     * devices.</p>
     *
     * <p>TODO: Consider making this as a test API.</p>
     */
    private static final long TIMEOUT_IN_REMOTE_INPUT_CONNECTION =
            TimeUnit.MILLISECONDS.toMillis(2000);

    private static final int TEST_VIEW_HEIGHT = 10;

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.InputConnectionHandlerTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private static final class InputConnectionHandlingThread extends HandlerThread
            implements AutoCloseable {

        private final Handler mHandler;

        InputConnectionHandlingThread() {
            super("IC-callback");
            start();
            mHandler = Handler.createAsync(getLooper());
        }

        @NonNull
        Handler getHandler() {
            return mHandler;
        }

        @Override
        public void close() {
            quitSafely();
            try {
                join(TIMEOUT);
            } catch (InterruptedException e) {
                fail("Failed to stop the thread: " + e);
            }
        }
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
                    ViewGroup.LayoutParams.MATCH_PARENT, TEST_VIEW_HEIGHT));
        }
    }

    /**
     * Test {@link InputConnection#commitText(CharSequence, int)} respects
     * {@link InputConnection#getHandler()}.
     */
    @Test
    public void testCommitText() throws Exception {
        try (InputConnectionHandlingThread thread = new InputConnectionHandlingThread();
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            final AtomicInteger callingThreadId = new AtomicInteger(0);
            final CountDownLatch latch = new CountDownLatch(1);

            final class MyInputConnection extends HandlerInputConnection {
                MyInputConnection() {
                    super(thread.getHandler());
                }

                @Override
                public boolean commitText(CharSequence text, int newCursorPosition) {
                    callingThreadId.set(Os.gettid());
                    latch.countDown();
                    return super.commitText(text, newCursorPosition);
                }
            }

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
                        if (imeSession.isActive()) {
                            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
                            outAttrs.privateImeOptions = marker;
                            return new MyInputConnection();
                        }
                        return null;
                    }
                };

                testEditor.requestFocus();
                layout.addView(testEditor);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final ImeCommand command = imeSession.callCommitText("", 1);
            expectCommand(stream, command, TIMEOUT);

            assertTrue("commitText() must be called", latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

            assertEquals("commitText() must happen on the handler thread",
                    thread.getThreadId(), callingThreadId.get());
        }
    }

    /**
     * Test {@link InputConnection#closeConnection()} gets called on the associated thread after
     * {@link InputMethodManager#restartInput(View)}.
     *
     * @see InputConnectionLifecycleTest#testCloseConnectionWithRestartInput()
     */
    @Test
    public void testCloseConnectionWithRestartInput() throws Exception {
        try (InputConnectionHandlingThread thread = new InputConnectionHandlingThread();
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger callingThreadId = new AtomicInteger(0);

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
                        outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
                        outAttrs.privateImeOptions = marker;
                        return new HandlerInputConnection(thread.getHandler()) {
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
            assertEquals("closeConnection() must happen on the handler thread",
                    thread.getThreadId(), callingThreadId.get());
        }
    }

    /**
     * Test {@link InputConnection#closeConnection()} gets called on the associated thread after
     * losing the {@link View} focus.
     *
     * @see InputConnectionLifecycleTest#testCloseConnectionWithLosingViewFocus()
     */
    @Test
    public void testCloseConnectionWithLosingViewFocus() throws Exception {
        try (InputConnectionHandlingThread thread = new InputConnectionHandlingThread();
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger callingThreadId = new AtomicInteger(0);

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
                        return new HandlerInputConnection(thread.getHandler()) {
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
            assertEquals("closeConnection() must happen on the handler thread",
                    thread.getThreadId(), callingThreadId.get());
        }
    }

    /**
     * Test {@link InputConnection#closeConnection()} gets called on the associated thread after
     * losing the {@link android.view.Window} focus.
     *
     * @see InputConnectionLifecycleTest#testCloseConnectionWithLosingWindowFocus()
     */
    @Test
    public void testCloseConnectionWithLosingWindowFocus() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (InputConnectionHandlingThread thread = new InputConnectionHandlingThread();
             MockImeSession imeSession = MockImeSession.create(
                     instrumentation.getContext(),
                     instrumentation.getUiAutomation(),
                     new ImeSettings.Builder())) {

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger callingThreadId = new AtomicInteger(0);

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
                        return new HandlerInputConnection(thread.getHandler()) {
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
                assertEquals("closeConnection() must happen on the handler thread",
                        thread.getThreadId(), callingThreadId.get());
            }
        }
    }

    /**
     * Test {@link InputConnection#reportFullscreenMode(boolean)} respects
     * {@link InputConnection#getHandler()}.
     */
    @Test
    public void testReportFullscreenMode() throws Exception {
        try (InputConnectionHandlingThread thread = new InputConnectionHandlingThread();
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder().setFullscreenModePolicy(
                             ImeSettings.FullscreenModePolicy.FORCE_FULLSCREEN))) {

            final AtomicInteger callingThreadId = new AtomicInteger(0);
            final CountDownLatch latch = new CountDownLatch(1);

            final class MyInputConnection extends HandlerInputConnection {
                MyInputConnection() {
                    super(thread.getHandler());
                }

                @Override
                public boolean reportFullscreenMode(boolean enabled) {
                    callingThreadId.set(Os.gettid());
                    latch.countDown();
                    return true;
                }
            }

            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();

            final AtomicReference<View> testEditorViewRef = new AtomicReference<>();
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
                        if (imeSession.isActive()) {
                            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
                            outAttrs.privateImeOptions = marker;
                            return new MyInputConnection();
                        }
                        return null;
                    }
                };

                testEditor.requestFocus();
                testEditorViewRef.set(testEditor);
                layout.addView(testEditor);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            assertFalse("InputMethodManager#isFullscreenMode() must return false",
                    getOnMainSync(() -> InstrumentationRegistry.getInstrumentation().getContext()
                            .getSystemService(InputMethodManager.class).isFullscreenMode()));

            // In order to have an IME be shown in the fullscreen mode,
            // SOFT_INPUT_STATE_ALWAYS_VISIBLE is insufficient.  An explicit API call is necessary.
            runOnMainSync(() -> {
                final View editor = testEditorViewRef.get();
                editor.getContext().getSystemService(InputMethodManager.class)
                        .showSoftInput(editor, 0);
            });

            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);

            assertTrue("reportFullscreenMode() must be called",
                    latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

            assertEquals("reportFullscreenMode() must happen on the handler thread",
                    thread.getThreadId(), callingThreadId.get());

            assertTrue("InputMethodManager#isFullscreenMode() must return true",
                    getOnMainSync(() -> InstrumentationRegistry.getInstrumentation().getContext()
                            .getSystemService(InputMethodManager.class).isFullscreenMode()));
            assertTrue(expectCommand(stream, imeSession.callVerifyExtractViewNotNull(), TIMEOUT)
                    .getReturnBooleanValue());
        }
    }

    /**
     * Make sure that handling incoming {@link InputConnection} tasks in a background thread while
     * the IME focus is being updated does not accelerate IME focus handling process.
     *
     * <p>Test case inspired from Bug 286470800 and Bug 283517132.</p>
     */
    @Test
    public void testInputConnectionSideEffect() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (InputConnectionHandlingThread thread = new InputConnectionHandlingThread();
                MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final String fenceMarker = getTestMarker();
            final AtomicReference<Runnable> removeViewRef = new AtomicReference<>();
            final CountDownLatch fenceCommandLatch = new CountDownLatch(1);
            final CountDownLatch closeConnectionLatch = new CountDownLatch(1);

            final class MyInputConnection extends HandlerInputConnection {
                MyInputConnection() {
                    super(thread.getHandler());
                }

                @Override
                public boolean performPrivateCommand(String action, Bundle data) {
                    if (fenceMarker.equals(action)) {
                        fenceCommandLatch.countDown();
                        return true;
                    }
                    return false;
                }

                @Override
                public void closeConnection() {
                    closeConnectionLatch.countDown();
                    super.closeConnection();
                }
            }

            // Launch test activity
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
                        if (imeSession.isActive()) {
                            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
                            outAttrs.privateImeOptions = marker;
                            return new MyInputConnection();
                        }
                        return null;
                    }
                };

                layout.addView(testEditor);
                removeViewRef.set(() -> layout.removeView(testEditor));

                testEditor.requestFocus();
                return layout;
            });

            // "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            runOnMainSyncWithRethrowing(() -> {
                // Trigger layout.removeView(testEditor)
                removeViewRef.getAndSet(null).run();

                // In this state, editText2 is scheduled to become the next IME focus target, but
                // it's not yet completed until the next on-idle.
                // IMEs' calling IMS#requestCursorUpdates() in this state should not **immediately**
                // trigger startInput().
                imeSession.callRequestCursorUpdates(0);

                // Then issue fence command to verify that IC still receives commands.
                imeSession.callPerformPrivateCommand(fenceMarker, null);
                try {
                    assertTrue(fenceCommandLatch.await(TIMEOUT, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                assertEquals(1, closeConnectionLatch.getCount());
            });
        }
    }

    /**
     * A holder of {@link Handler} that is bound to a background {@link Looper} where
     * {@link Throwable} thrown from tasks running there will be just ignored instead of triggering
     * process crashes.
     */
    private static final class ErrorSwallowingHandlerThread implements AutoCloseable {
        @NonNull
        private final Handler mHandler;

        @NonNull
        Handler getHandler() {
            return mHandler;
        }

        @NonNull
        static ErrorSwallowingHandlerThread create() {
            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Looper> mLooperRef = new AtomicReference<>();
            new Thread(() -> {
                Looper.prepare();
                mLooperRef.set(Looper.myLooper());
                latch.countDown();

                while (true) {
                    try {
                        Looper.loop();
                        return;
                    } catch (Throwable ignore) {
                    }
                }
            }).start();

            try {
                assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Failed to create a Handler thread");
            }

            final Handler handler = Handler.createAsync(mLooperRef.get());
            return new ErrorSwallowingHandlerThread(handler);
        }

        private ErrorSwallowingHandlerThread(@NonNull Handler handler) {
            mHandler = handler;
        }

        @Override
        public void close() {
            mHandler.getLooper().quitSafely();
            try {
                mHandler.getLooper().getThread().join(TIMEOUT);
            } catch (InterruptedException e) {
                fail("Failed to terminate the thread");
            }
        }
    }

    /**
     * Ensures that {@code event}'s elapse time is less than the given threshold.
     *
     * @param event {@link ImeEvent} to be tested.
     * @param elapseThresholdInMilliSecond threshold in milli sec.
     */
    private static void expectElapseTimeLessThan(@NonNull ImeEvent event,
            long elapseThresholdInMilliSecond) {
        final long elapseTimeInMilli = TimeUnit.NANOSECONDS.toMillis(
                event.getExitTimestamp() - event.getEnterTimestamp());
        if (elapseTimeInMilli > elapseThresholdInMilliSecond) {
            fail(event.getEventName() + " took " + elapseTimeInMilli + " msec,"
                    + " which must be less than " + elapseThresholdInMilliSecond + " msec.");
        }
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} that throws an exception.
     */
    @Test
    public void testExceptionFromGetSurroundingText() throws Exception {
        try (ErrorSwallowingHandlerThread handlerThread = ErrorSwallowingHandlerThread.create();
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            final CountDownLatch latch = new CountDownLatch(1);

            final class MyInputConnection extends HandlerInputConnection {
                MyInputConnection() {
                    super(handlerThread.getHandler());
                }

                @Override
                public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                        int flags) {
                    latch.countDown();
                    throw new RuntimeException("Exception!");
                }

            }

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
                        if (imeSession.isActive()) {
                            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
                            outAttrs.privateImeOptions = marker;
                            return new MyInputConnection();
                        }
                        return null;
                    }
                };

                testEditor.requestFocus();
                layout.addView(testEditor);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final ImeCommand command = imeSession.callGetSurroundingText(1, 1, 0);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);

            assertTrue("IC#getSurroundingText() must be called",
                    latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

            assertTrue("Exceptions from IC#getSurroundingText() must be interpreted as null.",
                    result.isNullReturnValue());
            expectElapseTimeLessThan(result, TIMEOUT_IN_REMOTE_INPUT_CONNECTION);
        }
    }
}
