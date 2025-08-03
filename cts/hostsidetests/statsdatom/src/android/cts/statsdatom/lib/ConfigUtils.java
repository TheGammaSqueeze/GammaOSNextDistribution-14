/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.cts.statsdatom.lib;

import com.android.internal.os.StatsdConfigProto.AtomMatcher;
import com.android.internal.os.StatsdConfigProto.EventMetric;
import com.android.internal.os.StatsdConfigProto.FieldFilter;
import com.android.internal.os.StatsdConfigProto.FieldMatcher;
import com.android.internal.os.StatsdConfigProto.FieldValueMatcher;
import com.android.internal.os.StatsdConfigProto.GaugeMetric;
import com.android.internal.os.StatsdConfigProto.MessageMatcher;
import com.android.internal.os.StatsdConfigProto.Position;
import com.android.internal.os.StatsdConfigProto.SimpleAtomMatcher;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.internal.os.StatsdConfigProto.TimeUnit;
import com.android.os.AtomsProto.Atom;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import com.google.common.io.Files;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

public final class ConfigUtils {
    public static final long CONFIG_ID = "cts_config".hashCode(); // evaluates to -1572883457
    public static final String CONFIG_ID_STRING = String.valueOf(CONFIG_ID);
    public static final String SHELL_UID_STRING = "2000";

    // Attribution chains are the first field in atoms.
    private static final int ATTRIBUTION_CHAIN_FIELD_NUMBER = 1;
    // Uids are the first field in attribution nodes.
    private static final int ATTRIBUTION_NODE_UID_FIELD_NUMBER = 1;
    // Uids as standalone fields are the first field in atoms.
    private static final int UID_FIELD_NUMBER = 1;

    // adb shell commands
    private static final String UPDATE_CONFIG_CMD = "cmd stats config update";
    private static final String REMOVE_CONFIG_CMD = "cmd stats config remove";

    /**
     * Create a new config with common fields filled out, such as allowed log sources and
     * default pull packages.
     *
     * @param pkgName test app package from which pushed atoms will be sent
     */
    public static StatsdConfig.Builder createConfigBuilder(String pkgName) {
        return StatsdConfig.newBuilder()
                .setId(CONFIG_ID)
                .addAllowedLogSource("AID_SYSTEM")
                .addAllowedLogSource("AID_BLUETOOTH")
                .addAllowedLogSource("com.android.bluetooth")
                .addAllowedLogSource("AID_LMKD")
                .addAllowedLogSource("AID_MEDIA")
                .addAllowedLogSource("AID_RADIO")
                .addAllowedLogSource("AID_ROOT")
                .addAllowedLogSource("AID_STATSD")
                .addAllowedLogSource("com.android.systemui")
                .addAllowedLogSource(pkgName)
                .addDefaultPullPackages("AID_RADIO")
                .addDefaultPullPackages("AID_SYSTEM")
                .addWhitelistedAtomIds(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER);
    }

    /**
     * Adds an event metric for the specified atom. The atom should contain a uid either within
     * an attribution chain or as a standalone field. Only those atoms which contain the uid of
     * the test app will be included in statsd's report.
     *
     * @param config
     * @param atomId index of atom within atoms.proto
     * @param uidInAttributionChain if true, the uid is part of the attribution chain; if false,
     *    uid is a standalone field
     * @param pkgName test app package from which atom will be logged
     */
    public static void addEventMetricForUidAtom(StatsdConfig.Builder config, int atomId,
            boolean uidInAttributionChain, String pkgName) {
        FieldValueMatcher.Builder fvm = createUidFvm(uidInAttributionChain, pkgName);
        addEventMetric(config, atomId, Arrays.asList(fvm));
    }

    /**
     * Adds an event metric for the specified atom. All such atoms received by statsd will be
     * included in the report. If only atoms meeting certain constraints should be added to the
     * report, use #addEventMetric(int atomId, List<FieldValueMatcher.Builder> fvms instead.
     *
     * @param config
     * @param atomId index of atom within atoms.proto
     */
    public static void addEventMetric(StatsdConfig.Builder config, int atomId) {
        addEventMetric(config, atomId, /*fvms=*/null);
    }

