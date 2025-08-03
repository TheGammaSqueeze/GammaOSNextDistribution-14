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

package android.os.profiling;

import android.os.ProfilingRequest;

import java.lang.IllegalArgumentException;

public final class Configs {

    static final String HEAP_PROFILE_ART = "heaps: \"com.android.art\"";

    static final String CONFIG_HEAP_PROFILE = "buffers {\n"
        + "  size_kb: 65536\n"
        + "}\n"
        + "\n"
        + "data_sources {\n"
        + "  config {\n"
        + "    name: \"android.heapprofd\"\n"
        + "    heapprofd_config {\n"
        + "      # 8Mb\n"
        + "      shmem_size_bytes: 8388608\n"
        + "      sampling_interval_bytes: {{sampling_interval}}\n"
        + "      process_cmdline: \"{{package_name}}\"\n"
        + "      {{art}}\n"
        + "    }\n"
        + "  }\n"
        + "}\n"
        + "\n"
        + "flush_timeout_ms: 30000\n"
        + "duration_ms: {{duration}}";
    static final String CONFIG_JAVA_HEAP_DUMP = "buffers {\n"
        + "  # This is the maximum size of the trace. The buffer will be mmap'd but, only\n"
        + "  # the non empty pages contribute to RSS.\n"
        + "  size_kb: 256000\n"
        + "  fill_policy: DISCARD\n"
        + "}\n"
        + "\n"
        + "data_sources {\n"
        + "  config {\n"
        + "    name: \"android.java_hprof\"\n"
        + "    java_hprof_config {\n"
        + "      process_cmdline: \"{{package_name}}\"\n"
        + "      dump_smaps: true\n"
        + "    }\n"
        + "  }\n"
        + "}\n"
        + "\n"
        + "# Wait 1s for the dump to start\n"
        + "duration_ms: 1000\n"
        + "# Wait up to 100s for the dump to finish\n"
        + "data_source_stop_timeout_ms: 100000";
    static final String CONFIG_STACK_SAMPLING = "buffers {\n"
        + "  size_kb: 65536\n"
        + "  fill_policy: DISCARD\n"
        + "}\n"
        + "\n"
        + "data_sources {\n"
        + "  config {\n"
        + "    name: \"linux.perf\"\n"
        + "    perf_event_config {\n"
        + "      timebase {\n"
        + "        counter: SW_CPU_CLOCK\n"
        + "        frequency: {{frequency}}\n"
        + "        timestamp_clock: PERF_CLOCK_MONOTONIC\n"
        + "      }\n"
        + "      callstack_sampling {\n"
        + "        scope {\n"
        + "          target_cmdline: \"{{package_name}}\"\n"
        + "        }\n"
        + "        # TODO: Do kernel frame disclose sensitive info?\n"
        + "        kernel_frames: true\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}\n"
        + "\n"
        + "flush_timeout_ms: 30000";
    static final String CONFIG_SYSTEM_TRACE = "buffers {\n"
        + "  size_kb: 32768\n"
        + "  fill_policy: RING_BUFFER\n"
        + "}\n"
        + "\n"
        + "data_sources {\n"
        + "  config {\n"
        + "    name: \"linux.process_stats\"\n"
        + "    target_buffer: 0\n"
        + "    process_stats_config {\n"
        + "      scan_all_processes_on_start: true\n"
        + "    }\n"
        + "  }\n"
        + "}\n"
        + "\n"
        + "data_sources {\n"
        + "  config {\n"
        + "    name: \"linux.ftrace\"\n"
        + "    target_buffer: 0\n"
        + "    ftrace_config {\n"
        + "      throttle_rss_stat: true\n"
        + "      disable_generic_events: true\n"
        + "      compact_sched {\n"
        + "        enabled: true\n"
        + "      }\n"
        + "\n"
        + "      # RSS and ION buffer events:\n"
        + "      ftrace_events: \"gpu_mem/gpu_mem_total\"\n"
        + "      ftrace_events: \"dmabuf_heap/dma_heap_stat\"\n"
        + "      ftrace_events: \"ion/ion_stat\"\n"
        + "      ftrace_events: \"kmem/ion_heap_grow\"\n"
        + "      ftrace_events: \"kmem/ion_heap_shrink\"\n"
        + "      ftrace_events: \"rss_stat\"\n"
        + "      ftrace_events: \"fastrpc/fastrpc_dma_stat\"\n"
        + "\n"
        + "      # Scheduling information & process tracking. Useful for:\n"
        + "      # - what is happening on each CPU at each moment\n"
        + "      # - why a thread was descheduled\n"
        + "      # - parent/child relationships between processes and threads.\n"
        + "      ftrace_events: \"power/suspend_resume\"\n"
        + "      ftrace_events: \"sched/sched_blocked_reason\"\n"
        + "      ftrace_events: \"sched/sched_process_free\"\n"
        + "      ftrace_events: \"sched/sched_switch\"\n"
        + "      ftrace_events: \"task/task_newtask\"\n"
        + "      ftrace_events: \"task/task_rename\"\n"
        + "\n"
        + "      # These two give us kernel wakelocks.\n"
        + "      ftrace_events: \"power/wakeup_source_activate\"\n"
        + "      ftrace_events: \"power/wakeup_source_deactivate\"\n"
        + "\n"
        + "      # Wakeup info. Allows you to compute how long a task was\n"
        + "      # blocked due to CPU contention.\n"
        + "      ftrace_events: \"sched/sched_waking\"\n"
        + "      ftrace_events: \"sched/sched_wakeup_new\"\n"
        + "\n"
        + "      # Workqueue events.\n"
        + "      ftrace_events: \"workqueue/workqueue_activate_work\"\n"
        + "      ftrace_events: \"workqueue/workqueue_execute_end\"\n"
        + "      ftrace_events: \"workqueue/workqueue_execute_start\"\n"
        + "      ftrace_events: \"workqueue/workqueue_queue_work\"\n"
        + "\n"
        + "      # vmscan and mm_compaction events.\n"
        + "      ftrace_events: \"vmscan/mm_vmscan_kswapd_wake\"\n"
        + "      ftrace_events: \"vmscan/mm_vmscan_kswapd_sleep\"\n"
        + "      ftrace_events: \"vmscan/mm_vmscan_direct_reclaim_begin\"\n"
        + "      ftrace_events: \"vmscan/mm_vmscan_direct_reclaim_end\"\n"
        + "      ftrace_events: \"compaction/mm_compaction_begin\"\n"
        + "      ftrace_events: \"compaction/mm_compaction_end\"\n"
        + "\n"
        + "      # CMA events.\n"
        + "      ftrace_events: \"cma/cma_alloc_start\"\n"
        + "      ftrace_events: \"cma/cma_alloc_info\"\n"
        + "\n"
        + "      # Atrace activity manager:\n"
        + "      atrace_categories: \"am\"\n"
        + "      # Atrace system_server:\n"
        + "      atrace_categories: \"ss\"\n"
        + "\n"
        + "      # Java and C:\n"
        + "      atrace_categories: \"dalvik\"\n"
        + "      atrace_categories: \"bionic\"\n"
        + "\n"
        + "      atrace_categories: \"binder_driver\"\n"
        + "\n"
        + "      atrace_apps: \"{{package_name}}\"\n"
        + "    }\n"
        + "  }\n"
        + "}\n"
        + "\n"
        + "data_sources {\n"
        + "  config {\n"
        + "    name: \"android.surfaceflinger.frametimeline\"\n"
        + "    target_buffer: 0\n"
        + "  }\n"
        + "}\n"
        + "duration_ms: {{duration}}";

