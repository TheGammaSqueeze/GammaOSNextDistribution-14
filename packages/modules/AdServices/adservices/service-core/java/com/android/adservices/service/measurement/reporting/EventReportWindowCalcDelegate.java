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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.measurement.PrivacyParams.EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS;
import static com.android.adservices.service.measurement.PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;

import android.annotation.NonNull;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerSpec;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.util.UnsignedLong;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Does event report window related calculations, e.g. count, reporting time. */
public class EventReportWindowCalcDelegate {
    private static final String EARLY_REPORTING_WINDOWS_CONFIG_DELIMITER = ",";

    private final Flags mFlags;

    public EventReportWindowCalcDelegate(@NonNull Flags flags) {
        mFlags = flags;
    }

    /**
     * Max reports count given the Source object.
     *
     * @param source the Source object
     * @return maximum number of reports allowed
     */
    public int getMaxReportCount(Source source) {
        return getMaxReportCount(source, source.isInstallDetectionEnabled());
    }

    /**
     * Max reports count based on conversion destination type.
     *
     * @param source the Source object
     * @param destinationType destination type
     * @return maximum number of reports allowed
     */
    public int getMaxReportCount(@NonNull Source source, @EventSurfaceType int destinationType) {
        return getMaxReportCount(source, isInstallCase(source, destinationType));
    }

    private int getMaxReportCount(@NonNull Source source, boolean isInstallCase) {
        // TODO(b/290101531): Cleanup flags
        if (mFlags.getMeasurementFlexLiteApiEnabled() && source.getMaxEventLevelReports() != null) {
            return source.getMaxEventLevelReports();
        }

        if (source.getSourceType() == Source.SourceType.EVENT) {
            // Additional report essentially for first open + 1 post install conversion. If there
            // is already more than 1 report allowed, no need to have that additional report.
            if (isInstallCase && !source.hasWebDestinations() && isDefaultConfiguredVtc()) {
                return PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS;
            }
            return mFlags.getMeasurementVtcConfigurableMaxEventReportsCount();
        }

        return PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS;
    }

    /**
     * Calculates the reporting time based on the {@link Trigger} time, {@link Source}'s expiry and
     * trigger destination type.
     *
     * @return the reporting time
     */
    public long getReportingTime(
            @NonNull Source source, long triggerTime, @EventSurfaceType int destinationType) {
        if (triggerTime < source.getEventTime()) {
            return -1;
        }

        // Cases where source could have both web and app destinations, there if the trigger
        // destination is an app, and it was installed, then installState should be considered true.
        List<Pair<Long, Long>> reportingWindows =
                getEffectiveReportingWindows(source, isInstallCase(source, destinationType));
        for (Pair<Long, Long> window : reportingWindows) {
            if (isWithinWindow(triggerTime, window)) {
                return window.second + mFlags.getMeasurementMinEventReportDelayMillis();
            }
        }

        return -1;
    }

    private boolean isWithinWindow(long time, Pair<Long, Long> window) {
        return window.first <= time && time < window.second;
    }

    /**
     * Enum shows trigger time and source window time relationship. It is used to generate different
     * verbose debug reports.
     */
    public enum MomentPlacement {
        BEFORE,
        AFTER,
        WITHIN;
    }

    /**
     * @param source source for which the window is calculated
     * @param trigger trigger
     * @param triggerData trigger data
     * @return how trigger time falls in source windows
     */
    public MomentPlacement fallsWithinWindow(
            @NonNull Source source,
            @NonNull Trigger trigger,
            @NonNull UnsignedLong triggerData) {
        long triggerTime = trigger.getTriggerTime();

        // Non-flex source
        if (source.getTriggerSpecs() == null) {
            List<Pair<Long, Long>> reportingWindows =
                    getEffectiveReportingWindows(
                            source, isInstallCase(source, trigger.getDestinationType()));
            if (triggerTime < reportingWindows.get(0).first) {
                return MomentPlacement.BEFORE;
            }
            if (triggerTime >= reportingWindows.get(reportingWindows.size() - 1).second) {
                return MomentPlacement.AFTER;
            }
            return MomentPlacement.WITHIN;
        }

        // Flex source
        TriggerSpecs triggerSpecs = source.getTriggerSpecs();
        long sourceRegistrationTime = source.getEventTime();

        if (triggerTime
                < triggerSpecs.findReportingStartTimeForTriggerData(triggerData)
                        + sourceRegistrationTime) {
            return MomentPlacement.BEFORE;
        }

        List<Long> reportingWindows = triggerSpecs.findReportingEndTimesForTriggerData(triggerData);
        for (Long window : reportingWindows) {
            if (triggerTime < window + sourceRegistrationTime) {
                return MomentPlacement.WITHIN;
            }
        }
        return MomentPlacement.AFTER;
    }

