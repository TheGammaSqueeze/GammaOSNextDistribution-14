/*
 * Copyright (C) 2022 Android Open Source Project
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

package com.android.helpers;

import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.os.nano.AtomsProto;
import com.android.os.nano.AtomsProto.BatteryUsageStatsAtomsProto;
import com.android.os.nano.AtomsProto.BatteryUsageStatsAtomsProto.BatteryConsumerData;
import com.android.os.nano.AtomsProto.BatteryUsageStatsAtomsProto.BatteryConsumerData.PowerComponentUsage;
import com.android.os.nano.AtomsProto.BatteryUsageStatsAtomsProto.PowerComponentModel;
import com.android.os.nano.AtomsProto.BatteryUsageStatsAtomsProto.UidBatteryConsumer;
import com.android.os.nano.StatsLog;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consists of helper methods for collecting the BatteryUsageStatsSinceReset atom from stats and
 * processing that data into metrics related to power consumption attributed to specific packages
 * and components of the phone.
 */
public class BatteryUsageStatsHelper implements ICollectorHelper<Long> {

    private static final String LOG_TAG = BatteryUsageStatsHelper.class.getSimpleName();

    private StatsdHelper mStatsdHelper = new StatsdHelper();

    @Override
    public boolean startCollecting() {
        Log.i(LOG_TAG, "Adding BatteryUsageStats config to statsd.");
        List<Integer> atomIdList = new ArrayList<>();
        atomIdList.add(AtomsProto.Atom.BATTERY_USAGE_STATS_SINCE_RESET_FIELD_NUMBER);
        atomIdList.add(
                AtomsProto.Atom
                        .BATTERY_USAGE_STATS_SINCE_RESET_USING_POWER_PROFILE_MODEL_FIELD_NUMBER);
        return mStatsdHelper.addGaugeConfig(atomIdList);
    }

