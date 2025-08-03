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

package com.google.android.car.kitchensink.telemetry;

import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.ACTIVITY_FOREGROUND_STATE_CHANGED;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.ANR_OCCURRED;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.APP_CRASH_OCCURRED;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.APP_START_MEMORY_STATE_CAPTURED;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.PROCESS_CPU_TIME;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.PROCESS_MEMORY_SNAPSHOT;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.PROCESS_MEMORY_STATE;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.PROCESS_START_TIME;
import static android.car.telemetry.TelemetryProto.StatsPublisher.SystemMetric.WTF_OCCURRED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RawRes;
import android.app.ActivityManager;
import android.car.VehiclePropertyIds;
import android.car.telemetry.CarTelemetryManager;
import android.car.telemetry.TelemetryProto;
import android.car.telemetry.TelemetryProto.ConnectivityPublisher;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.KitchenSinkActivity;
import com.google.android.car.kitchensink.R;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CarTelemetryTestFragment extends Fragment {
    private static final String TAG = CarTelemetryTestFragment.class.getSimpleName();

    private static final int SCRIPT_EXECUTION_PRIORITY_HIGH = 0;
    private static final int SCRIPT_EXECUTION_PRIORITY_LOW = 100;

    /** Vehicle property via gear change section. */
    private static final String LUA_SCRIPT_ON_GEAR_CHANGE =
            "function onGearChange(published_data, state)\n"
                    + "    t = {}\n"
                    + "    for k, v in ipairs(published_data) do\n"
                    + "        t['#' .. k] = 'Gear: ' .. v['vp.intVal'] \n"
                    + "        log(v[\"vp.intVal\"])\n"
                    + "    end\n"
                    + "    on_metrics_report(t)\n"
                    + "end\n";

    private static final TelemetryProto.Publisher VEHICLE_PROPERTY_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setVehicleProperty(
                            TelemetryProto.VehiclePropertyPublisher.newBuilder()
                                    .setVehiclePropertyId(VehiclePropertyIds.GEAR_SELECTION)
                                    .setReadRate(0f)
                                    .build())
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_ON_GEAR_CHANGE_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("my_metrics_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_GEAR_CHANGE)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onGearChange")
                                    .setPublisher(VEHICLE_PROPERTY_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String ON_GEAR_CHANGE_CONFIG_NAME =
            METRICS_CONFIG_ON_GEAR_CHANGE_V1.getName();

    /** ProcessMemoryState section. */
    private static final String LUA_SCRIPT_ON_PROCESS_MEMORY_STATE = new StringBuilder()
            .append("function calculateAverage(tbl)\n")
            .append("    local sum = 0\n")
            .append("    local size = 0\n")
            .append("    for _, value in ipairs(tbl) do\n")
            .append("        sum = sum + value\n")
            .append("        size = size + 1\n")
            .append("    end\n")
            .append("    return sum/size\n")
            .append("end\n")
            .append("function onProcessMemory(published_data, state)\n")
            .append("    local result = {}\n")
            .append("    result.page_fault_avg = calculateAverage("
                    + "published_data['stats.page_fault'])\n")
            .append("    result.major_page_fault_avg = calculateAverage("
                    + "published_data['stats.page_major_fault'])\n")
            .append("    result.oom_adj_score_avg = calculateAverage("
                    + "published_data['stats.oom_adj_score'])\n")
            .append("    result.rss_in_bytes_avg = calculateAverage("
                    + "published_data['stats.rss_in_bytes'])\n")
            .append("    result.swap_in_bytes_avg = calculateAverage("
                    + "published_data['stats.swap_in_bytes'])\n")
            .append("    result.cache_in_bytes_avg = calculateAverage("
                    + "published_data['stats.cache_in_bytes'])\n")
            .append("    on_script_finished(result)\n")
            .append("end\n")
            .toString();
    private static final TelemetryProto.Publisher PROCESS_MEMORY_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(
                            TelemetryProto.StatsPublisher.newBuilder()
                                    .setSystemMetric(PROCESS_MEMORY_STATE))
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_PROCESS_MEMORY_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("process_memory_metrics_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_PROCESS_MEMORY_STATE)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onProcessMemory")
                                    .setPublisher(PROCESS_MEMORY_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String PROCESS_MEMORY_CONFIG_NAME =
            METRICS_CONFIG_PROCESS_MEMORY_V1.getName();

    /** AppStartMemoryStateCaptured section. */
    private static final String LUA_SCRIPT_ON_APP_START_MEMORY_STATE_CAPTURED = new StringBuilder()
            .append("function calculateAverage(tbl)\n")
            .append("    local sum = 0\n")
            .append("    local size = 0\n")
            .append("    for _, value in ipairs(tbl) do\n")
            .append("        sum = sum + value\n")
            .append("        size = size + 1\n")
            .append("    end\n")
            .append("    return sum/size\n")
            .append("end\n")
            .append("function onAppStartMemoryStateCaptured(published_data, state)\n")
            .append("    local result = {}\n")
            .append("    result.uid = published_data['stats.uid']\n")
            .append("    result.page_fault_avg = calculateAverage("
                    + "published_data['stats.page_fault'])\n")
            .append("    result.major_page_fault_avg = calculateAverage("
                    + "published_data['stats.page_major_fault'])\n")
            .append("    result.rss_in_bytes_avg = calculateAverage("
                    + "published_data['stats.rss_in_bytes'])\n")
            .append("    result.swap_in_bytes_avg = calculateAverage("
                    + "published_data['stats.swap_in_bytes'])\n")
            .append("    result.cache_in_bytes_avg = calculateAverage("
                    + "published_data['stats.cache_in_bytes'])\n")
            .append("    on_script_finished(result)\n")
            .append("end\n")
            .toString();
    private static final TelemetryProto.Publisher APP_START_MEMORY_STATE_CAPTURED_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(
                            TelemetryProto.StatsPublisher.newBuilder()
                                    .setSystemMetric(APP_START_MEMORY_STATE_CAPTURED))
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_APP_START_MEMORY_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("app_start_memory_metrics_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_APP_START_MEMORY_STATE_CAPTURED)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onAppStartMemoryStateCaptured")
                                    .setPublisher(APP_START_MEMORY_STATE_CAPTURED_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String APP_START_MEMORY_STATE_CAPTURED_CONFIG_NAME =
            METRICS_CONFIG_APP_START_MEMORY_V1.getName();

    /** ActivityForegroundStateChanged section. */
    private static final String LUA_SCRIPT_ON_ACTIVITY_FOREGROUND_STATE_CHANGED =
            new StringBuilder()
                    .append("function onActivityForegroundStateChanged(published_data, state)\n")
                    .append("    local result = {}\n")
                    .append("    local n = 0\n")
                    .append("    for k, v in pairs(published_data) do\n")
                    .append("        result[k] = v[1]\n")
                    .append("        n = n + 1\n")
                    .append("    end\n")
                    .append("    result.n = n\n")
                    .append("    on_script_finished(result)\n")
                    .append("end\n")
                    .toString();

    private static final TelemetryProto.Publisher ACTIVITY_FOREGROUND_STATE_CHANGED_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(
                            TelemetryProto.StatsPublisher.newBuilder()
                                    .setSystemMetric(ACTIVITY_FOREGROUND_STATE_CHANGED))
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_ACTIVITY_FOREGROUND_STATE_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("activity_foreground_state_changed_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_ACTIVITY_FOREGROUND_STATE_CHANGED)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onActivityForegroundStateChanged")
                                    .setPublisher(ACTIVITY_FOREGROUND_STATE_CHANGED_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String ACTIVITY_FOREGROUND_STATE_CHANGED_CONFIG_NAME =
            METRICS_CONFIG_ACTIVITY_FOREGROUND_STATE_V1.getName();

    /** ProcessCpuTime section */
    private static final String LUA_SCRIPT_ON_PROCESS_CPU_TIME =
            new StringBuilder()
                    .append("function onProcessCpuTime(published_data, state)\n")
                    .append("    local result = {}\n")
                    .append("    local n = 0\n")
                    .append("    for k, v in pairs(published_data) do\n")
                    .append("        result[k] = v[1]\n")
                    .append("        n = n + 1\n")
                    .append("    end\n")
                    .append("    result.n = n\n")
                    .append("    on_script_finished(result)\n")
                    .append("end\n")
                    .toString();

    private static final TelemetryProto.Publisher PROCESS_CPU_TIME_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(
                            TelemetryProto.StatsPublisher.newBuilder()
                                    .setSystemMetric(PROCESS_CPU_TIME))
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_PROCESS_CPU_TIME_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("process_cpu_time_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_PROCESS_CPU_TIME)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onProcessCpuTime")
                                    .setPublisher(PROCESS_CPU_TIME_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String PROCESS_CPU_TIME_CONFIG_NAME =
            METRICS_CONFIG_PROCESS_CPU_TIME_V1.getName();

    /** AppCrashOccurred section */
    private static final String LUA_SCRIPT_ON_APP_CRASH_OCCURRED =
            new StringBuilder()
                    .append("function onAppCrashOccurred(published_data, state)\n")
                    .append("    local result = {}\n")
                    .append("    for k, v in pairs(published_data) do\n")
                    .append("        result[k] = v[1]\n")
                    .append("    end\n")
                    .append("    on_script_finished(result)\n")
                    .append("end\n")
                    .toString();

    private static final TelemetryProto.Publisher APP_CRASH_OCCURRED_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(
                            TelemetryProto.StatsPublisher.newBuilder()
                                    .setSystemMetric(APP_CRASH_OCCURRED))
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_APP_CRASH_OCCURRED_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("app_crash_occurred_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_APP_CRASH_OCCURRED)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onAppCrashOccurred")
                                    .setPublisher(APP_CRASH_OCCURRED_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String APP_CRASH_OCCURRED_CONFIG_NAME =
            METRICS_CONFIG_APP_CRASH_OCCURRED_V1.getName();

    /** ANROccurred section */
    private static final String LUA_SCRIPT_ON_ANR_OCCURRED =
            new StringBuilder()
                    .append("function onAnrOccurred(published_data, state)\n")
                    .append("    local result = {}\n")
                    .append("    for k, v in pairs(published_data) do\n")
                    .append("        result[k] = v[1]\n")
                    .append("    end\n")
                    .append("    on_script_finished(result)\n")
                    .append("end\n")
                    .toString();

    private static final TelemetryProto.Publisher ANR_OCCURRED_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(
                            TelemetryProto.StatsPublisher.newBuilder()
                                    .setSystemMetric(ANR_OCCURRED))
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_ANR_OCCURRED_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("anr_occurred_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_ANR_OCCURRED)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onAnrOccurred")
                                    .setPublisher(ANR_OCCURRED_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String ANR_OCCURRED_CONFIG_NAME = METRICS_CONFIG_ANR_OCCURRED_V1.getName();

    /** WTFOccurred section */
    private static final String LUA_SCRIPT_ON_WTF_OCCURRED =
            new StringBuilder()
                    .append("function onWtfOccurred(published_data, state)\n")
                    .append("    local result = {}\n")
                    .append("    for k, v in pairs(published_data) do\n")
                    .append("        result[k] = v[1]\n")
                    .append("    end\n")
                    .append("    on_script_finished(result)\n")
                    .append("end\n")
                    .toString();

    private static final TelemetryProto.Publisher WTF_OCCURRED_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(
                            TelemetryProto.StatsPublisher.newBuilder()
                                    .setSystemMetric(WTF_OCCURRED))
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_WTF_OCCURRED_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("wtf_occurred_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_WTF_OCCURRED)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onWtfOccurred")
                                    .setPublisher(WTF_OCCURRED_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String WTF_OCCURRED_CONFIG_NAME = METRICS_CONFIG_WTF_OCCURRED_V1.getName();

    private static final TelemetryProto.Publisher WIFI_NETSTATS_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setConnectivity(
                            ConnectivityPublisher.newBuilder()
                                    .setTransport(ConnectivityPublisher.Transport.TRANSPORT_WIFI)
                                    .setOemType(ConnectivityPublisher.OemType.OEM_NONE))
                    .build();
    // This config uses the script "R.raw.telemetry_stats_and_connectivity_script".
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_WIFI_TOP_CONSUMERS =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("wifi_top_consumers")
                    .setVersion(1)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onWifiNetstatsForTopConsumers")
                                    .setPublisher(WIFI_NETSTATS_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String WIFI_TOP_CONSUMERS_CONFIG_NAME =
            METRICS_CONFIG_WIFI_TOP_CONSUMERS.getName();

    // This config uses the script "R.raw.telemetry_driving_sessions_script".
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_DRIVING_SESSIONS =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("wifi_stats_with_driving_sessions")
                    .setVersion(1)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onWifiStatsForDrivingSessions")
                                    .setPublisher(WIFI_NETSTATS_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String WIFI_STATS_DRIVING_SESSIONS_CONFIG_NAME =
            METRICS_CONFIG_DRIVING_SESSIONS.getName();

    /**
     * PROCESS_CPU_TIME + PROCESS_MEMORY + WIFI_NETSTATS section. Reuses the same publisher
     * configuration that were defined above for PROCESS_CPU_TIME, PROCESS_MEMORY, and
     * WIFI_NETSTATS. Its script is R.raw.telemetry_stats_and_connectivity_script which is loaded at
     * runtime. The script produces a final report when it receives atoms PROCESS_MEMORY and
     * PROCESS_CPU_TIME, and more than 5 pieces of data from connectivity publisher.
     */
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_STATS_AND_CONNECTIVITY_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("stats_and_connectivity_metrics_config")
                    .setVersion(1)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onProcessMemory")
                                    .setPublisher(PROCESS_MEMORY_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onProcessCpuTime")
                                    .setPublisher(PROCESS_CPU_TIME_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onWifiNetstats")
                                    .setPublisher(WIFI_NETSTATS_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();

    private static final String STATS_AND_CONNECTIVITY_CONFIG_NAME =
            METRICS_CONFIG_STATS_AND_CONNECTIVITY_V1.getName();

    /** MemoryPublisher section. */
    private static final String LUA_SCRIPT_ON_MEMORY =
            new StringBuilder()
                    .append("function onMemory(published_data, state)\n")
                    .append("    local iterations = state['iterations']\n")
                    .append("    if iterations == nil then\n")
                    .append("        iterations = 0\n")
                    .append("    end\n")
                    .append("    state['iterations'] = iterations + 1\n")
                    .append("    local meminfo = published_data['mem.meminfo']\n")
                    .append("    local available_memory = string.match(meminfo, "
                            + "'.*MemAvailable:%s*(%d+).*')\n")
                    .append("    local mem_key = 'available_memory_' .. iterations\n")
                    .append("    published_data[mem_key] = available_memory\n")
                    .append("    published_data['mem.meminfo'] = nil\n")
                    .append("    on_metrics_report(published_data, state)\n")
                    .append("end\n")
                    .toString();
    private static final TelemetryProto.Publisher MEMORY_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setMemory(
                            TelemetryProto.MemoryPublisher.newBuilder()
                                    .setReadIntervalSec(3)
                                    .setMaxSnapshots(3)
                                    .setMaxPendingTasks(10)
                                    .addPackageNames("com.android.car")
                                    .addPackageNames("com.android.car.scriptexecutor")
                                    .build())
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_MEMORY_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("memory_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_MEMORY)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onMemory")
                                    .setPublisher(MEMORY_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String MEMORY_CONFIG_NAME =
            METRICS_CONFIG_MEMORY_V1.getName();

    /** ProcessMemorySnapshot section. */
    private static final String LUA_SCRIPT_ON_PROCESS_MEMORY_SNAPSHOT = new StringBuilder()
            .append("function onProcessMemorySnapshot(published_data, state)\n")
            .append("    on_script_finished(published_data)\n")
            .append("end\n")
            .toString();
    private static final TelemetryProto.Publisher PROCESS_MEMORY_SNAPSHOT_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(
                            TelemetryProto.StatsPublisher.newBuilder()
                                    .setSystemMetric(PROCESS_MEMORY_SNAPSHOT))
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_PROCESS_MEMORY_SNAPSHOT_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("process_memory_snapshot_metrics_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_PROCESS_MEMORY_SNAPSHOT)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onProcessMemorySnapshot")
                                    .setPublisher(PROCESS_MEMORY_SNAPSHOT_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String PROCESS_MEMORY_SNAPSHOT_CONFIG_NAME =
            METRICS_CONFIG_PROCESS_MEMORY_SNAPSHOT_V1.getName();

    /** ProcessStartTime section. */
    private static final String LUA_SCRIPT_ON_PROCESS_START_TIME = new StringBuilder()
            .append("function onProcessStartTime(published_data, state)\n")
            .append("    on_script_finished(published_data)\n")
            .append("end\n")
            .toString();
    private static final TelemetryProto.Publisher PROCESS_START_TIME_PUBLISHER =
            TelemetryProto.Publisher.newBuilder()
                    .setStats(
                            TelemetryProto.StatsPublisher.newBuilder()
                                    .setSystemMetric(PROCESS_START_TIME))
                    .build();
    private static final TelemetryProto.MetricsConfig METRICS_CONFIG_PROCESS_START_TIME_V1 =
            TelemetryProto.MetricsConfig.newBuilder()
                    .setName("process_start_time_metrics_config")
                    .setVersion(1)
                    .setScript(LUA_SCRIPT_ON_PROCESS_START_TIME)
                    .addSubscribers(
                            TelemetryProto.Subscriber.newBuilder()
                                    .setHandler("onProcessStartTime")
                                    .setPublisher(PROCESS_START_TIME_PUBLISHER)
                                    .setPriority(SCRIPT_EXECUTION_PRIORITY_HIGH))
                    .build();
    private static final String PROCESS_START_TIME_CONFIG_NAME =
                METRICS_CONFIG_PROCESS_START_TIME_V1.getName();

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    private boolean mReceiveReportNotification = false;
    private CarTelemetryManager mCarTelemetryManager;
    private FinishedReportListenerImpl mListener;
    private AddMetricsConfigCallbackImpl mAddMetricsConfigCallback;
    private KitchenSinkActivity mActivity;
    private TextView mOutputTextView;
    private Button mTootleConfigsBtn;
    private Button mEnableReportNotificationButton;
    private View mConfigButtonsView; // MetricsConfig buttons

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        mActivity = (KitchenSinkActivity) getActivity();
        mCarTelemetryManager = mActivity.getCarTelemetryManager();
        mListener = new FinishedReportListenerImpl();
        mAddMetricsConfigCallback = new AddMetricsConfigCallbackImpl();
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.car_telemetry_test, container, false);
        mOutputTextView = view.findViewById(R.id.output_textview);
        mConfigButtonsView = view.findViewById(R.id.metrics_config_buttons_view);
        mTootleConfigsBtn = view.findViewById(R.id.toggle_metrics_configs_btn);
        mTootleConfigsBtn.setOnClickListener(this::toggleMetricsConfigButtons);
        mEnableReportNotificationButton = view.findViewById(R.id.enable_report_notification_btn);
        mEnableReportNotificationButton.setOnClickListener(this::enableReportNotification);
        mEnableReportNotificationButton.setText(
                getString(R.string.receive_report_notification_text, mReceiveReportNotification));

        /** VehiclePropertyPublisher on_gear_change */
        view.findViewById(R.id.send_on_gear_change_config)
                .setOnClickListener(this::onSendGearChangeConfigBtnClick);
        view.findViewById(R.id.remove_on_gear_change_config)
                .setOnClickListener(this::onRemoveGearChangeConfigBtnClick);
        view.findViewById(R.id.get_on_gear_change_report)
                .setOnClickListener(this::onGetGearChangeReportBtnClick);
        /** StatsPublisher process_memory */
        view.findViewById(R.id.send_on_process_memory_config)
                .setOnClickListener(this::onSendProcessMemoryConfigBtnClick);
        view.findViewById(R.id.remove_on_process_memory_config)
                .setOnClickListener(this::onRemoveProcessMemoryConfigBtnClick);
        view.findViewById(R.id.get_on_process_memory_report)
                .setOnClickListener(this::onGetProcessMemoryReportBtnClick);
        /** StatsPublisher app_start_memory_state */
        view.findViewById(R.id.send_on_app_start_memory_state_captured_config)
                .setOnClickListener(this::onSendAppStartMemoryStateCapturedConfigBtnClick);
        view.findViewById(R.id.remove_on_app_start_memory_state_captured_config)
                .setOnClickListener(this::onRemoveAppStartMemoryStateCapturedConfigBtnClick);
        view.findViewById(R.id.get_on_app_start_memory_state_captured_report)
                .setOnClickListener(this::onGetAppStartMemoryStateCapturedReportBtnClick);
        /** StatsPublisher activity_foreground_state_change */
        view.findViewById(R.id.send_on_activity_foreground_state_changed_config)
                .setOnClickListener(this::onSendActivityForegroundStateChangedConfigBtnClick);
        view.findViewById(R.id.remove_on_activity_foreground_state_changed_config)
                .setOnClickListener(this::onRemoveActivityForegroundStateChangedConfigBtnClick);
        view.findViewById(R.id.get_on_activity_foreground_state_changed_report)
                .setOnClickListener(this::onGetActivityForegroundStateChangedReportBtnClick);
        /** StatsPublisher process_cpu_time */
        view.findViewById(R.id.send_on_process_cpu_time_config)
                .setOnClickListener(this::onSendProcessCpuTimeConfigBtnClick);
        view.findViewById(R.id.remove_on_process_cpu_time_config)
                .setOnClickListener(this::onRemoveProcessCpuTimeConfigBtnClick);
        view.findViewById(R.id.get_on_process_cpu_time_report)
                .setOnClickListener(this::onGetProcessCpuTimeReportBtnClick);
        /** StatsPublisher AppCrashOccurred section */
        view.findViewById(R.id.send_on_app_crash_occurred_config)
                .setOnClickListener(this::onSendAppCrashOccurredConfigBtnClick);
        view.findViewById(R.id.remove_on_app_crash_occurred_config)
                .setOnClickListener(this::onRemoveAppCrashOccurredConfigBtnClick);
        view.findViewById(R.id.get_on_app_crash_occurred_report)
                .setOnClickListener(this::onGetAppCrashOccurredReportBtnClick);
        /** StatsPublisher ANROccurred section */
        view.findViewById(R.id.send_on_anr_occurred_config)
                .setOnClickListener(this::onSendAnrOccurredConfigBtnClick);
        view.findViewById(R.id.remove_on_anr_occurred_config)
                .setOnClickListener(this::onRemoveAnrOccurredConfigBtnClick);
        view.findViewById(R.id.get_on_anr_occurred_report)
                .setOnClickListener(this::onGetAnrOccurredReportBtnClick);
        /** StatsPublisher WTFOccurred section */
        view.findViewById(R.id.send_on_wtf_occurred_config)
                .setOnClickListener(this::onSendWtfOccurredConfigBtnClick);
        view.findViewById(R.id.remove_on_wtf_occurred_config)
                .setOnClickListener(this::onRemoveWtfOccurredConfigBtnClick);
        view.findViewById(R.id.get_on_wtf_occurred_report)
                .setOnClickListener(this::onGetWtfOccurredReportBtnClick);
        /** ConnectivityPublisher wifi_netstats top consumers section */
        view.findViewById(R.id.send_on_wifi_netstats_config)
                .setOnClickListener(this::onSendWifiNetstatsConfigBtnClick);
        view.findViewById(R.id.remove_on_wifi_netstats_config)
                .setOnClickListener(this::onRemoveWifiNetstatsConfigBtnClick);
        view.findViewById(R.id.get_on_wifi_netstats_report)
                .setOnClickListener(this::onGetWifiNetstatsReportBtnClick);
        /** StatsPublisher + ConnectivityPublisher section */
        view.findViewById(R.id.send_stats_and_connectivity_config)
                .setOnClickListener(this::onSendStatsAndConnectivityConfigBtnClick);
        view.findViewById(R.id.remove_stats_and_connectivity_config)
                .setOnClickListener(this::onRemoveStatsAndConnectivityConfigBtnClick);
        view.findViewById(R.id.get_stats_and_connectivity_report)
                .setOnClickListener(this::onGetStatsAndConnectivityReportBtnClick);
        /** Driving sessions section */
        view.findViewById(R.id.send_driving_sessions_config)
                .setOnClickListener(this::onSendDrivingSessionsConfigBtnClick);
        view.findViewById(R.id.download_data)
                .setOnClickListener(this::onDownloadDataBtnClick);
        view.findViewById(R.id.emulate_suspend_to_RAM)
                .setOnClickListener(this::onEmulateSuspendToRAMBtnClick);
        view.findViewById(R.id.emulate_reboot)
                .setOnClickListener(this::onEmulateRebootBtnClick);
        view.findViewById(R.id.remove_driving_sessions_config)
                .setOnClickListener(this::onRemoveDrivingSessionsConfigBtnClick);
        view.findViewById(R.id.get_driving_sessions_report)
                .setOnClickListener(this::onGetDrivingSessionsReportBtnClick);
        /** MemoryPublisher section */
        view.findViewById(R.id.send_memory_config)
                .setOnClickListener(this::onSendMemoryConfigBtnClick);
        view.findViewById(R.id.remove_memory_config)
                .setOnClickListener(this::onRemoveMemoryConfigBtnClick);
        view.findViewById(R.id.get_memory_report)
                .setOnClickListener(this::onGetMemoryReportBtnClick);
        /** StatsPublisher process_memory_snapshot */
        view.findViewById(R.id.send_on_process_memory_snapshot_config)
                .setOnClickListener(this::onSendProcessMemorySnapshotConfigBtnClick);
        view.findViewById(R.id.remove_on_process_memory_snapshot_config)
                .setOnClickListener(this::onRemoveProcessMemorySnapshotConfigBtnClick);
        view.findViewById(R.id.get_on_process_memory_snapshot_report)
                .setOnClickListener(this::onGetProcessMemorySnapshotReportBtnClick);
        /** StatsPublisher process_start_time */
        view.findViewById(R.id.send_on_process_start_time_config)
                .setOnClickListener(this::onSendProcessStartTimeConfigBtnClick);
        view.findViewById(R.id.remove_on_process_start_time_config)
                .setOnClickListener(this::onRemoveProcessStartTimeConfigBtnClick);
        view.findViewById(R.id.get_on_process_start_time_report)
                .setOnClickListener(this::onGetProcessStartTimeReportBtnClick);
        /** Print mem info button */
        view.findViewById(R.id.print_mem_info_btn).setOnClickListener(this::onPrintMemInfoBtnClick);
        return view;
    }

    private void showOutput(String s) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        String text = now + " : " + s;
        Log.i(TAG, text);
        mActivity.runOnUiThread(
                () -> {
                    mOutputTextView.setText(text + "\n" + mOutputTextView.getText());
                });
    }

    private void toggleMetricsConfigButtons(View view) {
        boolean visible = mConfigButtonsView.getVisibility() == View.VISIBLE;
        mConfigButtonsView.setVisibility(visible ? View.GONE : View.VISIBLE);
        mTootleConfigsBtn.setText(visible ? "Configs ▶" : "Configs ▼");
    }

    private void enableReportNotification(View view) {
        mReceiveReportNotification = !mReceiveReportNotification;
        mEnableReportNotificationButton.setText(
                getString(R.string.receive_report_notification_text, mReceiveReportNotification));
        if (mReceiveReportNotification) {
            mCarTelemetryManager.setReportReadyListener(mExecutor, this::onReportReady);
        } else {
            mCarTelemetryManager.clearReportReadyListener();
        }
    }

    /** Implementation of functional interface {@link CarTelemetryManager.ReportReadyListener}. */
    private void onReportReady(@NonNull String metricsConfigName) {
        String s = "Report for MetricsConfig " + metricsConfigName + " is ready.";
        showOutput(s);
    }

    private void onSendGearChangeConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                ON_GEAR_CHANGE_CONFIG_NAME,
                METRICS_CONFIG_ON_GEAR_CHANGE_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveGearChangeConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for gear change...");
        mCarTelemetryManager.removeMetricsConfig(ON_GEAR_CHANGE_CONFIG_NAME);
    }

    private void onGetGearChangeReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(ON_GEAR_CHANGE_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendProcessMemoryConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                PROCESS_MEMORY_CONFIG_NAME,
                METRICS_CONFIG_PROCESS_MEMORY_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveProcessMemoryConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for PROCESS_MEMORY_STATE...");
        mCarTelemetryManager.removeMetricsConfig(PROCESS_MEMORY_CONFIG_NAME);
    }

    private void onGetProcessMemoryReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(PROCESS_MEMORY_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendAppStartMemoryStateCapturedConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                APP_START_MEMORY_STATE_CAPTURED_CONFIG_NAME,
                METRICS_CONFIG_APP_START_MEMORY_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveAppStartMemoryStateCapturedConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for APP_START_MEMORY_STATE_CAPTURED...");
        mCarTelemetryManager.removeMetricsConfig(APP_START_MEMORY_STATE_CAPTURED_CONFIG_NAME);
    }

    private void onGetAppStartMemoryStateCapturedReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(
                APP_START_MEMORY_STATE_CAPTURED_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendActivityForegroundStateChangedConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                ACTIVITY_FOREGROUND_STATE_CHANGED_CONFIG_NAME,
                METRICS_CONFIG_ACTIVITY_FOREGROUND_STATE_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveActivityForegroundStateChangedConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for ACTIVITY_FOREGROUND_STATE_CHANGED...");
        mCarTelemetryManager.removeMetricsConfig(ACTIVITY_FOREGROUND_STATE_CHANGED_CONFIG_NAME);
    }

    private void onGetActivityForegroundStateChangedReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(
                ACTIVITY_FOREGROUND_STATE_CHANGED_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendProcessCpuTimeConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                PROCESS_CPU_TIME_CONFIG_NAME,
                METRICS_CONFIG_PROCESS_CPU_TIME_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveProcessCpuTimeConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for PROCESS_CPU_TIME...");
        mCarTelemetryManager.removeMetricsConfig(PROCESS_CPU_TIME_CONFIG_NAME);
    }

    private void onGetProcessCpuTimeReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(PROCESS_CPU_TIME_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendAppCrashOccurredConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                APP_CRASH_OCCURRED_CONFIG_NAME,
                METRICS_CONFIG_APP_CRASH_OCCURRED_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveAppCrashOccurredConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for APP_CRASH_OCCURRED...");
        mCarTelemetryManager.removeMetricsConfig(APP_CRASH_OCCURRED_CONFIG_NAME);
    }

    private void onGetAppCrashOccurredReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(
                APP_CRASH_OCCURRED_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendAnrOccurredConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                ANR_OCCURRED_CONFIG_NAME,
                METRICS_CONFIG_ANR_OCCURRED_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveAnrOccurredConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for ANR_OCCURRED...");
        mCarTelemetryManager.removeMetricsConfig(ANR_OCCURRED_CONFIG_NAME);
    }

    private void onGetAnrOccurredReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(ANR_OCCURRED_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendWtfOccurredConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                WTF_OCCURRED_CONFIG_NAME,
                METRICS_CONFIG_WTF_OCCURRED_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveWtfOccurredConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for WTF_OCCURRED...");
        mCarTelemetryManager.removeMetricsConfig(WTF_OCCURRED_CONFIG_NAME);
    }

    private void onGetWtfOccurredReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(WTF_OCCURRED_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendWifiNetstatsConfigBtnClick(View view) {
        showOutput("If the config is added successfully, it will produce a report on the top "
                + "3 wifi network traffic consumers after 1 driving sessions.");
        mCarTelemetryManager.addMetricsConfig(
                WIFI_TOP_CONSUMERS_CONFIG_NAME,
                METRICS_CONFIG_WIFI_TOP_CONSUMERS
                        .toBuilder()
                        .setScript(
                                readTelemetryScript(R.raw.telemetry_stats_and_connectivity_script))
                        .build()
                        .toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveWifiNetstatsConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig on wifi netstats top consumers...");
        mCarTelemetryManager.removeMetricsConfig(WIFI_TOP_CONSUMERS_CONFIG_NAME);
    }

    private void onGetWifiNetstatsReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(
                WIFI_TOP_CONSUMERS_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendStatsAndConnectivityConfigBtnClick(View view) {
        String luaScript = readTelemetryScript(R.raw.telemetry_stats_and_connectivity_script);
        showOutput(
                "If the config added successfully, emulate power state change by first running:\n"
                        + "$ adb shell cmd car_service suspend\n"
                        + "and, after 1 minute pause:\n"
                        + "$ adb shell cmd car_service resume\n"
                        + "Repeat this 3 times and then pull the report after 10 minutes.");
        TelemetryProto.MetricsConfig config =
                METRICS_CONFIG_STATS_AND_CONNECTIVITY_V1.toBuilder().setScript(luaScript).build();
        mCarTelemetryManager.addMetricsConfig(
                STATS_AND_CONNECTIVITY_CONFIG_NAME,
                config.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private String readTelemetryScript(@RawRes int fileResourceId) {
        try (InputStream is =
                     getResources().openRawResource(fileResourceId)) {
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            return new String(bytes);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Unable to send MetricsConfig, because reading Lua script from file failed.",
                    e);
        }
    }

    private void onRemoveStatsAndConnectivityConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for stats data & connectivity data...");
        mCarTelemetryManager.removeMetricsConfig(STATS_AND_CONNECTIVITY_CONFIG_NAME);
    }

    private void onGetStatsAndConnectivityReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(
                STATS_AND_CONNECTIVITY_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendDrivingSessionsConfigBtnClick(View view) {
        String luaScript = readTelemetryScript(R.raw.telemetry_driving_sessions_script);
        showOutput(
                "If the config added successfully, please induce three driving sessions\n"
                        + "by using both Suspend-to-RAM and Reboot buttons and then check "
                        + "generated report\n"
                        + "Suggested sequence: \n"
                        + "1) Load new script\n"
                        + "2) Click DOWNLOAD DATA button and note the size of the file downloaded"
                        + ".\n"
                        + "3) Click on SUSPEND TO RAM to complete the 1st driving session. The "
                        + "app should reappear after a brief break.\n"
                        + "4) Click DOWNLOAD DATA to download the same file the 2nd time.\n"
                        + "5) Click on REBOOT button to complete the 2nd driving session and test"
                        + " preserving of session data on disk at shutdown.\n"
                        + "6) After the reboot is complete, bring up the Kitchensink app and "
                        + "telemetry screen again to continue the test.\n"
                        + "7) Click DOWNLOAD DATA to download the same file the 3rd time.\n"
                        + "8) Click on SUSPEND TO RAM to complete the 3rd driving session.\n"
                        + "9) After the screen and the app are brought back up, click on GET "
                        + "REPORT.\n"
                        + "10) The report requires 3 driving sessions to be generated.\n"
                        + "11) In the report, there will be three separate entries that show "
                        + "total traffic for kitchen sink app.\n"
                        + "12) Each entry corresponds to a driving session.\n"
                        + "13) Each entry should show the number of bytes transferred at least "
                        + "equal to the size of the file downloaded each of the 3 times.\n"
        );
        TelemetryProto.MetricsConfig config =
                METRICS_CONFIG_DRIVING_SESSIONS.toBuilder().setScript(luaScript).build();
        mCarTelemetryManager.addMetricsConfig(
                WIFI_STATS_DRIVING_SESSIONS_CONFIG_NAME,
                config.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onDownloadDataBtnClick(View view) {
        showOutput("Downloading data using curl...");

        // First, create a directory where the file will be downloaded.
        File tempDirectory;
        try {
            tempDirectory = Files.createTempDirectory(mActivity.getFilesDir().toPath(),
                    "downloadDir").toFile();
        } catch (IOException e) {
            showOutput(e.toString());
            return;
        }

        boolean status = runCommand(tempDirectory, "curl", "-O", "-L",
                "https://yts.devicecertification.youtube/yts_server.zip");
        Path filePath = Paths.get(tempDirectory.getAbsolutePath(), "yts_server.zip");
        if (status && Files.exists(filePath)) {
            try {
                showOutput("Successfully downloaded a file with size " + Files.size(filePath)
                        + " bytes.");
            } catch (IOException e) {
                showOutput(
                        "Successfully downloaded a file but exception occurred: " + e.toString());
            }
        }

        // clean up by removing the temporary download directory with all its contents.
        tempDirectory.delete();
    }

    private boolean runCommand(@Nullable File currentDirectory, String... command) {
        Process p = null;
        BufferedReader is = null;
        StringBuilder out = new StringBuilder();
        boolean success = false;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            if (currentDirectory != null) {
                processBuilder.directory(currentDirectory);
            }
            p = processBuilder.start();
            is = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;

            while ((line = is.readLine()) != null) {
                out.append(line);
                out.append(System.lineSeparator());
            }
            p.waitFor();
        } catch (Exception e) {
            showOutput(e.toString());
        } finally {
            if (p != null) {
                p.destroy();
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        if (p != null) {
            int processExitValue = p.exitValue();
            if (processExitValue == 0) {
                showOutput(out.toString().trim());
                success = true;
            }
        }
        return success;
    }

    private void onEmulateSuspendToRAMBtnClick(View view) {
        runCommand(null, "cmd", "car_service", "suspend", "--simulate", "--wakeup-after", "3");
    }

    private void onEmulateRebootBtnClick(View view) {
        runCommand(null, "cmd", "car_service", "power-off", "--reboot");
    }

    private void onRemoveDrivingSessionsConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for driving sessions...");
        mCarTelemetryManager.removeMetricsConfig(WIFI_STATS_DRIVING_SESSIONS_CONFIG_NAME);
    }

    private void onGetDrivingSessionsReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(
                WIFI_STATS_DRIVING_SESSIONS_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendMemoryConfigBtnClick(View view) {
        showOutput("If the MetricsConfig is added successfully, it will produce 3 metrics "
                + "reports on available memory. The reports are produced 3 seconds apart. "
                + "After 3 reports, the MetricsConfig's lifecycle is considered finished.");
        mCarTelemetryManager.addMetricsConfig(
                MEMORY_CONFIG_NAME,
                METRICS_CONFIG_MEMORY_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveMemoryConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig for memory...");
        mCarTelemetryManager.removeMetricsConfig(MEMORY_CONFIG_NAME);
    }

    private void onGetMemoryReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(MEMORY_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendProcessMemorySnapshotConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                PROCESS_MEMORY_SNAPSHOT_CONFIG_NAME,
                METRICS_CONFIG_PROCESS_MEMORY_SNAPSHOT_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveProcessMemorySnapshotConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for PROCESS_MEMORY_SNAPSHOT...");
        mCarTelemetryManager.removeMetricsConfig(PROCESS_MEMORY_SNAPSHOT_CONFIG_NAME);
    }

    private void onGetProcessMemorySnapshotReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(
                PROCESS_MEMORY_SNAPSHOT_CONFIG_NAME, mExecutor, mListener);
    }

    private void onSendProcessStartTimeConfigBtnClick(View view) {
        mCarTelemetryManager.addMetricsConfig(
                PROCESS_START_TIME_CONFIG_NAME,
                METRICS_CONFIG_PROCESS_START_TIME_V1.toByteArray(),
                mExecutor,
                mAddMetricsConfigCallback);
    }

    private void onRemoveProcessStartTimeConfigBtnClick(View view) {
        showOutput("Removing MetricsConfig that listens for PROCESS_START_TIME...");
        mCarTelemetryManager.removeMetricsConfig(PROCESS_START_TIME_CONFIG_NAME);
    }

    private void onGetProcessStartTimeReportBtnClick(View view) {
        mCarTelemetryManager.getFinishedReport(
                PROCESS_START_TIME_CONFIG_NAME, mExecutor, mListener);
    }

    /** Gets a MemoryInfo object for the device's current memory status. */
    private ActivityManager.MemoryInfo getAvailableMemory() {
        ActivityManager activityManager = getActivity().getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo;
    }

    private void onPrintMemInfoBtnClick(View view) {
        // Use android's "alloc-stress" system tool to create an artificial memory pressure.
        ActivityManager.MemoryInfo info = getAvailableMemory();
        showOutput("MemoryInfo availMem=" + (info.availMem / 1024 / 1024) + "/"
                + (info.totalMem / 1024 / 1024) + "mb, isLowMem=" + info.lowMemory
                + ", threshold=" + (info.threshold / 1024 / 1024) + "mb");
    }

    /**
     * Updates the view to show {@link CarTelemetryManager#addMetricsConfig(String, byte[],
     * Executor, CarTelemetryManager.AddMetricsConfigCallback)} status code. The callbacks are
     * executed in {@link #mExecutor}.
     */
    private final class AddMetricsConfigCallbackImpl
            implements CarTelemetryManager.AddMetricsConfigCallback {

        @Override
        public void onAddMetricsConfigStatus(@NonNull String metricsConfigName, int statusCode) {
            showOutput("Add MetricsConfig status for " + metricsConfigName + ": "
                    + statusCodeToString(statusCode));
        }

        private String statusCodeToString(int statusCode) {
            switch (statusCode) {
                case CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED:
                    return "SUCCESS";
                case CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_ALREADY_EXISTS:
                    return "ERROR ALREADY_EXISTS";
                case CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_VERSION_TOO_OLD:
                    return "ERROR VERSION_TOO_OLD";
                case CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_PARSE_FAILED:
                    return "ERROR PARSE_FAILED";
                case CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SIGNATURE_VERIFICATION_FAILED:
                    return "ERROR SIGNATURE_VERIFICATION_FAILED";
                default:
                    return "ERROR UNKNOWN";
            }
        }
    }

    /**
     * Implementation of the {@link CarTelemetryManager.MetricsReportCallback}. They update the view
     * to show the outputs from the APIs of {@link CarTelemetryManager}. The callbacks are executed
     * in {@link mExecutor}.
     */
    private final class FinishedReportListenerImpl implements
            CarTelemetryManager.MetricsReportCallback {

        @Override
        public void onResult(
                @NonNull String metricsConfigName,
                @Nullable PersistableBundle report,
                @Nullable byte[] telemetryError,
                @CarTelemetryManager.MetricsReportStatus int status) {
            if (report != null) {
                report.size(); // unparcel()'s
                showOutput(metricsConfigName + " has status: "
                        + statusCodeToString(status) + ". Printing report: \n\t" + report);
            } else if (telemetryError != null) {
                parseError(metricsConfigName, telemetryError);
            } else {
                showOutput("No report exists for MetricsConfig " + metricsConfigName
                        + ", reason = " + statusCodeToString(status));
            }
        }

        private void parseError(@NonNull String metricsConfigName, @NonNull byte[] error) {
            try {
                TelemetryProto.TelemetryError telemetryError =
                        TelemetryProto.TelemetryError.parseFrom(error);
                showOutput("Error for " + metricsConfigName + ": " + telemetryError);
            } catch (InvalidProtocolBufferException e) {
                showOutput("Unable to parse error result for MetricsConfig " + metricsConfigName
                        + ": " + e.getMessage());
            }
        }

        private String statusCodeToString(int statusCode) {
            switch (statusCode) {
                case CarTelemetryManager.STATUS_GET_METRICS_CONFIG_FINISHED:
                    return "REPORT RETRIEVED";
                case CarTelemetryManager.STATUS_GET_METRICS_CONFIG_PENDING:
                    return "REPORT PENDING";
                case CarTelemetryManager.STATUS_GET_METRICS_CONFIG_INTERIM_RESULTS:
                    return "INTERIM RESULT EXISTS";
                case CarTelemetryManager.STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR:
                    return "RUNTIME ERROR";
                case CarTelemetryManager.STATUS_GET_METRICS_CONFIG_DOES_NOT_EXIST:
                    return "METRICS CONFIG DOES NOT EXIST";
                default:
                    return "INVALID STATUS CODE";
            }
        }
    }
}
