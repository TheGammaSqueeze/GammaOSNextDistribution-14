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
package com.android.server.uwb.correction.math;

import static com.android.server.uwb.correction.math.MathHelper.F_HALF_PI;
import static com.android.server.uwb.correction.math.MathHelper.F_PI;

import static java.lang.Math.abs;
import static java.lang.Math.acos;
import static java.lang.Math.asin;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.signum;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import android.util.Log;

import androidx.annotation.NonNull;

import com.android.internal.annotations.Immutable;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a point in space as distance, azimuth and elevation.
 * This uses OpenGL's right-handed coordinate system, where the origin is facing in the
 * -Z direction. Increasing azimuth rotates around Y and increases X.  Increasing
 * elevation rotates around X and increases Y.
 *
 * Note that this is NOT quite a spherical vector.  It represents angles seen by AoA antennas.
 * In this implementation, azimuth and elevation are treated the same. Therefore, for example:
 * Very "up" or "down" targets will have an azimuth near 0, because the signal will arrive at
 * both AoA antennas at nearly the same time.
 * In a spherical vector, azimuth is computed exclusively from the horizontal plane and treated
 * independently of the vertical axis, but elevation is computed along the plane of the azimuth.
 * This also means that there are some angles that are impossible.  For example, something with
 * a 90deg azimuth (directly right of the phone) cannot possibly be viewed by the elevation
 * antennas from any angle other than 0deg.
 */
@Immutable
public final class AoaVector {
    // If true, negative distances will be converted to a positive distance facing the opposite
    // direction.
    private static final boolean NORMALIZE_NEGATIVE_DISTANCE = false;
    public static boolean logWarnings = false;
    public final float distance;
    public final float azimuth;
    public final float elevation;

    /**
     * Creates a AoAVector from the azimuth, elevation and distance of a viewpoint that is
     * facing into the -Z axis. Illegal azimuth and elevation combinations will be scaled away
     * from +/-90deg such that they are legal.
     *
     * @param azimuth The angle along the X axis, around the Y axis.
     * @param elevation The angle along the Y axis, around the X axis.
     * @param distance The distance to the origin.
     */
    private AoaVector(float azimuth, float elevation, float distance) {
        elevation = MathHelper.normalizeRadians(elevation);
        float ae = abs(elevation);
        if (ae > F_HALF_PI) {
            // Normalize elevation to be only +/-90 - if it's outside that, mirror and bound the
            // elevation and flip the azimuth.
            elevation = (F_PI - ae) * signum(elevation);
            azimuth += F_PI;
        }
        if (NORMALIZE_NEGATIVE_DISTANCE && distance < 0) {
            // Negative distance is equivalent to a flipped elevation and azimuth.
            azimuth += F_PI; // turn 180deg.
            elevation = -elevation; // Mirror top-to-bottom
            distance = -distance;
        }
        azimuth = MathHelper.normalizeRadians(azimuth);

        // Now verify validity
        boolean backFacing = abs(azimuth) > F_HALF_PI;

        // Compute azimuth if it was front-facing.
        float laz = backFacing ? (F_PI * signum(azimuth) - azimuth) : azimuth;
        float angleSum = abs(laz) + abs(elevation);
        float scaleFactor = angleSum / (F_HALF_PI);
        if (scaleFactor > 1) {
            // The combination of degrees isn't possible - for example, the azimuth suggests that
            // the target is exactly 90deg to the right, and yet elevation is non-zero.
            // The elevation and azimuth will be scaled down until they are within
            // legal limits. This will create a bias away from 90-degree readings.
            // Note that azimuth will be corrected to higher than 90deg if it was originally
            // above 90deg.
            elevation /= scaleFactor;
            azimuth = backFacing ? (F_PI * signum(azimuth) - laz / scaleFactor) : (azimuth
                    / scaleFactor);
            if (logWarnings) {
                Log.w("AOA", String.format(
                        "AoA value is illegal by a factor of %4.3f: ⦡% 3.1f,⦨% 3.1f",
                        scaleFactor,
                        toDegrees(azimuth),
                        toDegrees(elevation)
                        ));
            }
        }

        this.distance = distance;
        this.azimuth = azimuth;
        this.elevation = elevation;
    }

