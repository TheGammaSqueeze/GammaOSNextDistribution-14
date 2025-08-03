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

package android.os.profiling;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Binder;
import android.os.FileUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IProfilingResultCallback;
import android.os.IProfilingService;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.ProfilingRequest;
import android.os.ProfilingResult;
import android.os.RemoteException;
import android.util.Log;
import android.util.ArrayMap;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.SystemService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
import java.lang.Process;
import java.lang.ProcessBuilder;
import java.lang.Runnable;
import java.lang.RuntimeException;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;

public class ProfilingService extends IProfilingService.Stub {
    private static final String TAG = ProfilingService.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String TEMP_TRACE_PATH = "/data/misc/perfetto-traces/profiling";
    private static final String TRACE_FILE_RELATIVE_PATH = "/profiling";
    private static final String TRACE_FILE_PREFIX = "/trace_";
    private static final String TRACE_FILE_SUFFIX = ".perfetto-trace"; // TODO: match output type.

    private static final int PERFETTO_DESTROY_DEFAULT_TIMEOUT_MS = 10 * 1000;

    private final int PERFETTO_DESTROY_TIMEOUT_MS;

    private final Context mContext;
    @VisibleForTesting public RateLimiter mRateLimiter = null;

    private final HandlerThread mHandlerThread = new HandlerThread("ProfilingService");
    private Handler mHandler;


    // uid indexed collecion of JNI callbacks for results.
    @VisibleForTesting
    public SparseArray<IProfilingResultCallback> mResultCallbacks = new SparseArray<>();

    // Request UUID key indexed storage of active tracing sessions. Currently only 1 active session
    // is supported at a time, but this will be used in future to support multiple.
    @VisibleForTesting
    public ArrayMap<String, TracingSession> mTracingSessions = new ArrayMap<>();

    @VisibleForTesting
    public ProfilingService(Context context) {
        mContext = context;
        PERFETTO_DESTROY_TIMEOUT_MS = PERFETTO_DESTROY_DEFAULT_TIMEOUT_MS;
        mHandlerThread.start();
    }

    /**
     * This method validates the request, arguments, whether the app is allowed to profile now,
     * and if so, starts the profiling.
     */
    public void requestProfiling(byte[] profilingRequestBytes, String filePath, String tag,
            long keyMostSigBits, long keyLeastSigBits) {
        int uid = Binder.getCallingUid();

        // Check if we're running another trace so we don't run multiple at once.
        try {
            if (areAnyTracesRunning()) {
                processResultCallback(uid, keyMostSigBits, keyLeastSigBits,
                    ProfilingResult.ERROR_FAILED_PROFILING_IN_PROGRESS, null, tag, null);
                return;
            }
        } catch (RuntimeException e) {
            if (DEBUG) Log.d(TAG, "Error communicating with perfetto", e);
            processResultCallback(uid, keyMostSigBits, keyLeastSigBits,
                    ProfilingResult.ERROR_UNKNOWN, null, tag, "Error communicating with perfetto");
            return;
        }

        // Process the request from the byte array it was provided as.
        ProfilingRequest request = null;
        try {
            request = ProfilingRequest.parseFrom(profilingRequestBytes);
        } catch (IOException e) {
            if (DEBUG) Log.d(TAG, "Exception parsing request", e);
            processResultCallback(uid, keyMostSigBits, keyLeastSigBits,
                    ProfilingResult.ERROR_FAILED_INVALID_REQUEST, null, tag,
                    "Request parsing failed");
            return;
        }

        // Get package name for requesting process. We can't request the trace without it.
        String packageName = mContext.getPackageManager().getNameForUid(uid);
        if (packageName == null) {
            if (DEBUG) Log.d(TAG, "Could not get package name for UID: " + uid);
            processResultCallback(uid, keyMostSigBits, keyLeastSigBits,
                    ProfilingResult.ERROR_UNKNOWN, null, tag, "Couldn't determine package name");
            return;
        }

        // Check with rate limiter if this request is allowed.
        final int status = getRateLimiter().isProfilingRequestAllowed(Binder.getCallingUid(),
                request);
        if (DEBUG) Log.d(TAG, "Rate limiter status: " + status);
        if (status == RateLimiter.RATE_LIMIT_RESULT_ALLOWED) {
            // Rate limiter approved, try to start the request.
            try {
                TracingSession session = new TracingSession(request, filePath, uid, packageName,
                        tag, keyMostSigBits, keyLeastSigBits);
                startProfiling(session);
            } catch (IllegalArgumentException e) {
                // Issue with the request. Apps fault.
                if (DEBUG) Log.d(TAG, "Invalid request", e);
                processResultCallback(uid, keyMostSigBits, keyLeastSigBits,
                        ProfilingResult.ERROR_FAILED_INVALID_REQUEST, null, tag, e.getMessage());
                return;
            } catch (RuntimeException e) {
                // Perfetto error. Systems fault.
                if (DEBUG) Log.d(TAG, "Perfetto error", e);
                processResultCallback(uid, keyMostSigBits, keyLeastSigBits,
                        ProfilingResult.ERROR_UNKNOWN, null, tag, "Perfetto error");
                return;
            }
        } else {
            // Rate limiter denied, notify caller.
            if (DEBUG) Log.d(TAG, "Request denied with status: " + status);
            processResultCallback(uid, keyMostSigBits, keyLeastSigBits,
                    RateLimiter.statusToResult(status), null, tag, null);
        }
    }

