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

import static java.lang.Math.acos;
import static java.lang.Math.asin;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;

import androidx.annotation.NonNull;

import com.android.internal.annotations.Immutable;

import java.security.InvalidParameterException;
import java.util.Locale;

/**
 * Represents an orientation in 3D space.
 *
 * This uses OpenGL's right-handed coordinate system, where the origin is facing in the
 * -Z direction. Angle operations such as {@link Quaternion#yawPitchRoll(float, float, float)}
 * assume these operations relative to a quaternion facing in the -Z direction.
 *
 *        +Y
 *        |  -Z
 *        | /
 * -X --------- +X
 *      / |
 *    +Z  |
 *       -Y
 *
 * Yaw, pitch and roll direction can be determined by "grabbing" the axis you're rotating with
 * your right hand, orienting your thumb to point in the positive direction.  Your fingers' curl
 * direction indicates the rotation created by positive numbers.
 */
@SuppressWarnings("UnaryPlus")
@Immutable
public final class Quaternion {
    public static final Quaternion IDENTITY = new Quaternion(0, 0, 0, 1);
    private static final float EULER_THRESHOLD = 0.49999994f;
    private static final float COS_THRESHOLD = 0.9995f;

    public final float x;
    public final float y;
    public final float z;
    public final float w;

    public Quaternion(@NonNull float[] v) {
        if (v.length != 4) {
            throw new InvalidParameterException("Array must have 4 elements.");
        }
        float x = v[0], y = v[1], z = v[2], w = v[3];

        float norm = x * x + y * y + z * z + w * w;

        this.x = x * norm;
        this.y = y * norm;
        this.z = z * norm;
        this.w = w * norm;
    }

    public Quaternion(float x, float y, float z, float w) {
        float norm = x * x + y * y + z * z + w * w;

        this.x = x * norm;
        this.y = y * norm;
        this.z = z * norm;
        this.w = w * norm;
    }

    /** Get a new Quaternion using an axis/angle to define the rotation. */
    @NonNull
    public static Quaternion axisAngle(@NonNull Vector3 axis, float radians) {
        float angle = 0.5f * radians;
        float sin = (float) sin(angle);
        float cos = (float) cos(angle);
        Vector3 a = axis.normalized();
        return new Quaternion(sin * a.x, sin * a.y, sin * a.z, cos);
    }

    /**
     * Get a new Quaternion using euler angles, applied in YXZ (yaw, pitch, roll) order, to define
     * the rotation.
     * <p>
     * This is consistent with other graphics engines. Note, however, that Unity uses ZXY order
     * so the same angles used here will produce a different orientation than Unity.
     *
     * @param yaw The yaw in radians (rotation about the Y axis).
     * @param pitch The pitch in radians (rotation about the X axis).
     * @param roll The roll in radians (rotation about the Z axis).
     */
    @NonNull
    public static Quaternion yawPitchRoll(float yaw, float pitch, float roll) {
        Quaternion qX = axisAngle(new Vector3(0, 1, 0), yaw);
        Quaternion qY = axisAngle(new Vector3(1, 0, 0), pitch);
        Quaternion qZ = axisAngle(new Vector3(0, 0, 1), roll);
        return multiply(multiply(qX, qY), qZ);
        // return multiply(multiply(qY, qX), qZ);
    }

