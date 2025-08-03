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
package android.app.appsearch.safeparcel;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;
import android.os.Parcel;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SafeParcelableSparseArrayTest {
    private static final int TEST_ID = 42;
    private static final String BUNDLE_KEY = "test_bundle_key";

    private final Parcel mParcel = Parcel.obtain();

    private static Bundle newTestBundle(int key) {
        Bundle b = new Bundle();
        b.putInt(BUNDLE_KEY, key);
        return b;
    }

    private static void assertTestBundleEquals(Bundle expected, Bundle actual) {
        assertThat(actual.getInt(BUNDLE_KEY)).isEqualTo(expected.getInt(BUNDLE_KEY));
    }

    private static SparseArray<Bundle> newBundleSparseArray(int[] keys) {
        SparseArray<Bundle> sparseArray = new SparseArray<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            sparseArray.put(keys[i], newTestBundle(keys[i]));
        }
        return sparseArray;
    }

    @Test
    public void testReadWriteBytesSparseArray() {
        // Create SparseArray.
        SparseArray<byte[]> sparseArray = new SparseArray<>();
        sparseArray.append(20, new byte[] {1});
        sparseArray.append(200, new byte[] {2});

        SafeParcelWriter.writeByteArraySparseArray(
                mParcel, TEST_ID, sparseArray, /* writeNull= */ false);

        int header = prepareToReadFromParcel();
        SparseArray<byte[]> readSparseArray =
                SafeParcelReader.createByteArraySparseArray(mParcel, header);

        assertByteArrayEquals(sparseArray, readSparseArray);
    }

    @Test
    public void testReadWriteBundleSparseArray() {
        // Create SparseArray.
        int[] keys = new int[] {0, 5, 10, 300, 5000};
        SparseArray<Bundle> sparseArray = newBundleSparseArray(keys);

        SafeParcelWriter.writeTypedSparseArray(
                mParcel, TEST_ID, sparseArray, /* writeNull= */ false);

        int header = prepareToReadFromParcel();
        SparseArray<Bundle> readSparseArray =
                SafeParcelReader.createTypedSparseArray(mParcel, header, Bundle.CREATOR);

        assertTestBundleEquals(sparseArray, readSparseArray);
    }

    private static <T> SparseArray<T> create(T[] values) {
        SparseArray<T> result = new SparseArray<>();
        int key = 0;
        for (T value : values) {
            result.append(key, value);
            key += 2;
        }
        return result;
    }

    private static SparseBooleanArray createSparseBooleanArray(boolean[] values) {
        SparseBooleanArray result = new SparseBooleanArray();
        int key = 0;
        for (boolean value : values) {
            result.append(key, value);
            key += 2;
        }
        return result;
    }

    private static SparseIntArray createSparseIntArray(int[] values) {
        SparseIntArray result = new SparseIntArray();
        int key = 0;
        for (int value : values) {
            result.append(key, value);
            key += 2;
        }
        return result;
    }

    private static SparseLongArray createSparseLongArray(long[] values) {
        SparseLongArray result = new SparseLongArray();
        int key = 0;
        for (long value : values) {
            result.append(key, value);
            key += 2;
        }
        return result;
    }

    @Test
    public void testReadWriteSafeParcelableWithSparseArray() {
        // Create SafeParcelable.
        SparseBooleanArray booleans = createSparseBooleanArray(new boolean[] {false, true, true});
        SparseIntArray integers = createSparseIntArray(new int[] {100, 1000});
        SparseLongArray longs = createSparseLongArray(new long[] {1000L, 20L});
        SparseArray<Float> floats = create(new Float[] {3.1f, 3.14f});
        SparseArray<Double> doubles = create(new Double[] {3.14, 3.1});
        SparseArray<String> strings = create(new String[] {"foo", "bar"});
        SparseArray<Bundle> bundles = newBundleSparseArray(new int[] {3, 7, 11});
        SparseArray<byte[]> bytes = create(new byte[][] {new byte[] {1}, new byte[] {42}});
        TestSafeParcelableWithSparseArray val =
                new TestSafeParcelableWithSparseArray(
                        booleans, integers, longs, floats, doubles, strings, bundles, bytes);

        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, val, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelableWithSparseArray readVal =
                SafeParcelReader.createParcelable(
                        mParcel, header, TestSafeParcelableWithSparseArray.CREATOR);

        assertEquals(val.mBooleans, readVal.mBooleans);
        assertEquals(val.mIntegers, readVal.mIntegers);
        assertEquals(val.mLongs, readVal.mLongs);
        assertEquals(val.mFloats, readVal.mFloats);
        assertEquals(val.mDoubles, readVal.mDoubles);
        assertEquals(val.mStrings, readVal.mStrings);
        assertTestBundleEquals(val.mBundles, readVal.mBundles);
        assertByteArrayEquals(val.mBytes, readVal.mBytes);
    }

    private static void assertEquals(SparseIntArray expected, SparseIntArray actual) {
        assertThat(actual.size()).isEqualTo(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.keyAt(i)).isEqualTo(expected.keyAt(i));
            assertThat(actual.valueAt(i)).isEqualTo(expected.valueAt(i));
        }
    }

    private static void assertEquals(SparseLongArray expected, SparseLongArray actual) {
        assertThat(actual.size()).isEqualTo(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.keyAt(i)).isEqualTo(expected.keyAt(i));
            assertThat(actual.valueAt(i)).isEqualTo(expected.valueAt(i));
        }
    }

    private static void assertEquals(SparseBooleanArray expected, SparseBooleanArray actual) {
        assertThat(actual.size()).isEqualTo(expected.size());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.keyAt(i)).isEqualTo(expected.keyAt(i));
            assertThat(actual.valueAt(i)).isEqualTo(expected.valueAt(i));
        }
    }

    private static <T> void assertSizeEquals(SparseArray<T> expected, SparseArray<T> actual) {
        if (expected == null) {
            assertThat(actual).isNull();
            return;
        }
        assertThat(actual).isNotNull();
        assertThat(actual.size()).isEqualTo(expected.size());
    }

    private static <T> void assertEquals(SparseArray<T> expected, SparseArray<T> actual) {
        assertSizeEquals(expected, actual);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.keyAt(i)).isEqualTo(expected.keyAt(i));
            assertThat(actual.valueAt(i)).isEqualTo(expected.valueAt(i));
        }
    }

    private static void assertByteArrayEquals(
            SparseArray<byte[]> expected, SparseArray<byte[]> actual) {
        assertSizeEquals(expected, actual);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.keyAt(i)).isEqualTo(expected.keyAt(i));
            assertThat(actual.valueAt(i)).isEqualTo(expected.valueAt(i));
        }
    }

    private static void assertTestBundleEquals(
            SparseArray<Bundle> expected, SparseArray<Bundle> actual) {
        assertSizeEquals(expected, actual);
        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.keyAt(i)).isEqualTo(expected.keyAt(i));
            assertTestBundleEquals(expected.valueAt(i), actual.valueAt(i));
        }
    }

    // Reset the data position and return the header.
    private int prepareToReadFromParcel() {
        mParcel.setDataPosition(0);
        return SafeParcelReader.readHeader(mParcel);
    }
}
