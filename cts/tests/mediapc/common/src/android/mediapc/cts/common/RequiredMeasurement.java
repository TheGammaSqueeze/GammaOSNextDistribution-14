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

package android.mediapc.cts.common;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * A specific measurement for a Performance Class requirement.
 */
@AutoValue
public abstract class RequiredMeasurement<T> {
    private static final String TAG = RequiredMeasurement.class.getSimpleName();

    private T measuredValue;  // Note this is not part of the equals calculations
    private boolean measuredValueSet = false;

    public static <T> Builder<T> builder() {
        return new AutoValue_RequiredMeasurement.Builder<T>();
    }

    public abstract String id();

    /**
     * Tests if the measured value satisfies the  expected value(eg >=)
     * measuredValue, expectedValue
     */
    public abstract BiPredicate<T, T> predicate();

    /**
     * Maps MPC level to the expected value.
     */
    public abstract ImmutableMap<Integer, T> expectedValues();

    public void setMeasuredValue(T measuredValue) {
        this.measuredValueSet = true;
        this.measuredValue = measuredValue;
    }

    @AutoValue.Builder
    public static abstract class Builder<T> {

        public abstract Builder<T> setId(String id);

        public abstract Builder<T> setPredicate(BiPredicate<T, T> predicate);

        public abstract ImmutableMap.Builder<Integer, T> expectedValuesBuilder();

        public Builder<T> addRequiredValue(Integer performanceClass, T expectedValue) {
            this.expectedValuesBuilder().put(performanceClass, expectedValue);
            return this;
        }

        public abstract RequiredMeasurement<T> build();
    }

    public final RequirementConstants.Result meetsPerformanceClass(int mediaPerformanceClass)
            throws IllegalStateException {

        if (!this.measuredValueSet) {
            throw new IllegalStateException("measured value not set for required measurement "
                + this.id());
        }

        if (!this.expectedValues().containsKey(mediaPerformanceClass)) {
            return RequirementConstants.Result.NA;
        } else if (this.measuredValue == null || !this.predicate().test(this.measuredValue,
                this.expectedValues().get(mediaPerformanceClass))) {
            return RequirementConstants.Result.UNMET;
        } else {
            return RequirementConstants.Result.MET;
        }
    }

    /**
     * @return map PerfomenaceClass to result if that performance class has been met
     */
    public Map<Integer, RequirementConstants.Result> getPerformanceClass() {
        Map<Integer, RequirementConstants.Result> perfClassResults = new HashMap<>();
        for (Integer pc: this.expectedValues().keySet()) {
            perfClassResults.put(pc, this.meetsPerformanceClass(pc));
        }
        return perfClassResults;
    }

    @Override
    public final String toString() {
        return "Required Measurement with:"
            + "\n\tId: " + this.id()
            + "\n\tPredicate: " + this.predicate()
            + "\n\tMeasured Value: " + this.measuredValue
            + "\n\tExpected Values: " + this.expectedValues();
    }

    public void writeValue(ReportLog log) throws IllegalStateException {

        if (!this.measuredValueSet) {
            throw new IllegalStateException("measured value not set for required measurement "
                + this.id());
        }

        if (this.measuredValue == null) {
            log.addValue(this.id(), "<nullptr>", ResultType.NEUTRAL, ResultUnit.NONE);
        } else if (this.measuredValue instanceof Integer) {
            log.addValue(this.id(), (int)this.measuredValue, ResultType.NEUTRAL, ResultUnit.NONE);
        } else if (this.measuredValue instanceof Long) {
            log.addValue(this.id(), (long)this.measuredValue, ResultType.NEUTRAL, ResultUnit.NONE);
        } else if (this.measuredValue instanceof Float) {
            log.addValue(this.id(), (float)this.measuredValue, ResultType.NEUTRAL, ResultUnit.NONE);
        } else if (this.measuredValue instanceof Double) {
            log.addValue(this.id(), (double)this.measuredValue, ResultType.NEUTRAL,
                ResultUnit.NONE);
        } else if (this.measuredValue instanceof Boolean) {
            log.addValue(this.id(), (boolean)this.measuredValue, ResultType.NEUTRAL,
                ResultUnit.NONE);
        } else if (this.measuredValue instanceof String) {
            log.addValue(this.id(), (String)this.measuredValue, ResultType.NEUTRAL,
                ResultUnit.NONE);
        } else {
            // reporting all other types as Strings using toString()
            log.addValue(this.id(), this.measuredValue.toString(), ResultType.NEUTRAL,
                ResultUnit.NONE);
        }
    }
}
