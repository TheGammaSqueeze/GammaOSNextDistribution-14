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

package android.view.inputmethod.cts.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FileUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.internal.os.nano.StatsdConfigProto;
import com.android.internal.os.nano.StatsdConfigProto.AtomMatcher;
import com.android.internal.os.nano.StatsdConfigProto.EventMetric;
import com.android.internal.os.nano.StatsdConfigProto.FieldValueMatcher;
import com.android.internal.os.nano.StatsdConfigProto.MessageMatcher;
import com.android.internal.os.nano.StatsdConfigProto.SimpleAtomMatcher;
import com.android.internal.os.nano.StatsdConfigProto.StatsdConfig;
import com.android.os.nano.AtomsProto;
import com.android.os.nano.StatsLog.ConfigMetricsReportList;
import com.android.os.nano.StatsLog.EventMetricData;

import com.google.protobuf.nano.CodedOutputByteBufferNano;
import com.google.protobuf.nano.InvalidProtocolBufferNanoException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Utility methods for testing metric tracking and logging through {@code statsd}.
 */
public final class MetricsRecorder {

    private static final String TAG = "MetricsRecorder";

    private static final String DUMP_REPORT_CMD = "cmd stats dump-report";

    private static final String UPDATE_CONFIG_CMD = "cmd stats config update";

    private static final String REMOVE_CONFIG_CMD = "cmd stats config remove";

    private static final long CONFIG_ID = "cts_config".hashCode();

    private static final String CONFIG_ID_STRING = String.valueOf(CONFIG_ID);

    /** Index of the input file descriptor. */
    private static final int OUT_DESCRIPTOR_INDEX = 0;

    /** Index of the output file descriptor. */
    private static final int IN_DESCRIPTOR_INDEX = 1;

    /** Index of the error file descriptor. */
    private static final int ERR_DESCRIPTOR_INDEX = 2;

    // Attribution chains are the first field in atoms.
    private static final int ATTRIBUTION_CHAIN_FIELD_NUMBER = 1;

    // UIDs are the first field in attribution nodes.
    private static final int ATTRIBUTION_NODE_UID_FIELD_NUMBER = 1;

    // UIDs as standalone fields are the first field in atoms.
    private static final int UID_FIELD_NUMBER = 1;

    // Prevent instantiating utility class.
    private MetricsRecorder() {}

    /**
     * Adds an event metric for the specified pushed atom, and uploads the config to statsd.
     *
     * @param pkgName test app package name from which atoms will be logged.
     * @param atomId index of atom within atoms.proto.
     * @param useUidAttributionChain if true, the uid is part of the attribution chain;
     *                               if false, uid is a standalone field.
     *
     * @throws AssertionError if the upload fails.
     * @throws IllegalStateException if the config was only partially written.
     * @throws IOException if writing the config fails.
     * @throws RuntimeException if closing the FileDescriptors,
     * or writing the config to the command fails.
     */
    public static void uploadConfigForPushedAtomWithUid(@NonNull String pkgName, int atomId,
            boolean useUidAttributionChain) throws AssertionError, IOException, RuntimeException {
        final var config = createConfig(pkgName);
        addEventMetricForUidAtom(config, atomId, useUidAttributionChain, pkgName);
        uploadConfig(config);
    }

    /**
     * Create a new config with common fields filled out, such as allowed log sources and
     * default pull packages.
     *
     * @param pkgName test app package name from which atoms will be logged.
     */
    private static StatsdConfig createConfig(String pkgName) {
        final var config = new StatsdConfig();
        config.id = CONFIG_ID;
        config.allowedLogSource = new String[]{
                "AID_SYSTEM",
                "AID_BLUETOOTH",
                "com.android.bluetooth",
                "AID_LMKD",
                "AID_MEDIA",
                "AID_RADIO",
                "AID_ROOT",
                "AID_STATSD",
                "com.android.systemui",
                pkgName,
        };
        config.defaultPullPackages = new String[]{
                "AID_RADIO",
                "AID_SYSTEM",
        };
        config.whitelistedAtomIds = new int[]{
                AtomsProto.Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER,
        };

        return config;
    }

    /**
     * Upload a config to statsd.
     *
     * @param config config to upload.
     *
     * @throws AssertionError if the upload fails.
     * @throws IllegalStateException if the config was only partially written.
     * @throws IOException if writing the config fails.
     * @throws RuntimeException if closing the FileDescriptors,
     * or writing the config to the command fails.
     */
    private static void uploadConfig(StatsdConfig config)
            throws AssertionError, IllegalStateException, IOException, RuntimeException {
        final var bytes = new byte[config.getSerializedSize()];
        final var buffer = CodedOutputByteBufferNano.newInstance(bytes);

        config.writeTo(buffer);
        buffer.checkNoSpaceLeft();

        final var cmd = String.join(" ", UPDATE_CONFIG_CMD, CONFIG_ID_STRING);
        final var output = runShellCommandWithStdIn(
                InstrumentationRegistry.getInstrumentation().getUiAutomation(), cmd, bytes);
        assertThat(output).isEmpty();
    }

