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
package com.android.tradefed.invoker.tracing;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.invoker.logger.InvocationMetricLogger;
import com.android.tradefed.invoker.logger.InvocationMetricLogger.InvocationMetricKey;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import perfetto.protos.PerfettoTrace.DebugAnnotation;
import perfetto.protos.PerfettoTrace.ProcessDescriptor;
import perfetto.protos.PerfettoTrace.ThreadDescriptor;
import perfetto.protos.PerfettoTrace.Trace;
import perfetto.protos.PerfettoTrace.TracePacket;
import perfetto.protos.PerfettoTrace.TrackDescriptor;
import perfetto.protos.PerfettoTrace.TrackEvent;

/** Main class helping to describe and manage an active trace. */
public class ActiveTrace {

    public static final String TRACE_KEY = "invocation-trace";
    private final long pid;
    private final long tid;
    private final long traceUuid;
    private final int uid = 5555; // TODO: collect a real uid
    private final boolean mainTradefedProcess;
    private final Map<String, Long> mThreadToTracker;
    // File where the final trace gets outputed
    private File mTraceOutput;
    
    public ActiveTrace(long pid, long tid) {
        this(pid, tid, false);
    }

    /**
     * Constructor.
     *
     * @param pid Current process id
     * @param tid Current thread id
     */
    public ActiveTrace(long pid, long tid, boolean mainProcess) {
        this.pid = pid;
        this.tid = tid;
        this.traceUuid = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        mThreadToTracker = new HashMap<>();
        mainTradefedProcess = mainProcess;
    }

    /** Start the tracing and report the metadata of the trace. */
    public void startTracing(boolean isSubprocess) {
        if (mTraceOutput != null) {
            throw new IllegalStateException("Tracing was already started.");
        }
        try {
            mTraceOutput = FileUtil.createTempFile(TRACE_KEY, ".perfetto-trace");
        } catch (IOException e) {
            CLog.e(e);
        }
        // Initialize all the trace metadata
        createMainInvocationTracker((int) pid, (int) tid, traceUuid, isSubprocess);
    }

    /** Provide the trace file from a subprocess to be added to the parent. */
    public void addSubprocessTrace(File subTrace) {
        if (mTraceOutput == null) {
            return;
        }

        try (FileInputStream stream = new FileInputStream(subTrace)) {
            try (GZIPInputStream gzip = new GZIPInputStream(stream)) {
                CLog.logAndDisplay(LogLevel.DEBUG, "merging with gzipped %s", subTrace);
                FileUtil.writeToFile(gzip, mTraceOutput, true);
                return;
            } catch (IOException e) {
                CLog.logAndDisplay(LogLevel.DEBUG, "%s isn't gzip.", subTrace);
            }
        } catch (IOException e) {
            CLog.e(e);
        }

        try (FileInputStream stream = new FileInputStream(subTrace)) {
            FileUtil.writeToFile(stream, mTraceOutput, true);
        } catch (IOException e) {
            CLog.e(e);
        }
    }

    public void reportTraceEvent(String categories, String name, TrackEvent.Type type) {
        reportTraceEvent(categories, name, (int) tid, null, type);
    }

    /**
     * thread id of the thread that initiated the tracing.
     */
    public long reportingThreadId() {
        return tid;
    }

    public boolean isMainTradefedProcess() {
        return mainTradefedProcess;
    }

