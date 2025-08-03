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

import static com.android.server.uwb.correction.math.MathHelper.F_PI;

import static com.google.common.truth.Truth.assertThat;

import com.android.server.uwb.correction.TestHelpers;

import org.junit.Test;

public class MatrixTest {

    private static final float EPSILON = 1e-5f;

    @Test
    public void invert_invertsMatrixOntoItself() {
        Matrix matrix = new Matrix(
                new float[]{+1f, +2f, +3f, +4f, -1f, +1f, +2f, +3f, -2f, -1f, +1f, +2f, -3f, -2f,
                        -1f, +1f});

        matrix = matrix.invert();

        assertThat(matrix).isNotNull();

        Matrix expected = new Matrix(
                new float[]{+0.6f, -0.9f, +0.1f, +0.1f, -0.4f, +1.1f, -0.9f, +0.1f, -0.4f, +0.1f,
                        +1.1f, -0.9f, +0.6f, -0.4f, -0.4f, +0.6f});

        assertThat(matrix.data).usingTolerance(EPSILON).containsExactly(expected.data).inOrder();
    }

    @Test
    public void transformPoint_zeroZerosVector() {
        Matrix transform = new Matrix(new float[16]);

        Vector3 initial = new Vector3(1, 0, 1);
        Vector3 transformed = transform.transformPoint(initial);

        assertThat(transformed.length()).isWithin(EPSILON).of(0);
    }

    @Test
    public void transformPoint_identityKeepsVector() {
        Matrix transform = Matrix.identity();

        Vector3 initial = new Vector3(1, 0, 1);
        Vector3 transformed = transform.transformPoint(initial);

        TestHelpers.assertClose(transformed, initial);
    }

    @Test
    public void transformPoint_rotate180AroundOrthogonalFlipsVector() {
        Matrix transform = Matrix.newRotation(Quaternion.axisAngle(new Vector3(0, 1, 0), F_PI));

        Vector3 initial = new Vector3(1, 0, 1);
        Vector3 transformed = transform.transformPoint(initial);
        Vector3 expected = initial.scaled(-1);

        TestHelpers.assertClose(transformed, expected);
    }

    @Test
    public void transformPoint_isConsistentWithPoseTransformPoint() {
        RandomTestData random = new RandomTestData();
        for (int iteration = 0; iteration < 10; ++iteration) {
            Pose pose = random.nextPose();
            Vector3 initial = random.nextVector();

            Matrix transform = Matrix.newRigidTransform(pose);
            Vector3 transformed = transform.transformPoint(initial);
            Vector3 expected = pose.transformPoint(initial);

            TestHelpers.assertClose(transformed, expected);
        }
    }

    @Test
    public void newRigidTransform_isEquivalentToTrsWithoutScale() {
        RandomTestData random = new RandomTestData();
        for (int iteration = 0; iteration < 10; ++iteration) {
            Pose pose = random.nextPose();

            // Create matrix for translation, rotation, and scale separately.
            Matrix translationMat = Matrix.newTranslation(pose.translation);
            Matrix rotationMat = Matrix.newRotation(pose.rotation);

            // Create transform by multiplying matrices together.
            Matrix product = Matrix.multiply(rotationMat, translationMat);

            // Create transform with the newRigidTransform method.
            Matrix trs = Matrix.newRigidTransform(pose);

            // Create transform with the newTrs method.
            Matrix transform = Matrix.newTrs(pose.translation, pose.rotation, new Vector3(1, 1, 1));

            assertThat(transform.data).usingTolerance(EPSILON).containsExactly(product.data)
                    .inOrder();
            assertThat(transform.data).usingTolerance(EPSILON).containsExactly(trs.data).inOrder();
        }
    }

