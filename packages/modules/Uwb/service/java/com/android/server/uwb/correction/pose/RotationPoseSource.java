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

import static com.android.server.uwb.correction.math.MathHelper.F_HALF_PI;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.NonNull;

import com.android.server.uwb.correction.math.Pose;
import com.android.server.uwb.correction.math.Quaternion;
import com.android.server.uwb.correction.math.Vector3;

import java.security.InvalidParameterException;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Provides poses from the phone's rotation vector, which provides yaw, pitch and roll,
 * oriented where +Y is north and +Z is skyward.  Positional changes are not supported.
 * The output value is rotated to the local system's orientation, where +Y is skyward.
 *
 * This pose source is very reliable and available on almost all phones, but provides no
 * positioning.
 */
public class RotationPoseSource extends PoseSourceBase implements SensorEventListener {
    private static final String TAG = "RotationPoseSource";
    private final SensorManager mSensorManager;
    private final Sensor mSensor;
    private final int mIntervalUs;

    // The local system is oriented with Y up.  The Android rotation vector has Z up. Pitching down
    // will correct this.
    private final Quaternion mRotator = Quaternion.yawPitchRoll(0, -F_HALF_PI, 0);

    /**
     * Creates a new instance of the RotationPoseSource
     * @param intervalMs How frequently to update the pose.
     */
    public RotationPoseSource(@NonNull Context context, int intervalMs) {
        Objects.requireNonNull(context);
        if (intervalMs < MIN_INTERVAL_MS || intervalMs > MAX_INTERVAL_MS) {
            throw new InvalidParameterException("Invalid interval.");
        }
        mSensorManager = context.getSystemService(SensorManager.class);
        if (mSensorManager == null) {
            throw new UnsupportedOperationException("Sensor manager is not available.");
        }
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (mSensor == null) {
            throw new UnsupportedOperationException("Device does not support the rotation vector.");
        }
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
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // The rotation vector is a quaternion oriented to gravity and geomagnetic north.
            Quaternion base = new Quaternion(
                    event.values[0],
                    event.values[1],
                    event.values[2],
                    event.values[3]
            );
            publish(new Pose(Vector3.ORIGIN, Quaternion.multiply(mRotator, base)));
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
        return Capabilities.UPRIGHT_ROTATION;
    }
}
