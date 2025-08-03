/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tradefed.result.proto;

import com.android.tradefed.config.Option;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.proto.TestRecordProto.TestRecord;
import com.android.tradefed.util.RunUtil;
import com.android.tradefed.util.StreamUtil;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** An implementation of {@link ProtoResultReporter} */
public final class StreamProtoResultReporter extends ProtoResultReporter {

    public static final String PROTO_REPORT_PORT_OPTION = "proto-report-port";

    @Option(
        name = PROTO_REPORT_PORT_OPTION,
        description = "the port where to connect to send the protos."
    )
    private Integer mReportPort = null;

    private Socket mReportSocket = null;
    private boolean mPrintedMessage = false;

    private ResultWriterThread mResultWriterThread;
    private ConcurrentLinkedQueue<TestRecord> mToBeSent = new ConcurrentLinkedQueue<>();

    public StreamProtoResultReporter() {
        setInlineRecordOfChildren(false);
    }

    public void setProtoReportPort(Integer portValue) {
        mReportPort = portValue;
    }

    public Integer getProtoReportPort() {
        return mReportPort;
    }

    @Override
    public void processStartInvocation(
            TestRecord invocationStartRecord, IInvocationContext context) {
        mResultWriterThread = new ResultWriterThread();
        mResultWriterThread.start();
        mToBeSent.add(invocationStartRecord);
    }

    @Override
    public void processTestModuleStarted(TestRecord moduleStartRecord) {
        mToBeSent.add(moduleStartRecord);
    }

    @Override
    public void processTestModuleEnd(TestRecord moduleRecord) {
        mToBeSent.add(moduleRecord);
    }

    @Override
    public void processTestRunStarted(TestRecord runStartedRecord) {
        mToBeSent.add(runStartedRecord);
    }

    @Override
    public void processTestRunEnded(TestRecord runRecord, boolean moduleInProgress) {
        mToBeSent.add(runRecord);
    }

    @Override
    public void processTestCaseStarted(TestRecord testCaseStartedRecord) {
        mToBeSent.add(testCaseStartedRecord);
    }

    @Override
    public void processTestCaseEnded(TestRecord testCaseRecord) {
        mToBeSent.add(testCaseRecord);
    }

    @Override
    public void processFinalInvocationLogs(TestRecord invocationLogs) {
        if (mResultWriterThread.mCancelled.get()) {
            writeRecordToSocket(invocationLogs);
        } else {
            mToBeSent.add(invocationLogs);
        }
    }

    @Override
    public void processFinalProto(TestRecord finalRecord) {
        try {
            if (mResultWriterThread.mCancelled.get()) {
                writeRecordToSocket(finalRecord);
            } else {
                mToBeSent.add(finalRecord);
            }
        } finally {
            // Upon invocation ended, trigger the end of the socket when the process finishes
            SocketFinisher thread = new SocketFinisher();
            Runtime.getRuntime().addShutdownHook(thread);
            mResultWriterThread.mCancelled.set(true);
            try {
                mResultWriterThread.join();
            } catch (InterruptedException e) {
                CLog.e(e);
            }
        }
    }

    protected void closeSocket() {
        StreamUtil.close(mReportSocket);
    }

    private void writeRecordToSocket(TestRecord record) {
        if (mReportPort == null) {
            if (!mPrintedMessage) {
                CLog.d("No port set. Skipping the reporter.");
                mPrintedMessage = true;
            }
            return;
        }
        try {
            if (mReportSocket == null) {
                mReportSocket = new Socket("localhost", mReportPort);
            }
            record.writeDelimitedTo(mReportSocket.getOutputStream());
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    /** Threads that help terminating the socket. */
    private class SocketFinisher extends Thread {

        public SocketFinisher() {
            super();
            setName("StreamProtoResultReporter-socket-finisher");
        }

        @Override
        public void run() {
            closeSocket();
        }
    }

    /** Send events from the event queue */
    private class ResultWriterThread extends Thread {

        private AtomicBoolean mCancelled = new AtomicBoolean(false);

        public ResultWriterThread() {
            super();
            setName("ResultWriterThread");
        }

        @Override
        public void run() {
            while (!mCancelled.get()) {
                flushEvents();
                if (!mCancelled.get()) {
                    RunUtil.getDefault().sleep(1000);
                }
            }
            // Flush remaining events if any
            flushEvents();
        }

        public void flushEvents() {
            TestRecord record = mToBeSent.poll();
            while (record != null) {
                writeRecordToSocket(record);
                record = mToBeSent.poll();
            }
        }
    }
}
