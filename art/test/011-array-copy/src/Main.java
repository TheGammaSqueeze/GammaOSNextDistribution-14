/*
 * Copyright (C) 2007 The Android Open Source Project
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

import java.util.Arrays;

/**
 * System.arraycopy cases
 */
public class Main {
    public static void main(String args[]) {
        testObjectCopy();
        testOverlappingMoves();
        testFloatAndDouble();
        testArrayCopyChar();
    }

    public static void testObjectCopy() {
        String[] stringArray = new String[8];
        Object[] objectArray = new Object[8];

        for (int i = 0; i < stringArray.length; i++)
            stringArray[i] = new String(Integer.toString(i));

        System.out.println("string -> object");
        System.arraycopy(stringArray, 0, objectArray, 0, stringArray.length);
        System.out.println("object -> string");
        System.arraycopy(objectArray, 0, stringArray, 0, stringArray.length);
        System.out.println("object -> string (modified)");
        objectArray[4] = new ImplA();
        try {
            System.arraycopy(objectArray, 0, stringArray, 0,stringArray.length);
        }
        catch (ArrayStoreException ase) {
            System.out.println("caught ArrayStoreException (expected)");
        }
    }

    static final int ARRAY_SIZE = 8;

    static void initByteArray(byte[] array) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = (byte) i;
        }
    }
    static void initShortArray(short[] array) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = (short) i;
        }
    }
    static void initIntArray(int[] array) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = (int) i;
        }
    }
    static void initLongArray(long[] array) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = (long) i;
        }
    }
    static void initCharArray(char[] array) {
        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i] = (char) i;
        }
    }

    /*
     * Perform an array copy operation on primitive arrays with different
     * element widths.
     */
    static void makeCopies(int srcPos, int dstPos, int length) {
        byte[] byteArray = new byte[ARRAY_SIZE];
        short[] shortArray = new short[ARRAY_SIZE];
        int[] intArray = new int[ARRAY_SIZE];
        long[] longArray = new long[ARRAY_SIZE];
        char[] charArray = new char[ARRAY_SIZE];

        initByteArray(byteArray);
        initShortArray(shortArray);
        initIntArray(intArray);
        initLongArray(longArray);
        initCharArray(charArray);

        System.arraycopy(byteArray, srcPos, byteArray, dstPos, length);
        System.arraycopy(shortArray, srcPos, shortArray, dstPos, length);
        System.arraycopy(intArray, srcPos, intArray, dstPos, length);
        System.arraycopy(longArray, srcPos, longArray, dstPos, length);
        System.arraycopy(charArray, srcPos, charArray, dstPos, length);

        for (int i = 0; i < ARRAY_SIZE; i++) {
            if (intArray[i] != byteArray[i]) {
                System.out.println("mismatch int vs byte at " + i + " : " +
                    Arrays.toString(byteArray));
                break;
            } else if (intArray[i] != shortArray[i]) {
                System.out.println("mismatch int vs short at " + i + " : " +
                    Arrays.toString(shortArray));
                break;
            } else if (intArray[i] != longArray[i]) {
                System.out.println("mismatch int vs long at " + i + " : " +
                    Arrays.toString(longArray));
                break;
            } else if (intArray[i] != charArray[i]) {
                System.out.println("mismatch int vs char at " + i + " : " +
                    Arrays.toString(charArray));
                break;
            }
        }

        System.out.println("copy: " + srcPos + "," + dstPos + "," + length +
            ": " + Arrays.toString(intArray));
    }

    public static void testOverlappingMoves() {
        /* do nothing */
        makeCopies(0, 0, 0);

        /* do more nothing */
        makeCopies(0, 0, ARRAY_SIZE);

        /* copy forward, even alignment */
        makeCopies(0, 2, 4);

        /* copy backward, even alignment */
        makeCopies(2, 0, 4);

        /* copy forward, odd alignment */
        makeCopies(1, 3, 4);

        /* copy backward, odd alignment */
        makeCopies(3, 1, 4);

        /* copy backward, odd length */
        makeCopies(3, 1, 5);

        /* copy forward, odd length */
        makeCopies(1, 3, 5);

        /* copy forward, mixed alignment */
        makeCopies(0, 3, 5);

        /* copy backward, mixed alignment */
        makeCopies(3, 0, 5);

        /* copy forward, mixed alignment, trivial length */
        makeCopies(0, 5, 1);
    }

    private static void testFloatAndDouble() {
        // Float & double copies have the same implementation as int & long. However, there are
        // protective DCHECKs in the code (there is nothing unifying like ByteSizedArray or
        // ShortSizedArray). Just test that we don't fail those checks.
        final int len = 10;
        System.arraycopy(new float[len], 0, new float[len], 0, len);
        System.arraycopy(new double[len], 0, new double[len], 0, len);
    }

    static final char SRC_INIT_CHAR = '1';
    static final char DST_CHAR = '0';

    /* Return a char array of the specified length.
     * If do_increment is true, populate the array with (numerically) ascending
     * characters starting from initChar (note: char wraps-around on overflow).
     * If do_increment is false, populate all array elements with initChar.
     */
    public static char[] createCharArray(int length, char initChar, boolean do_increment) {
        char[] charArr = new char[length];
        char nextChar = initChar;

        for (int i = 0; i < length; ++i) {
            charArr[i] = nextChar;
            if (do_increment) {
                nextChar++;
            }
        }
        return charArr;
    }

    public static boolean verifyCorrectness(char[] src, char[] dst, int copiedPrefixLength) {
        for (int i = 0; i < dst.length; ++i) {
            if (i < copiedPrefixLength) {
                // Check that we copied source array.
                if (dst[i] != src[i]) {
                    return false;
                }
            } else {
                // Check that we didn't write more chars than necessary.
                if (dst[i] != DST_CHAR) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void testArrayCopyCharConstCase2() {
        final int copy_length = 2;
        char[] src = createCharArray(2 * copy_length, SRC_INIT_CHAR, true);
        char[] dst = createCharArray(4 * copy_length, DST_CHAR, false);

        System.arraycopy(src, 0, dst, 0, copy_length);

        boolean passed = verifyCorrectness(src, dst, copy_length);
        if (!passed) {
            System.out.println("arraycopy(char) const case 2 failed");
        } else {
            System.out.println("arraycopy(char) const case 2 passed");
        }
    }

    public static void testArrayCopyCharConstCase3() {
        final int copy_length = 3;
        char[] src = createCharArray(2 * copy_length, SRC_INIT_CHAR, true);
        char[] dst = createCharArray(4 * copy_length, DST_CHAR, false);

        System.arraycopy(src, 0, dst, 0, copy_length);

        boolean passed = verifyCorrectness(src, dst, copy_length);
        if (!passed) {
            System.out.println("arraycopy(char) const case 3 failed");
        } else {
            System.out.println("arraycopy(char) const case 3 passed");
        }
    }

    public static void testArrayCopyCharConstCase5() {
        final int copy_length = 5;
        char[] src = createCharArray(2 * copy_length, SRC_INIT_CHAR, true);
        char[] dst = createCharArray(4 * copy_length, DST_CHAR, false);

        System.arraycopy(src, 0, dst, 0, copy_length);

        boolean passed = verifyCorrectness(src, dst, copy_length);
        if (!passed) {
            System.out.println("arraycopy(char) const case 5 failed");
        } else {
            System.out.println("arraycopy(char) const case 5 passed");
        }
    }

    public static void testArrayCopyCharConstCase7() {
        final int copy_length = 7;
        char[] src = createCharArray(2 * copy_length, SRC_INIT_CHAR, true);
        char[] dst = createCharArray(4 * copy_length, DST_CHAR, false);

        System.arraycopy(src, 0, dst, 0, copy_length);

        boolean passed = verifyCorrectness(src, dst, copy_length);
        if (!passed) {
            System.out.println("arraycopy(char) const case 7 failed");
        } else {
            System.out.println("arraycopy(char) const case 7 passed");
        }
    }

    public static void testArrayCopyCharConstCase8() {
        final int copy_length = 8;
        char[] src = createCharArray(2 * copy_length, SRC_INIT_CHAR, true);
        char[] dst = createCharArray(4 * copy_length, DST_CHAR, false);

        System.arraycopy(src, 0, dst, 0, copy_length);

        boolean passed = verifyCorrectness(src, dst, copy_length);
        if (!passed) {
            System.out.println("arraycopy(char) const case 8 failed");
        } else {
            System.out.println("arraycopy(char) const case 8 passed");
        }
    }

    public static void testArrayCopyCharConstCase9() {
        final int copy_length = 9;
        char[] src = createCharArray(2 * copy_length, SRC_INIT_CHAR, true);
        char[] dst = createCharArray(4 * copy_length, DST_CHAR, false);

        System.arraycopy(src, 0, dst, 0, copy_length);

        boolean passed = verifyCorrectness(src, dst, copy_length);
        if (!passed) {
            System.out.println("arraycopy(char) const case 9 failed");
        } else {
            System.out.println("arraycopy(char) const case 9 passed");
        }
    }

    public static void testArrayCopyCharConstCase11() {
        final int copy_length = 11;
        char[] src = createCharArray(2 * copy_length, SRC_INIT_CHAR, true);
        char[] dst = createCharArray(4 * copy_length, DST_CHAR, false);

        System.arraycopy(src, 0, dst, 0, copy_length);

        boolean passed = verifyCorrectness(src, dst, copy_length);
        if (!passed) {
            System.out.println("arraycopy(char) const case 11 failed");
        } else {
            System.out.println("arraycopy(char) const case 11 passed");
        }
    }

    public static void testArrayCopyCharCase(int copy_length) {
        char[] src = createCharArray(2 * copy_length, SRC_INIT_CHAR, true);
        char[] dst = createCharArray(4 * copy_length, DST_CHAR, false);

        System.arraycopy(src, 0, dst, 0, copy_length);

        boolean passed = verifyCorrectness(src, dst, copy_length);
        if (!passed) {
            System.out.println("arraycopy(char) " + copy_length + " failed");
        } else {
            System.out.println("arraycopy(char) " + copy_length + " passed");
        }
    }

    public static void testArrayCopyChar() {
        testArrayCopyCharConstCase2();
        testArrayCopyCharConstCase3();
        testArrayCopyCharConstCase5();
        testArrayCopyCharConstCase7();
        testArrayCopyCharConstCase8();
        testArrayCopyCharConstCase9();
        testArrayCopyCharConstCase11();
        testArrayCopyCharCase(0);
        testArrayCopyCharCase(1);
        testArrayCopyCharCase(3);
        testArrayCopyCharCase(4);
        testArrayCopyCharCase(5);
        testArrayCopyCharCase(7);
        testArrayCopyCharCase(15);
        testArrayCopyCharCase(16);
        testArrayCopyCharCase(17);
        testArrayCopyCharCase(31);
        testArrayCopyCharCase(32);
        testArrayCopyCharCase(33);
        testArrayCopyCharCase(63);
        testArrayCopyCharCase(64);
        testArrayCopyCharCase(65);
        testArrayCopyCharCase(255);
        testArrayCopyCharCase(513);
        testArrayCopyCharCase(1025);
    }

}