    @Test
    public void newTrs_isEquivalentToMultiplyingTranslationRotationScale() {
        RandomTestData random = new RandomTestData();
        for (int iteration = 0; iteration < 10; ++iteration) {
            Vector3 translation = random.nextVector();
            Quaternion rotation = random.nextRotation();
            Vector3 scale = random.nextVector();

            // Create matrix for translation, rotation, and scale separately.
            Matrix translationMat = Matrix.newTranslation(translation);
            Matrix rotationMat = Matrix.newRotation(rotation);
            Matrix scaleMat = Matrix.newScale(scale);

            // Create TRS by multiplying matrices together.
            Matrix product;
            product = Matrix.multiply(rotationMat, translationMat);
            product = Matrix.multiply(scaleMat, product);

            // Create TRS directly with the newTrs method.
            Matrix trs = Matrix.newTrs(translation, rotation, scale);

            assertThat(trs.data).usingTolerance(EPSILON).containsExactly(product.data).inOrder();
        }
    }

    @Test
    public void newScale_uniformScaleMatricesAreEqual() {
        RandomTestData random = new RandomTestData();
        for (int iteration = 0; iteration < 10; ++iteration) {
            float scale = random.nextFloat();
            assertThat(Matrix.newScale(scale).data).usingTolerance(EPSILON)
                    .containsExactly(Matrix.newScale(new Vector3(scale, scale, scale)).data)
                    .inOrder();
        }
    }

    // I == I^-1
    @Test
    public void invert_identityInverseIsIdentity() {
        Matrix inverse = Matrix.identity().invert();
        assertThat(inverse).isNotNull();
        assertThat(inverse.data).usingTolerance(EPSILON).containsExactly(Matrix.identity().data)
                .inOrder();
    }

    // A * A^-1 == A^-1 * A == I
    @Test
    public void invert_multiplyWithInverseIsIdentity() {
        RandomTestData random = new RandomTestData();
        for (int iteration = 0; iteration < 10; ++iteration) {
            Matrix a = random.nextMatrix();
            Matrix inverse = a.invert();
            Matrix product;

            if (inverse != null) {
                product = Matrix.multiply(a, inverse);
                assertThat(product.data).usingTolerance(EPSILON)
                        .containsExactly(Matrix.identity().data).inOrder();

                product = Matrix.multiply(inverse, a);
                assertThat(product.data).usingTolerance(EPSILON)
                        .containsExactly(Matrix.identity().data).inOrder();
            }
        }
    }

    // A * I == I * A == A
    @Test
    public void multiply_multiplyWithIdentityIsNoOp() {
        Matrix product;
        RandomTestData random = new RandomTestData();
        for (int iteration = 0; iteration < 10; ++iteration) {
            Matrix a = random.nextMatrix();

            product = Matrix.multiply(a, Matrix.identity());
            assertThat(product.data).usingTolerance(EPSILON).containsExactly(a.data).inOrder();

            product = Matrix.multiply(Matrix.identity(), a);
            assertThat(product.data).usingTolerance(EPSILON).containsExactly(a.data).inOrder();
        }
    }

    // (A * B) ^ -1 == B^-1 * A^-1
    @Test
    public void invert_inverseMultiplyIsReversedMultiplyInverse() {
        RandomTestData random = new RandomTestData();
        for (int iteration = 0; iteration < 10; ++iteration) {
            Matrix a = random.nextMatrix();
            Matrix b = random.nextMatrix();

            Matrix product = Matrix.multiply(a, b);
            Matrix inverse = product.invert();
            if (inverse != null) {
                Matrix ainverse = a.invert();
                Matrix binverse = b.invert();
                Matrix rproduct = Matrix.multiply(binverse, ainverse);

                assertThat(inverse.data).usingTolerance(
                                10 * EPSILON) // This loses precision faster than other tests.
                        .containsExactly(rproduct.data).inOrder();
            }
        }
    }

    // (A * B) * C == A * (B * C)
    @Test
    public void multiply_isAssociative() {
        RandomTestData random = new RandomTestData();
        for (int iteration = 0; iteration < 10; ++iteration) {
            Matrix a = random.nextMatrix();
            Matrix b = random.nextMatrix();
            Matrix c = random.nextMatrix();

            Matrix ab = Matrix.multiply(a, b);
            Matrix bc = Matrix.multiply(b, c);
            Matrix abC = Matrix.multiply(ab, c);
            Matrix aBC = Matrix.multiply(a, bc);

            assertThat(abC.data).usingTolerance(EPSILON).containsExactly(aBC.data).inOrder();
        }
    }
}