    private Map<String, Long> batteryUsageStatsFromBucket(List<StatsLog.GaugeBucketInfo> buckets) {
        PackageManager packageManager =
                InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();

        // Get the atom with the best available data to BatteryStats.
        List<BatteryUsageStatsAtomsProto> bestModeledAtom =
                buckets.stream()
                        .flatMap(b -> Arrays.stream(b.atom))
                        .filter(a -> a.hasBatteryUsageStatsSinceReset())
                        .map(a -> a.getBatteryUsageStatsSinceReset().batteryUsageStats)
                        .collect(Collectors.toList());
        if (bestModeledAtom.size() != 1) {
            throw new IllegalStateException(
                    String.format(
                            "Expected exactly 1 BatteryUsageStats atom, but has %d.",
                            bestModeledAtom.size()));
        }
        BatteryUsageStatsAtomsProto atom = bestModeledAtom.get(0);

        // Get the atom with the power profile based data..
        List<BatteryUsageStatsAtomsProto> powerProfileOnlyAtoms =
                buckets.stream()
                        .flatMap(b -> Arrays.stream(b.atom))
                        .filter(a -> a.hasBatteryUsageStatsSinceResetUsingPowerProfileModel())
                        .map(
                                a ->
                                        a.getBatteryUsageStatsSinceResetUsingPowerProfileModel()
                                                .batteryUsageStats)
                        .collect(Collectors.toList());
        if (powerProfileOnlyAtoms.size() != 1) {
            throw new IllegalStateException(
                    String.format(
                            "Expected exactly 1 BatteryUsageStats using Power Profile atom, but"
                                    + " has %d.",
                            powerProfileOnlyAtoms.size()));
        }
        BatteryUsageStatsAtomsProto powerProfileOnlyAtom = powerProfileOnlyAtoms.get(0);

        // Find any component that has both Measured Energy and Power Profile data.
        Set<Integer> comparableModelComponents = new HashSet<>();
        if (atom.componentModels != null && powerProfileOnlyAtom.componentModels != null) {
            for (PowerComponentModel componentModel : atom.componentModels) {
                if (componentModel.powerModel != PowerComponentModel.MEASURED_ENERGY) {
                    // Measured Energy unavailable for component
                    continue;
                }

                if (Arrays.stream(powerProfileOnlyAtom.componentModels)
                        .filter(cm -> cm.component == componentModel.component)
                        .filter(cm -> cm.powerModel == PowerComponentModel.POWER_PROFILE)
                        .findAny()
                        .isPresent()) {
                    comparableModelComponents.add(componentModel.component);
                } else {
                    Log.w(
                            LOG_TAG,
                            "Component "
                                    + componentName(componentModel.component)
                                    + " has Measured Energy model but no Power Profile model");
                }
            }
        }

        Map<String, Long> results = new HashMap<>();

        // Collect the top-level consumer data.
        BatteryConsumerData totalConsumerData = atom.deviceBatteryConsumer;
        if (totalConsumerData != null) {
            results.put(
                    totalConsumptionMetricKey(), totalConsumerData.totalConsumedPowerDeciCoulombs);
            if (totalConsumerData.powerComponents != null) {
                for (PowerComponentUsage usage : totalConsumerData.powerComponents) {
                    results.put(
                            totalConsumptionByComponentMetricKey(usage.component),
                            usage.powerDeciCoulombs);
                    results.put(
                            totalDurationByComponentMetricKey(usage.component),
                            usage.durationMillis);
                    if (comparableModelComponents.contains(usage.component)) {
                        results.put(
                                powerModeledTotalConsumptionByComponentMetricKey(
                                        PowerComponentModel.MEASURED_ENERGY, usage.component),
                                usage.powerDeciCoulombs);
                    }
                }
            } else {
                Log.w(LOG_TAG, "Device consumer data doesn't have specific component data.");
            }
        } else {
            Log.w(LOG_TAG, "Atom doesn't have the expected device consumer data.");
        }

        // Collect Power Profile based data for components with model types.
        BatteryConsumerData powerProfileData = powerProfileOnlyAtom.deviceBatteryConsumer;
        if (powerProfileData != null && powerProfileData.powerComponents != null) {
            for (PowerComponentUsage usage : powerProfileData.powerComponents) {
                if (comparableModelComponents.contains(usage.component)) {
                    results.put(
                            powerModeledTotalConsumptionByComponentMetricKey(
                                    PowerComponentModel.POWER_PROFILE, usage.component),
                            usage.powerDeciCoulombs);
                }
            }
        }

        // Collect the per-UID consumer data.
        if (atom.uidBatteryConsumers != null) {
            for (UidBatteryConsumer perUidConsumer : atom.uidBatteryConsumers) {
                String[] packagesForUid = packageManager.getPackagesForUid(perUidConsumer.uid);
                String packageNamesForMetrics =
                        (packagesForUid == null || packagesForUid.length == 0)
                                ? "unknown"
                                : String.join("_", packagesForUid);
                long timeInForeground = perUidConsumer.timeInForegroundMillis;
                long timeInBackground = perUidConsumer.timeInBackgroundMillis;
                results.put("time-in-fg-by-" + packageNamesForMetrics + "-ms", timeInForeground);
                results.put("time-in-bg-by-" + packageNamesForMetrics + "-ms", timeInBackground);

                BatteryConsumerData perUidData = perUidConsumer.batteryConsumerData;
                if (perUidData != null && perUidData.powerComponents != null) {
                    for (PowerComponentUsage componentPerUid : perUidData.powerComponents) {
                        results.put(
                                attributedConsumptionMetricKey(
                                        packageNamesForMetrics, componentPerUid.component),
                                componentPerUid.powerDeciCoulombs);
                        results.put(
                                attributedDurationMetricKey(
                                        packageNamesForMetrics, componentPerUid.component),
                                componentPerUid.durationMillis);
                    }
                } else {
                    Log.w(
                            LOG_TAG,
                            String.format(
                                    "Per-UID consumer data was missing for: %s",
                                    packageNamesForMetrics));
                }
            }
        } else {
            Log.w(LOG_TAG, "Atom doesn't have the expected per-UID consumer data.");
        }

        results.put("session-start-ms", atom.sessionStartMillis);
        results.put("session-end-ms", atom.sessionEndMillis);
        results.put("session-duration-ms", atom.sessionDurationMillis);
        results.put("session-discharge-pct", (long) atom.sessionDischargePercentage);

        return results;
    }

    private String totalConsumptionMetricKey() {
        return "power-consumed-total-dC";
    }

