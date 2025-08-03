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
package com.android.car.telemetry;

import static android.car.telemetry.CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_PARSE_FAILED;
import static android.car.telemetry.CarTelemetryManager.STATUS_ADD_METRICS_CONFIG_SUCCEEDED;
import static android.car.telemetry.CarTelemetryManager.STATUS_GET_METRICS_CONFIG_DOES_NOT_EXIST;
import static android.car.telemetry.CarTelemetryManager.STATUS_GET_METRICS_CONFIG_FINISHED;
import static android.car.telemetry.CarTelemetryManager.STATUS_GET_METRICS_CONFIG_INTERIM_RESULTS;
import static android.car.telemetry.CarTelemetryManager.STATUS_GET_METRICS_CONFIG_PENDING;
import static android.car.telemetry.CarTelemetryManager.STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import static java.util.stream.Collectors.toList;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.Car;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.telemetry.CarTelemetryManager;
import android.car.telemetry.ICarTelemetryReportListener;
import android.car.telemetry.ICarTelemetryReportReadyListener;
import android.car.telemetry.ICarTelemetryService;
import android.car.telemetry.TelemetryProto;
import android.car.telemetry.TelemetryProto.TelemetryError;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.util.ArrayMap;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarPropertyService;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.OnShutdownReboot;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.telemetry.MetricsReportProto.MetricsReportContainer;
import com.android.car.telemetry.MetricsReportProto.MetricsReportList;
import com.android.car.telemetry.databroker.DataBroker;
import com.android.car.telemetry.databroker.DataBrokerImpl;
import com.android.car.telemetry.databroker.ScriptExecutionTask;
import com.android.car.telemetry.publisher.PublisherFactory;
import com.android.car.telemetry.sessioncontroller.SessionController;
import com.android.car.telemetry.systemmonitor.SystemMonitor;
import com.android.car.telemetry.systemmonitor.SystemMonitorEvent;
import com.android.car.telemetry.util.IoUtils;
import com.android.car.telemetry.util.MetricsReportProtoUtils;
import com.android.internal.annotations.VisibleForTesting;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * CarTelemetryService manages OEM telemetry collection, processing and communication
 * with a data upload service.
 */
public class CarTelemetryService extends ICarTelemetryService.Stub implements CarServiceBase {

    private static final String TAG = CarTelemetryService.class.getSimpleName();

    public static final boolean DEBUG = false; // STOPSHIP if true

    public static final String TELEMETRY_DIR = "telemetry";

    /**
     * Priorities range from 0 to 100, with 0 being the highest priority and 100 being the lowest.
     * A {@link ScriptExecutionTask} must have equal or higher priority than the threshold in order
     * to be executed.
     * The following constants are chosen with the idea that subscribers with a priority of 0
     * must be executed as soon as data is published regardless of system health conditions.
     * Otherwise {@link ScriptExecutionTask}s are executed from the highest priority to the lowest
     * subject to system health constraints from {@link SystemMonitor}.
     */
    public static final int TASK_PRIORITY_HI = 0;
    public static final int TASK_PRIORITY_MED = 50;
    public static final int TASK_PRIORITY_LOW = 100;

    private final Context mContext;
    private final CarPowerManagementService mCarPowerManagementService;
    private final CarPropertyService mCarPropertyService;
    private final Dependencies mDependencies;
    private final HandlerThread mTelemetryThread = CarServiceUtils.getHandlerThread(
            CarTelemetryService.class.getSimpleName());
    private final Handler mTelemetryHandler = new Handler(mTelemetryThread.getLooper());
    private final UidPackageMapper mUidMapper;