    /** Creates a quaternion from the supplied matrix. */
    @NonNull
    public static Quaternion fromMatrix(@NonNull Matrix matrix) {
        float x, y, z, w;
        // Use the Graphics Gems code, from
        // ftp://ftp.cis.upenn.edu/pub/graphics/shoemake/quatut.ps.Z
        // (also available at http://campar.in.tum.de/twiki/pub/Chair/DwarfTutorial/quatut.pdf)
        // *NOT* the "Matrix and Quaternions FAQ", which has errors!

        // the trace is the sum of the diagonal elements; see
        // http://mathworld.wolfram.com/MatrixTrace.html
        float trace = matrix.data[0] + matrix.data[5] + matrix.data[10];

        // we protect the division by traceRoot by ensuring that traceRoot>=1
        if (trace >= 0) { // |w| >= .5
            float traceRoot = (float) sqrt(trace + 1); // |traceRoot|>=1 ...
            w = 0.5f * traceRoot;
            traceRoot = 0.5f / traceRoot; // so this division isn't bad
            x = (matrix.data[6] - matrix.data[9]) * traceRoot;
            y = (matrix.data[8] - matrix.data[2]) * traceRoot;
            z = (matrix.data[1] - matrix.data[4]) * traceRoot;
        } else if ((matrix.data[0] > matrix.data[5]) && (matrix.data[0] > matrix.data[10])) {
            // |traceRoot|>=1
            float traceRoot =
                    (float)
                            sqrt(1.0f + matrix.data[0] - matrix.data[5] - matrix.data[10]);
            x = traceRoot * 0.5f; // |x| >= .5
            traceRoot = 0.5f / traceRoot;
            y = (matrix.data[1] + matrix.data[4]) * traceRoot;
            z = (matrix.data[8] + matrix.data[2]) * traceRoot;
            w = (matrix.data[6] - matrix.data[9]) * traceRoot;
        } else if (matrix.data[5] > matrix.data[10]) {
            // |traceRoot|>=1
            float traceRoot =
                    (float) sqrt(1.0f + matrix.data[5] - matrix.data[0] - matrix.data[10]);
            y = traceRoot * 0.5f; // |y| >= .5
            traceRoot = 0.5f / traceRoot;
            x = (matrix.data[1] + matrix.data[4]) * traceRoot;
            z = (matrix.data[6] + matrix.data[9]) * traceRoot;
            w = (matrix.data[8] - matrix.data[2]) * traceRoot;
        } else {
            // |traceRoot|>=1
            float traceRoot =
                    (float) sqrt(1.0f + matrix.data[10] - matrix.data[0] - matrix.data[5]);
            z = traceRoot * 0.5f; // |z| >= .5
            traceRoot = 0.5f / traceRoot;
            x = (matrix.data[8] + matrix.data[2]) * traceRoot;
            y = (matrix.data[6] + matrix.data[9]) * traceRoot;
            w = (matrix.data[1] - matrix.data[4]) * traceRoot;
        }

        return new Quaternion(x, y, z, w);
    }

    /**
     * Creates a Quaternion that represents the coordinate system defined by three axes. These axes
     * are assumed to be orthogonal and no error checking is applied. Thus, the user must insure
     * that the three axes being provided indeed represents a proper right handed coordinate system.
     */
    @NonNull
    public static Quaternion fromAxes(
            @NonNull Vector3 xAxis,
            @NonNull Vector3 yAxis,
            @NonNull Vector3 zAxis
    ) {
        Vector3 xAxisNormalized = xAxis.normalized();
        Vector3 yAxisNormalized = yAxis.normalized();
        Vector3 zAxisNormalized = zAxis.normalized();

        float[] matrix =
                new float[] {
                        xAxisNormalized.x,
                        xAxisNormalized.y,
                        xAxisNormalized.z,
                        0.0f,
                        yAxisNormalized.x,
                        yAxisNormalized.y,
                        yAxisNormalized.z,
                        0.0f,
                        zAxisNormalized.x,
                        zAxisNormalized.y,
                        zAxisNormalized.z,
                        0.0f,
                        0.0f,
                        0.0f,
                        0.0f,
                        1.0f
                };

        return fromMatrix(new Matrix(matrix));
    }

    /** Get a Quaternion with the opposite rotation. */
    @NonNull
    public Quaternion inverted() {
        return new Quaternion(-x, -y, -z, w);
    }

