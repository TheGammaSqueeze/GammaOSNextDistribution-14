/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tradefed.clearcut;

import com.android.annotations.VisibleForTesting;
import com.android.asuite.clearcut.Clientanalytics.ClientInfo;
import com.android.asuite.clearcut.Clientanalytics.LogEvent;
import com.android.asuite.clearcut.Clientanalytics.LogRequest;
import com.android.asuite.clearcut.Clientanalytics.LogResponse;
import com.android.asuite.clearcut.Common.UserType;
import com.android.tradefed.invoker.tracing.CloseableTraceScope;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.net.HttpHelper;

import com.google.common.base.Strings;
import com.google.protobuf.util.JsonFormat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Client that allows reporting usage metrics to clearcut. */
public class ClearcutClient {

    public static final String DISABLE_CLEARCUT_KEY = "DISABLE_CLEARCUT";
    private static final String CLEARCUT_SUB_TOOL_NAME = "CLEARCUT_SUB_TOOL_NAME";

    private static final String CLEARCUT_PROD_URL = "https://play.googleapis.com/log";
    private static final int CLIENT_TYPE = 1;
    private static final int INTERNAL_LOG_SOURCE = 971;
    private static final int EXTERNAL_LOG_SOURCE = 934;

    private static final long SCHEDULER_INITIAL_DELAY_MILLISECONDS = 1000;
    private static final long SCHEDULER_PERDIOC_MILLISECONDS = 250;

    private static final String GOOGLE_EMAIL = "@google.com";
    private static final String GOOGLE_HOSTNAME = ".google.com";

    private File mCachedUuidFile = new File(System.getProperty("user.home"), ".tradefed");
    private String mRunId;
    private long mSessionStartTime = 0L;

    private final int mLogSource;
    private final String mUrl;
    private final UserType mUserType;
    private final String mSubToolName;

    // Consider synchronized list
    private List<LogRequest> mExternalEventQueue;
    // The pool executor to actually post the metrics
    private ScheduledThreadPoolExecutor mExecutor;
    // Whether the clearcut client should be inop
    private boolean mDisabled = false;

    public ClearcutClient(String subToolName) {
        this(null, subToolName);
    }

    /**
     * Create Client with customized posting URL and forcing whether it's internal or external user.
     */
    @VisibleForTesting
    protected ClearcutClient(String url, String subToolName) {
        mDisabled = isClearcutDisabled();

        // We still have to set the 'final' variable so go through the assignments before returning
        if (!mDisabled && isGoogleUser()) {
            mLogSource = INTERNAL_LOG_SOURCE;
            mUserType = UserType.GOOGLE;
        } else {
            mLogSource = EXTERNAL_LOG_SOURCE;
            mUserType = UserType.EXTERNAL;
        }
        if (url == null) {
            mUrl = CLEARCUT_PROD_URL;
        } else {
            mUrl = url;
        }
        mRunId = UUID.randomUUID().toString();
        mExternalEventQueue = new ArrayList<>();
        if (Strings.isNullOrEmpty(subToolName) && System.getenv(CLEARCUT_SUB_TOOL_NAME) != null) {
            mSubToolName = System.getenv(CLEARCUT_SUB_TOOL_NAME);
        } else {
            mSubToolName = subToolName;
        }

        if (mDisabled) {
            return;
        }

        // Print the notice
        System.out.println(NoticeMessageUtil.getNoticeMessage(mUserType));

        // Executor to actually send the events.
        mExecutor =
                new ScheduledThreadPoolExecutor(
                        1,
                        new ThreadFactory() {
                            @Override
                            public Thread newThread(Runnable r) {
                                Thread t = Executors.defaultThreadFactory().newThread(r);
                                t.setDaemon(true);
                                t.setName("clearcut-client-thread");
                                return t;
                            }
                        });
        Runnable command =
                new Runnable() {
                    @Override
                    public void run() {
                        flushEvents();
                    }
                };
        mExecutor.scheduleAtFixedRate(
                command,
                SCHEDULER_INITIAL_DELAY_MILLISECONDS,
                SCHEDULER_PERDIOC_MILLISECONDS,
                TimeUnit.MILLISECONDS);
    }

    /** Send the first event to notify that Tradefed was started. */
    public void notifyTradefedStartEvent() {
        if (mDisabled) {
            return;
        }
        mSessionStartTime = System.nanoTime();
        long eventTimeMs = System.currentTimeMillis();
        CompletableFuture.supplyAsync(() -> createStartEvent(eventTimeMs));
    }

    private boolean createStartEvent(long eventTimeMs) {
        LogRequest.Builder request = createBaseLogRequest();
        LogEvent.Builder logEvent = LogEvent.newBuilder();
        logEvent.setEventTimeMs(eventTimeMs);
        logEvent.setSourceExtension(
                ClearcutEventHelper.createStartEvent(
                        getGroupingKey(), mRunId, mUserType, mSubToolName));
        request.addLogEvent(logEvent);
        queueEvent(request.build());
        return true;
    }

    /** Send the last event to notify that Tradefed is done. */
    public void notifyTradefedFinishedEvent() {
        if (mDisabled) {
            return;
        }
        Duration duration = java.time.Duration.ofNanos(System.nanoTime() - mSessionStartTime);
        LogRequest.Builder request = createBaseLogRequest();
        LogEvent.Builder logEvent = LogEvent.newBuilder();
        logEvent.setEventTimeMs(System.currentTimeMillis());
        logEvent.setSourceExtension(
                ClearcutEventHelper.createFinishedEvent(
                        getGroupingKey(), mRunId, mUserType, mSubToolName, duration));
        request.addLogEvent(logEvent);
        queueEvent(request.build());
    }

