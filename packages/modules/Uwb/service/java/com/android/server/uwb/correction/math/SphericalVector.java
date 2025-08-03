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
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.signum;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

import androidx.annotation.NonNull;

import com.android.internal.annotations.Immutable;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a point in space represented as distance, azimuth and elevation.
 * This uses OpenGL's right-handed coordinate system, where the origin is facing in the
 * -Z direction. Increasing azimuth rotates around Y and increases X.  Increasing
 * elevation rotates around X and increases Y.
 * This class is invariant.
 */
@Immutable
public class SphericalVector {
    // If true, negative distances will be converted to a positive distance facing the opposite
    //  direction.
    private static final boolean NORMALIZE_NEGATIVE_DISTANCE = false;
    public final float distance;
    public final float azimuth;
    public final float elevation;

    /**
     * Creates a SphericalVector from the azimuth, elevation and distance of a viewpoint that is
     * facing into the -Z axis.
     *
     * @param azimuth   The angle along the X axis, around the Y axis.
     * @param elevation The angle along the Y axis, around the X axis.
     * @param distance  The distance to the origin.
     */
    private SphericalVector(float azimuth, float elevation, float distance) {
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

        this.distance = distance;
        this.azimuth = azimuth;
        this.elevation = elevation;
    }

    /**
     * Creates an SphericalVector from azimuth and elevation in radians.
     *
     * @param azimuth   The azimuth in degrees.
     * @param elevation The elevation in degrees.
     * @param distance  The distance in meters.
     * @return A new SphericalVector.
     */
    @NonNull
    public static SphericalVector fromRadians(float azimuth, float elevation, float distance) {
        return new SphericalVector(azimuth, elevation, distance);
    }

    /**
     * Creates an SphericalVector from azimuth and elevation in radians.
     *
     * @param azimuth   The azimuth in radians.
     * @param elevation The elevation in radians.
     * @param distance  The distance in meters.
     * @return A new SphericalVector.
     */
    @NonNull
    public static SphericalVector fromDegrees(float azimuth, float elevation, float distance) {
        return new SphericalVector(
                (float) toRadians(azimuth),
                (float) toRadians(elevation),
                distance);
    }

    /**
     * Produces a SphericalVector from a cartesian vector, converting X, Y and Z values to
     * azimuth, elevation and distance.
     *
     * @param position The cartesian representation to convert.
     * @return An equivalent spherical vector representation.
     */
    @NonNull
    public static SphericalVector fromCartesian(@NonNull Vector3 position) {
        Objects.requireNonNull(position);
        return fromCartesian(position.x, position.y, position.z);
    }

    /**
     * Produces a spherical vector from a cartesian vector, converting X, Y and Z values to
     * azimuth, elevation and distance.
     *
     * @param x The cartesian x-coordinate to convert.
     * @param y The cartesian y-coordinate to convert.
     * @param z The cartesian z-coordinate to convert.
     * @return An equivalent spherical vector representation.
     */
    @NonNull
    public static SphericalVector fromCartesian(float x, float y, float z) {
        float d = (float) sqrt(x * x + y * y + z * z);
        if (d == 0) {
            return new SphericalVector(0, 0, 0);
        }
        float azimuth = (float) atan2(x, -z);
        float elevation = (float) asin(min(max(y / d, -1), 1));
        return new SphericalVector(azimuth, elevation, d);
    }

    /**
     * Converts an AoAVector to a SphericalVector.
     *
     * @param vec The AoAVector to convert.
     * @return An equivalent SphericalVector.
     */
    public static SphericalVector fromAoAVector(AoaVector vec) {
        float azimuth = vec.azimuth;
        boolean mirrored = abs(azimuth) > F_HALF_PI;
        if (mirrored) {
            azimuth = F_PI - azimuth;
        }
        double ca = cos(azimuth);
        double se = sin(vec.elevation);
        double azz = sqrt(max(ca * ca - se * se, 0)) / cos(vec.elevation);
        double az = acos(min(max(azz, -1), 1)) * signum(vec.azimuth);
        if (mirrored) {
            return new SphericalVector(F_PI - (float) az, vec.elevation, vec.distance);
        } else {
            return new SphericalVector((float) az, vec.elevation, vec.distance);
        }
    }

    /**
     * Converts the SphericalVector to an AoA vector.
     *
     * @return An equivalent AoA vector.
     */
    public AoaVector toAoAVector() {
        return AoaVector.fromSphericalVector(this);
    }

