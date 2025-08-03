/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.os.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static java.util.Collections.singletonList;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.platform.test.annotations.IgnoreUnderRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;

public class BundleTest {
    @Rule public RavenwoodRule mRavenwood = new RavenwoodRule();

    private static final boolean BOOLEANKEYVALUE = false;
    private static final int INTKEYVALUE = 20;
    private static final String INTKEY = "intkey";
    private static final String BOOLEANKEY = "booleankey";

    /** Keys should be in hash code order */
    private static final String KEY1 = "key1";
    private static final String KEY2 = "key2";

    private Bundle mBundle;
    private Spannable mSpannable;

    @Before
    public void setUp() throws Exception {
        mBundle = new Bundle();
        mBundle.setClassLoader(getClass().getClassLoader());
    }

    private void initSpannable() {
        mSpannable = new SpannableString("foo bar");
        mSpannable.setSpan(new ForegroundColorSpan(0x123456), 0, 3, 0);
    }

    @After
    public void tearDown() throws Exception {
        CustomParcelable.sDeserialized = false;
        CustomSerializable.sDeserialized = false;
    }

    @Test
    public void testBundle() {
        final Bundle b1 = new Bundle();
        assertTrue(b1.isEmpty());
        b1.putBoolean(KEY1, true);
        assertFalse(b1.isEmpty());

        final Bundle b2 = new Bundle(b1);
        assertTrue(b2.getBoolean(KEY1));

        new Bundle(1024);
        new Bundle(getClass().getClassLoader());
    }

    @Test
    public void testEmptyStream() {
        Parcel p = Parcel.obtain();
        p.unmarshall(new byte[] {}, 0, 0);
        Bundle b = p.readBundle();
        assertTrue(b.isEmpty());
        mBundle.putBoolean("android", true);
        p.unmarshall(new byte[] {}, 0, 0);
        mBundle.readFromParcel(p);
        assertTrue(mBundle.isEmpty());
    }

    // first put sth into tested Bundle, it shouldn't be empty, then clear it and it should be empty
    @Test
    public void testClear() {
        mBundle.putBoolean("android", true);
        mBundle.putBoolean(KEY1, true);
        assertFalse(mBundle.isEmpty());
        mBundle.clear();
        assertTrue(mBundle.isEmpty());
    }

    // first clone the tested Bundle, then compare the original Bundle with the
    // cloned Bundle, they should equal
    @Test
    public void testClone() {
        mBundle.putBoolean(BOOLEANKEY, BOOLEANKEYVALUE);
        mBundle.putInt(INTKEY, INTKEYVALUE);
        Bundle cloneBundle = (Bundle) mBundle.clone();
        assertEquals(mBundle.size(), cloneBundle.size());
        assertEquals(mBundle.getBoolean(BOOLEANKEY), cloneBundle.getBoolean(BOOLEANKEY));
        assertEquals(mBundle.getInt(INTKEY), cloneBundle.getInt(INTKEY));
    }

    // containsKey would return false if nothing has been put into the Bundle,
    // else containsKey would return true if any putXXX has been called before
    @Test
    public void testContainsKey() {
        assertFalse(mBundle.containsKey(KEY1));
        mBundle.putBoolean(KEY1, true);
        assertTrue(mBundle.containsKey(KEY1));
        roundtrip();
        assertTrue(mBundle.containsKey(KEY1));
    }

    // get would return null if nothing has been put into the Bundle,else get
    // would return the value set by putXXX
    @Test
    public void testGet() {
        assertNull(mBundle.get(KEY1));
        mBundle.putBoolean(KEY1, true);
        assertNotNull(mBundle.get(KEY1));
        roundtrip();
        assertNotNull(mBundle.get(KEY1));
    }

    @Test
    public void testGetBoolean1() {
        assertFalse(mBundle.getBoolean(KEY1));
        mBundle.putBoolean(KEY1, true);
        assertTrue(mBundle.getBoolean(KEY1));
        roundtrip();
        assertTrue(mBundle.getBoolean(KEY1));
    }

    @Test
    public void testGetBoolean2() {
        assertTrue(mBundle.getBoolean(KEY1, true));
        mBundle.putBoolean(KEY1, false);
        assertFalse(mBundle.getBoolean(KEY1, true));
        roundtrip();
        assertFalse(mBundle.getBoolean(KEY1, true));
    }

    @Test
    public void testGetBooleanArray() {
        assertNull(mBundle.getBooleanArray(KEY1));
        mBundle.putBooleanArray(KEY1, new boolean[] {
                true, false, true
        });
        boolean[] booleanArray = mBundle.getBooleanArray(KEY1);
        assertNotNull(booleanArray);
        assertEquals(3, booleanArray.length);
        assertEquals(true, booleanArray[0]);
        assertEquals(false, booleanArray[1]);
        assertEquals(true, booleanArray[2]);
        roundtrip();
        booleanArray = mBundle.getBooleanArray(KEY1);
        assertNotNull(booleanArray);
        assertEquals(3, booleanArray.length);
        assertEquals(true, booleanArray[0]);
        assertEquals(false, booleanArray[1]);
        assertEquals(true, booleanArray[2]);
    }

    @Test
    public void testGetBundle() {
        assertNull(mBundle.getBundle(KEY1));
        final Bundle bundle = new Bundle();
        mBundle.putBundle(KEY1, bundle);
        assertTrue(bundle.equals(mBundle.getBundle(KEY1)));
        roundtrip();
        assertBundleEquals(bundle, mBundle.getBundle(KEY1));
    }

    @Test
    public void testGetByte1() {
        final byte b = 7;

        assertEquals(0, mBundle.getByte(KEY1));
        mBundle.putByte(KEY1, b);
        assertEquals(b, mBundle.getByte(KEY1));
        roundtrip();
        assertEquals(b, mBundle.getByte(KEY1));
    }

    @Test
    public void testGetByte2() {
        final byte b1 = 6;
        final byte b2 = 7;

        assertEquals((Byte)b1, mBundle.getByte(KEY1, b1));
        mBundle.putByte(KEY1, b2);
        assertEquals((Byte)b2, mBundle.getByte(KEY1, b1));
        roundtrip();
        assertEquals((Byte)b2, mBundle.getByte(KEY1, b1));
    }

    @Test
    public void testGetByteArray() {
        assertNull(mBundle.getByteArray(KEY1));
        mBundle.putByteArray(KEY1, new byte[] {
                1, 2, 3
        });
        byte[] byteArray = mBundle.getByteArray(KEY1);
        assertNotNull(byteArray);
        assertEquals(3, byteArray.length);
        assertEquals(1, byteArray[0]);
        assertEquals(2, byteArray[1]);
        assertEquals(3, byteArray[2]);
        roundtrip();
        byteArray = mBundle.getByteArray(KEY1);
        assertNotNull(byteArray);
        assertEquals(3, byteArray.length);
        assertEquals(1, byteArray[0]);
        assertEquals(2, byteArray[1]);
        assertEquals(3, byteArray[2]);
    }

    @Test
    public void testGetChar1() {
        final char c = 'l';

        assertEquals((char)0, mBundle.getChar(KEY1));
        mBundle.putChar(KEY1, c);
        assertEquals(c, mBundle.getChar(KEY1));
        roundtrip();
        assertEquals(c, mBundle.getChar(KEY1));
    }

    @Test
    public void testGetChar2() {
        final char c1 = 'l';
        final char c2 = 'i';

        assertEquals(c1, mBundle.getChar(KEY1, c1));
        mBundle.putChar(KEY1, c2);
        assertEquals(c2, mBundle.getChar(KEY1, c1));
        roundtrip();
        assertEquals(c2, mBundle.getChar(KEY1, c1));
    }

