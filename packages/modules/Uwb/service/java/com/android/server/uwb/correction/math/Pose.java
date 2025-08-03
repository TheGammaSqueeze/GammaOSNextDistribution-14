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

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Represents a rigid transformation from one coordinate space to another.
 *
 * <p>A Pose describes the transformation from an object's local coordinate space to some coordinate
 * system, typically the world coordinate space.
 *
 * <p>The transformation is defined using a quaternion rotation about the origin followed by a
 * translation.
 */
public class Pose {
    public static final Pose IDENTITY = new Pose(Vector3.ORIGIN, Quaternion.IDENTITY);

    public final Vector3 translation;
    public final Quaternion rotation;

    /** Creates a Pose given a translation and a rotation. */
    public Pose(Vector3 translation, Quaternion rotation) {
        this.translation = translation;
        this.rotation = rotation;
    }

    /**
     * Creates a Transformation given a translation and a rotation.
     *
     * @param translation a {@code float[3]} representing the translation vector
     * @param rotation a {@code float[4]} representing the rotation quaternion following the
     *                 Hamilton convention.
     * @throws IllegalArgumentException if translation and rotation lengths are wrong.
     */
    public Pose(float[] translation, float[] rotation) {
        Objects.requireNonNull(translation);
        Objects.requireNonNull(rotation);
        if (translation.length != 3) {
            throw new IllegalArgumentException(
                    "Translation array size must be 3. Found " + translation.length + ".");
        }
        if (rotation.length != 4) {
            throw new IllegalArgumentException(
                    "Rotation array size must be 4. Found " + rotation.length + ".");
        }
        this.translation = new Vector3(translation[0], translation[1], translation[2]);
        this.rotation = new Quaternion(rotation[0], rotation[1], rotation[2], rotation[3]);
    }

    /**
     * Returns the result of composing {@code lhs} with {@code rhs}. That is, transforming a point
     * by the resulting pose will be equivalent to transforming that point first by {@code rhs}, and
     * then transforming the result by {@code lhs}. Ordering is important for this operation.
     */
    public static Pose compose(Pose lhs, Pose rhs) {
        Vector3 composedTranslation = lhs.rotation
                .rotateVector(rhs.translation)
                .add(lhs.translation);
        return new Pose(composedTranslation, Quaternion.multiply(lhs.rotation, rhs.rotation));
    }

    /**
     * Creates a Pose given a transformation matrix.
     *
     * @param matrix a {@code Matrix} instance representing the transformation.
     */
    public static Pose fromMatrix(Matrix matrix) {
        Vector3 translation = new Vector3(matrix.data[12], matrix.data[13], matrix.data[14]);
        Quaternion rotation = Quaternion.fromMatrix(matrix);
        return new Pose(translation, rotation);
    }

    /**
     * Returns a pose that performs the opposite transformation.
     *
     * <p>{@code pose.compose(pose.inverted())} will, allowing for floating point precision errors,
     * produce an identity pose.
     */
    public Pose inverted() {
        Quaternion outRotation = rotation.inverted();
        Vector3 outTranslation = outRotation.rotateVector(translation).inverted();
        return new Pose(outTranslation, outRotation);
    }

    /**
     * Transforms the provided point by the pose. This converts a point relative to the pose into
     * a world-relative point.  To convert from a world-relative point to a pose-relative point,
     * use pose.inverted().transformPoint(point)
     */
    public Vector3 transformPoint(Vector3 point) {
        return rotation.rotateVector(point).add(translation);
    }

    @NonNull
    @Override
    public String toString() {
        return "Pose T=" + translation + " R=" + rotation;
    }
}
