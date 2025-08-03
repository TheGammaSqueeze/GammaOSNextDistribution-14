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
package com.android.statsd.shelltools.testdrive;

import com.android.internal.os.StatsdConfigProto;
import com.android.internal.os.StatsdConfigProto.AtomMatcher;
import com.android.internal.os.StatsdConfigProto.EventMetric;
import com.android.internal.os.StatsdConfigProto.FieldFilter;
import com.android.internal.os.StatsdConfigProto.GaugeMetric;
import com.android.internal.os.StatsdConfigProto.PullAtomPackages;
import com.android.internal.os.StatsdConfigProto.SimpleAtomMatcher;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.internal.os.StatsdConfigProto.TimeUnit;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog;
import com.android.os.StatsLog.ConfigMetricsReport;
import com.android.os.StatsLog.ConfigMetricsReportList;
import com.android.os.StatsLog.StatsLogReport;
import com.android.os.telephony.qns.QnsExtensionAtoms;
import com.android.statsd.shelltools.Utils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestDrive {

    private static final int METRIC_ID_BASE = 1111;
    private static final long ATOM_MATCHER_ID_BASE = 1234567;
    private static final long APP_BREADCRUMB_MATCHER_ID = 1111111;
    private static final int PULL_ATOM_START = 10000;
    private static final int MAX_PLATFORM_ATOM_TAG = 100000;
    private static final int VENDOR_PULLED_ATOM_START_TAG = 150000;
    private static final long CONFIG_ID = 54321;
    private static final String[] ALLOWED_LOG_SOURCES = {
            "AID_GRAPHICS",
            "AID_INCIDENTD",
            "AID_STATSD",
            "AID_RADIO",
            "com.android.systemui",
            "com.android.vending",
            "AID_SYSTEM",
            "AID_ROOT",
            "AID_BLUETOOTH",
            "AID_LMKD",
            "com.android.managedprovisioning",
            "AID_MEDIA",
            "AID_NETWORK_STACK",
            "com.google.android.providers.media.module",
            "com.android.imsserviceentitlement",
            "com.google.android.cellbroadcastreceiver",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.markup",
            "com.android.art",
            "com.google.android.art",
            "AID_KEYSTORE",
            "AID_VIRTUALIZATIONSERVICE",
            "com.google.android.permissioncontroller",
            "AID_NFC",
            "AID_SECURE_ELEMENT",
            "com.google.android.wearable.media.routing",
            "com.google.android.healthconnect.controller",
            "com.android.telephony.qns",
            "com.android.car",
    };
    private static final String[] DEFAULT_PULL_SOURCES = {
            "AID_KEYSTORE", "AID_RADIO", "AID_SYSTEM",
    };
    private static final Logger LOGGER = Logger.getLogger(TestDrive.class.getName());
    private static final String HW_ATOMS_PROTO_FILEPATH =
            "hardware/google/pixel/pixelstats/pixelatoms.proto";

    @VisibleForTesting
    String mDeviceSerial = null;
    @VisibleForTesting
    Dumper mDumper = new BasicDumper();
    boolean mPressToContinue = false;
    Integer mReportCollectionDelayMillis = 60_000;
    List<String> mProtoIncludes = new ArrayList<>();

    public static void main(String[] args) {
        final Configuration configuration = new Configuration();
        final TestDrive testDrive = new TestDrive();
        Utils.setUpLogger(LOGGER, false);

        if (!testDrive.processArgs(
                configuration, args, Utils.getDeviceSerials(LOGGER),
                Utils.getDefaultDevice(LOGGER))) {
            return;
        }

        final ConfigMetricsReportList reports =
                testDrive.testDriveAndGetReports(
                        configuration.createConfig(),
                        configuration.hasPulledAtoms(),
                        configuration.hasPushedAtoms());
        if (reports != null) {
            configuration.dumpMetrics(reports, testDrive.mDumper);
        }
    }

    static void printUsageMessage() {
        LOGGER.severe("Usage: ./test_drive [options] <atomId1> <atomId2> ... <atomIdN>");
        LOGGER.severe("OPTIONS");
        LOGGER.severe("-h, --help");
        LOGGER.severe("\tPrint this message");
        LOGGER.severe("-one");
        LOGGER.severe("\tCreating one event metric to catch all pushed atoms");
        LOGGER.severe("-i");
        LOGGER.severe("\tPath to proto file to include (pixelatoms.proto, etc.)");
        LOGGER.severe("\tPath is absolute or relative to current dir or to ANDROID_BUILD_TOP");
        LOGGER.severe("-terse");
        LOGGER.severe("\tTerse output format.");
        LOGGER.severe("-p additional_allowed_packages_csv");
        LOGGER.severe("\tAllows collection atoms from an additional packages");
        LOGGER.severe("-s DEVICE_SERIAL_NUMBER");
        LOGGER.severe("\tDevice serial number to use for adb communication");
        LOGGER.severe("-e");
        LOGGER.severe("\tWait for Enter key press before collecting report");
        LOGGER.severe("-d delay_ms");
        LOGGER.severe("\tWait for delay_ms before collecting report, default is 60000 ms");
        LOGGER.severe("-v");
        LOGGER.severe("\tDebug logging level");
    }

    boolean processArgs(
            Configuration configuration,
            String[] args,
            List<String> connectedDevices,
            String defaultDevice) {
        if (args.length < 1) {
            printUsageMessage();
            return false;
        }

        int first_arg = 0;
        // Consume all flags, which must precede all atoms
        for (; first_arg < args.length; ++first_arg) {
            String arg = args[first_arg];
            int remaining_args = args.length - first_arg;
            if (remaining_args >= 2 && arg.equals("-one")) {
                LOGGER.info("Creating one event metric to catch all pushed atoms.");
                configuration.mOnePushedAtomEvent = true;
            } else if (remaining_args >= 2 && arg.equals("-terse")) {
                LOGGER.info("Terse output format.");
                mDumper = new TerseDumper();
            } else if (remaining_args >= 3 && arg.equals("-p")) {
                Collections.addAll(configuration.mAdditionalAllowedPackages,
                    args[++first_arg].split(","));
            } else if (remaining_args >= 3 && arg.equals("-i")) {
                mProtoIncludes.add(args[++first_arg]);
            } else if (remaining_args >= 3 && arg.equals("-s")) {
                mDeviceSerial = args[++first_arg];
            } else if (remaining_args >= 2 && arg.equals("-e")) {
                mPressToContinue = true;
            } else if (remaining_args >= 2 && arg.equals("-v")) {
                Utils.setUpLogger(LOGGER, true);
            } else if (remaining_args >= 2 && arg.equals("-d")) {
                mPressToContinue = false;
                mReportCollectionDelayMillis = Integer.parseInt(args[++first_arg]);
            } else if (arg.equals("-h") || arg.equals("--help")) {
                printUsageMessage();
                return false;
            } else {
                break; // Found the atom list
            }
        }

        if (mProtoIncludes.size() == 0) {
            mProtoIncludes.add(HW_ATOMS_PROTO_FILEPATH);
        }

        for (; first_arg < args.length; ++first_arg) {
            String atom = args[first_arg];
            try {
                configuration.addAtom(Integer.valueOf(atom), mProtoIncludes);
            } catch (NumberFormatException e) {
                LOGGER.severe("Bad atom id provided: " + atom);
            }
        }

        mDeviceSerial = Utils.chooseDevice(mDeviceSerial, connectedDevices, defaultDevice, LOGGER);
        if (mDeviceSerial == null) {
            return false;
        }

        return configuration.hasPulledAtoms() || configuration.hasPushedAtoms();
    }

    private ConfigMetricsReportList testDriveAndGetReports(
            StatsdConfig config, boolean hasPulledAtoms, boolean hasPushedAtoms) {
        if (config == null) {
            LOGGER.severe("Failed to create valid config.");
            return null;
        }

        String remoteConfigPath = null;
        try {
            remoteConfigPath = pushConfig(config, mDeviceSerial);
            LOGGER.info("Pushed the following config to statsd on device '" + mDeviceSerial + "':");
            LOGGER.info(config.toString());
            if (hasPushedAtoms) {
                LOGGER.info("Now please play with the device to trigger the event.");
            }
            if (!hasPulledAtoms) {
                if (mPressToContinue) {
                    LOGGER.info("Press Enter after you finish playing with the device...");
                    Scanner scanner = new Scanner(System.in);
                    scanner.nextLine();
                } else {
                    LOGGER.info(
                            String.format(
                                    "All events should be dumped after %d ms ...",
                                    mReportCollectionDelayMillis));
                    Thread.sleep(mReportCollectionDelayMillis);
                }
            } else {
                LOGGER.info("All events should be dumped after 1.5 minutes ...");
                Thread.sleep(15_000);
                Utils.logAppBreadcrumb(0, 0, LOGGER, mDeviceSerial);
                Thread.sleep(75_000);
            }
            return Utils.getReportList(CONFIG_ID, true, false, LOGGER, mDeviceSerial);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to test drive: " + e.getMessage(), e);
        } finally {
            removeConfig(mDeviceSerial);
            if (remoteConfigPath != null) {
                try {
                    Utils.runCommand(
                            null, LOGGER, "adb", "-s", mDeviceSerial, "shell", "rm",
                            remoteConfigPath);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING,
                            "Unable to remove remote config file: " + remoteConfigPath, e);
                }
            }
        }
        return null;
    }

    static class Configuration {
        boolean mOnePushedAtomEvent = false;
        @VisibleForTesting
        Set<Integer> mPushedAtoms = new TreeSet<>();
        @VisibleForTesting
        Set<Integer> mPulledAtoms = new TreeSet<>();
        @VisibleForTesting
        ArrayList<String> mAdditionalAllowedPackages = new ArrayList<>();
        private final Set<Long> mTrackedMetrics = new HashSet<>();
        private final String mAndroidBuildTop = System.getenv("ANDROID_BUILD_TOP");

        private Descriptors.Descriptor externalDescriptor = null;

        private void dumpMetrics(ConfigMetricsReportList reportList, Dumper dumper) {
            // We may get multiple reports. Take the last one.
            ConfigMetricsReport report = reportList.getReports(reportList.getReportsCount() - 1);
            for (StatsLogReport statsLog : report.getMetricsList()) {
                if (isTrackedMetric(statsLog.getMetricId())) {
                    dumper.dump(statsLog, externalDescriptor);
                }
            }
        }

        boolean isTrackedMetric(long metricId) {
            return mTrackedMetrics.contains(metricId);
        }

        static boolean isPulledAtom(int atomId) {
            return atomId >= PULL_ATOM_START && atomId <= MAX_PLATFORM_ATOM_TAG
                    || atomId >= VENDOR_PULLED_ATOM_START_TAG;
        }

        void addAtom(Integer atom, List<String> protoIncludes) {
            if (Atom.getDescriptor().findFieldByNumber(atom) == null &&
                    Atom.getDescriptor().isExtensionNumber(atom) == false) {
                // try to look in alternative locations
                if (protoIncludes != null) {
                    boolean isAtomDefined = false;
                    for (int i = 0; i < protoIncludes.size(); i++) {
                        isAtomDefined = isAtomDefinedInFile(protoIncludes.get(i), atom);
                        if (isAtomDefined) {
                            break;
                        }
                    }
                    if (!isAtomDefined) {
                        LOGGER.severe("No such atom found: " + atom);
                        return;
                    }
                }
            }
            if (isPulledAtom(atom)) {
                mPulledAtoms.add(atom);
            } else {
                mPushedAtoms.add(atom);
            }
        }

        private String compileProtoFileIntoDescriptorSet(String protoFileName) {
            final String protoCompilerBinary = "aprotoc";
            final String descSetFlag = "--descriptor_set_out";
            final String includeImportsFlag = "--include_imports";
            final String includeSourceInfoFlag = "--include_source_info";
            final String dsFileName = generateDescriptorSetFileName(protoFileName);

            if (dsFileName == null) return null;

            LOGGER.log(Level.FINE, "Target DescriptorSet File " + dsFileName);

            try {
                List<String> cmdArgs = new ArrayList<>();
                cmdArgs.add(protoCompilerBinary);
                cmdArgs.add(descSetFlag);
                cmdArgs.add(dsFileName);
                cmdArgs.add(includeImportsFlag);
                cmdArgs.add(includeSourceInfoFlag);

                // populate the proto_path argument
                if (mAndroidBuildTop != null) {
                    cmdArgs.add("-I");
                    cmdArgs.add(mAndroidBuildTop);

                    Path protoBufSrcPath = Paths.get(mAndroidBuildTop, "external/protobuf/src");
                    cmdArgs.add("-I");
                    cmdArgs.add(protoBufSrcPath.toString());
                }

                Path protoPath = Paths.get(protoFileName);
                while (protoPath.getParent() != null) {
                    LOGGER.log(Level.FINE, "Including " + protoPath.getParent().toString());
                    cmdArgs.add("-I");
                    cmdArgs.add(protoPath.getParent().toString());
                    protoPath = protoPath.getParent();
                }
                cmdArgs.add(protoFileName);

                String[] commands = new String[cmdArgs.size()];
                commands = cmdArgs.toArray(commands);
                Utils.runCommand(null, LOGGER, commands);
                return dsFileName;
            } catch (InterruptedException | IOException e) {
                LOGGER.severe("Error while performing proto compilation: " + e.getMessage());
            }
            return null;
        }

        private String validateIncludeProtoPath(String protoFileName) {
            try {
                File protoFile = new File(protoFileName);
                if (!protoFile.exists()) {
                    protoFileName = Paths.get(mAndroidBuildTop).resolve(
                            protoFileName).toRealPath().toString();
                }

                // file will be generated in the current work dir
                return Paths.get(protoFileName).toRealPath().toString();
            } catch (IOException e) {
                LOGGER.log(Level.INFO, "Could not find file " + protoFileName);
            }
            return null;
        }

        private String generateDescriptorSetFileName(String protoFileName) {
            try {
                // file will be generated in the current work dir
                final Path protoPath = Paths.get(protoFileName).toRealPath();
                LOGGER.log(Level.FINE, "Absolute proto file " + protoPath.toString());
                Path dsPath = Paths.get(System.getProperty("user.dir"));
                dsPath = dsPath.resolve(protoPath.getFileName().toString() + ".ds.tmp");
                return dsPath.toString();
            } catch (IOException e) {
                LOGGER.severe("Could not find file " + protoFileName);
            }
            return null;
        }

        private boolean isAtomDefinedInFile(String fileName, Integer atom) {
            final String fullProtoFilePath = validateIncludeProtoPath(fileName);
            if (fullProtoFilePath == null) return false;

            final String dsFileName = compileProtoFileIntoDescriptorSet(fullProtoFilePath);
            if (dsFileName == null) return false;

            try (InputStream input = new FileInputStream(dsFileName)) {
                DescriptorProtos.FileDescriptorSet fileDescriptorSet =
                        DescriptorProtos.FileDescriptorSet.parseFrom(input);
                Descriptors.FileDescriptor fieldOptionsDesc =
                        DescriptorProtos.FieldOptions.getDescriptor().getFile();

                LOGGER.fine("Files count is " + fileDescriptorSet.getFileCount());

                // preparing dependencies list
                List<Descriptors.FileDescriptor> dependencies =
                        new ArrayList<Descriptors.FileDescriptor>();
                for (int fileIndex = 0; fileIndex < fileDescriptorSet.getFileCount(); fileIndex++) {
                    LOGGER.fine("Processing file " + fileIndex);
                    try {
                        Descriptors.FileDescriptor dep = Descriptors.FileDescriptor.buildFrom(
                                fileDescriptorSet.getFile(fileIndex),
                                new Descriptors.FileDescriptor[0]);
                        dependencies.add(dep);
                    } catch (Descriptors.DescriptorValidationException e) {
                        LOGGER.fine("Unable to parse atoms proto file: " + fileName + ". Error: "
                                + e.getDescription());
                    }
                }

                Descriptors.FileDescriptor[] fileDescriptorDeps =
                        new Descriptors.FileDescriptor[dependencies.size()];
                fileDescriptorDeps = dependencies.toArray(fileDescriptorDeps);

                // looking for a file with an Atom definition
                for (int fileIndex = 0; fileIndex < fileDescriptorSet.getFileCount(); fileIndex++) {
                    LOGGER.fine("Processing file " + fileIndex);
                    Descriptors.Descriptor atomMsgDesc = null;
                    try {
                        atomMsgDesc = Descriptors.FileDescriptor.buildFrom(
                                        fileDescriptorSet.getFile(fileIndex), fileDescriptorDeps,
                                        true)
                                .findMessageTypeByName("Atom");
                    } catch (Descriptors.DescriptorValidationException e) {
                        LOGGER.severe("Unable to parse atoms proto file: " + fileName + ". Error: "
                                + e.getDescription());
                    }

                    if (atomMsgDesc != null) {
                        LOGGER.fine("Atom message is located");
                    }

                    if (atomMsgDesc != null && atomMsgDesc.findFieldByNumber(atom) != null) {
                        externalDescriptor = atomMsgDesc;
                        return true;
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Unable to parse atoms proto file: " + fileName, e);
            } finally {
                File dsFile = new File(dsFileName);
                dsFile.delete();
            }
            return false;
        }

        private boolean hasPulledAtoms() {
            return !mPulledAtoms.isEmpty();
        }

        private boolean hasPushedAtoms() {
            return !mPushedAtoms.isEmpty();
        }

        StatsdConfig createConfig() {
            long metricId = METRIC_ID_BASE;
            long atomMatcherId = ATOM_MATCHER_ID_BASE;

            StatsdConfig.Builder builder = baseBuilder();

            if (hasPulledAtoms()) {
                builder.addAtomMatcher(
                        createAtomMatcher(
                                Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER,
                                APP_BREADCRUMB_MATCHER_ID));
            }

            for (int atomId : mPulledAtoms) {
                builder.addAtomMatcher(createAtomMatcher(atomId, atomMatcherId));
                GaugeMetric.Builder gaugeMetricBuilder = GaugeMetric.newBuilder();
                gaugeMetricBuilder
                        .setId(metricId)
                        .setWhat(atomMatcherId)
                        .setTriggerEvent(APP_BREADCRUMB_MATCHER_ID)
                        .setGaugeFieldsFilter(FieldFilter.newBuilder().setIncludeAll(true).build())
                        .setBucket(TimeUnit.ONE_MINUTE)
                        .setSamplingType(GaugeMetric.SamplingType.FIRST_N_SAMPLES)
                        .setMaxNumGaugeAtomsPerBucket(100);
                builder.addGaugeMetric(gaugeMetricBuilder.build());
                atomMatcherId++;
                mTrackedMetrics.add(metricId++);
            }

            // A simple atom matcher for each pushed atom.
            List<AtomMatcher> simpleAtomMatchers = new ArrayList<>();
            for (int atomId : mPushedAtoms) {
                final AtomMatcher atomMatcher = createAtomMatcher(atomId, atomMatcherId++);
                simpleAtomMatchers.add(atomMatcher);
                builder.addAtomMatcher(atomMatcher);
            }

            if (mOnePushedAtomEvent) {
                // Create a union event metric, using a matcher that matches all pushed atoms.
                AtomMatcher unionAtomMatcher = createUnionMatcher(simpleAtomMatchers,
                        atomMatcherId);
                builder.addAtomMatcher(unionAtomMatcher);
                EventMetric.Builder eventMetricBuilder = EventMetric.newBuilder();
                eventMetricBuilder.setId(metricId).setWhat(unionAtomMatcher.getId());
                builder.addEventMetric(eventMetricBuilder.build());
                mTrackedMetrics.add(metricId++);
            } else {
                // Create multiple event metrics, one per pushed atom.
                for (AtomMatcher atomMatcher : simpleAtomMatchers) {
                    EventMetric.Builder eventMetricBuilder = EventMetric.newBuilder();
                    eventMetricBuilder.setId(metricId).setWhat(atomMatcher.getId());
                    builder.addEventMetric(eventMetricBuilder.build());
                    mTrackedMetrics.add(metricId++);
                }
            }

            return builder.build();
        }

        private static AtomMatcher createAtomMatcher(int atomId, long matcherId) {
            AtomMatcher.Builder atomMatcherBuilder = AtomMatcher.newBuilder();
            atomMatcherBuilder
                    .setId(matcherId)
                    .setSimpleAtomMatcher(SimpleAtomMatcher.newBuilder().setAtomId(atomId));
            return atomMatcherBuilder.build();
        }

        private AtomMatcher createUnionMatcher(
                List<AtomMatcher> simpleAtomMatchers, long atomMatcherId) {
            AtomMatcher.Combination.Builder combinationBuilder =
                    AtomMatcher.Combination.newBuilder();
            combinationBuilder.setOperation(StatsdConfigProto.LogicalOperation.OR);
            for (AtomMatcher matcher : simpleAtomMatchers) {
                combinationBuilder.addMatcher(matcher.getId());
            }
            AtomMatcher.Builder atomMatcherBuilder = AtomMatcher.newBuilder();
            atomMatcherBuilder.setId(atomMatcherId).setCombination(combinationBuilder.build());
            return atomMatcherBuilder.build();
        }

        private StatsdConfig.Builder baseBuilder() {
            ArrayList<String> allowedSources = new ArrayList<>();
            Collections.addAll(allowedSources, ALLOWED_LOG_SOURCES);
            allowedSources.addAll(mAdditionalAllowedPackages);
            return StatsdConfig.newBuilder()
                    .addAllAllowedLogSource(allowedSources)
                    .addAllDefaultPullPackages(Arrays.asList(DEFAULT_PULL_SOURCES))
                    .addPullAtomPackages(
                            PullAtomPackages.newBuilder()
                                    .setAtomId(Atom.MEDIA_DRM_ACTIVITY_INFO_FIELD_NUMBER)
                                    .addPackages("AID_MEDIA"))
                    .addPullAtomPackages(
                            PullAtomPackages.newBuilder()
                                    .setAtomId(Atom.GPU_STATS_GLOBAL_INFO_FIELD_NUMBER)
                                    .addPackages("AID_GPU_SERVICE"))
                    .addPullAtomPackages(
                            PullAtomPackages.newBuilder()
                                    .setAtomId(Atom.GPU_STATS_APP_INFO_FIELD_NUMBER)
                                    .addPackages("AID_GPU_SERVICE"))
                    .addPullAtomPackages(
                            PullAtomPackages.newBuilder()
                                    .setAtomId(Atom.TRAIN_INFO_FIELD_NUMBER)
                                    .addPackages("AID_STATSD"))
                    .addPullAtomPackages(
                            PullAtomPackages.newBuilder()
                                    .setAtomId(
                                            Atom.GENERAL_EXTERNAL_STORAGE_ACCESS_STATS_FIELD_NUMBER)
                                    .addPackages("com.google.android.providers.media.module"))
                    .addPullAtomPackages(
                            PullAtomPackages.newBuilder()
                                    .setAtomId(Atom.LAUNCHER_LAYOUT_SNAPSHOT_FIELD_NUMBER)
                                    .addPackages("com.google.android.apps.nexuslauncher"))
                    .addPullAtomPackages(
                            PullAtomPackages.newBuilder()
                                    .setAtomId(QnsExtensionAtoms
                                            .QNS_RAT_PREFERENCE_MISMATCH_INFO_FIELD_NUMBER)
                                    .addPackages("com.android.telephony.qns"))
                    .addPullAtomPackages(
                            PullAtomPackages.newBuilder()
                                    .setAtomId(QnsExtensionAtoms
                                            .QNS_HANDOVER_TIME_MILLIS_FIELD_NUMBER)
                                    .addPackages("com.android.telephony.qns"))
                    .addPullAtomPackages(
                            PullAtomPackages.newBuilder()
                                    .setAtomId(QnsExtensionAtoms
                                            .QNS_HANDOVER_PINGPONG_FIELD_NUMBER)
                                    .addPackages("com.android.telephony.qns"))
                    .setHashStringsInMetricReport(false);
        }
    }

    interface Dumper {
        void dump(StatsLogReport report, Descriptors.Descriptor externalDescriptor);
    }

    static class BasicDumper implements Dumper {
        @Override
        public void dump(StatsLogReport report, Descriptors.Descriptor externalDescriptor) {
            System.out.println(report.toString());
        }
    }

    static class TerseDumper extends BasicDumper {
        @Override
        public void dump(StatsLogReport report, Descriptors.Descriptor externalDescriptor) {
            if (report.hasGaugeMetrics()) {
                dumpGaugeMetrics(report);
            }
            if (report.hasEventMetrics()) {
                dumpEventMetrics(report, externalDescriptor);
            }
        }

        void dumpEventMetrics(StatsLogReport report,
                Descriptors.Descriptor externalDescriptor) {
            final List<StatsLog.EventMetricData> data = Utils.getEventMetricData(report);
            if (data.isEmpty()) {
                return;
            }
            long firstTimestampNanos = data.get(0).getElapsedTimestampNanos();
            for (StatsLog.EventMetricData event : data) {
                final double deltaSec =
                        (event.getElapsedTimestampNanos() - firstTimestampNanos) / 1e9;
                System.out.println(String.format("+%.3fs: %s", deltaSec,
                        dumpAtom(event.getAtom(), externalDescriptor)));
            }
        }

        void dumpGaugeMetrics(StatsLogReport report) {
            final List<StatsLog.GaugeMetricData> data = report.getGaugeMetrics().getDataList();
            if (data.isEmpty()) {
                return;
            }
            for (StatsLog.GaugeMetricData gauge : data) {
                System.out.println(gauge.toString());
            }
        }
    }

    private static String dumpAtom(AtomsProto.Atom atom,
            Descriptors.Descriptor externalDescriptor) {
        if (atom.getPushedCase().getNumber() != 0 || atom.getPulledCase().getNumber() != 0 ||
                externalDescriptor == null) {
            return atom.toString();
        } else {
            try {
                return convertToExternalAtom(atom, externalDescriptor).toString();
            } catch (Exception e) {
                LOGGER.severe("Failed to parse an atom: " + e.getMessage());
                return "";
            }
        }
    }

    private static DynamicMessage convertToExternalAtom(AtomsProto.Atom atom,
            Descriptors.Descriptor externalDescriptor) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CodedOutputStream cos = CodedOutputStream.newInstance(outputStream);
        atom.writeTo(cos);
        cos.flush();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                outputStream.toByteArray());
        CodedInputStream cis = CodedInputStream.newInstance(inputStream);
        return DynamicMessage.parseFrom(externalDescriptor, cis);
    }


    private static String pushConfig(StatsdConfig config, String deviceSerial)
            throws IOException, InterruptedException {
        File configFile = File.createTempFile("statsdconfig", ".config");
        configFile.deleteOnExit();
        Files.write(config.toByteArray(), configFile);
        String remotePath = "/data/local/tmp/" + configFile.getName();
        Utils.runCommand(
                null, LOGGER, "adb", "-s", deviceSerial, "push", configFile.getAbsolutePath(),
                remotePath);
        Utils.runCommand(
                null,
                LOGGER,
                "adb",
                "-s",
                deviceSerial,
                "shell",
                "cat",
                remotePath,
                "|",
                Utils.CMD_UPDATE_CONFIG,
                String.valueOf(CONFIG_ID));
        return remotePath;
    }

    private static void removeConfig(String deviceSerial) {
        try {
            Utils.runCommand(
                    null,
                    LOGGER,
                    "adb",
                    "-s",
                    deviceSerial,
                    "shell",
                    Utils.CMD_REMOVE_CONFIG,
                    String.valueOf(CONFIG_ID));
        } catch (Exception e) {
            LOGGER.severe("Failed to remove config: " + e.getMessage());
        }
    }
}
