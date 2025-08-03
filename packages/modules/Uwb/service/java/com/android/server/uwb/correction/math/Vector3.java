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

import static java.lang.Math.acos;
import static java.lang.Math.sqrt;

import androidx.annotation.NonNull;

import com.android.internal.annotations.Immutable;

import java.util.Locale;

/**
 * A Vector with 3 floats. This class is immutable.
 */
@Immutable
public class Vector3 {
    public static final Vector3 ORIGIN = new Vector3();

    public final float x;
    public final float y;
    public final float z;

    /**
     * Creates a vector at the origin. Because a Vector3 is immutable, it's better to use the
     * singleton of {@link #ORIGIN}
     */
    private Vector3() {
        x = 0;
        y = 0;
        z = 0;
    }

    /**
     * Creates a new Vector3.
     *
     * @param x The x value of the vector.
     * @param y The y value of the vector.
     * @param z The z value of the vector.
     */
    public Vector3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "[% 5.1f,% 5.1f,% 5.1f]", x, y, z);
    }

    /** Get a new Vector3 scaled to the unit length. */
    public Vector3 normalized() {
        Vector3 result;
        float normSquared = lengthSquared();

        if (normSquared <= 0.0f) {
            result = ORIGIN;
        } else {
            float norm = MathHelper.rsqrt(normSquared);
            result = new Vector3(x * norm, y * norm, z * norm);
        }
        return result;
    }

    /** Get a new Vector3 multiplied by a scalar amount. */
    @NonNull
    public Vector3 scaled(float a) {
        return new Vector3(x * a, y * a, z * a);
    }

    /** Adds this vector to another and returns the sum. */
    @NonNull
    public Vector3 add(@NonNull Vector3 rhs) {
        return new Vector3(x + rhs.x, y + rhs.y, z + rhs.z);
    }

    /**
     * Subtracts a vector from this vector.
     * @param rhs The subtrahend.
     * @return The difference between the two vectors.
     */
    @NonNull
    public Vector3 subtract(@NonNull Vector3 rhs) {
        return new Vector3(x - rhs.x, y - rhs.y, z - rhs.z);
    }

    /**
     * Multiplies each element of two vectors
     */
    @NonNull
    public Vector3 multiply(@NonNull Vector3 rhs) {
        return new Vector3(x * rhs.x, y * rhs.y, z * rhs.z);
    }

    /** Get dot product of two Vector3s. */
    public static float dot(@NonNull Vector3 lhs, @NonNull Vector3 rhs) {
        return lhs.x * rhs.x + lhs.y * rhs.y + lhs.z * rhs.z;
    }

    /** Get cross product of two Vector3s. */
    @NonNull
    public static Vector3 cross(@NonNull Vector3 lhs, @NonNull Vector3 rhs) {
        float lhsX = lhs.x;
        float lhsY = lhs.y;
        float lhsZ = lhs.z;
        float rhsX = rhs.x;
        float rhsY = rhs.y;
        float rhsZ = rhs.z;
        return new Vector3(
                lhsY * rhsZ - lhsZ * rhsY,
                lhsZ * rhsX - lhsX * rhsZ,
                lhsX * rhsY - lhsY * rhsX
        );
    }

    /**
     * Gets the square of the length of the vector. When performing length comparisons,
     * it is more optimal to compare against a squared length to avoid having to perform
     * a sqrt.
     * @return The square of the length of the vector.
     */
    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    /**
     * Gets the length of the vector. Consider {@link Vector3#lengthSquared} for performance.
     *
     * @return The length of the vector.
     */
    public float length() {
        return (float) sqrt(lengthSquared());
    }

    /**
     * Returns a clamped version of the current Vector.
     *
     * @param min the floor value, each individual component if below will be replaced by this.
     * @param max the ceiling value, each individual component if above will be replaced by this.
     * @return A Vector3 within the provided range.
     */
    public Vector3 clamp(@NonNull Vector3 min, @NonNull Vector3 max) {
        float clampedX = MathHelper.clamp(x, min.x, max.x);
        float clampedY = MathHelper.clamp(y, min.y, max.y);
        float clampedZ = MathHelper.clamp(z, min.z, max.z);

        return new Vector3(clampedX, clampedY, clampedZ);
    }

    /**
     * Get the shortest angle in radians between two vectors. The result is never greater than PI
     * radians (180 degrees).
     */
    public static float angleBetweenVectors(@NonNull Vector3 a, @NonNull Vector3 b) {
        float lengthA = a.length();
        float lengthB = b.length();
        float combinedLength = lengthA * lengthB;

        if (combinedLength < 1e-10f) {
            return 0.0f;
        }

        float dot = Vector3.dot(a, b);
        float cos = dot / combinedLength;

        // Clamp due to floating point precision that could cause dot to be > combinedLength.
        // Which would cause acos to return NaN.
        cos = MathHelper.clamp(cos, -1.0f, 1.0f);
        return (float) acos(cos);
    }

    /**
     * Linearly interpolates between two points. Interpolates between the points a and b by the
     * interpolant alpha. This is most commonly used to find a point some fraction of the way along
     * a line between two endpoints (e.g. to move an object gradually between those points). This
     * is an extension of {@link MathHelper#lerp(float, float, float)} for Vector3.
     *
     * @param start the beginning Vector3 (variable a in MathHelper#lerp).
     * @param end the ending Vector3 (variable b in MathHelper#lerp).
     * @param ratio ratio between the two Vector3 (variable t in MathHelper#lerp).
     * @return interpolated value between the two Vector3
     */
    @NonNull
    public static Vector3 lerp(@NonNull Vector3 start, @NonNull Vector3 end, float ratio) {
        return new Vector3(
                /*x=*/ MathHelper.lerp(start.x, end.x, ratio),
                /*y=*/ MathHelper.lerp(start.y, end.y, ratio),
                /*z=*/ MathHelper.lerp(start.z, end.z, ratio));
    }

    /**
     * Converts a vector expressed in radians (ie - yaw, pitch, roll), into degrees. Primarily
     * used as a convenience to display data to a user.
     * @return A Vector3 multiplied by 180/PI.
     */
    public Vector3 toDegrees() {
        return new Vector3(
                (float) Math.toDegrees(x),
                (float) Math.toDegrees(y),
                (float) Math.toDegrees(z)
        );
    }

    /**
     * Negates all values in the vector and returns the result.
     *
     * @return A negated vector.
     */
    @NonNull
    public Vector3 inverted() {
        return new Vector3(-x, -y, -z);
    }
}
