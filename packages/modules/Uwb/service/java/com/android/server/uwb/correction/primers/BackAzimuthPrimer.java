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
package com.android.server.uwb.correction.primers;

import static com.android.server.uwb.correction.math.MathHelper.F_HALF_PI;
import static com.android.server.uwb.correction.math.MathHelper.F_PI;
import static com.android.server.uwb.correction.math.MathHelper.MS_PER_SEC;
import static com.android.server.uwb.correction.math.MathHelper.normalizeRadians;

import static java.lang.Math.abs;
import static java.lang.Math.exp;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.signum;
import static java.lang.Math.toDegrees;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.correction.math.MathHelper;
import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.math.SphericalVector.Annotated;
import com.android.server.uwb.correction.pose.IPoseSource;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Tracks the correlation between azimuth and pose yaw to determine if the controlee has gone
 * to the other side of the device. This mirrors the azimuth value if:
 * - The prediction crosses the ±90° threshold.
 * - The covariance between pose yaw and azimuth exceeds a certain threshold.
 */
public class BackAzimuthPrimer implements IPrimer {
    static final String TAG = "BackAzimuthPrimer";
    /** The decay rate of the low pass filter. (0-1, decay per sample) */
    private static final double FOM_DECAY = 0.1;
    /** How quickly the FOM falls when using mirrored predictions. */
    private static final int MINIMUM_DETERMINATIONS = 4;

    private static final boolean sDebug;
    // Keep sample time measurements reasonable to prevent div/0 or overflows.
    private static final long MIN_TIME_MS = 5; // Shortest allowed time between ranging samples
    private static final long MAX_TIME_MS = 5000; // Longest allowed time between ranging samples

    static {
        sDebug = (Build.TYPE != null && Build.TYPE.equals("userdebug"))
                || System.getProperty("DEBUG") != null;
    }

    private final boolean mMaskRawAzimuthWhenBackfacing;
    private final float mDiscrepancyCoefficient;
    private final int mWindowSize;
    private final float mStdDev;
    private final float mNormalThresholdRadPerSec;
    private final float mMirrorThresholdRadPerSec;
    private final Queue<Float> mScoreHistory;
    private final Queue<Float> mDiscrepancyHistory = new ArrayDeque<>();
    private boolean mMirrored = false;
    private float mLastAzimuthPrediction;
    // This initial value causes the first sample to have the least effect.
    private long mLastSampleTimeMs = Long.MIN_VALUE;
    private Pose mLastPose;
    private SphericalVector mLastInput;
    private int mDeterminationCount = 0;
    private double mFomFilterValue = MINIMUM_FOM;
    private double mLastGoodReferenceTimeMs;

    /**
     * Creates a new instance of the BackAzimuthPrimer class.
     *
     * @param normalThresholdRadPerSec     How many radians per second of correlated rotation are
     *                                     necessary to force a non-mirrored azimuth.
     * @param mirrorThresholdRadPerSec     How many radians per second of correlated rotation are
     *                                     necessary to force a mirrored azimuth.
     * @param windowSize                   The size of the moving window filter for determining
     *                                     correlation.
     * @param maskRawAzimuthWhenBackfacing If true, readings from the back will be replaced with
     *                                     predictions. If false, azimuth readings will be mirrored
     *                                     front-to-back.
     * @param stdDev                       Controls the width of the curve used to judge if the
     *                                     readings are acting like
     *                                     unmirrored or mirrored readings.
     * @param discrepancyCoefficient       The coefficient of how much the typical forward
     *                                     prediction
     *                                     error (rads per sample) should count against the forward
     *                                     score (rads per second). For
     *                                     example, a value of 0.5 will cause the score to be 5
     *                                     degrees lower if the typical front
     *                                     prediction error is 10.
     */
    public BackAzimuthPrimer(float normalThresholdRadPerSec,
            float mirrorThresholdRadPerSec,
            int windowSize,
            boolean maskRawAzimuthWhenBackfacing,
            float stdDev,
            float discrepancyCoefficient
    ) {
        mNormalThresholdRadPerSec = normalThresholdRadPerSec;
        mMirrorThresholdRadPerSec = mirrorThresholdRadPerSec;
        mMaskRawAzimuthWhenBackfacing = maskRawAzimuthWhenBackfacing;
        mDiscrepancyCoefficient = discrepancyCoefficient;
        mScoreHistory = new ArrayDeque<>();
        mWindowSize = windowSize;
        mStdDev = stdDev;
    }

