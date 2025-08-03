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

package com.android.car.internal.util;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.util.ArraySet;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public final class ArrayUtilsTest {

    @Test
    public void testEquals() {
        byte[] array1 = new byte[]{(byte) 1, (byte) 2, (byte) 4};
        byte[] array2 = new byte[]{(byte) 1, (byte) 2, (byte) 3};
        assertThat(ArrayUtils.equals(array1, array2, 2)).isTrue();
        assertThat(ArrayUtils.equals(array1, array2, 3)).isFalse();
    }

    @Test
    public void testEqualsSameArray() {
        byte[] array1 = new byte[]{(byte) 1, (byte) 2, (byte) 4};
        assertThat(ArrayUtils.equals(array1, array1, 2)).isTrue();
    }

    @Test
    public void testEqualsNull() {
        byte[] array1 = new byte[]{(byte) 1, (byte) 2, (byte) 4};
        assertThat(ArrayUtils.equals(array1, null, 2)).isFalse();
        assertThat(ArrayUtils.equals(null, array1, 2)).isFalse();
    }

    @Test
    public void testEqualsLengthTooSmall() {
        byte[] array1 = new byte[]{(byte) 1, (byte) 2, (byte) 3};
        byte[] array2 = new byte[]{(byte) 1, (byte) 2, (byte) 3};
        assertThat(ArrayUtils.equals(array1, array2, 4)).isFalse();
    }

    @Test
    public void testEqualsNegativeLength() {
        byte[] array1 = new byte[]{(byte) 1, (byte) 2, (byte) 3};
        assertThrows(IllegalArgumentException.class,
                () -> ArrayUtils.equals(array1, array1, -1));
    }

    @Test
    public void testEmptyArray() {
        Integer[] array1 = ArrayUtils.emptyArray(Integer.class);
        // This should return an array from cache.
        Integer[] array2 = ArrayUtils.emptyArray(Integer.class);
        Float[] array3 = ArrayUtils.emptyArray(Float.class);

        assertThat(ArrayUtils.isEmpty(array1)).isTrue();
        assertThat(ArrayUtils.isEmpty(array2)).isTrue();
        assertThat(ArrayUtils.isEmpty(array3)).isTrue();
    }

    @Test
    public void testEmptyIfNull() {
        Integer[] array1 = ArrayUtils.emptyIfNull(null, Integer.class);
        Integer[] array2 = ArrayUtils.emptyIfNull(new Integer[]{1, 2}, Integer.class);

        assertThat(ArrayUtils.isEmpty(array1)).isTrue();
        assertThat(ArrayUtils.isEmpty(array2)).isFalse();
    }

    @Test
    public void testIsEmptyAndSize() {
        assertThat(ArrayUtils.isEmpty((ArrayList<Integer>) null)).isTrue();

        ArrayList<Integer> list = new ArrayList<Integer>();
        assertThat(ArrayUtils.isEmpty(list)).isTrue();
        list = new ArrayList<Integer>(Arrays.asList(1, 2));
        assertThat(ArrayUtils.isEmpty(list)).isFalse();
        assertThat(ArrayUtils.size(list)).isEqualTo(2);

        HashMap<Integer, Integer> map = new HashMap();
        assertThat(ArrayUtils.isEmpty(map)).isTrue();
        map.put(1, 1);
        assertThat(ArrayUtils.isEmpty(map)).isFalse();
        assertThat(ArrayUtils.size(map)).isEqualTo(1);

        Integer[] array = new Integer[0];
        assertThat(ArrayUtils.isEmpty(array)).isTrue();
        array = new Integer[]{1, 2};
        assertThat(ArrayUtils.isEmpty(array)).isFalse();
        assertThat(ArrayUtils.size(array)).isEqualTo(2);

        assertThat(ArrayUtils.isEmpty(new int[0])).isTrue();
        assertThat(ArrayUtils.isEmpty(new int[]{1, 2})).isFalse();

        assertThat(ArrayUtils.isEmpty(new long[0])).isTrue();
        assertThat(ArrayUtils.isEmpty(new long[]{1L, 2L})).isFalse();

        assertThat(ArrayUtils.isEmpty(new byte[0])).isTrue();
        assertThat(ArrayUtils.isEmpty(new byte[]{(byte) 1, (byte) 2})).isFalse();

        assertThat(ArrayUtils.isEmpty(new boolean[0])).isTrue();
        assertThat(ArrayUtils.isEmpty(new boolean[]{true, false})).isFalse();
    }

    @Test
    public void testContains() {
        Integer[] array = null;
        assertThat(ArrayUtils.contains(array, 1)).isFalse();

        array = new Integer[]{};
        assertThat(ArrayUtils.contains(array, 1)).isFalse();

        array = new Integer[]{1, 2};
        assertThat(ArrayUtils.contains(array, 1)).isTrue();
    }

    @Test
    public void testIndexOf() {
        Integer[] array = new Integer[]{1, 2};
        assertThat(ArrayUtils.indexOf(array, 1)).isEqualTo(0);
        assertThat(ArrayUtils.indexOf(array, 2)).isEqualTo(1);
        assertThat(ArrayUtils.indexOf(array, 3)).isEqualTo(-1);
    }

    @Test
    public void testContainsAll() {
        Integer[] array = new Integer[]{1, 2};
        assertThat(ArrayUtils.containsAll(array, array)).isTrue();
        assertThat(ArrayUtils.containsAll(array, new Integer[]{2, 1})).isTrue();
        assertThat(ArrayUtils.containsAll(array, new Integer[]{1, 2, 3})).isFalse();
    }

    @Test
    public void testContainsAny() {
        Integer[] array = new Integer[]{1, 2};
        assertThat(ArrayUtils.containsAny(array, new Integer[]{1, 3})).isTrue();
        assertThat(ArrayUtils.containsAny(array, new Integer[]{4, 5})).isFalse();
    }

    @Test
    public void testContainsInt() {
        int[] array = null;
        assertThat(ArrayUtils.contains(array, 1)).isFalse();

        array = new int[]{};
        assertThat(ArrayUtils.contains(array, 1)).isFalse();

        array = new int[]{1, 2};
        assertThat(ArrayUtils.contains(array, 1)).isTrue();
    }

    @Test
    public void testContainsLong() {
        long[] array = null;
        assertThat(ArrayUtils.contains(array, 1L)).isFalse();

        array = new long[]{};
        assertThat(ArrayUtils.contains(array, 1L)).isFalse();

        array = new long[]{1L, 2L};
        assertThat(ArrayUtils.contains(array, 1L)).isTrue();
    }

    @Test
    public void testContainsChar() {
        char[] array = null;
        assertThat(ArrayUtils.contains(array, 'a')).isFalse();

        array = new char[]{};
        assertThat(ArrayUtils.contains(array, 'a')).isFalse();

        array = new char[]{'a', 'b'};
        assertThat(ArrayUtils.contains(array, 'a')).isTrue();
    }

    @Test
    public void testContainsAllChar() {
        char[] array = null;
        char[] check = null;

        assertThat(ArrayUtils.containsAll(array, check)).isTrue();

        array = new char[]{'a', 'b', 'c'};
        check = new char[]{'a', 'c'};

        assertThat(ArrayUtils.containsAll(array, check)).isTrue();

        check = new char[]{'a', 'd'};

        assertThat(ArrayUtils.containsAll(array, check)).isFalse();
    }

    @Test
    public void testTotal() {
        long[] array = null;

        assertThat(ArrayUtils.total(array)).isEqualTo(0L);

        array = new long[]{1L, 2L, 3L};
        assertThat(ArrayUtils.total(array)).isEqualTo(6L);
    }

    @Test
    public void testConverToIntArray() {
        ArrayList<Integer> list = new ArrayList<Integer>(Arrays.asList(1, 2));
        assertThat(ArrayUtils.convertToIntArray(list)).isEqualTo(new int[]{1, 2});
    }

    @Test
    public void testConverToLongArray() {
        int[] array = null;

        assertThat(ArrayUtils.convertToLongArray(array)).isNull();

        array = new int[]{1, 2};

        assertThat(ArrayUtils.convertToLongArray(array)).isEqualTo(new long[]{1L, 2L});
    }

    @Test
    public void testConcatElements() {
        Integer[] array1 = new Integer[]{1, 2};
        Integer[] array2 = new Integer[]{3, 4};

        Integer[] array = ArrayUtils.concatElements(Integer.class, array1, array2, null);

        assertThat(array).isEqualTo(new Integer[]{1, 2, 3, 4});
        assertThat(ArrayUtils.concatElements(Integer.class, (Integer[]) null)).isEqualTo(
                new Integer[0]);
        assertThat(ArrayUtils.concatElements(Integer.class, (Integer[][]) null)).isEqualTo(
                new Integer[0]);
    }

    @Test
    public void testAppendElement() {
        Integer[] array = new Integer[]{1, 2};

        assertThat(ArrayUtils.appendElement(Integer.class, array, 3)).isEqualTo(
                new Integer[]{1, 2, 3});
        assertThat(ArrayUtils.appendElement(Integer.class, (Integer[]) null, 3)).isEqualTo(
                new Integer[]{3});
        assertThat(ArrayUtils.appendElement(Integer.class, array, 2, /* allowDuplicate=*/ true))
                .isEqualTo(new Integer[]{1, 2, 2});
        assertThat(ArrayUtils.appendElement(Integer.class, array, 2, /* allowDuplicate=*/ false))
                .isEqualTo(new Integer[]{1, 2});
    }

    @Test
    public void testRemoveElement() {
        Integer[] array = new Integer[]{1, 2};

        assertThat(ArrayUtils.removeElement(Integer.class, array, 1)).isEqualTo(new Integer[]{2});
        assertThat(ArrayUtils.removeElement(Integer.class, array, 3)).isEqualTo(
                new Integer[]{1, 2});
        assertThat(ArrayUtils.removeElement(Integer.class, new Integer[]{1}, 1)).isNull();

        assertThat(ArrayUtils.removeElement(Integer.class, (Integer[]) null, 1)).isNull();
    }

    @Test
    public void testAppendInt() {
        int[] array = new int[]{1, 2};

        assertThat(ArrayUtils.appendInt(array, 3)).isEqualTo(new int[]{1, 2, 3});
        assertThat(ArrayUtils.appendInt((int[]) null, 3)).isEqualTo(new int[]{3});
        assertThat(ArrayUtils.appendInt(array, 2, /* allowDuplicate=*/ true))
                .isEqualTo(new int[]{1, 2, 2});
        assertThat(ArrayUtils.appendInt(array, 2, /* allowDuplicate=*/ false))
                .isEqualTo(new int[]{1, 2});
        assertThat(ArrayUtils.appendInt(null, 3)).isEqualTo(new int[]{3});
    }

    @Test
    public void testRemoveInt() {
        int[] array = new int[]{1, 2};

        assertThat(ArrayUtils.removeInt(array, 1)).isEqualTo(new int[]{2});
        assertThat(ArrayUtils.removeInt(array, 2)).isEqualTo(new int[]{1});
        assertThat(ArrayUtils.removeInt(array, 3)).isEqualTo(new int[]{1, 2});
        assertThat(ArrayUtils.removeInt(new int[]{1}, 1)).isEqualTo(new int[0]);
        assertThat(ArrayUtils.removeInt(null, 1)).isEqualTo(null);
    }

    @Test
    public void testRemoveString() {
        String[] array = new String[]{"a", "b"};

        assertThat(ArrayUtils.removeString(array, "a")).isEqualTo(new String[]{"b"});
        assertThat(ArrayUtils.removeString(array, "b")).isEqualTo(new String[]{"a"});
        assertThat(ArrayUtils.removeString(array, "c")).isEqualTo(new String[]{"a", "b"});
        assertThat(ArrayUtils.removeString(new String[]{"a"}, "a")).isEqualTo(new String[0]);
        assertThat(ArrayUtils.removeString(null, "a")).isEqualTo(null);
    }

    @Test
    public void testAppendLong() {
        long[] array = new long[]{1, 2};

        assertThat(ArrayUtils.appendLong(array, 3L)).isEqualTo(new long[]{1L, 2L, 3L});
        assertThat(ArrayUtils.appendLong((long[]) null, 3)).isEqualTo(new long[]{3L});
        assertThat(ArrayUtils.appendLong(array, 2L, /* allowDuplicate=*/ true))
                .isEqualTo(new long[]{1L, 2L, 2L});
        assertThat(ArrayUtils.appendLong(array, 2L, /* allowDuplicate=*/ false))
                .isEqualTo(new long[]{1L, 2L});
        assertThat(ArrayUtils.appendLong(null, 3L)).isEqualTo(new long[]{3L});
    }

    @Test
    public void testRemoveLong() {
        long[] array = new long[]{1, 2};

        assertThat(ArrayUtils.removeLong(array, 1L)).isEqualTo(new long[]{2L});
        assertThat(ArrayUtils.removeLong(array, 2L)).isEqualTo(new long[]{1L});
        assertThat(ArrayUtils.removeLong(array, 3L)).isEqualTo(new long[]{1L, 2L});
        assertThat(ArrayUtils.removeLong(new long[]{1L}, 1L)).isEqualTo(new long[0]);
        assertThat(ArrayUtils.removeLong(null, 1L)).isEqualTo(null);
    }

    @Test
    public void testCloneOrNull() {
        long[] longArray = null;
        assertThat(ArrayUtils.cloneOrNull(longArray)).isNull();

        longArray = new long[]{1L};
        assertThat(ArrayUtils.cloneOrNull(longArray)).isEqualTo(longArray);

        Integer[] intArray = null;
        assertThat(ArrayUtils.cloneOrNull(intArray)).isNull();

        intArray = new Integer[]{1};
        assertThat(ArrayUtils.cloneOrNull(intArray)).isEqualTo(intArray);

        ArraySet<Integer> intSet = null;
        assertThat(ArrayUtils.cloneOrNull(intSet)).isNull();

        intSet = new ArraySet();
        intSet.add(1);
        assertThat(ArrayUtils.cloneOrNull(intSet)).isEqualTo(intSet);
    }

    @Test
    public void testArraySetAdd() {
        ArraySet<Integer> set = null;

        set = ArrayUtils.add(set, 1);

        assertThat(set).isEqualTo(new ArraySet<Integer>(new Integer[]{1}));

        set = ArrayUtils.add(set, 2);

        assertThat(set).isEqualTo(new ArraySet<Integer>(new Integer[]{1, 2}));
    }

    @Test
    public void testArraySetAddAll() {
        ArraySet<Integer> set = null;

        set = ArrayUtils.addAll(set, new ArrayList<Integer>(Arrays.asList(1, 2)));

        assertThat(set).isEqualTo(new ArraySet<Integer>(new Integer[]{1, 2}));

        set = ArrayUtils.addAll(set, new ArrayList<Integer>(Arrays.asList(3, 4)));

        assertThat(set).isEqualTo(new ArraySet<Integer>(new Integer[]{1, 2, 3, 4}));
    }

    @Test
    public void testArraySetRemove() {
        ArraySet<Integer> set = null;

        assertThat(ArrayUtils.remove(set, 1)).isNull();

        set = new ArraySet(new Integer[]{1});

        assertThat(ArrayUtils.remove(set, 1)).isNull();

        set = new ArraySet(new Integer[]{1, 2});

        assertThat(ArrayUtils.remove(set, 1)).isEqualTo(new ArraySet(new Integer[]{2}));
    }

    @Test
    public void testArrayListAdd() {
        ArrayList<Integer> array = null;

        array = ArrayUtils.add(array, 1);

        assertThat(array).isEqualTo(new ArrayList<Integer>(Arrays.asList(1)));

        array = ArrayUtils.add(array, 2);

        assertThat(array).isEqualTo(new ArrayList<Integer>(Arrays.asList(1, 2)));

        array = ArrayUtils.add(array, 1, 3);

        assertThat(array).isEqualTo(new ArrayList<Integer>(Arrays.asList(1, 3, 2)));
        assertThat(ArrayUtils.add((ArrayList<Integer>) null, 0, 1)).isEqualTo(
                new ArrayList<Integer>(Arrays.asList(1)));
    }

    @Test
    public void testArrayListRemove() {
        ArrayList<Integer> list = null;
        assertThat(ArrayUtils.remove(list, 1)).isNull();

        list = new ArrayList(Arrays.asList(1));
        assertThat(ArrayUtils.remove(list, 1)).isNull();

        list = new ArrayList(Arrays.asList(1, 2));
        assertThat(ArrayUtils.remove(list, 1)).isEqualTo(new ArrayList(Arrays.asList(2)));
    }

    @Test
    public void testColloectionContains() {
        assertThat(ArrayUtils.contains((ArrayList<Integer>) null, 1)).isFalse();
        assertThat(ArrayUtils.contains(new ArrayList(Arrays.asList(1, 2)), 2)).isTrue();
    }

    @Test
    public void testTrimToSize() {
        assertThat(ArrayUtils.trimToSize((Integer[]) null, 1)).isNull();
        assertThat(ArrayUtils.trimToSize(new Integer[]{1}, 1)).isEqualTo(new Integer[]{1});
        assertThat(ArrayUtils.trimToSize(new Integer[]{1, 2, 3}, 1)).isEqualTo(new Integer[]{1});
    }

    @Test
    public void testReferenceEquals() {
        ArrayList<String> array1 = new ArrayList<String>();
        ArrayList<String> array2 = new ArrayList<String>();

        assertThat(ArrayUtils.referenceEquals(array1, array2)).isTrue();
        assertThat(ArrayUtils.referenceEquals(array1, array1)).isTrue();

        String testString1 = new String("abcd");
        String testString2 = new String("abcd");
        array1.add(testString1);

        assertThat(ArrayUtils.referenceEquals(array1, array2)).isFalse();

        array2.add(testString2);

        assertThat(ArrayUtils.referenceEquals(array1, array2)).isFalse();

        array2.set(0, testString1);

        assertThat(ArrayUtils.referenceEquals(array1, array2)).isTrue();
    }

    @Test
    public void testUnstableRemoveIf() {
        ArrayList<Integer> array = new ArrayList(Arrays.asList(1, 2, 1, 3, 1, 4, 1));

        assertThat(ArrayUtils.unstableRemoveIf(array, (o) -> {
            return (Integer) o == 1;
        })).isEqualTo(4);
        assertThat(array).containsExactly(2, 3, 4);
    }

    @Test
    public void testDefeatNullable() {
        assertThat(ArrayUtils.defeatNullable((int[]) null)).isEqualTo(new int[0]);
        assertThat(ArrayUtils.defeatNullable(new int[]{1})).isEqualTo(new int[]{1});
        assertThat(ArrayUtils.defeatNullable((String[]) null)).isEqualTo(new String[0]);
        assertThat(ArrayUtils.defeatNullable(new String[]{"a"})).isEqualTo(new String[]{"a"});
        assertThat(ArrayUtils.defeatNullable((File[]) null)).isEqualTo(new File[0]);
        assertThat(ArrayUtils.defeatNullable(new File[0])).isEqualTo(new File[0]);
    }

    @Test
    public void testCheckBounds() {
        ArrayUtils.checkBounds(1, 0);
    }

    @Test
    public void testCheckBoundsNegative() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> ArrayUtils.checkBounds(1, -1));
    }

    @Test
    public void testCheckBoundsTooLarge() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> ArrayUtils.checkBounds(1, 1));
    }

    @Test
    public void testThrowsIfOutOfBounds() {
        ArrayUtils.throwsIfOutOfBounds(/* len= */ 2, /* offset= */ 1, /* count= */ 1);
    }

    @Test
    public void testThrowsIfOutOfBoundsNegativeLen() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> ArrayUtils.throwsIfOutOfBounds(/* len= */ -1, /* offset= */ 1,
                        /* count= */ 1));
    }

    @Test
    public void testThrowsIfOutOfBoundsNegativeOffset() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> ArrayUtils.throwsIfOutOfBounds(/* len= */ 2, /* offset= */ -1,
                        /* count= */ 1));
    }

    @Test
    public void testThrowsIfOutOfBoundsNegativeCount() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> ArrayUtils.throwsIfOutOfBounds(/* len= */ 2, /* offset= */ 1,
                        /* count= */ -1));
    }

    @Test
    public void testThrowsIfOutOfBoundsInvalidOffset() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> ArrayUtils.throwsIfOutOfBounds(/* len= */ 2, /* offset= */ 2,
                        /* count= */ 1));
    }

    @Test
    public void testThrowsIfOutOfBoundsInvalidCount() {
        assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> ArrayUtils.throwsIfOutOfBounds(/* len= */ 2, /* offset= */ 1,
                        /* count= */ 2));
    }

    @Test
    public void testFilterNotNull() {
        assertThat(ArrayUtils.filterNotNull(new Integer[]{1, 2}, Integer[]::new)).isEqualTo(
                new Integer[]{1, 2});
        assertThat(ArrayUtils.filterNotNull(new Integer[]{1, null, 2}, Integer[]::new)).isEqualTo(
                new Integer[]{1, 2});
    }

    @Test
    public void testFilter() {
        assertThat(ArrayUtils.filter(new Integer[]{1, 2}, Integer[]::new, (o) -> {
            return (Integer) o == 1;
        })).isEqualTo(new Integer[]{1});
        assertThat(ArrayUtils.filter(null, Integer[]::new, (o) -> {
            return (Integer) o == 1;
        })).isNull();
        assertThat(ArrayUtils.filter(new Integer[]{1, 2}, Integer[]::new, (o) -> {
            return (Integer) o == 1 || (Integer) o == 2;
        })).isEqualTo(new Integer[]{1, 2});
        assertThat(ArrayUtils.filter(new Integer[]{1, 2}, Integer[]::new, (o) -> {
            return (Integer) o == 3;
        })).isEqualTo(new Integer[0]);
    }

    @Test
    public void testStartsWith() {
        assertThat(ArrayUtils.startsWith(new byte[]{1, 2, 3}, new byte[]{1, 2})).isTrue();
        assertThat(ArrayUtils.startsWith(new byte[]{1, 2, 3}, new byte[]{2, 3})).isFalse();
        assertThat(ArrayUtils.startsWith(new byte[]{1, 2, 3}, new byte[]{1, 2, 3, 4})).isFalse();
        assertThat(ArrayUtils.startsWith(new byte[]{1, 2, 3}, null)).isFalse();
        assertThat(ArrayUtils.startsWith(null, new byte[]{1})).isFalse();
    }

    @Test
    public void testFind() {
        assertThat(ArrayUtils.find(new Integer[]{1, 2}, (o) -> {
            return (Integer) o == 1;
        })).isEqualTo(1);
        assertThat(ArrayUtils.find(new Integer[]{1, 2}, (o) -> {
            return (Integer) o == 3;
        })).isNull();
        assertThat(ArrayUtils.find((Integer[]) null, (o) -> {
            return (Integer) o == 1;
        })).isNull();
    }

    @Test
    public void testFirstOrNull() {
        assertThat(ArrayUtils.firstOrNull(new Integer[0])).isNull();
        assertThat(ArrayUtils.firstOrNull(new Integer[]{1, 2})).isEqualTo(1);
    }
}
