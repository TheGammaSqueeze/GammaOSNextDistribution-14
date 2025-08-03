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
package com.android.server.uwb.correction;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.correction.filtering.IPositionFilter;
import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.SphericalVector;
import com.android.server.uwb.correction.pose.IPoseSource;
import com.android.server.uwb.correction.pose.PoseEventListener;
import com.android.server.uwb.correction.primers.IPrimer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Consumes raw UWB values and outputs filtered UWB values. See the {@link UwbFilterEngine.Builder}
 * for how it is configured.
 */
public class UwbFilterEngine implements AutoCloseable, PoseEventListener {
    public static final String BIG_LOG_TAG = "UwbFilterEngine";
    @NonNull private final List<IPrimer> mPrimers;
    @Nullable private final IPositionFilter mFilter;
    @Nullable private final IPoseSource mPoseSource;
    private static final boolean sDebug;

    static {
        sDebug = (Build.TYPE != null && Build.TYPE.equals("userdebug"))
                || System.getProperty("DEBUG") != null;
    }

    /**
     * The last UWB reading, after priming or filtering, depending on which facilities
     * are available.  If computation fails or is not possible (ie - filter or primer is not
     * configured), the computation function will return this.
     */
    @Nullable private SphericalVector.Annotated mLastInputState;

    private boolean mClosed;

    private UwbFilterEngine(
            @NonNull List<IPrimer> primers,
            @Nullable IPoseSource poseSource,
            @Nullable IPositionFilter filter) {
        this.mPrimers = primers;
        this.mPoseSource = poseSource;
        this.mFilter = filter;
        if (poseSource != null) {
            // A listener must be registered in order for the poseSource to start.
            poseSource.registerListener(this);
        }
    }

    /**
     * Updates the engine with the latest UWB data.
     * @param position The raw position produced by the UWB hardware.
     * @param timeMs The time at which the UWB value was received, in ms since boot.
     */
    public void add(@NonNull SphericalVector.Annotated position, long timeMs) {
        StringBuilder bigLog = sDebug ? new StringBuilder(position.toString()) : null;
        Objects.requireNonNull(position);

        SphericalVector prediction = compute(timeMs);

        if (sDebug) {
            bigLog.append("(Prediction ");
            bigLog.append(prediction);
            bigLog.append(")");
        }

        for (IPrimer primer: mPrimers) {
            position = primer.prime(position, prediction, mPoseSource, timeMs);
            if (bigLog != null) {
                bigLog.append(" ->")
                        .append(primer.getClass().getSimpleName()).append("=")
                        .append(position);
            }
        }
        if (position.isComplete() || prediction == null) {
            mLastInputState = position;
        } else {
            // Primers did not fully prime the position vector. This can happen when elevation is
            //  missing and there is no primer for an estimate, or if there was a bad UWB reading.
            // Fill in with predictions.
            mLastInputState = SphericalVector.fromRadians(
                    position.hasAzimuth ? position.azimuth : prediction.azimuth,
                    position.hasElevation ? position.elevation : prediction.elevation,
                    position.hasDistance ? position.distance : prediction.distance
            ).toAnnotated().copyFomFrom(position);
        }
        if (mFilter != null) {
            mFilter.updatePose(mPoseSource, timeMs);
            mFilter.add(mLastInputState, timeMs);
            if (bigLog != null) {
                bigLog.append(" : filtered=")
                        .append(mFilter.compute(timeMs));
            }
        }
        if (bigLog != null) {
            Log.d(BIG_LOG_TAG, bigLog.toString());
        }
    }

    /**
     * Computes the most probable UWB location as of the given time.
     * @param timeMs The time at which the UWB value was received, in ms since boot.
     * @return A SphericalVector representing the most likely UWB location.
     */
    @Nullable
    public SphericalVector.Annotated compute(long timeMs) {
        if (mFilter != null) {
            mFilter.updatePose(mPoseSource, timeMs);
            return mFilter.compute(timeMs);
        }
        return mLastInputState;
    }

    /**
     * Gets the current device pose.
     */
    @NonNull
    public Pose getPose() {
        Pose pose = null;
        if (mPoseSource != null) {
            pose = mPoseSource.getPose();
        }
        if (pose == null) {
            pose = Pose.IDENTITY;
        }
        return pose;
    }

    /**
     * Frees or closes all resources consumed by this object.
     */
    @Override
    public void close() {
        if (!mClosed) {
            mClosed = true;
            if (mPoseSource != null) {
                mPoseSource.unregisterListener(this);
            }
        }
    }

    /**
     * Called when there is an update to the device's pose. The origin is arbitrary, but
     * position could be relative to the starting position, and rotation could be relative
     * to magnetic north and the direction of gravity.
     *
     * @param pose The new location and orientation of the device.
     */
    @Override
    public void onPoseChanged(@SuppressWarnings("unused") @NonNull Pose pose) {
        // We don't use pose change as they happen at this point. If you're implementing UWB
        // oversampling, this might be a good place to call compute() and produce a result.
    }

    /**
     * Builder for a {@link UwbFilterEngine}.
     */
    public static class Builder {
        @Nullable private IPositionFilter mFilter;
        @Nullable private IPoseSource mPoseSource;
        @NonNull private final ArrayList<IPrimer> mPrimers = new ArrayList<>();

        /**
         * Sets the filter this UWB filter engine will use. If not provided, no filtering will
         * occur.
         * @param filter The position filter to use.
         * @return This builder.
         */
        public Builder setFilter(IPositionFilter filter) {
            this.mFilter = filter;
            return this;
        }

        /**
         * Sets the pose source the UWB filter engine will use. If not set, no pose processing
         * will occur.
         * @param poseSource Any pose source.
         * @return This builder.
         */
        public Builder setPoseSource(IPoseSource poseSource) {
            this.mPoseSource = poseSource;
            return this;
        }

        /**
         * Adds a primer to the list of primers the engine will use. The primers will execute
         * in the order in which this is called.
         * @param primer The primer to add.
         * @return This builder.
         */
        public Builder addPrimer(@NonNull IPrimer primer) {
            Objects.requireNonNull(primer);
            this.mPrimers.add(primer);
            return this;
        }

        /**
         * Builds a UWB filter engine based on the calls made to the builder.
         * @return the constructed UWB filter engine.
         */
        public UwbFilterEngine build() {
            return new UwbFilterEngine(mPrimers, mPoseSource, mFilter);
        }
    }
}