    /**
     * Converts to a Vector3.
     * See {@link #SphericalVector} for orientation information.
     *
     * @return A Vector3 whose coordinates are at the indicated location.
     */
    @NonNull
    public Vector3 toCartesian() {
        float sa = (float) sin(azimuth);
        float x = distance * (float) cos(elevation) * sa;
        float y = distance * (float) sin(elevation);
        float z = distance * (float) abs(cos(elevation) * cos(azimuth));
        if (abs(azimuth) <= F_HALF_PI) {
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

    /**
     * Converts this SphericalVector to an equivalent annotated Spherical Vector that has all 3
     * components and null annotations.
     *
     * @return An equivalent {@link Annotated}.
     */
    public Annotated toAnnotated() {
        return new Annotated(this, true, true, true);
    }

    /**
     * Converts this SphericalVector to an equivalent annotated Spherical Vector, with the specified
     * presence or absence of values.
     *
     * @param hasAzimuth   True if the vector includes azimuth.
     * @param hasElevation True if the vector includes elevation.
     * @param hasDistance  True if the vector includes distance.
     * @return An equivalent {@link Annotated}.
     */
    public Annotated toAnnotated(boolean hasAzimuth, boolean hasElevation, boolean hasDistance) {
        return new Annotated(
                this,
                hasAzimuth,
                hasElevation,
                hasDistance
        );
    }

    /**
     * Represents a {@link SphericalVector} with annotations about the presence and quality of
     * each of its values. While SphericalVector is invariant, the annotations on this class are not
     * invariant.
     */
    public static class Annotated extends SphericalVector {
        public final boolean hasAzimuth;
        public final boolean hasElevation;
        public final boolean hasDistance;
        public double azimuthFom = 1;
        public double elevationFom = 1;
        public double distanceFom = 1;

        /**
         * Creates a new instance of the {@link SphericalVector.Annotated}
         *
         * @param vector The source SphericalVector.
         */
        public Annotated(
                @NonNull SphericalVector vector
        ) {
            super(vector.azimuth, vector.elevation, vector.distance);

            this.hasAzimuth = true;
            this.hasElevation = true;
            this.hasDistance = true;
        }

        /**
         * Creates a new instance of the {@link SphericalVector.Annotated}
         *
         * @param vector       The source SphericalVector.
         * @param hasAzimuth   True if the vector includes azimuth.
         * @param hasElevation True if the vector includes elevation.
         * @param hasDistance  True if the vector includes distance.
         */
        public Annotated(
                @NonNull SphericalVector vector,
                boolean hasAzimuth,
                boolean hasElevation,
                boolean hasDistance
        ) {
            super(vector.azimuth, vector.elevation, vector.distance);
            this.hasAzimuth = hasAzimuth;
            this.hasElevation = hasElevation;
            this.hasDistance = hasDistance;
        }

        /**
         * Determines if a sparse vector has all components.
         *
         * @return true if azimuth, elevation and distance are present.
         */
        public boolean isComplete() {
            return hasAzimuth && hasElevation && hasDistance;
        }

        /**
         * Copies the annotations from another {@link Annotated}. This updates the current class.
         *
         * @param basis The {@link Annotated} from which to copy the annotations.
         * @return This object.
         */
        @NonNull
        public Annotated copyFomFrom(Annotated basis) {
            azimuthFom = basis.azimuthFom;
            elevationFom = basis.elevationFom;
            distanceFom = basis.distanceFom;
            return this;
        }

        /**
         * Returns a string representation of the object. In general, the
         * {@code toString} method returns a string that
         * "textually represents" this object. The result should
         * be a concise but informative representation that is easy for a
         * person to read.
         * It is recommended that all subclasses override this method.
         * <p>
         * The {@code toString} method for class {@code Object}
         * returns a string consisting of the name of the class of which the
         * object is an instance, the at-sign character `{@code @}', and
         * the unsigned hexadecimal representation of the hash code of the
         * object. In other words, this method returns a string equal to the
         * value of:
         * <blockquote>
         * <pre>
         * getClass().getName() + '@' + Integer.toHexString(hashCode())
         * </pre></blockquote>
         *
         * @return a string representation of the object.
         */
        @NonNull
        @Override
        public String toString() {
            String az = "   x  ", el = "  x  ", dist = "  x  ";
            Locale dl = Locale.getDefault();
            if (hasAzimuth) {
                az = String.format(dl, "% 6.1f %d%%", toDegrees(azimuth),
                        (int) (azimuthFom * 100));
            }
            if (hasElevation) {
                el = String.format(dl, "% 5.1f %d%%", toDegrees(elevation),
                        (int) (elevationFom * 100));
            }
            if (hasDistance) {
                dist = String.format(dl, "%5.2f %d%%", distance,
                        (int) (distanceFom * 100));

            }
            return String.format(dl, "[⦡%s,⦨%s,⤠%s]", az, el, dist);
        }
    }
}
