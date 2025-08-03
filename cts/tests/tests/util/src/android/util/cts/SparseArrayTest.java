/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.util.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.function.BiFunction;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SparseArrayTest {
    private static final int[] KEYS = {12, 23, 4, 6, 8, 1, 3, -12, 0, -3, 11, 14, -23};
    private static final Integer[] VALUES = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    private static final int LENGTH = VALUES.length;
    private static final int NON_EXISTED_KEY = 123;
    private static final Integer VALUE_FOR_NON_EXISTED_KEY = -1;

    @Test
    public void testSparseArrayWithDefaultCapacity() {
        SparseArray<Integer> sparseArray = new SparseArray<>();
        assertEquals(0, sparseArray.size());

        int length = VALUES.length;

        for (int i = 0; i < length; i++) {
            sparseArray.put(KEYS[i], VALUES[i]);
            assertEquals(i + 1, sparseArray.size());
        }

        for (int i = 0; i < length; i++) {
            assertEquals(new Integer(i), sparseArray.get(KEYS[i]));
        }

        for (int i = 0; i < length; i++) {
            assertEquals(sparseArray.indexOfValue(VALUES[i]), sparseArray.indexOfKey(KEYS[i]));
        }

        // for key already exist, old value will be replaced
        int existKey = KEYS[0];
        Integer oldValue = VALUES[0]; // 0
        Integer newValue = 100;
        assertEquals(oldValue, sparseArray.get(existKey));
        assertEquals(LENGTH, sparseArray.size());
        sparseArray.put(existKey, newValue);
        assertEquals(newValue, sparseArray.get(existKey));
        assertEquals(LENGTH, sparseArray.size());

        assertEquals(VALUE_FOR_NON_EXISTED_KEY,
                sparseArray.get(NON_EXISTED_KEY, VALUE_FOR_NON_EXISTED_KEY));
        assertNull(sparseArray.get(NON_EXISTED_KEY)); // the default value is null

        int size = sparseArray.size();
        sparseArray.append(NON_EXISTED_KEY, VALUE_FOR_NON_EXISTED_KEY);
        assertEquals(size + 1, sparseArray.size());
        assertEquals(size, sparseArray.indexOfKey(NON_EXISTED_KEY));
        assertEquals(size, sparseArray.indexOfValue(VALUE_FOR_NON_EXISTED_KEY));
        assertEquals(NON_EXISTED_KEY, sparseArray.keyAt(size));
        assertEquals(VALUE_FOR_NON_EXISTED_KEY, sparseArray.valueAt(size));

        sparseArray.setValueAt(size, VALUES[1]);
        assertTrue(VALUE_FOR_NON_EXISTED_KEY != sparseArray.valueAt(size));
        assertEquals(VALUES[1], sparseArray.valueAt(size));

        size = sparseArray.size();
        assertEquals(VALUES[1], sparseArray.get(KEYS[1]));
        assertFalse(VALUE_FOR_NON_EXISTED_KEY == VALUES[1]);
        sparseArray.delete(KEYS[1]);
        assertEquals(VALUE_FOR_NON_EXISTED_KEY,
                sparseArray.get(KEYS[1], VALUE_FOR_NON_EXISTED_KEY));
        assertEquals(size - 1, sparseArray.size());

        size = sparseArray.size();
        assertEquals(VALUES[2], sparseArray.get(KEYS[2]));
        assertFalse(VALUE_FOR_NON_EXISTED_KEY == VALUES[2]);
        sparseArray.remove(KEYS[2]);
        assertEquals(VALUE_FOR_NON_EXISTED_KEY,
                sparseArray.get(KEYS[2], VALUE_FOR_NON_EXISTED_KEY));
        assertEquals(size - 1, sparseArray.size());

        sparseArray.clear();
        assertEquals(0, sparseArray.size());
    }

    @Test
    public void testSparseArrayWithSpecifiedCapacity() {
        SparseArray<Integer> sparseArray = new SparseArray<>(5);
        assertEquals(0, sparseArray.size());

        int length = VALUES.length;

        for (int i = 0; i < length; i++) {
            sparseArray.put(KEYS[i], VALUES[i]);
            assertEquals(i + 1, sparseArray.size());
        }

        for (int i = 0; i < length; i++) {
            assertEquals(VALUES[i], sparseArray.get(KEYS[i]));
        }

        for (int i = 0; i < length; i++) {
            assertEquals(sparseArray.indexOfValue(VALUES[i]), sparseArray.indexOfKey(KEYS[i]));
        }

        // for key already exist, old value will be replaced
        int existKey = KEYS[0];
        Integer oldValue = VALUES[0]; // 0
        Integer newValue = 100;
        assertEquals(oldValue, sparseArray.get(existKey));
        assertEquals(LENGTH, sparseArray.size());
        sparseArray.put(existKey, newValue);
        assertEquals(newValue, sparseArray.get(existKey));
        assertEquals(LENGTH, sparseArray.size());

        assertEquals(VALUE_FOR_NON_EXISTED_KEY,
                sparseArray.get(NON_EXISTED_KEY, VALUE_FOR_NON_EXISTED_KEY));
        assertNull(sparseArray.get(NON_EXISTED_KEY)); // the default value is null

        int size = sparseArray.size();
        sparseArray.append(NON_EXISTED_KEY, VALUE_FOR_NON_EXISTED_KEY);
        assertEquals(size + 1, sparseArray.size());
        assertEquals(size, sparseArray.indexOfKey(NON_EXISTED_KEY));
        assertEquals(size, sparseArray.indexOfValue(VALUE_FOR_NON_EXISTED_KEY));
        assertEquals(NON_EXISTED_KEY, sparseArray.keyAt(size));
        assertEquals(VALUE_FOR_NON_EXISTED_KEY, sparseArray.valueAt(size));

        sparseArray.setValueAt(size, VALUES[1]);
        assertTrue(VALUE_FOR_NON_EXISTED_KEY != sparseArray.valueAt(size));
        assertEquals(VALUES[1], sparseArray.valueAt(size));

        size = sparseArray.size();
        assertEquals(VALUES[1], sparseArray.get(KEYS[1]));
        assertFalse(VALUE_FOR_NON_EXISTED_KEY == VALUES[1]);
        sparseArray.delete(KEYS[1]);
        assertEquals(VALUE_FOR_NON_EXISTED_KEY,
                sparseArray.get(KEYS[1], VALUE_FOR_NON_EXISTED_KEY));
        assertEquals(size - 1, sparseArray.size());

        size = sparseArray.size();
        assertEquals(VALUES[2], sparseArray.get(KEYS[2]));
        assertFalse(VALUE_FOR_NON_EXISTED_KEY == VALUES[2]);
        sparseArray.remove(KEYS[2]);
        assertEquals(VALUE_FOR_NON_EXISTED_KEY,
                sparseArray.get(KEYS[2], VALUE_FOR_NON_EXISTED_KEY));
        assertEquals(size - 1, sparseArray.size());

        sparseArray.clear();
        assertEquals(0, sparseArray.size());
    }

    @Test
    public void testIterationOrder() {
        SparseArray<Long> sparseArray = new SparseArray<>();
        // No matter in which order they are inserted.
        sparseArray.put(1, Long.valueOf(2L));
        sparseArray.put(10, Long.valueOf(20L));
        sparseArray.put(5, Long.valueOf(40L));
        sparseArray.put(Integer.MAX_VALUE, Long.valueOf(Long.MIN_VALUE));
        // The keys are returned in order.
        assertEquals(1, sparseArray.keyAt(0));
        assertEquals(5, sparseArray.keyAt(1));
        assertEquals(10, sparseArray.keyAt(2));
        assertEquals(Integer.MAX_VALUE, sparseArray.keyAt(3));
        // The values are returned in the order of the corresponding keys.
        assertEquals(2L, sparseArray.valueAt(0).longValue());
        assertEquals(40L, sparseArray.valueAt(1).longValue());
        assertEquals(20L, sparseArray.valueAt(2).longValue());
        assertEquals(Long.MIN_VALUE, sparseArray.valueAt(3).longValue());
    }

    @Test
    public void testSet() {
        SparseArray<String> first = new SparseArray<>();
        first.put(0, "0");
        first.put(1, "1");
        first.put(2, "2");

        SparseArray<String> second = new SparseArray<>();
        second.set(2, "2");
        second.set(0, "0");
        second.set(1, "1");

        assertThat(first.size()).isEqualTo(second.size());
        assertThat(first.get(0)).isEqualTo(second.get(0));
        assertThat(first.get(1)).isEqualTo(second.get(1));
        assertThat(first.get(2)).isEqualTo(second.get(2));
        assertThat(first.get(3, "-1")).isEqualTo(second.get(3, "-1"));

        testContentEquals(first, second, SparseArray::contentEquals);
    }

    @Test
    public void testContentEquals() {
        SparseArray<TestData> first = new SparseArray<>();
        first.put(0, new TestData("0"));
        first.put(1, new TestData("1"));
        first.put(2, new TestData("2"));

        SparseArray<SubTestData> second = new SparseArray<>();
        second.put(2, new SubTestData("2"));
        second.put(0, new SubTestData("0"));
        second.put(1, new SubTestData("1"));

        // Subclass succeeds
        testContentEquals(first, second, SparseArray::contentEquals);

        SparseArray<TestData2> noMatchParent = new SparseArray<>();
        noMatchParent.put(2, new TestData2("2"));
        noMatchParent.put(0, new TestData2("0"));
        noMatchParent.put(1, new TestData2("1"));

        // Non-matching parent class fails (as implemented in equals instanceof check)
        testContentNotEquals(first, noMatchParent, SparseArray::contentEquals);

        SparseArray<TestDataCustomEquals> customEqualsOne = new SparseArray<>();
        customEqualsOne.put(0, new TestDataCustomEquals("0"));
        customEqualsOne.put(1, new TestDataCustomEquals("1"));
        customEqualsOne.put(2, new TestDataCustomEquals("2"));

        SparseArray<TestDataCustomEquals2> customEqualsTwo = new SparseArray<>();
        customEqualsTwo.put(2, new TestDataCustomEquals2("2"));
        customEqualsTwo.put(0, new TestDataCustomEquals2("0"));
        customEqualsTwo.put(1, new TestDataCustomEquals2("1"));

        // Non-matching parent class succeeds (as implemented in custom equals check)
        testContentEquals(customEqualsOne, customEqualsTwo, SparseArray::contentEquals);

        // Null fails
        assertFalse(first.contentEquals(null));
    }

    private <T> void testContentEquals(@NonNull SparseArray<?> first,
            @NonNull SparseArray<T> second,
            BiFunction<SparseArray<?>, SparseArray<?>, Boolean> block) {
        // Assert mirrored equality
        assertTrue(block.apply(first, second));
        assertTrue(block.apply(second, first));

        //noinspection unchecked
        second.put(1, (T) first.valueAt(2));

        // Non-matching data at index 1 fails
        assertFalse(first.contentEquals(second));
        assertFalse(second.contentEquals(first));

        // Assert failure of normal Objects.equals maintained
        assertNotEquals(first, second);
        assertNotEquals(second, first);
    }

    private <T> void testContentNotEquals(@NonNull SparseArray<?> first,
            @NonNull SparseArray<T> second,
            BiFunction<SparseArray<?>, SparseArray<?>, Boolean> block) {
        // Assert mirrored equality
        assertFalse(block.apply(first, second));
        assertFalse(block.apply(second, first));
        assertFalse(second.contentEquals(first));

        // Assert failure of normal Objects.equals maintained
        assertNotEquals(first, second);
        assertNotEquals(second, first);
    }

    @Test
    public void testContentHashCode() {
        SparseArray<TestData> first = new SparseArray<>();
        first.put(0, new TestData("0"));
        first.put(1, new TestData("1"));
        first.put(2, new TestData("2"));

        SparseArray<TestData2> second = new SparseArray<>();
        second.put(2, new TestData2("2"));
        second.put(0, new TestData2("0"));
        second.put(1, new TestData2("1"));

        // Non-equal classes that evaluate to the same hash code passes
        assertEquals(first.contentHashCode(), second.contentHashCode());

        // Assert failure of normal Objects.hashCode maintained
        assertNotEquals(first.hashCode(), second.hashCode());

        second.put(1, new TestData2("2"));

        // Non-matching data at index 1 fails
        assertNotEquals(first.contentHashCode(), second.contentHashCode());

        // Assert failure of normal Objects.hashCode maintained
        assertNotEquals(first.hashCode(), second.hashCode());
    }

    private static class TestData {

        private final String data;

        private TestData(@NonNull String data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestData)) return false;
            TestData testData = (TestData) o;
            return Objects.equals(data, testData.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }
    }

    private static class TestData2 {

        private final String data;

        private TestData2(@NonNull String data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestData2)) return false;
            TestData2 testData2 = (TestData2) o;
            return Objects.equals(data, testData2.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }
    }

    private static class TestDataCustomEquals {

        private final String data;

        private TestDataCustomEquals(@NonNull String data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof TestDataCustomEquals) {
                return Objects.equals(data, ((TestDataCustomEquals) o).data);
            } else if (o instanceof TestDataCustomEquals2) {
                return Objects.equals(data, ((TestDataCustomEquals2) o).data);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }
    }

    private static class TestDataCustomEquals2 {

        private final String data;

        private TestDataCustomEquals2(@NonNull String data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof TestDataCustomEquals) {
                return Objects.equals(data, ((TestDataCustomEquals) o).data);
            } else if (o instanceof TestDataCustomEquals2) {
                return Objects.equals(data, ((TestDataCustomEquals2) o).data);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(data);
        }
    }

    private static class SubTestData extends TestData {

        private SubTestData(@NonNull String data) {
            super(data);
        }
    }
}