    @Test
    public void testGetCharArray() {
        assertNull(mBundle.getCharArray(KEY1));
        mBundle.putCharArray(KEY1, new char[] {
                'h', 'i'
        });
        char[] charArray = mBundle.getCharArray(KEY1);
        assertEquals('h', charArray[0]);
        assertEquals('i', charArray[1]);
        roundtrip();
        charArray = mBundle.getCharArray(KEY1);
        assertEquals('h', charArray[0]);
        assertEquals('i', charArray[1]);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = SpannableString.class)
    public void testGetCharSequence() {
        initSpannable();
        final CharSequence cS = "Bruce Lee";

        assertNull(mBundle.getCharSequence(KEY1));
        assertNull(mBundle.getCharSequence(KEY2));
        mBundle.putCharSequence(KEY1, cS);
        mBundle.putCharSequence(KEY2, mSpannable);
        assertEquals(cS, mBundle.getCharSequence(KEY1));
        assertSpannableEquals(mSpannable, mBundle.getCharSequence(KEY2));
        roundtrip();
        assertEquals(cS, mBundle.getCharSequence(KEY1));
        assertSpannableEquals(mSpannable, mBundle.getCharSequence(KEY2));
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = SpannableString.class)
    public void testGetCharSequenceArray() {
        initSpannable();
        assertNull(mBundle.getCharSequenceArray(KEY1));
        mBundle.putCharSequenceArray(KEY1, new CharSequence[] {
                "one", "two", "three", mSpannable
        });
        CharSequence[] ret = mBundle.getCharSequenceArray(KEY1);
        assertEquals(4, ret.length);
        assertEquals("one", ret[0]);
        assertEquals("two", ret[1]);
        assertEquals("three", ret[2]);
        assertSpannableEquals(mSpannable, ret[3]);
        roundtrip();
        ret = mBundle.getCharSequenceArray(KEY1);
        assertEquals(4, ret.length);
        assertEquals("one", ret[0]);
        assertEquals("two", ret[1]);
        assertEquals("three", ret[2]);
        assertSpannableEquals(mSpannable, ret[3]);
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = SpannableString.class)
    public void testGetCharSequenceArrayList() {
        initSpannable();
        assertNull(mBundle.getCharSequenceArrayList(KEY1));
        final ArrayList<CharSequence> list = new ArrayList<CharSequence>();
        list.add("one");
        list.add("two");
        list.add("three");
        list.add(mSpannable);
        mBundle.putCharSequenceArrayList(KEY1, list);
        roundtrip();
        ArrayList<CharSequence> ret = mBundle.getCharSequenceArrayList(KEY1);
        assertEquals(4, ret.size());
        assertEquals("one", ret.get(0));
        assertEquals("two", ret.get(1));
        assertEquals("three", ret.get(2));
        assertSpannableEquals(mSpannable, ret.get(3));
        roundtrip();
        ret = mBundle.getCharSequenceArrayList(KEY1);
        assertEquals(4, ret.size());
        assertEquals("one", ret.get(0));
        assertEquals("two", ret.get(1));
        assertEquals("three", ret.get(2));
        assertSpannableEquals(mSpannable, ret.get(3));
    }

    @Test
    public void testGetDouble1() {
        final double d = 10.07;

        assertEquals(0.0, mBundle.getDouble(KEY1), 0.0);
        mBundle.putDouble(KEY1, d);
        assertEquals(d, mBundle.getDouble(KEY1), 0.0);
        roundtrip();
        assertEquals(d, mBundle.getDouble(KEY1), 0.0);
    }

    @Test
    public void testGetDouble2() {
        final double d1 = 10.06;
        final double d2 = 10.07;

        assertEquals(d1, mBundle.getDouble(KEY1, d1), 0.0);
        mBundle.putDouble(KEY1, d2);
        assertEquals(d2, mBundle.getDouble(KEY1, d1), 0.0);
        roundtrip();
        assertEquals(d2, mBundle.getDouble(KEY1, d1), 0.0);
    }

    @Test
    public void testGetDoubleArray() {
        assertNull(mBundle.getDoubleArray(KEY1));
        mBundle.putDoubleArray(KEY1, new double[] {
                10.06, 10.07
        });
        double[] doubleArray = mBundle.getDoubleArray(KEY1);
        assertEquals(10.06, doubleArray[0], 0.0);
        assertEquals(10.07, doubleArray[1], 0.0);
        roundtrip();
        doubleArray = mBundle.getDoubleArray(KEY1);
        assertEquals(10.06, doubleArray[0], 0.0);
        assertEquals(10.07, doubleArray[1], 0.0);
    }

    @Test
    public void testGetFloat1() {
        final float f = 10.07f;

        assertEquals(0.0f, mBundle.getFloat(KEY1), 0.0f);
        mBundle.putFloat(KEY1, f);
        assertEquals(f, mBundle.getFloat(KEY1), 0.0f);
        roundtrip();
        assertEquals(f, mBundle.getFloat(KEY1), 0.0f);
    }

    @Test
    public void testGetFloat2() {
        final float f1 = 10.06f;
        final float f2 = 10.07f;

        assertEquals(f1, mBundle.getFloat(KEY1, f1), 0.0f);
        mBundle.putFloat(KEY1, f2);
        assertEquals(f2, mBundle.getFloat(KEY1, f1), 0.0f);
        roundtrip();
        assertEquals(f2, mBundle.getFloat(KEY1, f1), 0.0f);
    }

    @Test
    public void testGetFloatArray() {
        assertNull(mBundle.getFloatArray(KEY1));
        mBundle.putFloatArray(KEY1, new float[] {
                10.06f, 10.07f
        });
        float[] floatArray = mBundle.getFloatArray(KEY1);
        assertEquals(10.06f, floatArray[0], 0.0f);
        assertEquals(10.07f, floatArray[1], 0.0f);
        roundtrip();
        floatArray = mBundle.getFloatArray(KEY1);
        assertEquals(10.06f, floatArray[0], 0.0f);
        assertEquals(10.07f, floatArray[1], 0.0f);
    }

    @Test
    public void testGetInt1() {
        final int i = 1007;

        assertEquals(0, mBundle.getInt(KEY1));
        mBundle.putInt(KEY1, i);
        assertEquals(i, mBundle.getInt(KEY1));
        roundtrip();
        assertEquals(i, mBundle.getInt(KEY1));
    }

    @Test
    public void testGetInt2() {
        final int i1 = 1006;
        final int i2 = 1007;

        assertEquals(i1, mBundle.getInt(KEY1, i1));
        mBundle.putInt(KEY1, i2);
        assertEquals(i2, mBundle.getInt(KEY1, i2));
        roundtrip();
        assertEquals(i2, mBundle.getInt(KEY1, i2));
    }

    @Test
    public void testGetIntArray() {
        assertNull(mBundle.getIntArray(KEY1));
        mBundle.putIntArray(KEY1, new int[] {
                1006, 1007
        });
        int[] intArray = mBundle.getIntArray(KEY1);
        assertEquals(1006, intArray[0]);
        assertEquals(1007, intArray[1]);
        roundtrip();
        intArray = mBundle.getIntArray(KEY1);
        assertEquals(1006, intArray[0]);
        assertEquals(1007, intArray[1]);
    }

    // getIntegerArrayList should only return the IntegerArrayList set by putIntegerArrayLis
    @Test
    public void testGetIntegerArrayList() {
        final int i1 = 1006;
        final int i2 = 1007;

        assertNull(mBundle.getIntegerArrayList(KEY1));
        final ArrayList<Integer> arrayList = new ArrayList<Integer>();
        arrayList.add(i1);
        arrayList.add(i2);
        mBundle.putIntegerArrayList(KEY1, arrayList);
        ArrayList<Integer> retArrayList = mBundle.getIntegerArrayList(KEY1);
        assertNotNull(retArrayList);
        assertEquals(2, retArrayList.size());
        assertEquals((Integer)i1, retArrayList.get(0));
        assertEquals((Integer)i2, retArrayList.get(1));
        roundtrip();
        retArrayList = mBundle.getIntegerArrayList(KEY1);
        assertNotNull(retArrayList);
        assertEquals(2, retArrayList.size());
        assertEquals((Integer)i1, retArrayList.get(0));
        assertEquals((Integer)i2, retArrayList.get(1));
    }

