package com.android.tv.feedbackconsent;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates and returns diagnostic data (such as logs, dumpsys, etc) from a device.
 */
final class TvFeedbackConsentDataCollector {

    private static final String TAG = TvFeedbackConsentDataCollector.class.getSimpleName();

    private List<String> mSystemLogs = new ArrayList<>(0);
    private final TvFeedbackConsentDataCollectorCallback mDataCollectorCallback;

    TvFeedbackConsentDataCollector(TvFeedbackConsentDataCollectorCallback callback) {
        mDataCollectorCallback = callback;
    }

    public List<String> getSystemLogs() {
        return mSystemLogs;
    }

    private void setSystemLogs(List<String> systemLogs) {
        this.mSystemLogs = systemLogs;
    }

    /**
     * Collects system logs through logcat (usually around 1-2 MB for the default 10,000 lines).
     */
    public void collectSystemLogs(long numLines) {
        List<String> systemLogsCommand =
                Arrays.asList("logcat", "-d", "-v", "time", "-t", String.valueOf(numLines));
        setSystemLogs(runCommand(systemLogsCommand));
        mDataCollectorCallback.onSystemLogsReady();
    }

    @NonNull
    private List<String> runCommand(List<String> command) {
        List<String> output = new ArrayList<>(0);
        try {
            Process proc = new ProcessBuilder(command).start();

            try (InputStream stream = proc.getInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line.trim());
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Caught exception while running command.", e);
        }
        return output;
    }

    /**
     * Interface defining callbacks for data collection.
     *
     * @hide
     */
    interface TvFeedbackConsentDataCollectorCallback {
        /**
         * Callback invoked when system Logs are ready
         */
        void onSystemLogsReady();
    }
}