    /**
     * Return reporting time by index for noising based on the index
     *
     * @param windowIndex index of the reporting window for which
     * @return reporting time in milliseconds
     */
    public long getReportingTimeForNoising(
            @NonNull Source source, int windowIndex) {
        List<Pair<Long, Long>> reportingWindows = getEffectiveReportingWindows(
                source, source.isInstallDetectionEnabled());
        Pair<Long, Long> finalWindow = reportingWindows.get(reportingWindows.size() - 1);
        // TODO: (b/288646239) remove this check, confirming noising indexing accuracy.
        return windowIndex < reportingWindows.size()
                ? reportingWindows.get(windowIndex).second
                        + mFlags.getMeasurementMinEventReportDelayMillis()
                : finalWindow.second + mFlags.getMeasurementMinEventReportDelayMillis();
    }

    /**
     * Returns effective, that is, the ones that occur before {@link
     * Source#getEffectiveEventReportWindow()}, event reporting windows count for noising cases.
     *
     * @param source source for which the count is requested
     */
    public int getReportingWindowCountForNoising(@NonNull Source source) {
        return getEffectiveReportingWindows(source, source.isInstallDetectionEnabled()).size();
    }

    /**
     * Returns reporting time for noising with flex event API.
     *
     * @param windowIndex window index corresponding to which the reporting time should be returned
     * @param triggerDataIndex trigger data state index
     * @param triggerSpecs flex event trigger specs
     */
    public long getReportingTimeForNoisingFlexEventApi(
            int windowIndex, int triggerDataIndex, TriggerSpecs triggerSpecs) {
        for (TriggerSpec triggerSpec : triggerSpecs.getTriggerSpecs()) {
            triggerDataIndex -= triggerSpec.getTriggerData().size();
            if (triggerDataIndex < 0) {
                return triggerSpec.getEventReportWindowsEnd().get(windowIndex)
                        + mFlags.getMeasurementMinEventReportDelayMillis();
            }
        }
        return 0;
    }

    /**
     * Calculates the reporting time based on the {@link Trigger} time for flexible event report API
     *
     * @param triggerSpecs the report specification to be processed
     * @param sourceRegistrationTime source registration time
     * @param triggerTime trigger time
     * @param triggerData the trigger data
     * @return the reporting time
     */
    public long getFlexEventReportingTime(
            TriggerSpecs triggerSpecs,
            long sourceRegistrationTime,
            long triggerTime,
            UnsignedLong triggerData) {
        if (triggerTime < sourceRegistrationTime) {
            return -1L;
        }
        if (triggerTime
                < triggerSpecs.findReportingStartTimeForTriggerData(triggerData)
                        + sourceRegistrationTime) {
            return -1L;
        }

        List<Long> reportingWindows = triggerSpecs.findReportingEndTimesForTriggerData(triggerData);
        for (Long window : reportingWindows) {
            if (triggerTime < window + sourceRegistrationTime) {
                return sourceRegistrationTime + window
                        + mFlags.getMeasurementMinEventReportDelayMillis();
            }
        }
        return -1L;
    }

    private static boolean isInstallCase(Source source, @EventSurfaceType int destinationType) {
        return destinationType == EventSurfaceType.APP && source.isInstallAttributed();
    }

    /**
     * If the flag is enabled and the specified report windows are valid, picks from flag controlled
     * configurable early reporting windows. Otherwise, falls back to the values provided in
     * {@code getDefaultEarlyReportingWindowEnds}, which can have install-related custom behaviour.
     * It curtails the windows that occur after {@link Source#getEffectiveEventReportWindow()}
     * because they would effectively be unusable.
     */
    private List<Pair<Long, Long>> getEffectiveReportingWindows(Source source,
            boolean installState) {
        // TODO(b/290221611) Remove early reporting windows from code, only use them for flags.
        if (mFlags.getMeasurementFlexLiteApiEnabled() && source.hasManualEventReportWindows()) {
            return source.parsedProcessedEventReportWindows();
        }
        List<Long> defaultEarlyWindowEnds =
                getDefaultEarlyReportingWindowEnds(
                        source.getSourceType(),
                        installState && !source.hasWebDestinations());
        List<Long> earlyWindowEnds =
                getConfiguredOrDefaultEarlyReportingWindowEnds(
                        source.getSourceType(), defaultEarlyWindowEnds);
        // Add source event time to windows
        earlyWindowEnds =
                earlyWindowEnds.stream()
                        .map((x) -> source.getEventTime() + x)
                        .collect(Collectors.toList());

        List<Pair<Long, Long>> windowList = new ArrayList<>();
        long windowStart = 0L;
        Pair<Long, Long> finalWindow =
                getFinalReportingWindow(source, earlyWindowEnds);

        for (long windowEnd : earlyWindowEnds) {
            // Start time of `finalWindow` is either 0 or one of `earlyWindowEnds` times; stop
            // iterating if we see it, and add `finalWindow`.
            if (windowStart == finalWindow.first) {
                break;
            }
            windowList.add(Pair.create(windowStart, windowEnd));
            windowStart = windowEnd;
        }

        windowList.add(finalWindow);

        return ImmutableList.copyOf(windowList);
    }