    private final DataBroker.DataBrokerListener mDataBrokerListener =
            new DataBroker.DataBrokerListener() {
        @Override
        public void onEventConsumed(
                @NonNull String metricsConfigName, @NonNull PersistableBundle state) {
            mResultStore.putInterimResult(metricsConfigName, state);
            mDataBroker.scheduleNextTask();
        }
        @Override
        public void onReportFinished(@NonNull String metricsConfigName) {
            cleanupMetricsConfig(metricsConfigName); // schedules next script execution task
            if (mResultStore.getErrorResult(metricsConfigName, false) != null
                    || mResultStore.getMetricsReports(metricsConfigName, false) != null) {
                onReportReady(metricsConfigName);
            }
        }

        @Override
        public void onReportFinished(
                @NonNull String metricsConfigName, @NonNull PersistableBundle report) {
            cleanupMetricsConfig(metricsConfigName); // schedules next script execution task
            mResultStore.putMetricsReport(metricsConfigName, report, /* finished = */ true);
            onReportReady(metricsConfigName);
        }

        @Override
        public void onReportFinished(
                @NonNull String metricsConfigName, @NonNull TelemetryProto.TelemetryError error) {
            cleanupMetricsConfig(metricsConfigName); // schedules next script execution task
            mResultStore.putErrorResult(metricsConfigName, error);
            onReportReady(metricsConfigName);
        }

        @Override
        public void onMetricsReport(
                @NonNull String metricsConfigName,
                @NonNull PersistableBundle report,
                @Nullable PersistableBundle state) {
            mResultStore.putMetricsReport(metricsConfigName, report, /* finished = */ false);
            if (state != null) {
                mResultStore.putInterimResult(metricsConfigName, state);
            }
            onReportReady(metricsConfigName);
            mDataBroker.scheduleNextTask();
        }
    };

    // accessed and updated on the main thread
    private boolean mReleased = false;

    // all the following fields are accessed and updated on the telemetry thread
    private DataBroker mDataBroker;
    private ICarTelemetryReportReadyListener mReportReadyListener;
    private MetricsConfigStore mMetricsConfigStore;
    private OnShutdownReboot mOnShutdownReboot;
    private PublisherFactory mPublisherFactory;
    private ResultStore mResultStore;
    private SessionController mSessionController;
    private SystemMonitor mSystemMonitor;
    private TimingsTraceLog mTelemetryThreadTraceLog; // can only be used on telemetry thread

    static class Dependencies {

        /** Returns a new PublisherFactory instance. */
        public PublisherFactory getPublisherFactory(
                CarPropertyService carPropertyService,
                Handler handler,
                Context context,
                SessionController sessionController, ResultStore resultStore,
                UidPackageMapper uidMapper) {
            return new PublisherFactory(
                    carPropertyService, handler, context, sessionController, resultStore,
                    uidMapper);
        }

        /** Returns a new UidPackageMapper instance. */
        public UidPackageMapper getUidPackageMapper(Context context, Handler telemetryHandler) {
            return new UidPackageMapper(context, telemetryHandler);
        }
    }

    public CarTelemetryService(
            Context context,
            CarPowerManagementService carPowerManagementService,
            CarPropertyService carPropertyService) {
        this(context, carPowerManagementService, carPropertyService, new Dependencies(),
                /* dataBroker = */ null, /* sessionController = */ null);
    }

    @VisibleForTesting
    CarTelemetryService(
            Context context,
            CarPowerManagementService carPowerManagementService,
            CarPropertyService carPropertyService,
            Dependencies deps,
            DataBroker dataBroker,
            SessionController sessionController) {
        mContext = context;
        mCarPowerManagementService = carPowerManagementService;
        mCarPropertyService = carPropertyService;
        mDependencies = deps;
        mUidMapper = mDependencies.getUidPackageMapper(mContext, mTelemetryHandler);
        mDataBroker = dataBroker;
        mSessionController = sessionController;
    }