    // Time to wait beyond trace timeout to ensure perfetto has time to finish writing output.
    private static final int FILE_PROCESSING_DELAY_MS = 5000;

    private static final int DEFAULT_HEAP_PROFILE_DURATION_MS = 2 * 60 * 1000;
    // 1 second duration + 100 seconds max wait for dump to finish.
    private static final int DEFAULT_JAVA_HEAP_DUMP_DURATION_MS = (1 + 100) * 1000;
    private static final int DEFAULT_STACK_SAMPLING_DURATION_MS = 60 * 1000;
    private static final int DEFAULT_TRACE_DURATION_MS = 5 * 60 * 1000;
    private static final long DEFAULT_HEAP_PROFILE_SAMPLING_INTERVAL = 4096;
    private static final int DEFAULT_STACK_SAMPLING_FREQUENCY = 100;
    private static final boolean DEFAULT_HEAP_PROFILE_ART = false;

    private static final int sDefaultTraceDurationMs;
    private static final long sDefaultHeapProfileSamplingInterval;
    private static final int sDefaultStackSamplingFrequency;
    private static final boolean sDefaultHeapProfileArt;

    static {
        sDefaultTraceDurationMs = DEFAULT_TRACE_DURATION_MS;
        sDefaultHeapProfileSamplingInterval = DEFAULT_HEAP_PROFILE_SAMPLING_INTERVAL;
        sDefaultStackSamplingFrequency = DEFAULT_STACK_SAMPLING_FREQUENCY;
        sDefaultHeapProfileArt = DEFAULT_HEAP_PROFILE_ART;
    }