    public void registerResultsCallback(IProfilingResultCallback callback) {
        mResultCallbacks.put(Binder.getCallingUid(), callback);
    }

    public void requestCancel(long keyMostSigBits, long keyLeastSigBits) {
        String key = (new UUID(keyMostSigBits, keyLeastSigBits)).toString();
        if (!isTraceRunning(key)) {
            // No trace running, nothing to cancel.
            if (DEBUG) Log.d(TAG, "Exited requestCancel without stopping trace key:" + key
                    + " due to no trace running.");
            return;
        }
        stopProfiling(key);
    }

    private void processResultCallback(TracingSession session, int status, @Nullable String error) {
        processResultCallback(session.getUid(), session.getKeyMostSigBits(),
                session.getKeyLeastSigBits(), status,
                session.getDestinationFileName(TRACE_FILE_RELATIVE_PATH),
                session.getTag(), error);
    }

    private void processResultCallback(int uid, long keyMostSigBits, long keyLeastSigBits,
            int status, @Nullable String filePath, @Nullable String tag, @Nullable String error) {
        if (!mResultCallbacks.contains(uid)) {
            // No callback, nowhere to notify with result or this failure.
            if (DEBUG) Log.d(TAG, "No callback to ProfilingManager, callback dropped.");
            return;
        }
        try {
            mResultCallbacks.get(uid).sendResult(filePath, keyMostSigBits, keyLeastSigBits, status,
                    tag, error);
        } catch (RemoteException e) {
            // Failed to send result. Ignore.
            if (DEBUG) Log.d(TAG, "Exception processing result callback", e);
        }
    }