    @Test
    public void testGetLong1() {
        final long l = 1007;

        assertEquals(0, mBundle.getLong(KEY1));
        mBundle.putLong(KEY1, l);
        assertEquals(l, mBundle.getLong(KEY1));
        roundtrip();
        assertEquals(l, mBundle.getLong(KEY1));
    }

    @Test
    public void testGetLong2() {
        final long l1 = 1006;
        final long l2 = 1007;

        assertEquals(l1, mBundle.getLong(KEY1, l1));
        mBundle.putLong(KEY1, l2);
        assertEquals(l2, mBundle.getLong(KEY1, l2));
        roundtrip();
        assertEquals(l2, mBundle.getLong(KEY1, l2));
    }

    @Test
    public void testGetLongArray() {
        assertNull(mBundle.getLongArray(KEY1));
        mBundle.putLongArray(KEY1, new long[] {
                1006, 1007
        });
        long[] longArray = mBundle.getLongArray(KEY1);
        assertEquals(1006, longArray[0]);
        assertEquals(1007, longArray[1]);
        roundtrip();
        longArray = mBundle.getLongArray(KEY1);
        assertEquals(1006, longArray[0]);
        assertEquals(1007, longArray[1]);
    }

    @Test
    public void testGetParcelable() {
        assertNull(mBundle.getParcelable(KEY1));
        final Bundle bundle = new Bundle();
        mBundle.putParcelable(KEY1, bundle);
        assertTrue(bundle.equals(mBundle.getParcelable(KEY1)));
        roundtrip();
        assertBundleEquals(bundle, (Bundle) mBundle.getParcelable(KEY1));
    }

    @Test
    public void testGetParcelableTypeSafe_withMismatchingType_returnsNull() {
        mBundle.putParcelable(KEY1, new CustomParcelable(42, "don't panic"));
        roundtrip();
        assertNull(mBundle.getParcelable(KEY1, Intent.class));
        assertFalse(CustomParcelable.sDeserialized);
    }

    @Test
    public void testGetParcelableTypeSafe_withMatchingType_returnsObject() {
        final CustomParcelable original = new CustomParcelable(42, "don't panic");
        mBundle.putParcelable(KEY1, original);
        roundtrip();
        assertEquals(original, mBundle.getParcelable(KEY1, CustomParcelable.class));
    }

    @Test
    public void testGetParcelableTypeSafe_withBaseType_returnsObject() {
        final CustomParcelable original = new CustomParcelable(42, "don't panic");
        mBundle.putParcelable(KEY1, original);
        roundtrip();
        assertEquals(original, mBundle.getParcelable(KEY1, Parcelable.class));
    }

    // getParcelableArray should only return the ParcelableArray set by putParcelableArray
    @Test
    public void testGetParcelableArray() {
        assertNull(mBundle.getParcelableArray(KEY1));
        final Bundle bundle1 = new Bundle();
        final Bundle bundle2 = new Bundle();
        mBundle.putParcelableArray(KEY1, new Bundle[] {
                bundle1, bundle2
        });
        Parcelable[] parcelableArray = mBundle.getParcelableArray(KEY1);
        assertEquals(2, parcelableArray.length);
        assertTrue(bundle1.equals(parcelableArray[0]));
        assertTrue(bundle2.equals(parcelableArray[1]));
        roundtrip();
        parcelableArray = mBundle.getParcelableArray(KEY1);
        assertEquals(2, parcelableArray.length);
        assertBundleEquals(bundle1, (Bundle) parcelableArray[0]);
        assertBundleEquals(bundle2, (Bundle) parcelableArray[1]);
    }

    @Test
    public void testGetParcelableArrayTypeSafe_withMismatchingType_returnsNull() {
        mBundle.putParcelableArray(KEY1, new CustomParcelable[] {
                new CustomParcelable(42, "don't panic")
        });
        roundtrip();
        assertNull(mBundle.getParcelableArray(KEY1, Intent.class));
        assertFalse(CustomParcelable.sDeserialized);
    }

    @Test
    public void testGetParcelableArrayTypeSafe_withMatchingType_returnsObject() {
        final CustomParcelable[] original = new CustomParcelable[] {
                new CustomParcelable(42, "don't panic"),
                new CustomParcelable(1961, "off we go")
        };
        mBundle.putParcelableArray(KEY1, original);
        roundtrip();
        assertArrayEquals(original, mBundle.getParcelableArray(KEY1, CustomParcelable.class));
    }

    @Test
    public void testGetParcelableArrayTypeSafe_withBaseType_returnsObject() {
        final CustomParcelable[] original = new CustomParcelable[] {
                new CustomParcelable(42, "don't panic"),
                new CustomParcelable(1961, "off we go")
        };
        mBundle.putParcelableArray(KEY1, original);
        roundtrip();
        assertArrayEquals(original, mBundle.getParcelableArray(KEY1, Parcelable.class));
    }

    // getParcelableArrayList should only return the parcelableArrayList set by putParcelableArrayList
    @Test
    public void testGetParcelableArrayList() {
        assertNull(mBundle.getParcelableArrayList(KEY1));
        final ArrayList<Parcelable> parcelableArrayList = new ArrayList<Parcelable>();
        final Bundle bundle1 = new Bundle();
        final Bundle bundle2 = new Bundle();
        parcelableArrayList.add(bundle1);
        parcelableArrayList.add(bundle2);
        mBundle.putParcelableArrayList(KEY1, parcelableArrayList);
        ArrayList<Parcelable> ret = mBundle.getParcelableArrayList(KEY1);
        assertEquals(2, ret.size());
        assertTrue(bundle1.equals(ret.get(0)));
        assertTrue(bundle2.equals(ret.get(1)));
        roundtrip();
        ret = mBundle.getParcelableArrayList(KEY1);
        assertEquals(2, ret.size());
        assertBundleEquals(bundle1, (Bundle) ret.get(0));
        assertBundleEquals(bundle2, (Bundle) ret.get(1));
    }

    @Test
    public void testGetParcelableArrayListTypeSafe_withMismatchingType_returnsNull() {
        final ArrayList<CustomParcelable> originalObjects = new ArrayList<>();
        originalObjects.add(new CustomParcelable(42, "don't panic"));
        mBundle.putParcelableArrayList(KEY1, originalObjects);
        roundtrip();
        assertNull(mBundle.getParcelableArrayList(KEY1, Intent.class));
        assertFalse(CustomParcelable.sDeserialized);
    }

    @Test
    public void testGetParcelableArrayListTypeSafe_withMatchingType_returnsObject() {
        final ArrayList<CustomParcelable> original = new ArrayList<>();
        original.add(new CustomParcelable(42, "don't panic"));
        original.add(new CustomParcelable(1961, "off we go"));
        mBundle.putParcelableArrayList(KEY1, original);
        roundtrip();
        assertEquals(original, mBundle.getParcelableArrayList(KEY1, CustomParcelable.class));
    }

    @Test
    public void testGetParcelableArrayListTypeSafe_withMismatchingTypeAndDifferentReturnType_returnsNull() {
        final ArrayList<CustomParcelable> originalObjects = new ArrayList<>();
        originalObjects.add(new CustomParcelable(42, "don't panic"));
        mBundle.putParcelableArrayList(KEY1, originalObjects);
        roundtrip();
        ArrayList<Parcelable> result = mBundle.getParcelableArrayList(KEY1, Intent.class);
        assertNull(result);
        assertFalse(CustomParcelable.sDeserialized);
    }