    /**
     * Uses pose information to disambiguate the input azimuth.
     *
     * @param input      The original UWB reading.
     * @param prediction The previous filtered UWB result adjusted by the pose change since then.
     * @param poseSource A pose source that may indicate phone orientation.
     * @param timeMs     When the input occurred, in ms since boot.
     * @return A replacement value for the UWB vector that has been corrected for the situation.
     */
    @SuppressWarnings("ConstantConditions") /* Unboxing longs in mCaptureTimes */
    @Override
    public SphericalVector.Annotated prime(
            @NonNull SphericalVector.Annotated input,
            @Nullable SphericalVector prediction,
            @Nullable IPoseSource poseSource,
            long timeMs) {
        if (!input.hasAzimuth || poseSource == null || prediction == null) {
            // Can't perform correction if there is no azimuth data, no pose information,
            // or no prediction.
            return input;
        }

        long timeDeltaMs = min(MAX_TIME_MS, max(MIN_TIME_MS, timeMs - mLastSampleTimeMs));
        float timeScale = (float) MathHelper.MS_PER_SEC / timeDeltaMs;
        mLastSampleTimeMs = timeMs;

        // Mirror if the pose simply rotated the azimuth past the 90-degree mark.
        if (abs(prediction.azimuth) > F_HALF_PI
                && abs(mLastAzimuthPrediction) <= F_HALF_PI) {
            // Prediction went from forward to backward-facing.
            mMirrored = true;
            flipScoreHistory();
        } else if (abs(prediction.azimuth) <= F_HALF_PI
                && abs(mLastAzimuthPrediction) > F_HALF_PI) {
            // Prediction went from backward to forward-facing.
            mMirrored = false;
            flipScoreHistory();
        }
        mLastAzimuthPrediction = prediction.azimuth;
        // Because the prediction is influenced by mirroring itself and can have a significant
        //  delay due to the filter, we will not be using the prediction to guess front/back.

        // Get coordinates for normal and mirrored azimuth versions of the input.
        // input.vector may be >90deg due to previous primers, so front/back is forced here.
        SphericalVector normalInput = forceAzimuth(input, false);
        SphericalVector mirrorInput = forceAzimuth(input, true);

        Pose newPose = poseSource.getPose();
        if (mLastPose == null || newPose == null || mLastPose == newPose || mLastInput == null) {
            // Can't do anything without pose deltas and input history.
            mLastPose = newPose;
            mLastInput = normalInput;
            return input;
        }

        // Pose transform for theorizing how the previous reading might have changed.
        // Note that we're using a full pose transform instead of just azimuth changes, as
        // the phone may have rolled or performed other movements that aren't just azimuth.
        Pose deltaPose = Pose.compose(newPose.inverted(), mLastPose);

        // Theorize, based on the old location, what the new location should be for mirrored and
        // unmirrored inputs.
        SphericalVector normalTheory = transformSpherical(mLastInput, deltaPose);
        SphericalVector mirrorTheory = transformSpherical(mirrorAzimuth(mLastInput), deltaPose);

        // Compute how many radians of pose change have affected the azimuth. More movement means
        // more certainty can be applied to the score.
        float azimuthDeltaFromPoseRad =
                normalizeRadians(abs(normalTheory.azimuth - mLastInput.azimuth));

        // Judge how well the front and back predictions did.
        float normalDifference = abs(normalizeRadians(normalTheory.azimuth - normalInput.azimuth));
        float mirrorDifference = abs(normalizeRadians(mirrorTheory.azimuth - mirrorInput.azimuth));
        // Note that one of these predictions will be perfect if the input itself is a prediction,
        // which FovPrimer might do. Carrying this detail in SphericalVector.Sparse may provide an
        // opportunity to ignore scoring when the input is predicted.
        float normalAccuracy = bell(normalDifference, mStdDev);
        float mirrorAccuracy = bell(mirrorDifference, mStdDev);

        // Score by the user's pose-induced azimuth change.
        float scoreRadPerSec = (normalAccuracy - mirrorAccuracy) // score per sample
                * azimuthDeltaFromPoseRad // convert to score radians per sample
                * timeScale; // convert to score radians per second

        // Bias the score toward the back based on UWB noise.
        float scoreRadPerSecBiased = biasScore(scoreRadPerSec, normalDifference, mirrorDifference);

        mLastInput = normalInput;
        mLastPose = newPose;

        mScoreHistory.offer(scoreRadPerSecBiased);

        double typScore = 0;
        if (mScoreHistory.size() > mWindowSize) {
            mScoreHistory.poll();
            // Get the median score.
            typScore = mScoreHistory.stream().mapToDouble(Float::doubleValue).sorted()
                    .skip(mWindowSize / 2).limit(1 + (mWindowSize % 2)).average().getAsDouble();

            // Finally, the mirroring decision.
            if (typScore > mNormalThresholdRadPerSec) {
                mMirrored = false;
                if (mDeterminationCount < MINIMUM_DETERMINATIONS) {
                    mDeterminationCount++;
                }
            } else if (typScore < -mMirrorThresholdRadPerSec) {
                mMirrored = true;
                if (mDeterminationCount < MINIMUM_DETERMINATIONS) {
                    mDeterminationCount++;
                }
            }
        }

        if (sDebug) {
            Log.d(TAG,
                    String.format(
                            "time %4d, pose % 6.1f, nd % 6.1f (%3d%%), md % 6.1f (%3d%%), "
                                    + "rawSco % 5.1f, sco % 5.1f, aggSco % 5.1f, %s",
                            timeDeltaMs,
                            toDegrees(azimuthDeltaFromPoseRad),
                            toDegrees(normalDifference), (int) (normalAccuracy * 100),
                            toDegrees(mirrorDifference), (int) (mirrorAccuracy * 100),
                            toDegrees(scoreRadPerSec),
                            toDegrees(scoreRadPerSecBiased),
                            toDegrees(typScore),
                            mMirrored ? "mirr" : "norm"
                    ));
        }

        SphericalVector result = input;

        if (mMirrored && mMaskRawAzimuthWhenBackfacing) {
            // Replace angles with prediction. The mMaskRawAzimuthWhenBackfacing setting will be set
            // when through-device readings are poor or not predictably mirrored.
            result = SphericalVector.fromRadians(
                    prediction.azimuth,
                    prediction.elevation,
                    input.distance);
        }

        result = forceAzimuth(result, mMirrored);

        Annotated annotatedResult = new Annotated(
                result,
                true,
                input.hasElevation,
                input.hasDistance).copyFomFrom(input);

        updateFom(annotatedResult, normalAccuracy, mirrorAccuracy);

        return annotatedResult;
    }

