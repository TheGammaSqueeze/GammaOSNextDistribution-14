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

package com.android.nn.benchmark.core;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BenchmarkResult implements Parcelable {
    // Used by CTS tests.
    public final static String BACKEND_TFLITE_NNAPI = "TFLite_NNAPI";
    public final static String BACKEND_TFLITE_CPU = "TFLite_CPU";

    private final static int TIME_FREQ_ARRAY_SIZE = 32;

    /** The name of the benchmark */
    private String mTestInfo;

    /** Latency results */
    private LatencyResult mLatencyInference;
    private LatencyResult mLatencyCompileWithoutCache;
    private LatencyResult mLatencySaveToCache;
    private LatencyResult mLatencyPrepareFromCache;

    /** Accuracy results */
    private float mSumOfMSEs;
    private float mMaxSingleError;
    private int mNumberOfEvaluatorResults;
    private String[] mEvaluatorKeys = {};
    private float[] mEvaluatorResults = {};

    /** Type of backend used for inference */
    private String mBackendType;

    /** Size of test set using for inference */
    private int mTestSetSize;

    /** Size of compilation cache files in bytes */
    private int mCompilationCacheSizeBytes = 0;

    /** List of validation errors */
    private String[] mValidationErrors = {};

    /** Error that prevents the benchmark from running, e.g. SDK version not supported. */
    private String mBenchmarkError;

    public BenchmarkResult(LatencyResult inferenceLatency,
            float sumOfMSEs, float maxSingleError, String testInfo,
            String[] evaluatorKeys, float[] evaluatorResults,
            String backendType, int testSetSize, String[] validationErrors) {
        mLatencyInference = inferenceLatency;
        mSumOfMSEs = sumOfMSEs;
        mMaxSingleError = maxSingleError;
        mTestInfo = testInfo;
        mBackendType = backendType;
        mTestSetSize = testSetSize;
        if (validationErrors == null) {
            mValidationErrors = new String[0];
        } else {
            mValidationErrors = validationErrors;
        }

        if (evaluatorKeys == null) {
            mEvaluatorKeys = new String[0];
        } else {
            mEvaluatorKeys = evaluatorKeys;
        }
        if (evaluatorResults == null) {
            mEvaluatorResults = new float[0];
        } else {
            mEvaluatorResults = evaluatorResults;
        }
        if (mEvaluatorResults.length != mEvaluatorKeys.length) {
            throw new IllegalArgumentException("Different number of evaluator keys vs values");
        }
        mNumberOfEvaluatorResults = mEvaluatorResults.length;
    }

    public BenchmarkResult(String benchmarkError) {
        mBenchmarkError = benchmarkError;
    }

    public boolean hasValidationErrors() {
        return mValidationErrors.length > 0;
    }

    protected BenchmarkResult(Parcel in) {
        mLatencyInference = in.readParcelable(LatencyResult.class.getClassLoader());
        mLatencyCompileWithoutCache = in.readParcelable(LatencyResult.class.getClassLoader());
        mLatencySaveToCache = in.readParcelable(LatencyResult.class.getClassLoader());
        mLatencyPrepareFromCache = in.readParcelable(LatencyResult.class.getClassLoader());
        mSumOfMSEs = in.readFloat();
        mMaxSingleError = in.readFloat();
        mTestInfo = in.readString();
        mNumberOfEvaluatorResults = in.readInt();
        mEvaluatorKeys = new String[mNumberOfEvaluatorResults];
        in.readStringArray(mEvaluatorKeys);
        mEvaluatorResults = new float[mNumberOfEvaluatorResults];
        in.readFloatArray(mEvaluatorResults);
        if (mEvaluatorResults.length != mEvaluatorKeys.length) {
            throw new IllegalArgumentException("Different number of evaluator keys vs values");
        }
        mBackendType = in.readString();
        mTestSetSize = in.readInt();
        mCompilationCacheSizeBytes = in.readInt();
        int validationsErrorsSize = in.readInt();
        mValidationErrors = new String[validationsErrorsSize];
        in.readStringArray(mValidationErrors);
        mBenchmarkError = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mLatencyInference, flags);
        dest.writeParcelable(mLatencyCompileWithoutCache, flags);
        dest.writeParcelable(mLatencySaveToCache, flags);
        dest.writeParcelable(mLatencyPrepareFromCache, flags);
        dest.writeFloat(mSumOfMSEs);
        dest.writeFloat(mMaxSingleError);
        dest.writeString(mTestInfo);
        dest.writeInt(mNumberOfEvaluatorResults);
        dest.writeStringArray(mEvaluatorKeys);
        dest.writeFloatArray(mEvaluatorResults);
        dest.writeString(mBackendType);
        dest.writeInt(mTestSetSize);
        dest.writeInt(mCompilationCacheSizeBytes);
        dest.writeInt(mValidationErrors.length);
        dest.writeStringArray(mValidationErrors);
        dest.writeString(mBenchmarkError);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<BenchmarkResult> CREATOR =
            new Parcelable.Creator<BenchmarkResult>() {
                @Override
                public BenchmarkResult createFromParcel(Parcel in) {
                    return new BenchmarkResult(in);
                }

                @Override
                public BenchmarkResult[] newArray(int size) {
                    return new BenchmarkResult[size];
                }
            };

    public float getError() {
        return mSumOfMSEs;
    }

    public float getMeanTimeSec() {
        return mLatencyInference.getMeanTimeSec();
    }

    public float getCompileWithoutCacheMeanTimeSec() {
        return mLatencyCompileWithoutCache == null ? 0.0f
            : mLatencyCompileWithoutCache.getMeanTimeSec();
    }

    public float getSaveToCacheMeanTimeSec() {
        return mLatencySaveToCache == null ? 0.0f : mLatencySaveToCache.getMeanTimeSec();
    }

    public float getPrepareFromCacheMeanTimeSec() {
        return mLatencyPrepareFromCache == null ? 0.0f : mLatencyPrepareFromCache.getMeanTimeSec();
    }

    public List<Pair<String, Float>> getEvaluatorResults() {
        List<Pair<String, Float>> results = new ArrayList<>();
        for (int i = 0; i < mEvaluatorKeys.length; ++i) {
            results.add(new Pair<>(mEvaluatorKeys[i], mEvaluatorResults[i]));
        }
        return results;
    }

    @Override
    public String toString() {
        if (!TextUtils.isEmpty(mBenchmarkError)) {
            return mBenchmarkError;
        }

        StringBuilder result = new StringBuilder("BenchmarkResult{" +
                "mTestInfo='" + mTestInfo + '\'' +
                ", mLatencyInference=" + mLatencyInference.toString() +
                ", mSumOfMSEs=" + mSumOfMSEs +
                ", mMaxSingleErrors=" + mMaxSingleError +
                ", mBackendType=" + mBackendType +
                ", mTestSetSize=" + mTestSetSize);
        for (int i = 0; i < mEvaluatorKeys.length; i++) {
            result.append(", ").append(mEvaluatorKeys[i]).append("=").append(mEvaluatorResults[i]);
        }

        result.append(", mValidationErrors=[");
        for (int i = 0; i < mValidationErrors.length; i++) {
            result.append(mValidationErrors[i]);
            if (i < mValidationErrors.length - 1) {
                result.append(",");
            }
        }
        result.append("]");

        if (mLatencyCompileWithoutCache != null) {
            result.append(", mLatencyCompileWithoutCache=")
                    .append(mLatencyCompileWithoutCache.toString());
        }
        if (mLatencySaveToCache != null) {
            result.append(", mLatencySaveToCache=").append(mLatencySaveToCache.toString());
        }
        if (mLatencyPrepareFromCache != null) {
            result.append(", mLatencyPrepareFromCache=")
                    .append(mLatencyPrepareFromCache.toString());
        }
        result.append(", mCompilationCacheSizeBytes=").append(mCompilationCacheSizeBytes);

        result.append('}');
        return result.toString();
    }

    public boolean hasBenchmarkError() {
        return !TextUtils.isEmpty(mBenchmarkError);
    }

    public String getBenchmarkError() {
        if (!hasBenchmarkError()) return null;

        return mBenchmarkError;
    }

    public void setBenchmarkError(String benchmarkError) {
        mBenchmarkError = benchmarkError;
    }

    public String getSummary(float baselineSec) {
        if (hasBenchmarkError()) {
            return getBenchmarkError();
        }
        return mLatencyInference.getSummary(baselineSec);
    }

    public Bundle toBundle(String testName) {
        Bundle results = new Bundle();
        if (!TextUtils.isEmpty(mBenchmarkError)) {
            results.putString(testName + "_error", mBenchmarkError);
            return results;
        }

        mLatencyInference.putToBundle(results, testName + "_inference");
        results.putFloat(testName + "_inference_mean_square_error",
                mSumOfMSEs / mLatencyInference.getIterations());
        results.putFloat(testName + "_inference_max_single_error", mMaxSingleError);
        for (int i = 0; i < mEvaluatorKeys.length; i++) {
            results.putFloat(testName + "_inference_" + mEvaluatorKeys[i], mEvaluatorResults[i]);
        }
        if (mLatencyCompileWithoutCache != null) {
            mLatencyCompileWithoutCache.putToBundle(results, testName + "_compile_without_cache");
        }
        if (mLatencySaveToCache != null) {
            mLatencySaveToCache.putToBundle(results, testName + "_save_to_cache");
        }
        if (mLatencyPrepareFromCache != null) {
            mLatencyPrepareFromCache.putToBundle(results, testName + "_prepare_from_cache");
        }
        if (mCompilationCacheSizeBytes > 0) {
            results.putInt(testName + "_compilation_cache_size", mCompilationCacheSizeBytes);
        }
        return results;
    }

    @SuppressWarnings("AndroidJdkLibsChecker")
    public String toCsvLine() {
        if (!TextUtils.isEmpty(mBenchmarkError)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(mTestInfo).append(',').append(mBackendType);

        mLatencyInference.appendToCsvLine(sb);

        sb.append(',').append(String.join(",",
            String.valueOf(mMaxSingleError),
            String.valueOf(mTestSetSize),
            String.valueOf(mEvaluatorKeys.length),
            String.valueOf(mValidationErrors.length)));

        for (int i = 0; i < mEvaluatorKeys.length; ++i) {
            sb.append(',').append(mEvaluatorKeys[i]);
        }

        for (int i = 0; i < mEvaluatorKeys.length; ++i) {
            sb.append(',').append(mEvaluatorResults[i]);
        }

        for (String validationError : mValidationErrors) {
            sb.append(',').append(validationError.replace(',', ' '));
        }

        sb.append(',').append(mLatencyCompileWithoutCache != null);
        if (mLatencyCompileWithoutCache != null) {
            mLatencyCompileWithoutCache.appendToCsvLine(sb);
        }
        sb.append(',').append(mLatencySaveToCache != null);
        if (mLatencySaveToCache != null) {
            mLatencySaveToCache.appendToCsvLine(sb);
        }
        sb.append(',').append(mLatencyPrepareFromCache != null);
        if (mLatencyPrepareFromCache != null) {
            mLatencyPrepareFromCache.appendToCsvLine(sb);
        }
        sb.append(',').append(mCompilationCacheSizeBytes);

        sb.append('\n');
        return sb.toString();
    }

    public static BenchmarkResult fromInferenceResults(
            String testInfo,
            String backendType,
            List<InferenceInOutSequence> inferenceInOuts,
            List<InferenceResult> inferenceResults,
            EvaluatorInterface evaluator) {
        float[] latencies = new float[inferenceResults.size()];
        float sumOfMSEs = 0;
        float maxSingleError = 0;
        for (int i = 0; i < inferenceResults.size(); i++) {
            InferenceResult iresult = inferenceResults.get(i);
            latencies[i] = iresult.mComputeTimeSec;
            if (iresult.mMeanSquaredErrors != null) {
                for (float mse : iresult.mMeanSquaredErrors) {
                    sumOfMSEs += mse;
                }
            }
            if (iresult.mMaxSingleErrors != null) {
                for (float mse : iresult.mMaxSingleErrors) {
                    if (mse > maxSingleError) {
                        maxSingleError = mse;
                    }
                }
            }
        }

        String[] evaluatorKeys = null;
        float[] evaluatorResults = null;
        String[] validationErrors = null;
        if (evaluator != null) {
            ArrayList<String> keys = new ArrayList<String>();
            ArrayList<Float> results = new ArrayList<Float>();
            ArrayList<String> validationErrorsList = new ArrayList<>();
            evaluator.EvaluateAccuracy(inferenceInOuts, inferenceResults, keys, results,
                    validationErrorsList);
            evaluatorKeys = new String[keys.size()];
            evaluatorKeys = keys.toArray(evaluatorKeys);
            evaluatorResults = new float[results.size()];
            for (int i = 0; i < evaluatorResults.length; i++) {
                evaluatorResults[i] = results.get(i).floatValue();
            }
            validationErrors = new String[validationErrorsList.size()];
            validationErrorsList.toArray(validationErrors);
        }

        // Calc test set size
        int testSetSize = 0;
        for (InferenceInOutSequence iios : inferenceInOuts) {
            testSetSize += iios.size();
        }

        return new BenchmarkResult(new LatencyResult(latencies), sumOfMSEs, maxSingleError,
                testInfo, evaluatorKeys, evaluatorResults, backendType, testSetSize,
                validationErrors);
    }

    public void setCompilationBenchmarkResult(CompilationBenchmarkResult result) {
        mLatencyCompileWithoutCache = new LatencyResult(result.mCompileWithoutCacheTimeSec);
        if (result.mSaveToCacheTimeSec != null) {
            mLatencySaveToCache = new LatencyResult(result.mSaveToCacheTimeSec);
        }
        if (result.mPrepareFromCacheTimeSec != null) {
            mLatencyPrepareFromCache = new LatencyResult(result.mPrepareFromCacheTimeSec);
        }
        mCompilationCacheSizeBytes = result.mCacheSizeBytes;
    }
}
