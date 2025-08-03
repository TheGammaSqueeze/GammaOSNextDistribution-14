package com.android.helpers;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MetricUtility consist of basic utility methods to construct the metrics
 * reported at the end of the test.
 */
public class MetricUtility {

    private static final String TAG = MetricUtility.class.getSimpleName();
    private static final String KEY_JOIN = "_";
    public static final String METRIC_SEPARATOR = ",";

    public static final int BUFFER_SIZE = 1024;
    private static final DecimalFormat DOUBLE_FORMAT = new DecimalFormat("#0.000001");

    /**
     * Append the given array of string to construct the final key used to track the metrics.
     *
     * @param keys to append using KEY_JOIN
     */
    public static String constructKey(String... keys) {
        return String.join(KEY_JOIN, keys);
    }

    /**
     * Add metric to the result map. If metric key already exist append the new metric.
     *
     * @param metricKey Unique key to track the metric.
     * @param metric metric to track.
     * @param resultMap map of all the metrics.
     */
    public static void addMetric(String metricKey, long metric, Map<String,
            StringBuilder> resultMap) {
        resultMap.compute(metricKey, (key, value) -> (value == null) ?
                new StringBuilder().append(metric) : value.append(METRIC_SEPARATOR).append(metric));
    }

    /**
     * Add metric to the result map. If metric key already exist append the new metric.
     *
     * @param metricKey Unique key to track the metric.
     * @param metric metric to track.
     * @param resultMap map of all the metrics.
     */
    public static void addMetric(
            String metricKey, double metric, Map<String, StringBuilder> resultMap) {
        resultMap.compute(
                metricKey,
                (key, value) ->
                        (value == null ? new StringBuilder() : value.append(METRIC_SEPARATOR))
                                .append(DOUBLE_FORMAT.format(metric)));
    }

    /**
     * Add metric to the result map. If metric key already exist increment the value by 1.
     *
     * @param metricKey Unique key to track the metric.
     * @param resultMap map of all the metrics.
     */
    public static void addMetric(String metricKey, Map<String,
            Integer> resultMap) {
        resultMap.compute(metricKey, (key, value) -> (value == null) ? 1 : value + 1);
    }

    /**
     * Get metric values from result map.
     *
     * @param metricKey Unique key to track the metric.
     * @param resultMap Map of all the metrics.
     * @return Double List of metric values for metric key
     */
    public static List<Double> getMetricDoubles(
            String metricKey, Map<String, StringBuilder> resultMap) {
        List<Double> result = new ArrayList<Double>();
        if (!resultMap.containsKey(metricKey)) {
            Log.e(TAG, String.format("No such metric key %s", metricKey));
            return result;
        } else {
            String value = resultMap.get(metricKey).toString();
            if (value.length() == 0) {
                Log.e(TAG, String.format("Missed value of metric key %s", metricKey));
                return result;
            } else {
                String[] values = value.split(METRIC_SEPARATOR);
                for (int i = 0; i < values.length; i++) {
                    try {
                        result.add(DOUBLE_FORMAT.parse(values[i]).doubleValue());
                    } catch (ParseException e) {
                        Log.e(
                                TAG,
                                String.format(
                                        "Error parsing value of metric key %s: #%d of value %s",
                                        metricKey, i, value));
                        return new ArrayList<Double>();
                    }
                }
            }
        }
        return result;
    }

    /**
     * Turn executeShellCommand into a blocking operation.
     *
     * @param command shell command to be executed.
     * @param instr used to run the shell command.
     * @return byte array of execution result
     */
    public static byte[] executeCommandBlocking(String command, Instrumentation instr) {
        try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(instr.getUiAutomation()
                .executeShellCommand(command));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[BUFFER_SIZE];
            int length;
            Log.i(TAG, "Start reading the data");
            while ((length = is.read(buf)) >= 0) {
                out.write(buf, 0, length);
            }
            Log.i(TAG, "Stop reading the data");
            return out.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Error executing: " + command, e);
            return null;
        }
    }

}