    @Test
    public void testGetParcelableArrayListTypeSafe_withMatchingTypeAndDifferentReturnType__returnsObject() {
        final ArrayList<CustomParcelable> original = new ArrayList<>();
        original.add(new CustomParcelable(42, "don't panic"));
        original.add(new CustomParcelable(1961, "off we go"));
        mBundle.putParcelableArrayList(KEY1, original);
        roundtrip();
        ArrayList<Parcelable> result = mBundle.getParcelableArrayList(KEY1, CustomParcelable.class);
        assertEquals(original, result);
    }

    @Test
    public void testGetParcelableArrayListTypeSafe_withBaseType_returnsObject() {
        final ArrayList<CustomParcelable> original = new ArrayList<>();
        original.add(new CustomParcelable(42, "don't panic"));
        original.add(new CustomParcelable(1961, "off we go"));
        mBundle.putParcelableArrayList(KEY1, original);
        roundtrip();
        assertEquals(original, mBundle.getParcelableArrayList(KEY1, Parcelable.class));
    }

    @Test
    public void testGetSerializableTypeSafe_withMismatchingType_returnsNull() {
        mBundle.putSerializable(KEY1, new CustomSerializable());
        roundtrip();
        assertNull(mBundle.getSerializable(KEY1, AnotherSerializable.class));
        assertFalse(CustomSerializable.sDeserialized);
    }

    @Test
    public void testGetSerializableTypeSafe_withMatchingType_returnsObject() {
        mBundle.putSerializable(KEY1, new CustomSerializable());
        roundtrip();
        assertNotNull(mBundle.getSerializable(KEY1, CustomSerializable.class));
        assertTrue(CustomSerializable.sDeserialized);
    }

    @Test
    public void testGetSerializableTypeSafe_withBaseType_returnsObject() {
        mBundle.putSerializable(KEY1, new CustomSerializable());
        roundtrip();
        assertNotNull(mBundle.getSerializable(KEY1, Serializable.class));
        assertTrue(CustomSerializable.sDeserialized);
    }

    @Test
    public void testGetSerializableWithString() {
        assertNull(mBundle.getSerializable(KEY1));
        String s = "android";
        mBundle.putSerializable(KEY1, s);
        assertEquals(s, mBundle.getSerializable(KEY1));
        roundtrip();
        assertEquals(s, mBundle.getSerializable(KEY1));
    }

    @Test
    public void testGetSerializableWithStringArray() {
        assertNull(mBundle.getSerializable(KEY1));
        String[] strings = new String[]{"first", "last"};
        mBundle.putSerializable(KEY1, strings);
        assertEquals(Arrays.asList(strings),
                Arrays.asList((String[]) mBundle.getSerializable(KEY1)));
        roundtrip();
        assertEquals(Arrays.asList(strings),
                Arrays.asList((String[]) mBundle.getSerializable(KEY1)));
    }

    @Test
    public void testGetSerializableWithMultiDimensionalObjectArray() {
        assertNull(mBundle.getSerializable(KEY1));
        Object[][] objects = new Object[][] {
                {"string", 1L}
        };
        mBundle.putSerializable(KEY1, objects);
        assertEquals(Arrays.asList(objects[0]),
                Arrays.asList(((Object[][]) mBundle.getSerializable(KEY1))[0]));
        roundtrip();
        assertEquals(Arrays.asList(objects[0]),
                Arrays.asList(((Object[][]) mBundle.getSerializable(KEY1))[0]));
    }

    @Test
    public void testGetShort1() {
        final short s = 1007;

        assertEquals(0, mBundle.getShort(KEY1));
        mBundle.putShort(KEY1, s);
        assertEquals(s, mBundle.getShort(KEY1));
        roundtrip();
        assertEquals(s, mBundle.getShort(KEY1));
    }

    @Test
    public void testGetShort2() {
        final short s1 = 1006;
        final short s2 = 1007;

        assertEquals(s1, mBundle.getShort(KEY1, s1));
        mBundle.putShort(KEY1, s2);
        assertEquals(s2, mBundle.getShort(KEY1, s1));
        roundtrip();
        assertEquals(s2, mBundle.getShort(KEY1, s1));
    }

    @Test
    public void testGetShortArray() {
        final short s1 = 1006;
        final short s2 = 1007;

        assertNull(mBundle.getShortArray(KEY1));
        mBundle.putShortArray(KEY1, new short[] {
                s1, s2
        });
        short[] shortArray = mBundle.getShortArray(KEY1);
        assertEquals(s1, shortArray[0]);
        assertEquals(s2, shortArray[1]);
        roundtrip();
        shortArray = mBundle.getShortArray(KEY1);
        assertEquals(s1, shortArray[0]);
        assertEquals(s2, shortArray[1]);
    }

    // getSparseParcelableArray should only return the SparseArray<Parcelable>
    // set by putSparseParcelableArray
    @Test
    public void testGetSparseParcelableArray() {
        assertNull(mBundle.getSparseParcelableArray(KEY1));
        final SparseArray<Parcelable> sparseArray = new SparseArray<Parcelable>();
        final Bundle bundle = new Bundle();
        final CustomParcelable custom = new CustomParcelable(42, "example");
        sparseArray.put(1006, bundle);
        sparseArray.put(1007, custom);
        mBundle.putSparseParcelableArray(KEY1, sparseArray);
        SparseArray<Parcelable> ret = mBundle.getSparseParcelableArray(KEY1);
        assertEquals(2, ret.size());
        assertNull(ret.get(1008));
        assertTrue(bundle.equals(ret.get(1006)));
        assertTrue(custom.equals(ret.get(1007)));
        roundtrip();
        ret = mBundle.getSparseParcelableArray(KEY1);
        assertEquals(2, ret.size());
        assertNull(ret.get(1008));
        assertBundleEquals(bundle, (Bundle) ret.get(1006));
        assertEquals(custom, (CustomParcelable) ret.get(1007));
    }

    @Test
    public void testGetSparseParcelableArrayTypeSafe_withMismatchingType_returnsNull() {
        final SparseArray<CustomParcelable> originalObjects = new SparseArray<>();
        originalObjects.put(42, new CustomParcelable(42, "don't panic"));
        mBundle.putSparseParcelableArray(KEY1, originalObjects);
        roundtrip();
        assertNull(mBundle.getSparseParcelableArray(KEY1, Intent.class));
        assertFalse(CustomParcelable.sDeserialized);
    }

    @Test
    public void testGetSparseParcelableArrayTypeSafe_withMatchingType_returnsObject() {
        final SparseArray<CustomParcelable> original = new SparseArray<>();
        original.put(42, new CustomParcelable(42, "don't panic"));
        original.put(1961, new CustomParcelable(1961, "off we go"));
        mBundle.putSparseParcelableArray(KEY1, original);
        roundtrip();
        assertTrue(original.contentEquals(mBundle.getSparseParcelableArray(KEY1, CustomParcelable.class)));
    }

    @Test
    public void testGetSparseParcelableArrayTypeSafe_withMismatchingTypeAndDifferentReturnType_returnsNull() {
        final SparseArray<CustomParcelable> originalObjects = new SparseArray<>();
        originalObjects.put(42, new CustomParcelable(42, "don't panic"));
        mBundle.putSparseParcelableArray(KEY1, originalObjects);
        roundtrip();
        SparseArray<Parcelable> result = mBundle.getSparseParcelableArray(KEY1, Intent.class);
        assertNull(result);
        assertFalse(CustomParcelable.sDeserialized);
    }

