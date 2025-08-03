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
 * Provides poses from the device's 6DOF fused sensor, which provides a full position and rotation
 * relative to an arbitrary origin.
 *
 * This virtual sensor is usually only implemented in purpose-built spatial-tracking systems such as
 * Google Glass and Meta Quest. It can be power-hungry.
 *
 * Functionally this resembles ARCore's pose tracking, but relieves the difficulty of having to get
 * ARCore pose updates from the user application.
 */
public class SixDofPoseSource extends PoseSourceBase implements SensorEventListener {
    private static final String TAG = "SixDOFPoseSource";
    private final SensorManager mSensorManager;
    private final Sensor mSensor;
    private final int mIntervalUs;

    // The local system is oriented with Y up. The Android rotation vector has Z up. Pitching down
    // will correct this.
    private final Quaternion mRotator = Quaternion.yawPitchRoll(0, -F_HALF_PI, 0);

    /**
     * Creates a new instance of the SixDofPoseSource.
     * @param intervalMs How frequently to update the pose.
     */
    public SixDofPoseSource(@NonNull Context context, int intervalMs) {
        Objects.requireNonNull(context);
        if (intervalMs < MIN_INTERVAL_MS || intervalMs > MAX_INTERVAL_MS) {
            throw new InvalidParameterException("Invalid interval.");
        }
        mSensorManager = context.getSystemService(SensorManager.class);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_POSE_6DOF);
        if (mSensor == null) {
            throw new UnsupportedOperationException(
                    "Device does not support the Pose 6DOF sensor."
            );
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
        if (event.sensor.getType() == Sensor.TYPE_POSE_6DOF) {
            // The rotation vector is a quaternion oriented to gravity and geomagnetic north.
            // See https://developer.android.com/reference/android/hardware/Sensor#TYPE_POSE_6DOF

            // The local system is oriented with Y up. The Android position vector has Z up.
            Vector3 position = new Vector3(
                    event.values[4],
                    event.values[6], // Y and Z swapped
                    event.values[5]
            );

            Quaternion rotation = new Quaternion(
                    event.values[0],
                    event.values[1],
                    event.values[2],
                    event.values[3]
            );
            publish(new Pose(position, Quaternion.multiply(mRotator, rotation)));
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
        return Capabilities.ALL;
    }
}
