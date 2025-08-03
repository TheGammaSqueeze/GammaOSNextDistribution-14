/*
 * Copyright 2024 The Android Open Source Project
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Matrix;
import android.graphics.Matrix44;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.graphics.hwui.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class Matrix44Test {
    private Matrix44 mMatrix44;
    private float[] mValues;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() {
        mMatrix44 = new Matrix44();
        mValues = new float[16];
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testConstructor() {
        // mMatrix initialized in setup()
        assertTrue(mMatrix44.isIdentity());

        Matrix m33 = new Matrix();
        assertTrue(new Matrix44(m33).isIdentity());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testIsIdentity() {
        assertTrue(mMatrix44.isIdentity());
        mMatrix44.set(0, 0, 5);
        assertFalse(mMatrix44.isIdentity());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testGetValues() {
        mMatrix44.getValues(mValues);
        assertArrayEquals(mValues, new float[] {1.0f, 0.0f, 0.0f, 0.0f,
                                                0.0f, 1.0f, 0.0f, 0.0f,
                                                0.0f, 0.0f, 1.0f, 0.0f,
                                                0.0f, 0.0f, 0.0f, 1.0f}, 0.0f);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testSetValues() {
        mValues[0] = 9.0f;
        mValues[4] = 2.0f;
        mValues[15] = 3.0f;
        mMatrix44.setValues(mValues);
        verifyMatrix(new float[] {9.0f, 0.0f, 0.0f, 0.0f,
                                  2.0f, 0.0f, 0.0f, 0.0f,
                                  0.0f, 0.0f, 0.0f, 0.0f,
                                  0.0f, 0.0f, 0.0f, 3.0f}, 0.0f);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testGet() {
        float a = mMatrix44.get(0, 0);
        float b = mMatrix44.get(3, 1);
        assertEquals(1.0f, a, 0.0);
        assertEquals(0.0f, b, 0.0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testSet() {
        mMatrix44.set(0, 0, 5.0f);
        mMatrix44.set(3, 3, 2.0f);

        float a = mMatrix44.get(0, 0);
        float b = mMatrix44.get(3, 3);
        assertEquals(5.0f, a, 0.0);
        assertEquals(2.0f, b, 0.0);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testReset() {
        mMatrix44.set(2, 2, 5.0f);
        assertFalse(mMatrix44.isIdentity());
        mMatrix44.reset();
        assertTrue(mMatrix44.isIdentity());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testInvert() {
        mMatrix44.setValues(new float[] {1.0f, 2.0f, 2.0f, 2.0f,
                                         2.0f, 2.0f, 4.0f, 4.0f,
                                         0.0f, 0.0f, 4.0f, 0.0f,
                                         1.0f, 0.0f, 0.0f, 4.0f});
        mMatrix44.invert();
        verifyMatrix(new float[] {-2.0f, 2.0f, -1.0f, -1.0f,
                                  1.0f, -0.5f, 0.0f, 0.0f,
                                  0.0f, 0.0f, 0.25f, 0.0f,
                                  0.5f, -0.5f, 0.25f, 0.5f}, 0.0f);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testInvertFailure() {
        mMatrix44.setValues(new float[] {0.0f, 0.0f, 0.0f, 0.0f,
                                         0.0f, 0.0f, 0.0f, 0.0f,
                                         0.0f, 0.0f, 0.0f, 0.0f,
                                         0.0f, 0.0f, 0.0f, 1.0f});
        assertFalse(mMatrix44.invert());
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testMap() {
        mMatrix44.setValues(new float[] {2.0f, 0.0f, 0.0f, 3.0f,
                                         0.0f, 1.0f, 0.0f, 5.0f,
                                         0.0f, 0.0f, 3.0f, 1.0f,
                                         0.0f, 0.0f, 0.0f, 1.0f});
        float[] result = mMatrix44.map(4, 3, 2, 1);
        assertArrayEquals(new float[]{11, 8, 7, 1}, result, 0.0f);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testConcat() {
        mMatrix44.set(1, 1, 2.0f);
        mMatrix44.set(2, 3, 3.0f);
        Matrix44 b = new Matrix44();
        b.set(0, 0, 20.0f);
        b.set(2, 3, 3.0f);

        mMatrix44.concat(b);
        verifyMatrix(new float[] {20.0f, 0.0f, 0.0f, 0.0f,
                                  0.0f, 2.0f, 0.0f, 0.0f,
                                  0.0f, 0.0f, 1.0f, 6.0f,
                                  0.0f, 0.0f, 0.0f, 1.0f}, 0.0f);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testRotateX() {
        mMatrix44.rotate(45, 1, 0, 0);
        verifyMatrix(new float[] {1.0f, 0.0f, 0.0f, 0.0f,
                                  0.0f, 0.70710677f, -0.70710677f, 0.0f,
                                  0.0f, 0.70710677f, 0.70710677f, 0.0f,
                                  0.0f, 0.0f, 0.0f, 1.0f}, 0.01f);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testRotateY() {
        mMatrix44.rotate(45, 0, 1, 0);
        verifyMatrix(new float[] {0.70710677f, 0.0f, 0.70710677f, 0.0f,
                                  0.0f, 1.0f, 0.0f, 0.0f,
                                  -0.70710677f, 0.0f, 0.70710677f, 0.0f,
                                  0.0f, 0.0f, 0.0f, 1.0f}, 0.01f);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testRotateZ() {
        mMatrix44.rotate(45, 0, 0, 1);
        verifyMatrix(new float[] {0.70710677f, -0.70710677f, 0.0f, 0.0f,
                                  0.70710677f, 0.70710677f, 0.0f, 0.0f,
                                  0.0f, 0.0f, 1.0f, 0.0f,
                                  0.0f, 0.0f, 0.0f, 1.0f}, 0.01f);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testScale() {
        mMatrix44.scale(5, -5, 2);
        verifyMatrix(new float[] {5.0f, 0.0f, 0.0f, 0.0f,
                                  0.0f, -5.0f, 0.0f, 0.0f,
                                  0.0f, 0.0f, 2.0f, 0.0f,
                                  0.0f, 0.0f, 0.0f, 1.0f}, 0.0f);
    }

    @RequiresFlagsEnabled(Flags.FLAG_MATRIX_44)
    @Test
    public void testTranslate() {
        mMatrix44.translate(5, -5, 2);
        verifyMatrix(new float[] {1.0f, 0.0f, 0.0f, 5.0f,
                                  0.0f, 1.0f, 0.0f, -5.0f,
                                  0.0f, 0.0f, 1.0f, 2.0f,
                                  0.0f, 0.0f, 0.0f, 1.0f}, 0.0f);
    }

    // will clear values in mValues
    private void verifyMatrix(float[] expected, float delta) {
        if ((expected == null) || (expected.length != 16)) {
            fail("Expected does not have 9 elements");
        }
        mMatrix44.getValues(mValues);
        assertArrayEquals(expected, mValues, delta);
    }
}
