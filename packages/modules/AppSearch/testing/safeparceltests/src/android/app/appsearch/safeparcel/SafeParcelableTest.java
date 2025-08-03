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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.collect.ImmutableSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Tests for safe parcels. */
@RunWith(AndroidJUnit4.class)
public class SafeParcelableTest {

    private static final int TEST_ID = 25;
    private Parcel mParcel;
    private int mWriteFinalPosition;

    @Before
    public void setUp() throws Exception {
        mParcel = Parcel.obtain();
        mParcel.setDataPosition(0);
    }

    @After
    public void tearDown() throws Exception {
        mParcel.recycle();
    }

    @Test
    public void testReadWriteByteArray() {
        byte[] buffer = new byte[] {0, 1, 2, 3, 4, 5, 6, 7};
        SafeParcelWriter.writeByteArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        byte[] readBuffer = SafeParcelReader.createByteArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(buffer.length, readBuffer.length);
        for (int i = 0; i < buffer.length; i++) {
            assertEquals(buffer[i], readBuffer[i]);
        }
    }

    @Test
    public void testReadWriteByteArrayNull() {
        SafeParcelWriter.writeByteArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        byte[] readBuffer = SafeParcelReader.createByteArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteByteArrayNullIgnore() {
        SafeParcelWriter.writeByteArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteByteArrayArray() {
        byte[][] buffer = new byte[3][];
        buffer[0] = new byte[] {0, 1, 2, 3};
        buffer[1] = new byte[] {4, 5};
        buffer[2] = new byte[] {6, 7, 8};
        SafeParcelWriter.writeByteArrayArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        byte[][] readBuffer = SafeParcelReader.createByteArrayArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(buffer.length, readBuffer.length);
        for (int i = 0; i < buffer.length; i++) {
            assertEquals(buffer[i].length, readBuffer[i].length);
            for (int j = 0; j < buffer[i].length; j++) {
                assertEquals(buffer[i][j], readBuffer[i][j]);
            }
        }
    }

    @Test
    public void testReadWriteByteArrayArrayNull() {
        SafeParcelWriter.writeByteArrayArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        byte[][] readBuffer = SafeParcelReader.createByteArrayArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteByteArrayArrayNullIgnore() {
        SafeParcelWriter.writeByteArrayArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteBoolean() {
        boolean val = true;
        SafeParcelWriter.writeBoolean(mParcel, TEST_ID, val);

        int header = prepareToReadFromParcel();
        boolean readVal = SafeParcelReader.readBoolean(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteBooleanObject() {
        Boolean val = true;
        SafeParcelWriter.writeBooleanObject(mParcel, TEST_ID, val, false);

        int header = prepareToReadFromParcel();
        Boolean readVal = SafeParcelReader.readBooleanObject(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteBooleanObjectNull() {
        SafeParcelWriter.writeBooleanObject(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        Boolean readBuffer = SafeParcelReader.readBooleanObject(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteBooleanObjectNullIgnore() {
        SafeParcelWriter.writeBooleanObject(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteByte() {
        byte val = 23;
        SafeParcelWriter.writeByte(mParcel, TEST_ID, val);

        int header = prepareToReadFromParcel();
        byte readVal = SafeParcelReader.readByte(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteChar() {
        char val = 'm';
        SafeParcelWriter.writeChar(mParcel, TEST_ID, val);

        int header = prepareToReadFromParcel();
        char readVal = SafeParcelReader.readChar(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteShort() {
        short val = 34;
        SafeParcelWriter.writeShort(mParcel, TEST_ID, val);

        int header = prepareToReadFromParcel();
        short readVal = SafeParcelReader.readShort(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteInt() {
        int val = 45;
        SafeParcelWriter.writeInt(mParcel, TEST_ID, val);

        int header = prepareToReadFromParcel();
        int readVal = SafeParcelReader.readInt(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteIntegerObject() {
        Integer val = 45;
        SafeParcelWriter.writeIntegerObject(mParcel, TEST_ID, val, false);

        int header = prepareToReadFromParcel();
        Integer readVal = SafeParcelReader.readIntegerObject(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteIntegerObjectNull() {
        SafeParcelWriter.writeIntegerObject(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        Integer readBuffer = SafeParcelReader.readIntegerObject(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteIntegerObjectNullIgnore() {
        SafeParcelWriter.writeIntegerObject(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteLong() {
        long val = 123456789;
        SafeParcelWriter.writeLong(mParcel, TEST_ID, val);

        int header = prepareToReadFromParcel();
        long readVal = SafeParcelReader.readLong(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteLongObject() {
        Long val = 123456789L;
        SafeParcelWriter.writeLongObject(mParcel, TEST_ID, val, false);

        int header = prepareToReadFromParcel();
        Long readVal = SafeParcelReader.readLongObject(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteLongObjectNull() {
        SafeParcelWriter.writeLongObject(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        Long readBuffer = SafeParcelReader.readLongObject(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteLongObjectNullIgnore() {
        SafeParcelWriter.writeLongObject(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteBigInteger() {
        BigInteger val = new BigInteger("99999999999999999999999999999999999999999999999999999999");
        SafeParcelWriter.writeBigInteger(mParcel, TEST_ID, val, false);

        int header = prepareToReadFromParcel();
        BigInteger readVal = SafeParcelReader.createBigInteger(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteBigIntegerNull() {
        SafeParcelWriter.writeBigInteger(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        BigInteger readBuffer = SafeParcelReader.createBigInteger(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteBigIntegerNullIgnore() {
        SafeParcelWriter.writeBigInteger(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteFloat() {
        float val = 12.3456f;
        SafeParcelWriter.writeFloat(mParcel, TEST_ID, val);

        int header = prepareToReadFromParcel();
        float readVal = SafeParcelReader.readFloat(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readVal).isEqualTo(val);
    }

    @Test
    public void testReadWriteFloatObject() {
        Float val = 12.3456f;
        SafeParcelWriter.writeFloatObject(mParcel, TEST_ID, val, false);

        int header = prepareToReadFromParcel();
        Float readVal = SafeParcelReader.readFloatObject(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readVal).isEqualTo(val);
    }

    @Test
    public void testReadWriteFloatObjectNull() {
        SafeParcelWriter.writeFloatObject(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        Float readBuffer = SafeParcelReader.readFloatObject(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteFloatObjectNullIgnore() {
        SafeParcelWriter.writeFloatObject(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteDouble() {
        double val = 12.3456;
        SafeParcelWriter.writeDouble(mParcel, TEST_ID, val);

        int header = prepareToReadFromParcel();
        double readVal = SafeParcelReader.readDouble(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readVal).isEqualTo(val);
    }

    @Test
    public void testReadWriteDoubleObject() {
        Double val = 12.3456;
        SafeParcelWriter.writeDoubleObject(mParcel, TEST_ID, val, false);

        int header = prepareToReadFromParcel();
        Double readVal = SafeParcelReader.readDouble(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readVal).isEqualTo(val);
    }

    @Test
    public void testReadWriteDoubleObjectNull() {
        SafeParcelWriter.writeDoubleObject(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        Double readBuffer = SafeParcelReader.readDoubleObject(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteDoubleObjectNullIgnore() {
        SafeParcelWriter.writeDoubleObject(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteBigDecimal() {
        BigDecimal val =
                new BigDecimal(
                        new BigInteger(
                                "9999999999999999999999999999999999999999999999999999999999999999"),
                        888888);
        SafeParcelWriter.writeBigDecimal(mParcel, TEST_ID, val, false);

        int header = prepareToReadFromParcel();
        BigDecimal readVal = SafeParcelReader.createBigDecimal(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteBigDecimalNull() {
        SafeParcelWriter.writeBigDecimal(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        BigDecimal readBuffer = SafeParcelReader.createBigDecimal(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteBigDecimalNullIgnore() {
        SafeParcelWriter.writeBigDecimal(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteString() {
        String val = "foo";
        SafeParcelWriter.writeString(mParcel, TEST_ID, val, false);

        int header = prepareToReadFromParcel();
        String readVal = SafeParcelReader.createString(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteStringNull() {
        SafeParcelWriter.writeString(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        String readBuffer = SafeParcelReader.createString(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteStringNullIgnore() {
        SafeParcelWriter.writeString(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteIBinder() {
        // TODO
    }

    @Test
    public void testReadWriteParcelable() {
        Parcelable val = new TestParcelable(12, 3.45f, "foo-bar");
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, val, 0, false);

        int header = prepareToReadFromParcel();
        TestParcelable readVal =
                SafeParcelReader.createParcelable(mParcel, header, TestParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
    }

    @Test
    public void testReadWriteParcelableNull() {
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, null, 0, true);

        int header = prepareToReadFromParcel();
        TestParcelable readBuffer =
                SafeParcelReader.createParcelable(mParcel, header, TestParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteParcelableNullIgnore() {
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, null, 0, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteSafeParcelable() {
        TestSafeParcelable val = new TestSafeParcelable("begin", "end");
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, val, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelable readVal =
                SafeParcelReader.createParcelable(mParcel, header, TestSafeParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val, readVal);
        assertEquals(val.stringField1, readVal.stringField1);
        assertEquals(val.stringField2, readVal.stringField2);
    }

    @Test
    public void testReadWriteSafeParcelableNull() {
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, null, 0, true);

        int header = prepareToReadFromParcel();
        TestSafeParcelable readBuffer =
                SafeParcelReader.createParcelable(mParcel, header, TestSafeParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteBundle() {
        Bundle val = new Bundle();
        val.putBoolean("bool", true);
        val.putInt("int", 123);
        val.putString("string", "foo");
        SafeParcelWriter.writeBundle(mParcel, TEST_ID, val, false);

        int header = prepareToReadFromParcel();
        Bundle readVal = SafeParcelReader.createBundle(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(val.size(), readVal.size());
        assertEquals(val.getBoolean("bool"), readVal.getBoolean("bool"));
        assertEquals(val.getInt("int"), readVal.getInt("int"));
        assertEquals(val.getString("string"), readVal.getString("string"));
    }

    @Test
    public void testReadWriteBundleNull() {
        SafeParcelWriter.writeBundle(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        Bundle readBuffer = SafeParcelReader.createBundle(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteBundleNullIgnore() {
        SafeParcelWriter.writeBundle(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteBooleanArray() {
        boolean[] buffer = new boolean[] {true, true, false, true, false};
        SafeParcelWriter.writeBooleanArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        boolean[] readBuffer = SafeParcelReader.createBooleanArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readBuffer).isEqualTo(buffer);
    }

    @Test
    public void testReadWriteBooleanArrayNull() {
        SafeParcelWriter.writeBooleanArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        boolean[] readBuffer = SafeParcelReader.createBooleanArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteBooleanArrayNullIgnore() {
        SafeParcelWriter.writeBooleanArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteCharArray() {
        char[] buffer = new char[] {'a', 'b', 'c', 'd', 'e'};
        SafeParcelWriter.writeCharArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        char[] readBuffer = SafeParcelReader.createCharArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readBuffer).isEqualTo(buffer);
    }

    @Test
    public void testReadWriteCharArrayNull() {
        SafeParcelWriter.writeCharArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        char[] readBuffer = SafeParcelReader.createCharArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteCharArrayNullIgnore() {
        SafeParcelWriter.writeCharArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteIntArray() {
        int[] buffer = new int[] {1, 2, 3, 4, 5};
        SafeParcelWriter.writeIntArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        int[] readBuffer = SafeParcelReader.createIntArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readBuffer).isEqualTo(buffer);
    }

    @Test
    public void testReadWriteIntArrayNull() {
        SafeParcelWriter.writeIntArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        int[] readBuffer = SafeParcelReader.createIntArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteIntArrayNullIgnore() {
        SafeParcelWriter.writeIntArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteLongArray() {
        long[] buffer = new long[] {1L, 2L, 3L, 4L, 5L};
        SafeParcelWriter.writeLongArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        long[] readBuffer = SafeParcelReader.createLongArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readBuffer).isEqualTo(buffer);
    }

    @Test
    public void testReadWriteLongArrayNull() {
        SafeParcelWriter.writeLongArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        long[] readBuffer = SafeParcelReader.createLongArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteLongArrayNullIgnore() {
        SafeParcelWriter.writeLongArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteBigIntegerArray() {
        BigInteger[] buffer =
                new BigInteger[] {new BigInteger("1"), new BigInteger("2"), new BigInteger("3")};
        SafeParcelWriter.writeBigIntegerArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        BigInteger[] readBuffer = SafeParcelReader.createBigIntegerArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readBuffer).isEqualTo(buffer);
    }

    @Test
    public void testReadWriteBigIntegerArrayNull() {
        SafeParcelWriter.writeBigIntegerArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        BigInteger[] readBuffer = SafeParcelReader.createBigIntegerArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteBigIntegerArrayNullIgnore() {
        SafeParcelWriter.writeBigIntegerArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteFloatArray() {
        float[] buffer = new float[] {1.1f, 2, 2f, 3.3f, 4.4f, 5.5f};
        SafeParcelWriter.writeFloatArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        float[] readBuffer = SafeParcelReader.createFloatArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readBuffer).isEqualTo(buffer);
    }

    @Test
    public void testReadWriteFloatArrayNull() {
        SafeParcelWriter.writeFloatArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        float[] readBuffer = SafeParcelReader.createFloatArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteFloatArrayNullIgnore() {
        SafeParcelWriter.writeFloatArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteDoubleArray() {
        double[] buffer = new double[] {1.1, 2.2, 3.3, 4.4, 5.5};
        SafeParcelWriter.writeDoubleArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        double[] readBuffer = SafeParcelReader.createDoubleArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readBuffer).isEqualTo(buffer);
    }

    @Test
    public void testReadWriteDoubleArrayNull() {
        SafeParcelWriter.writeDoubleArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        double[] readBuffer = SafeParcelReader.createDoubleArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteDoubleArrayNullIgnore() {
        SafeParcelWriter.writeDoubleArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteBigDecimalArray() {
        BigDecimal[] buffer =
                new BigDecimal[] {
                    new BigDecimal(new BigInteger("1"), 1000),
                    new BigDecimal(new BigInteger("2"), 2000),
                    new BigDecimal(new BigInteger("3"), 3000)
                };
        SafeParcelWriter.writeBigDecimalArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        BigDecimal[] readBuffer = SafeParcelReader.createBigDecimalArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readBuffer).isEqualTo(buffer);
    }

    @Test
    public void testReadWriteBigDecimalArrayNull() {
        SafeParcelWriter.writeBigDecimalArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        BigDecimal[] readBuffer = SafeParcelReader.createBigDecimalArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteBigDecimalArrayNullIgnore() {
        SafeParcelWriter.writeBigDecimalArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteStringArray() {
        String[] buffer = new String[] {"abc", "def", "ghi", "jkl", "mnopqrst"};
        SafeParcelWriter.writeStringArray(mParcel, TEST_ID, buffer, false);

        int header = prepareToReadFromParcel();
        String[] readBuffer = SafeParcelReader.createStringArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readBuffer).isEqualTo(buffer);
    }

    @Test
    public void testReadWriteStringArrayNull() {
        SafeParcelWriter.writeStringArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        String[] readBuffer = SafeParcelReader.createStringArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteStringArrayNullIgnore() {
        SafeParcelWriter.writeStringArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteIBinderArrayArray() {
        // TODO
    }

    @Test
    public void testReadWriteBooleanList() {
        List<Boolean> list = new ArrayList<Boolean>();
        list.add(true);
        list.add(false);
        list.add(false);
        list.add(true);
        SafeParcelWriter.writeBooleanList(mParcel, TEST_ID, list, false);

        int header = prepareToReadFromParcel();
        List<Boolean> readList = SafeParcelReader.createBooleanList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readList).isEqualTo(list);
    }

    @Test
    public void testReadWriteBooleanListNull() {
        SafeParcelWriter.writeBooleanList(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        List<Boolean> readBuffer = SafeParcelReader.createBooleanList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteBooleanListNullIgnore() {
        SafeParcelWriter.writeBooleanList(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteIntegerList() {
        List<Integer> list = new ArrayList<Integer>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);
        SafeParcelWriter.writeIntegerList(mParcel, TEST_ID, list, false);

        int header = prepareToReadFromParcel();
        List<Integer> readList = SafeParcelReader.createIntegerList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readList).isEqualTo(list);
    }

    @Test
    public void testReadWriteIntegerListNull() {
        SafeParcelWriter.writeIntegerList(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        List<Integer> readBuffer = SafeParcelReader.createIntegerList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteIntegerListNullIgnore() {
        SafeParcelWriter.writeIntegerList(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteLongList() {
        List<Long> list = new ArrayList<Long>();
        list.add(1L);
        list.add(2L);
        list.add(3L);
        list.add(4L);
        SafeParcelWriter.writeLongList(mParcel, TEST_ID, list, false);

        int header = prepareToReadFromParcel();
        List<Long> readList = SafeParcelReader.createLongList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readList).isEqualTo(list);
    }

    @Test
    public void testReadWriteLongListNull() {
        SafeParcelWriter.writeLongList(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        List<Long> readBuffer = SafeParcelReader.createLongList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteLongListNullIgnore() {
        SafeParcelWriter.writeLongList(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteFloatList() {
        List<Float> list = new ArrayList<Float>();
        list.add(1.1f);
        list.add(2.2f);
        list.add(3.3f);
        list.add(4.4f);
        SafeParcelWriter.writeFloatList(mParcel, TEST_ID, list, false);

        int header = prepareToReadFromParcel();
        List<Float> readList = SafeParcelReader.createFloatList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readList).isEqualTo(list);
    }

    @Test
    public void testReadWriteFloatListNull() {
        SafeParcelWriter.writeFloatList(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        List<Float> readBuffer = SafeParcelReader.createFloatList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteFloatListNullIgnore() {
        SafeParcelWriter.writeFloatList(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteDoubleList() {
        List<Double> list = new ArrayList<Double>();
        list.add(.1);
        list.add(.2);
        list.add(.3);
        list.add(.4);
        SafeParcelWriter.writeDoubleList(mParcel, TEST_ID, list, false);

        int header = prepareToReadFromParcel();
        List<Double> readList = SafeParcelReader.createDoubleList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readList).isEqualTo(list);
    }

    @Test
    public void testReadWriteDoubleListNull() {
        SafeParcelWriter.writeDoubleList(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        List<Double> readBuffer = SafeParcelReader.createDoubleList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteDoubleListNullIgnore() {
        SafeParcelWriter.writeDoubleList(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteStringList() {
        List<String> list = new ArrayList<String>();
        list.add("abc");
        list.add("def");
        list.add("ghijklm");
        list.add("nopqrstuv");
        SafeParcelWriter.writeStringList(mParcel, TEST_ID, list, false);

        int header = prepareToReadFromParcel();
        List<String> readList = SafeParcelReader.createStringList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readList).isEqualTo(list);
    }

    @Test
    public void testReadWriteStringListNull() {
        SafeParcelWriter.writeStringList(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        List<String> readBuffer = SafeParcelReader.createStringList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteStringListNullIgnore() {
        SafeParcelWriter.writeStringList(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteParcel() {
        char[] charArray = {'a', 'b', 'c', 'd', 'e'};
        Parcel parcel = Parcel.obtain();
        parcel.writeCharArray(charArray);
        SafeParcelWriter.writeParcel(mParcel, TEST_ID, parcel, false);

        int header = prepareToReadFromParcel();
        Parcel readParcel = SafeParcelReader.createParcel(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(parcel.dataSize(), readParcel.dataSize());
        readParcel.setDataPosition(0);
        char[] readCharArray = readParcel.createCharArray();
        assertThat(readCharArray).isEqualTo(charArray);
    }

    @Test
    public void testReadWriteParcelNull() {
        SafeParcelWriter.writeParcel(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        Parcel readBuffer = SafeParcelReader.createParcel(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteParcelNullIgnore() {
        SafeParcelWriter.writeParcel(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteParcelArray() {
        Parcel[] parcelArray = new Parcel[3];
        String[] stringArray = new String[] {"parcel1", "parcel2", "parcel3"};
        for (int i = 0; i < parcelArray.length; i++) {
            parcelArray[i] = Parcel.obtain();
            parcelArray[i].writeString(stringArray[i]);
        }
        SafeParcelWriter.writeParcelArray(mParcel, TEST_ID, parcelArray, false);

        int header = prepareToReadFromParcel();
        Parcel[] readParcelArray = SafeParcelReader.createParcelArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(parcelArray.length, readParcelArray.length);
        for (int i = 0; i < parcelArray.length; i++) {
            Parcel readParcel = readParcelArray[i];
            readParcel.setDataPosition(0);
            assertThat(readParcel.readString()).isEqualTo(stringArray[i]);
        }
    }

    @Test
    public void testReadWriteParcelArrayNull() {
        SafeParcelWriter.writeParcelArray(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        Parcel[] readBuffer = SafeParcelReader.createParcelArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteParcelArrayNullIgnore() {
        SafeParcelWriter.writeParcelArray(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteParcelList() {
        List<Parcel> list = new ArrayList<Parcel>();
        String[] strings = new String[] {"one", "two", "three"};
        Parcel p = Parcel.obtain();
        p.writeString(strings[0]);
        list.add(p);
        p = Parcel.obtain();
        p.writeString(strings[1]);
        list.add(p);
        p = Parcel.obtain();
        p.writeString(strings[2]);
        list.add(p);
        SafeParcelWriter.writeParcelList(mParcel, TEST_ID, list, false);

        int header = prepareToReadFromParcel();
        List<Parcel> readList = SafeParcelReader.createParcelList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(list.size(), readList.size());
        for (int i = 0; i < list.size(); i++) {
            Parcel readParcel = readList.get(i);
            readParcel.setDataPosition(0);
            assertThat(readParcel.readString()).isEqualTo(strings[i]);
        }
    }

    @Test
    public void testReadWriteParcelListNull() {
        SafeParcelWriter.writeParcelList(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        List<Parcel> readBuffer = SafeParcelReader.createParcelList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteParcelListNullIgnore() {
        SafeParcelWriter.writeParcelList(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteIBinderList() {
        // TODO
    }

    @Test
    public void testReadWriteTypedArray() {
        TestParcelable[] array = new TestParcelable[3];
        array[0] = new TestParcelable(1, 1.1f, "one");
        array[1] = new TestParcelable(2, 2.2f, "two");
        array[2] = new TestParcelable(3, 3.3f, "three");
        SafeParcelWriter.writeTypedArray(mParcel, TEST_ID, array, 0, false);

        int header = prepareToReadFromParcel();
        TestParcelable[] readArray =
                SafeParcelReader.createTypedArray(mParcel, header, TestParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readArray).isEqualTo(array);
    }

    @Test
    public void testReadWriteTypedArrayNull() {
        SafeParcelWriter.writeTypedArray(mParcel, TEST_ID, null, 0, true);

        int header = prepareToReadFromParcel();
        TestParcelable[] readBuffer =
                SafeParcelReader.createTypedArray(mParcel, header, TestParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteTypedArrayNullIgnore() {
        SafeParcelWriter.writeTypedArray(mParcel, TEST_ID, null, 0, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteTypedList() {
        List<TestParcelable> list = new ArrayList<TestParcelable>();
        list.add(new TestParcelable(1, 1.1f, "one"));
        list.add(new TestParcelable(2, 2.2f, "two"));
        list.add(new TestParcelable(3, 3.3f, "three"));
        SafeParcelWriter.writeTypedList(mParcel, TEST_ID, list, false);

        int header = prepareToReadFromParcel();
        List<TestParcelable> readList =
                SafeParcelReader.createTypedList(mParcel, header, TestParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readList).isEqualTo(list);
    }

    @Test
    public void testReadWriteTypedListNull() {
        SafeParcelWriter.writeTypedList(mParcel, TEST_ID, null, true);

        int header = prepareToReadFromParcel();
        List<TestParcelable> readBuffer =
                SafeParcelReader.createTypedList(mParcel, header, TestParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertNull(readBuffer);
    }

    @Test
    public void testReadWriteTypedListNullIgnore() {
        SafeParcelWriter.writeTypedList(mParcel, TEST_ID, null, false);
        assertEquals(0, mParcel.dataPosition());
    }

    @Test
    public void testReadWriteBetweenTypedArrayAndParcelArray() {
        TestSafeParcelable[] array =
                new TestSafeParcelable[] {
                    new TestSafeParcelable("foo", "bar"),
                    new TestSafeParcelable("one", "two"),
                    new TestSafeParcelable("fantastic", "four"),
                };
        SafeParcelWriter.writeTypedArray(mParcel, TEST_ID, array, 0, false);

        int header = prepareToReadFromParcel();
        Parcel[] readArray = SafeParcelReader.createParcelArray(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(array.length, readArray.length);
        for (int i = 0; i < array.length; i++) {
            readArray[i].setDataPosition(0);
            TestSafeParcelable object = TestSafeParcelable.CREATOR.createFromParcel(readArray[i]);
            assertEquals(array[i], object);
        }

        mParcel.recycle();
        mParcel = Parcel.obtain();
        mParcel.setDataPosition(0);

        SafeParcelWriter.writeParcelArray(mParcel, TEST_ID, readArray, false);

        header = prepareToReadFromParcel();
        TestSafeParcelable[] readTypedArray =
                SafeParcelReader.createTypedArray(mParcel, header, TestSafeParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readTypedArray).isEqualTo(array);
    }

    @Test
    public void testReadWriteBetweenTypedListAndParcelList() {
        List<TestSafeParcelable> list = new ArrayList<TestSafeParcelable>();
        list.add(new TestSafeParcelable("foo", "bar"));
        list.add(new TestSafeParcelable("one", "two"));
        list.add(new TestSafeParcelable("fantastic", "four"));
        SafeParcelWriter.writeTypedList(mParcel, TEST_ID, list, false);

        int header = prepareToReadFromParcel();
        List<Parcel> readList = SafeParcelReader.createParcelList(mParcel, header);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(list.size(), readList.size());
        for (int i = 0; i < list.size(); i++) {
            readList.get(i).setDataPosition(0);
            TestSafeParcelable object =
                    TestSafeParcelable.CREATOR.createFromParcel(readList.get(i));
            assertEquals(list.get(i), object);
        }

        mParcel.recycle();
        mParcel = Parcel.obtain();
        mParcel.setDataPosition(0);

        SafeParcelWriter.writeParcelList(mParcel, TEST_ID, readList, false);

        header = prepareToReadFromParcel();
        List<TestSafeParcelable> readTypedList =
                SafeParcelReader.createTypedList(mParcel, header, TestSafeParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readTypedList).isEqualTo(list);
    }

    @Test
    public void testNewReadingOldCompatibility() {
        TestSafeParcelable p1 = new TestSafeParcelable("begin", "end");
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, p1, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelableV2 p2 =
                SafeParcelReader.createParcelable(mParcel, header, TestSafeParcelableV2.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(p1.stringField1, p2.string1);
        assertEquals(p1.stringField2, p2.string2);
        assertNull(p2.string3);
        assertEquals(p1.getVersionCode(), p2.getVersionCode());
    }

    @Test
    public void testOldReadingNewCompatibility() {
        TestSafeParcelableV2 p2 = new TestSafeParcelableV2("begin", "end", "middle");
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, p2, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelable p1 =
                SafeParcelReader.createParcelable(mParcel, header, TestSafeParcelable.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(p1.stringField1, p2.string1);
        assertEquals(p1.stringField2, p2.string2);
        assertEquals(p1.getVersionCode(), p2.getVersionCode());
    }

    @Test
    public void testSafeParcelableFields() {
        TestSafeParcelableV2 p2 = new TestSafeParcelableV2();
        p2.string1 = "string1";
        p2.string2 = "string2";
        p2.string3 = "string3";
        p2.byteArray = new byte[] {0, 1, 2, 3, 4, 5, 6, 7};
        p2.booleanVar = false;
        p2.byteVar = 123;
        p2.charVar = 'k';
        p2.shortVar = 45;
        p2.intVar = 6789;
        p2.longVar = 987654321;
        p2.floatVar = 987.1234f;
        p2.doubleVar = 1234.567245;
        p2.parcelableVar = new TestSafeParcelable("foo", "bar");
        p2.bundleVar = new Bundle();
        p2.bundleVar.putString("key", "value");
        p2.charArray = new char[] {'a', 'b', 'c', 'd', 'e'};
        p2.intArray = new int[] {8, 9, 10, 11};
        p2.longArray = new long[] {12L, 13L, 14L};
        p2.floatArray = new float[] {15.0f, 124.24f};
        p2.doubleArray = new double[] {.001, .002, .003};
        p2.stringArray = new String[] {"what", "a", "wonderful", "test"};
        p2.stringList = new ArrayList<String>();
        p2.stringList.add("this");
        p2.stringList.add("is");
        p2.stringList.add("a");
        p2.stringList.add("list");
        p2.parcelableArray = new TestSafeParcelable[3];
        p2.parcelableArray[0] = new TestSafeParcelable("zero", "zero");
        p2.parcelableArray[1] = new TestSafeParcelable("one", "one");
        p2.parcelableArray[2] = new TestSafeParcelable("two", "two");
        p2.parcelableList = new ArrayList<TestSafeParcelable>();
        p2.parcelableList.add(new TestSafeParcelable("aaa", "aaa"));
        p2.parcelableList.add(new TestSafeParcelable("bbb", "bbb"));
        p2.parcelableList.add(new TestSafeParcelable("bbb", "bbb"));
        p2.setPackageString("private");
        p2.setPackageInt(-125);
        p2.contentValues = new ContentValues();
        p2.contentValues.put("foo", true);
        p2.contentValues.put("bar", "string");
        p2.parcel = Parcel.obtain();
        p2.parcel.writeString("parcelFooBar");
        p2.byteArrayArray = new byte[3][];
        p2.byteArrayArray[0] = new byte[] {0, 1};
        p2.byteArrayArray[1] = new byte[] {2, 3, 4};
        p2.byteArrayArray[2] = new byte[] {5, 6, 7, 8};
        p2.bigInteger = new BigInteger("1");
        p2.bigDecimal = new BigDecimal("1.3");
        p2.bigIntegerArray = new BigInteger[] {new BigInteger("2"), new BigInteger("3")};
        p2.bigDecimalArray = new BigDecimal[] {new BigDecimal("4.5"), new BigDecimal("5.5")};
        p2.booleanArray = new boolean[] {true, false, true};
        p2.parcelList = new ArrayList<Parcel>();
        Parcel parcel = Parcel.obtain();
        parcel.writeString("foo");
        p2.parcelList.add(parcel);
        parcel = Parcel.obtain();
        parcel.writeString("bar");
        p2.parcelList.add(parcel);
        p2.integerObject = 2345;
        p2.booleanObject = true;
        p2.longObject = 109745L;
        p2.floatObject = 1.23456f;
        p2.doubleObject = 1234.594397234;
        p2.booleanList = new ArrayList<Boolean>();
        p2.booleanList.add(true);
        p2.booleanList.add(false);
        p2.integerList = new ArrayList<Integer>();
        p2.integerList.add(1);
        p2.integerList.add(2);
        p2.longList = new ArrayList<Long>();
        p2.longList.add(1L);
        p2.longList.add(2L);
        p2.floatList = new ArrayList<Float>();
        p2.floatList.add(1f);
        p2.floatList.add(2f);
        p2.doubleList = new ArrayList<Double>();
        p2.doubleList.add(.1);
        p2.doubleList.add(.2);
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, p2, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelableV2 readP2 =
                SafeParcelReader.createParcelable(mParcel, header, TestSafeParcelableV2.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertThat(readP2.string1).isEqualTo(p2.string1);
        assertThat(readP2.string2).isEqualTo(p2.string2);
        assertThat(readP2.string3).isEqualTo(p2.string3);
        assertThat(readP2.byteArray).isEqualTo(p2.byteArray);
        assertThat(readP2.booleanVar).isEqualTo(p2.booleanVar);
        assertThat(readP2.byteVar).isEqualTo(p2.byteVar);
        assertThat(readP2.charVar).isEqualTo(p2.charVar);
        assertThat(readP2.shortVar).isEqualTo(p2.shortVar);
        assertThat(readP2.intVar).isEqualTo(p2.intVar);
        assertThat(readP2.longVar).isEqualTo(p2.longVar);
        assertThat(readP2.floatVar).isEqualTo(p2.floatVar);
        assertThat(readP2.doubleVar).isEqualTo(p2.doubleVar);
        assertThat(readP2.parcelableVar).isEqualTo(p2.parcelableVar);
        assertEquals(p2.bundleVar.size(), readP2.bundleVar.size());
        for (String key : p2.bundleVar.keySet()) {
            assertEquals(p2.bundleVar.get(key), readP2.bundleVar.get(key));
        }
        assertThat(readP2.charArray).isEqualTo(p2.charArray);
        assertThat(readP2.intArray).isEqualTo(p2.intArray);
        assertThat(readP2.longArray).isEqualTo(p2.longArray);
        assertThat(readP2.floatArray).isEqualTo(p2.floatArray);
        assertThat(readP2.doubleArray).isEqualTo(p2.doubleArray);
        assertThat(readP2.stringArray).isEqualTo(p2.stringArray);
        assertThat(readP2.stringList).isEqualTo(p2.stringList);
        assertThat(readP2.parcelableArray).isEqualTo(p2.parcelableArray);
        assertThat(readP2.parcelableList).isEqualTo(p2.parcelableList);
        assertEquals(p2.getPackageString(), readP2.getPackageString());
        assertEquals(p2.getPackageInt(), readP2.getPackageInt());
        assertEquals(p2.contentValues, readP2.contentValues);
        assertEquals(p2.parcel.dataSize(), readP2.parcel.dataSize());
        p2.parcel.setDataPosition(0);
        readP2.parcel.setDataPosition(0);
        assertEquals(p2.parcel.readString(), readP2.parcel.readString());
        assertThat(readP2.byteArrayArray).isEqualTo(p2.byteArrayArray);
        assertThat(readP2.bigInteger).isEqualTo(p2.bigInteger);
        assertThat(readP2.bigDecimal).isEqualTo(p2.bigDecimal);
        assertThat(readP2.bigIntegerArray).isEqualTo(p2.bigIntegerArray);
        assertThat(readP2.bigDecimalArray).isEqualTo(p2.bigDecimalArray);
        assertThat(readP2.booleanArray).isEqualTo(p2.booleanArray);
        assertEquals(p2.parcelList.size(), readP2.parcelList.size());
        for (int i = 0; i < p2.parcelList.size(); i++) {
            parcel = p2.parcelList.get(i);
            parcel.setDataPosition(0);
            Parcel readParcel = readP2.parcelList.get(i);
            readParcel.setDataPosition(0);
            assertThat(readParcel.readString()).isEqualTo(parcel.readString());
        }
        assertThat(readP2.integerObject).isEqualTo(p2.integerObject);
        assertThat(readP2.booleanObject).isEqualTo(p2.booleanObject);
        assertThat(readP2.longObject).isEqualTo(p2.longObject);
        assertThat(readP2.floatObject).isEqualTo(p2.floatObject);
        assertThat(readP2.doubleObject).isEqualTo(p2.doubleObject);
        assertThat(readP2.booleanList).isEqualTo(p2.booleanList);
        assertThat(readP2.integerList).isEqualTo(p2.integerList);
        assertThat(readP2.longList).isEqualTo(p2.longList);
        assertThat(readP2.floatList).isEqualTo(p2.floatList);
        assertThat(readP2.doubleList).isEqualTo(p2.doubleList);
    }

    @Test
    public void testSafeParcelableWithPrivateProtectedFinal() {
        TestSafeParcelableV3 p =
                new TestSafeParcelableV3(
                        "publicString",
                        "protectedString",
                        "privateString",
                        "packageString",
                        "publicFinalString",
                        "protectedFinalString",
                        "privateFinalString",
                        "packageFinalString");
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, p, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelableV3 readP =
                SafeParcelReader.createParcelable(mParcel, header, TestSafeParcelableV3.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(p, readP);
    }

    @Test
    public void testSafeParcelableWithIndicator() {
        TestSafeParcelableWithIndicator p = new TestSafeParcelableWithIndicator();
        p.string1 = "string1";
        p.int1 = 1;
        p.string2 = "string2";
        p.int2 = 2;
        p.getIndicator().add(2);
        p.getIndicator().add(5);
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, p, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelableWithIndicator readP =
                SafeParcelReader.createParcelable(
                        mParcel, header, TestSafeParcelableWithIndicator.CREATOR);
        validateParcelLengthAndFieldId(TEST_ID);

        assertEquals(2, readP.getIndicator().size());
        assertTrue(readP.getIndicator().contains(2));
        assertTrue(readP.getIndicator().contains(5));
        assertEquals(p.string1, readP.string1);
        assertEquals(0, readP.int1);
        assertNull(readP.string2);
        assertEquals(p.int2, readP.int2);
    }

    @Test
    public void testSafeParcelableWithRemovedField() {
        TestSafeParcelableWithoutRemovedParam p = new TestSafeParcelableWithoutRemovedParam("5");
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, p, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelableWithRemovedParam readP =
                SafeParcelReader.createParcelable(
                        mParcel, header, TestSafeParcelableWithRemovedParam.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals(5, readP.newField);

        // verify old field is not written by reading in as a TestSafeParcelableWithoutRemovedParam

        mParcel.setDataPosition(0);
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, readP, 0, false);

        header = prepareToReadFromParcel();
        TestSafeParcelableWithoutRemovedParam readOldP =
                SafeParcelReader.createParcelable(
                        mParcel, header, TestSafeParcelableWithoutRemovedParam.CREATOR);

        validateParcelLengthAndFieldId(TEST_ID);
        assertEquals("1", readOldP.oldField);
    }

    @Test
    public void testSkipUnknownField() {
        byte[] byteArray = new byte[5];

        Parcel parcel = Parcel.obtain();
        int start = SafeParcelWriter.beginObjectHeader(parcel);
        SafeParcelWriter.writeByteArray(parcel, 1, byteArray, false);
        SafeParcelWriter.finishObjectHeader(parcel, start);
        parcel.setDataPosition(0);

        int end = SafeParcelReader.validateObjectHeader(parcel);
        int header = SafeParcelReader.readHeader(parcel);
        int id = SafeParcelReader.getFieldId(header);
        assertEquals(1, id);
        SafeParcelReader.skipUnknownField(parcel, header);
        assertEquals(end, parcel.dataPosition());
    }

    @Test
    public void writeToParcel_doNotParcelTypeDefaultValues_noValues() {
        TestSafeParcelableWithDoNotParcelTypeDefaultValues parcelable =
                new TestSafeParcelableWithDoNotParcelTypeDefaultValues();

        Parcel parcel = Parcel.obtain();
        parcelable.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        int end = SafeParcelReader.validateObjectHeader(parcel);
        int start = parcel.dataPosition();
        assertThat(end - start).isEqualTo(0);
        assertThat(parcel.dataPosition()).isEqualTo(end);
    }

    @Test
    public void writeToParcel_doNotParcelTypeDefaultValues_changedValueParceled() {
        TestSafeParcelableWithDoNotParcelTypeDefaultValues parcelable =
                new TestSafeParcelableWithDoNotParcelTypeDefaultValues();
        parcelable.boolean1 = true;
        parcelable.int1 = 1234;
        parcelable.char1 = 'a';
        parcelable.byte1 = 127;
        parcelable.short1 = 42;
        parcelable.long1 = 1234567890L;
        parcelable.float1 = 1.0f;
        parcelable.double1 = 1.0;

        Parcel parcel = Parcel.obtain();
        parcelable.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        int end = SafeParcelReader.validateObjectHeader(parcel);
        int start = parcel.dataPosition();
        // 6 * 2 * 4byte + 2 * (4byte + 8byte) = 72
        assertThat(end - start).isEqualTo(72);
        assertThat(SafeParcelReader.getFieldId(parcel.readInt())).isEqualTo(1);
        assertThat(parcel.readInt()).isEqualTo(1);
        assertThat(SafeParcelReader.getFieldId(parcel.readInt())).isEqualTo(3);
        assertThat(parcel.readInt()).isEqualTo(1234);
        assertThat(SafeParcelReader.getFieldId(parcel.readInt())).isEqualTo(4);
        assertThat((char) parcel.readInt()).isEqualTo('a');
        assertThat(SafeParcelReader.getFieldId(parcel.readInt())).isEqualTo(5);
        assertThat(parcel.readInt()).isEqualTo(127);
        assertThat(SafeParcelReader.getFieldId(parcel.readInt())).isEqualTo(6);
        assertThat(parcel.readInt()).isEqualTo(42);
        assertThat(SafeParcelReader.getFieldId(parcel.readInt())).isEqualTo(7);
        assertThat(parcel.readLong()).isEqualTo(1234567890);
        assertThat(SafeParcelReader.getFieldId(parcel.readInt())).isEqualTo(8);
        assertThat(parcel.readFloat()).isEqualTo(1.0f);
        assertThat(SafeParcelReader.getFieldId(parcel.readInt())).isEqualTo(9);
        assertThat(parcel.readDouble()).isEqualTo(1.0);
        assertThat(parcel.dataPosition()).isEqualTo(end);
    }

    @Test
    public void convertIndicatorSetToDoNotParcelTypeDefaultValues() {
        TestSafeParcelableWithParcelTypeDefaultValues parcelable =
                new TestSafeParcelableWithParcelTypeDefaultValues();
        parcelable.boolean1 = true;
        parcelable.int1 = 1234;
        parcelable.char1 = 'a';
        parcelable.byte1 = 127;
        parcelable.short1 = 42;
        parcelable.long1 = 1234567890L;
        parcelable.float1 = 1.0f;
        parcelable.double1 = 1.0;
        parcelable.getIndicator().addAll(ImmutableSet.of(1, 3, 4, 5, 6, 7, 8, 9));
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, parcelable, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelableWithDoNotParcelTypeDefaultValues readP =
                SafeParcelReader.createParcelable(
                        mParcel,
                        header,
                        TestSafeParcelableWithDoNotParcelTypeDefaultValues.CREATOR);

        assertThat(readP.boolean1).isEqualTo(parcelable.boolean1);
        assertThat(readP.string1).isEqualTo(parcelable.string1);
        assertThat(readP.int1).isEqualTo(parcelable.int1);
        assertThat(readP.char1).isEqualTo(parcelable.char1);
        assertThat(readP.byte1).isEqualTo(parcelable.byte1);
        assertThat(readP.short1).isEqualTo(parcelable.short1);
        assertThat(readP.long1).isEqualTo(parcelable.long1);
        assertThat(readP.float1).isEqualTo(parcelable.float1);
        assertThat(readP.double1).isEqualTo(parcelable.double1);
        assertThat(readP.array1).isEqualTo(parcelable.array1);
    }

    @Test
    public void convertDoNotParcelTypeDefaultValuesToIndicatorSet() {
        TestSafeParcelableWithDoNotParcelTypeDefaultValues parcelable =
                new TestSafeParcelableWithDoNotParcelTypeDefaultValues();
        parcelable.boolean1 = true;
        parcelable.int1 = 1234;
        parcelable.char1 = 'a';
        parcelable.byte1 = 127;
        parcelable.short1 = 42;
        parcelable.long1 = 1234567890L;
        parcelable.float1 = 1.0f;
        parcelable.double1 = 1.0;
        SafeParcelWriter.writeParcelable(mParcel, TEST_ID, parcelable, 0, false);

        int header = prepareToReadFromParcel();
        TestSafeParcelableWithParcelTypeDefaultValues readP =
                SafeParcelReader.createParcelable(
                        mParcel, header, TestSafeParcelableWithParcelTypeDefaultValues.CREATOR);

        assertThat(readP.getIndicator()).isEqualTo(ImmutableSet.of(1, 3, 4, 5, 6, 7, 8, 9));
        assertThat(readP.boolean1).isEqualTo(parcelable.boolean1);
        assertThat(readP.string1).isEqualTo(parcelable.string1);
        assertThat(readP.int1).isEqualTo(parcelable.int1);
        assertThat(readP.char1).isEqualTo(parcelable.char1);
        assertThat(readP.byte1).isEqualTo(parcelable.byte1);
        assertThat(readP.short1).isEqualTo(parcelable.short1);
        assertThat(readP.long1).isEqualTo(parcelable.long1);
        assertThat(readP.float1).isEqualTo(parcelable.float1);
        assertThat(readP.double1).isEqualTo(parcelable.double1);
        assertThat(readP.array1).isEqualTo(parcelable.array1);
    }

    /**
     * This records the data position of mParcel and sets the position to 0 for reading. Also, this
     * reads in the header and fieldId from the parcel.
     */
    private int prepareToReadFromParcel() {
        mWriteFinalPosition = mParcel.dataPosition();
        mParcel.setDataPosition(0);

        return SafeParcelReader.readHeader(mParcel);
    }

    private void validateParcelLengthAndFieldId(int fieldId) {
        assertThat(fieldId).isEqualTo(TEST_ID);
        assertThat(mParcel.dataPosition()).isEqualTo(mWriteFinalPosition);
    }
}
