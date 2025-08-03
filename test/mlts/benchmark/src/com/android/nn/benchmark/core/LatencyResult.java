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

package com.android.nn.benchmark.core;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class LatencyResult implements Parcelable {
    private final static int TIME_FREQ_ARRAY_SIZE = 32;

    private float mTotalTimeSec;
    private int mIterations;
    private float mTimeStdDeviation;

    /** Time offset for inference frequency counts */
    private float mTimeFreqStartSec;

    /** Index time offset for inference frequency counts */
    private float mTimeFreqStepSec;

    /**
     * Array of inference frequency counts.
     * Each entry contains inference count for time range:
     * [mTimeFreqStartSec + i*mTimeFreqStepSec, mTimeFreqStartSec + (1+i*mTimeFreqStepSec)
     */
    private float[] mTimeFreqSec = {};

    public LatencyResult(float[] results) {
        mIterations = results.length;
        mTotalTimeSec = 0.0f;
        float maxComputeTimeSec = 0.0f;
        float minComputeTimeSec = Float.MAX_VALUE;
        for (float result : results) {
            mTotalTimeSec += result;
            maxComputeTimeSec = Math.max(maxComputeTimeSec, result);
            minComputeTimeSec = Math.min(minComputeTimeSec, result);
        }

        // Calculate standard deviation.
        float latencyMean = (mTotalTimeSec / mIterations);
        float variance = 0.0f;
        for (float result : results) {
            float v = (result - latencyMean);
            variance += v * v;
        }
        variance /= mIterations;
        mTimeStdDeviation = (float) Math.sqrt(variance);

        // Calculate inference frequency/histogram across TIME_FREQ_ARRAY_SIZE buckets.
        mTimeFreqStartSec = minComputeTimeSec;
        mTimeFreqStepSec = (maxComputeTimeSec - minComputeTimeSec) / (TIME_FREQ_ARRAY_SIZE - 1);
        mTimeFreqSec = new float[TIME_FREQ_ARRAY_SIZE];
        for (float result : results) {
            int bucketIndex = (int) ((result - minComputeTimeSec) / mTimeFreqStepSec);
            mTimeFreqSec[bucketIndex] += 1;
        }
    }

    public LatencyResult(Parcel in) {
        mTotalTimeSec = in.readFloat();
        mIterations = in.readInt();
        mTimeStdDeviation = in.readFloat();
        mTimeFreqStartSec = in.readFloat();
        mTimeFreqStepSec = in.readFloat();
        int timeFreqSecLength = in.readInt();
        mTimeFreqSec = new float[timeFreqSecLength];
        in.readFloatArray(mTimeFreqSec);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(mTotalTimeSec);
        dest.writeInt(mIterations);
        dest.writeFloat(mTimeStdDeviation);
        dest.writeFloat(mTimeFreqStartSec);
        dest.writeFloat(mTimeFreqStepSec);
        dest.writeInt(mTimeFreqSec.length);
        dest.writeFloatArray(mTimeFreqSec);
    }

    public static final Parcelable.Creator<LatencyResult> CREATOR =
        new Parcelable.Creator<LatencyResult>() {
          public LatencyResult createFromParcel(Parcel in) {
            return new LatencyResult(in);
          }

          public LatencyResult[] newArray(int size) {
            return new LatencyResult[size];
          }
        };

    public void putToBundle(Bundle results, String prefix) {
        // Reported in ms
        results.putFloat(prefix + "_avg", getMeanTimeSec() * 1000.0f);
        results.putFloat(prefix + "_std_dev", mTimeStdDeviation * 1000.0f);
        results.putFloat(prefix + "_total_time", mTotalTimeSec * 1000.0f);
        results.putInt(prefix + "_iterations", mIterations);
    }

    @Override
    public String toString() {
        return "LatencyResult{"
                + "getMeanTimeSec()=" + getMeanTimeSec()
                + ", mTotalTimeSec=" + mTotalTimeSec
                + ", mIterations=" + mIterations
                + ", mTimeStdDeviation=" + mTimeStdDeviation
                + ", mTimeFreqStartSec=" + mTimeFreqStartSec
                + ", mTimeFreqStepSec=" + mTimeFreqStepSec + "}";
    }

    public int getIterations() { return mIterations; }

    public float getMeanTimeSec() { return mTotalTimeSec / mIterations; }

    private float rebase(float v, float baselineSec) {
        if (v > 0.001) {
            v = baselineSec / v;
        }
        return v;
    }

    public String getSummary(float baselineSec) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("######.##");
        return df.format(rebase(getMeanTimeSec(), baselineSec)) + "X, n=" + mIterations
                + ", μ=" + df.format(getMeanTimeSec() * 1000.0)
                + "ms, σ=" + df.format(mTimeStdDeviation * 1000.0) + "ms";
    }

    public void appendToCsvLine(StringBuilder sb) {
        sb.append(',').append(String.join(",",
            String.valueOf(mIterations),
            String.valueOf(mTotalTimeSec),
            String.valueOf(mTimeFreqStartSec),
            String.valueOf(mTimeFreqStepSec),
            String.valueOf(mTimeFreqSec.length)));

        for (float value : mTimeFreqSec) {
            sb.append(',').append(value);
        }
    }
}
