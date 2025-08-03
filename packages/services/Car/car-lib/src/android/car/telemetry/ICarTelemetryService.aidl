package android.car.telemetry;

import android.car.telemetry.ICarTelemetryReportListener;
import android.car.telemetry.ICarTelemetryReportReadyListener;
import android.os.ResultReceiver;

/**
 * Internal binder interface for {@code CarTelemetryService}, used by {@code CarTelemetryManager}.
 *
 * @hide
 */
interface ICarTelemetryService {

    /**
     * Adds telemetry MetricsConfigs to CarTelemetryService. Status code is sent to
     * CarTelemetryManager via ResultReceiver.
     */
    void addMetricsConfig(in String metricsConfigName, in byte[] metricsConfig,
            in ResultReceiver callback);

    /**
     * Removes a MetricsConfig based on the name. This will also remove outputs produced by the
     * MetricsConfig.
     */
    void removeMetricsConfig(in String metricsConfigName);

    /**
     * Removes all MetricsConfigs. This will also remove all MetricsConfig outputs.
     */
    void removeAllMetricsConfigs();

    /**
     * Sends finished telemetry reports or errors associated with the given name using the
     * {@code ICarTelemetryServiceListener}.
     */
    void getFinishedReport(in String metricsConfigName, in ICarTelemetryReportListener listener);

    /**
     * Sends all finished telemetry reports or errors using the {@code ICarTelemetryReportListener}.
     */
    void getAllFinishedReports(in ICarTelemetryReportListener listener);

    /**
     * Registers a listener for receiving notifications when a report or telemetry error is ready.
     */
    void setReportReadyListener(in ICarTelemetryReportReadyListener listener);

    /**
     * Clears listener to stop receiving notifications when a report or telemetry error is ready.
     */
    void clearReportReadyListener();
}