    /**
     * Converts the AoAVector to a spherical vector.
     * @return An equivalent spherical vector.
     */
    public SphericalVector toSphericalVector() {
        return SphericalVector.fromAoAVector(this);
    }

    /**
     * Creates an AoAVector from azimuth and elevation in radians.
     *
     * @param azimuth The azimuth in radians.
     * @param elevation The elevation in radians.
     * @param distance The distance in meters.
     * @return A new AoAVector.
     */
    @NonNull
    public static AoaVector fromRadians(float azimuth, float elevation, float distance) {
        return new AoaVector(azimuth, elevation, distance);
    }

    /**
     * Creates an AoAVector from azimuth and elevation in degrees.
     *
     * @param azimuth The azimuth in degrees.
     * @param elevation The elevation in degrees.
     * @param distance The distance in meters.
     * @return A new AoAVector.
     */
    @NonNull
    public static AoaVector fromDegrees(float azimuth, float elevation, float distance) {
        return new AoaVector(
                (float) toRadians(azimuth),
                (float) toRadians(elevation),
                distance);
    }

    /**
     * Produces an AoA vector from a cartesian vector, converting X, Y and Z values to
     * azimuth, elevation and distance.
     *
     * @param position The cartesian representation to convert.
     * @return An equivalent AoA vector representation.
     */
    @NonNull
    public static AoaVector fromCartesian(@NonNull Vector3 position) {
        Objects.requireNonNull(position);
        return fromCartesian(position.x, position.y, position.z);
    }

    /**
     * Produces a AoA vector from a cartesian vector, converting X, Y and Z values to
     * azimuth, elevation and distance.
     *
     * @param x The cartesian x-coordinate to convert.
     * @param y The cartesian y-coordinate to convert.
     * @param z The cartesian z-coordinate to convert.
     * @return An equivalent AoA vector representation.
     */
    @NonNull
    public static AoaVector fromCartesian(float x, float y, float z) {
        float d = (float) sqrt(x * x + y * y + z * z);
        if (d == 0) {
            return new AoaVector(0, 0, 0);
        }
        float azimuth = (float) asin(min(max(x / d, -1), 1));
        float elevation = (float) asin(min(max(y / d, -1), 1));
        if (z > 0) {
            // If z is "behind", mirror azimuth front/back.
            azimuth = F_PI * signum(azimuth) - azimuth;
        }
        return new AoaVector(azimuth, elevation, d);
    }

    /**
     * Converts a SphericalVector to an AoAVector.
     * @param vec The SphericalVector to convert.
     * @return An equivalent AoAVector.
     */
    public static AoaVector fromSphericalVector(SphericalVector vec) {
        float azimuth = vec.azimuth;
        boolean mirrored = abs(azimuth) > F_HALF_PI;
        if (mirrored) {
            azimuth = F_PI - azimuth;
        }
        double ca = cos(azimuth);
        double se = sin(vec.elevation);
        double ce = cos(vec.elevation);
        double az = acos(sqrt(max(ce * ce * ca * ca, 0) + se * se))
                * signum(vec.azimuth);
        if (mirrored) {
            return new AoaVector(F_PI - (float) az, vec.elevation, vec.distance);
        } else {
            return new AoaVector((float) az, vec.elevation, vec.distance);
        }
    }

    /**
     * Converts to a Vector3.
     * See {@link #AoaVector} for orientation information.
     *
     * @return A Vector3 whose coordinates are at the indicated location.
     */
    @NonNull
    public Vector3 toCartesian() {
        float x = distance * (float) sin(azimuth);
        float y = distance * (float) sin(elevation);
        float z = (float) sqrt(distance * distance - x * x - y * y);
        if (Float.isNaN(z)) {
            z = 0; // Impossible angle.  This is the closest we can get to it.
        }
        if (abs(azimuth) < F_HALF_PI) {
            z = -z;
        }
        return new Vector3(x, y, z);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String toString() {
        String format = "[⦡% 6.1f,⦨% 5.1f,⤠%5.2f]";
        return String.format(
                Locale.getDefault(),
                format,
                toDegrees(azimuth),
                toDegrees(elevation),
                distance
        );
    }
}