    /**
     * Very basic event reporting to do START / END of traces.
     *
     * @param categories Category associated with event
     * @param name Event name
     * @param type Type of the event being reported
     */
    public void reportTraceEvent(
            String categories, String name, int threadId, String threadName, TrackEvent.Type type) {
        long traceIdentifier = traceUuid;
        if (threadId != this.tid) {
            synchronized (mThreadToTracker) {
                if (mThreadToTracker.containsKey(Integer.toString(threadId))) {
                    Long returnedValue = mThreadToTracker.get(Integer.toString(threadId));
                    if (returnedValue == null) {
                        CLog.e("Consistency error in trace identifier.");
                        InvocationMetricLogger.addInvocationMetrics(
                                InvocationMetricKey.TRACE_INTERNAL_ERROR, 1);
                        return;
                    }
                    traceIdentifier = returnedValue;
                } else {
                    traceIdentifier = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
                    createThreadTracker((int) pid, threadId, threadName, traceIdentifier);
                    mThreadToTracker.put(Integer.toString(threadId), Long.valueOf(traceIdentifier));
                }
            }
        }
        TracePacket.Builder tracePacket =
                TracePacket.newBuilder()
                        .setTrustedUid(uid)
                        .setTrustedPid((int) pid)
                        .setTimestamp(System.nanoTime())
                        .setTrustedPacketSequenceId(1)
                        .setSequenceFlags(1)
                        .setProcessDescriptor(ProcessDescriptor.newBuilder().setPid((int) pid))
                        .setThreadDescriptor(ThreadDescriptor.newBuilder().setTid(threadId))
                        .setTrackEvent(
                                TrackEvent.newBuilder()
                                        .setTrackUuid(traceIdentifier)
                                        .setName(name)
                                        .setType(type)
                                        .addCategories(categories)
                                        .addDebugAnnotations(
                                                DebugAnnotation.newBuilder().setName(name)));
        writeToTrace(tracePacket.build());
    }

    /** Reports the final trace files and clean up resources as needed. */
    public File finalizeTracing() {
        CLog.logAndDisplay(LogLevel.DEBUG, "Finalizing trace: %s", mTraceOutput);
        File trace = mTraceOutput;
        mTraceOutput = null;
        return trace;
    }

    private String createProcessName(boolean isSubprocess) {
        if (isSubprocess) {
            return "subprocess-test-invocation";
        }
        if (isMainTradefedProcess()) {
            return "Tradefed";
        }
        return "test-invocation";
    }

    private void createMainInvocationTracker(
            int pid, int tid, long traceUuid, boolean isSubprocess) {
        TrackDescriptor.Builder descriptor =
                TrackDescriptor.newBuilder()
                        .setUuid(traceUuid)
                        .setName(createProcessName(isSubprocess))
                        .setThread(
                                ThreadDescriptor.newBuilder()
                                        .setTid(tid)
                                        .setThreadName("invocation-thread")
                                        .setPid(pid))
                        .setProcess(
                                ProcessDescriptor.newBuilder()
                                        .setPid(pid)
                                        .setProcessName(createProcessName(isSubprocess)));

        TracePacket.Builder traceTrackDescriptor =
                TracePacket.newBuilder()
                        .setTrustedUid(uid)
                        .setTimestamp(System.nanoTime())
                        .setTrustedPacketSequenceId(1)
                        .setSequenceFlags(1)
                        .setTrustedPid(pid)
                        .setTrackDescriptor(descriptor.build());

        writeToTrace(traceTrackDescriptor.build());
    }

    private void createThreadTracker(int pid, int tid, String threadName, long traceUuid) {
        TrackDescriptor.Builder descriptor =
                TrackDescriptor.newBuilder()
                        .setUuid(traceUuid)
                        .setThread(
                                ThreadDescriptor.newBuilder()
                                        .setTid(tid)
                                        .setThreadName(threadName)
                                        .setPid(pid));

        TracePacket.Builder traceTrackDescriptor =
                TracePacket.newBuilder()
                        .setTrustedUid(uid)
                        .setTimestamp(System.nanoTime())
                        .setTrustedPacketSequenceId(1)
                        .setSequenceFlags(1)
                        .setTrustedPid(pid)
                        .setTrackDescriptor(descriptor.build());

        writeToTrace(traceTrackDescriptor.build());
    }

    private synchronized void writeToTrace(TracePacket packet) {
        if (mTraceOutput == null) {
            return;
        }
        // Perfetto UI supports repeated Trace
        Trace wrappingTrace = Trace.newBuilder().addPacket(packet).build();
        try (FileOutputStream out = new FileOutputStream(mTraceOutput, true)) {
            wrappingTrace.writeTo(out);
            out.flush();
        } catch (IOException e) {
            CLog.e("Failed to write execution trace to file.");
            CLog.e(e);
        }
    }
}