    /**
     * Executes a shell command using shell user identity, and return the standard output in string.
     *
     * @apiNote Calling this function requires API level 21 or above.
     *
     * @param automation {@link UiAutomation} instance, obtained from a test running in
     *                   instrumentation framework.
     * @param cmd the command to run.
     * @param stdInBytes the byte array to pass as input to the command.
     *
     * @return the standard output of the command.
     *
     * @throws AssertionError if the shell command fails.
     * @throws RuntimeException if closing the FileDescriptors,
     * or writing the input argument to the command fails.
     */
    @NonNull
    private static String runShellCommandWithStdIn(UiAutomation automation, @NonNull String cmd,
            @NonNull byte[] stdInBytes) throws AssertionError, RuntimeException {
        try {
            Log.v(TAG, "Running command: " + cmd);
            final var fds = automation.executeShellCommandRwe(cmd);
            final var fdOut = fds[OUT_DESCRIPTOR_INDEX];
            final var fdIn = fds[IN_DESCRIPTOR_INDEX];
            final var fdErr = fds[ERR_DESCRIPTOR_INDEX];

            // Nested try to allow resuming execution after stdIn error.
            if (fdIn != null) {
                try (var fos = new AutoCloseOutputStream(fdIn)) {
                    fos.write(stdInBytes);
                } catch (Exception e) {
                    // Ignore.
                }
            }

            final String out;
            try (var fis = new AutoCloseInputStream(fdOut)) {
                out = new String(FileUtils.readInputStreamFully(fis));
            }

            final String err;
            try (var fis = new AutoCloseInputStream(fdErr)) {
                err = new String(FileUtils.readInputStreamFully(fis));
            }

            if (!err.isEmpty()) {
                fail("Command failed:\n$ " + cmd
                        + "\n\nstderr:\n" + err
                        + "\n\nstdout:\n" + out);
            }

            return out;
        } catch (IOException e) {
            fail("Failed reading command output: " + e);
            return "";
        }
    }

    /**
     * Adds an event metric for the specified atom. The atom should contain a uid either within
     * an attribution chain or as a standalone field. Only those atoms which contain the uid of
     * the test app will be included in statsd's report.
     *
     * @param config config to upload.
     * @param atomId index of atom within atoms.proto.
     * @param uidInAttributionChain if true, the uid is part of the attribution chain;
     *                              if false, uid is a standalone field.
     * @param pkgName test app package name from which atoms will be logged.
     */
    private static void addEventMetricForUidAtom(StatsdConfig config, int atomId,
            boolean uidInAttributionChain, @NonNull String pkgName) {
        final var fvm = createUidFvm(uidInAttributionChain, pkgName);
        addEventMetric(config, atomId, Collections.singletonList(fvm));
    }

    /**
     * Adds an event metric to the config for the specified atom. The atom's fields must meet
     * the constraints specified in fvms for the atom to be included in statsd's report.
     *
     * @param config config to upload.
     * @param atomId index of atom within atoms.proto.
     * @param fvms list of constraints that atoms are filtered on.
     */
    private static void addEventMetric(StatsdConfig config, int atomId,
            @NonNull List<FieldValueMatcher> fvms) {
        final long nanoTime = System.nanoTime();
        final var matcherName = "Atom matcher" + nanoTime;
        final var eventName = "Event " + nanoTime;

        final var sam = new SimpleAtomMatcher();
        sam.atomId = atomId;
        sam.fieldValueMatcher = fvms.toArray(new FieldValueMatcher[]{});

        final var atomMatcher = new AtomMatcher();
        atomMatcher.id = matcherName.hashCode();
        atomMatcher.setSimpleAtomMatcher(sam);

        config.atomMatcher = new AtomMatcher[]{
                atomMatcher,
        };

        final var eventMetric = new EventMetric();
        eventMetric.id = eventName.hashCode();
        eventMetric.what = matcherName.hashCode();

        config.eventMetric = new EventMetric[]{
                eventMetric,
        };
    }