    /**
     * Adds an event metric to the config for the specified atom. The atom's fields must meet
     * the constraints specified in fvms for the atom to be included in statsd's report.
     *
     * @param config
     * @param atomId index of atom within atoms.proto
     * @param fvms list of constraints that atoms are filtered on
     */
    public static void addEventMetric(StatsdConfig.Builder config, int atomId,
            @Nullable List<FieldValueMatcher.Builder> fvms) {
        final String matcherName = "Atom matcher" + System.nanoTime();
        final String eventName = "Event " + System.nanoTime();

        SimpleAtomMatcher.Builder sam = SimpleAtomMatcher.newBuilder().setAtomId(atomId);
        if (fvms != null) {
            for (FieldValueMatcher.Builder fvm : fvms) {
                sam.addFieldValueMatcher(fvm);
            }
        }

        config.addAtomMatcher(AtomMatcher.newBuilder()
                .setId(matcherName.hashCode())
                .setSimpleAtomMatcher(sam));
        config.addEventMetric(EventMetric.newBuilder()
                .setId(eventName.hashCode())
                .setWhat(matcherName.hashCode()));
    }

    /**
     * Adds a gauge metric for a pulled atom with a uid field to the config. The atom will be
     * pulled when an AppBreadcrumbReported atom is logged to statsd, and only those pulled atoms
     * containing the uid of the test app will be included in statsd's report.
     *
     * @param config
     * @param atomId index of atom within atoms.proto
     * @param uidInAttributionChain if true, the uid is part of the attribution chain; if false, uid
     *    is a standalone field
     * @param pkgName test app package from which atom will be logged
     */
    public static void addGaugeMetricForUidAtom(StatsdConfig.Builder config, int atomId,
            boolean uidInAttributionChain, String pkgName) {
        addGaugeMetricInternal(config, atomId, /*filterByUid=*/true, uidInAttributionChain, pkgName,
                /*dimensionsInWhat=*/null);
    }

    /**
     * Equivalent to addGaugeMetricForUidAtom except that the output in the report is sliced by the
     * specified dimensions.
     *
     * @param dimensionsInWhat dimensions to slice the output by
     */
    public static void addGaugeMetricForUidAtomWithDimensions(StatsdConfig.Builder config,
            int atomId, boolean uidInAttributionChain, String pkgName,
            FieldMatcher.Builder dimensionsInWhat) {
        addGaugeMetricInternal(config, atomId, /*filterByUid=*/true, uidInAttributionChain, pkgName,
                dimensionsInWhat);
    }

    /**
     * Adds a gauge metric for a pulled atom to the config. The atom will be pulled when an
     * AppBreadcrumbReported atom is logged to statsd.
     *
     * @param config
     * @param atomId index of the atom within atoms.proto
     * @param dimensionsInWhat dimensions to slice the output by
     */
    public static void addGaugeMetric(StatsdConfig.Builder config, int atomId) {
        addGaugeMetricInternal(config, atomId, /*filterByUid=*/false,
                /*uidInAttributionChain=*/false, /*pkgName=*/null, /*dimensionsInWhat=*/null);
    }

    /**
     * Equivalent to addGaugeMetric except that output in the report is sliced by the specified
     * dimensions.
     *
     * @param dimensionsInWhat dimensions to slice the output by
     */
    public static void addGaugeMetricWithDimensions(StatsdConfig.Builder config, int atomId,
            FieldMatcher.Builder dimensionsInWhat) {
        addGaugeMetricInternal(config, atomId, /*filterByUid=*/false,
                /*uidInAttributionChain=*/false, /*pkgName=*/null, dimensionsInWhat);
    }

    private static void addGaugeMetricInternal(StatsdConfig.Builder config, int atomId,
            boolean filterByUid, boolean uidInAttributionChain, @Nullable String pkgName,
            @Nullable FieldMatcher.Builder dimensionsInWhat) {
        final String gaugeName = "Gauge metric " + System.nanoTime();
        final String whatName = "What atom matcher " + System.nanoTime();
        final String triggerName = "Trigger atom matcher " + System.nanoTime();

        // Add atom matcher for "what"
        SimpleAtomMatcher.Builder whatMatcher = SimpleAtomMatcher.newBuilder().setAtomId(atomId);
        if (filterByUid && pkgName != null) {
            whatMatcher.addFieldValueMatcher(createUidFvm(uidInAttributionChain, pkgName));
        }
        config.addAtomMatcher(AtomMatcher.newBuilder()
                .setId(whatName.hashCode())
                .setSimpleAtomMatcher(whatMatcher));

        // Add atom matcher for trigger event
        SimpleAtomMatcher.Builder triggerMatcher = SimpleAtomMatcher.newBuilder()
                .setAtomId(Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER);
        config.addAtomMatcher(AtomMatcher.newBuilder()
                .setId(triggerName.hashCode())
                .setSimpleAtomMatcher(triggerMatcher));

        // Add gauge metric
        GaugeMetric.Builder gaugeMetric = GaugeMetric.newBuilder()
                .setId(gaugeName.hashCode())
                .setWhat(whatName.hashCode())
                .setTriggerEvent(triggerName.hashCode())
                .setGaugeFieldsFilter(FieldFilter.newBuilder().setIncludeAll(true).build())
                .setBucket(TimeUnit.CTS)
                .setSamplingType(GaugeMetric.SamplingType.FIRST_N_SAMPLES)
                .setMaxNumGaugeAtomsPerBucket(10_000);
        if (dimensionsInWhat != null) {
            gaugeMetric.setDimensionsInWhat(dimensionsInWhat.build());
        }
        config.addGaugeMetric(gaugeMetric.build());
    }