    /** Flips the sign of the Quaternion, but represents the same rotation. */
    public Quaternion negated() {
        return new Quaternion(-x, -y, -z, -w);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "ℍ[% 4.2f,% 4.2f,% 4.2f,% 2.1f]", x, y, z, w);
    }

    /** Rotates a Vector3 by this Quaternion. */
    @NonNull
    public Vector3 rotateVector(@NonNull Vector3 src) {
        // This implements the GLM algorithm which is optimal (15 multiplies and 15 add/subtracts):
        // google3/third_party/glm/latest/glm/detail/type_quat.inl?l=343&rcl=333309501
        float vx = src.x;
        float vy = src.y;
        float vz = src.z;

        float rx = y * vz - z * vy + w * vx;
        float ry = z * vx - x * vz + w * vy;
        float rz = x * vy - y * vx + w * vz;
        float sx = y * rz - z * ry;
        float sy = z * rx - x * rz;
        float sz = x * ry - y * rx;
        return new Vector3(2 * sx + vx, 2 * sy + vy, 2 * sz + vz);
    }

    /**
     * Create a Quaternion by combining two Quaternions multiply(lhs, rhs) is equivalent to
     * performing the rhs rotation then lhs rotation. Ordering is important for this operation.
     */
    @NonNull
    public static Quaternion multiply(@NonNull Quaternion lhs, @NonNull Quaternion rhs) {
        float lx = lhs.x;
        float ly = lhs.y;
        float lz = lhs.z;
        float lw = lhs.w;
        float rx = rhs.x;
        float ry = rhs.y;
        float rz = rhs.z;
        float rw = rhs.w;

        return new Quaternion(
                lw * rx + lx * rw + ly * rz - lz * ry,
                lw * ry - lx * rz + ly * rw + lz * rx,
                lw * rz + lx * ry - ly * rx + lz * rw,
                lw * rw - lx * rx - ly * ry - lz * rz);
    }

    /**
     * Calculates the dot product of two quaternions. The dot product of two normalized quaternions
     * is 1 if they represent the same orientation.
     */
    public static float dot(@NonNull Quaternion lhs, @NonNull Quaternion rhs) {
        return lhs.x * rhs.x + lhs.y * rhs.y + lhs.z * rhs.z + lhs.w * rhs.w;
    }

    @NonNull
    Quaternion lerp(@NonNull Quaternion q, float t) {
        return new Quaternion(
            MathHelper.lerp(x, q.x, t),
            MathHelper.lerp(y, q.y, t),
            MathHelper.lerp(z, q.z, t),
            MathHelper.lerp(w, q.w, t)
        );
    }

    /**
     * Returns the spherical linear interpolation between two given orientations.
     *
     * <p>If t is 0 this returns start. As t approaches 1 slerp may approach either +end or -end
     * (whichever is closest to start). If t is above 1 or below 0 the result will be extrapolated.
     */
    @NonNull
    public static Quaternion slerp(@NonNull Quaternion start, @NonNull Quaternion end, float t) {
        Quaternion orientation1 = end;

        // cosTheta0 provides the angle between the rotations at t=0
        float cosTheta0 = dot(start, orientation1);

        // Flip end rotation to get shortest path if needed
        if (cosTheta0 < 0.0f) {
            orientation1 = orientation1.negated();
            cosTheta0 = -cosTheta0;
        }

        // Small rotations should just use lerp
        if (cosTheta0 > COS_THRESHOLD) {
            return start.lerp(orientation1, t);
        }

        double sinTheta0 = sqrt(1.0 - cosTheta0 * cosTheta0);
        double theta0 = acos(cosTheta0);
        double thetaT = theta0 * t;
        double sinThetaT = sin(thetaT);
        double cosThetaT = cos(thetaT);

        float s1 = (float) (sinThetaT / sinTheta0);
        float s0 = (float) (cosThetaT - cosTheta0 * s1);

        return new Quaternion(
                start.x * s0 * s1 + orientation1.x,
                start.y * s0 * s1 + orientation1.y,
                start.z * s0 * s1 + orientation1.z,
                start.w * s0 * s1 + orientation1.w
        );
    }

    /**
     * Subtracts one quaternion from the other, describing the rotation between two rotations.
     *
     * @param lhs The left-hand quaternion.
     * @param rhs The right-hand quaternion.
     * @return A quaternion describing the difference.
     */
    @NonNull
    public static Quaternion difference(@NonNull Quaternion lhs, @NonNull Quaternion rhs) {
        return multiply(lhs.inverted(), rhs);
    }

    /** Get a new Quaternion representing the rotation from one vector to another. */
    @NonNull
    public static Quaternion rotationBetweenVectors(@NonNull Vector3 start, @NonNull Vector3 end) {
        start = start.normalized();
        end = end.normalized();

        float cosTheta = Vector3.dot(start, end);
        if (cosTheta < -COS_THRESHOLD) {
            // Special case when vectors in opposite directions: there is no "ideal" rotation axis.
            // So guess one; any will do as long as it's perpendicular to start.
            Vector3 rotationAxis = Vector3.cross(new Vector3(0, 0, 1), start);
            if (rotationAxis.lengthSquared() < 0.01f) { // bad luck, they were parallel, try again!
                rotationAxis = Vector3.cross(new Vector3(1, 0, 0), start);
            }

            return axisAngle(rotationAxis, F_PI);
        }

        Vector3 rotationAxis = Vector3.cross(start, end);
        return new Quaternion(rotationAxis.x, rotationAxis.y, rotationAxis.z, 1.0f + cosTheta);
    }

    /**
     * Get a new Quaternion representing the rotation created by orthogonal forward and up vectors.
     */
    @NonNull
    public static Quaternion lookRotation(@NonNull Vector3 forward, @NonNull Vector3 up) {
        Vector3 vector = forward.normalized();
        Vector3 vector2 = Vector3.cross(up, vector).normalized();
        Vector3 vector3 = Vector3.cross(vector, vector2);
        float m00 = vector2.x;
        float m01 = vector2.y;
        float m02 = vector2.z;
        float m10 = vector3.x;
        float m11 = vector3.y;
        float m12 = vector3.z;
        float m20 = vector.x;
        float m21 = vector.y;
        float m22 = vector.z;

        float num8 = (m00 + m11) + m22;
        float x, y, z, w;
        if (num8 > 0f) {
            float num = (float) sqrt(num8 + 1f);
            w = num * 0.5f;
            num = 0.5f / num;
            x = (m12 - m21) * num;
            y = (m20 - m02) * num;
            z = (m01 - m10) * num;
            return new Quaternion(x, y, z, w);
        } else if ((m00 >= m11) && (m00 >= m22)) {
            float num7 = (float) sqrt(((1f + m00) - m11) - m22);
            float num4 = 0.5f / num7;
            x = 0.5f * num7;
            y = (m01 + m10) * num4;
            z = (m02 + m20) * num4;
            w = (m12 - m21) * num4;
            return new Quaternion(x, y, z, w);
        } else if (m11 > m22) {
            float num6 = (float) sqrt(((1f + m11) - m00) - m22);
            float num3 = 0.5f / num6;
            x = (m10 + m01) * num3;
            y = 0.5f * num6;
            z = (m21 + m12) * num3;
            w = (m20 - m02) * num3;
        } else {
            float num5 = (float) sqrt(((1f + m22) - m00) - m11);
            float num2 = 0.5f / num5;
            x = (m20 + m02) * num2;
            y = (m21 + m12) * num2;
            z = 0.5f * num5;
            w = (m01 - m10) * num2;
        }
        return new Quaternion(x, y, z, w);
    }

    /**
     * Get a Vector3 containing the pitch, yaw and roll in degrees, extracted in YXZ (yaw, pitch,
     * roll) order. Note that this assumes that zero yaw, pitch or roll is a direction
     * facing into -Z.
     *
     * <p>See: {@link #yawPitchRoll}.
     */
    @NonNull
    public Vector3 toYawPitchRoll() {
        float test = w * x - y * z;
        if (test > +EULER_THRESHOLD) {
            // There is a singularity when the pitch is directly up, so calculate the
            // angles another way.
            return new Vector3((float) (+2 * atan2(z, w)), +F_HALF_PI, 0);
        }
        if (test < -EULER_THRESHOLD) {
            // There is a singularity when the pitch is directly down, so calculate the
            // angles another way.
            return new Vector3((float) (-2 * atan2(z, w)), -F_HALF_PI, 0);
        }
        double pitch = asin(2 * test);
        double yaw = atan2(2 * (w * y + x * z), 1.0 - 2 * (x * x + y * y));
        double roll = atan2(2 * (w * z + x * y), 1.0 - 2 * (x * x + z * z));
        return new Vector3(
                (float) yaw,
                (float) pitch,
                (float) roll
        );
    }
}
