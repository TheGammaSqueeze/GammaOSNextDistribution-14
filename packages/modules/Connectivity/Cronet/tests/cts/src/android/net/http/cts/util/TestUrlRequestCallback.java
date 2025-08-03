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

package android.net.http.cts.util;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.net.http.CallbackException;
import android.net.http.HttpException;
import android.net.http.InlineExecutionProhibitedException;
import android.net.http.UrlRequest;
import android.net.http.UrlResponseInfo;
import android.os.ConditionVariable;
import android.os.StrictMode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Callback that tracks information from different callbacks and has a
 * method to block thread until the request completes on another thread.
 * Allows us to cancel, block request or throw an exception from an arbitrary step.
 */
public class TestUrlRequestCallback implements UrlRequest.Callback {
    private static final int TIMEOUT_MS = 12_000;
    public ArrayList<UrlResponseInfo> mRedirectResponseInfoList = new ArrayList<>();
    public ArrayList<String> mRedirectUrlList = new ArrayList<>();
    public UrlResponseInfo mResponseInfo;
    public HttpException mError;

    public ResponseStep mResponseStep = ResponseStep.NOTHING;

    public int mRedirectCount;
    public boolean mOnErrorCalled;
    public boolean mOnCanceledCalled;

    public int mHttpResponseDataLength;
    public String mResponseAsString = "";

    public int mReadBufferSize = 32 * 1024;

    // When false, the consumer is responsible for all calls into the request
    // that advance it.
    private boolean mAutoAdvance = true;
    // Whether an exception is thrown by maybeThrowCancelOrPause().
    private boolean mCallbackExceptionThrown;

    // Whether to permit calls on the network thread.
    private boolean mAllowDirectExecutor;

    // Whether to stop the executor thread after reaching a terminal method.
    // Terminal methods are (onSucceeded, onFailed or onCancelled)
    private boolean mBlockOnTerminalState;

    // Conditionally fail on certain steps.
    private FailureType mFailureType = FailureType.NONE;
    private ResponseStep mFailureStep = ResponseStep.NOTHING;

    // Signals when request is done either successfully or not.
    private final ConditionVariable mDone = new ConditionVariable();

    // Hangs the calling thread until a terminal method has started executing.
    private final ConditionVariable mWaitForTerminalToStart = new ConditionVariable();

    // Signaled on each step when mAutoAdvance is false.
    private final ConditionVariable mStepBlock = new ConditionVariable();

    // Executor Service for Http callbacks.
    private final ExecutorService mExecutorService;
    private Thread mExecutorThread;

    // position() of ByteBuffer prior to read() call.
    private int mBufferPositionBeforeRead;

