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

package com.android.adservices.common;

import com.android.adservices.common.AndroidSdk.Level;
import com.android.adservices.common.AndroidSdk.Range;
import com.android.adservices.common.Logger.RealLogger;

import com.google.common.annotations.VisibleForTesting;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule used to skip a test when it's not supported by the device's SDK version.
 *
 * <p>It provides factory methods for the most common "at least" versions (like {@code
 * SdkLevelSupportRule.forAtLeastS()}) and it also supports annotations (like {@code
 * RequiresSdkLevelAtLeastS}) and SDK ranges (like {@code RequiresSdkRange atLeast=RVC,
 * atMost=T_MINUS}).
 *
 * <p>This rule is abstract so subclass can define what a "feature" means. It also doesn't have any
 * dependency on Android code, so it can be used both on device-side and host-side tests.
 *
 * <p><b>NOTE: </b>this class should NOT be used as {@code ClassRule}, as it would result in a "no
 * tests run" scenario if it throws a {@link AssumptionViolatedException}.
 */
abstract class AbstractSdkLevelSupportedRule implements TestRule {

    private static final String TAG = "SdkLevelSupportRule";

    @VisibleForTesting static final String DEFAULT_REASON = "N/A";

    private final Range mDefaultRequiredRange;
    protected final Logger mLog;

    AbstractSdkLevelSupportedRule(RealLogger logger, Range defaultRange) {
        mLog = new Logger(Objects.requireNonNull(logger), TAG);
        mDefaultRequiredRange = Objects.requireNonNull(defaultRange);
        mLog.d("Constructor: logger=%s, defaultRange=%s", logger, defaultRange);
    }

    AbstractSdkLevelSupportedRule(RealLogger logger) {
        this(logger, /* defaultRange= */ Range.forAnyLevel());
    }