    private void startProfiling(final TracingSession session)
            throws RuntimeException {
        // Parse config and post processing delay out of request first, if we can't get these
        // we can't start the trace.
        int postProcessingDelayMs;
        byte[] config;
        try {
            postProcessingDelayMs = session.getPostProcessingScheduleDelayMs();
            config = session.getConfigBytes();
        } catch (IllegalArgumentException e) {
            // Request couldn't be processed. This shouldn't happen.
            if (DEBUG) Log.d(TAG, "Request couldn't be processed", e);
            processResultCallback(session, ProfilingResult.ERROR_FAILED_INVALID_REQUEST,
                    e.getMessage());
            throw new RuntimeException(e);

        }

        // Now start the trace.
        session.setFileName(TRACE_FILE_PREFIX + session.getKey() + TRACE_FILE_SUFFIX);
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/perfetto", "-o",
                    TEMP_TRACE_PATH + session.getFileName(), "-c", "-", "--txt");
            Process activeTrace = pb.start();
            activeTrace.getOutputStream().write(config);
            activeTrace.getOutputStream().close();
            // If we made it this far the trace is running, save the session.
            session.setActiveTrace(activeTrace);
            mTracingSessions.put(session.getKey(), session);
        } catch (Exception e) {
            // Catch all exceptions related to starting process as they'll all be handled similarly.
            if (DEBUG) Log.d(TAG, "Trace couldn't be started", e);
            processResultCallback(session, ProfilingResult.ERROR_FAILED_EXECUTING, null);
            throw new RuntimeException(e);
        }

        // Create post process runnable, store it, and schedule it.
        session.setProcessResultRunnable(new Runnable() {
            @Override
            public void run() {
                // TODO: confirm perfetto is done and reschedule if not
                session.setProcessResultRunnable(null);
                processResult(session);
            }
        });
        getHandler().postDelayed(session.getProcessResultRunnable(), postProcessingDelayMs);
    }

    private void stopProfiling(String key) throws RuntimeException {
        TracingSession session = mTracingSessions.get(key);
        if (session == null || session.getActiveTrace() == null) {
            if (DEBUG) Log.d(TAG, "No active trace, nothing to stop.");
            return;
        }

        if (session.getProcessResultRunnable() == null) {
            if (DEBUG) Log.d(TAG,
                    "No runnable, it either stopped already or is in the process of stopping.");
            return;
        }

        // Remove the post processing runnable set with the default timeout. After stopping use the
        // same runnable to process immediately.
        getHandler().removeCallbacks(session.getProcessResultRunnable());

        // End the tracing session.
        session.getActiveTrace().destroyForcibly();
        try {
            if (!session.getActiveTrace().waitFor(PERFETTO_DESTROY_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                if (DEBUG) Log.d(TAG, "Stopping of running trace process timed out.");
                throw new RuntimeException("topping of running trace process timed out.");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // If we made it here the result is ready, now run the post processing runnable.
        getHandler().post(session.getProcessResultRunnable());
    }

    public boolean areAnyTracesRunning() throws RuntimeException {
        for (int i = 0; i < mTracingSessions.size(); i++) {
            if (isTraceRunning(mTracingSessions.keyAt(i))) {
                return true;
            }
        }
        return false;
    }

    public boolean isTraceRunning(String key) throws RuntimeException {
        TracingSession session = mTracingSessions.get(key);
        if (session == null || session.getActiveTrace() == null) {
            // No subprocess, nothing running.
            if (DEBUG) Log.d(TAG, "No subprocess, nothing running.");
            return false;
        } else if (session.getActiveTrace().isAlive()) {
            // Subprocess exists and is alive.
            if (DEBUG) Log.d(TAG, "Subprocess exists and is alive, trace is running.");
            return true;
        } else {
            // Subprocess exists but is not alive, nothing running. Clean up before returning.
            if (DEBUG) Log.d(TAG, "Subprocess exists but is not alive, nothing running.");
            stopProfiling(key);
            if (DEBUG) Log.d(TAG, "Non running process cleaned up.");
            return false;
        }
    }

    /**
     * Move the result file from temporary storage to the apps internal storage.
     *
     * This is done by requesting {@link ProfilingManager} create a file from within app context
     * and return a {@link ParcelFileDescriptor} to copy the temporary file contents to.
     * Finally, delete the temporary file.
     *
     * @return true if file was successfully copied and cleaned up, false if not.
     */
    private boolean moveFileToAppStorage(TracingSession session) {
        if (!mResultCallbacks.contains(session.getUid())) {
            // No callback, nowhere to notify with result.
            if (DEBUG) Log.d(TAG, "No callback to ProfilingManager, callback dropped.");
            // TODO: queue this and try next time the uid registers a receiver.
            // TODO: run a cleanup of old results based on a max size and time.
            return false;
        }

        // Setup file streams.
        File tempPerfettoFile = new File(TEMP_TRACE_PATH + session.getFileName());
        FileInputStream tempPerfettoFileInStream = null;
        FileOutputStream appFileOutStream = null;
        ParcelFileDescriptor pfd = null;
        boolean failed = false;
        try {
            tempPerfettoFileInStream = new FileInputStream(tempPerfettoFile);
        } catch (IOException e) {
            // IO Exception opening temp perfetto file. No result.
            if (DEBUG) Log.d(TAG, "Exception opening temp perfetto file.", e);
            failed = true;
        }
        if (!failed) {
            try {
                pfd = mResultCallbacks.get(session.getUid())
                    .generateFile(session.getAppFilePath() + TRACE_FILE_RELATIVE_PATH,
                        session.getFileName());
                appFileOutStream = new FileOutputStream(pfd.getFileDescriptor());
            } catch (RemoteException e) {
                // Binder exception getting file.
                if (DEBUG)
                    Log.d(TAG, "Binder exception getting file.", e);
                // TODO: queue this and try next time the uid registers a receiver.
                failed = true;
            }
        }

        // Now copy the file over.
        if (!failed) {
            try {
                FileUtils.copy(tempPerfettoFileInStream, appFileOutStream);
            } catch (IOException e) {
                // Exception writing to local app file.
                if (DEBUG)  Log.d(TAG, "Exception writing to local app file.", e);
                // TODO: queue this and try again later.
                failed = true;
            }
        }

        // Finally delete the temp file.
        if (!failed) {
            try {
                tempPerfettoFile.delete();
            } catch (SecurityException e) {
                // Exception deleting temp file.
                if (DEBUG) Log.d(TAG, "Permissions exception deleting temp file.", e);
            }
        }

        // Now cleanup.
        if (tempPerfettoFileInStream != null) {
            try {
                tempPerfettoFileInStream.close();
            } catch (IOException e) {
                if (DEBUG) Log.d(TAG, "Failed to close temp perfetto input stream.", e);
            }
        }
        if (pfd != null) {
            try {
                pfd.close();
            } catch (IOException e) {
                if (DEBUG) Log.d(TAG, "Failed to close app file output file FileDescriptor.", e);
            }
        }
        if (appFileOutStream != null) {
            try {
                appFileOutStream.close();
            } catch (IOException e) {
                if (DEBUG) Log.d(TAG, "Failed to close app file output file stream.", e);
            }
        }

        return !failed;
    }

    private void processResult(TracingSession session) {
        // todo: start redaction in its own process.
        boolean success = moveFileToAppStorage(session);
        if (success) {
            processResultCallback(session, ProfilingResult.ERROR_NONE, null);
            mTracingSessions.remove(session.getKey());
        } else {
            // Couldn't move file. File is still in temp directory and can be tried later.
            // TODO queue and try later when another listener is registered to this uid.
            if (DEBUG) Log.d(TAG, "Couldn't move file to app storage.");
            processResultCallback(session, ProfilingResult.ERROR_FAILED_POST_PROCESSING, null);
        }
    }

    private Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler(mHandlerThread.getLooper());
        }
        return mHandler;
    }

    private RateLimiter getRateLimiter() {
        if (mRateLimiter == null) {
            mRateLimiter = new RateLimiter(mContext);
        }
        return mRateLimiter;
    }

    public static final class Lifecycle extends SystemService {
        final ProfilingService mService;

        public Lifecycle(Context context) {
            this(context, new ProfilingService(context));
        }

        @VisibleForTesting
        public Lifecycle(Context context, ProfilingService service) {
            super(context);
            mService = service;
        }

        @Override
        public void onStart() {
            try {
                publishBinderService("profiling_service", mService);
            } catch (Exception e) {
                if (DEBUG) Log.d(TAG, "Failed to publish service", e);
            }
        }

        @Override
        public void onBootPhase(int phase) {
            super.onBootPhase(phase);
        }
    }
}
