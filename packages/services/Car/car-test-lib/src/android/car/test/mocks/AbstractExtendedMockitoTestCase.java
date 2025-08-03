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
package android.car.test.mocks;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.test.AbstractExpectableTestCase;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Trace;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.Slog;
import android.util.TimingsTraceLog;

import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.internal.util.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.quality.Strictness;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for tests that must use {@link com.android.dx.mockito.inline.extended.ExtendedMockito}
 * to mock static classes and final methods.
 *
 * <p><b>Note: </b> this class automatically spy on {@link Log} and {@link Slog} and fail tests that
 * all any of their {@code wtf()} methods. If a test is expect to call {@code wtf()}, it should be
 * annotated with {@link ExpectWtf}.
 *
 * <p><b>Note: </b>when using this class, you must include the following
 * dependencies on {@code Android.bp} (or {@code Android.mk}:
 * <pre><code>
    jni_libs: [
        "libdexmakerjvmtiagent",
        "libstaticjvmtiagent",
    ],

   LOCAL_JNI_SHARED_LIBRARIES := \
      libdexmakerjvmtiagent \
      libstaticjvmtiagent \
 *  </code></pre>
 */
public abstract class AbstractExtendedMockitoTestCase extends AbstractExpectableTestCase {

    static final String TAG = AbstractExtendedMockitoTestCase.class.getSimpleName();

    private static final boolean TRACE = false;

    private static final long SYNC_RUNNABLE_MAX_WAIT_TIME = 5_000L;

    @SuppressWarnings("IsLoggableTagLength")
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * Should be used on constructors for test case whose object under test doesn't make any logging
     * call.
     */
    protected static final String[] NO_LOG_TAGS = new String[] {
            "I can't believe a test case is using this String as a log TAG! Well done!"
    };

    /**
     * Number of invocations, used to force a failure on {@link #forceFailure(int, Class, String)}.
     */
    private static int sInvocationsCounter;

    /**
     * Sessions follow the "Highlander Rule": There can be only one!
     *
     * <p>So, we keep track of that and force-close it if needed.
     */
    @Nullable
    private static MockitoSession sHighlanderSession;

    /**
     * Points to where the current session was created.
     */
    private static Exception sSessionCreationLocation;

    private final List<Class<?>> mStaticSpiedClasses = new ArrayList<>();
    private final List<Class<?>> mStaticMockedClasses = new ArrayList<>();

    // Tracks (S)Log.wtf() calls made during code execution, then used on verifyWtfNeverLogged()
    private final List<RuntimeException> mWtfs = new ArrayList<>();

    private MockitoSession mSession;

    @Nullable
    private final TimingsTraceLog mTracer;

    @Nullable
    private final ArraySet<String> mLogTags;

    @Rule
    public final WtfCheckerRule mWtfCheckerRule = new WtfCheckerRule();

    /**
     * Default constructor.
     *
     * @param logTags tags to be checked for issues (like {@code wtf()} calls); use
     * {@link #NO_LOG_TAGS} when object under test doesn't log anything.
     */
    protected AbstractExtendedMockitoTestCase(String... logTags) {
        Objects.requireNonNull(logTags, "logTags cannot be null");

        sInvocationsCounter++;

        if (VERBOSE) {
            Log.v(TAG, "constructor for " + getClass() + ": sInvocationsCount="
                    + sInvocationsCounter + ", logTags=" + Arrays.toString(logTags));
        }

        String prefix = getClass().getSimpleName();
        if (Arrays.equals(logTags, NO_LOG_TAGS)) {
            if (VERBOSE) {
                Log.v(TAG, prefix + ": not checking for wtf logs");
            }
            mLogTags = null;
        } else {
            if (VERBOSE) {
                Log.v(TAG, prefix + ": checking for wtf calls on tags " + Arrays.toString(logTags));
            }
            mLogTags = new ArraySet<>(logTags.length);
            for (String logTag: logTags) {
                mLogTags.add(logTag);
            }
        }
        mTracer = TRACE ? new TimingsTraceLog(TAG, Trace.TRACE_TAG_APP) : null;
    }

    @Before
    public final void startSession() {
        if (VERBOSE) {
            Log.v(TAG, "startSession() for " + getTestName() + " on thread "
                    + Thread.currentThread() + "; sHighlanderSession=" + sHighlanderSession);
        }
        finishHighlanderSessionIfNeeded("startSession()");

        beginTrace("startSession()");

        createSessionLocation();

        StaticMockitoSessionBuilder builder = mockitoSession()
                .strictness(getSessionStrictness());

        CustomMockitoSessionBuilder customBuilder =
                new CustomMockitoSessionBuilder(builder, mStaticSpiedClasses, mStaticMockedClasses)
                    .spyStatic(Log.class)
                    .spyStatic(Slog.class);

        beginTrace("onSessionBuilder()");
        onSessionBuilder(customBuilder);
        endTrace();

        if (VERBOSE) {
            Log.v(TAG, "spied classes: " + customBuilder.mStaticSpiedClasses
                    + " mocked classes:" + customBuilder.mStaticMockedClasses);
        }

        beginTrace("startMocking()");
        sHighlanderSession = mSession = builder.initMocks(this).startMocking();
        endTrace();

        if (customBuilder.mCallback != null) {
            if (VERBOSE) {
                Log.v(TAG, "Calling " + customBuilder.mCallback);
            }
            customBuilder.mCallback.afterSessionStarted();
        }

        beginTrace("interceptWtfCalls()");
        interceptWtfCalls();
        endTrace();

        endTrace(); // startSession
    }

    private void createSessionLocation() {
        beginTrace("createSessionLocation()");
        try {
            sSessionCreationLocation = new Exception(getTestName());
        } catch (Exception e) {
            // Better safe than sorry...
            Log.e(TAG, "Could not create sSessionCreationLocation with " + getTestName()
                    + " on thread " + Thread.currentThread(), e);
            sSessionCreationLocation = e;
        }
        endTrace();
    }

    @After
    public final void finishSession() throws Exception {
        if (VERBOSE) {
            Log.v(TAG, "finishSession() for " + getTestName() + " on thread "
                    + Thread.currentThread() + "; sHighlanderSession=" + sHighlanderSession);

        }
        if (false) { // For obvious reasons, should NEVER be merged as true
            forceFailure(1, RuntimeException.class, "to simulate an unfinished session");
        }

        // mSession.finishMocking() must ALWAYS be called (hence the over-protective try/finally
        // statements), otherwise it would cause failures on future tests as mockito
        // cannot start a session when a previous one is not finished
        try {
            beginTrace("finishSession()");
            completeAllHandlerThreadTasks();
        } finally {
            sHighlanderSession = null;
            finishSessionMocking();
        }
        endTrace();
    }

    private void finishSessionMocking() {
        if (mSession == null) {
            Log.w(TAG, getClass().getSimpleName() + ".finishSession(): no session");
            return;
        }
        try {
            beginTrace("finishMocking()");
        } finally {
            try {
                mSession.finishMocking();
            } finally {
                // Shouldn't need to set mSession to null as JUnit always instantiate a new object,
                // but it doesn't hurt....
                mSession = null;
                clearInlineMocks("finishMocking()");
                endTrace(); // finishMocking
            }
        }
    }

    protected void clearInlineMocks(String when) {
        // When using inline mock maker, clean up inline mocks to prevent OutOfMemory
        // errors. See https://github.com/mockito/mockito/issues/1614 and b/259280359.
        Log.d(TAG, "Calling Mockito.framework().clearInlineMocks() on " + when);
        Mockito.framework().clearInlineMocks();

    }

    private void finishHighlanderSessionIfNeeded(String where) {
        if (sHighlanderSession == null) {
            if (VERBOSE) {
                Log.v(TAG, "finishHighlanderSessionIfNeeded(): sHighlanderSession already null");
            }
            return;
        }

        beginTrace("finishHighlanderSessionIfNeeded()");

        if (sSessionCreationLocation != null) {
            if (VERBOSE) {
                Log.e(TAG, where + ": There can be only one! Closing unfinished session, "
                        + "created at", sSessionCreationLocation);
            } else {
                Log.e(TAG, where + ": There can be only one! Closing unfinished session, "
                        + "created at " +  sSessionCreationLocation);
            }
        } else {
            Log.e(TAG, where + ": There can be only one! Closing unfinished session created at "
                    + "unknown location");
        }
        try {
            sHighlanderSession.finishMocking();
        } catch (Throwable t) {
            if (VERBOSE) {
                Log.e(TAG, "Failed to close unfinished session on " + getTestName(), t);
            } else {
                Log.e(TAG, "Failed to close unfinished session on " + getTestName() + ": " + t);
            }
        } finally {
            if (VERBOSE) {
                Log.v(TAG, "Resetting sHighlanderSession at finishHighlanderSessionIfNeeded()");
            }
            sHighlanderSession = null;
        }

        endTrace();
    }

    /**
     * Forces a failure at the given invocation of a test method by throwing an exception.
     */
    protected final <T extends Throwable> void forceFailure(int invocationCount,
            Class<T> failureClass, String reason) throws T {
        if (sInvocationsCounter != invocationCount) {
            Log.d(TAG, "forceFailure(" + invocationCount + "): no-op on invocation #"
                    + sInvocationsCounter);
            return;
        }
        String message = "Throwing on invocation #" + sInvocationsCounter + ": " + reason;
        Log.e(TAG, message);
        T throwable;
        try {
            Constructor<T> constructor = failureClass.getConstructor(String.class);
            throwable = constructor.newInstance("Throwing on invocation #" + sInvocationsCounter
                    + ": " + reason);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not create exception of class " + failureClass
                    + " using msg='" + message + "' as constructor");
        }
        throw throwable;
    }

    /**
     * Gets the name of the test being run.
     */
    protected final String getTestName() {
        return mWtfCheckerRule.mTestName;
    }

    /**
     * Waits for completion of all pending Handler tasks for all HandlerThread in the process.
     *
     * <p>This can prevent pending Handler tasks of one test from affecting another. This does not
     * work if the message is posted with delay.
     */
    protected final void completeAllHandlerThreadTasks() {
        beginTrace("completeAllHandlerThreadTasks");
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        ArrayList<HandlerThread> handlerThreads = new ArrayList<>(threadSet.size());
        Thread currentThread = Thread.currentThread();
        for (Thread t : threadSet) {
            if (t != currentThread && t instanceof HandlerThread) {
                if (VERBOSE) {
                    Log.v(TAG, "Will wait for " + t);
                }
                handlerThreads.add((HandlerThread) t);
            } else if (VERBOSE) {
                Log.v(TAG, "Skipping " + t);
            }
        }
        int size = handlerThreads.size();
        ArrayList<SyncRunnable> syncs = new ArrayList<>(size);
        Log.d(TAG, "Waiting for " + size + " HandlerThreads");
        for (int i = 0; i < size; i++) {
            HandlerThread thread = handlerThreads.get(i);
            Looper looper = thread.getLooper();
            if (looper == null) {
                Log.w(TAG, "Ignoring thread " + thread + ". It doesn't have a looper.");
                continue;
            }
            if (VERBOSE) {
                Log.v(TAG, "Waiting for thread " + thread);
            }
            Handler handler = new Handler(looper);
            SyncRunnable sr = new SyncRunnable(() -> { });
            handler.post(sr);
            syncs.add(sr);
        }
        beginTrace("waitForComplete");
        for (int i = 0; i < syncs.size(); i++) {
            syncs.get(i).waitForComplete(SYNC_RUNNABLE_MAX_WAIT_TIME);
        }
        endTrace(); // waitForComplete
        endTrace(); // completeAllHandlerThreadTasks
    }

    /**
     * Subclasses can use this method to initialize the Mockito session that's started before every
     * test on {@link #startSession()}.
     *
     * <p>Typically, it should be overridden when mocking static methods.
     *
     * <p><b>NOTE:</b> you don't need to call it to spy on {@link Log} or {@link Slog}, as those
     * are already spied on.
     */
    protected void onSessionBuilder(@NonNull CustomMockitoSessionBuilder session) {
        if (VERBOSE) Log.v(TAG, getLogPrefix() + "onSessionBuilder()");
    }

    /**
     * Changes the value of the session created by
     * {@link #onSessionBuilder(CustomMockitoSessionBuilder)}.
     *
     * <p>By default it's set to {@link Strictness.LENIENT}, but subclasses can overwrite this
     * method to change the behavior.
     */
    @NonNull
    protected Strictness getSessionStrictness() {
        return Strictness.LENIENT;
    }

    /**
     * Mocks a call to {@link ActivityManager#getCurrentUser()}.
     *
     * @param userId result of such call
     *
     * @throws IllegalStateException if class didn't override
     * {@link #onSessionBuilder(CustomMockitoSessionBuilder)} and called
     * {@code spyStatic(Binder.class)} on the session passed to it.
     */
    protected final void mockGetCurrentUser(@UserIdInt int userId) {
        if (VERBOSE) Log.v(TAG, getLogPrefix() + "mockGetCurrentUser(" + userId + ")");
        assertSpied(ActivityManager.class);

        beginTrace("mockAmGetCurrentUser-" + userId);
        AndroidMockitoHelper.mockAmGetCurrentUser(userId);
        endTrace();
    }

    /**
     * Mocks a call to {@link UserManager#isHeadlessSystemUserMode()}.
     *
     * @param mode result of such call
     *
     * @throws IllegalStateException if class didn't override
     * {@link #onSessionBuilder(CustomMockitoSessionBuilder)} and called
     * {@code spyStatic(Binder.class)} on the session passed to it.
     */
    protected final void mockIsHeadlessSystemUserMode(boolean mode) {
        if (VERBOSE) Log.v(TAG, getLogPrefix() + "mockIsHeadlessSystemUserMode(" + mode + ")");
        assertSpied(UserManager.class);

        beginTrace("mockUmIsHeadlessSystemUserMode");
        AndroidMockitoHelper.mockUmIsHeadlessSystemUserMode(mode);
        endTrace();
    }

    /**
     * Mocks a call to {@link Binder.getCallingUserHandle()}.
     *
     * @throws IllegalStateException if class didn't override
     * {@link #onSessionBuilder(CustomMockitoSessionBuilder)} and called
     * {@code spyStatic(Binder.class)} on the session passed to it.
     */
    protected final void mockGetCallingUserHandle(@UserIdInt int userId) {
        if (VERBOSE) Log.v(TAG, getLogPrefix() + "mockBinderCallingUser(" + userId + ")");
        assertSpied(Binder.class);

        beginTrace("mockBinderCallingUser");
        AndroidMockitoHelper.mockBinderGetCallingUserHandle(userId);
        endTrace();
    }

    /**
     * Starts a tracing message.
     *
     * <p>MUST be followed by a {@link #endTrace()} calls.
     *
     * <p>Ignored if {@value #VERBOSE} is {@code false}.
     */
    protected final void beginTrace(@NonNull String message) {
        if (mTracer == null) return;

        Log.d(TAG, getLogPrefix() + message);
        mTracer.traceBegin(message);
    }

    /**
     * Ends a tracing call.
     *
     * <p>MUST be called after {@link #beginTrace(String)}.
     *
     * <p>Ignored if {@value #VERBOSE} is {@code false}.
     */
    protected final void endTrace() {
        if (mTracer == null) return;

        mTracer.traceEnd();
    }

    private void interceptWtfCalls() {
        try {
            doAnswer((invocation) -> {
                return addWtf(invocation);
            }).when(() -> Log.wtf(anyString(), anyString()));
            doAnswer((invocation) -> {
                return addWtf(invocation);
            }).when(() -> Log.wtf(anyString(), any(Throwable.class)));
            doAnswer((invocation) -> {
                return addWtf(invocation);
            }).when(() -> Log.wtf(anyString(), anyString(), any(Throwable.class)));
            doAnswer((invocation) -> {
                return addWtf(invocation);
            }).when(() -> Slog.wtf(anyString(), anyString()));
            doAnswer((invocation) -> {
                return addWtf(invocation);
            }).when(() -> Slog.wtf(anyString(), any(Throwable.class)));
            doAnswer((invocation) -> {
                return addWtf(invocation);
            }).when(() -> Slog.wtf(anyString(), anyString(), any(Throwable.class)));
            // NOTE: android.car.builtin.util.Slogf calls android.util.Slog behind the scenes, so no
            // need to check for calls of the former...
        } catch (Throwable t) {
            Log.e(TAG, "interceptWtfCalls(): failed for test " + getTestName(), t);
        }
    }

    private Object addWtf(InvocationOnMock invocation) {
        String message = "Called " + invocation;
        Log.d(TAG, message); // Log always, as some test expect it
        String actualTag = (String) invocation.getArguments()[0];
        if (mLogTags != null && mLogTags.contains(actualTag)) {
            mWtfs.add(new IllegalStateException(message));
        } else if (VERBOSE) {
            Log.v(TAG, "ignoring WTF invocation on tag " + actualTag + ". mLogTags=" + mLogTags);
        }
        return null;
    }

    private void verifyWtfLogged() {
        Preconditions.checkState(!mWtfs.isEmpty(), "no wtf() called");
    }

    private void verifyWtfNeverLogged() {
        int size = mWtfs.size();
        if (VERBOSE) {
            Log.v(TAG, "verifyWtfNeverLogged(): mWtfs=" + mWtfs);
        }

        switch (size) {
            case 0:
                return;
            case 1:
                throw mWtfs.get(0);
            default:
                StringBuilder msg = new StringBuilder("wtf called ").append(size).append(" times")
                        .append(": ").append(mWtfs);
                throw new AssertionError(msg.toString());
        }
    }

    /**
     * Gets a prefix for {@link Log} calls
     */
    protected final String getLogPrefix() {
        return getClass().getSimpleName() + ".";
    }

    /**
     * Asserts the given class is being spied in the Mockito session.
     */
    protected final void assertSpied(Class<?> clazz) {
        Preconditions.checkArgument(mStaticSpiedClasses.contains(clazz),
                "did not call spyStatic() on %s", clazz.getName());
    }

    /**
     * Asserts the given class is being mocked in the Mockito session.
     */
    protected final void assertMocked(Class<?> clazz) {
        Preconditions.checkArgument(mStaticMockedClasses.contains(clazz),
                "did not call mockStatic() on %s", clazz.getName());
    }

    /**
     * Custom {@code MockitoSessionBuilder} used to make sure some pre-defined mock expectations
     * (like {@link AbstractExtendedMockitoTestCase#mockGetCurrentUser(int)} fail if the test case
     * didn't explicitly set it to spy / mock the required classes.
     *
     * <p><b>NOTE: </b>for now it only provides simple {@link #spyStatic(Class)}, but more methods
     * (as provided by {@link StaticMockitoSessionBuilder}) could be provided as needed.
     */
    public static final class CustomMockitoSessionBuilder {
        private final StaticMockitoSessionBuilder mBuilder;
        private final List<Class<?>> mStaticSpiedClasses;
        private final List<Class<?>> mStaticMockedClasses;

        private @Nullable SessionCallback mCallback;

        private CustomMockitoSessionBuilder(StaticMockitoSessionBuilder builder,
                List<Class<?>> staticSpiedClasses, List<Class<?>> staticMockedClasses) {
            mBuilder = builder;
            mStaticSpiedClasses = staticSpiedClasses;
            mStaticMockedClasses = staticMockedClasses;
        }

        /**
         * Same as {@link StaticMockitoSessionBuilder#spyStatic(Class)}.
         */
        public <T> CustomMockitoSessionBuilder spyStatic(Class<T> clazz) {
            Preconditions.checkState(!mStaticSpiedClasses.contains(clazz),
                    "already called spyStatic() on " + clazz);
            mStaticSpiedClasses.add(clazz);
            mBuilder.spyStatic(clazz);
            return this;
        }

        /**
         * Same as {@link StaticMockitoSessionBuilder#mockStatic(Class)}.
         */
        public <T> CustomMockitoSessionBuilder mockStatic(Class<T> clazz) {
            Preconditions.checkState(!mStaticMockedClasses.contains(clazz),
                    "already called mockStatic() on " + clazz);
            mStaticMockedClasses.add(clazz);
            mBuilder.mockStatic(clazz);
            return this;
        }

        void setSessionCallback(SessionCallback callback) {
            mCallback = callback;
        }
    }

    // TODO(b/156033195): only used by MockSettings, should go away if that class is refactored to
    // not mock stuff
    interface SessionCallback {
        void afterSessionStarted();
    }

    private final class WtfCheckerRule implements TestRule {

        @Nullable
        private String mTestName;

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    mTestName = description.getDisplayName();
                    String testMethodName = description.getMethodName();
                    if (VERBOSE) Log.v(TAG, "running " + mTestName);

                    Method testMethod = AbstractExtendedMockitoTestCase.this.getClass()
                            .getMethod(testMethodName);
                    ExpectWtf expectWtfAnnotation = testMethod.getAnnotation(ExpectWtf.class);
                    Preconditions.checkState(expectWtfAnnotation == null || mLogTags != null,
                            "Must call constructor that pass logTags on %s to use @%s",
                            description.getTestClass(), ExpectWtf.class.getSimpleName());

                    beginTrace("evaluate-" + testMethodName);
                    base.evaluate();
                    endTrace();

                    beginTrace("verify-wtfs");
                    try {
                        if (expectWtfAnnotation != null) {
                            if (VERBOSE) Log.v(TAG, "expecting wtf()");
                            verifyWtfLogged();
                        } else {
                            if (VERBOSE) Log.v(TAG, "NOT expecting wtf()");
                            verifyWtfNeverLogged();
                        }
                    } finally {
                        endTrace();
                    }
                }
            };
        }
    }

    /**
     * Annotation used on test methods that are expect to call {@code wtf()} methods on {@link Log}
     * or {@link Slog} - if such methods are not annotated with this annotation, they will fail.
     */
    @Retention(RUNTIME)
    @Target({METHOD})
    public static @interface ExpectWtf {
    }

    private static final class SyncRunnable implements Runnable {
        private final Runnable mTarget;
        private volatile boolean mComplete = false;

        private SyncRunnable(Runnable target) {
            mTarget = target;
        }

        @Override
        public void run() {
            mTarget.run();
            synchronized (this) {
                mComplete = true;
                notifyAll();
            }
        }

        private void waitForComplete(long maxWaitTime) {
            long t0 = System.currentTimeMillis();
            synchronized (this) {
                while (!mComplete && System.currentTimeMillis() - t0 < maxWaitTime) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted SyncRunnable thread", e);
                    }
                }
            }
        }
    }
}