    @Test
    public void testGetSparseParcelableArrayTypeSafe_withMatchingTypeAndDifferentReturnType_returnsObject() {
        final SparseArray<CustomParcelable> original = new SparseArray<>();
        original.put(42, new CustomParcelable(42, "don't panic"));
        original.put(1961, new CustomParcelable(1961, "off we go"));
        mBundle.putSparseParcelableArray(KEY1, original);
        roundtrip();
        SparseArray<Parcelable> result = mBundle.getSparseParcelableArray(KEY1,
                CustomParcelable.class);
        assertTrue(original.contentEquals(result));
    }

    @Test
    public void testGetSparseParcelableArrayTypeSafe_withBaseType_returnsObject() {
        final SparseArray<CustomParcelable> original = new SparseArray<>();
        original.put(42, new CustomParcelable(42, "don't panic"));
        original.put(1961, new CustomParcelable(1961, "off we go"));
        mBundle.putSparseParcelableArray(KEY1, original);
        roundtrip();
        assertTrue(original.contentEquals(mBundle.getSparseParcelableArray(KEY1, Parcelable.class)));
    }

    @Test
    public void testGetSparseParcelableArrayTypeSafe_withMixedTypes_returnsObject() {
        final SparseArray<Parcelable> original = new SparseArray<>();
        original.put(42, new CustomParcelable(42, "don't panic"));
        original.put(1961, new ComposedParcelable(21, new CustomParcelable(21, "off we go")));
        mBundle.putSparseParcelableArray(KEY1, original);
        roundtrip();
        final SparseArray<Parcelable> received = mBundle.getSparseParcelableArray(KEY1, Parcelable.class);
        assertEquals(original.size(), received.size());
        assertEquals(original.get(42), received.get(42));
        assertEquals((ComposedParcelable) original.get(1961),
                (ComposedParcelable) received.get(1961));
    }

    @Test
    public void testGetString() {
        assertNull(mBundle.getString(KEY1));
        mBundle.putString(KEY1, "android");
        assertEquals("android", mBundle.getString(KEY1));
        roundtrip();
        assertEquals("android", mBundle.getString(KEY1));
    }

    @Test
    public void testGetStringArray() {
        assertNull(mBundle.getStringArray(KEY1));
        mBundle.putStringArray(KEY1, new String[] {
                "one", "two", "three"
        });
        String[] ret = mBundle.getStringArray(KEY1);
        assertEquals("one", ret[0]);
        assertEquals("two", ret[1]);
        assertEquals("three", ret[2]);
        roundtrip();
        ret = mBundle.getStringArray(KEY1);
        assertEquals("one", ret[0]);
        assertEquals("two", ret[1]);
        assertEquals("three", ret[2]);
    }

    // getStringArrayList should only return the StringArrayList set by putStringArrayList
    @Test
    public void testGetStringArrayList() {
        assertNull(mBundle.getStringArrayList(KEY1));
        final ArrayList<String> stringArrayList = new ArrayList<String>();
        stringArrayList.add("one");
        stringArrayList.add("two");
        stringArrayList.add("three");
        mBundle.putStringArrayList(KEY1, stringArrayList);
        ArrayList<String> ret = mBundle.getStringArrayList(KEY1);
        assertEquals(3, ret.size());
        assertEquals("one", ret.get(0));
        assertEquals("two", ret.get(1));
        assertEquals("three", ret.get(2));
        roundtrip();
        ret = mBundle.getStringArrayList(KEY1);
        assertEquals(3, ret.size());
        assertEquals("one", ret.get(0));
        assertEquals("two", ret.get(1));
        assertEquals("three", ret.get(2));
    }

    @Test
    public void testKeySet() {
        Set<String> setKey = mBundle.keySet();
        assertFalse(setKey.contains("one"));
        assertFalse(setKey.contains("two"));
        mBundle.putBoolean("one", true);
        mBundle.putChar("two", 't');
        setKey = mBundle.keySet();
        assertEquals(2, setKey.size());
        assertTrue(setKey.contains("one"));
        assertTrue(setKey.contains("two"));
        assertFalse(setKey.contains("three"));
        roundtrip();
        setKey = mBundle.keySet();
        assertEquals(2, setKey.size());
        assertTrue(setKey.contains("one"));
        assertTrue(setKey.contains("two"));
        assertFalse(setKey.contains("three"));
    }

    // same as hasFileDescriptors, the only difference is that describeContents
    // return 0 if no fd and return 1 if has fd for the tested Bundle

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testDescribeContents() {
        assertTrue((mBundle.describeContents()
                & Parcelable.CONTENTS_FILE_DESCRIPTOR) == 0);

        final Parcel parcel = Parcel.obtain();
        try {
            mBundle.putParcelable("foo", ParcelFileDescriptor.open(
                    new File("/system"), ParcelFileDescriptor.MODE_READ_ONLY));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("can't open /system", e);
        }
        assertTrue((mBundle.describeContents()
                & Parcelable.CONTENTS_FILE_DESCRIPTOR) != 0);
        mBundle.writeToParcel(parcel, 0);
        mBundle.clear();
        assertTrue((mBundle.describeContents()
                & Parcelable.CONTENTS_FILE_DESCRIPTOR) == 0);
        parcel.setDataPosition(0);
        mBundle.readFromParcel(parcel);
        assertTrue((mBundle.describeContents()
                & Parcelable.CONTENTS_FILE_DESCRIPTOR) != 0);
        ParcelFileDescriptor pfd = (ParcelFileDescriptor)mBundle.getParcelable("foo");
        assertTrue((mBundle.describeContents()
                & Parcelable.CONTENTS_FILE_DESCRIPTOR) != 0);
    }