    /**
     * Creates a FieldValueMatcher.Builder object that matches atoms whose uid field is equal to
     * the uid of pkgName.
     *
     * @param uidInAttributionChain if true, the uid is part of the attribution chain; if false, uid
     * is a standalone field
     * @param pkgName test app package from which atom will be logged
     */
    public static FieldValueMatcher.Builder createUidFvm(boolean uidInAttributionChain,
            String pkgName) {
        if (uidInAttributionChain) {
            FieldValueMatcher.Builder nodeFvm = createFvm(ATTRIBUTION_NODE_UID_FIELD_NUMBER)
                    .setEqString(pkgName);
            return createFvm(ATTRIBUTION_CHAIN_FIELD_NUMBER)
                    .setPosition(Position.ANY)
                    .setMatchesTuple(MessageMatcher.newBuilder().addFieldValueMatcher(nodeFvm));
        } else {
            return createFvm(UID_FIELD_NUMBER).setEqString(pkgName);
        }
    }

    /**
     * Creates a FieldValueMatcher.Builder for a particular field. Note that the value still needs
     * to be set.
     *
     * @param fieldNumber index of field within the atom
     */
    public static FieldValueMatcher.Builder createFvm(int fieldNumber) {
        return FieldValueMatcher.newBuilder().setField(fieldNumber);
    }

    /**
     * Upload a config to statsd.
     */
    public static void uploadConfig(ITestDevice device, StatsdConfig.Builder configBuilder)
            throws Exception {
        StatsdConfig config = configBuilder.build();
        CLog.d("Uploading the following config to statsd:\n" + config.toString());

        File configFile = File.createTempFile("statsdconfig", ".config");
        try {
            Files.write(config.toByteArray(), configFile);

            // Push config to temporary location
            String remotePath = "/data/local/tmp/" + configFile.getName();
            device.pushFile(configFile, remotePath);

            // Send config to statsd
            device.executeShellCommand(
                    String.join(" ", "cat", remotePath, "|", UPDATE_CONFIG_CMD, SHELL_UID_STRING,
                            CONFIG_ID_STRING));

            // Remove config from temporary location
            device.executeShellCommand("rm " + remotePath);
        } finally {
            configFile.delete();
        }

        // Sleep for a bit so that statsd receives config before more work is done within the test.
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);
    }

    /**
     * Removes any pre-existing CTS configs from statsd.
     */
    public static void removeConfig(ITestDevice device) throws Exception {
        device.executeShellCommand(String.join(" ", REMOVE_CONFIG_CMD, SHELL_UID_STRING,
                    CONFIG_ID_STRING));
    }

    public static void uploadConfigForPushedAtomWithUid(ITestDevice device, String pkgName,
            int atomId,
            boolean useUidAttributionChain) throws Exception {
        StatsdConfig.Builder config = createConfigBuilder(pkgName);
        addEventMetricForUidAtom(config, atomId, useUidAttributionChain, pkgName);
        uploadConfig(device, config);
    }

    public static void uploadConfigForPulledAtomWithUid(ITestDevice device, String pkgName,
            int atomId,
            boolean useUidAttributionChain) throws Exception {
        StatsdConfig.Builder config = createConfigBuilder(pkgName);
        addGaugeMetricForUidAtom(config, atomId, useUidAttributionChain, pkgName);
        uploadConfig(device, config);
    }

    public static void uploadConfigForPushedAtom(ITestDevice device, String pkgName, int atomId)
            throws Exception {
        StatsdConfig.Builder config = createConfigBuilder(pkgName);
        addEventMetric(config, atomId);
        uploadConfig(device, config);
    }

    public static void uploadConfigForPushedAtoms(ITestDevice device, String pkgName, int[] atomIds)
            throws Exception {
        StatsdConfig.Builder config = createConfigBuilder(pkgName);
        for (int atomId : atomIds) {
            addEventMetric(config, atomId);
        }
        uploadConfig(device, config);
    }

    public static void uploadConfigForPulledAtom(ITestDevice device, String pkgName, int atomId)
            throws Exception {
        StatsdConfig.Builder config = createConfigBuilder(pkgName);
        addGaugeMetric(config, atomId);
        uploadConfig(device, config);
    }

    private ConfigUtils() {
    }
}
