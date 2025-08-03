/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.graphics.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.opengl.Matrix;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MatrixTest {

    @Test
    public void testMultiplyMM() {
        // Assert legal arguments
        float[] mat = new float[16];
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM(null, 0,  mat, 0,  mat, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( mat, 0, null, 0,  mat, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( mat, 0,  mat, 0, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( mat, 1,  mat, 0,  mat, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( mat, 0,  mat, 1,  mat, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( mat, 0,  mat, 0,  mat, 1));

        float[] matResult = new float[16];
        float[] matLhs = new float[16];
        float[] matRhs = new float[16];

        // Test that identity = identity * identity
        Matrix.setIdentityM(matLhs, 0);
        Matrix.setIdentityM(matRhs, 0);
        Matrix.multiplyMM(matResult, 0, matLhs, 0, matRhs, 0);
        verifyMatrix(matResult, new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f});

        // Test that checks mult of two diagonal matrices
        matLhs = new float[] {
            2.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 3.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 5.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 7.0f,
        };
        matRhs = new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 2.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 3.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 5.0f,
        };
        Matrix.multiplyMM(matResult, 0, matLhs, 0, matRhs, 0);
        verifyMatrix(matResult, new float[] {
            2.0f, 0.0f,  0.0f,  0.0f,
            0.0f, 6.0f,  0.0f,  0.0f,
            0.0f, 0.0f, 15.0f,  0.0f,
            0.0f, 0.0f,  0.0f, 35.0f});

        // Tests that checks mult of two triangular matrices
        matLhs = new float[] {
            1.0f, 2.0f, 3.0f, 4.0f,
            0.0f, 5.0f, 6.0f, 7.0f,
            0.0f, 0.0f, 8.0f, 9.0f,
            0.0f, 0.0f, 0.0f, 10.0f,
        };
        matRhs = new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            2.0f, 5.0f, 0.0f, 0.0f,
            3.0f, 6.0f, 8.0f, 0.0f,
            4.0f, 7.0f, 9.0f, 10.0f,
        };
        Matrix.multiplyMM(matResult, 0, matLhs, 0, matRhs, 0);
        verifyMatrix(matResult, new float[] {
            1.0f,  2.0f,   3.0f,   4.0f,
            2.0f, 29.0f,  36.0f,  43.0f,
            3.0f, 36.0f, 109.0f, 126.0f,
            4.0f, 43.0f, 126.0f, 246.0f});

        matLhs = new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            2.0f, 5.0f, 0.0f, 0.0f,
            3.0f, 6.0f, 8.0f, 0.0f,
            4.0f, 7.0f, 9.0f, 10.0f,
        };
        matRhs = new float[] {
            1.0f, 2.0f, 3.0f, 4.0f,
            0.0f, 5.0f, 6.0f, 7.0f,
            0.0f, 0.0f, 8.0f, 9.0f,
            0.0f, 0.0f, 0.0f, 10.0f,
        };
        Matrix.multiplyMM(matResult, 0, matLhs, 0, matRhs, 0);
        verifyMatrix(matResult, new float[] {
            30.0f,  56.0f,  60.0f,  40.0f,
            56.0f, 110.0f, 111.0f,  70.0f,
            60.0f, 111.0f, 145.0f,  90.0f,
            40.0f,  70.0f,  90.0f, 100.0f});

        // Test that checks mult of two filled matrices
        matLhs = new float[] {
            1.0f,  7.0f, 19.0f, 37.0f,
            2.0f, 11.0f, 23.0f, 41.0f,
            3.0f, 13.0f, 29.0f, 43.0f,
            5.0f, 17.0f, 31.0f, 47.0f,
        };
        matRhs = new float[] {
            1.0f, 5.0f,  9.0f, 13.0f,
            2.0f, 6.0f, 10.0f, 14.0f,
            3.0f, 7.0f, 11.0f, 15.0f,
            4.0f, 8.0f, 12.0f, 16.0f,
        };
        Matrix.multiplyMM(matResult, 0, matLhs, 0, matRhs, 0);
        verifyMatrix(matResult, new float[] {
            103.0f, 400.0f,  798.0f, 1240.0f,
            114.0f, 448.0f,  900.0f, 1408.0f,
            125.0f, 496.0f, 1002.0f, 1576.0f,
            136.0f, 544.0f, 1104.0f, 1744.0f});
    }

    @Test
    public void testMultiplyMMInPlace() {
        float[] matLhs = new float[16];
        float[] matRhs = new float[16];

        // Multiply RHS in place
        matLhs = new float[] {
            1.0f,  7.0f, 19.0f, 37.0f,
            2.0f, 11.0f, 23.0f, 41.0f,
            3.0f, 13.0f, 29.0f, 43.0f,
            5.0f, 17.0f, 31.0f, 47.0f,
        };
        matRhs = new float[] {
            1.0f, 5.0f,  9.0f, 13.0f,
            2.0f, 6.0f, 10.0f, 14.0f,
            3.0f, 7.0f, 11.0f, 15.0f,
            4.0f, 8.0f, 12.0f, 16.0f,
        };
        Matrix.multiplyMM(matRhs, 0, matLhs, 0, matRhs, 0);
        verifyMatrix(matRhs, new float[] {
            103.0f, 400.0f,  798.0f, 1240.0f,
            114.0f, 448.0f,  900.0f, 1408.0f,
            125.0f, 496.0f, 1002.0f, 1576.0f,
            136.0f, 544.0f, 1104.0f, 1744.0f});

        // Multiply LHS in place
        matLhs = new float[] {
            1.0f,  7.0f, 19.0f, 37.0f,
            2.0f, 11.0f, 23.0f, 41.0f,
            3.0f, 13.0f, 29.0f, 43.0f,
            5.0f, 17.0f, 31.0f, 47.0f,
        };
        matRhs = new float[] {
            1.0f, 5.0f,  9.0f, 13.0f,
            2.0f, 6.0f, 10.0f, 14.0f,
            3.0f, 7.0f, 11.0f, 15.0f,
            4.0f, 8.0f, 12.0f, 16.0f,
        };
        Matrix.multiplyMM(matLhs, 0, matLhs, 0, matRhs, 0);
        verifyMatrix(matLhs, new float[] {
            103.0f, 400.0f,  798.0f, 1240.0f,
            114.0f, 448.0f,  900.0f, 1408.0f,
            125.0f, 496.0f, 1002.0f, 1576.0f,
            136.0f, 544.0f, 1104.0f, 1744.0f});

        // Multiply both in place
        float[] mat = new float[] {
            1.0f,  7.0f, 19.0f, 37.0f,
            2.0f, 11.0f, 23.0f, 41.0f,
            3.0f, 13.0f, 29.0f, 43.0f,
            5.0f, 17.0f, 31.0f, 47.0f,
        };
        Matrix.multiplyMM(mat, 0, mat, 0, mat, 0);
        verifyMatrix(mat, new float[] {
            257.0f,  960.0f, 1878.0f, 2880.0f,
            298.0f, 1131.0f, 2229.0f, 3441.0f,
            331.0f, 1272.0f, 2530.0f, 3912.0f,
            367.0f, 1424.0f, 2842.0f, 4424.0f});
    }

    @Test
    public void testMultiplyMV() {
        // Assert legal arguments
        float[] mat = new float[16];
        float[] vec = new float[4];
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMV(null, 0,  mat, 0,  vec, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( vec, 0, null, 0,  vec, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( vec, 0,  mat, 0, null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( vec, 1,  mat, 0,  vec, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( vec, 0,  mat, 1,  vec, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Matrix.multiplyMM( vec, 0,  mat, 0,  vec, 1));

        float[] vecResult = new float[4];
        float[] matLhs = new float[16];
        float[] vecRhs = new float[4];

        // Test that vector = identity * vector
        Matrix.setIdentityM(matLhs, 0);
        vecRhs = new float[] {1.0f, 2.0f, 3.0f, 4.0f};
        Matrix.multiplyMV(vecResult, 0, matLhs, 0, vecRhs, 0);
        verifyVector(vecResult, new float[] {1.0f, 2.0f, 3.0f, 4.0f});

        // Test that checks mult of a diagonal matrix with an arbitrary vector
        matLhs = new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 2.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 3.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 5.0f,
        };
        vecRhs = new float[] {2.0f, 3.0f, 5.0f, 7.0f};
        Matrix.multiplyMV(vecResult, 0, matLhs, 0, vecRhs, 0);
        verifyVector(vecResult, new float[] {2.0f, 6.0f, 15.0f, 35.0f});

        // Tests that checks mult of a triangular matrix with an arbitrary vector
        matLhs = new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            2.0f, 5.0f, 0.0f, 0.0f,
            3.0f, 6.0f, 8.0f, 0.0f,
            4.0f, 7.0f, 9.0f, 10.0f,
        };
        vecRhs = new float[] {1.0f, 2.0f, 3.0f, 5.0f};
        Matrix.multiplyMV(vecResult, 0, matLhs, 0, vecRhs, 0);
        verifyVector(vecResult, new float[] {34.0f, 63.0f, 69.0f, 50.0f});

        matLhs = new float[] {
            1.0f, 2.0f, 3.0f, 4.0f,
            0.0f, 5.0f, 6.0f, 7.0f,
            0.0f, 0.0f, 8.0f, 9.0f,
            0.0f, 0.0f, 0.0f, 10.0f,
        };
        vecRhs = new float[] {1.0f, 2.0f, 3.0f, 5.0f};
        Matrix.multiplyMV(vecResult, 0, matLhs, 0, vecRhs, 0);
        verifyVector(vecResult, new float[] {1.0f, 12.0f, 39.0f, 95.0f});

        // Arbitrary filled matrix times arbitrary vector
        matLhs = new float[] {
            1.0f,  7.0f, 19.0f, 37.0f,
            2.0f, 11.0f, 23.0f, 41.0f,
            3.0f, 13.0f, 29.0f, 43.0f,
            5.0f, 17.0f, 31.0f, 47.0f,
        };
        vecRhs = new float[] {2.0f, 3.0f, 5.0f, 7.0f};
        Matrix.multiplyMV(vecResult, 0, matLhs, 0, vecRhs, 0);
        verifyVector(vecResult, new float[] {58.0f, 231.0f, 469.0f, 741.0f});
    }

    @Test
    public void testMultiplyMVInPlace() {
        float[] matLhs = new float[] {
            1.0f,  7.0f, 19.0f, 37.0f,
            2.0f, 11.0f, 23.0f, 41.0f,
            3.0f, 13.0f, 29.0f, 43.0f,
            5.0f, 17.0f, 31.0f, 47.0f,
        };
        float[] vecRhs = new float[] {2.0f, 3.0f, 5.0f, 7.0f};
        Matrix.multiplyMV(vecRhs, 0, matLhs, 0, vecRhs, 0);
        verifyVector(vecRhs, new float[] {58.0f, 231.0f, 469.0f, 741.0f});
    }

    @Test
    public void testTransposeM() {
        /*  matrices are stored in column-major order.
         *
         *  | 1 0 0  0 |
         *  | 2 5 0  0 |
         *  | 3 6 8  0 |
         *  | 4 7 9 10 |
         *
         *  is initialized as
         */
        float[] mat = new float[] {
            1.0f, 2.0f, 3.0f, 4.0f,
            0.0f, 5.0f, 6.0f, 7.0f,
            0.0f, 0.0f, 8.0f, 9.0f,
            0.0f, 0.0f, 0.0f, 10.0f,
        };

        /*  the matrix once transposed should be:
         *  | 1 2 3  4 |
         *  | 0 5 6  7 |
         *  | 0 0 8  9 |
         *  | 0 0 0 10 |
         *
         *  and represented as
         */
        float[] matTranspose = new float[] {
            1.0f, 0.0f, 0.0f,  0.0f,
            2.0f, 5.0f, 0.0f,  0.0f,
            3.0f, 6.0f, 8.0f,  0.0f,
            4.0f, 7.0f, 9.0f, 10.0f,
        };

        float[] matResult = new float[16];
        Matrix.transposeM(matResult, 0, mat, 0);
        verifyMatrix(matResult, matTranspose);
    }

    @Test
    public void testInvertM() {
        // Inverse of identity is identity
        float[] matIden = new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f};

        float[] mat = matIden;
        float[] matResult = new float[16];

        Matrix.invertM(matResult, 0, mat, 0);
        verifyMatrix(matResult, matIden);

        // Inverse of arbitrary nonsingular matrix
        mat = new float[] {
             0.814f,  4.976f, -3.858f,  7.206f,
             5.112f, -2.420f,  8.791f,  6.426f,
             2.945f,  1.801f, -2.594f,  2.663f,
            -5.003f, -4.188f,  3.340f, -1.235f
        };
        float[] matInv = new float[] {
            -0.112707f, 0.033867f,  0.189963f, -0.071792f,
             0.158030f, 0.011872f, -0.603463f, -0.317385f,
             0.056107f, 0.076268f, -0.406444f, -0.152194f,
             0.072418f, 0.028810f,  0.177650f,  0.145793f,
        };
        Matrix.invertM(matResult, 0, mat, 0);
        verifyMatrix(matResult, matInv, 0.001f);
    }

    @Test
    public void testLength() {
        float zeroLength = Matrix.length(0.0f, 0.0f, 0.0f);
        assertEquals(zeroLength, 0.0f, 0.001f);

        float unitLength = Matrix.length(1.0f, 0.0f, 0.0f);
        assertEquals(unitLength, 1.0f, 0.001f);
        unitLength = Matrix.length(0.0f, 1.0f, 0.0f);
        assertEquals(unitLength, 1.0f, 0.001f);
        unitLength = Matrix.length(0.0f, 0.0f, 1.0f);
        assertEquals(unitLength, 1.0f, 0.001f);

        unitLength = Matrix.length(0.707107f, 0.707107f, 0.0f);
        assertEquals(unitLength, 1.0f, 0.001f);
        unitLength = Matrix.length(0.0f, 0.707107f, 0.707107f);
        assertEquals(unitLength, 1.0f, 0.001f);
        unitLength = Matrix.length(0.707107f, 0.0f, 0.707107f);
        assertEquals(unitLength, 1.0f, 0.001f);

        unitLength = Matrix.length(0.577350f, 0.577350f, 0.577350f);
        assertEquals(unitLength, 1.0f, 0.001f);

        float length = Matrix.length(1.0f, 1.0f, 1.0f);
        assertEquals(length, 1.732051f, 0.001f);

        length = Matrix.length(2.0f, 3.0f, 4.0f);
        assertEquals(length, 5.385165f, 0.001f);
    }

    @Test
    public void testSetIdentityM() {
        float[] mat = new float[16];
        Matrix.setIdentityM(mat, 0);
        verifyMatrix(mat, new float[] {
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f});
    }

    @Test
    public void testRotateM() {
        float[] mat = new float[16];
        float[] matResult = new float[16];

        // Rotate around X
        Matrix.setIdentityM(mat, 0);
        Matrix.setIdentityM(matResult, 0);
        Matrix.rotateM(matResult, 0, mat, 0, 45.0f, 1.0f, 0.0f, 0.0f);
        float[] matRotate = new float[] {
            1.0f,  0.0f,    0.0f,    0.0f,
            0.0f,  0.7071f, 0.7071f, 0.0f,
            0.0f, -0.7071f, 0.7071f, 0.0f,
            0.0f,  0.0f,    0.0f,    1.0f
        };
        verifyMatrix(matResult, matRotate, 0.001f);

        // Rotate around Y
        Matrix.setIdentityM(mat, 0);
        Matrix.setIdentityM(matResult, 0);
        Matrix.rotateM(matResult, 0, mat, 0, 45.0f, 0.0f, 1.0f, 0.0f);
        matRotate = new float[] {
            0.7071f, 0.0f, -0.7071f, 0.0f,
            0.0f,    1.0f,  0.0f,    0.0f,
            0.7071f, 0.0f,  0.7071f, 0.0f,
            0.0f,    0.0f,  0.0f,    1.0f
        };
        verifyMatrix(matResult, matRotate, 0.001f);

        // Rotate around Z
        Matrix.setIdentityM(mat, 0);
        Matrix.setIdentityM(matResult, 0);
        Matrix.rotateM(matResult, 0, mat, 0, 45.0f, 0.0f, 0.0f, 1.0f);
        matRotate = new float[] {
             0.7071f, 0.7071f, 0.0f, 0.0f,
            -0.7071f, 0.7071f, 0.0f, 0.0f,
             0.0f,    0.0f,    1.0f, 0.0f,
             0.0f,    0.0f,    0.0f, 1.0f
        };
        verifyMatrix(matResult, matRotate, 0.001f);
    }

    @Test
    public void testRotateMInPlace() {
        float[] mat = new float[16];

        // Rotate around X
        Matrix.setIdentityM(mat, 0);
        Matrix.rotateM(mat, 0, 45.0f, 1.0f, 0.0f, 0.0f);
        float[] matRotate = new float[] {
            1.0f,  0.0f,    0.0f,    0.0f,
            0.0f,  0.7071f, 0.7071f, 0.0f,
            0.0f, -0.7071f, 0.7071f, 0.0f,
            0.0f,  0.0f,    0.0f,    1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);

        // Rotate around Y
        Matrix.setIdentityM(mat, 0);
        Matrix.rotateM(mat, 0, 45.0f, 0.0f, 1.0f, 0.0f);
        matRotate = new float[] {
            0.7071f, 0.0f, -0.7071f, 0.0f,
            0.0f,    1.0f,  0.0f,    0.0f,
            0.7071f, 0.0f,  0.7071f, 0.0f,
            0.0f,    0.0f,  0.0f,    1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);

        // Rotate around Z
        Matrix.setIdentityM(mat, 0);
        Matrix.rotateM(mat, 0, 45.0f, 0.0f, 0.0f, 1.0f);
        matRotate = new float[] {
             0.7071f, 0.7071f, 0.0f, 0.0f,
            -0.7071f, 0.7071f, 0.0f, 0.0f,
             0.0f,    0.0f,    1.0f, 0.0f,
             0.0f,    0.0f,    0.0f, 1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);
    }

    @Test
    public void testSetRotateM() {
        float[] mat = new float[16];

        // Rotate around X
        Matrix.setIdentityM(mat, 0);
        Matrix.setRotateM(mat, 0, 45.0f, 1.0f, 0.0f, 0.0f);
        float[] matRotate = new float[] {
            1.0f,  0.0f,    0.0f,    0.0f,
            0.0f,  0.7071f, 0.7071f, 0.0f,
            0.0f, -0.7071f, 0.7071f, 0.0f,
            0.0f,  0.0f,    0.0f,    1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);

        // Rotate around Y
        Matrix.setIdentityM(mat, 0);
        Matrix.setRotateM(mat, 0, 45.0f, 0.0f, 1.0f, 0.0f);
        matRotate = new float[] {
            0.7071f, 0.0f, -0.7071f, 0.0f,
            0.0f,    1.0f,  0.0f,    0.0f,
            0.7071f, 0.0f,  0.7071f, 0.0f,
            0.0f,    0.0f,  0.0f,    1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);

        // Rotate around Z
        Matrix.setIdentityM(mat, 0);
        Matrix.setRotateM(mat, 0, 45.0f, 0.0f, 0.0f, 1.0f);
        matRotate = new float[] {
             0.7071f, 0.7071f, 0.0f, 0.0f,
            -0.7071f, 0.7071f, 0.0f, 0.0f,
             0.0f,    0.0f,    1.0f, 0.0f,
             0.0f,    0.0f,    0.0f, 1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);
    }

    @Test
    public void testSetRotateEulerM() {
        float[] mat = new float[16];

        // Rotate around X
        Matrix.setIdentityM(mat, 0);
        Matrix.setRotateEulerM(mat, 0, 45.0f, 0.0f, 0.0f);
        float[] matRotate = new float[] {
            1.0f, 0.0f,     0.0f,    0.0f,
            0.0f, 0.7071f, -0.7071f, 0.0f,
            0.0f, 0.7071f,  0.7071f, 0.0f,
            0.0f, 0.0f,     0.0f,    1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);

        // setRotateEulerM is broken around the Y axis

        // Rotate around Z
        Matrix.setIdentityM(mat, 0);
        Matrix.setRotateEulerM(mat, 0, 0.0f, 0.0f, 45.0f);
        matRotate = new float[] {
            0.7071f, -0.7071f, 0.0f, 0.0f,
            0.7071f,  0.7071f, 0.0f, 0.0f,
            0.0f,     0.0f,    1.0f, 0.0f,
            0.0f,     0.0f,    0.0f, 1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);
    }

    @Test
    public void testSetRotateEulerM2() {
        float[] mat = new float[16];

        // Rotate around X
        Matrix.setIdentityM(mat, 0);
        Matrix.setRotateEulerM2(mat, 0, 45.0f, 0.0f, 0.0f);
        float[] matRotate = new float[] {
            1.0f, 0.0f,     0.0f,    0.0f,
            0.0f, 0.7071f, -0.7071f, 0.0f,
            0.0f, 0.7071f,  0.7071f, 0.0f,
            0.0f, 0.0f,     0.0f,    1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);

        // Rotate around y
        Matrix.setIdentityM(mat, 0);
        Matrix.setRotateEulerM2(mat, 0, 0.0f, 45.0f, 0.0f);
        matRotate = new float[] {
             0.7071f, 0.0f, 0.7071f, 0.0f,
             0.0f,    1.0f, 0.0f,    0.0f,
            -0.7071f, 0.0f, 0.7071f, 0.0f,
             0.0f,    0.0f, 0.0f,    1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);

        // Rotate around Z
        Matrix.setIdentityM(mat, 0);
        Matrix.setRotateEulerM2(mat, 0, 0.0f, 0.0f, 45.0f);
        matRotate = new float[] {
            0.7071f, -0.7071f, 0.0f, 0.0f,
            0.7071f,  0.7071f, 0.0f, 0.0f,
            0.0f,     0.0f,    1.0f, 0.0f,
            0.0f,     0.0f,    0.0f, 1.0f
        };
        verifyMatrix(mat, matRotate, 0.001f);
    }

    private void verifyMatrix(float[] actual, float[] expected) {
        if ((expected == null) || (expected.length != 16)) {
            fail("Expected does not have 16 elements");
        }
        assertArrayEquals(actual, expected, 0.0f);
    }

    private void verifyMatrix(float[] actual, float[] expected, float delta) {
        if ((expected == null) || (expected.length != 16)) {
            fail("Expected does not have 16 elements");
        }
        assertArrayEquals(actual, expected, delta);
    }

    private void verifyVector(float[] actual, float[] expected) {
        if ((expected == null) || (expected.length != 4)) {
            fail("Expected does not have 4 elements");
        }
        assertArrayEquals(actual, expected, 0.0f);
    }
}
