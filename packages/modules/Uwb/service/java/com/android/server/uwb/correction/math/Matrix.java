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

import java.util.Locale;

/**
 * 4x4 Matrix representing translation, scale, and rotation. Column major, right handed:
 *
 * <pre>
 *  [0, 4,  8, 12]
 *  [1, 5,  9, 13]
 *  [2, 6, 10, 14]
 *  [3, 7, 11, 15]
 * </pre>
 */
public class Matrix {
    private static final float[] IDENTITY_DATA =
            new float[] {
                    1.0f, 0.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 0.0f, 1.0f
            };

    public final float[] data = new float[16];

    private Matrix() {}

    /**
     * Creates a new instance of the Matrix class.
     * @param data A 16-element array of the matrix contents.
     */
    public Matrix(float[] data) {
        set(data);
    }

    private void set(float[] data) {
        if (data.length != 16) {
            throw new IllegalArgumentException("Cannot set Matrix, invalid data.");
        }

        System.arraycopy(data, 0, this.data, 0, 16);
    }

    /**
     * Gets the identity matrix.
     *
     * @return The identity matrix.
     */
    public static Matrix identity() {
        return new Matrix(IDENTITY_DATA);
    }

    /** Transform a Vector3 with this Matrix. */
    public Vector3 transformPoint(Vector3 vector) {
        float vx = vector.x;
        float vy = vector.y;
        float vz = vector.z;

        float x, y, z;
        x = data[0] * vx;
        x += data[4] * vy;
        x += data[8] * vz;
        x += data[12]; // *1

        y = data[1] * vx;
        y += data[5] * vy;
        y += data[9] * vz;
        y += data[13]; // *1

        z = data[2] * vx;
        z += data[6] * vy;
        z += data[10] * vz;
        z += data[14]; // *1
        return new Vector3(x, y, z);
    }