    /** Send the event to notify that a Tradefed invocation was started. */
    public void notifyTradefedInvocationStartEvent() {
        if (mDisabled) {
            return;
        }
        LogRequest.Builder request = createBaseLogRequest();
        LogEvent.Builder logEvent = LogEvent.newBuilder();
        logEvent.setEventTimeMs(System.currentTimeMillis());
        logEvent.setSourceExtension(
                ClearcutEventHelper.createRunStartEvent(
                        getGroupingKey(), mRunId, mUserType, mSubToolName));
        request.addLogEvent(logEvent);
        queueEvent(request.build());
    }

    /** Send the event to notify that a test run finished. */
    public void notifyTestRunFinished(long startTimeNano) {
        if (mDisabled) {
            return;
        }
        Duration duration = java.time.Duration.ofNanos(System.nanoTime() - startTimeNano);
        LogRequest.Builder request = createBaseLogRequest();
        LogEvent.Builder logEvent = LogEvent.newBuilder();
        logEvent.setEventTimeMs(System.currentTimeMillis());
        logEvent.setSourceExtension(
                ClearcutEventHelper.creatRunTestFinished(
                        getGroupingKey(), mRunId, mUserType, mSubToolName, duration));
        request.addLogEvent(logEvent);
        queueEvent(request.build());
    }

    /** Stop the periodic sending of clearcut events */
    public void stop() {
        if (mExecutor != null) {
            mExecutor.setRemoveOnCancelPolicy(true);
            mExecutor.shutdown();
            mExecutor = null;
        }
        // Send all remaining events
        flushEvents();
    }

    /** Add an event to the queue of events that needs to be send. */
    public void queueEvent(LogRequest event) {
        synchronized (mExternalEventQueue) {
            mExternalEventQueue.add(event);
        }
    }

    /** Returns the current queue size. */
    public final int getQueueSize() {
        synchronized (mExternalEventQueue) {
            return mExternalEventQueue.size();
        }
    }

    /** Allows to override the default cached uuid file. */
    public void setCachedUuidFile(File uuidFile) {
        mCachedUuidFile = uuidFile;
    }

    /** Get a new or the cached uuid for the user. */
    @VisibleForTesting
    String getGroupingKey() {
        String uuid = null;
        if (mCachedUuidFile.exists()) {
            try {
                uuid = FileUtil.readStringFromFile(mCachedUuidFile);
            } catch (IOException e) {
                CLog.e(e);
            }
        }
        if (uuid == null || uuid.isEmpty()) {
            uuid = UUID.randomUUID().toString();
            try {
                FileUtil.writeToFile(uuid, mCachedUuidFile);
            } catch (IOException e) {
                CLog.e(e);
            }
        }
        return uuid;
    }

    /** Returns True if clearcut is disabled, False otherwise. */
    @VisibleForTesting
    public boolean isClearcutDisabled() {
        return "1".equals(System.getenv(DISABLE_CLEARCUT_KEY));
    }

    /** Returns True if the user is a Googler, False otherwise. */
    @VisibleForTesting
    boolean isGoogleUser() {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            if (hostname.contains(GOOGLE_HOSTNAME)) {
                return true;
            }
        } catch (UnknownHostException e) {
            // Ignore
        }
        CommandResult gitRes =
                RunUtil.getDefault()
                        .runTimedCmdSilently(60000L, "git", "config", "--get", "user.email");
        if (CommandStatus.SUCCESS.equals(gitRes.getStatus())) {
            String stdout = gitRes.getStdout();
            if (stdout != null && stdout.trim().endsWith(GOOGLE_EMAIL)) {
                return true;
            }
        }

        return false;
    }

    private LogRequest.Builder createBaseLogRequest() {
        LogRequest.Builder request = LogRequest.newBuilder();
        request.setLogSource(mLogSource);
        request.setClientInfo(ClientInfo.newBuilder().setClientType(CLIENT_TYPE));
        return request;
    }

    private void flushEvents() {
        List<LogRequest> copy = new ArrayList<>();
        synchronized (mExternalEventQueue) {
            copy.addAll(mExternalEventQueue);
            mExternalEventQueue.clear();
        }
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        while (!copy.isEmpty()) {
            LogRequest event = copy.remove(0);
            futures.add(CompletableFuture.supplyAsync(() -> sendToClearcut(event)));
        }

        for (CompletableFuture<Boolean> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                CLog.e(e);
            }
        }
    }

    /** Send one event to the configured server. */
    private boolean sendToClearcut(LogRequest event) {
        HttpHelper helper = new HttpHelper();

        InputStream inputStream = null;
        InputStream errorStream = null;
        OutputStream outputStream = null;
        OutputStreamWriter outputStreamWriter = null;
        try (CloseableTraceScope ignored = new CloseableTraceScope("sendToClearcut")) {
            HttpURLConnection connection = helper.createConnection(new URL(mUrl), "POST", "text");
            outputStream = connection.getOutputStream();
            outputStreamWriter = new OutputStreamWriter(outputStream);

            String jsonObject = JsonFormat.printer().preservingProtoFieldNames().print(event);
            outputStreamWriter.write(jsonObject.toString());
            outputStreamWriter.flush();

            inputStream = connection.getInputStream();
            LogResponse response = LogResponse.parseFrom(inputStream);

            errorStream = connection.getErrorStream();
            if (errorStream != null) {
                String message = StreamUtil.getStringFromStream(errorStream);
                CLog.e("Error posting clearcut event: '%s'. LogResponse: '%s'", message, response);
            }
        } catch (IOException e) {
            CLog.e(e);
        } finally {
            StreamUtil.close(outputStream);
            StreamUtil.close(inputStream);
            StreamUtil.close(outputStreamWriter);
            StreamUtil.close(errorStream);
        }
        return true;
    }
}