    @Override
    public void init() {
        mTelemetryHandler.post(() -> {
            mTelemetryThreadTraceLog = new TimingsTraceLog(
                    CarLog.TAG_TELEMETRY, TraceHelper.TRACE_TAG_CAR_SERVICE);
            mTelemetryThreadTraceLog.traceBegin("init");
            SystemInterface systemInterface = CarLocalServices.getService(SystemInterface.class);
            // starts metrics collection after CarService initializes.
            CarServiceUtils.runOnMain(this::startMetricsCollection);
            // full root directory path is /data/system/car/telemetry
            File rootDirectory = new File(systemInterface.getSystemCarDir(), TELEMETRY_DIR);
            // initialize all necessary components
            mUidMapper.init();
            mMetricsConfigStore = new MetricsConfigStore(rootDirectory);
            mResultStore = new ResultStore(mContext, rootDirectory);
            if (mSessionController == null) {
                mSessionController = new SessionController(
                        mContext, mCarPowerManagementService, mTelemetryHandler);
            }
            mPublisherFactory = mDependencies.getPublisherFactory(mCarPropertyService,
                    mTelemetryHandler, mContext, mSessionController, mResultStore, mUidMapper);
            if (mDataBroker == null) {
                mDataBroker = new DataBrokerImpl(mContext, mPublisherFactory, mResultStore,
                        mTelemetryThreadTraceLog);
            }
            mDataBroker.setDataBrokerListener(mDataBrokerListener);
            // TODO (b/233973826): Re-enable once SystemMonitor tune-up is complete.
            if (false) {
                ActivityManager activityManager = mContext.getSystemService(ActivityManager.class);
                mSystemMonitor = SystemMonitor.create(activityManager, mTelemetryHandler);
                mSystemMonitor.setSystemMonitorCallback(this::onSystemMonitorEvent);
            } else {
                Log.w(TAG, "Not creating mSystemMonitor due to bug 233973826");
            }
            mTelemetryThreadTraceLog.traceEnd();
            // save state at reboot and shutdown
            mOnShutdownReboot = new OnShutdownReboot(mContext);
            mOnShutdownReboot.init();
            mOnShutdownReboot.addAction((context, intent) -> release());
        });
    }