    /** This method transforms a request into a useable config for perfetto. */
    public static String generateConfigForRequest(ProfilingRequest request, String packageName)
            throws IllegalArgumentException {
        if (!request.hasConfig()) {
            // Proto has no config, not requesting anything.
            throw new IllegalArgumentException("Proto config is missing");
        }

        ProfilingRequest.Config config = request.getConfig();
        String result = null;

        // Config can have at most one collection type, find out which and then process parameters.
        if (config.hasJavaHeapDump()) {
            result = CONFIG_JAVA_HEAP_DUMP;
        } else if (config.hasHeapProfile()) {
            ProfilingRequest.HeapProfile heapProfile = config.getHeapProfile();
            boolean art = heapProfile.hasArt() ? heapProfile.getArt() : sDefaultHeapProfileArt;
            long samplingIntervalBytes = heapProfile.hasSamplingIntervalBytes()
                    ? heapProfile.getSamplingIntervalBytes() : sDefaultHeapProfileSamplingInterval;
            result = CONFIG_HEAP_PROFILE
                    .replace("{{sampling_interval}}", String.valueOf(samplingIntervalBytes))
                    .replace("{{art}}", art ? HEAP_PROFILE_ART : "")
                    .replace("{{duration}}", String.valueOf(DEFAULT_HEAP_PROFILE_DURATION_MS));
        } else if (config.hasStackSampling()) {
            ProfilingRequest.StackSampling stackSampling = config.getStackSampling();
            int frequency = stackSampling.hasFrequency()
                    ? stackSampling.getFrequency() : sDefaultStackSamplingFrequency;
            result = CONFIG_STACK_SAMPLING.replace("{{frequency}}", String.valueOf(frequency));
        } else if (config.hasSystemTrace()) {
            ProfilingRequest.SystemTrace systemTrace = config.getSystemTrace();
            int durationMs = systemTrace.hasDurationMs() ? systemTrace.getDurationMs()
                    : sDefaultTraceDurationMs;
            result = CONFIG_SYSTEM_TRACE.replace("{{duration}}",
                    String.valueOf(durationMs));
            // TODO: remove when redaction is hooked up b/327423523
            throw new IllegalArgumentException("Trace is not supported until redaction lands");
        }

        if (result == null) {
            // Proto config has no type, we don't know what the app wants.
            throw new IllegalArgumentException("Proto config type is missing");
        }

        // Fill in package name and return config.
        return result.replace("{{package_name}}", packageName);
    }

    /**
     * This method returns how long in ms to wait before post processing and cleaning up the result
     * in the event that it's not stopped manually.
     */
    public static int getPostProcessingScheduleDelayMs(ProfilingRequest request) {
        // TODO: b/327660454 adjust timeout/logic to ensure perfetto is finished
        if (!request.hasConfig()) {
            // Proto has no config, not requesting anything.
            throw new IllegalArgumentException("Proto config is missing");
        }

        ProfilingRequest.Config config = request.getConfig();

        // Config can have at most one collection type, find out which and then determine time.
        if (config.hasJavaHeapDump()) {
            return DEFAULT_JAVA_HEAP_DUMP_DURATION_MS + FILE_PROCESSING_DELAY_MS;
        } else if (config.hasHeapProfile()) {
            return DEFAULT_HEAP_PROFILE_DURATION_MS + FILE_PROCESSING_DELAY_MS;
        } else if (config.hasStackSampling()) {
            return DEFAULT_STACK_SAMPLING_DURATION_MS + FILE_PROCESSING_DELAY_MS;
        } else if (config.hasSystemTrace()) {
            ProfilingRequest.SystemTrace systemTrace = config.getSystemTrace();
            return (systemTrace.hasDurationMs() ? systemTrace.getDurationMs()
                    : sDefaultTraceDurationMs) + FILE_PROCESSING_DELAY_MS;
        }

        // Proto config has no type, we don't know what the app wants.
        throw new IllegalArgumentException("Proto config type is missing");
    }
}
