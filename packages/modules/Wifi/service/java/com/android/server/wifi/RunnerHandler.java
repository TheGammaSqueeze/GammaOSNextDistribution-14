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

package com.android.server.wifi;

import static com.android.server.wifi.proto.WifiStatsLog.WIFI_THREAD_TASK_EXECUTED;

import android.annotation.NonNull;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.Trace;
import android.util.LocalLog;

import com.android.modules.utils.HandlerExecutor;
import com.android.server.wifi.proto.WifiStatsLog;

import java.util.HashSet;
import java.util.Set;

/**
 * RunnerHandler tracks all the Runnable jobs posted to the handler for the running time and
 * monitor if the running time exceeds the expected threshold.
 *
 */
public class RunnerHandler extends Handler {
    private static final String TAG = "WifiThreadRunner";

    private static final String KEY_SIGNATURE = "KEY_RUNNER_HANDLER_SIGNATURE";
    private static final String KEY_WHEN = "KEY_RUNNER_HANDLER_WHEN";
    private static final int METRICS_THRESHOLD_MILLIS = 100;

    private final int mRunningTimeThresholdInMilliseconds;
    private Set<String> mIgnoredClasses = new HashSet<>();
    private Set<String> mIgnoredMethods = new HashSet<>();

    // TODO: b/246623192 Add Wifi metric for Runner state overruns.
    private final LocalLog mLocalLog;

    /**
     * The Runner handler Constructor
     *
     * @param looper looper for the handler
     * @param threshold the running time threshold in milliseconds
     */
    public RunnerHandler(Looper looper, int threshold, @NonNull LocalLog localLog) {
        super(looper);
        mRunningTimeThresholdInMilliseconds = threshold;
        mLocalLog = localLog;
        mIgnoredClasses.add(WifiThreadRunner.class.getName());
        mIgnoredClasses.add(WifiThreadRunner.class.getName() + "$BlockingRunnable");
        mIgnoredClasses.add(RunnerHandler.class.getName());
        mIgnoredClasses.add(HandlerExecutor.class.getName());
        mIgnoredClasses.add(Handler.class.getName());
        mIgnoredClasses.add(HandlerThread.class.getName());
        mIgnoredClasses.add(Looper.class.getName());
        mIgnoredMethods.add("handleMessage");
    }

    private String getSignature(StackTraceElement[] elements, Runnable callback) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement e : elements) {
            // Go through the stack elements to find out the caller who schedule the job.
            // Ignore the stack frames generated with ignored classes and methods, until the stack
            // frame where the runnable job is posted to the handler.
            if (!mIgnoredClasses.contains(e.getClassName()) && !mIgnoredMethods.contains(
                    e.getMethodName())) {
                String[] nameArr = e.getClassName().split("\\.", 5);
                final int len = nameArr.length;
                if (len > 0) {
                    sb.append(nameArr[len - 1]).append("#").append(e.getMethodName());
                    break;
                }
            }
            // The callback is the lambada function posted as Runnable#run function.
            // If we can't identify the caller from the stack trace, then we will use the symbol
            // of the lambada function as the signature of the caller.
            if (HandlerThread.class.getName().equals(e.getClassName())) {
                sb.append(callback);
                break;
            }
        }
        return sb.length() == 0 ? "<UNKNOWN>" : sb.toString();
    }

    @Override
    public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
        String signature = getSignature(new Throwable("RunnerHandler:").getStackTrace(),
                msg.getCallback());
        Bundle bundle = msg.getData();
        bundle.putString(KEY_SIGNATURE, signature);
        return super.sendMessageAtTime(msg, uptimeMillis);
    }

    @Override
    public void dispatchMessage(@NonNull Message msg) {
        final Bundle bundle = msg.getData();
        final String signature = bundle.getString(KEY_SIGNATURE);
        if (signature != null) {
            Trace.traceBegin(Trace.TRACE_TAG_NETWORK, signature);
        }
        // The message sent to front of the queue has when=0, get from the bundle in that case.
        final long when = msg.getWhen() != 0 ? msg.getWhen() : bundle.getLong(KEY_WHEN);
        final long start = SystemClock.uptimeMillis();
        final long scheduleLatency = start - when;
        super.dispatchMessage(msg);
        if (signature != null) {
            Trace.traceEnd(Trace.TRACE_TAG_NETWORK);
        }
        final long runTime = SystemClock.uptimeMillis() - start;
        final String signatureToLog = signature != null ? signature : "unknown";
        if (runTime > mRunningTimeThresholdInMilliseconds) {
            mLocalLog.log(signatureToLog + " was running for " + runTime);
        }
        if (scheduleLatency > WifiThreadRunner.getScissorsTimeoutThreshold()) {
            mLocalLog.log(signatureToLog + " schedule latency " + scheduleLatency + " ms");
        }
        if (runTime > METRICS_THRESHOLD_MILLIS || scheduleLatency > METRICS_THRESHOLD_MILLIS) {
            WifiStatsLog.write(
                    WIFI_THREAD_TASK_EXECUTED,
                    (int) runTime,
                    (int) scheduleLatency,
                    signatureToLog);
        }
    }

    /**
     * Use this helper function rather than directly calling Handler#postAtFrontOfQueue, which does
     * not call sendMessageAtTime and set the signature. This function will set the signature
     * before enqueueing the message to front of the queue.
     * @param r runnable to be queued to the front
     * @return true when success
     */
    public final boolean postToFront(@NonNull Runnable r) {
        Message msg = Message.obtain(this, r);
        String signature = getSignature(new Throwable("RunnerHandler:").getStackTrace(),
                msg.getCallback());
        Bundle bundle = msg.getData();
        bundle.putString(KEY_SIGNATURE, signature);
        bundle.putLong(KEY_WHEN, SystemClock.uptimeMillis());
        return sendMessageAtFrontOfQueue(msg);
    }
}