    @Override
    public void release() {
        if (mReleased) {
            return;
        }
        mReleased = true;
        mTelemetryHandler.post(() -> {
            mTelemetryThreadTraceLog.traceBegin("release");
            mResultStore.flushToDisk();
            mOnShutdownReboot.release();
            mSessionController.release();
            mUidMapper.release();
            mTelemetryThreadTraceLog.traceEnd();
        });
        CarServiceUtils.runOnLooperSync(mTelemetryThread.getLooper(), () -> {});
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarTelemetryService*");
        writer.println();
        // Print active configs with their interim results and errors.
        writer.println("Active Configs");
        writer.println();
        for (TelemetryProto.MetricsConfig config : mMetricsConfigStore.getActiveMetricsConfigs()) {
            writer.println("    Name: " + config.getName());
            writer.println("    Version: " + config.getVersion());
            PersistableBundle interimResult = mResultStore.getInterimResult(config.getName());
            if (interimResult != null) {
                writer.println("    Interim Result");
                writer.println("        Bundle keys: "
                        + Arrays.toString(interimResult.keySet().toArray()));
            }
            writer.println();
        }
        // Print info on stored final results.
        ArrayMap<String, MetricsReportList> finalResults = mResultStore.getAllMetricsReports();
        writer.println("Final Results");
        writer.println();
        for (int i = 0; i < finalResults.size(); i++) {
            writer.println("\tConfig name: " + finalResults.keyAt(i));
            MetricsReportList reportList = finalResults.valueAt(i);
            writer.println("\tTotal number of metrics reports: " + reportList.getReportCount());
            for (int j = 0; j < reportList.getReportCount(); j++) {
                writer.println("\tBundle keys for report " + j + ":");
                PersistableBundle report = MetricsReportProtoUtils.getBundle(reportList, j);
                writer.println("\t\t" + Arrays.toString(report.keySet().toArray()));
            }
            writer.println();
        }
        // Print info on stored errors. Configs are inactive after producing errors.
        ArrayMap<String, TelemetryProto.TelemetryError> errors = mResultStore.getAllErrorResults();
        writer.println("Errors");
        writer.println();
        for (int i = 0; i < errors.size(); i++) {
            writer.println("\tConfig name: " + errors.keyAt(i));
            TelemetryProto.TelemetryError error = errors.valueAt(i);
            writer.println("\tError");
            writer.println("\t\tType: " + error.getErrorType());
            writer.println("\t\tMessage: " + error.getMessage());
            if (error.hasStackTrace() && !error.getStackTrace().isEmpty()) {
                writer.println("\t\tStack trace: " + error.getStackTrace());
            }
            writer.println();
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    /**
     * Send a telemetry metrics config to the service.
     *
     * @param metricsConfigName name of the MetricsConfig.
     * @param config            the serialized bytes of a MetricsConfig object.
     * @param callback          to send status code to CarTelemetryManager.
     */
    @Override
    public void addMetricsConfig(@NonNull String metricsConfigName, @NonNull byte[] config,
            @NonNull ResultReceiver callback) {
        mContext.enforceCallingOrSelfPermission(
                Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE, "addMetricsConfig");
        mTelemetryHandler.post(() -> {
            mTelemetryThreadTraceLog.traceBegin("addMetricsConfig");
            int status = addMetricsConfigInternal(metricsConfigName, config);
            callback.send(status, null);
            mTelemetryThreadTraceLog.traceEnd();
        });
    }

    /** Adds the MetricsConfig and returns the status. */
    private int addMetricsConfigInternal(
            @NonNull String metricsConfigName, @NonNull byte[] config) {
        Slogf.d(CarLog.TAG_TELEMETRY,
                "Adding metrics config: " + metricsConfigName + " to car telemetry service");
        TelemetryProto.MetricsConfig metricsConfig;
        try {
            metricsConfig = TelemetryProto.MetricsConfig.parseFrom(config);
        } catch (InvalidProtocolBufferException e) {
            Slogf.e(CarLog.TAG_TELEMETRY, "Failed to parse MetricsConfig.", e);
            return STATUS_ADD_METRICS_CONFIG_PARSE_FAILED;
        }
        if (metricsConfig.getName().length() == 0) {
            Slogf.e(CarLog.TAG_TELEMETRY, "MetricsConfig name cannot be an empty string");
            return STATUS_ADD_METRICS_CONFIG_PARSE_FAILED;
        }
        if (!metricsConfig.getName().equals(metricsConfigName)) {
            Slogf.e(CarLog.TAG_TELEMETRY, "Argument config name " + metricsConfigName
                    + " doesn't match name in MetricsConfig (" + metricsConfig.getName() + ").");
            return STATUS_ADD_METRICS_CONFIG_PARSE_FAILED;
        }
        int status = mMetricsConfigStore.addMetricsConfig(metricsConfig);
        if (status != STATUS_ADD_METRICS_CONFIG_SUCCEEDED) {
            return status;
        }
        // If no error (config is added to the MetricsConfigStore), remove previously collected data
        // for this config and add config to the DataBroker for metrics collection.
        mResultStore.removeResult(metricsConfigName);
        mDataBroker.removeMetricsConfig(metricsConfigName);
        // add config to DataBroker could fail due to invalid metrics configurations, such as
        // containing an illegal field. An example is setting the read_interval_sec to 0 in
        // MemoryPublisher. The read_interval_sec must be at least 1.
        try {
            mDataBroker.addMetricsConfig(metricsConfigName, metricsConfig);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Invalid config, failed to add to DataBroker", e);
            removeMetricsConfig(metricsConfigName); // clean up
            return STATUS_ADD_METRICS_CONFIG_PARSE_FAILED;
        }
        // TODO(b/199410900): update logic once metrics configs have expiration dates
        return STATUS_ADD_METRICS_CONFIG_SUCCEEDED;
    }

    /**
     * Removes a metrics config based on the name. This will also remove outputs produced by the
     * MetricsConfig.
     *
     * @param metricsConfigName the unique identifier of a MetricsConfig.
     */
    @Override
    public void removeMetricsConfig(@NonNull String metricsConfigName) {
        mContext.enforceCallingOrSelfPermission(
                Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE, "removeMetricsConfig");
        mTelemetryHandler.post(() -> {
            if (DEBUG) {
                Slogf.d(CarLog.TAG_TELEMETRY, "Removing metrics config " + metricsConfigName
                        + " from car telemetry service");
            }
            mTelemetryThreadTraceLog.traceBegin("removeMetricsConfig");
            mMetricsConfigStore.removeMetricsConfig(metricsConfigName);
            mDataBroker.removeMetricsConfig(metricsConfigName);
            mResultStore.removeResult(metricsConfigName);
            mTelemetryThreadTraceLog.traceEnd();
        });
    }

    /**
     * Removes all MetricsConfigs. This will also remove all MetricsConfig outputs.
     */
    @Override
    public void removeAllMetricsConfigs() {
        mContext.enforceCallingOrSelfPermission(
                Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE, "removeAllMetricsConfigs");
        mTelemetryHandler.post(() -> {
            mTelemetryThreadTraceLog.traceBegin("removeAllMetricsConfig");
            Slogf.d(CarLog.TAG_TELEMETRY,
                    "Removing all metrics config from car telemetry service");
            mDataBroker.removeAllMetricsConfigs();
            mMetricsConfigStore.removeAllMetricsConfigs();
            mResultStore.removeAllResults();
            mTelemetryThreadTraceLog.traceEnd();
        });
    }

    /**
     * Sends telemetry reports associated with the given config name using the
     * {@link ICarTelemetryReportListener}.
     *
     * @param metricsConfigName the unique identifier of a MetricsConfig.
     * @param listener          to receive finished report or error.
     */
    @Override
    public void getFinishedReport(@NonNull String metricsConfigName,
            @NonNull ICarTelemetryReportListener listener) {
        mContext.enforceCallingOrSelfPermission(
                Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE, "getFinishedReport");
        mTelemetryHandler.post(() -> {
            if (DEBUG) {
                Slogf.d(CarLog.TAG_TELEMETRY,
                        "Getting report for metrics config " + metricsConfigName);
            }
            mTelemetryThreadTraceLog.traceBegin("getFinishedReport");
            MetricsReportList reportList;
            TelemetryProto.TelemetryError error;
            if ((reportList = mResultStore.getMetricsReports(metricsConfigName, true)) != null) {
                streamReports(listener, metricsConfigName, reportList);
            } else if (mResultStore.getInterimResult(metricsConfigName) != null) {
                sendResult(listener, metricsConfigName, /* reportFd = */ null, /* error = */null,
                        /* status = */ STATUS_GET_METRICS_CONFIG_INTERIM_RESULTS);
            } else if ((error = mResultStore.getErrorResult(metricsConfigName, true)) != null) {
                sendResult(listener, metricsConfigName, /* reportFd = */ null, /* error = */ error,
                        /* status = */ STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR);
            } else if (mMetricsConfigStore.containsConfig(metricsConfigName)) {
                sendResult(listener, metricsConfigName, /* reportFd = */ null, /* error = */ null,
                        /* status = */ STATUS_GET_METRICS_CONFIG_PENDING);
            } else {
                sendResult(listener, metricsConfigName, /* reportFd = */ null, /* error = */ null,
                        /* status = */ STATUS_GET_METRICS_CONFIG_DOES_NOT_EXIST);
            }
            mTelemetryThreadTraceLog.traceEnd();
        });
    }

    /**
     * Sends all script reports or errors using the {@link ICarTelemetryReportListener}.
     */
    @Override
    public void getAllFinishedReports(@NonNull ICarTelemetryReportListener listener) {
        mContext.enforceCallingOrSelfPermission(
                Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE, "getAllFinishedReports");
        mTelemetryHandler.post(() -> {
            if (DEBUG) {
                Slogf.d(CarLog.TAG_TELEMETRY, "Getting all reports");
            }
            mTelemetryThreadTraceLog.traceBegin("getAllFinishedReports");
            Set<String> finishedReports = mResultStore.getFinishedMetricsConfigNames();
            // TODO(b/236843813): Optimize sending multiple reports
            for (String configName : finishedReports) {
                MetricsReportList reportList =
                        mResultStore.getMetricsReports(configName, true);
                if (reportList != null) {
                    streamReports(listener, configName, reportList);
                    continue;
                }
                TelemetryProto.TelemetryError telemetryError =
                        mResultStore.getErrorResult(configName, true);
                sendResult(listener, configName, /* reportFd = */ null, telemetryError,
                        STATUS_GET_METRICS_CONFIG_RUNTIME_ERROR);
            }
            mTelemetryThreadTraceLog.traceEnd();
        });
    }

    /**
     * Sets a listener for report ready notifications.
     */
    @Override
    public void setReportReadyListener(@NonNull ICarTelemetryReportReadyListener listener) {
        mContext.enforceCallingOrSelfPermission(
                Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE, "setReportReadyListener");
        mTelemetryHandler.post(() -> {
            mReportReadyListener = listener;
            Set<String> configNames = mResultStore.getFinishedMetricsConfigNames();
            for (String name : configNames) {
                try {
                    mReportReadyListener.onReady(name);
                } catch (RemoteException e) {
                    Slogf.w(CarLog.TAG_TELEMETRY, "error with ICarTelemetryReportReadyListener", e);
                }
            }
        });
    }

    /**
     * Clears the listener to stop report ready notifications.
     */
    @Override
    public void clearReportReadyListener() {
        mContext.enforceCallingOrSelfPermission(
                Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE, "clearReportReadyListener");
        mTelemetryHandler.post(() -> mReportReadyListener = null);
    }

    /**
     * Invoked when a script produces a report or a runtime error.
     */
    private void onReportReady(@NonNull String metricsConfigName) {
        if (mReportReadyListener == null) {
            return;
        }
        try {
            mReportReadyListener.onReady(metricsConfigName);
        } catch (RemoteException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "error with ICarTelemetryReportReadyListener", e);
        }
    }

    /**
     * Returns the list of config names and versions. This methods is expected to be used only by
     * {@code CarShellCommand} class. Other usages are not supported.
     */
    @NonNull
    public List<String> getActiveMetricsConfigDetails() {
        return mMetricsConfigStore.getActiveMetricsConfigs().stream()
                .map((config) -> config.getName() + " version=" + config.getVersion())
                .collect(toList());
    }

    /**
     * Streams the reports in the reportList to the client using a pipe to prevent exceeding
     * binder memory limit.
     */
    private void streamReports(
            @NonNull ICarTelemetryReportListener listener,
            @NonNull String metricsConfigName,
            @NonNull MetricsReportList reportList) {
        if (reportList.getReportCount() == 0) {
            sendResult(listener, metricsConfigName, null, null, STATUS_GET_METRICS_CONFIG_PENDING);
            return;
        }
        // if the last report is produced via 'on_script_finished', the config is finished
        int getReportStatus =
                reportList.getReport(reportList.getReportCount() - 1).getIsLastReport()
                        ? STATUS_GET_METRICS_CONFIG_FINISHED
                        : STATUS_GET_METRICS_CONFIG_PENDING;
        ParcelFileDescriptor[] fds = null;
        try {
            fds = ParcelFileDescriptor.createPipe();
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to create pipe to stream reports", e);
            return;
        }
        // send the file descriptor to the client so it can start reading
        sendResult(listener, metricsConfigName, fds[0], /* error = */ null, getReportStatus);
        try (DataOutputStream dataOutputStream = new DataOutputStream(
                new ParcelFileDescriptor.AutoCloseOutputStream(fds[1]))) {
            for (MetricsReportContainer reportContainer : reportList.getReportList()) {
                ByteString reportBytes = reportContainer.getReportBytes();
                // write the report size in bytes to the pipe, so the read end of the pipe
                // knows how many bytes to read for this report
                dataOutputStream.writeInt(reportBytes.size());
                dataOutputStream.write(reportBytes.toByteArray());
            }
        } catch (IOException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "Failed to write reports to pipe", e);
        }
        // close the read end of the pipe, write end of the pipe should be auto-closed
        IoUtils.closeQuietly(fds[0]);
    }