    /**
     * Changes the azimuthFom of the annotated result to reflect 3 conditions:
     * 1. How long the filter has been using predicted values
     * 2. How well-correlated yaw and azimuth are (normal or mirror Accuracy)
     * 3. Whether or not a number of initial good determinations have been made.
     * @param annotatedResult The reading whose azimuthFom will be updated.
     * @param normalAccuracy The computed accuracy of correlation for the normal case.
     * @param mirrorAccuracy The computed accuracy of correlation for the mirrored case.
     */
    private void updateFom(Annotated annotatedResult, float normalAccuracy, float mirrorAccuracy) {
        double newFom;
        if (mMirrored) {
            newFom = mirrorAccuracy;
            if (mMaskRawAzimuthWhenBackfacing) {
                // We've gone totally to predictions. Tweak the FOM based on how fresh our data is.
                double elapsedMs = mLastSampleTimeMs - mLastGoodReferenceTimeMs;
                double fom = max(1 - elapsedMs / MS_PER_SEC * FALLOFF_FOM_PER_SEC, MINIMUM_FOM);
                annotatedResult.azimuthFom *= fom;
            }
        } else {
            newFom = normalAccuracy;

            // This brings the FOM back up for subsequent estimations
            mLastGoodReferenceTimeMs = mLastSampleTimeMs;
        }
        mFomFilterValue = mFomFilterValue * (1 - FOM_DECAY) + (newFom * FOM_DECAY);
        annotatedResult.azimuthFom *= mFomFilterValue;

        if (mDeterminationCount < MINIMUM_DETERMINATIONS) {
            // If we haven't actually seen good evidence of front or back, our certainty is up to
            // 50% less, depending on how many determinations have been made.
            annotatedResult.azimuthFom *=
                    0.5 + ((double) mDeterminationCount) / MINIMUM_DETERMINATIONS / 2;
        }
    }

