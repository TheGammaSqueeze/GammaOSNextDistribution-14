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

import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
import static android.view.inputmethod.InputConnection.HANDWRITING_GESTURE_RESULT_SUCCESS;

import static com.android.cts.mocka11yime.MockA11yImeEventStreamUtils.editorMatcherForA11yIme;
import static com.android.cts.mocka11yime.MockA11yImeEventStreamUtils.expectA11yImeCommand;
import static com.android.cts.mocka11yime.MockA11yImeEventStreamUtils.expectA11yImeEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.withDescription;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ClipDescription;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.text.Annotation;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.DeleteGesture;
import android.view.inputmethod.DeleteRangeGesture;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.HandwritingGesture;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InsertGesture;
import android.view.inputmethod.InsertModeGesture;
import android.view.inputmethod.JoinOrSplitGesture;
import android.view.inputmethod.PreviewableHandwritingGesture;
import android.view.inputmethod.RemoveSpaceGesture;
import android.view.inputmethod.SelectGesture;
import android.view.inputmethod.SelectRangeGesture;
import android.view.inputmethod.SurroundingText;
import android.view.inputmethod.TextAttribute;
import android.view.inputmethod.TextBoundsInfo;
import android.view.inputmethod.TextBoundsInfoResult;
import android.view.inputmethod.TextSnapshot;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.cts.inputmethod.LegacyImeClientTestUtils;
import com.android.cts.mocka11yime.MockA11yImeEventStream;
import com.android.cts.mocka11yime.MockA11yImeSession;
import com.android.cts.mocka11yime.MockA11yImeSettings;
import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import com.google.common.truth.Correspondence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * Provides basic tests for APIs defined in {@link InputConnection}.
 *
 * <p>TODO(b/193535269): Clean up boilerplate code around mocking InputConnection.</p>
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputConnectionEndToEndTest extends EndToEndImeTestBase {
    private static final long TIME_SLICE = TimeUnit.MILLISECONDS.toMillis(125);
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long EXPECTED_NOT_CALLED_TIMEOUT = TimeUnit.SECONDS.toMillis(1);
    private static final long LONG_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
    private static final long IMMEDIATE_TIMEOUT_NANO = TimeUnit.MILLISECONDS.toNanos(200);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.InputConnectionEndToEndTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    @Rule
    public final ErrorCollector mErrorCollector = new ErrorCollector();

    /**
     * A utility method to verify a method is called within a certain timeout period then block
     * it by {@link BlockingMethodVerifier#close()} is called.
     */
    private static final class BlockingMethodVerifier implements AutoCloseable {
        private final CountDownLatch mWaitUntilMethodCalled = new CountDownLatch(1);
        private final CountDownLatch mWaitUntilTestFinished = new CountDownLatch(1);

        /**
         * Used to notify when a method to be tested is called.
         */
        void onMethodCalled() {
            try {
                mWaitUntilMethodCalled.countDown();
                mWaitUntilTestFinished.await();
            } catch (InterruptedException e) {
            }
        }

        /**
         * Ensures that the method to be tested is called within {@param timeout}.
         *
         * @param message Message to be shown when the method is not called despite the expectation.
         * @param timeout Timeout in milliseconds.
         */
        void expectMethodCalled(@NonNull String message, long timeout) {
            try {
                assertTrue(message, mWaitUntilMethodCalled.await(timeout, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                fail(message + e);
            }
        }

        /**
         * Unblock the method to be tested to avoid the test from being blocked forever.
         */
        @Override
        public void close() throws Exception {
            mWaitUntilTestFinished.countDown();
        }
    }

    /**
     * A utility method to verify that a method is called with a certain set of parameters.
     */
    private static final class MethodCallVerifier {
        private final AtomicReference<Bundle> mArgs = new AtomicReference<>();
        private final AtomicInteger mCallCount = new AtomicInteger(0);

        @AnyThread
        void reset() {
            mArgs.set(null);
            mCallCount.set(0);
        }

        /**
         * Used to record when a method to be tested is called.
         *
         * @param argumentsRecorder a {@link Consumer} to capture method parameters.
         */
        void onMethodCalled(@NonNull Consumer<Bundle> argumentsRecorder) {
            final Bundle bundle = new Bundle();
            argumentsRecorder.accept(bundle);
            mArgs.set(bundle);
            mCallCount.incrementAndGet();
        }

        /**
         * Used to assert captured parameters later.
         *
         * @param argumentsVerifier a {@link Consumer} to verify method arguments.
         * @throws AssertionError when {@link #onMethodCalled(Consumer)} was not called only once.
         */
        void assertCalledOnce(@NonNull Consumer<Bundle> argumentsVerifier) {
            assertEquals(1, mCallCount.get());
            final Bundle bundle = mArgs.get();
            assertNotNull(bundle);
            argumentsVerifier.accept(bundle);
        }

        /**
         * Ensures that the method to be tested is called within {@param timeout}.
         *
         * @param argumentsVerifier a {@link Consumer} to verify method arguments.
         * @param timeout timeout in millisecond
         * @throws AssertionError when {@link #onMethodCalled(Consumer)} was not called only once.
         */
        void expectCalledOnce(@NonNull Consumer<Bundle> argumentsVerifier, long timeout) {
            // Currently using busy-wait because CountDownLatch is not compatible with reset().
            // TODO: Consider using other more efficient operation.
            long remainingTime = timeout;
            while (mCallCount.get() == 0) {
                if (remainingTime < 0) {
                    fail("The method must be called, but was not within" + timeout + " msec.");
                }
                SystemClock.sleep(TIME_SLICE);
                remainingTime -= TIME_SLICE;
            }
            assertEquals(1, mCallCount.get());
            final Bundle bundle = mArgs.get();
            assertNotNull(bundle);
            argumentsVerifier.accept(bundle);
        }

        /**
         * Used to assert that {@link #onMethodCalled(Consumer)} was never called.
         *
         * @param callCountVerificationMessage A message to be used when the assertion fails.
         */
        void assertNotCalled(@Nullable String callCountVerificationMessage) {
            if (callCountVerificationMessage != null) {
                assertEquals(callCountVerificationMessage, 0, mCallCount.get());
            } else {
                assertEquals(0, mCallCount.get());
            }
        }

        /**
         * Ensures that the method to be tested is not called within {@param timeout}.
         *
         * @param callCountVerificationMessage A message to be used when the assertion fails.
         * @param timeout timeout in millisecond
         */
        void expectNotCalled(@Nullable String callCountVerificationMessage, long timeout) {
            // Currently using busy-wait because CountDownLatch is not compatible with reset().
            // TODO: Consider using other more efficient operation.
            long remainingTime = timeout;
            while (true) {
                if (mCallCount.get() != 0) {
                    fail("The method must not be called. params=" + evaluateBundle(mArgs.get()));
                }
                if (remainingTime < 0) {
                    break;  // This is indeed an expected scenario, not an error.
                }
                SystemClock.sleep(TIME_SLICE);
                remainingTime -= TIME_SLICE;
            }
            if (callCountVerificationMessage != null) {
                assertEquals(callCountVerificationMessage, 0, mCallCount.get());
            } else {
                assertEquals(0, mCallCount.get());
            }
        }

        /**
         * Recursively evaluate {@link Bundle} so that {@link Bundle#toString()} can print all the
         * nested {@link Bundle} objects.
         *
         * @param bundle {@link Bundle} to recursively evaluate.
         * @return the {@code bundle} object passed.
         */
        @Nullable
        private static Bundle evaluateBundle(@Nullable Bundle bundle) {
            if (bundle != null) {
                for (String key : bundle.keySet()) {
                    final Object value = bundle.get(key);
                    if (value instanceof Bundle) {
                        evaluateBundle((Bundle) value);
                    }
                }
            }
            return bundle;
        }
    }

    /**
     * A test procedure definition for
     * {@link #testInputConnection(Function, TestProcedure, AutoCloseable)}.
     */
    @FunctionalInterface
    interface TestProcedure {
        /**
         * The test body of {@link #testInputConnection(Function, TestProcedure, AutoCloseable)}
         *
         * @param session {@link MockImeSession} to be used during this test.
         * @param stream {@link ImeEventStream} associated with {@code session}.
         */
        void run(@NonNull MockImeSession session, @NonNull ImeEventStream stream) throws Exception;
    }

    /**
     * A test procedure definition for
     * {@link #testA11yInputConnection(Function, TestProcedureForAccessibilityIme)}
     */
    @FunctionalInterface
    interface TestProcedureForAccessibilityIme {
        /**
         * The test body of {@link #testInputConnection(Function, TestProcedure, AutoCloseable)}
         *
         * @param a11yImeSession {@link MockA11yImeSession} to be used during this test.
         * @param stream {@link MockA11yImeEventStream} associated with {@code session}.
         */
        void run(@NonNull MockA11yImeSession a11yImeSession, @NonNull MockA11yImeEventStream stream)
                throws Exception;
    }

    /**
     * A test procedure definition for
     * {@link #testInputConnection(Function, TestProcedureForMixedImes, AutoCloseable)}.
     */
    @FunctionalInterface
    interface TestProcedureForMixedImes {
        /**
         * The test body of {@link #testInputConnection(Function, TestProcedure, AutoCloseable)}
         *
         * @param imeSession {@link MockImeSession} to be used during this test.
         * @param imeStream {@link ImeEventStream} associated with {@code session}.
         * @param a11yImeSession {@link MockA11yImeSession} to be used during this test.
         * @param a11yImeStream {@link MockA11yImeEventStream} associated with {@code session}.
         */
        void run(@NonNull MockImeSession imeSession, @NonNull ImeEventStream imeStream,
                @NonNull MockA11yImeSession a11yImeSession,
                @NonNull MockA11yImeEventStream a11yImeStream)
                throws Exception;
    }

    /**
     * Tries to trigger {@link com.android.cts.mockime.MockIme#onUnbindInput()} by showing another
     * Activity in a different process.
     */
    private void triggerUnbindInput() {
        final boolean isInstant = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getPackageManager().isInstantApp();
        MockTestActivityUtil.launchSync(isInstant, TIMEOUT);
    }

    /**
     * A utility method to run a unit test for {@link InputConnection}.
     *
     * <p>This utility method enables you to avoid boilerplate code when writing unit tests for
     * {@link InputConnection}.</p>
     *
     * @param inputConnectionWrapperProvider {@link Function} to install custom hooks to the
     *                                       original {@link InputConnection}.
     * @param testProcedure Test body.
     */
    private void testInputConnection(
            Function<InputConnection, InputConnection> inputConnectionWrapperProvider,
            TestProcedure testProcedure) throws Exception {
        testInputConnection(inputConnectionWrapperProvider, testProcedure, null);
    }

    /**
     * A utility method to run a unit test for {@link InputConnection}.
     *
     * <p>This utility method enables you to avoid boilerplate code when writing unit tests for
     * {@link InputConnection}.</p>
     *
     * @param inputConnectionWrapperProvider {@link Function} to install custom hooks to the
     *                                       original {@link InputConnection}.
     * @param testProcedure Test body.
     */
    private void testInputConnection(
            Function<InputConnection, InputConnection> inputConnectionWrapperProvider,
            TestProcedureForMixedImes testProcedure) throws Exception {
        testInputConnection(inputConnectionWrapperProvider, testProcedure, null);
    }

    /**
     * A utility method to run a unit test for {@link InputConnection} with
     * {@link android.accessibilityservice.InputMethod}.
     *
     * <p>This utility method enables you to avoid boilerplate code when writing unit tests for
     * {@link InputConnection}.</p>
     *
     * @param inputConnectionWrapperProvider {@link Function} to install custom hooks to the
     *                                       original {@link InputConnection}.
     * @param testProcedure Test body.
     */
    private void testA11yInputConnection(
            Function<InputConnection, InputConnection> inputConnectionWrapperProvider,
            TestProcedureForAccessibilityIme testProcedure) throws Exception {
        testInputConnection(inputConnectionWrapperProvider,
                (imeSession, imeStream, a11ySession, a11yStream)
                        -> testProcedure.run(a11ySession, a11yStream), null);
    }

    /**
     * A utility method to run a unit test for {@link InputConnection} with
     * {@link android.accessibilityservice.InputMethod}.
     *
     * <p>This utility method enables you to avoid boilerplate code when writing unit tests for
     * {@link InputConnection}.</p>
     *
     * @param inputConnectionWrapperProvider {@link Function} to install custom hooks to the
     *                                       original {@link InputConnection}.
     * @param testProcedure Test body.
     * @param closeable {@link AutoCloseable} object to be cleaned up after running test.
     **/
    private void testA11yInputConnection(
            Function<InputConnection, InputConnection> inputConnectionWrapperProvider,
            TestProcedureForAccessibilityIme testProcedure,
            @Nullable AutoCloseable closeable) throws Exception {
        testInputConnection(inputConnectionWrapperProvider,
                (imeSession, imeStream, a11ySession, a11yStream)
                        -> testProcedure.run(a11ySession, a11yStream), closeable);
    }

    /**
     * A utility method to run a unit test for {@link InputConnection} that is as-if built with
     * {@link android.os.Build.VERSION_CODES#CUPCAKE} SDK.
     *
     * <p>This helps you to test the situation where IMEs' calling newly added
     * {@link InputConnection} APIs would be fallen back to its default interface method or could be
     * causing {@link java.lang.AbstractMethodError} unless specially handled.
     *
     * @param testProcedure Test body.
     */
    private void testMinimallyImplementedInputConnection(TestProcedure testProcedure)
            throws Exception {
        testInputConnection(
                ic -> LegacyImeClientTestUtils.createMinimallyImplementedNoOpInputConnection(),
                testProcedure, null);
    }

    /**
     * A utility method to run a unit test for {@link InputConnection} that is as-if built with
     * {@link android.os.Build.VERSION_CODES#CUPCAKE} SDK.
     *
     * <p>This helps you to test the situation where IMEs' calling newly added
     * {@link InputConnection} APIs would be fallen back to its default interface method or could be
     * causing {@link java.lang.AbstractMethodError} unless specially handled.
     *
     * @param testProcedure Test body.
     */
    private void testMinimallyImplementedInputConnectionForA11y(
            TestProcedureForAccessibilityIme testProcedure)
            throws Exception {
        testA11yInputConnection(
                ic -> LegacyImeClientTestUtils.createMinimallyImplementedNoOpInputConnection(),
                testProcedure);
    }

    /**
     * A utility method to run a unit test for {@link InputConnection}.
     *
     * <p>This utility method enables you to avoid boilerplate code when writing unit tests for
     * {@link InputConnection}.</p>
     *
     * @param inputConnectionWrapperProvider {@link Function} to install custom hooks to the
     *                                       original {@link InputConnection}.
     * @param testProcedure Test body.
     * @param closeable {@link AutoCloseable} object to be cleaned up after running test.
     */
    private void testInputConnection(
            Function<InputConnection, InputConnection> inputConnectionWrapperProvider,
            TestProcedure testProcedure, @Nullable AutoCloseable closeable) throws Exception {
        try (AutoCloseable closeableHolder = closeable;
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Just to be conservative, we explicitly check MockImeSession#isActive() here when
                // injecting our custom InputConnection implementation.
                final EditText editText = new EditText(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return imeSession.isActive();
                    }

                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        if (imeSession.isActive()) {
                            final InputConnection ic = super.onCreateInputConnection(outAttrs);
                            return inputConnectionWrapperProvider.apply(ic);
                        }
                        return null;
                    }
                };

                editText.setPrivateImeOptions(marker);
                editText.setHint("editText");
                editText.requestFocus();

                layout.addView(editText);
                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            testProcedure.run(imeSession, stream);
        }
    }

    /**
     * A utility method to run a unit test for {@link InputConnection}.
     *
     * <p>This utility method enables you to avoid boilerplate code when writing unit tests for
     * {@link InputConnection}.</p>
     *
     * @param inputConnectionWrapperProvider {@link Function} to install custom hooks to the
     *                                       original {@link InputConnection}.
     * @param testProcedure Test body.
     * @param closeable {@link AutoCloseable} object to be cleaned up after running test.
     */
    private void testInputConnection(
            Function<InputConnection, InputConnection> inputConnectionWrapperProvider,
            TestProcedureForMixedImes testProcedure,
            @Nullable AutoCloseable closeable) throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final UiAutomation uiAutomation = instrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        try (AutoCloseable closeableHolder = closeable;
             MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                     uiAutomation, new ImeSettings.Builder())) {
            final ImeEventStream imeStream = imeSession.openEventStream();

            final String marker = getTestMarker();

            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Just to be conservative, we explicitly check MockImeSession#isActive() here when
                // injecting our custom InputConnection implementation.
                final EditText editText = new EditText(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return imeSession.isActive();
                    }

                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        if (imeSession.isActive()) {
                            final InputConnection ic = super.onCreateInputConnection(outAttrs);
                            return inputConnectionWrapperProvider.apply(ic);
                        }
                        return null;
                    }
                };

                editText.setPrivateImeOptions(marker);
                editText.setHint("editText");
                editText.requestFocus();

                layout.addView(editText);
                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                return layout;
            });

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(imeStream, editorMatcher("onStartInput", marker), TIMEOUT);

            try (MockA11yImeSession a11yImeSession = MockA11yImeSession.create(
                    instrumentation.getContext(), uiAutomation, MockA11yImeSettings.DEFAULT,
                    TIMEOUT)) {
                final MockA11yImeEventStream a11yImeEventStream = a11yImeSession.openEventStream();

                // Wait until "onStartInput" gets called for the EditText.
                expectA11yImeEvent(a11yImeEventStream,
                        editorMatcherForA11yIme("onStartInput", marker), TIMEOUT);

                // Now everything is stable and ready to start testing.
                testProcedure.run(imeSession, imeStream, a11yImeSession, a11yImeEventStream);
            }
        }
    }

    /**
     * Ensures that {@code event}'s elapse time is less than the given threshold.
     *
     * @param event {@link ImeEvent} to be tested.
     * @param elapseNanoTimeThreshold threshold in nano sec.
     */
    private static void expectElapseTimeLessThan(@NonNull ImeEvent event,
            long elapseNanoTimeThreshold) {
        final long elapseNanoTime = event.getExitTimestamp() - event.getEnterTimestamp();
        if (elapseNanoTime > elapseNanoTimeThreshold) {
            fail(event.getEventName() + " took " + elapseNanoTime + " nsec,"
                    + " which must be less than" + elapseNanoTimeThreshold + " nsec.");
        }
    }

    @Nullable
    private static CharSequence createTestCharSequence(@Nullable String text,
            @Nullable Annotation annotation) {
        if (text == null) {
            return null;
        }
        final SpannableStringBuilder sb = new SpannableStringBuilder(text);
        if (annotation != null) {
            sb.setSpan(annotation, 0, sb.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        return sb;
    }

    private static void assertEqualsForTestCharSequence(@Nullable CharSequence expected,
            @Nullable CharSequence actual) {
        assertEquals(Objects.toString(expected), Objects.toString(actual));
        final Function<CharSequence, List<Annotation>> toAnnotations = cs -> {
            if (cs instanceof Spanned) {
                final Spanned spanned = (Spanned) cs;
                return Arrays.asList(spanned.getSpans(0, cs.length(), Annotation.class));
            }
            return Collections.emptyList();
        };
        assertThat(toAnnotations.apply(actual)).comparingElementsUsing(Correspondence.transforming(
                (Annotation annotation) -> Pair.create(annotation.getKey(), annotation.getValue()),
                (Annotation annotation) -> Pair.create(annotation.getKey(), annotation.getValue()),
                "has the same Key/Value as"))
                .containsExactlyElementsIn(toAnnotations.apply(expected));
    }

    /**
     * Test {@link InputConnection#getTextAfterCursor(int, int)} works as expected.
     */
    @Test
    public void testGetTextAfterCursor() throws Exception {
        final int expectedN = 3;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final CharSequence expectedResult =
                createTestCharSequence("89", new Annotation("command", "getTextAfterCursor"));

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextAfterCursor(int n, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("n", n);
                    args.putInt("flags", flags);
                });
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetTextAfterCursor(expectedN, expectedFlags);
            final CharSequence result =
                    expectCommand(stream, command, TIMEOUT).getReturnCharSequenceValue();
            assertEqualsForTestCharSequence(expectedResult, result);
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedN, args.get("n"));
                assertEquals(expectedFlags, args.get("flags"));
            });
        });
    }

    /**
     * Test {@link InputConnection#getTextAfterCursor(int, int)} fails when a negative
     * {@code length} is passed.  See Bug 169114026 for background.
     */
    @Test
    public void testGetTextAfterCursorFailWithNegativeLength() throws Exception {
        final String unexpectedResult = "123";

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextAfterCursor(int n, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("n", n);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetTextAfterCursor(-1, 0);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertTrue("IC#getTextAfterCursor() returns null for a negative length.",
                    result.isNullReturnValue());
            methodCallVerifier.expectNotCalled(
                    "IC#getTextAfterCursor() will not be triggered with a negative length.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#getTextAfterCursor(int, int)} fails after a system-defined
     * time-out even if the target app does not respond.
     */
    @Test
    public void testGetTextAfterCursorFailWithTimeout() throws Exception {
        final int expectedN = 3;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final String unexpectedResult = "89";
        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextAfterCursor(int n, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("n", n);
                    args.putInt("flags", flags);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetTextAfterCursor(expectedN, expectedFlags);
            blocker.expectMethodCalled("IC#getTextAfterCursor() must be called back", TIMEOUT);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertTrue("When timeout happens, IC#getTextAfterCursor() returns null",
                    result.isNullReturnValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedN, args.get("n"));
                assertEquals(expectedFlags, args.get("flags"));
            });
        }, blocker);
    }

    /**
     * Test {@link InputConnection#getTextAfterCursor(int, int)} fail-fasts once unbindInput() is
     * issued.
     */
    @Test
    public void testGetTextAfterCursorFailFastAfterUnbindInput() throws Exception {
        final String unexpectedResult = "89";

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextAfterCursor(int n, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("n", n);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#getTextAfterCursor() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream, session.callGetTextAfterCursor(
                    unexpectedResult.length(), InputConnection.GET_TEXT_WITH_STYLES), TIMEOUT);
            assertTrue("Once unbindInput() happened, IC#getTextAfterCursor() returns null",
                    result.isNullReturnValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);
            methodCallVerifier.assertNotCalled(
                    "Once unbindInput() happened, IC#getTextAfterCursor() fails fast.");
        });
    }

    /**
     * Test {@link InputConnection#getTextBeforeCursor(int, int)} works as expected.
     */
    @Test
    public void testGetTextBeforeCursor() throws Exception {
        final int expectedN = 3;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final CharSequence expectedResult =
                createTestCharSequence("123", new Annotation("command", "getTextBeforeCursor"));


        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextBeforeCursor(int n, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("n", n);
                    args.putInt("flags", flags);
                });
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetTextBeforeCursor(expectedN, expectedFlags);
            final CharSequence result =
                    expectCommand(stream, command, TIMEOUT).getReturnCharSequenceValue();
            assertEqualsForTestCharSequence(expectedResult, result);
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedN, args.get("n"));
                assertEquals(expectedFlags, args.get("flags"));
            });
        });
    }

    /**
     * Test {@link InputConnection#getTextBeforeCursor(int, int)} fails when a negative
     * {@code length} is passed.  See Bug 169114026 for background.
     */
    @Test
    public void testGetTextBeforeCursorFailWithNegativeLength() throws Exception {
        final String unexpectedResult = "123";

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextBeforeCursor(int n, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("n", n);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetTextBeforeCursor(-1, 0);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertTrue("IC#getTextBeforeCursor() returns null for a negative length.",
                    result.isNullReturnValue());
            methodCallVerifier.expectNotCalled(
                    "IC#getTextBeforeCursor() will not be triggered with a negative length.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#getTextBeforeCursor(int, int)} fails after a system-defined
     * time-out even if the target app does not respond.
     */
    @Test
    public void testGetTextBeforeCursorFailWithTimeout() throws Exception {
        final int expectedN = 3;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final String unexpectedResult = "123";
        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextBeforeCursor(int n, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("n", n);
                    args.putInt("flags", flags);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetTextBeforeCursor(expectedN, expectedFlags);
            blocker.expectMethodCalled("IC#getTextBeforeCursor() must be called back", TIMEOUT);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertTrue("When timeout happens, IC#getTextBeforeCursor() returns null",
                    result.isNullReturnValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedN, args.get("n"));
                assertEquals(expectedFlags, args.get("flags"));
            });
        }, blocker);
    }

    /**
     * Test {@link InputConnection#getTextBeforeCursor(int, int)} fail-fasts once unbindInput() is
     * issued.
     */
    @Test
    public void testGetTextBeforeCursorFailFastAfterUnbindInput() throws Exception {
        final String unexpectedResult = "123";

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextBeforeCursor(int n, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("n", n);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#getTextBeforeCursor() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream, session.callGetTextBeforeCursor(
                    unexpectedResult.length(), InputConnection.GET_TEXT_WITH_STYLES), TIMEOUT);
            assertTrue("Once unbindInput() happened, IC#getTextBeforeCursor() returns null",
                    result.isNullReturnValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);
            methodCallVerifier.assertNotCalled(
                    "Once unbindInput() happened, IC#getTextBeforeCursor() fails fast.");
        });
    }

    /**
     * Test {@link InputConnection#getSelectedText(int)} works as expected.
     */
    @Test
    public void testGetSelectedText() throws Exception {
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final CharSequence expectedResult =
                createTestCharSequence("4567", new Annotation("command", "getSelectedText"));

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getSelectedText(int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("flags", flags);
                });
                assertEquals(expectedFlags, flags);
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetSelectedText(expectedFlags);
            final CharSequence result =
                    expectCommand(stream, command, TIMEOUT).getReturnCharSequenceValue();
            assertEqualsForTestCharSequence(expectedResult, result);
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedFlags, args.get("flags"));
            });
        });
    }

    /**
     * Test {@link InputConnection#getSelectedText(int)} fails after a system-defined time-out even
     * if the target app does not respond.
     */
    @Test
    public void testGetSelectedTextFailWithTimeout() throws Exception {
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final String unexpectedResult = "4567";
        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getSelectedText(int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("flags", flags);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command =
                    session.callGetSelectedText(InputConnection.GET_TEXT_WITH_STYLES);
            blocker.expectMethodCalled("IC#getSelectedText() must be called back", TIMEOUT);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertTrue("When timeout happens, IC#getSelectedText() returns null",
                    result.isNullReturnValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedFlags, args.get("flags"));
            });
        }, blocker);
    }

    /**
     * Test {@link InputConnection#getSelectedText(int)} fail-fasts once unbindInput() is issued.
     */
    @Test
    public void testGetSelectedTextFailFastAfterUnbindInput() throws Exception {
        final String unexpectedResult = "4567";

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getSelectedText(int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#getSelectedText() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream, session.callGetSelectedText(
                    InputConnection.GET_TEXT_WITH_STYLES), TIMEOUT);
            assertTrue("Once unbindInput() happened, IC#getSelectedText() returns null",
                    result.isNullReturnValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);
            methodCallVerifier.assertNotCalled(
                    "Once unbindInput() happened, IC#getSelectedText() fails fast.");
        });
    }

    /**
     * Verify that {@link InputConnection#getSelectedText(int)} returns {@code null} when the target
     * app does not implement it.  This can happen if the app was built before
     * {@link android.os.Build.VERSION_CODES#GINGERBREAD}.
     */
    @Test
    public void testGetSelectedTextFailWithMethodMissing() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetSelectedText(0);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertTrue("Currently getSelectedText() returns null when the target app does not"
                    + " implement it.", result.isNullReturnValue());
        });
    }

    @Test
    @ApiTest(apis = {"android.view.inputmethod.InputConnection#requestTextBoundsInfo"})
    public void testRequestTextBoundsInfo() throws Exception {
        final var methodCallVerifier = new MethodCallVerifier();
        final var tbiResult = new TextBoundsInfoResult(TextBoundsInfoResult.CODE_FAILED, null);

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public void requestTextBoundsInfo(RectF rectF, Executor executor,
                    Consumer<TextBoundsInfoResult> consumer) {
                mErrorCollector.checkSucceeds(() -> {
                    methodCallVerifier.onMethodCalled(args -> {
                        args.putParcelable("rectF", rectF);
                    });

                    var called = new boolean[1];
                    executor.execute(() -> {
                        called[0] = true;
                        consumer.accept(tbiResult);
                    });
                    assertTrue("editor-side executor must be Runnable::run", called[0]);
                    return null;
                });
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final RectF rectF = new RectF(1f, 2f, 3f, 4f);
            final ImeCommand command = session.callRequestTextBoundsInfo(rectF);
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(rectF, args.getParcelable("rectF", RectF.class));
            }, TIMEOUT);
            expectCommand(stream, command, TIMEOUT);
            var event = expectEvent(stream, onRequestTextBoundsInfoResultMatcher(command.getId()),
                    TIMEOUT);
            var actualResultCode = event.getArguments().getInt("resultCode");
            var actualBoundsInfo = event.getArguments().getParcelable("boundsInfo",
                    TextBoundsInfo.class);

            assertEquals(TextBoundsInfoResult.CODE_FAILED, actualResultCode);
            assertNull(actualBoundsInfo);
        });
    }

    @Test
    public void testRequestTextBoundsInfo_unimplemented() throws Exception {
        testMinimallyImplementedInputConnection((session, stream) -> {
            final RectF rectF = new RectF(1f, 2f, 3f, 4f);
            final ImeCommand command = session.callRequestTextBoundsInfo(rectF);
            expectCommand(stream, command, TIMEOUT);
            var event = expectEvent(stream, onRequestTextBoundsInfoResultMatcher(command.getId()),
                    TIMEOUT);
            var actualResultCode = event.getArguments().getInt("resultCode");
            var actualBoundsInfo = event.getArguments().getParcelable("boundsInfo",
                    TextBoundsInfo.class);

            assertEquals(TextBoundsInfoResult.CODE_UNSUPPORTED, actualResultCode);
            assertNull(actualBoundsInfo);
        });
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} works as expected.
     */
    @Test
    public void testGetSurroundingText() throws Exception {
        final int expectedBeforeLength = 3;
        final int expectedAfterLength = 4;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final CharSequence expectedText =
                createTestCharSequence("012345", new Annotation("command", "getSurroundingText"));
        final SurroundingText expectedResult = new SurroundingText(expectedText, 1, 2, 0);

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                    int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                    args.putInt("flags", flags);
                });
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetSurroundingText(expectedBeforeLength,
                    expectedAfterLength, expectedFlags);
            final SurroundingText result =
                    expectCommand(stream, command, TIMEOUT).getReturnParcelableValue();
            assertEqualsForTestCharSequence(expectedResult.getText(), result.getText());
            assertEquals(expectedResult.getSelectionStart(), result.getSelectionStart());
            assertEquals(expectedResult.getSelectionEnd(), result.getSelectionEnd());
            assertEquals(expectedResult.getOffset(), result.getOffset());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedBeforeLength, args.get("beforeLength"));
                assertEquals(expectedAfterLength, args.get("afterLength"));
                assertEquals(expectedFlags, args.get("flags"));
            });
        });
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} fails when a nagative
     * {@code afterLength} is passed.  See Bug 169114026 for background.
     */
    @Test
    public void testGetSurroundingTextFailWithNegativeAfterLength() throws Exception {
        final SurroundingText unexpectedResult = new SurroundingText("012345", 1, 2, 0);

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                    int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetSurroundingText(1, -1, 0);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertTrue("IC#getSurroundingText() returns null for a negative afterLength.",
                    result.isNullReturnValue());
            methodCallVerifier.expectNotCalled(
                    "IC#getSurroundingText() will not be triggered with a negative afterLength.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} fails when a negative
     * {@code beforeLength} is passed.  See Bug 169114026 for background.
     */
    @Test
    public void testGetSurroundingTextFailWithNegativeBeforeLength() throws Exception {
        final SurroundingText unexpectedResult = new SurroundingText("012345", 1, 2, 0);

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                    int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetSurroundingText(-1, 1, 0);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertTrue("IC#getSurroundingText() returns null for a negative beforeLength.",
                    result.isNullReturnValue());
            methodCallVerifier.expectNotCalled(
                    "IC#getSurroundingText() will not be triggered with a negative beforeLength.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} fails after a system-defined
     * time-out even if the target app does not respond.
     */
    @Test
    public void testGetSurroundingTextFailWithTimeout() throws Exception {
        final int expectedBeforeLength = 3;
        final int expectedAfterLength = 4;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final SurroundingText unexpectedResult = new SurroundingText("012345", 1, 2, 0);

        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                    int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                    args.putInt("flags", flags);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetSurroundingText(expectedBeforeLength,
                    expectedAfterLength, expectedFlags);
            blocker.expectMethodCalled("IC#getSurroundingText() must be called back", TIMEOUT);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertTrue("When timeout happens, IC#getSurroundingText() returns null",
                    result.isNullReturnValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedBeforeLength, args.get("beforeLength"));
                assertEquals(expectedAfterLength, args.get("afterLength"));
                assertEquals(expectedFlags, args.get("flags"));
            });
        }, blocker);
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} fail-fasts once unbindInput()
     * is issued.
     */
    @Test
    public void testGetSurroundingTextFailFastAfterUnbindInput() throws Exception {
        final int beforeLength = 3;
        final int afterLength = 4;
        final int flags = InputConnection.GET_TEXT_WITH_STYLES;
        final SurroundingText unexpectedResult = new SurroundingText("012345", 1, 2, 0);

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                    int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#getTextBeforeCursor() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream, session.callGetSurroundingText(
                    beforeLength, afterLength, flags), TIMEOUT);
            assertTrue("Once unbindInput() happened, IC#getSurroundingText() returns null",
                    result.isNullReturnValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);
            methodCallVerifier.assertNotCalled(
                    "Once unbindInput() happened, IC#getSurroundingText() fails fast.");
        });
    }

    /**
     * Verify that the default implementation of
     * {@link InputConnection#getSurroundingText(int, int, int)} returns {@code null} without any
     * crash even when the target app does not override it .
     */
    @Test
    public void testGetSurroundingTextDefaultMethod() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetSurroundingText(1, 2, 0);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertTrue("Default IC#getSurroundingText() returns null.",
                    result.isNullReturnValue());
        });
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} works as expected for
     * {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testGetSurroundingTextForA11y() throws Exception {
        final int expectedBeforeLength = 3;
        final int expectedAfterLength = 4;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final CharSequence expectedText =
                createTestCharSequence("012345", new Annotation("command", "getSurroundingText"));
        final SurroundingText expectedResult = new SurroundingText(expectedText, 1, 2, 0);

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                    int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                    args.putInt("flags", flags);
                });
                return expectedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callGetSurroundingText(expectedBeforeLength,
                    expectedAfterLength, expectedFlags);
            final var result = expectA11yImeCommand(stream, command, TIMEOUT)
                    .<SurroundingText>getReturnParcelableValue();
            assertEqualsForTestCharSequence(expectedResult.getText(), result.getText());
            assertEquals(expectedResult.getSelectionStart(), result.getSelectionStart());
            assertEquals(expectedResult.getSelectionEnd(), result.getSelectionEnd());
            assertEquals(expectedResult.getOffset(), result.getOffset());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedBeforeLength, args.get("beforeLength"));
                assertEquals(expectedAfterLength, args.get("afterLength"));
                assertEquals(expectedFlags, args.get("flags"));
            });
        });
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} fails when a negative
     * {@code afterLength} is passed for {@link android.accessibilityservice.InputMethod}.
     * See Bug 169114026 for background.
     */
    @Test
    public void testGetSurroundingTextFailWithNegativeAfterLengthForA11y() throws Exception {
        final SurroundingText unexpectedResult = new SurroundingText("012345", 1, 2, 0);

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                    int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callGetSurroundingText(1, -1, 0);
            final var result = expectA11yImeCommand(stream, command, TIMEOUT);
            assertTrue("IC#getSurroundingText() returns null for a negative afterLength.",
                    result.isNullReturnValue());
            methodCallVerifier.expectNotCalled(
                    "IC#getSurroundingText() will not be triggered with a negative afterLength.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} fails when a negative
     * {@code beforeLength} is passed for {@link android.accessibilityservice.InputMethod}.
     * See Bug 169114026 for background.
     */
    @Test
    public void testGetSurroundingTextFailWithNegativeBeforeLengthForA11y() throws Exception {
        final SurroundingText unexpectedResult = new SurroundingText("012345", 1, 2, 0);

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                    int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callGetSurroundingText(-1, 1, 0);
            final var result = expectA11yImeCommand(stream, command, TIMEOUT);
            assertTrue("IC#getSurroundingText() returns null for a negative beforeLength.",
                    result.isNullReturnValue());
            methodCallVerifier.expectNotCalled(
                    "IC#getSurroundingText() will not be triggered with a negative beforeLength.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#getSurroundingText(int, int, int)} fails for
     * {@link android.accessibilityservice.InputMethod} after a system-defined time-out even if the
     * target app does not respond.
     */
    @Test
    public void testGetSurroundingTextFailWithTimeoutForA11y() throws Exception {
        final int expectedBeforeLength = 3;
        final int expectedAfterLength = 4;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final SurroundingText unexpectedResult = new SurroundingText("012345", 1, 2, 0);

        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public SurroundingText getSurroundingText(int beforeLength, int afterLength,
                    int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                    args.putInt("flags", flags);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callGetSurroundingText(expectedBeforeLength,
                    expectedAfterLength, expectedFlags);
            blocker.expectMethodCalled("IC#getSurroundingText() must be called back", TIMEOUT);
            final var result = expectA11yImeCommand(stream, command, TIMEOUT);
            assertTrue("When timeout happens, IC#getSurroundingText() returns null",
                    result.isNullReturnValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedBeforeLength, args.get("beforeLength"));
                assertEquals(expectedAfterLength, args.get("afterLength"));
                assertEquals(expectedFlags, args.get("flags"));
            });
        }, blocker);
    }

    /**
     * Verify that the default implementation of
     * {@link InputConnection#getSurroundingText(int, int, int)} returns {@code null} without any
     * crash even when the target app does not override it for
     * {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testGetSurroundingTextDefaultMethodForA11y() throws Exception {
        testMinimallyImplementedInputConnectionForA11y((session, stream) -> {
            final var command = session.callGetSurroundingText(1, 2, 0);
            final var result = expectA11yImeCommand(stream, command, TIMEOUT);
            assertTrue("Default IC#getSurroundingText() returns null.",
                    result.isNullReturnValue());
        });
    }

    /**
     * Test
     * {@link InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}
     * works as expected for {@link SelectGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.SelectGesture.Builder#setGranularity",
            "android.view.inputmethod.SelectGesture.Builder#setSelectionArea",
            "android.view.inputmethod.SelectGesture.Builder#setGranularity",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingSelectGesture() throws Exception {
        SelectGesture.Builder builder = new SelectGesture.Builder();
        testPerformHandwritingGesture(
                builder.setGranularity(HandwritingGesture.GRANULARITY_WORD)
                .setSelectionArea(new RectF(1, 2, 3, 4))
                        .setFallbackText("").build(),
                HANDWRITING_GESTURE_RESULT_SUCCESS);
    }

    /**
     * Test
     * {@link InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}
     * works as expected for {@link SelectRangeGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.SelectRangeGesture.Builder#setGranularity",
            "android.view.inputmethod.SelectRangeGesture.Builder#setSelectionArea",
            "android.view.inputmethod.SelectRangeGesture.Builder#setGranularity",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingSelectRangeGesture() throws Exception {
        SelectRangeGesture.Builder builder = new SelectRangeGesture.Builder();
        testPerformHandwritingGesture(
                builder.setGranularity(HandwritingGesture.GRANULARITY_WORD)
                        .setSelectionStartArea(new RectF(1, 2, 3, 4))
                        .setSelectionEndArea(new RectF(5, 6, 7, 8))
                        .setFallbackText("").build(),
                HANDWRITING_GESTURE_RESULT_SUCCESS);
    }

    /**
     * Test
     * {@link InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}
     * works as expected for {@link SelectGesture} by returning
     * {@link InputConnection#HANDWRITING_GESTURE_RESULT_FAILED}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.SelectGesture.Builder#setGranularity",
            "android.view.inputmethod.SelectGesture.Builder#setSelectionArea",
            "android.view.inputmethod.SelectGesture.Builder#setFallbackText",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingSelectGesture_failed() throws Exception {
        SelectGesture.Builder builder = new SelectGesture.Builder();
        testPerformHandwritingGesture(
                builder.setGranularity(HandwritingGesture.GRANULARITY_WORD)
                        .setSelectionArea(new RectF(1, 2, 3, 4))
                        .setFallbackText("").build(),
                InputConnection.HANDWRITING_GESTURE_RESULT_FAILED);
    }

    /**
     * Test
     * InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}
     * works as expected  for {@link InsertGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.InsertGesture.Builder#setInsertionPoint",
            "android.view.inputmethod.InsertGesture.Builder#setFallbackText",
            "android.view.inputmethod.InsertGesture.Builder#setTextToInsert",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingInsertGesture() throws Exception {
        InsertGesture.Builder builder = new InsertGesture.Builder();
        testPerformHandwritingGesture(builder.setTextToInsert("text")
                        .setInsertionPoint(new PointF(1, 1)).setFallbackText("").build(),
                HANDWRITING_GESTURE_RESULT_SUCCESS);
    }

    /**
     * Test
     * InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}
     * works as expected  for {@link InsertGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.InsertGesture.Builder#setInsertionPoint",
            "android.view.inputmethod.InsertGesture.Builder#setFallbackText",
            "android.view.inputmethod.InsertGesture.Builder#setTextToInsert",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingInsertGesture_emptyText() throws Exception {
        InsertGesture.Builder builder = new InsertGesture.Builder();
        testPerformHandwritingGesture(builder.setTextToInsert("")
                        .setInsertionPoint(new PointF(1, 1)).setFallbackText("").build(),
                HANDWRITING_GESTURE_RESULT_SUCCESS);
    }

    /**
     * Test
     * InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}
     * works as expected  for {@link InsertGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.InsertModeGesture.Builder#setInsertionPoint",
            "android.view.inputmethod.InsertGesture.Builder#setFallbackText",
            "android.view.inputmethod.InsertGesture.Builder#setCancellationSignal",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingInsertModeGesture() throws Exception {
        InsertModeGesture.Builder builder = new InsertModeGesture.Builder();
        testPerformHandwritingGesture(builder
                        .setCancellationSignal(new CancellationSignal())
                        .setInsertionPoint(new PointF(1, 1)).setFallbackText("").build(),
                HANDWRITING_GESTURE_RESULT_SUCCESS);
    }

    @Test
    @ApiTest(apis = {"android.view.inputmethod.InsertModeGesture.Builder#setInsertionPoint",
            "android.view.inputmethod.InsertGesture.Builder#setFallbackText",
            "android.view.inputmethod.InsertGesture.Builder#setCancellationSignal",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingInsertModeGesture_ongoingGestureCancellation()
            throws Exception {
        InsertModeGesture.Builder builder = new InsertModeGesture.Builder();
        testInsertModeGestureOngoingCancellation(
                builder.setCancellationSignal(new CancellationSignal())
                        .setInsertionPoint(new PointF(1, 1))
                        .setFallbackText("").build());
    }

    /**
     * Test
     * InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}}
     * works as expected for {@link DeleteGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.DeleteGesture.Builder#setGranularity",
            "android.view.inputmethod.DeleteGesture.Builder#setSelectionArea",
            "android.view.inputmethod.DeleteGesture.Builder#setFallbackText",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingDeleteGesture() throws Exception {
        DeleteGesture.Builder builder = new DeleteGesture.Builder();
        testPerformHandwritingGesture(
                builder.setGranularity(HandwritingGesture.GRANULARITY_WORD)
                        .setDeletionArea(new RectF(1, 2, 3, 4))
                        .setFallbackText("").build(),
                HANDWRITING_GESTURE_RESULT_SUCCESS);
    }

    /**
     * Test
     * InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}}
     * works as expected for {@link DeleteRangeGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.DeleteRangeGesture.Builder#setGranularity",
            "android.view.inputmethod.DeleteRangeGesture.Builder#setSelectionArea",
            "android.view.inputmethod.DeleteRangeGesture.Builder#setFallbackText",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingDeleteRangeGesture() throws Exception {
        DeleteRangeGesture.Builder builder = new DeleteRangeGesture.Builder();
        testPerformHandwritingGesture(
                builder.setGranularity(HandwritingGesture.GRANULARITY_WORD)
                        .setDeletionStartArea(new RectF(1, 2, 3, 4))
                        .setDeletionEndArea(new RectF(5, 6, 7, 8))
                        .setFallbackText("").build(),
                HANDWRITING_GESTURE_RESULT_SUCCESS);
    }

    /**
     * Test InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}}
     * works as expected for {@link RemoveSpaceGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.RemoveSpaceGesture.Builder#setPoints",
            "android.view.inputmethod.RemoveSpaceGesture.Builder#setFallbackText",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingRemoveSpaceGesture() throws Exception {
        testPerformHandwritingGesture(
                new RemoveSpaceGesture.Builder()
                        .setPoints(new PointF(1f, 2f), new PointF(3f, 4f))
                        .setFallbackText("")
                        .build(),
                HANDWRITING_GESTURE_RESULT_SUCCESS);
    }

    /**
     * Test InputConnection#performHandwritingGesture(HandwritingGesture, Executor, IntConsumer)}}
     * works as expected for {@link JoinOrSplitGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.JoinOrSplitGesture.Builder#setJoinOrSplitPoint",
            "android.view.inputmethod.JoinOrSplitGesture.Builder#setFallbackText",
            "android.view.inputmethod.InputConnection#performHandwritingGesture"})
    public void testPerformHandwritingJoinOrSplitGesture() throws Exception {
        testPerformHandwritingGesture(
                new JoinOrSplitGesture.Builder()
                        .setJoinOrSplitPoint(new PointF(1f, 2f))
                        .setFallbackText("")
                        .build(),
                HANDWRITING_GESTURE_RESULT_SUCCESS);
    }

    private <T extends HandwritingGesture> void testPerformHandwritingGesture(
            T gesture, int returnResult) throws Exception {
        final int expectedResult = returnResult;
        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public void performHandwritingGesture(
                    HandwritingGesture gesture, Executor executor, IntConsumer consumer) {
                assertNotNull(executor);
                assertNotNull(consumer);
                if (returnResult > InputConnection.HANDWRITING_GESTURE_RESULT_UNKNOWN) {
                    consumer.accept(returnResult);
                    // Intentionally try to send second time. This update should be ignored
                    // and never sent back to IME.
                    consumer.accept(InputConnection.HANDWRITING_GESTURE_RESULT_UNSUPPORTED);
                }
                methodCallVerifier.onMethodCalled(args -> {
                    args.putByteArray("gesture", gesture.toByteArray());
                });
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            ImeCommand command = session.callPerformHandwritingGesture(
                    gesture, false /* useDelayedCancellation */);
            expectCommand(stream, command, TIMEOUT);

            long requestId = command.getId();
            ImeEvent callbackEvent = expectEvent(
                    stream, onPerformHandwritingGestureResultMatcher(requestId), TIMEOUT);
            assertEquals(expectedResult, callbackEvent.getArguments().getInt("result"));

            methodCallVerifier.assertCalledOnce(args -> {
                byte[] bytes = args.getByteArray("gesture");
                HandwritingGesture gesture1 = HandwritingGesture.fromByteArray(bytes);
                assertEquals(gesture, gesture1);
            });

            // Verify that the second callback was filtered out.
            notExpectEvent(stream, onPerformHandwritingGestureResultMatcher(requestId),
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    private void testInsertModeGestureOngoingCancellation(
            InsertModeGesture gesture1) throws Exception {
        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();
        final CountDownLatch latch = new CountDownLatch(1);
        final int resultCode = HANDWRITING_GESTURE_RESULT_SUCCESS;
        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public void performHandwritingGesture(
                    HandwritingGesture gesture, Executor executor, IntConsumer consumer) {
                CancellationSignal cs = ((InsertModeGesture) gesture).getCancellationSignal();
                assertNotNull(cs);

                methodCallVerifier.onMethodCalled(args -> {});
                cs.setOnCancelListener(() -> latch.countDown());
                consumer.accept(resultCode);
            }
        }
        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            ImeCommand command = session.callPerformHandwritingGesture(
                    gesture1, true /* useDelayedCancellation */);

            expectCommand(stream, command, TIMEOUT);
            methodCallVerifier.assertCalledOnce(args -> {});

            long requestId = command.getId();
            ImeEvent callbackEvent = expectEvent(
                    stream, onPerformHandwritingGestureResultMatcher(requestId), TIMEOUT);
            assertEquals(resultCode, callbackEvent.getArguments().getInt("result"));
        });

        latch.await(3, TimeUnit.SECONDS);
        assertEquals(0, latch.getCount());

    }

    private static Predicate<ImeEvent> onPerformHandwritingGestureResultMatcher(
            long requestId) {
        return withDescription("onPerformHandwritingGestureResult(" + requestId + ")", event -> {
            if (!TextUtils.equals("onPerformHandwritingGestureResult", event.getEventName())) {
                return false;
            }
            return event.getArguments().getLong("requestId") == requestId;
        });
    }

    private static Predicate<ImeEvent> onRequestTextBoundsInfoResultMatcher(
            long requestId) {
        return withDescription("onRequestTextBoundsInfoResult(" + requestId + ")", event -> {
            if (!TextUtils.equals("onRequestTextBoundsInfoResult", event.getEventName())) {
                return false;
            }
            return event.getArguments().getLong("requestId") == requestId;
        });
    }

    /**
     * Test
     * {@link InputConnection#previewHandwritingGesture(PreviewableHandwritingGesture,
     * CancellationSignal)} works as expected for {@link SelectGesture}.
     */
    @Test
    @ApiTest(apis = {"android.view.inputmethod.SelectGesture.Builder#setGranularity",
            "android.view.inputmethod.SelectGesture.Builder#setSelectionArea",
            "android.view.inputmethod.SelectGesture.Builder#setGranularity",
            "android.view.inputmethod.InputConnection#previewHandwritingGesture"})
    public void testPreviewHandwritingSelectGesture() throws Exception {
        SelectGesture.Builder builder = new SelectGesture.Builder();
        testPreviewHandwritingGesture(
                builder.setGranularity(HandwritingGesture.GRANULARITY_WORD)
                        .setSelectionArea(new RectF(1, 2, 3, 4))
                        .build());
    }

    @Test
    @ApiTest(apis = {"android.view.inputmethod.SelectGesture.Builder#setGranularity",
            "android.view.inputmethod.SelectGesture.Builder#setSelectionArea",
            "android.view.inputmethod.SelectGesture.Builder#setGranularity",
            "android.view.inputmethod.InputConnection#previewHandwritingGesture"})
    public void testPreviewHandwritingSelectGesture_ongoingGestureCancellation()
            throws Exception {
        SelectGesture.Builder builder = new SelectGesture.Builder();
        testPreviewHandwritingGestureOngoingCancellation(
                builder.setGranularity(HandwritingGesture.GRANULARITY_WORD)
                        .setSelectionArea(new RectF(1, 2, 3, 4))
                        .build());
    }

    private <T extends PreviewableHandwritingGesture> void testPreviewHandwritingGesture(T gesture)
            throws Exception {
        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean previewHandwritingGesture(
                    PreviewableHandwritingGesture gesture, CancellationSignal cancellationSignal) {
                assertNotNull(gesture);

                methodCallVerifier.onMethodCalled(args ->
                        args.putByteArray("gesture", gesture.toByteArray()));

                return true;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            ImeCommand command =
                    session.callPreviewHandwritingGesture(
                            gesture, false /* useDelayedCancellation */);

            expectCommand(stream, command, TIMEOUT);
            methodCallVerifier.assertCalledOnce(
                    args -> assertEquals(gesture,
                            HandwritingGesture.fromByteArray(args.getByteArray("gesture"))));
        });
    }

    private <T extends PreviewableHandwritingGesture> void
            testPreviewHandwritingGestureOngoingCancellation(T gesture) throws Exception {
        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();
        final CountDownLatch latch = new CountDownLatch(1);

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean previewHandwritingGesture(
                    PreviewableHandwritingGesture gesture, CancellationSignal cancellationSignal) {
                assertNotNull(gesture);
                assertNotNull(cancellationSignal);
                methodCallVerifier.onMethodCalled(args ->{});
                cancellationSignal.setOnCancelListener(() -> latch.countDown());

                return true;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            ImeCommand command =
                    session.callPreviewHandwritingGesture(
                            gesture, true /* useDelayedCancellation */);

            expectCommand(stream, command, TIMEOUT);
            methodCallVerifier.assertCalledOnce(args -> {});
        });

        latch.await(3, TimeUnit.SECONDS);
        assertEquals(0, latch.getCount());
    }

    /**
     * Test {@link InputConnection#getCursorCapsMode(int)} works as expected.
     */
    @Test
    public void testGetCursorCapsMode() throws Exception {
        final int expectedReqMode = TextUtils.CAP_MODE_SENTENCES | TextUtils.CAP_MODE_CHARACTERS
                | TextUtils.CAP_MODE_WORDS;
        final int expectedResult = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public int getCursorCapsMode(int reqModes) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("reqModes", reqModes);
                });
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetCursorCapsMode(expectedReqMode);
            final int result = expectCommand(stream, command, TIMEOUT).getReturnIntegerValue();
            assertEquals(expectedResult, result);
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedReqMode, args.getInt("reqModes"));
            });
        });
    }

    /**
     * Test {@link InputConnection#getCursorCapsMode(int)} fails after a system-defined time-out
     * even if the target app does not respond.
     */
    @Test
    public void testGetCursorCapsModeFailWithTimeout() throws Exception {
        final int expectedReqMode = TextUtils.CAP_MODE_SENTENCES | TextUtils.CAP_MODE_CHARACTERS
                | TextUtils.CAP_MODE_WORDS;
        final int unexpectedResult = EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;
        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public int getCursorCapsMode(int reqModes) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("reqModes", reqModes);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetCursorCapsMode(expectedReqMode);
            blocker.expectMethodCalled("IC#getCursorCapsMode() must be called back", TIMEOUT);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertEquals("When timeout happens, IC#getCursorCapsMode() returns 0",
                    0, result.getReturnIntegerValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedReqMode, args.getInt("reqModes"));
            });
        }, blocker);
    }

    /**
     * Test {@link InputConnection#getCursorCapsMode(int)} fail-fasts once unbindInput() is issued.
     */
    @Test
    public void testGetCursorCapsModeFailFastAfterUnbindInput() throws Exception {
        final int unexpectedResult = EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public int getCursorCapsMode(int reqModes) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("reqModes", reqModes);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#getCursorCapsMode() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream,
                    session.callGetCursorCapsMode(TextUtils.CAP_MODE_WORDS), TIMEOUT);
            assertEquals("Once unbindInput() happened, IC#getCursorCapsMode() returns 0",
                    0, result.getReturnIntegerValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);
            methodCallVerifier.assertNotCalled(
                    "Once unbindInput() happened, IC#getCursorCapsMode() fails fast.");
        });
    }

    /**
     * Test {@link InputConnection#getCursorCapsMode(int)} works as expected for
     * {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testGetCursorCapsModeForA11y() throws Exception {
        final int expectedReqMode = TextUtils.CAP_MODE_SENTENCES | TextUtils.CAP_MODE_CHARACTERS
                | TextUtils.CAP_MODE_WORDS;
        final int expectedResult = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public int getCursorCapsMode(int reqModes) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("reqModes", reqModes);
                });
                return expectedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callGetCursorCapsMode(expectedReqMode);
            final int result = expectA11yImeCommand(stream, command, TIMEOUT)
                    .getReturnIntegerValue();
            assertEquals(expectedResult, result);
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedReqMode, args.getInt("reqModes"));
            });
        });
    }

    /**
     * Test {@link InputConnection#getCursorCapsMode(int)} fails for
     * {@link android.accessibilityservice.InputMethod} after a system-defined time-out even if the
     * target app does not respond.
     */
    @Test
    public void testGetCursorCapsModeFailWithTimeoutForA11y() throws Exception {
        final int expectedReqMode = TextUtils.CAP_MODE_SENTENCES | TextUtils.CAP_MODE_CHARACTERS
                | TextUtils.CAP_MODE_WORDS;
        final int unexpectedResult = EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;
        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public int getCursorCapsMode(int reqModes) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("reqModes", reqModes);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callGetCursorCapsMode(expectedReqMode);
            blocker.expectMethodCalled("IC#getCursorCapsMode() must be called back", TIMEOUT);
            final var result = expectA11yImeCommand(stream, command, LONG_TIMEOUT);
            assertEquals("When timeout happens, IC#getCursorCapsMode() returns 0",
                    0, result.getReturnIntegerValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedReqMode, args.getInt("reqModes"));
            });
        }, blocker);
    }

    /**
     * Test {@link InputConnection#getExtractedText(ExtractedTextRequest, int)} works as expected.
     */
    @Test
    public void testGetExtractedText() throws Exception {
        final ExtractedTextRequest expectedRequest = ExtractedTextRequestTest.createForTest();
        final int expectedFlags = InputConnection.GET_EXTRACTED_TEXT_MONITOR;
        final ExtractedText expectedResult = ExtractedTextTest.createForTest();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putParcelable("request", request);
                    args.putInt("flags", flags);
                });
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetExtractedText(expectedRequest, expectedFlags);
            final ExtractedText result =
                    expectCommand(stream, command, TIMEOUT).getReturnParcelableValue();
            ExtractedTextTest.assertTestInstance(result);
            methodCallVerifier.assertCalledOnce(args -> {
                ExtractedTextRequestTest.assertTestInstance(args.getParcelable("request"));
                assertEquals(expectedFlags, args.getInt("flags"));
            });
        });
    }

    /**
     * Test {@link InputConnection#getExtractedText(ExtractedTextRequest, int)} fails after a
     * system-defined time-out even if the target app does not respond.
     */
    @Test
    public void testGetExtractedTextFailWithTimeout() throws Exception {
        final ExtractedTextRequest expectedRequest = ExtractedTextRequestTest.createForTest();
        final int expectedFlags = InputConnection.GET_EXTRACTED_TEXT_MONITOR;
        final ExtractedText unexpectedResult = ExtractedTextTest.createForTest();
        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putParcelable("request", request);
                    args.putInt("flags", flags);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetExtractedText(expectedRequest, expectedFlags);
            blocker.expectMethodCalled("IC#getExtractedText() must be called back", TIMEOUT);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertTrue("When timeout happens, IC#getExtractedText() returns null",
                    result.isNullReturnValue());
            methodCallVerifier.assertCalledOnce(args -> {
                ExtractedTextRequestTest.assertTestInstance(args.getParcelable("request"));
                assertEquals(expectedFlags, args.getInt("flags"));
            });
        }, blocker);
    }

    /**
     * Test {@link InputConnection#getExtractedText(ExtractedTextRequest, int)} fail-fasts once
     * unbindInput() is issued.
     */
    @Test
    public void testGetExtractedTextFailFastAfterUnbindInput() throws Exception {
        final ExtractedText unexpectedResult = ExtractedTextTest.createForTest();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putParcelable("request", request);
                    args.putInt("flags", flags);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#getExtractedText() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream, session.callGetExtractedText(
                    ExtractedTextRequestTest.createForTest(),
                    InputConnection.GET_EXTRACTED_TEXT_MONITOR), TIMEOUT);
            assertTrue("Once unbindInput() happened, IC#getExtractedText() returns null",
                    result.isNullReturnValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);
            methodCallVerifier.assertNotCalled(
                    "Once unbindInput() happened, IC#getExtractedText() fails fast.");
        });
    }

    /**
     * Test {@link InputConnection#requestCursorUpdates(int)} works as expected.
     */
    @Test
    public void testRequestCursorUpdates() throws Exception {
        final int expectedFlags = InputConnection.CURSOR_UPDATE_IMMEDIATE;
        final boolean expectedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean requestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("cursorUpdateMode", cursorUpdateMode);
                    args.putInt("cursorUpdateFilter", cursorUpdateFilter);
                });
                assertEquals(expectedFlags, cursorUpdateMode);
                assertEquals(0, cursorUpdateFilter);
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callRequestCursorUpdates(expectedFlags);
            assertTrue(expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedFlags, args.getInt("cursorUpdateMode"));
            });
        });
    }

    /**
     * Test {@link InputConnection#requestCursorUpdates(int, int)} works as expected.
     */
    @Test
    public void testRequestCursorUpdatesWithFilter() throws Exception {
        final int expectedUpdateFlags = InputConnection.CURSOR_UPDATE_IMMEDIATE;
        final int expectedFilterFlags = InputConnection.CURSOR_UPDATE_FILTER_EDITOR_BOUNDS;
        final boolean expectedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean requestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("cursorUpdateMode", cursorUpdateMode);
                    args.putInt("cursorUpdateFilter", cursorUpdateFilter);
                });
                assertEquals(expectedUpdateFlags, cursorUpdateMode);
                assertEquals(expectedFilterFlags, cursorUpdateFilter);
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command =
                    session.callRequestCursorUpdates(expectedUpdateFlags, expectedFilterFlags);
            assertTrue(expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedUpdateFlags, args.getInt("cursorUpdateMode"));
                assertEquals(expectedFilterFlags, args.getInt("cursorUpdateFilter"));
            });
        });
    }

    /**
     * Test {@link InputConnection#requestCursorUpdates(int)} fails after a system-defined time-out
     * even if the target app does not respond.
     */
    @Test
    public void testRequestCursorUpdatesFailWithTimeout() throws Exception {
        final int expectedFlags = InputConnection.CURSOR_UPDATE_IMMEDIATE;
        final boolean unexpectedResult = true;
        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean requestCursorUpdates(int cursorUpdateMode, int cursorUpdateFilter) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("cursorUpdateMode", cursorUpdateMode);
                    args.putInt("cursorUpdateFilter", cursorUpdateFilter);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callRequestCursorUpdates(
                    InputConnection.CURSOR_UPDATE_IMMEDIATE);
            blocker.expectMethodCalled("IC#requestCursorUpdates() must be called back", TIMEOUT);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertFalse("When timeout happens, IC#requestCursorUpdates() returns false",
                    result.getReturnBooleanValue());
            methodCallVerifier.assertCalledOnce(args -> {
                assertEquals(expectedFlags, args.getInt("cursorUpdateMode"));
            });
        }, blocker);
    }

    /**
     * Test {@link InputConnection#requestCursorUpdates(int)} fail-fasts once unbindInput() is
     * issued.
     */
    @Test
    public void testRequestCursorUpdatesFailFastAfterUnbindInput() throws Exception {
        final boolean unexpectedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean requestCursorUpdates(int cursorUpdateMode) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("cursorUpdateMode", cursorUpdateMode);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#requestCursorUpdates() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream, session.callRequestCursorUpdates(
                    InputConnection.CURSOR_UPDATE_IMMEDIATE), TIMEOUT);
            assertFalse("Once unbindInput() happened, IC#requestCursorUpdates() returns false",
                    result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);
            methodCallVerifier.assertNotCalled(
                    "Once unbindInput() happened, IC#requestCursorUpdates() fails fast.");
        });
    }

    /**
     * Verify that {@link InputConnection#requestCursorUpdates(int)} fails when the target app does
     * not implement it. This can happen if the app was built before
     * {@link android.os.Build.VERSION_CODES#LOLLIPOP}.
     */
    @Test
    public void testRequestCursorUpdatesFailWithMethodMissing() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callRequestCursorUpdates(
                    InputConnection.CURSOR_UPDATE_IMMEDIATE);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertFalse("IC#requestCursorUpdates() returns false when the target app does not "
                    + " implement it.", result.getReturnBooleanValue());
        });
    }

    /**
     * Test {@link InputConnection#commitContent(InputContentInfo, int, Bundle)} works as expected.
     */
    @Test
    public void testCommitContent() throws Exception {
        final InputContentInfo expectedInputContentInfo = new InputContentInfo(
                Uri.parse("content://com.example/path"),
                new ClipDescription("sample content", new String[]{"image/png"}),
                Uri.parse("https://example.com"));
        final Bundle expectedOpt = new Bundle();
        final String expectedOptKey = "testKey";
        final int expectedOptValue = 42;
        expectedOpt.putInt(expectedOptKey, expectedOptValue);
        final int expectedFlags = InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        final boolean expectedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitContent(InputContentInfo inputContentInfo, int flags,
                    Bundle opts) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putParcelable("inputContentInfo", inputContentInfo);
                    args.putInt("flags", flags);
                    args.putBundle("opts", opts);
                });
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command =
                    session.callCommitContent(expectedInputContentInfo, expectedFlags, expectedOpt);
            assertTrue(expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.assertCalledOnce(args -> {
                final InputContentInfo inputContentInfo = args.getParcelable("inputContentInfo");
                final Bundle opts = args.getBundle("opts");
                assertNotNull(inputContentInfo);
                assertEquals(expectedInputContentInfo.getContentUri(),
                        inputContentInfo.getContentUri());
                assertEquals(expectedFlags, args.getInt("flags"));
                assertNotNull(opts);
                assertEquals(expectedOpt.getInt(expectedOptKey), opts.getInt(expectedOptKey));
            });
        });
    }

    /**
     * Test {@link InputConnection#commitContent(InputContentInfo, int, Bundle)} fails after a
     * system-defined time-out even if the target app does not respond.
     */
    @Test
    public void testCommitContentFailWithTimeout() throws Exception {
        final InputContentInfo expectedInputContentInfo = new InputContentInfo(
                Uri.parse("content://com.example/path"),
                new ClipDescription("sample content", new String[]{"image/png"}),
                Uri.parse("https://example.com"));
        final Bundle expectedOpt = new Bundle();
        final String expectedOptKey = "testKey";
        final int expectedOptValue = 42;
        expectedOpt.putInt(expectedOptKey, expectedOptValue);
        final int expectedFlags = InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        final boolean unexpectedResult = true;
        final BlockingMethodVerifier blocker = new BlockingMethodVerifier();

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitContent(InputContentInfo inputContentInfo, int flags,
                    Bundle opts) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putParcelable("inputContentInfo", inputContentInfo);
                    args.putInt("flags", flags);
                    args.putBundle("opts", opts);
                });
                blocker.onMethodCalled();
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command =
                    session.callCommitContent(expectedInputContentInfo, expectedFlags, expectedOpt);
            blocker.expectMethodCalled("IC#commitContent() must be called back", TIMEOUT);
            final ImeEvent result = expectCommand(stream, command, LONG_TIMEOUT);
            assertFalse("When timeout happens, IC#commitContent() returns false",
                    result.getReturnBooleanValue());
            methodCallVerifier.assertCalledOnce(args -> {
                final InputContentInfo inputContentInfo = args.getParcelable("inputContentInfo");
                final Bundle opts = args.getBundle("opts");
                assertNotNull(inputContentInfo);
                assertEquals(expectedInputContentInfo.getContentUri(),
                        inputContentInfo.getContentUri());
                assertEquals(expectedFlags, args.getInt("flags"));
                assertNotNull(opts);
                assertEquals(expectedOpt.getInt(expectedOptKey), opts.getInt(expectedOptKey));
            });
        }, blocker);
    }

    /**
     * Test {@link InputConnection#commitContent(InputContentInfo, int, Bundle)} fail-fasts once
     * unbindInput() is issued.
     */
    @Test
    public void testCommitContentFailFastAfterUnbindInput() throws Exception {
        final boolean unexpectedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitContent(InputContentInfo inputContentInfo, int flags,
                    Bundle opts) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putParcelable("inputContentInfo", inputContentInfo);
                    args.putInt("flags", flags);
                    args.putBundle("opts", opts);
                });
                return unexpectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#getTextAfterCursor() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream, session.callCommitContent(
                    new InputContentInfo(Uri.parse("content://com.example/path"),
                            new ClipDescription("sample content", new String[]{"image/png"}),
                            Uri.parse("https://example.com")), 0, null), TIMEOUT);
            assertFalse("Once unbindInput() happened, IC#commitContent() returns false",
                    result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);
            methodCallVerifier.assertNotCalled(
                    "Once unbindInput() happened, IC#commitContent() fails fast.");
        });
    }

    /**
     * Verify that {@link InputConnection#commitContent(InputContentInfo, int, Bundle)} fails when
     * the target app does not implement it. This can happen if the app was built before
     * {@link android.os.Build.VERSION_CODES#N_MR1}.
     */
    @Test
    public void testCommitContentFailWithMethodMissing() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callCommitContent(
                    new InputContentInfo(Uri.parse("content://com.example/path"),
                            new ClipDescription("sample content", new String[]{"image/png"}),
                            Uri.parse("https://example.com")), 0, null);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertFalse("Currently IC#commitContent() returns false when the target app does not"
                    + " implement it.", result.getReturnBooleanValue());
        });
    }

    /**
     * Test {@link InputConnection#deleteSurroundingText(int, int)} works as expected.
     */
    @Test
    public void testDeleteSurroundingText() throws Exception {
        final int expectedBeforeLength = 5;
        final int expectedAfterLength = 4;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command =
                    session.callDeleteSurroundingText(expectedBeforeLength, expectedAfterLength);
            assertTrue("deleteSurroundingText() always returns true unless RemoteException is"
                    + " thrown", expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedBeforeLength, args.getInt("beforeLength"));
                assertEquals(expectedAfterLength, args.getInt("afterLength"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#deleteSurroundingText(int, int)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testDeleteSurroundingTextAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#deleteSurroundingText() for the memorized IC should fail fast.
            final ImeCommand command = session.callDeleteSurroundingText(3, 4);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#deleteSurroundingText() still returns true even after"
                    + " unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#deleteSurroundingText() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#deleteSurroundingText(int, int)} works as expected for
     * {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testDeleteSurroundingTextForA11y() throws Exception {
        final int expectedBeforeLength = 5;
        final int expectedAfterLength = 4;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                });
                return returnedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command =
                    session.callDeleteSurroundingText(expectedBeforeLength, expectedAfterLength);
            expectA11yImeCommand(stream, command, TIMEOUT);
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedBeforeLength, args.getInt("beforeLength"));
                assertEquals(expectedAfterLength, args.getInt("afterLength"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#deleteSurroundingTextInCodePoints(int, int)} works as expected.
     */
    @Test
    public void testDeleteSurroundingTextInCodePoints() throws Exception {
        final int expectedBeforeLength = 5;
        final int expectedAfterLength = 4;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callDeleteSurroundingTextInCodePoints(
                    expectedBeforeLength, expectedAfterLength);
            assertTrue("deleteSurroundingText() always returns true unless RemoteException is"
                    + " thrown", expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedBeforeLength, args.getInt("beforeLength"));
                assertEquals(expectedAfterLength, args.getInt("afterLength"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#deleteSurroundingTextInCodePoints(int, int)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testDeleteSurroundingTextInCodePointsAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("beforeLength", beforeLength);
                    args.putInt("afterLength", afterLength);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#deleteSurroundingTextInCodePoints() for the memorized IC should fail fast.
            final ImeCommand command = session.callDeleteSurroundingTextInCodePoints(3, 4);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#deleteSurroundingTextInCodePoints() still returns true even"
                    + " after unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#deleteSurroundingTextInCodePoints() fails"
                    + " fast.", EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Verify that the app does not crash even if it does not implement
     * {@link InputConnection#deleteSurroundingTextInCodePoints(int, int)}, which can happen if the
     * app was built before {@link android.os.Build.VERSION_CODES#N}.
     */
    @Test
    public void testDeleteSurroundingTextInCodePointsFailWithMethodMissing() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callDeleteSurroundingTextInCodePoints(1, 2);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertTrue("IC#deleteSurroundingTextInCodePoints() returns true even when the target"
                    + " app does not implement it.", result.getReturnBooleanValue());
        });
    }

    /**
     * Test {@link InputConnection#commitText(CharSequence, int)} works as expected.
     */
    @Test
    public void testCommitText() throws Exception {
        final Annotation expectedSpan = new Annotation("expectedKey", "expectedValue");
        final CharSequence expectedText = createTestCharSequence("expectedText", expectedSpan);
        final int expectedNewCursorPosition = 123;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putCharSequence("text", text);
                    args.putInt("newCursorPosition", newCursorPosition);
                });

                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command =
                    session.callCommitText(expectedText, expectedNewCursorPosition);
            assertTrue("commitText() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEqualsForTestCharSequence(expectedText, args.getCharSequence("text"));
                assertEquals(expectedNewCursorPosition, args.getInt("newCursorPosition"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#commitText(CharSequence, int)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testCommitTextAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putCharSequence("text", text);
                    args.putInt("newCursorPosition", newCursorPosition);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#getTextAfterCursor() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream,
                    session.callCommitText("text", 1), TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#commitText() still returns true even after unbindInput().",
                    result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#commitText() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#commitText(CharSequence, int, TextAttribute)} works as expected.
     */
    @Test
    public void testCommitTextWithTextAttribute() throws Exception {
        final Annotation expectedSpan = new Annotation("expectedKey", "expectedValue");
        final CharSequence expectedText = createTestCharSequence("expectedText", expectedSpan);
        final int expectedNewCursorPosition = 123;
        final ArrayList<String> expectedSuggestions = new ArrayList<>();
        expectedSuggestions.add("test");
        final TextAttribute expectedTextAttribute = new TextAttribute.Builder()
                .setTextConversionSuggestions(expectedSuggestions).build();
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitText(
                    CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putCharSequence("text", text);
                    args.putInt("newCursorPosition", newCursorPosition);
                    args.putParcelable("textAttribute", textAttribute);
                });

                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callCommitText(
                    expectedText, expectedNewCursorPosition, expectedTextAttribute);
            assertTrue("commitText() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEqualsForTestCharSequence(expectedText, args.getCharSequence("text"));
                assertEquals(expectedNewCursorPosition, args.getInt("newCursorPosition"));
                final TextAttribute textAttribute = args.getParcelable("textAttribute");
                assertThat(textAttribute).isNotNull();
                assertThat(textAttribute.getTextConversionSuggestions())
                        .containsExactlyElementsIn(expectedSuggestions);
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#commitText(CharSequence, int, TextAttribute)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testCommitTextAfterUnbindInputWithTextAttribute() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitText(
                    CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putCharSequence("text", text);
                    args.putInt("newCursorPosition", newCursorPosition);
                    args.putParcelable("textAttribute", textAttribute);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now IC#getTextAfterCursor() for the memorized IC should fail fast.
            final ImeEvent result = expectCommand(stream,
                    session.callCommitText("text", 1,
                            new TextAttribute.Builder().setTextConversionSuggestions(
                                    Collections.singletonList("test")).build()),
                    TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#commitText() still returns true even after unbindInput().",
                    result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#commitText() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#commitText(CharSequence, int, TextAttribute)} works as expected
     * for {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testCommitTextWithTextAttributeForA11y() throws Exception {
        final Annotation expectedSpan = new Annotation("expectedKey", "expectedValue");
        final CharSequence expectedText = createTestCharSequence("expectedText", expectedSpan);
        final int expectedNewCursorPosition = 123;
        final ArrayList<String> expectedSuggestions = new ArrayList<>();
        expectedSuggestions.add("test");
        final TextAttribute expectedTextAttribute = new TextAttribute.Builder()
                .setTextConversionSuggestions(expectedSuggestions).build();
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitText(
                    CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putCharSequence("text", text);
                    args.putInt("newCursorPosition", newCursorPosition);
                    args.putParcelable("textAttribute", textAttribute);
                });

                return returnedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callCommitText(
                    expectedText, expectedNewCursorPosition, expectedTextAttribute);
            expectA11yImeCommand(stream, command, TIMEOUT);
            methodCallVerifier.expectCalledOnce(args -> {
                assertEqualsForTestCharSequence(expectedText, args.getCharSequence("text"));
                assertEquals(expectedNewCursorPosition, args.getInt("newCursorPosition"));
                final var textAttribute = args.getParcelable("textAttribute", TextAttribute.class);
                assertThat(textAttribute).isNotNull();
                assertThat(textAttribute.getTextConversionSuggestions())
                        .containsExactlyElementsIn(expectedSuggestions);
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link android.accessibilityservice.InputMethod.AccessibilityInputConnection#commitText(
     * CharSequence, int, TextAttribute)} finishes any existing composing text.
     */
    @Test
    public void testCommitTextFromA11yFinishesExistingComposition() throws Exception {
        final MethodCallVerifier endBatchEditVerifier = new MethodCallVerifier();
        final CopyOnWriteArrayList<String> callHistory = new CopyOnWriteArrayList<>();

        final class Wrapper extends InputConnectionWrapper {
            private int mBatchEditCount = 0;

            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition,
                    TextAttribute textAttribute) {
                callHistory.add("setComposingText");
                return true;
            }

            @Override
            public boolean beginBatchEdit() {
                callHistory.add("beginBatchEdit");
                ++mBatchEditCount;
                return true;
            }

            @Override
            public boolean finishComposingText() {
                callHistory.add("finishComposingText");
                return true;
            }

            @Override
            public boolean commitText(
                    CharSequence text, int newCursorPosition, TextAttribute textAttribute) {
                callHistory.add("commitText");
                return true;
            }

            @Override
            public boolean endBatchEdit() {
                callHistory.add("endBatchEdit");
                --mBatchEditCount;
                final boolean batchEditStillInProgress = mBatchEditCount > 0;
                if (!batchEditStillInProgress) {
                    endBatchEditVerifier.onMethodCalled(args -> { });
                }
                return batchEditStillInProgress;
            }
        }

        testInputConnection(Wrapper::new, (imeSession, imeStream, a11ySession, a11yStream) -> {
            expectCommand(imeStream, imeSession.callSetComposingText("fromIme", 1, null), TIMEOUT);
            expectA11yImeCommand(a11yStream, a11ySession.callCommitText("fromA11y", 1, null),
                    TIMEOUT);
            endBatchEditVerifier.expectCalledOnce(args -> { }, TIMEOUT);
            assertThat(callHistory).containsExactly(
                    "setComposingText",
                    "beginBatchEdit",
                    "finishComposingText",
                    "commitText",
                    "endBatchEdit").inOrder();
        });
    }

    /**
     * Test {@link InputConnection#setComposingText(CharSequence, int)} works as expected.
     */
    @Test
    public void testSetComposingText() throws Exception {
        final Annotation expectedSpan = new Annotation("expectedKey", "expectedValue");
        final CharSequence expectedText = createTestCharSequence("expectedText", expectedSpan);
        final int expectedNewCursorPosition = 123;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putCharSequence("text", text);
                    args.putInt("newCursorPosition", newCursorPosition);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command =
                    session.callSetComposingText(expectedText, expectedNewCursorPosition);
            assertTrue("setComposingText() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEqualsForTestCharSequence(expectedText, args.getCharSequence("text"));
                assertEquals(expectedNewCursorPosition, args.getInt("newCursorPosition"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setComposingText(CharSequence, int)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testSetComposingTextAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putCharSequence("text", text);
                    args.putInt("newCursorPosition", newCursorPosition);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callSetComposingText("text", 1);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#setComposingText() still returns true even after "
                    + "unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#setComposingText() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setComposingText(CharSequence, int, TextAttribute)}
     * works as expected.
     */
    @Test
    public void testSetComposingTextWithTextAttribute() throws Exception {
        final Annotation expectedSpan = new Annotation("expectedKey", "expectedValue");
        final CharSequence expectedText = createTestCharSequence("expectedText", expectedSpan);
        final int expectedNewCursorPosition = 123;
        final ArrayList<String> expectedSuggestions = new ArrayList<>();
        expectedSuggestions.add("test");
        final TextAttribute expectedTextAttribute = new TextAttribute.Builder()
                .setTextConversionSuggestions(expectedSuggestions).build();
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition,
                    TextAttribute textAttribute) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putCharSequence("text", text);
                    args.putInt("newCursorPosition", newCursorPosition);
                    args.putParcelable("textAttribute", textAttribute);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callSetComposingText(
                    expectedText, expectedNewCursorPosition, expectedTextAttribute);
            assertTrue("testSetComposingTextWithTextAttribute() always returns true unless"
                            + " RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEqualsForTestCharSequence(expectedText, args.getCharSequence("text"));
                assertEquals(expectedNewCursorPosition, args.getInt("newCursorPosition"));
                final TextAttribute textAttribute = args.getParcelable("textAttribute");
                assertThat(textAttribute).isNotNull();
                assertThat(textAttribute.getTextConversionSuggestions())
                        .containsExactlyElementsIn(expectedSuggestions);
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setComposingText(CharSequence, int, TextAttribute)} fails fast
     * once {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testSetComposingTextAfterUnbindInputWithTextAttribute() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition,
                    TextAttribute textAttribute) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putCharSequence("text", text);
                    args.putInt("newCursorPosition", newCursorPosition);
                    args.putParcelable("textAttribute", textAttribute);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callSetComposingText(
                    "text", 1, new TextAttribute.Builder()
                            .setTextConversionSuggestions(Collections.singletonList("test"))
                            .build());
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#setComposingText() still returns true even after "
                    + "unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#setComposingText() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setComposingRegion(int, int)} works as expected.
     */
    @Test
    public void testSetComposingRegion() throws Exception {
        final int expectedStart = 3;
        final int expectedEnd = 17;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setComposingRegion(int start, int end) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("start", start);
                    args.putInt("end", end);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callSetComposingRegion(expectedStart, expectedEnd);
            assertTrue("setComposingRegion() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedStart, args.getInt("start"));
                assertEquals(expectedEnd, args.getInt("end"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setComposingRegion(int, int)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testSetComposingRegionTextAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setComposingRegion(int start, int end) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("start", start);
                    args.putInt("end", end);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callSetComposingRegion(1, 23);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#setComposingRegion() still returns true even after"
                    + " unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#setComposingRegion() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Verify that the app does not crash even if it does not implement
     * {@link InputConnection#setComposingRegion(int, int)}, which can happen if the app was built
     * before {@link android.os.Build.VERSION_CODES#GINGERBREAD}.
     */
    @Test
    public void testSetComposingRegionFailWithMethodMissing() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callSetComposingRegion(1, 23);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertTrue("IC#setComposingRegion() returns true even when the target app does not"
                    + " implement it.", result.getReturnBooleanValue());
        });
    }

    /**
     * Test {@link InputConnection#setComposingRegion} works as expected.
     */
    @Test
    public void testSetComposingRegionWithTextAttribute() throws Exception {
        final int expectedStart = 3;
        final int expectedEnd = 17;
        final ArrayList<String> expectedSuggestions = new ArrayList<>();
        expectedSuggestions.add("test");
        final TextAttribute expectedTextAttribute = new TextAttribute.Builder()
                .setTextConversionSuggestions(expectedSuggestions).build();
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setComposingRegion(
                    int start, int end, TextAttribute textAttribute) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("start", start);
                    args.putInt("end", end);
                    args.putParcelable("textAttribute", textAttribute);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callSetComposingRegion(
                    expectedStart, expectedEnd, expectedTextAttribute);
            assertTrue("setComposingRegion() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedStart, args.getInt("start"));
                assertEquals(expectedEnd, args.getInt("end"));
                final TextAttribute textAttribute = args.getParcelable("textAttribute");
                assertThat(textAttribute).isNotNull();
                assertThat(textAttribute.getTextConversionSuggestions())
                        .containsExactlyElementsIn(expectedSuggestions);
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setComposingRegion(int, int, TextAttribute)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testSetComposingRegionTextAfterUnbindInputWithTextAttribute() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setComposingRegion(int start, int end, TextAttribute textAttribute) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("start", start);
                    args.putInt("end", end);
                    args.putParcelable("textAttribute", textAttribute);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callSetComposingRegion(1, 23,
                    new TextAttribute.Builder().setTextConversionSuggestions(
                            Collections.singletonList("test")).build());
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#setComposingRegion() still returns true even after"
                    + " unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#setComposingRegion() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#finishComposingText()} works as expected.
     */
    @Test
    public void testFinishComposingText() throws Exception {
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean finishComposingText() {
                methodCallVerifier.onMethodCalled(bundle -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callFinishComposingText();
            assertTrue("finishComposingText() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> { }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#finishComposingText()} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testFinishComposingTextAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean finishComposingText() {
                methodCallVerifier.onMethodCalled(bundle -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // The system internally calls "finishComposingText". So wait for a while then reset
            // the verifier before our calling "finishComposingText".
            SystemClock.sleep(TIMEOUT);
            methodCallVerifier.reset();

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callFinishComposingText();
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#finishComposingText() still returns true even after"
                    + " unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#finishComposingText() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#commitCompletion(CompletionInfo)} works as expected.
     */
    @Test
    public void testCommitCompletion() throws Exception {
        final CompletionInfo expectedCompletionInfo = new CompletionInfo(0x12345678, 0x87654321,
                createTestCharSequence("testText", new Annotation("param", "text")),
                createTestCharSequence("testLabel", new Annotation("param", "label")));
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitCompletion(CompletionInfo text) {
                methodCallVerifier.onMethodCalled(bundle -> {
                    bundle.putParcelable("text", text);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callCommitCompletion(expectedCompletionInfo);
            assertTrue("commitCompletion() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                final CompletionInfo actualCompletionInfo = args.getParcelable("text");
                assertNotNull(actualCompletionInfo);
                assertEquals(expectedCompletionInfo.getId(), actualCompletionInfo.getId());
                assertEquals(expectedCompletionInfo.getPosition(),
                        actualCompletionInfo.getPosition());
                assertEqualsForTestCharSequence(expectedCompletionInfo.getText(),
                        actualCompletionInfo.getText());
                assertEqualsForTestCharSequence(expectedCompletionInfo.getLabel(),
                        actualCompletionInfo.getLabel());
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#commitCompletion(CompletionInfo)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testCommitCompletionAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitCompletion(CompletionInfo text) {
                methodCallVerifier.onMethodCalled(bundle -> {
                    bundle.putParcelable("text", text);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callCommitCompletion(new CompletionInfo(
                    0x12345678, 0x87654321,
                    createTestCharSequence("testText", new Annotation("param", "text")),
                    createTestCharSequence("testLabel", new Annotation("param", "label"))));
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#commitCompletion() still returns true even after"
                    + " unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#commitCompletion() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#commitCorrection(CorrectionInfo)} works as expected.
     */
    @Test
    public void testCommitCorrection() throws Exception {
        final CorrectionInfo expectedCorrectionInfo = new CorrectionInfo(0x11111111,
                createTestCharSequence("testOldText", new Annotation("param", "oldText")),
                createTestCharSequence("testNewText", new Annotation("param", "newText")));
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitCorrection(CorrectionInfo correctionInfo) {
                methodCallVerifier.onMethodCalled(bundle -> {
                    bundle.putParcelable("correctionInfo", correctionInfo);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callCommitCorrection(expectedCorrectionInfo);
            assertTrue("commitCorrection() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                final CorrectionInfo actualCorrectionInfo = args.getParcelable("correctionInfo");
                assertNotNull(actualCorrectionInfo);
                assertEquals(expectedCorrectionInfo.getOffset(),
                        actualCorrectionInfo.getOffset());
                assertEqualsForTestCharSequence(expectedCorrectionInfo.getOldText(),
                        actualCorrectionInfo.getOldText());
                assertEqualsForTestCharSequence(expectedCorrectionInfo.getNewText(),
                        actualCorrectionInfo.getNewText());
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#commitCorrection(CorrectionInfo)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testCommitCorrectionAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitCorrection(CorrectionInfo correctionInfo) {
                methodCallVerifier.onMethodCalled(bundle -> {
                    bundle.putParcelable("correctionInfo", correctionInfo);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callCommitCorrection(new CorrectionInfo(0x11111111,
                    createTestCharSequence("testOldText", new Annotation("param", "oldText")),
                    createTestCharSequence("testNewText", new Annotation("param", "newText"))));
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#commitCorrection() still returns true even after"
                    + " unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#commitCorrection() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Verify that the app does not crash even if it does not implement
     * {@link InputConnection#commitCorrection(CorrectionInfo)}, which can happen if the app was
     * built before {@link android.os.Build.VERSION_CODES#HONEYCOMB}.
     */
    @Test
    public void testCommitCorrectionFailWithMethodMissing() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callCommitCorrection(new CorrectionInfo(0x11111111,
                    createTestCharSequence("testOldText", new Annotation("param", "oldText")),
                    createTestCharSequence("testNewText", new Annotation("param", "newText"))));
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertTrue("IC#commitCorrection() returns true even when the target app does not"
                    + " implement it.", result.getReturnBooleanValue());
        });
    }

    /**
     * Test {@link InputConnection#setSelection(int, int)} works as expected.
     */
    @Test
    public void testSetSelection() throws Exception {
        final int expectedStart = 123;
        final int expectedEnd = 456;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setSelection(int start, int end) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("start", start);
                    args.putInt("end", end);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callSetSelection(expectedStart, expectedEnd);
            assertTrue("setSelection() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedStart, args.getInt("start"));
                assertEquals(expectedEnd, args.getInt("end"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setSelection(int, int)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testSetSelectionTextAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setSelection(int start, int end) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("start", start);
                    args.putInt("end", end);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callSetSelection(123, 456);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#setSelection() still returns true even after unbindInput().",
                    result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#setSelection() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setSelection(int, int)} works as expected for
     * {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testSetSelectionForA11y() throws Exception {
        final int expectedStart = 123;
        final int expectedEnd = 456;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setSelection(int start, int end) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("start", start);
                    args.putInt("end", end);
                });
                return returnedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callSetSelection(expectedStart, expectedEnd);
            expectA11yImeCommand(stream, command, TIMEOUT);
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedStart, args.getInt("start"));
                assertEquals(expectedEnd, args.getInt("end"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#performEditorAction(int)} works as expected.
     */
    @Test
    public void testPerformEditorAction() throws Exception {
        final int expectedEditorAction = EditorInfo.IME_ACTION_GO;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performEditorAction(int editorAction) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("editorAction", editorAction);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callPerformEditorAction(expectedEditorAction);
            assertTrue("performEditorAction() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedEditorAction, args.getInt("editorAction"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#performEditorAction(int)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testPerformEditorActionAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performEditorAction(int editorAction) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("editorAction", editorAction);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callPerformEditorAction(EditorInfo.IME_ACTION_GO);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#performEditorAction() still returns true even after "
                    + "unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#performEditorAction() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#performEditorAction(int)} works as expected for
     * {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testPerformEditorActionForA11y() throws Exception {
        final int expectedEditorAction = EditorInfo.IME_ACTION_GO;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performEditorAction(int editorAction) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("editorAction", editorAction);
                });
                return returnedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callPerformEditorAction(expectedEditorAction);
            expectA11yImeCommand(stream, command, TIMEOUT);
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedEditorAction, args.getInt("editorAction"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#performContextMenuAction(int)} works as expected.
     */
    @Test
    public void testPerformContextMenuAction() throws Exception {
        final int expectedId = android.R.id.selectAll;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performContextMenuAction(int id) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("id", id);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callPerformContextMenuAction(expectedId);
            assertTrue("performContextMenuAction() always returns true unless RemoteException is "
                            + "thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedId, args.getInt("id"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#performContextMenuAction(int)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testPerformContextMenuActionAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performContextMenuAction(int id) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("id", id);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callPerformEditorAction(EditorInfo.IME_ACTION_GO);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#performContextMenuAction() still returns true even after "
                    + "unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#performContextMenuAction() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#performContextMenuAction(int)} works as expected
     * for {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testPerformContextMenuActionForA11y() throws Exception {
        final int expectedId = android.R.id.selectAll;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performContextMenuAction(int id) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("id", id);
                });
                return returnedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callPerformContextMenuAction(expectedId);
            expectA11yImeCommand(stream, command, TIMEOUT);
            methodCallVerifier.expectCalledOnce(args -> {
                assertEquals(expectedId, args.getInt("id"));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#beginBatchEdit()} works as expected.
     */
    @Test
    public void testBeginBatchEdit() throws Exception {
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean beginBatchEdit() {
                methodCallVerifier.onMethodCalled(args -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callBeginBatchEdit();
            assertTrue("beginBatchEdit() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> { }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#beginBatchEdit()} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testBeginBatchEditAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean beginBatchEdit() {
                methodCallVerifier.onMethodCalled(args -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callBeginBatchEdit();
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#beginBatchEdit() still returns true even after unbindInput().",
                    result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#beginBatchEdit() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#endBatchEdit()} works as expected.
     */
    @Test
    public void testEndBatchEdit() throws Exception {
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean endBatchEdit() {
                methodCallVerifier.onMethodCalled(args -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callEndBatchEdit();
            assertTrue("endBatchEdit() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> { }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#endBatchEdit()} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testEndBatchEditAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean endBatchEdit() {
                methodCallVerifier.onMethodCalled(args -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callEndBatchEdit();
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#endBatchEdit() still returns true even after unbindInput().",
                    result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#endBatchEdit() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#sendKeyEvent(KeyEvent)} works as expected.
     */
    @Test
    public void testSendKeyEvent() throws Exception {
        final KeyEvent expectedKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X);
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putParcelable("event", event);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callSendKeyEvent(expectedKeyEvent);
            assertTrue("sendKeyEvent() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                final KeyEvent actualKeyEvent = args.getParcelable("event");
                assertNotNull(actualKeyEvent);
                assertEquals(expectedKeyEvent.getAction(), actualKeyEvent.getAction());
                assertEquals(expectedKeyEvent.getKeyCode(), actualKeyEvent.getKeyCode());
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#sendKeyEvent(KeyEvent)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testSendKeyEventAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putParcelable("event", event);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callSendKeyEvent(
                    new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X));
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#sendKeyEvent() still returns true even after unbindInput().",
                    result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#sendKeyEvent() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#sendKeyEvent(KeyEvent)} works as expected for
     * {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testSendKeyEventForA11y() throws Exception {
        final KeyEvent expectedKeyEvent = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_X);
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putParcelable("event", event);
                });
                return returnedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callSendKeyEvent(expectedKeyEvent);
            expectA11yImeCommand(stream, command, TIMEOUT);
            methodCallVerifier.expectCalledOnce(args -> {
                final KeyEvent actualKeyEvent = args.getParcelable("event");
                assertNotNull(actualKeyEvent);
                assertEquals(expectedKeyEvent.getAction(), actualKeyEvent.getAction());
                assertEquals(expectedKeyEvent.getKeyCode(), actualKeyEvent.getKeyCode());
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#clearMetaKeyStates(int)} works as expected.
     */
    @Test
    public void testClearMetaKeyStates() throws Exception {
        final int expectedStates = KeyEvent.META_ALT_MASK;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean clearMetaKeyStates(int states) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("states", states);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callClearMetaKeyStates(expectedStates);
            assertTrue("clearMetaKeyStates() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                final int actualStates = args.getInt("states");
                assertEquals(expectedStates, actualStates);
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#clearMetaKeyStates(int)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testClearMetaKeyStatesAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean clearMetaKeyStates(int states) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("states", states);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callClearMetaKeyStates(KeyEvent.META_ALT_MASK);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#clearMetaKeyStates() still returns true even after "
                    + "unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#clearMetaKeyStates() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#clearMetaKeyStates(int)} works as expected for
     * {@link android.accessibilityservice.InputMethod}.
     */
    @Test
    public void testClearMetaKeyStatesForA11y() throws Exception {
        final int expectedStates = KeyEvent.META_ALT_MASK;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean clearMetaKeyStates(int states) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putInt("states", states);
                });
                return returnedResult;
            }
        }

        testA11yInputConnection(Wrapper::new, (session, stream) -> {
            final var command = session.callClearMetaKeyStates(expectedStates);
            expectA11yImeCommand(stream, command, TIMEOUT);
            methodCallVerifier.expectCalledOnce(args -> {
                final int actualStates = args.getInt("states");
                assertEquals(expectedStates, actualStates);
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#reportFullscreenMode(boolean)} is ignored as expected.
     */
    @Test
    public void testReportFullscreenMode() throws Exception {
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean reportFullscreenMode(boolean enabled) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putBoolean("enabled", enabled);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callReportFullscreenMode(true);
            assertFalse("reportFullscreenMode() always returns false on API 26+",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "IC#reportFullscreenMode() must be ignored on API 26+",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#reportFullscreenMode(boolean)} is ignored as expected even after
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testReportFullscreenModeAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean reportFullscreenMode(boolean enabled) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putBoolean("enabled", enabled);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callReportFullscreenMode(true);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertFalse("reportFullscreenMode() always returns false on API 26+",
                    result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled("IC#reportFullscreenMode() must be ignored on "
                    + "API 26+ even after unbindInput().", EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#performSpellCheck()} works as expected.
     */
    @Test
    public void testPerformSpellCheck() throws Exception {
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performSpellCheck() {
                methodCallVerifier.onMethodCalled(args -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callPerformSpellCheck();
            assertTrue("performSpellCheck() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> { }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#performSpellCheck()} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testPerformSpellCheckAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performSpellCheck() {
                methodCallVerifier.onMethodCalled(args -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callPerformSpellCheck();
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#performSpellCheck() still returns true even after "
                    + "unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#performSpellCheck() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Verify that the default implementation of {@link InputConnection#performSpellCheck()}
     * returns {@code true} without any crash even when the target app does not override it.
     */
    @Test
    public void testPerformSpellCheckDefaultMethod() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callPerformSpellCheck();
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertTrue("IC#performSpellCheck() still returns true even when the target "
                    + "application does not implement it.", result.getReturnBooleanValue());
        });
    }

    /**
     * Test {@link InputConnection#performPrivateCommand(String, Bundle)} works as expected.
     */
    @Test
    public void testPerformPrivateCommand() throws Exception {
        final String expectedAction = "myAction";
        final Bundle expectedData = new Bundle();
        final String expectedDataKey = "testKey";
        final int expectedDataValue = 42;
        expectedData.putInt(expectedDataKey, expectedDataValue);
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performPrivateCommand(String action, Bundle data) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putString("action", action);
                    args.putBundle("data", data);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command =
                    session.callPerformPrivateCommand(expectedAction, expectedData);
            assertTrue("performPrivateCommand() always returns true unless RemoteException is "
                    + "thrown", expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                final String actualAction = args.getString("action");
                final Bundle actualData = args.getBundle("data");
                assertEquals(expectedAction, actualAction);
                assertNotNull(actualData);
                assertEquals(expectedData.get(expectedDataKey), actualData.getInt(expectedDataKey));
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#performPrivateCommand(String, Bundle)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testPerformPrivateCommandAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean performPrivateCommand(String action, Bundle data) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putString("action", action);
                    args.putBundle("data", data);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callPerformPrivateCommand("myAction", null);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#performPrivateCommand() still returns true even after "
                    + "unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#performPrivateCommand() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#getHandler()} is ignored as expected.
     */
    @Test
    public void testGetHandler() throws Exception {
        final Handler returnedResult = null;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public Handler getHandler() {
                methodCallVerifier.onMethodCalled(args -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // The system internally calls "getHandler". So reset the verifier before our calling
            // "callGetHandler".
            methodCallVerifier.reset();
            final ImeCommand command = session.callGetHandler();
            assertTrue("getHandler() always returns null",
                    expectCommand(stream, command, TIMEOUT).isNullReturnValue());

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled("IC#getHandler() must be ignored.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#getHandler()} is ignored as expected even after
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testGetHandlerAfterUnbindInput() throws Exception {
        final Handler returnedResult = null;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public Handler getHandler() {
                methodCallVerifier.onMethodCalled(args -> { });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // The system internally calls "getHandler". So reset the verifier before our calling
            // "callGetHandler".
            methodCallVerifier.reset();
            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callGetHandler();
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertTrue("getHandler() always returns null", result.isNullReturnValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "IC#getHandler() must be ignored even after unbindInput().",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Verify that applications that do not implement {@link InputConnection#getHandler()} will not
     * crash.  This can happen if the app was built before {@link android.os.Build.VERSION_CODES#N}.
     */
    @Test
    public void testGetHandlerWithMethodMissing() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetHandler();
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertTrue("IC#getHandler() still returns null even when the target app does not"
                    + " implement it.", result.isNullReturnValue());
        });
    }

    /**
     * Test {@link InputConnection#closeConnection()} is ignored as expected.
     */
    @Test
    public void testCloseConnection() throws Exception {
        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public void closeConnection() {
                methodCallVerifier.onMethodCalled(args -> { });
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callCloseConnection();
            expectCommand(stream, command, TIMEOUT);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled("IC#getHandler() must be ignored.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#closeConnection()} is ignored as expected even after
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testCloseConnectionAfterUnbindInput() throws Exception {
        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();
        final CountDownLatch latch = new CountDownLatch(1);

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public void closeConnection() {
                methodCallVerifier.onMethodCalled(args -> { });
                latch.countDown();
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // The system internally calls "closeConnection". So wait for it to happen then reset
            // the verifier before our calling "closeConnection".
            assertTrue("closeConnection() must be called by the system.",
                    latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
            methodCallVerifier.reset();

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callCloseConnection();
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "IC#closeConnection() must be ignored even after unbindInput().",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Verify that applications that do not implement {@link InputConnection#closeConnection()}
     * will not crash. This can happen if the app was built before
     * {@link android.os.Build.VERSION_CODES#N}.
     */
    @Test
    public void testCloseConnectionWithMethodMissing() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callCloseConnection();
            expectCommand(stream, command, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setImeConsumesInput(boolean)} works as expected.
     */
    @Test
    public void testSetImeConsumesInput() throws Exception {
        final boolean expectedImeConsumesInput = true;
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setImeConsumesInput(boolean imeConsumesInput) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putBoolean("imeConsumesInput", imeConsumesInput);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callSetImeConsumesInput(expectedImeConsumesInput);
            assertTrue("setImeConsumesInput() always returns true unless RemoteException is thrown",
                    expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
            methodCallVerifier.expectCalledOnce(args -> {
                final boolean actualImeConsumesInput = args.getBoolean("imeConsumesInput");
                assertEquals(expectedImeConsumesInput, actualImeConsumesInput);
            }, TIMEOUT);
        });
    }

    /**
     * Test {@link InputConnection#setImeConsumesInput(boolean)} fails fast once
     * {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testSetImeConsumesInputAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean setImeConsumesInput(boolean imeConsumesInput) {
                methodCallVerifier.onMethodCalled(args -> {
                    args.putBoolean("imeConsumesInput", imeConsumesInput);
                });
                return returnedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            // Memorize the current InputConnection.
            expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

            // Let unbindInput happen.
            triggerUnbindInput();
            expectEvent(stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

            // Now this API call on the memorized IC should fail fast.
            final ImeCommand command = session.callSetImeConsumesInput(true);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            // CAVEAT: this behavior is a bit questionable and may change in a future version.
            assertTrue("Currently IC#setImeConsumesInput() still returns true even after "
                    + "unbindInput().", result.getReturnBooleanValue());
            expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

            // Make sure that the app does not receive the call (for a while).
            methodCallVerifier.expectNotCalled(
                    "Once unbindInput() happened, IC#setImeConsumesInput() fails fast.",
                    EXPECTED_NOT_CALLED_TIMEOUT);
        });
    }

    /**
     * Verify that the default implementation of
     * {@link InputConnection#setImeConsumesInput(boolean)} returns {@code true} without any crash
     * even when the target app does not override it.
     */
    @Test
    public void testSetImeConsumesInputDefaultMethod() throws Exception {
        testMinimallyImplementedInputConnection((MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callSetImeConsumesInput(true);
            final ImeEvent result = expectCommand(stream, command, TIMEOUT);
            assertTrue("IC#setImeConsumesInput() still returns true even when the target "
                    + "application does not implement it.", result.getReturnBooleanValue());
        });
    }

    /**
     * Test {@link InputConnection#takeSnapshot()} is ignored as expected.
     */
    @Test
    public void testTakeSnapshot() throws Exception {
        final TextSnapshot returnedTextSnapshot = new TextSnapshot(
                new SurroundingText("test", 4, 4, 0), -1, -1, 0);
        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public TextSnapshot takeSnapshot() {
                return returnedTextSnapshot;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callTakeSnapshot();
            assertTrue("takeSnapshot() always returns null",
                    expectCommand(stream, command, TIMEOUT).isNullReturnValue());
        });
    }

    /**
     * Test {@link InputConnection#replaceText(int, int, CharSequence, int, TextAttribute)} works as
     * expected.
     */
    @Test
    public void testReplaceText() throws Exception {
        final Annotation expectedSpan = new Annotation("expectedKey", "expectedValue");
        final int expectedStart = 0;
        final int expectedEnd = 5;
        final CharSequence expectedText = createTestCharSequence("expectedText", expectedSpan);
        final int expectedNewCursorPosition = 123;
        final ArrayList<String> expectedSuggestions = new ArrayList<>();
        expectedSuggestions.add("test");
        final TextAttribute expectedTextAttribute =
                new TextAttribute.Builder()
                        .setTextConversionSuggestions(expectedSuggestions)
                        .build();
        // Intentionally let the app return "false" to confirm that IME still receives "true".
        final boolean returnedResult = false;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean replaceText(
                    int start,
                    int end,
                    CharSequence text,
                    int newCursorPosition,
                    TextAttribute textAttribute) {
                methodCallVerifier.onMethodCalled(
                        args -> {
                            args.putInt("start", start);
                            args.putInt("end", end);
                            args.putCharSequence("text", text);
                            args.putInt("newCursorPosition", newCursorPosition);
                            args.putParcelable("textAttribute", textAttribute);
                        });

                return returnedResult;
            }
        }

        testInputConnection(
                Wrapper::new,
                (MockImeSession session, ImeEventStream stream) -> {
                    final ImeCommand command =
                            session.callReplaceText(
                                    expectedStart,
                                    expectedEnd,
                                    expectedText,
                                    expectedNewCursorPosition,
                                    expectedTextAttribute);
                    assertTrue(
                            "replaceText() always returns true unless RemoteException is thrown",
                            expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
                    methodCallVerifier.expectCalledOnce(
                            args -> {
                                assertEquals(expectedStart, args.getInt("start"));
                                assertEquals(expectedEnd, args.getInt("end"));
                                assertEqualsForTestCharSequence(
                                        expectedText, args.getCharSequence("text"));
                                assertEquals(
                                        expectedNewCursorPosition,
                                        args.getInt("newCursorPosition"));
                                final TextAttribute textAttribute =
                                        args.getParcelable("textAttribute");
                                assertThat(textAttribute).isNotNull();
                                assertThat(textAttribute.getTextConversionSuggestions())
                                        .containsExactlyElementsIn(expectedSuggestions);
                            },
                            TIMEOUT);
                });
    }

    /**
     * Test {@link InputConnection#replaceText(int, int, CharSequence, int, TextAttribute)} fails
     * fast once {@link android.view.inputmethod.InputMethod#unbindInput()} is issued.
     */
    @Test
    public void testReplaceTextAfterUnbindInput() throws Exception {
        final boolean returnedResult = true;

        final MethodCallVerifier methodCallVerifier = new MethodCallVerifier();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean replaceText(
                    int start,
                    int end,
                    CharSequence text,
                    int newCursorPosition,
                    TextAttribute textAttribute) {
                methodCallVerifier.onMethodCalled(
                        args -> {
                            args.putInt("start", start);
                            args.putInt("end", end);
                            args.putCharSequence("text", text);
                            args.putInt("newCursorPosition", newCursorPosition);
                            args.putParcelable("textAttribute", textAttribute);
                        });

                return returnedResult;
            }
        }

        testInputConnection(
                Wrapper::new,
                (MockImeSession session, ImeEventStream stream) -> {
                    // Memorize the current InputConnection.
                    expectCommand(stream, session.memorizeCurrentInputConnection(), TIMEOUT);

                    // Let unbindInput happen.
                    triggerUnbindInput();
                    expectEvent(
                            stream, event -> "unbindInput".equals(event.getEventName()), TIMEOUT);

                    // Now IC#getTextAfterCursor() for the memorized IC should fail fast.
                    final ImeEvent result =
                            expectCommand(
                                    stream,
                                    session.callReplaceText(0, 5, "text", 1, null),
                                    TIMEOUT);
                    // CAVEAT: this behavior is a bit questionable and may change in a future
                    // version.
                    assertTrue(
                            "Currently IC#replaceText() still returns true even after"
                                    + " unbindInput().",
                            result.getReturnBooleanValue());
                    expectElapseTimeLessThan(result, IMMEDIATE_TIMEOUT_NANO);

                    // Make sure that the app does not receive the call (for a while).
                    methodCallVerifier.expectNotCalled(
                            "Once unbindInput() happened, IC#replaceText() fails fast.",
                            EXPECTED_NOT_CALLED_TIMEOUT);
                });
    }
}