    private static class ExecutorThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(final Runnable r) {
            return new Thread(new Runnable() {
                @Override
                public void run() {
                    StrictMode.ThreadPolicy threadPolicy = StrictMode.getThreadPolicy();
                    try {
                        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                                .detectNetwork()
                                .penaltyLog()
                                .penaltyDeath()
                                .build());
                        r.run();
                    } finally {
                        StrictMode.setThreadPolicy(threadPolicy);
                    }
                }
            });
        }
    }

    public enum ResponseStep {
        NOTHING,
        ON_RECEIVED_REDIRECT,
        ON_RESPONSE_STARTED,
        ON_READ_COMPLETED,
        ON_SUCCEEDED,
        ON_FAILED,
        ON_CANCELED,
    }

    public enum FailureType {
        NONE,
        CANCEL_SYNC,
        CANCEL_ASYNC,
        // Same as above, but continues to advance the request after posting
        // the cancellation task.
        CANCEL_ASYNC_WITHOUT_PAUSE,
        THROW_SYNC
    }

    private static void assertContains(String expectedSubstring, String actualString) {
        assertNotNull(actualString);
        assertTrue("String [" + actualString + "] doesn't contain substring [" + expectedSubstring
                + "]", actualString.contains(expectedSubstring));

    }

    /**
     * Set {@code mExecutorThread}.
     */
    private void fillInExecutorThread() {
        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                mExecutorThread = Thread.currentThread();
            }
        });
    }

    private boolean isTerminalCallback(ResponseStep step) {
        switch (step) {
            case ON_SUCCEEDED:
            case ON_CANCELED:
            case ON_FAILED:
                return true;
            default:
                return false;
        }
    }

    /**
     * Create a {@link TestUrlRequestCallback} with a new single-threaded executor.
     */
    public TestUrlRequestCallback() {
        this(Executors.newSingleThreadExecutor(new ExecutorThreadFactory()));
    }

    /**
     * Create a {@link TestUrlRequestCallback} using a custom single-threaded executor.
     */
    public TestUrlRequestCallback(ExecutorService executorService) {
        mExecutorService = executorService;
        fillInExecutorThread();
    }

    /**
     * This blocks the callback executor thread once it has reached a final state callback.
     * In order to continue execution, this method must be called again and providing {@code false}
     * to continue execution.
     *
     * @param blockOnTerminalState the state to set for the executor thread
     */
    public void setBlockOnTerminalState(boolean blockOnTerminalState) {
        mBlockOnTerminalState = blockOnTerminalState;
        if (!blockOnTerminalState) {
            mDone.open();
        }
    }

    public void setAutoAdvance(boolean autoAdvance) {
        mAutoAdvance = autoAdvance;
    }

    public void setAllowDirectExecutor(boolean allowed) {
        mAllowDirectExecutor = allowed;
    }

    public void setFailure(FailureType failureType, ResponseStep failureStep) {
        mFailureStep = failureStep;
        mFailureType = failureType;
    }

    /**
     * Blocks the calling thread till callback execution is done
     *
     * @return true if the condition was opened, false if the call returns because of the timeout.
     */
    public boolean blockForDone() {
        return mDone.block(TIMEOUT_MS);
    }

    /**
     * Waits for a terminal callback to complete execution before failing if the callback
     * is not the expected one
     *
     * @param expectedStep the expected callback step
     */
    public void expectCallback(ResponseStep expectedStep) {
        if (isTerminalCallback(expectedStep)) {
            assertTrue("Did not receive terminal callback before timeout", blockForDone());
        }
        assertSame(expectedStep, mResponseStep);
    }

    /**
     * Waits for a terminal callback to complete execution before skipping the test if the
     * callback is not the expected one
     *
     * @param expectedStep the expected callback step
     */
    public void assumeCallback(ResponseStep expectedStep) {
        if (isTerminalCallback(expectedStep)) {
            assumeTrue("Did not receive terminal callback before timeout", blockForDone());
        }
        assumeThat(expectedStep, equalTo(mResponseStep));
    }

    /**
     * Blocks the calling thread until one of the final states has been called.
     * This is called before the callback has finished executed.
     */
    public void waitForTerminalToStart() {
        mWaitForTerminalToStart.block();
    }

    public void waitForNextStep() {
        mStepBlock.block();
        mStepBlock.close();
    }

    public ExecutorService getExecutor() {
        return mExecutorService;
    }

    public void shutdownExecutor() {
        mExecutorService.shutdown();
    }

    /**
     * Shuts down the ExecutorService and waits until it executes all posted
     * tasks.
     */
    public void shutdownExecutorAndWait() {
        mExecutorService.shutdown();
        try {
            // Termination shouldn't take long. Use 1 min which should be more than enough.
            mExecutorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            fail("ExecutorService is interrupted while waiting for termination");
        }
        assertTrue(mExecutorService.isTerminated());
    }

    @Override
    public void onRedirectReceived(
            UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
        checkExecutorThread();
        assertFalse(request.isDone());
        assertThat(mResponseStep, anyOf(
                equalTo(ResponseStep.NOTHING),
                equalTo(ResponseStep.ON_RECEIVED_REDIRECT)));
        assertNull(mError);

        mResponseStep = ResponseStep.ON_RECEIVED_REDIRECT;
        mRedirectUrlList.add(newLocationUrl);
        mRedirectResponseInfoList.add(info);
        ++mRedirectCount;
        if (maybeThrowCancelOrPause(request)) {
            return;
        }
        request.followRedirect();
    }

    @Override
    public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
        checkExecutorThread();
        assertFalse(request.isDone());
        assertThat(mResponseStep, anyOf(
                equalTo(ResponseStep.NOTHING),
                equalTo(ResponseStep.ON_RECEIVED_REDIRECT)));
        assertNull(mError);

        mResponseStep = ResponseStep.ON_RESPONSE_STARTED;
        mResponseInfo = info;
        if (maybeThrowCancelOrPause(request)) {
            return;
        }
        startNextRead(request);
    }

    @Override
    public void onReadCompleted(UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) {
        checkExecutorThread();
        assertFalse(request.isDone());
        assertThat(mResponseStep, anyOf(
                equalTo(ResponseStep.ON_RESPONSE_STARTED),
                equalTo(ResponseStep.ON_READ_COMPLETED)));
        assertNull(mError);

        mResponseStep = ResponseStep.ON_READ_COMPLETED;

        final byte[] lastDataReceivedAsBytes;
        final int bytesRead = byteBuffer.position() - mBufferPositionBeforeRead;
        mHttpResponseDataLength += bytesRead;
        lastDataReceivedAsBytes = new byte[bytesRead];
        // Rewind |byteBuffer.position()| to pre-read() position.
        byteBuffer.position(mBufferPositionBeforeRead);
        // This restores |byteBuffer.position()| to its value on entrance to
        // this function.
        byteBuffer.get(lastDataReceivedAsBytes);
        mResponseAsString += new String(lastDataReceivedAsBytes);

        if (maybeThrowCancelOrPause(request)) {
            return;
        }
        startNextRead(request);
    }

    @Override
    public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
        checkExecutorThread();
        assertTrue(request.isDone());
        assertThat(mResponseStep, anyOf(
                equalTo(ResponseStep.ON_RESPONSE_STARTED),
                equalTo(ResponseStep.ON_READ_COMPLETED)));
        assertFalse(mOnErrorCalled);
        assertFalse(mOnCanceledCalled);
        assertNull(mError);

        mResponseStep = ResponseStep.ON_SUCCEEDED;
        mResponseInfo = info;
        mWaitForTerminalToStart.open();
        if (mBlockOnTerminalState) mDone.block();
        openDone();
        maybeThrowCancelOrPause(request);
    }

    @Override
    public void onFailed(UrlRequest request, UrlResponseInfo info, HttpException error) {
        // If the failure is because of prohibited direct execution, the test shouldn't fail
        // since the request already did.
        if (error.getCause() instanceof InlineExecutionProhibitedException) {
            mAllowDirectExecutor = true;
        }
        checkExecutorThread();
        assertTrue(request.isDone());
        // Shouldn't happen after success.
        assertNotEquals(ResponseStep.ON_SUCCEEDED, mResponseStep);
        // Should happen at most once for a single request.
        assertFalse(mOnErrorCalled);
        assertFalse(mOnCanceledCalled);
        assertNull(mError);
        if (mCallbackExceptionThrown) {
            assertTrue(error instanceof CallbackException);
            assertContains("Exception received from UrlRequest.Callback", error.getMessage());
            assertNotNull(error.getCause());
            assertTrue(error.getCause() instanceof IllegalStateException);
            assertContains("Listener Exception.", error.getCause().getMessage());
        }

        mResponseStep = ResponseStep.ON_FAILED;
        mOnErrorCalled = true;
        mError = error;
        mWaitForTerminalToStart.open();
        if (mBlockOnTerminalState) mDone.block();
        openDone();
        maybeThrowCancelOrPause(request);
    }

    @Override
    public void onCanceled(UrlRequest request, UrlResponseInfo info) {
        checkExecutorThread();
        assertTrue(request.isDone());
        // Should happen at most once for a single request.
        assertFalse(mOnCanceledCalled);
        assertFalse(mOnErrorCalled);
        assertNull(mError);

        mResponseStep = ResponseStep.ON_CANCELED;
        mOnCanceledCalled = true;
        mWaitForTerminalToStart.open();
        if (mBlockOnTerminalState) mDone.block();
        openDone();
        maybeThrowCancelOrPause(request);
    }

    public void startNextRead(UrlRequest request) {
        startNextRead(request, ByteBuffer.allocateDirect(mReadBufferSize));
    }

    public void startNextRead(UrlRequest request, ByteBuffer buffer) {
        mBufferPositionBeforeRead = buffer.position();
        request.read(buffer);
    }

    public boolean isDone() {
        // It's not mentioned by the Android docs, but block(0) seems to block
        // indefinitely, so have to block for one millisecond to get state
        // without blocking.
        return mDone.block(1);
    }

    protected void openDone() {
        mDone.open();
    }

    private void checkExecutorThread() {
        if (!mAllowDirectExecutor) {
            assertEquals(mExecutorThread, Thread.currentThread());
        }
    }

    /**
     * Returns {@code false} if the listener should continue to advance the
     * request.
     */
    private boolean maybeThrowCancelOrPause(final UrlRequest request) {
        checkExecutorThread();
        if (mResponseStep != mFailureStep || mFailureType == FailureType.NONE) {
            if (!mAutoAdvance) {
                mStepBlock.open();
                return true;
            }
            return false;
        }

        if (mFailureType == FailureType.THROW_SYNC) {
            assertFalse(mCallbackExceptionThrown);
            mCallbackExceptionThrown = true;
            throw new IllegalStateException("Listener Exception.");
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                request.cancel();
            }
        };
        if (mFailureType == FailureType.CANCEL_ASYNC
                || mFailureType == FailureType.CANCEL_ASYNC_WITHOUT_PAUSE) {
            getExecutor().execute(task);
        } else {
            task.run();
        }
        return mFailureType != FailureType.CANCEL_ASYNC_WITHOUT_PAUSE;
    }
}