    /** Flips the score history, for when azimuth goes from front to behind or vice-versa. */
    private void flipScoreHistory() {
        for (int x = 0; x < mScoreHistory.size(); x++) {
            Float sh = mScoreHistory.poll();
            mScoreHistory.offer(sh == null ? null : -sh);
        }
    }

    /**
     * Biases a score toward the back based on the accuracy of front-facing predictions.
     *
     * @param scoreRadPerSec   The score to bias.
     * @param normalDifference The difference between the front-based prediction and front-based
     *                         reading.
     * @param mirrorDifference The difference between the back-based prediction and back-based
     *                         reading.
     * @return A new, adjusted version of the input score.
     */
    private float biasScore(float scoreRadPerSec, float normalDifference, float mirrorDifference) {
        // Discrepancy measures how much the forward-facing values are off. They will be WAY off
        // if the UWB signal is noisy, and slightly off if the signal is in the back. Rear azimuths
        // are usually both.
        mDiscrepancyHistory.offer(normalDifference);

        if (mDiscrepancyHistory.size() > mWindowSize) {
            mDiscrepancyHistory.poll();
            float avgDiscrepancyRad =
                    (float) mDiscrepancyHistory.stream().mapToDouble(Float::doubleValue).average()
                            .getAsDouble();

            // Average discrepancy is multiplied by the configurable coefficient to bias the
            //  score toward the back.
            return scoreRadPerSec - avgDiscrepancyRad * mDiscrepancyCoefficient;
        }
        return scoreRadPerSec;
    }

    /**
     * Applies a pose delta (a transform) to a spherical coordinate.
     *
     * @param input     The spherical vector to transform.
     * @param deltaPose The pose object representing how to transform the input.
     * @return A new SphericalVector representing the input transformed by the delta pose.
     */
    private SphericalVector transformSpherical(SphericalVector input, Pose deltaPose) {
        return SphericalVector.fromCartesian(deltaPose.transformPoint(input.toCartesian()));
    }

    /**
     * Mirrors the azimuth front-to-back or back-to-front.
     *
     * @param vector The SphericalVector to mirror.
     * @return A mirrored version of the SphericalVector.
     */
    @NonNull
    private SphericalVector mirrorAzimuth(SphericalVector vector) {
        return SphericalVector.fromRadians(
                signum(vector.azimuth) * (F_PI - abs(vector.azimuth)),
                vector.elevation,
                vector.distance);
    }

    /**
     * Forces the azimuth to be front or back, mirroring it as necessary.
     *
     * @param vector The SphericalVector to force to a direction.
     * @param back   If true, forces the SphericalVector's azimuth to be back, otherwise forward.
     * @return A version of the SphericalVector that is facing the specified direction.
     */
    @NonNull
    private SphericalVector forceAzimuth(SphericalVector vector, boolean back) {
        if (back == abs(vector.azimuth) < F_HALF_PI) {
            return mirrorAzimuth(vector);
        }
        return vector;
    }

    /**
     * Plots x on a bell curve with a magnitude of 1. This is a gaussian curve(φ) multiplied by
     * 1/φ(0) so that bell(0, n) = 1.
     *
     * @param x      The x value of the normal curve.
     * @param stdDev The standard deviation to use for the initial normal curve.
     * @return A value along a gaussian curve scaled by 1/φ(0).
     */
    private float bell(float x, float stdDev) {
        float variance = stdDev * stdDev;
        return (float) exp(-(x * x / (2 * variance)));
    }
}
