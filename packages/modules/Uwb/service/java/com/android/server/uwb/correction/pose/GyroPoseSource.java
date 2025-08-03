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
package com.android.server.uwb.correction.pose;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.NonNull;

import com.android.server.uwb.correction.math.MathHelper;
import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.Quaternion;
import com.android.server.uwb.correction.math.Vector3;

import java.security.InvalidParameterException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Provides poses from the phone's gyro, which provides relative changes to yaw, pitch and roll.
 * Positional changes are not supported. Note that this pose source has many limitations,
 * particularly because it drifts and has no sense of down.  It is likely to produce weak results
 * as the phone rotates, and non-elevation phones cannot estimate elevation because there is no
 * absolute sense of pitch from this pose source.
 *
 * The only reason to use this class is to save a very, very marginal amount of power by not using
 * the accelerometer (to make pitch and roll absolute) and the magnetometer (to make yaw absolute),
 * or if those sensors do not exist.
 *
 * Consider using the {@link RotationPoseSource}.
 */
public class GyroPoseSource extends PoseSourceBase implements SensorEventListener {
    private static final String TAG = "GyroPoseSource";
    private final SensorManager mSensorManager;
    private final Sensor mSensor;
    private final int mIntervalUs;
    private final int mIntervalMs;

    float mAbsoluteYaw = 0;
    float mAbsolutePitch = 0;
    float mAbsoluteRoll = 0;

    private long mLastUpdateMs;

    /**
     * Creates a new instance of the GyroPoseSource
     * @param intervalMs How frequently to update the pose.
     */
    public GyroPoseSource(@NonNull Context context, int intervalMs)
            throws UnsupportedOperationException {
        Objects.requireNonNull(context);
        if (intervalMs < MIN_INTERVAL_MS || intervalMs > MAX_INTERVAL_MS) {
            throw new InvalidParameterException("Invalid interval.");
        }
        mSensorManager = context.getSystemService(SensorManager.class);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (mSensor == null) {
            throw new UnsupportedOperationException("Device does not support the gyroscope.");
        }
        this.mIntervalMs = intervalMs;
        mIntervalUs = intervalMs * 1000;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void start() {
        mSensorManager.registerListener(this, mSensor, mIntervalUs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void stop() {
        mSensorManager.unregisterListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Yaw changes are relative to the phone, but rotation vector yaw changes are relative to
        // to the world.
        // Pitch and roll might just spin forever due to drift; this filter might actually
        // be more useful if it only produced yaw values.
        // Further, because the data is accumulated as YPR instead of a Quaternion, there may
        // be strange gimbal side-effects.
        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            long now = Instant.now().toEpochMilli();
            long timeSpan = now - mLastUpdateMs;

            mLastUpdateMs = now;
            if (timeSpan > mIntervalMs * 2L) {
                // Keep a limit on how long we'll integrate motion.
                timeSpan = mIntervalMs;
            }

            // Compute az/el absolute change over the course of the sample.
            float yaw = event.values[1] * (timeSpan / 1000F);
            float pitch = event.values[0] * (timeSpan / 1000F);
            float roll = event.values[2] * (timeSpan / 1000F);

            mAbsoluteYaw = MathHelper.normalizeRadians(mAbsoluteYaw + yaw);
            mAbsolutePitch = MathHelper.normalizeRadians(mAbsolutePitch + pitch);
            mAbsoluteRoll = MathHelper.normalizeRadians(mAbsoluteRoll + roll);

            publish(new Pose(
                    Vector3.ORIGIN,
                    Quaternion.yawPitchRoll(mAbsoluteYaw, mAbsolutePitch, mAbsoluteRoll)
            ));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Don't need to know when accuracy changes.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public EnumSet<Capabilities> getCapabilities() {
        return Capabilities.ROTATION;
    }
}