    /** Multiply two Matrices together and store the result in a third Matrix. */
    public static Matrix multiply(Matrix lhs, Matrix rhs) {
        float[] result = new float[16];

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += lhs.data[row * 4 + k] * rhs.data[k * 4 + col];
                }
                result[row * 4 + col] = sum;
            }
        }
        return new Matrix(result);
    }

    /** Returns a matrix that is an inversion of this one. */
    public Matrix invert() {
        float a00 = data[0], a01 = data[1], a02 = data[2], a03 = data[3];
        float a10 = data[4], a11 = data[5], a12 = data[6], a13 = data[7];
        float a20 = data[8], a21 = data[9], a22 = data[10], a23 = data[11];
        float a30 = data[12], a31 = data[13], a32 = data[14], a33 = data[15];

        float b00 = a00 * a11 - a01 * a10;
        float b01 = a00 * a12 - a02 * a10;
        float b02 = a00 * a13 - a03 * a10;
        float b03 = a01 * a12 - a02 * a11;
        float b04 = a01 * a13 - a03 * a11;
        float b05 = a02 * a13 - a03 * a12;
        float b06 = a20 * a31 - a21 * a30;
        float b07 = a20 * a32 - a22 * a30;
        float b08 = a20 * a33 - a23 * a30;
        float b09 = a21 * a32 - a22 * a31;
        float b10 = a21 * a33 - a23 * a31;
        float b11 = a22 * a33 - a23 * a32;

        float det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (det == 0) {
            return null;
        }

        float[] m = new float[16];

        float invDet = 1.0f / det;
        m[0] = (a11 * b11 - a12 * b10 + a13 * b09) * invDet;
        m[1] = (-a01 * b11 + a02 * b10 - a03 * b09) * invDet;
        m[2] = (a31 * b05 - a32 * b04 + a33 * b03) * invDet;
        m[3] = (-a21 * b05 + a22 * b04 - a23 * b03) * invDet;
        m[4] = (-a10 * b11 + a12 * b08 - a13 * b07) * invDet;
        m[5] = (a00 * b11 - a02 * b08 + a03 * b07) * invDet;
        m[6] = (-a30 * b05 + a32 * b02 - a33 * b01) * invDet;
        m[7] = (a20 * b05 - a22 * b02 + a23 * b01) * invDet;
        m[8] = (a10 * b10 - a11 * b08 + a13 * b06) * invDet;
        m[9] = (-a00 * b10 + a01 * b08 - a03 * b06) * invDet;
        m[10] = (a30 * b04 - a31 * b02 + a33 * b00) * invDet;
        m[11] = (-a20 * b04 + a21 * b02 - a23 * b00) * invDet;
        m[12] = (-a10 * b09 + a11 * b07 - a12 * b06) * invDet;
        m[13] = (a00 * b09 - a01 * b07 + a02 * b06) * invDet;
        m[14] = (-a30 * b03 + a31 * b01 - a32 * b00) * invDet;
        m[15] = (a20 * b03 - a21 * b01 + a22 * b00) * invDet;
        return new Matrix(m);
    }

    /** Set the translation component of this Matrix. */
    public void setTranslation(Vector3 translation) {
        data[12] = translation.x;
        data[13] = translation.y;
        data[14] = translation.z;
    }

    /** Make this a translation Matrix. */
    public void makeTranslation(Vector3 translation) {
        set(IDENTITY_DATA);
        setTranslation(translation);
    }

    /** Make this a rotation Matrix. */
    public void makeRotation(Quaternion q) {
        set(IDENTITY_DATA);

        float tx = 2 * q.x;
        float ty = 2 * q.y;
        float tz = 2 * q.z;

        float mdsqx = 1 - tx * q.x;
        float dqxy = ty * q.x;
        float dsqy = ty * q.y;
        float dqyz = tz * q.y;
        float dsqz = tz * q.z;
        float dqxz = tx * q.z;
        float dqxw = tx * q.w;
        float dqyw = ty * q.w;
        float dqzw = tz * q.w;

        data[0] = 1 - dsqy - dsqz;
        data[4] = dqxy - dqzw;
        data[8] = dqxz + dqyw;

        data[1] = dqxy + dqzw;
        data[5] = mdsqx - dsqz;
        data[9] = dqyz - dqxw;

        data[2] = dqxz - dqyw;
        data[6] = dqyz + dqxw;
        data[10] = mdsqx - dsqy;
    }

    /** Make this a rigid transform Matrix. */
    public void makeRigidTransform(Pose pose) {
        makeRotation(pose.rotation);
        setTranslation(pose.translation);
    }

    /** Make this a uniform scale Matrix. */
    public void makeScale(float scale) {
        set(IDENTITY_DATA);

        data[0] = scale;
        data[5] = scale;
        data[10] = scale;
    }

    /** Make this a scale Matrix. */
    public void makeScale(Vector3 scale) {
        set(IDENTITY_DATA);

        data[0] = scale.x;
        data[5] = scale.y;
        data[10] = scale.z;
    }

    /**
     * Make this a translation, rotation and scale Matrix. See {@link
     * #newTrs(Vector3,Quaternion,Vector3 )}.
     */
    public void makeTrs(Vector3 translation, Quaternion q, Vector3 scale) {
        float tx = 2 * q.x;
        float ty = 2 * q.y;
        float tz = 2 * q.z;

        float mdsqx = 1 - tx * q.x;
        float dqxy = ty * q.x;
        float dsqy = ty * q.y;
        float dqyz = tz * q.y;
        float dsqz = tz * q.z;
        float dqxz = tx * q.z;
        float dqxw = tx * q.w;
        float dqyw = ty * q.w;
        float dqzw = tz * q.w;

        data[0] = (1 - dsqy - dsqz) * scale.x;
        data[4] = (dqxy - dqzw) * scale.y;
        data[8] = (dqxz + dqyw) * scale.z;

        data[1] = (dqxy + dqzw) * scale.x;
        data[5] = (mdsqx - dsqz) * scale.y;
        data[9] = (dqyz - dqxw) * scale.z;

        data[2] = (dqxz - dqyw) * scale.x;
        data[6] = (dqyz + dqxw) * scale.y;
        data[10] = (mdsqx - dsqy) * scale.z;

        data[12] = translation.x;
        data[13] = translation.y;
        data[14] = translation.z;

        data[3] = 0.0f;
        data[7] = 0.0f;
        data[11] = 0.0f;
        data[15] = 1.0f;
    }

    /** Create a new translation Matrix. */
    public static Matrix newTranslation(Vector3 translation) {
        Matrix m = new Matrix();
        m.makeTranslation(translation);
        return m;
    }

    /** Create a new rotation Matrix. */
    public static Matrix newRotation(Quaternion rotation) {
        Matrix m = new Matrix();
        m.makeRotation(rotation);
        return m;
    }

    /** Create a new rigid transform Matrix. */
    public static Matrix newRigidTransform(Pose pose) {
        Matrix m = new Matrix();
        m.makeRigidTransform(pose);
        return m;
    }

    /** Create a new uniform scale Matrix. */
    public static Matrix newScale(float scale) {
        Matrix m = new Matrix();
        m.makeScale(scale);
        return m;
    }

    /** Create a new scale Matrix. */
    public static Matrix newScale(Vector3 scale) {
        Matrix m = new Matrix();
        m.makeScale(scale);
        return m;
    }

    /**
     * Creates a new translation, rotation and scale Matrix. The returned matrix is such that it
     * first scales objects, then rotates them, and finally translates them.
     *
     * <p>See
     * https://www.opengl-tutorial.org/beginners-tutorials/tutorial-3-matrices/#cumulating-transformations
     */
    public static Matrix newTrs(Vector3 translation, Quaternion rotation, Vector3 scale) {
        Matrix m = new Matrix();
        m.makeTrs(translation, rotation, scale);
        return m;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(),
            "% 5.1f, % 5.1f, % 5.1f, % 5.1f / "
                + "% 5.1f, % 5.1f, % 5.1f, % 5.1f / "
                + "% 5.1f, % 5.1f, % 5.1f, % 5.1f / "
                + "% 5.1f, % 5.1f, % 5.1f, % 5.1f",
            data[0], data[1], data[2], data[3],
            data[4], data[5], data[6], data[7],
            data[8], data[9], data[10], data[11],
            data[12], data[13], data[14], data[15]
            );
    }
}