    @Nullable
    private byte[] getBytes(@Nullable TelemetryProto.TelemetryError error) {
        if (error == null) {
            return null;
        }
        return error.toByteArray();
    }

    private void sendResult(
            @NonNull ICarTelemetryReportListener listener,
            @NonNull String metricsConfigName,
            @Nullable ParcelFileDescriptor reportFd,
            @Nullable TelemetryProto.TelemetryError error,
            @CarTelemetryManager.MetricsReportStatus int status) {
        try {
            listener.onResult(metricsConfigName, reportFd, getBytes(error), status);
        } catch (RemoteException e) {
            Slogf.w(CarLog.TAG_TELEMETRY, "error with ICarTelemetryReportListener", e);
        }
    }

    /**
     * Starts collecting data. Once data is sent by publishers, DataBroker will arrange scripts to
     * run. This method is called by some thread on executor service, therefore the work needs to
     * be posted on the telemetry thread.
     */
    private void startMetricsCollection() {
        mTelemetryHandler.post(() -> {
            for (TelemetryProto.MetricsConfig config :
                    mMetricsConfigStore.getActiveMetricsConfigs()) {
                try {
                    mDataBroker.addMetricsConfig(config.getName(), config);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    Slogf.w(CarLog.TAG_TELEMETRY,
                            "Loading MetricsConfig from disk failed, stopping MetricsConfig("
                                    + config.getName() + ") and storing error", e);
                    removeMetricsConfig(config.getName()); // clean up
                    TelemetryError error = TelemetryError.newBuilder()
                            .setErrorType(TelemetryError.ErrorType.PUBLISHER_FAILED)
                            .setMessage("Publisher failed when loading MetricsConfig from disk")
                            .setStackTrace(Log.getStackTraceString(e))
                            .build();
                    // this will remove the MetricsConfig from disk and clean up its associated
                    // subscribers and tasks from CarTelemetryService, and also notify the client
                    // that an error report is available for them
                    mDataBrokerListener.onReportFinished(config.getName(), error);
                }
            }
            // By this point all publishers are instantiated according to the active configs
            // and subscribed to session updates. The publishers are ready to handle session updates
            // that this call might trigger.
            mSessionController.initSession();
        });
    }