    /**
     * Returns the default early reporting windows
     *
     * @param sourceType Source's Type
     * @param installAttributionEnabled whether windows for install attribution should be provided
     * @return a list of windows
     */
    public static List<Long> getDefaultEarlyReportingWindowEnds(
            Source.SourceType sourceType, boolean installAttributionEnabled) {
        long[] earlyWindows;
        if (installAttributionEnabled) {
            earlyWindows =
                    sourceType == Source.SourceType.EVENT
                            ? INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
                            : INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
        } else {
            earlyWindows =
                    sourceType == Source.SourceType.EVENT
                            ? EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
                            : NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
        }
        return asList(earlyWindows);
    }

    /**
     * Returns default or configured (via flag) early reporting windows for the SourceType
     *
     * @param sourceType Source's Type
     * @param defaultEarlyWindows default value for early windows
     * @return list of windows
     */
    public List<Long> getConfiguredOrDefaultEarlyReportingWindowEnds(
            Source.SourceType sourceType, List<Long> defaultEarlyWindowEnds) {
        // `defaultEarlyWindowEnds` may contain custom install-related logic, which we only apply if
        // the configurable report windows (and max reports) are in their default state. Without
        // this check, we may construct default-value report windows without the custom
        // install-related logic applied.
        if ((sourceType == Source.SourceType.EVENT && isDefaultConfiguredVtc())
                || (sourceType == Source.SourceType.NAVIGATION && isDefaultConfiguredCtc())) {
            return defaultEarlyWindowEnds;
        }

        String earlyReportingWindowsString = pickEarlyReportingWindowsConfig(mFlags, sourceType);

        if (earlyReportingWindowsString.isEmpty()) {
            // No early reporting windows specified. It needs to be handled separately because
            // splitting an empty string results in an array containing a single empty string. We
            // want to handle it as an empty array.
            return Collections.emptyList();
        }

        ImmutableList.Builder<Long> earlyWindowEnds = new ImmutableList.Builder<>();
        String[] split =
                earlyReportingWindowsString.split(EARLY_REPORTING_WINDOWS_CONFIG_DELIMITER);
        if (split.length > MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Invalid configurable early reporting window; more than allowed size: "
                                    + MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS);
            return defaultEarlyWindowEnds;
        }

        for (String windowEnd : split) {
            try {
                earlyWindowEnds.add(TimeUnit.SECONDS.toMillis(Long.parseLong(windowEnd)));
            } catch (NumberFormatException e) {
                LoggerFactory.getMeasurementLogger()
                        .d(e, "Configurable early reporting window parsing failed.");
                return defaultEarlyWindowEnds;
            }
        }
        return earlyWindowEnds.build();
    }

    private Pair<Long, Long> getFinalReportingWindow(
            Source source, List<Long> earlyWindowEnds) {
        // The latest end-time we can associate with a report for this source
        long effectiveExpiry = Math.min(
                source.getEffectiveEventReportWindow(), source.getExpiryTime());
        // Find the latest end-time that can start a window ending at effectiveExpiry
        for (int i = earlyWindowEnds.size() - 1; i >= 0; i--) {
            long windowEnd = earlyWindowEnds.get(i);
            if (windowEnd < effectiveExpiry) {
                return Pair.create(windowEnd, effectiveExpiry);
            }
        }
        return Pair.create(0L, effectiveExpiry);
    }

    /** Indicates whether VTC report windows and max reports are default configured, which can
     * affect custom install-related attribution.
     */
    public boolean isDefaultConfiguredVtc() {
        return mFlags.getMeasurementEventReportsVtcEarlyReportingWindows().isEmpty()
                && mFlags.getMeasurementVtcConfigurableMaxEventReportsCount() == 1;
    }

    /** Indicates whether CTC report windows are default configured, which can affect custom
     * install-related attribution.
     */
    private boolean isDefaultConfiguredCtc() {
        return mFlags.getMeasurementEventReportsCtcEarlyReportingWindows().equals(
                Flags.MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS);
    }

    private static String pickEarlyReportingWindowsConfig(Flags flags,
            Source.SourceType sourceType) {
        return sourceType == Source.SourceType.EVENT
                ? flags.getMeasurementEventReportsVtcEarlyReportingWindows()
                : flags.getMeasurementEventReportsCtcEarlyReportingWindows();
    }

    private static List<Long> asList(long[] values) {
        final List<Long> list = new ArrayList<>();
        for (Long value : values) {
            list.add(value);
        }
        return list;
    }
}
