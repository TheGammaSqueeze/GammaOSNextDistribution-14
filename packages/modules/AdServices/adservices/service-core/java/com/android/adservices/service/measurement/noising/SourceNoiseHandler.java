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

package com.android.adservices.service.measurement.noising;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.measurement.util.UnsignedLong;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Generates noised reports for the provided source. */
public class SourceNoiseHandler {
    private static final int PROBABILITY_DECIMAL_POINTS_LIMIT = 7;

    private final Flags mFlags;
    private final EventReportWindowCalcDelegate mEventReportWindowCalcDelegate;

    public SourceNoiseHandler(@NonNull Flags flags) {
        mFlags = flags;
        mEventReportWindowCalcDelegate = new EventReportWindowCalcDelegate(flags);
    }

    @VisibleForTesting
    SourceNoiseHandler(
            @NonNull Flags flags,
            @NonNull EventReportWindowCalcDelegate eventReportWindowCalcDelegate) {
        mFlags = flags;
        mEventReportWindowCalcDelegate = eventReportWindowCalcDelegate;
    }

    /** Multiplier is 1, when only one destination needs to be considered. */
    public static final int SINGLE_DESTINATION_IMPRESSION_NOISE_MULTIPLIER = 1;

    /**
     * Double-folds the number of states in order to allocate half to app destination and half to
     * web destination for fake reports generation.
     */
    public static final int DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER = 2;

    /**
     * Assign attribution mode based on random rate and generate fake reports if needed. Should only
     * be called for a new Source.
     *
     * @return fake reports to be stored in the datastore.
     */
    public List<Source.FakeReport> assignAttributionModeAndGenerateFakeReports(
            @NonNull Source source) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double value = rand.nextDouble();
        if (value >= getRandomizedSourceResponsePickRate(source)) {
            source.setAttributionMode(Source.AttributionMode.TRUTHFULLY);
            return null;
        }

        List<Source.FakeReport> fakeReports;
        TriggerSpecs triggerSpecs = source.getTriggerSpecs();
        if (triggerSpecs == null) {
            // There will at least be one (app or web) destination available
            ImpressionNoiseParams noiseParams = getImpressionNoiseParams(source);
            fakeReports =
                    ImpressionNoiseUtil.selectRandomStateAndGenerateReportConfigs(
                                    noiseParams, rand)
                            .stream()
                            .map(
                                    reportConfig ->
                                            new Source.FakeReport(
                                                    new UnsignedLong(
                                                            Long.valueOf(reportConfig[0])),
                                                    mEventReportWindowCalcDelegate
                                                            .getReportingTimeForNoising(
                                                                    source,
                                                                    reportConfig[1]),
                                                    resolveFakeReportDestinations(
                                                            source, reportConfig[2])))
                            .collect(Collectors.toList());
        } else {
            int destinationTypeMultiplier = source.getDestinationTypeMultiplier(mFlags);
            List<int[]> fakeReportConfigs =
                    ImpressionNoiseUtil.selectFlexEventReportRandomStateAndGenerateReportConfigs(
                            triggerSpecs, destinationTypeMultiplier, rand);
            fakeReports =
                    fakeReportConfigs.stream()
                            .map(
                                    reportConfig ->
                                            new Source.FakeReport(
                                                    triggerSpecs.getTriggerDataFromIndex(
                                                            reportConfig[0]),
                                                    mEventReportWindowCalcDelegate
                                                            .getReportingTimeForNoisingFlexEventApi(
                                                                    reportConfig[1],
                                                                    reportConfig[0],
                                                                    triggerSpecs),
                                                    resolveFakeReportDestinations(
                                                            source, reportConfig[2])))
                            .collect(Collectors.toList());
        }
        @Source.AttributionMode
        int attributionMode =
                fakeReports.isEmpty()
                        ? Source.AttributionMode.NEVER
                        : Source.AttributionMode.FALSELY;
        source.setAttributionMode(attributionMode);
        return fakeReports;
    }

    @VisibleForTesting
    double getRandomizedSourceResponsePickRate(Source source) {
        // Methods on Source and EventReportWindowCalcDelegate that calculate flip probability for
        // the source rely on reporting windows and max reports that are obtained with consideration
        // to install-state and its interaction with configurable report windows and configurable
        // max reports.
        return source.getFlipProbability(mFlags);
    }

    /** @return Probability of selecting random state for attribution */
    public double getRandomizedTriggerRate(@NonNull Source source) {
        return convertToDoubleAndLimitDecimal(getRandomizedSourceResponsePickRate(source));
    }

    private double convertToDoubleAndLimitDecimal(double probability) {
        return BigDecimal.valueOf(probability)
                .setScale(PROBABILITY_DECIMAL_POINTS_LIMIT, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Either both app and web destinations can be available or one of them will be available. When
     * both destinations are available, we double the number of states at noise generation to be
     * able to randomly choose one of them for fake report creation. We don't add the multiplier
     * when only one of them is available. In that case, choose the one that's non-null.
     *
     * @param destinationIdentifier destination identifier, can be 0 (app) or 1 (web)
     * @return app or web destination {@link Uri}
     */
    private List<Uri> resolveFakeReportDestinations(Source source, int destinationIdentifier) {
        if (source.shouldReportCoarseDestinations(mFlags)) {
            ImmutableList.Builder<Uri> destinations = new ImmutableList.Builder<>();
            Optional.ofNullable(source.getAppDestinations()).ifPresent(destinations::addAll);
            Optional.ofNullable(source.getWebDestinations()).ifPresent(destinations::addAll);
            return destinations.build();
        }

        if (source.hasAppDestinations() && source.hasWebDestinations()) {
            return destinationIdentifier % DUAL_DESTINATION_IMPRESSION_NOISE_MULTIPLIER == 0
                    ? source.getAppDestinations()
                    : source.getWebDestinations();
        }

        return source.hasAppDestinations()
                ? source.getAppDestinations()
                : source.getWebDestinations();
    }

    @VisibleForTesting
    ImpressionNoiseParams getImpressionNoiseParams(Source source) {
        int destinationTypeMultiplier = source.getDestinationTypeMultiplier(mFlags);
        return new ImpressionNoiseParams(
                mEventReportWindowCalcDelegate.getMaxReportCount(source),
                source.getTriggerDataCardinality(),
                mEventReportWindowCalcDelegate.getReportingWindowCountForNoising(source),
                destinationTypeMultiplier);
    }
}