    private String attributedConsumptionMetricKey(String packages, int component) {
        return String.format("power-consumed-by-%s-on-%s-dC", packages, componentName(component));
    }

    private String totalConsumptionByComponentMetricKey(int component) {
        return String.format("power-consumed-total-on-%s-dC", componentName(component));
    }

    private String powerModeledTotalConsumptionByComponentMetricKey(int model, int component) {
        return String.format(
                "modeled-%s-total-on-%s-dC", modelName(model), componentName(component));
    }

    private String attributedDurationMetricKey(String packages, int component) {
        return String.format("duration-by-%s-on-%s-ms", packages, componentName(component));
    }

    private String totalDurationByComponentMetricKey(int component) {
        return String.format("duration-total-on-%s-ms", componentName(component));
    }

    private String componentName(int component) {
        switch (component) {
            case 0:
                return "screen";
            case 1:
                return "cpu";
            case 2:
                return "bluetooth";
            case 3:
                return "camera";
            case 4:
                return "audio";
            case 5:
                return "video";
            case 6:
                return "flashlight";
            case 7:
                return "system_services";
            case 8:
                return "mobile_radio";
            case 9:
                return "sensors";
            case 10:
                return "gnss";
            case 11:
                return "wifi";
            case 12:
                return "wakelock";
            case 13:
                return "memory";
            case 14:
                return "phone";
            case 15:
                return "ambient_display";
            case 16:
                return "idle";
            case 17:
                return "reattributed_to_other_consumers";
            default:
                return "unknown_component_" + component;
        }
    }

    private String modelName(int model) {
        switch (model) {
            case BatteryUsageStatsAtomsProto.PowerComponentModel.UNDEFINED:
                return "undefined";
            case BatteryUsageStatsAtomsProto.PowerComponentModel.POWER_PROFILE:
                return "power_profile";
            case BatteryUsageStatsAtomsProto.PowerComponentModel.MEASURED_ENERGY:
                return "measured_energy";
            default:
                return "unknown_model_" + model;
        }
    }

    @Override
    public Map<String, Long> getMetrics() {
        List<StatsLog.GaugeMetricData> gaugeMetricList = mStatsdHelper.getGaugeMetrics();
        if (gaugeMetricList.size() != 2) {
            throw new IllegalStateException(
                    String.format(
                            "Expected exactly 2 gauge metric data, but has %d.",
                            gaugeMetricList.size()));
        }

        List<StatsLog.GaugeBucketInfo> beforeBuckets = new ArrayList<>();
        List<StatsLog.GaugeBucketInfo> afterBuckets = new ArrayList<>();
        for (StatsLog.GaugeMetricData gaugeMetricData : gaugeMetricList) {
            // It's possible for multiple statsd-based listeners to run at the same time, which
            // causes there to be more than 2 buckets. To most safely collect the beginning and
            // end of collection, we take the first and last buckets.
            if (gaugeMetricData.bucketInfo.length < 2) {
                throw new IllegalStateException(
                        String.format(
                                "Expected at least 2 buckets in data, but has %d.",
                                gaugeMetricData.bucketInfo.length));
            }
            beforeBuckets.add(gaugeMetricData.bucketInfo[0]);
            afterBuckets.add(gaugeMetricData.bucketInfo[gaugeMetricData.bucketInfo.length - 1]);
        }

        Map<String, Long> beforeData = batteryUsageStatsFromBucket(beforeBuckets);
        Map<String, Long> afterData = batteryUsageStatsFromBucket(afterBuckets);

        printEntries("First bucket", beforeData);
        printEntries("Last bucket", afterData);

        Map<String, Long> results = new HashMap<>();
        for (String sharedKey : beforeData.keySet()) {
            if (!afterData.containsKey(sharedKey)) {
                continue;
            }

            results.put(sharedKey, afterData.get(sharedKey) - beforeData.get(sharedKey));
        }
        return results;
    }

    @Override
    public boolean stopCollecting() {
        return mStatsdHelper.removeStatsConfig();
    }

    private void printEntries(String prefix, Map<String, Long> data) {
        for (Map.Entry<String, Long> datum : data.entrySet()) {
            Log.e(
                    LOG_TAG,
                    String.format("%s\t|\t%s = %s", prefix, datum.getKey(), datum.getValue()));
        }
    }
}