    /**
     * Listens to {@link SystemMonitorEvent} and changes the cut-off priority
     * for {@link DataBroker} such that only tasks with the same or more urgent
     * priority can be run.
     *
     * Highest priority is 0 and lowest is 100.
     *
     * @param event the {@link SystemMonitorEvent} received.
     */
    private void onSystemMonitorEvent(@NonNull SystemMonitorEvent event) {
        if (event.getCpuUsageLevel() == SystemMonitorEvent.USAGE_LEVEL_HI
                || event.getMemoryUsageLevel() == SystemMonitorEvent.USAGE_LEVEL_HI) {
            mDataBroker.setTaskExecutionPriority(TASK_PRIORITY_HI);
        } else if (event.getCpuUsageLevel() == SystemMonitorEvent.USAGE_LEVEL_MED
                || event.getMemoryUsageLevel() == SystemMonitorEvent.USAGE_LEVEL_MED) {
            mDataBroker.setTaskExecutionPriority(TASK_PRIORITY_MED);
        } else {
            mDataBroker.setTaskExecutionPriority(TASK_PRIORITY_LOW);
        }
    }

    /**
     * As a MetricsConfig completes its lifecycle, it should be cleaned up from the service.
     * It will be removed from the MetricsConfigStore, all subscribers should be unsubscribed,
     * and associated tasks should be removed from DataBroker.
     */
    private void cleanupMetricsConfig(String metricsConfigName) {
        mMetricsConfigStore.removeMetricsConfig(metricsConfigName);
        mResultStore.removeInterimResult(metricsConfigName);
        mDataBroker.removeMetricsConfig(metricsConfigName);
        mDataBroker.scheduleNextTask();
    }

    @VisibleForTesting
    Handler getTelemetryHandler() {
        return mTelemetryHandler;
    }

    @VisibleForTesting
    ResultStore getResultStore() {
        return mResultStore;
    }

    @VisibleForTesting
    MetricsConfigStore getMetricsConfigStore() {
        return mMetricsConfigStore;
    }
}