    // case 1: The default bundle doesn't has FileDescriptor.
    // case 2: The tested Bundle should has FileDescriptor
    //  if it read data from a Parcel object, which is created with a FileDescriptor.
    // case 3: The tested Bundle should has FileDescriptor
    //  if put a Parcelable object, which is created with a FileDescriptor, into it.
    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testHasFileDescriptors_withParcelFdItem() {
        assertFalse(mBundle.hasFileDescriptors());

        final Parcel parcel = Parcel.obtain();
        assertFalse(parcel.hasFileDescriptors());
        try {
            mBundle.putParcelable("foo", ParcelFileDescriptor.open(
                    new File("/system"), ParcelFileDescriptor.MODE_READ_ONLY));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("can't open /system", e);
        }
        assertTrue(mBundle.hasFileDescriptors());
        mBundle.writeToParcel(parcel, 0);
        assertTrue(parcel.hasFileDescriptors());
        mBundle.clear();
        assertFalse(mBundle.hasFileDescriptors());
        parcel.setDataPosition(0);
        mBundle.readFromParcel(parcel);
        assertTrue(mBundle.hasFileDescriptors()); // Checks the parcel

        // Remove item to trigger deserialization and remove flag FLAG_HAS_FDS_KNOWN such that next
        // query triggers flag computation from lazy value
        mBundle.remove("unexistent");
        assertTrue(mBundle.hasFileDescriptors()); // Checks the lazy value

        // Trigger flag computation
        mBundle.remove("unexistent");
        ParcelFileDescriptor pfd = mBundle.getParcelable("foo"); // Extracts the lazy value
        assertTrue(mBundle.hasFileDescriptors()); // Checks the object

        // Now, check the lazy value returns false
        mBundle.clear();
        mBundle.putParcelable(KEY1, new CustomParcelable(13, "Tiramisu"));
        roundtrip();
        // Trigger flag computation
        mBundle.putParcelable("random", new CustomParcelable(13, "Tiramisu"));
        assertFalse(mBundle.hasFileDescriptors()); // Checks the lazy value
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testHasFileDescriptors_withParcelable() throws Exception {
        assertTrue(mBundle.isEmpty());
        assertFalse(mBundle.hasFileDescriptors());

        mBundle.putParcelable("key", ParcelFileDescriptor.dup(FileDescriptor.in));
        assertTrue(mBundle.hasFileDescriptors());

        mBundle.putParcelable("key", new CustomParcelable(13, "Tiramisu"));
        assertFalse(mBundle.hasFileDescriptors());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testHasFileDescriptors_withStringArray() throws Exception {
        assertTrue(mBundle.isEmpty());
        assertFalse(mBundle.hasFileDescriptors());

        mBundle.putStringArray("key", new String[] { "string" });
        assertFalse(mBundle.hasFileDescriptors());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testHasFileDescriptors_withSparseArray() throws Exception {
        assertTrue(mBundle.isEmpty());
        assertFalse(mBundle.hasFileDescriptors());

        SparseArray<Parcelable> fdArray = new SparseArray<>();
        fdArray.append(0, ParcelFileDescriptor.dup(FileDescriptor.in));
        mBundle.putSparseParcelableArray("key", fdArray);
        assertTrue(mBundle.hasFileDescriptors());

        SparseArray<Parcelable> noFdArray = new SparseArray<>();
        noFdArray.append(0, new CustomParcelable(13, "Tiramisu"));
        mBundle.putSparseParcelableArray("key", noFdArray);
        assertFalse(mBundle.hasFileDescriptors());

        SparseArray<Parcelable> emptyArray = new SparseArray<>();
        mBundle.putSparseParcelableArray("key", emptyArray);
        assertFalse(mBundle.hasFileDescriptors());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testHasFileDescriptors_withParcelableArray() throws Exception {
        assertTrue(mBundle.isEmpty());
        assertFalse(mBundle.hasFileDescriptors());

        mBundle.putParcelableArray("key",
                new Parcelable[] { ParcelFileDescriptor.dup(FileDescriptor.in) });
        assertTrue(mBundle.hasFileDescriptors());

        mBundle.putParcelableArray("key",
                new Parcelable[] { new CustomParcelable(13, "Tiramisu") });
        assertFalse(mBundle.hasFileDescriptors());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testHasFileDescriptorsOnNullValuedCollection() {
        assertFalse(mBundle.hasFileDescriptors());

        mBundle.putParcelableArray("foo", new Parcelable[1]);
        assertFalse(mBundle.hasFileDescriptors());
        mBundle.clear();

        SparseArray<Parcelable> sparseArray = new SparseArray<Parcelable>();
        sparseArray.put(0, null);
        mBundle.putSparseParcelableArray("bar", sparseArray);
        assertFalse(mBundle.hasFileDescriptors());
        mBundle.clear();

        ArrayList<Parcelable> arrayList = new ArrayList<Parcelable>();
        arrayList.add(null);
        mBundle.putParcelableArrayList("baz", arrayList);
        assertFalse(mBundle.hasFileDescriptors());
        mBundle.clear();
    }

    @SuppressWarnings("unchecked")
    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testHasFileDescriptors_withNestedContainers() throws IOException {
        // Purposely omitting generic types here, this is still "valid" app code after all.
        ArrayList nested = new ArrayList(
                Arrays.asList(Arrays.asList(ParcelFileDescriptor.dup(FileDescriptor.in))));
        mBundle.putParcelableArrayList("list", nested);
        assertTrue(mBundle.hasFileDescriptors());

        roundtrip(/* parcel */ false);
        assertTrue(mBundle.hasFileDescriptors());

        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        mBundle.remove("unexistent"); // Removes cached value (removes FLAG_HAS_FDS_KNOWN)
        assertTrue(mBundle.hasFileDescriptors()); // Checks lazy value
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testHasFileDescriptors_withOriginalParcelContainingFdButNotItems() throws IOException {
        mBundle.putParcelable("fd", ParcelFileDescriptor.dup(FileDescriptor.in));
        mBundle.putParcelable("parcelable", new CustomParcelable(13, "Tiramisu"));
        assertTrue(mBundle.hasFileDescriptors());

        roundtrip(/* parcel */ false);
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        assertTrue(mBundle.hasFileDescriptors());
        mBundle.remove("fd");

        // Will check the item's specific range in the original parcel
        assertFalse(mBundle.hasFileDescriptors());
    }

    @Test
    @IgnoreUnderRavenwood(blockedBy = ParcelFileDescriptor.class)
    public void testHasFileDescriptors_withOriginalParcelAndItemsContainingFd() throws IOException {
        mBundle.putParcelable("fd", ParcelFileDescriptor.dup(FileDescriptor.in));
        mBundle.putParcelable("parcelable", new CustomParcelable(13, "Tiramisu"));
        assertTrue(mBundle.hasFileDescriptors());

        roundtrip(/* parcel */ false);
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        assertTrue(mBundle.hasFileDescriptors());
        mBundle.remove("parcelable");

        // Will check the item's specific range in the original parcel
        assertTrue(mBundle.hasFileDescriptors());
    }

    @Test
    public void testSetClassLoader() {
        mBundle.setClassLoader(new MockClassLoader());
    }

    // Write the bundle(A) to a parcel(B), and then create a bundle(C) from B.
    // C should be same as A.
    @Test
    public void testWriteToParcel() {
        final String li = "Bruce Li";

        mBundle.putString(KEY1, li);
        final Parcel parcel = Parcel.obtain();
        mBundle.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final Bundle bundle = Bundle.CREATOR.createFromParcel(parcel);
        assertEquals(li, bundle.getString(KEY1));
    }

    // test the size should be right after add/remove key-value pair of the Bundle.
    @Test
    public void testSize() {
        assertEquals(0, mBundle.size());
        mBundle.putBoolean("one", true);
        assertEquals(1, mBundle.size());

        mBundle.putBoolean("two", true);
        assertEquals(2, mBundle.size());

        mBundle.putBoolean("three", true);
        assertEquals(3, mBundle.size());

        mBundle.putBoolean("four", true);
        mBundle.putBoolean("five", true);
        assertEquals(5, mBundle.size());
        mBundle.remove("six");
        assertEquals(5, mBundle.size());

        mBundle.remove("one");
        assertEquals(4, mBundle.size());
        mBundle.remove("one");
        assertEquals(4, mBundle.size());

        mBundle.remove("two");
        assertEquals(3, mBundle.size());

        mBundle.remove("three");
        mBundle.remove("four");
        mBundle.remove("five");
        assertEquals(0, mBundle.size());
    }

    // The return value of toString() should not be null.
    @Test
    public void testToString() {
        assertNotNull(mBundle.toString());
        mBundle.putString("foo", "this test is so stupid");
        assertNotNull(mBundle.toString());
    }

    // The tested Bundle should hold mappings from the given after putAll be invoked.
    @Test
    public void testPutAll() {
        assertEquals(0, mBundle.size());

        final Bundle map = new Bundle();
        map.putBoolean(KEY1, true);
        assertEquals(1, map.size());
        mBundle.putAll(map);
        assertEquals(1, mBundle.size());
    }

    private void roundtrip() {
        roundtrip(/* parcel */ true);
    }

    private void roundtrip(boolean parcel) {
        mBundle = roundtrip(mBundle, parcel);
    }

    private Bundle roundtrip(Bundle bundle) {
        return roundtrip(bundle, /* parcel */ true);
    }

    private Bundle roundtrip(Bundle bundle, boolean parcel) {
        Parcel p = Parcel.obtain();
        bundle.writeToParcel(p, 0);
        if (parcel) {
            p = roundtripParcel(p);
        }
        p.setDataPosition(0);
        return p.readBundle(bundle.getClassLoader());
    }

    private Parcel roundtripParcel(Parcel out) {
        byte[] buf = out.marshall();
        Parcel in = Parcel.obtain();
        in.unmarshall(buf, 0, buf.length);
        in.setDataPosition(0);
        return in;
    }

    private void assertBundleEquals(Bundle expected, Bundle observed) {
        assertEquals(expected.size(), observed.size());
        for (String key : expected.keySet()) {
            assertEquals(expected.get(key), observed.get(key));
        }
    }

    private void assertSpannableEquals(Spannable expected, CharSequence observed) {
        final Spannable observedSpan = (Spannable) observed;
        assertEquals(expected.toString(), observed.toString());
        Object[] expectedSpans = expected.getSpans(0, expected.length(), Object.class);
        Object[] observedSpans = observedSpan.getSpans(0, observedSpan.length(), Object.class);
        assertEquals(expectedSpans.length, observedSpans.length);
        for (int i = 0; i < expectedSpans.length; i++) {
            // Can't compare values of arbitrary objects
            assertEquals(expectedSpans[i].getClass(), observedSpans[i].getClass());
        }
    }

    @Test
    @IgnoreUnderRavenwood
    public void testHasFileDescriptor() throws Exception {
        final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        try {
            final ParcelFileDescriptor fd = pipe[0];

            assertNotHaveFd(Bundle.EMPTY);
            assertNotHaveFd(new Bundle());

            assertNotHaveFd(buildBundle("a", 1));

            assertHasFd(buildBundle("a", 1, fd));
            assertHasFd(buildBundle("a", 1, new Parcelable[]{fd}));
            assertHasFd(buildBundle("a", 1, buildBundle(new Parcelable[]{fd})));
            assertNotHaveFd(buildBundle("a", 1, buildBundle(1)));

            Bundle nested1 = buildBundle(fd, buildBundle(1));
            assertHasFd(nested1); // Outer bundle has an FD.
            assertNotHaveFd(nested1.getParcelable("key-1")); // But inner bundle doesn't.

            Bundle nested2 = buildBundle(1, buildBundle(fd));
            assertHasFd(nested2);
            assertHasFd(nested2.getParcelable("key-1"));

            // More tricky case.  Create a parcel with mixed objects.
            Parcel p = Parcel.obtain();
            p.writeParcelable(fd, 0);
            p.writeInt(123);
            p.writeParcelable(buildBundle(1), 0);

            // Now the parcel has an FD.
            p.setDataPosition(0);
            assertTrue(p.hasFileDescriptors());

            // Note even though the entire parcel has an FD, the inner bundle doesn't.
            assertEquals(ParcelFileDescriptor.class,
                    p.readParcelable(getClass().getClassLoader()).getClass());
            assertEquals(123, p.readInt());
            assertNotHaveFd(p.readParcelable(Bundle.class.getClassLoader()));
        } finally {
            pipe[0].close();
            pipe[1].close();
        }
    }

    @Test
    public void testBundleLengthNotAlignedByFour() {
        mBundle.putBoolean(KEY1, true);
        assertEquals(1, mBundle.size());
        Parcel p = Parcel.obtain();
        final int lengthPos = p.dataPosition();
        mBundle.writeToParcel(p, 0);
        p.setDataPosition(lengthPos);
        final int length = p.readInt();
        assertTrue(length != 0);
        assertTrue(length % 4 == 0);
        // Corrupt the bundle length so it is not aligned by 4.
        p.setDataPosition(lengthPos);
        p.writeInt(length - 1);
        p.setDataPosition(0);
        final Bundle b = new Bundle();
        try {
            b.readFromParcel(p);
            fail("Failed to get an IllegalStateException");
        } catch (IllegalStateException e) {
            // Expect IllegalStateException here.
        }
    }

    @Test
    public void testGetCustomParcelable() {
        Parcelable parcelable = new CustomParcelable(13, "Tiramisu");
        mBundle.putParcelable(KEY1, parcelable);
        assertEquals(parcelable, mBundle.getParcelable(KEY1));
        assertEquals(1, mBundle.size());
        roundtrip();
        assertNotSame(parcelable, mBundle.getParcelable(KEY1));
        assertEquals(parcelable, mBundle.getParcelable(KEY1));
        assertEquals(1, mBundle.size());
    }

    @Test
    public void testGetNestedParcelable() {
        Parcelable nested = new CustomParcelable(13, "Tiramisu");
        ComposedParcelable parcelable = new ComposedParcelable(26, nested);
        mBundle.putParcelable(KEY1, parcelable);
        assertEquals(parcelable, mBundle.getParcelable(KEY1));
        assertEquals(1, mBundle.size());
        roundtrip();
        ComposedParcelable reconstructed = mBundle.getParcelable(KEY1);
        assertNotSame(parcelable, reconstructed);
        assertEquals(parcelable, reconstructed);
        assertNotSame(nested, reconstructed.parcelable);
        assertEquals(nested, reconstructed.parcelable);
        assertEquals(1, mBundle.size());
    }

    @Test
    public void testItemDeserializationIndependence() {
        Parcelable parcelable = new CustomParcelable(13, "Tiramisu");
        Parcelable bomb = new CustomParcelable(13, "Tiramisu").setThrowsDuringDeserialization(true);
        mBundle.putParcelable(KEY1, parcelable);
        mBundle.putParcelable(KEY2, bomb);
        assertEquals(parcelable, mBundle.getParcelable(KEY1));
        assertEquals(bomb, mBundle.getParcelable(KEY2));
        assertEquals(2, mBundle.size());
        roundtrip();
        assertEquals(2, mBundle.size());
        Parcelable reParcelable = mBundle.getParcelable(KEY1);
        // Passed if it didn't throw
        assertNotSame(parcelable, reParcelable);
        assertEquals(parcelable, reParcelable);
        assertThrows(RuntimeException.class, () -> mBundle.getParcelable(KEY2));
        assertEquals(2, mBundle.size());
    }

    @Test
    public void testLazyValueReserialization() {
        Parcelable parcelable = new CustomParcelable(13, "Tiramisu");
        mBundle.putParcelable(KEY1, parcelable);
        mBundle.putString(KEY2, "value");
        roundtrip();
        assertEquals("value", mBundle.getString(KEY2));
        // Since we haven't retrieved KEY1, its value is still a lazy value inside bundle
        roundtrip();
        assertEquals(parcelable, mBundle.getParcelable(KEY1));
        assertEquals("value", mBundle.getString(KEY2));
    }

    @Test
    public void testPutAll_withLazyValues() {
        Parcelable parcelable = new CustomParcelable(13, "Tiramisu");
        mBundle.putParcelable(KEY1, parcelable);
        roundtrip();
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        Bundle copy = new Bundle();
        copy.putAll(mBundle);
        assertEquals(parcelable, copy.getParcelable(KEY1));
        // Here we're verifying that LazyValue caches the deserialized object, hence they are the
        // same instance
        assertSame(copy.getParcelable(KEY1), mBundle.getParcelable(KEY1));
        assertEquals(1, copy.size());
    }

    @Test
    public void testDeepCopy_withLazyValues() {
        Parcelable parcelable = new CustomParcelable(13, "Tiramisu");
        mBundle.putParcelable(KEY1, parcelable);
        roundtrip();
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        Bundle copy = mBundle.deepCopy();
        assertEquals(parcelable, copy.getParcelable(KEY1));
        // Here we're verifying that LazyValue caches the deserialized object, hence they are the
        // same instance
        assertSame(copy.getParcelable(KEY1), mBundle.getParcelable(KEY1));
        assertEquals(1, copy.size());
    }

    @Test
    public void testDeepCopy_withNestedParcelable() {
        Parcelable nested = new CustomParcelable(13, "Tiramisu");
        ComposedParcelable parcelable = new ComposedParcelable(26, nested);
        mBundle.putParcelable(KEY1, parcelable);
        roundtrip();
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        Bundle copy = mBundle.deepCopy();
        ComposedParcelable reconstructed = copy.getParcelable(KEY1);
        assertEquals(parcelable, reconstructed);
        assertSame(copy.getParcelable(KEY1), mBundle.getParcelable(KEY1));
        assertEquals(nested, reconstructed.parcelable);
        assertEquals(1, copy.size());
    }

    @Test
    public void testDeepCopy_withNestedBundleAndLazyValues() {
        Parcelable parcelable = new CustomParcelable(13, "Tiramisu");
        Bundle inner = new Bundle();
        inner.putParcelable(KEY1, parcelable);
        inner = roundtrip(inner);
        inner.setClassLoader(getClass().getClassLoader());
        inner.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        mBundle.putParcelable(KEY1, inner);
        Bundle copy = mBundle.deepCopy();
        assertEquals(parcelable, copy.getBundle(KEY1).getParcelable(KEY1));
        assertNotSame(mBundle.getBundle(KEY1), copy.getBundle(KEY1));
        assertSame(mBundle.getBundle(KEY1).getParcelable(KEY1),
                copy.getBundle(KEY1).getParcelable(KEY1));
        assertEquals(1, copy.getBundle(KEY1).size());
        assertEquals(1, copy.size());
    }

    @Test
    public void testGetParcelable_isLazy() {
        mBundle.putParcelable(KEY1, new CustomParcelable(13, "Tiramisu"));
        roundtrip();
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        assertThat(CustomParcelable.sDeserialized).isFalse();
        mBundle.getParcelable(KEY1);
        assertThat(CustomParcelable.sDeserialized).isTrue();
    }

    @Test
    public void testGetParcelableArray_isLazy() {
        mBundle.putParcelableArray(KEY1, new Parcelable[] {new CustomParcelable(13, "Tiramisu")});
        roundtrip();
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        assertThat(CustomParcelable.sDeserialized).isFalse();
        mBundle.getParcelableArray(KEY1);
        assertThat(CustomParcelable.sDeserialized).isTrue();
    }

    @Test
    public void testGetParcelableArrayList_isLazy() {
        mBundle.putParcelableArrayList(KEY1,
                new ArrayList<>(singletonList(new CustomParcelable(13, "Tiramisu"))));
        roundtrip();
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        assertThat(CustomParcelable.sDeserialized).isFalse();
        mBundle.getParcelableArrayList(KEY1);
        assertThat(CustomParcelable.sDeserialized).isTrue();
    }

    @Test
    public void testGetSparseParcelableArray_isLazy() {
        SparseArray<Parcelable> container = new SparseArray<>();
        container.put(0, new CustomParcelable(13, "Tiramisu"));
        mBundle.putSparseParcelableArray(KEY1, container);
        roundtrip();
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        assertThat(CustomParcelable.sDeserialized).isFalse();
        mBundle.getSparseParcelableArray(KEY1);
        assertThat(CustomParcelable.sDeserialized).isTrue();
    }

    @Test
    public void testGetSerializable_isLazy() {
        mBundle.putSerializable(KEY1, new CustomSerializable());
        roundtrip();
        mBundle.isEmpty(); // Triggers partial deserialization (leaving lazy values)
        assertThat(CustomSerializable.sDeserialized).isFalse();
        mBundle.getSerializable(KEY1);
        assertThat(CustomSerializable.sDeserialized).isTrue();
    }

    public static class CustomSerializable implements Serializable {
        public static boolean sDeserialized = false;

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            sDeserialized = true;
        }
    }

    public static class AnotherSerializable implements Serializable {
    }

    public static class CustomParcelable implements Parcelable {
        public static boolean sDeserialized = false;

        public final int integer;
        public final String string;
        public boolean throwsDuringDeserialization;

        public CustomParcelable(int integer, String string) {
            this.integer = integer;
            this.string = string;
        }

        protected CustomParcelable(Parcel in) {
            integer = in.readInt();
            string = in.readString();
            throwsDuringDeserialization = in.readBoolean();
            if (throwsDuringDeserialization) {
                throw new RuntimeException();
            }
            sDeserialized = true;
        }

        public CustomParcelable setThrowsDuringDeserialization(
                boolean throwsDuringDeserialization) {
            this.throwsDuringDeserialization = throwsDuringDeserialization;
            return this;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(integer);
            out.writeString(string);
            out.writeBoolean(throwsDuringDeserialization);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CustomParcelable)) {
                return false;
            }
            CustomParcelable that = (CustomParcelable) other;
            return integer == that.integer
                    && throwsDuringDeserialization == that.throwsDuringDeserialization
                    && string.equals(that.string);
        }

        @Override
        public int hashCode() {
            return Objects.hash(integer, string, throwsDuringDeserialization);
        }

        public static final Creator<CustomParcelable> CREATOR = new Creator<CustomParcelable>() {
            @Override
            public CustomParcelable createFromParcel(Parcel in) {
                return new CustomParcelable(in);
            }
            @Override
            public CustomParcelable[] newArray(int size) {
                return new CustomParcelable[size];
            }
        };
    }

    public static class ComposedParcelable implements Parcelable {
        public final int integer;
        public final Parcelable parcelable;

        public ComposedParcelable(int integer, Parcelable parcelable) {
            this.integer = integer;
            this.parcelable = parcelable;
        }

        protected ComposedParcelable(Parcel in) {
            integer = in.readInt();
            parcelable = in.readParcelable(getClass().getClassLoader());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeInt(integer);
            out.writeParcelable(parcelable, flags);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ComposedParcelable)) {
                return false;
            }
            ComposedParcelable that = (ComposedParcelable) other;
            return integer == that.integer && Objects.equals(parcelable, that.parcelable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(integer, parcelable);
        }

        public static final Creator<ComposedParcelable> CREATOR =
                new Creator<ComposedParcelable>() {
                    @Override
                    public ComposedParcelable createFromParcel(Parcel in) {
                        return new ComposedParcelable(in);
                    }
                    @Override
                    public ComposedParcelable[] newArray(int size) {
                        return new ComposedParcelable[size];
                    }
                };
    }

    /** Create a Bundle with values, with autogenerated keys. */
    private static Bundle buildBundle(Object... values) {
        final Bundle result = new Bundle();

        for (int i = 0; i < values.length; i++) {
            final String key = "key-" + i;

            final Object value = values[i];
            if (value == null) {
                result.putString(key, null);

            } else if (value instanceof String) {
                result.putString(key, (String) value);

            } else if (value instanceof Integer) {
                result.putInt(key, (Integer) value);

            } else if (value instanceof Parcelable) {
                result.putParcelable(key, (Parcelable) value);

            } else if (value instanceof Parcelable[]) {
                result.putParcelableArray(key, (Parcelable[]) value);

            } else {
                fail("Unsupported value type: " + value.getClass());
            }
        }
        return result;
    }

    private static Bundle cloneBundle(Bundle b) {
        return new Bundle(b);
    }

    private static Bundle cloneBundleViaParcel(Bundle b) {
        final Parcel p = Parcel.obtain();
        try {
            p.writeParcelable(b, 0);

            p.setDataPosition(0);

            return p.readParcelable(Bundle.class.getClassLoader());
        } finally {
            p.recycle();
        }
    }

    private static void assertHasFd(Bundle b) {
        assertTrue(b.hasFileDescriptors());

        // Make sure cloned ones have the same result.
        assertTrue(cloneBundle(b).hasFileDescriptors());
        assertTrue(cloneBundleViaParcel(b).hasFileDescriptors());
    }

    private static void assertNotHaveFd(Bundle b) {
        assertFalse(b.hasFileDescriptors());

        // Make sure cloned ones have the same result.
        assertFalse(cloneBundle(b).hasFileDescriptors());
        assertFalse(cloneBundleViaParcel(b).hasFileDescriptors());
    }

    class MockClassLoader extends ClassLoader {
        MockClassLoader() {
            super();
        }
    }

    private static <T> T uncheck(Callable<T> runnable) {
        try {
            return runnable.call();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