    /**
     * Creates a FieldValueMatcher object that matches atoms whose uid field is equal to
     * the uid of pkgName.
     *
     * @param uidInAttributionChain if true, the uid is part of the attribution chain;
     *                              if false, uid is a standalone field.
     * @param pkgName test app package name from which atoms will be logged.
     */
    private static FieldValueMatcher createUidFvm(boolean uidInAttributionChain,
            @NonNull String pkgName) {
        if (uidInAttributionChain) {
            final var nodeFvm = createFvm(ATTRIBUTION_NODE_UID_FIELD_NUMBER).setEqString(pkgName);
            final var chainFvm = createFvm(ATTRIBUTION_CHAIN_FIELD_NUMBER);
            chainFvm.position = StatsdConfigProto.ANY;

            final var messageMatcher = new MessageMatcher();
            messageMatcher.fieldValueMatcher = new FieldValueMatcher[] {
                    nodeFvm,
            };

            return chainFvm.setMatchesTuple(messageMatcher);
        } else {
            return createFvm(UID_FIELD_NUMBER).setEqString(pkgName);
        }
    }

    /**
     * Creates a FieldValueMatcher for a particular field.
     *
     * <p>Note that the value still needs to be set.</p>
     *
     * @param fieldNumber index of field within the atom.
     */
    private static FieldValueMatcher createFvm(int fieldNumber) {
        final var fvm = new FieldValueMatcher();
        fvm.field = fieldNumber;
        return fvm;
    }

    /**
     * Removes any pre-existing CTS configs from statsd.
     */
    public static void removeConfig() {
        final var cmd = String.join(" ", REMOVE_CONFIG_CMD, CONFIG_ID_STRING);
        final var output = SystemUtil.runShellCommandOrThrow(cmd);
        assertThat(output).isEmpty();
    }

    /**
     * Delete all pre-existing reports corresponding to the CTS config.
     *
     * @throws Exception if fetching and parsing the statsd report fails.
     */
    public static void clearReports() throws Exception {
        getReportList();
    }

    /**
     * Retrieves the ConfigMetricsReports corresponding to the CTS config from statsd.
     *
     * @implNote Calling this functions deletes the report from statsd.
     *
     * @throws Exception if fetching and parsing the statsd report fails.
     */
    private static ConfigMetricsReportList getReportList() throws Exception {
        try {
            final var cmd = String.join(" ", DUMP_REPORT_CMD, CONFIG_ID_STRING,
                    "--include_current_bucket", "--proto");
            final var output = SystemUtil.runShellCommandByteOutput(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(), cmd);

            return ConfigMetricsReportList.parseFrom(output);
        } catch (InvalidProtocolBufferNanoException e) {
            throw new Exception("Failed to fetch and parse the statsd output report. Perhaps there"
                    + "is not a valid statsd config, id=" + CONFIG_ID + ".", e);
        }
    }

    /**
     * Returns a list of event metrics, which is sorted by timestamp, from the statsd report.
     *
     * @implNote Calling this function deletes the report from statsd.
     *
     * @throws Exception if fetching and parsing the statsd report fails.
     */
    public static List<EventMetricData> getEventMetricDataList() throws Exception {
        final var reportList = getReportList();
        return getEventMetricDataList(reportList);
    }

    /**
     * Extracts and sorts the EventMetricData from the given ConfigMetricsReportList (which must
     * contain a single report) and sorts the atoms by timestamp within the report.
     */
    private static List<EventMetricData> getEventMetricDataList(
            ConfigMetricsReportList reportList) {
        assertThat(reportList.reports.length).isEqualTo(1);
        final var report = reportList.reports[0];

        final var data = new ArrayList<EventMetricData>();
        Log.v(TAG, Arrays.toString(report.metrics));
        for (final var metric: report.metrics) {
            final var eventMetrics = metric.getEventMetrics();
            if (eventMetrics != null) {
                for (final var eventMetricData: eventMetrics.data) {
                    if (eventMetricData.atom != null) {
                        data.add(eventMetricData);
                    } else {
                        data.addAll(backfillAggregatedAtomsInEventMetric(eventMetricData));
                    }
                }
            }
        }
        data.sort(Comparator.comparing(e-> e.elapsedTimestampNanos));

        return data;
    }

    private static List<EventMetricData> backfillAggregatedAtomsInEventMetric(
            EventMetricData metricData) {
        if (metricData.aggregatedAtomInfo == null) {
            return Collections.emptyList();
        }

        final var data = new ArrayList<EventMetricData>();
        final var atomInfo = metricData.aggregatedAtomInfo;
        for (long timestamp: atomInfo.elapsedTimestampNanos) {
            final var newMetricData = new EventMetricData();
            newMetricData.atom = atomInfo.atom;
            newMetricData.elapsedTimestampNanos = timestamp;
            data.add(newMetricData);
        }

        return data;
    }
}