    @Override
    public final Statement apply(Statement base, Description description) {
        if (!description.isTest()) {
            throw new IllegalStateException(
                    "This rule can only be applied to individual tests, it cannot be used as"
                            + " @ClassRule or in a test suite");
        }
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String testName = description.getDisplayName();
                RequiredRange requiredRange = getRequiredRange(description);
                mLog.v("required SDK range for %s: %s", testName, requiredRange);

                int deviceLevel = getDeviceApiLevel().getLevel();
                boolean skip = !requiredRange.range.isInRange(deviceLevel);
                if (skip) {
                    String message =
                            "requires "
                                    + requiredRange.range
                                    + " (and device level is "
                                    + deviceLevel
                                    + "). Reasons: "
                                    + requiredRange.reasons;
                    mLog.i("Skipping %s, as it %s", testName, message);
                    throw new AssumptionViolatedException("Test " + message);
                }
                base.evaluate();
            }
        };
    }

    @VisibleForTesting
    RequiredRange getRequiredRange(Description description) {
        // List of ranges defined in the test itself and its superclasses
        Set<Range> ranges = new HashSet<>();
        List<String> reasons;

        // Start with the test class
        RequiredRange testRange =
                getRequiredRange(
                        description.getAnnotations(),
                        /* allowEmpty= */ false,
                        /* addDefaultRange= */ true,
                        /* setDefaultReason= */ false);
        reasons = testRange.reasons;
        ranges.add(testRange.range);

        // Then the superclasses
        Class<?> clazz = description.getTestClass();
        do {
            RequiredRange testClassRange = getRequiredRangeFromClass(clazz);
            if (testClassRange != null) {
                ranges.add(testClassRange.range);
                reasons.addAll(testClassRange.reasons);
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        if (reasons.isEmpty()) {
            reasons.add(DEFAULT_REASON);
        }
        Range mergedRange = Range.merge(ranges);
        return new RequiredRange(mergedRange, reasons);
    }

    @VisibleForTesting
    RequiredRange getRequiredRange(Collection<Annotation> annotations) {
        return getRequiredRange(
                annotations,
                /* allowEmpty= */ false,
                /* addDefaultRange= */ true,
                /* setDefaultReason= */ true);
    }

    @Nullable
    private RequiredRange getRequiredRangeFromClass(Class<?> testClass) {
        Annotation[] annotations = testClass.getAnnotations();
        if (annotations == null) {
            return null;
        }

        return getRequiredRange(
                Arrays.asList(annotations),
                /* allowEmpty= */ true,
                /* addDefaultRange= */ false,
                /* setDefaultReason= */ false);
    }

    @Nullable
    private RequiredRange getRequiredRange(
            Collection<Annotation> annotations,
            boolean allowEmpty,
            boolean addDefaultRange,
            boolean setDefaultReason) {
        Set<Range> ranges = new HashSet<>();
        if (addDefaultRange) {
            ranges.add(mDefaultRequiredRange);
        }
        String reason = null;

        for (Annotation annotation : annotations) {
            if (annotation instanceof RequiresSdkLevelAtLeastR) {
                ranges.add(Range.forAtLeast(Level.R.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastR) annotation).reason());
            }
            if (annotation instanceof RequiresSdkLevelAtLeastS) {
                ranges.add(Range.forAtLeast(Level.S.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastS) annotation).reason());
            }
            if (annotation instanceof RequiresSdkLevelAtLeastS2) {
                ranges.add(Range.forAtLeast(Level.S2.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastS2) annotation).reason());
            }
            if (annotation instanceof RequiresSdkLevelAtLeastT) {
                ranges.add(Range.forAtLeast(Level.T.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastT) annotation).reason());
                reason = ((RequiresSdkLevelAtLeastT) annotation).reason();
            }
            if (annotation instanceof RequiresSdkLevelAtLeastU) {
                ranges.add(Range.forAtLeast(Level.U.getLevel()));
                reason = getReason(reason, ((RequiresSdkLevelAtLeastU) annotation).reason());
            }
            if (annotation instanceof RequiresSdkRange) {
                RequiresSdkRange range = (RequiresSdkRange) annotation;
                ranges.add(Range.forRange(range.atLeast(), range.atMost()));
                reason = getReason(reason, ((RequiresSdkRange) annotation).reason());
            }
        }

        if (ranges.isEmpty() && allowEmpty) {
            return null;
        }

        if (reason == null && setDefaultReason) {
            reason = DEFAULT_REASON;
        }

        try {
            Range mergedRange = Range.merge(ranges);
            return new RequiredRange(mergedRange, reason);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid range when combining constructor range ("
                            + ranges
                            + ") and annotations ("
                            + annotations
                            + ")",
                    e);
        }
    }

    private String getReason(String currentReason, String newReason) {
        if (newReason == null) {
            return currentReason;
        }
        if (currentReason == null || currentReason.equals(newReason)) {
            return newReason;
        }
        throw new IllegalStateException(
                "Found annotation with reason ("
                        + newReason
                        + ") different from previous annotation reason ("
                        + currentReason
                        + ")");
    }

    @VisibleForTesting
    static final class RequiredRange {
        public final Range range;
        public final List<String> reasons;

        RequiredRange(Range range, @Nullable String... reasons) {
            // getRequiredRange() might call it with a null reason, hence the check for 1st element
            // being null
            this(
                    range,
                    reasons == null || (reasons.length == 1 && reasons[0] == null)
                            ? new ArrayList<>()
                            : Arrays.stream(reasons).collect(Collectors.toList()));
        }

        RequiredRange(Range range, List<String> reasons) {
            this.range = range;
            this.reasons = reasons;
        }

        @Override
        public int hashCode() {
            return Objects.hash(range);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            RequiredRange other = (RequiredRange) obj;
            return Objects.equals(range, other.range);
        }

        @Override
        public String toString() {
            return "[range=" + range + ", reasons=" + reasons + "]";
        }
    }
    /** Gets the device API level. */
    public abstract Level getDeviceApiLevel();

    /** Gets whether the device supports at least Android {@code R}. */
    public final boolean isAtLeastR() {
        return getDeviceApiLevel().isAtLeast(Level.R);
    }

    /** Gets whether the device supports at least Android {@code S}. */
    public final boolean isAtLeastS() {
        return getDeviceApiLevel().isAtLeast(Level.S);
    }

    /** Gets whether the device supports at least Android {@code SC_V2}. */
    public final boolean isAtLeastS2() {
        return getDeviceApiLevel().isAtLeast(Level.S2);
    }

    /** Gets whether the device supports at least Android {@code T}. */
    public final boolean isAtLeastT() {
        return getDeviceApiLevel().isAtLeast(Level.T);
    }

    /** Gets whether the device supports at least Android {@code U}. */
    public final boolean isAtLeastU() {
        return getDeviceApiLevel().isAtLeast(Level.U);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[mDefaultRequiredRange=" + mDefaultRequiredRange + "]";
    }

}
