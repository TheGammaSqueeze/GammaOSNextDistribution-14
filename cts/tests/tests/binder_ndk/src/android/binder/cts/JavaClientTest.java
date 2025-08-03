/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.binder.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import androidx.test.InstrumentationRegistry;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import test_package.Bar;
import test_package.Baz;
import test_package.ByteEnum;
import test_package.ExtendableParcelable;
import test_package.Foo;
import test_package.GenericBar;
import test_package.GenericFoo;
import test_package.ICompatTest;
import test_package.IEmpty;
import test_package.ITest;
import test_package.IntEnum;
import test_package.LongEnum;
import test_package.MyExt;
import test_package.RegularPolygon;
import test_package.SimpleUnion;

@RunWith(Parameterized.class)
public class JavaClientTest {
    private final String TAG = "JavaClientTest";

    private Class mServiceClass;
    private ITest mInterface;
    private String mExpectedName;
    private boolean mShouldBeRemote;
    private boolean mShouldBeOld;

    public JavaClientTest(Class serviceClass, String expectedName, boolean shouldBeRemote, boolean shouldBeOld) {
        mServiceClass = serviceClass;
        mExpectedName = expectedName;
        mShouldBeRemote = shouldBeRemote;
        mShouldBeOld = shouldBeOld;
    }

    @Parameterized.Parameters( name = "{0}" )
    public static Collection<Object[]> data() {
        // For local interfaces, this test will parcel the data locally.
        // Whenever possible, the desired service should be accessed directly
        // in order to avoid this additional overhead.
        return Arrays.asList(new Object[][] {
                {NativeService.Local.class, "CPP", false /*shouldBeRemote*/, false /*shouldBeOld*/},
                {JavaService.Local.class, "JAVA", false /*shouldBeRemote*/, false /*shouldBeOld*/},
                {NativeService.Remote.class, "CPP", true /*shouldBeRemote*/, false /*shouldBeOld*/},
                {NativeService.RemoteOld.class, "CPP", true /*shouldBeRemote*/, true /*shouldBeOld*/},
                {JavaService.Remote.class, "JAVA", true /*shouldBeRemote*/, false /*shouldBeOld*/},
            });
    }

    @Before
    public void setUp() {
        Log.e(TAG, "Setting up");

        SyncTestServiceConnection connection = new SyncTestServiceConnection(
            InstrumentationRegistry.getTargetContext(), mServiceClass);

        mInterface = connection.get();
        assertNotEquals(null, mInterface);
    }

    @Test
    public void testSanityCheckSource() throws RemoteException {
        String name = mInterface.GetName();

        Log.i(TAG, "Service GetName: " + name);
        assertEquals(mExpectedName, name);
    }

    @Test
    public void testVoidReturn() throws RemoteException {
        mInterface.TestVoidReturn();
    }

    @Test
    public void testOneway() throws RemoteException {
        boolean isLocalJava = !mShouldBeRemote && mExpectedName == "JAVA";
        try {
            mInterface.TestOneway();
            assertFalse("local Java should throw exception", isLocalJava);
        } catch (RemoteException e) {
            assertTrue("only local Java should report error", isLocalJava);
        }
    }

    private void checkDump(String expected, String[] args) throws RemoteException, IOException {
        ParcelFileDescriptor[] sockets = ParcelFileDescriptor.createReliableSocketPair();
        ParcelFileDescriptor socketIn = sockets[0];
        ParcelFileDescriptor socketOut = sockets[1];

        mInterface.asBinder().dump(socketIn.getFileDescriptor(), args);
        socketIn.close();

        FileInputStream fileInputStream = new ParcelFileDescriptor.AutoCloseInputStream(socketOut);

        byte[] expectedBytes = expected.getBytes();
        byte[] input = new byte[expectedBytes.length];

        assertEquals(input.length, fileInputStream.read(input));
        Assert.assertArrayEquals(input, expectedBytes);
    }

    @Test
    public void testDump() throws RemoteException, IOException {
        checkDump("", new String[]{});
        checkDump("", new String[]{"", ""});
        checkDump("Hello World!", new String[]{"Hello ", "World!"});
        checkDump("ABC", new String[]{"A", "B", "C"});
    }

    @Test
    public void testCallingInfo() throws RemoteException {
      mInterface.CacheCallingInfoFromOneway();

      assertEquals(Process.myPid(), mInterface.GiveMeMyCallingPid());
      assertEquals(Process.myUid(), mInterface.GiveMeMyCallingUid());

      if (mShouldBeRemote) {
        // PID is hidden from oneway calls
        assertEquals(0, mInterface.GiveMeMyCallingPidFromOneway());
      } else {
        assertEquals(Process.myPid(), mInterface.GiveMeMyCallingPidFromOneway());
      }

      assertEquals(Process.myUid(), mInterface.GiveMeMyCallingUidFromOneway());
    }

    @Test
    public void testRepeatPrimitives() throws RemoteException {
        assertEquals(1, mInterface.RepeatInt(1));
        assertEquals(2, mInterface.RepeatLong(2));
        assertEquals(1.0f, mInterface.RepeatFloat(1.0f), 0.0f);
        assertEquals(2.0, mInterface.RepeatDouble(2.0), 0.0);
        assertEquals(true, mInterface.RepeatBoolean(true));
        assertEquals('a', mInterface.RepeatChar('a'));
        assertEquals((byte)3, mInterface.RepeatByte((byte)3));
        assertEquals(ByteEnum.FOO, mInterface.RepeatByteEnum(ByteEnum.FOO));
        assertEquals(IntEnum.FOO, mInterface.RepeatIntEnum(IntEnum.FOO));
        assertEquals(LongEnum.FOO, mInterface.RepeatLongEnum(LongEnum.FOO));
    }

    @Test
    public void testRepeatBinder() throws RemoteException {
        IBinder binder = mInterface.asBinder();

        assertEquals(binder, mInterface.RepeatBinder(binder));
        assertEquals(binder, mInterface.RepeatNullableBinder(binder));
        assertEquals(null, mInterface.RepeatNullableBinder(null));
    }

    private static class Empty extends IEmpty.Stub {}

    @Test
    public void testRepeatInterface() throws RemoteException {
        IEmpty empty = new Empty();

        assertEquals(empty, mInterface.RepeatInterface(empty));
        assertEquals(empty, mInterface.RepeatNullableInterface(empty));
        assertEquals(null, mInterface.RepeatNullableInterface(null));
    }

    private static interface IRepeatFd {
        ParcelFileDescriptor repeat(ParcelFileDescriptor fd) throws RemoteException;
    }

    private void checkFdRepeated(IRepeatFd transformer) throws RemoteException, IOException {
        ParcelFileDescriptor[] sockets = ParcelFileDescriptor.createReliableSocketPair();
        ParcelFileDescriptor socketIn = sockets[0];
        ParcelFileDescriptor socketOut = sockets[1];

        ParcelFileDescriptor repeatFd = transformer.repeat(socketIn);

        boolean isNativeRemote = mInterface.GetName().equals("CPP");
        try {
            socketOut.checkError();

            // Either native didn't properly call detach, or native properly handles detach, and
            // we should change the test to enforce that socket comms work.
            assertFalse("Native doesn't implement comm fd but did not get detach.", isNativeRemote);
        } catch (ParcelFileDescriptor.FileDescriptorDetachedException e) {
            assertTrue("Detach, so remote should be native", isNativeRemote);
        }

        // Both backends support these.
        socketIn.checkError();
        repeatFd.checkError();

        checkInOutSockets(repeatFd, socketOut);
    }

    @Test
    public void testRepeatFd() throws RemoteException, IOException {
        checkFdRepeated((fd) -> mInterface.RepeatFd(fd));
    }

    @Test
    public void testRepeatFdNull() throws RemoteException {
        boolean isNativeRemote = mInterface.GetName().equals("CPP");

        try {
            mInterface.RepeatFd(null);
            assertFalse("Native shouldn't accept null here", isNativeRemote);
        } catch (java.lang.NullPointerException e) {
            assertTrue("Java should accept null here", isNativeRemote);
        }
    }

    @Test
    public void testRepeatNullableFd() throws RemoteException, IOException {
        checkFdRepeated((fd) -> mInterface.RepeatNullableFd(fd));
        assertEquals(null, mInterface.RepeatNullableFd(null));
    }

    private void checkInOutSockets(ParcelFileDescriptor in, ParcelFileDescriptor out) throws IOException {
        FileOutputStream repeatFdStream = new ParcelFileDescriptor.AutoCloseOutputStream(in);
        String testData = "asdf";
        byte[] output = testData.getBytes();
        repeatFdStream.write(output);
        repeatFdStream.close();

        FileInputStream fileInputStream = new ParcelFileDescriptor.AutoCloseInputStream(out);
        byte[] input = new byte[output.length];

        assertEquals(input.length, fileInputStream.read(input));
        Assert.assertArrayEquals(input, output);
    }

    @Test
    public void testRepeatFdArray() throws RemoteException, IOException {
        ParcelFileDescriptor[] sockets1 = ParcelFileDescriptor.createReliableSocketPair();
        ParcelFileDescriptor[] sockets2 = ParcelFileDescriptor.createReliableSocketPair();
        ParcelFileDescriptor[] inputs = {sockets1[0], sockets2[0]};
        ParcelFileDescriptor[] repeatFdArray = new ParcelFileDescriptor[inputs.length];
        mInterface.RepeatFdArray(inputs, repeatFdArray);

        checkInOutSockets(repeatFdArray[0], sockets1[1]);
        checkInOutSockets(repeatFdArray[1], sockets2[1]);
    }

    @Test
    public void testRepeatString() throws RemoteException {
        assertEquals("", mInterface.RepeatString(""));
        assertEquals("a", mInterface.RepeatString("a"));
        assertEquals("foo", mInterface.RepeatString("foo"));

        String stringWithNulls = "a\0df";
        assertEquals(stringWithNulls, mInterface.RepeatString(stringWithNulls));
    }

    @Test
    public void testRepeatNullableString() throws RemoteException {
        assertEquals(null, mInterface.RepeatNullableString(null));
        assertEquals("", mInterface.RepeatNullableString(""));
        assertEquals("a", mInterface.RepeatNullableString("a"));
        assertEquals("foo", mInterface.RepeatNullableString("foo"));
    }

    public void assertPolygonEquals(RegularPolygon lhs, RegularPolygon rhs) {
        assertEquals(lhs.name, rhs.name);
        assertEquals(lhs.numSides, rhs.numSides);
        assertEquals(lhs.sideLength, rhs.sideLength, 0.0f);
    }
    public void assertPolygonEquals(RegularPolygon[] lhs, RegularPolygon[] rhs) {
        assertEquals(lhs.length, rhs.length);
        for (int i = 0; i < lhs.length; i++) {
            assertPolygonEquals(lhs[i], rhs[i]);
        }
    }

    @Test
    public void testRepeatPolygon() throws RemoteException {
        RegularPolygon polygon = new RegularPolygon();
        polygon.name = "hexagon";
        polygon.numSides = 6;
        polygon.sideLength = 1.0f;

        RegularPolygon result = mInterface.RepeatPolygon(polygon);

        assertPolygonEquals(polygon, result);
    }

    @Test
    public void testRepeatUnexpectedNullPolygon() throws RemoteException {
        try {
           RegularPolygon result = mInterface.RepeatPolygon(null);
        } catch (NullPointerException e) {
           // non-@nullable C++ result can't handle null Polygon
           return;
        }
        // Java always works w/ nullptr
        assertEquals("JAVA", mExpectedName);
    }

    @Test
    public void testRepeatNullNullablePolygon() throws RemoteException {
        RegularPolygon result = mInterface.RepeatNullablePolygon(null);
        assertEquals(null, result);
    }

    @Test
    public void testRepeatDefaultPersistableBundle() throws RemoteException {
        PersistableBundle bundle = new PersistableBundle();
        PersistableBundle result = mInterface.RepeatPersistableBundle(bundle);
        assertNotEquals(null, result);
        assertEquals(bundle.size(), result.size());
    }

    @Test
    public void testRepeatTypesPersistableBundle() throws RemoteException {
        PersistableBundle bundle = new PersistableBundle();
        boolean[] boolV = {true, false, true};
        int[] intV = {123, 111, 321};
        long[] longV = {123, 111, 321};
        double[] doubleV = {123, 111, 321};
        String[] stringV = {"hello", "world", "!"};
        bundle.putBoolean("bool", true);
        bundle.putInt("int", 11111);
        bundle.putLong("long", 12345);
        bundle.putDouble("double", 54321);
        bundle.putString("string", "cool");
        bundle.putBooleanArray("boolv", boolV);
        bundle.putIntArray("intv", intV);
        bundle.putLongArray("longv", longV);
        bundle.putDoubleArray("doublev", doubleV);
        bundle.putStringArray("stringv", stringV);
        PersistableBundle innerBundle = new PersistableBundle();
        innerBundle.putBoolean("bool", true);
        bundle.putPersistableBundle("bundle", innerBundle);
        PersistableBundle result = mInterface.RepeatPersistableBundle(bundle);
        assertNotEquals(null, result);
        assertEquals(bundle.size(), result.size());
        assertEquals(true, result.getBoolean("bool"));
        assertEquals(11111, result.getInt("int"));
        assertEquals(12345, result.getLong("long"));
        assertEquals(54321, result.getDouble("double"), 0.0);
        assertEquals("cool", result.getString("string"));
        assertArrayEquals(boolV, result.getBooleanArray("boolv"));
        assertArrayEquals(intV, result.getIntArray("intv"));
        assertArrayEquals(longV, result.getLongArray("longv"));
        // no assertArrayEquals for double
        double[] ret = result.getDoubleArray("doublev");
        for (int i = 0; i < doubleV.length; i++) {
          assertEquals(doubleV[i], ret[i], 0.0);
        }
        assertArrayEquals(stringV, result.getStringArray("stringv"));
        assertEquals(innerBundle.size(), result.getPersistableBundle("bundle").size());
    }

    @Test
    public void testEraseAllPersistableBundle() throws RemoteException {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean("bool", true);
        bundle.putInt("int", 11111);
        bundle.putLong("long", 12345);
        bundle.putDouble("double", 54321);
        bundle.putString("string", "cool");
        assertNotEquals(0, bundle.size());
        bundle.remove("bool");
        bundle.remove("int");
        bundle.remove("long");
        bundle.remove("double");
        bundle.remove("string");
    }

    @Test
    public void testRepeatPresentNullablePolygon() throws RemoteException {
        RegularPolygon polygon = new RegularPolygon();
        polygon.name = "septagon";
        polygon.numSides = 7;
        polygon.sideLength = 9.0f;

        RegularPolygon result = mInterface.RepeatNullablePolygon(polygon);

        assertPolygonEquals(polygon, result);
    }

    @Test
    public void testInsAndOuts() throws RemoteException {
        RegularPolygon polygon = new RegularPolygon();
        mInterface.RenamePolygon(polygon, "Jerry");
        assertEquals("Jerry", polygon.name);
    }

    @Test
    public void testArrays() throws RemoteException {
        {
            boolean[] value = {};
            boolean[] out1 = new boolean[value.length];
            boolean[] out2 = mInterface.RepeatBooleanArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            boolean[] value = {false, true, false};
            boolean[] out1 = new boolean[value.length];
            boolean[] out2 = mInterface.RepeatBooleanArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            byte[] value = {1, 2, 3};
            byte[] out1 = new byte[value.length];
            byte[] out2 = mInterface.RepeatByteArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            char[] value = {'h', 'a', '!'};
            char[] out1 = new char[value.length];
            char[] out2 = mInterface.RepeatCharArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            int[] value = {1, 2, 3};
            int[] out1 = new int[value.length];
            int[] out2 = mInterface.RepeatIntArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            long[] value = {1, 2, 3};
            long[] out1 = new long[value.length];
            long[] out2 = mInterface.RepeatLongArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            float[] value = {1.0f, 2.0f, 3.0f};
            float[] out1 = new float[value.length];
            float[] out2 = mInterface.RepeatFloatArray(value, out1);

            Assert.assertArrayEquals(value, out1, 0.0f);
            Assert.assertArrayEquals(value, out2, 0.0f);
        }
        {
            double[] value = {1.0, 2.0, 3.0};
            double[] out1 = new double[value.length];
            double[] out2 = mInterface.RepeatDoubleArray(value, out1);

            Assert.assertArrayEquals(value, out1, 0.0);
            Assert.assertArrayEquals(value, out2, 0.0);
        }
        {
            byte[] value = {ByteEnum.FOO, ByteEnum.BAR};
            byte[] out1 = new byte[value.length];
            byte[] out2 = mInterface.RepeatByteEnumArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            int[] value = {IntEnum.FOO, IntEnum.BAR};
            int[] out1 = new int[value.length];
            int[] out2 = mInterface.RepeatIntEnumArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            long[] value = {LongEnum.FOO, LongEnum.BAR};
            long[] out1 = new long[value.length];
            long[] out2 = mInterface.RepeatLongEnumArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {
            String[] value = {"", "aoeu", "lol", "brb"};
            String[] out1 = new String[value.length];
            String[] out2 = mInterface.RepeatStringArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
        {

            RegularPolygon septagon = new RegularPolygon();
            septagon.name = "septagon";
            septagon.numSides = 7;
            septagon.sideLength = 1.0f;

            RegularPolygon[] value = {septagon, new RegularPolygon(), new RegularPolygon()};
            RegularPolygon[] out1 = new RegularPolygon[value.length];
            RegularPolygon[] out2 = mInterface.RepeatRegularPolygonArray(value, out1);

            assertPolygonEquals(value, out1);
            assertPolygonEquals(value, out2);
        }
    }

    @Test
    public void testLists() throws RemoteException {
        {
            List<String> value = Arrays.asList("", "aoeu", "lol", "brb");
            List<String> out1 = new ArrayList<>();
            List<String> out2 = mInterface.Repeat2StringList(value, out1);

            List<String> expected = new ArrayList<>();
            expected.addAll(value);
            expected.addAll(value);
            String[] expectedArray = expected.toArray(new String[0]);

            Assert.assertArrayEquals(expectedArray, out1.toArray(new String[0]));
            Assert.assertArrayEquals(expectedArray, out2.toArray(new String[0]));
        }
        {
            RegularPolygon septagon = new RegularPolygon();
            septagon.name = "septagon";
            septagon.numSides = 7;
            septagon.sideLength = 1.0f;

            List<RegularPolygon> value = Arrays.asList(septagon, new RegularPolygon(), new RegularPolygon());
            List<RegularPolygon> out1 = new ArrayList<>();
            List<RegularPolygon> out2 = mInterface.Repeat2RegularPolygonList(value, out1);

            List<RegularPolygon> expected = new ArrayList<>();
            expected.addAll(value);
            expected.addAll(value);
            RegularPolygon[] expectedArray = expected.toArray(new RegularPolygon[0]);

            assertPolygonEquals(expectedArray, out1.toArray(new RegularPolygon[0]));
            assertPolygonEquals(expectedArray, out1.toArray(new RegularPolygon[0]));
        }
    }

    @Test
    public void testNullableArrays() throws RemoteException {
        {
            boolean[] emptyValue = {};
            boolean[] value = {false, true, false};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableBooleanArray(null));
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableBooleanArray(emptyValue));
            Assert.assertArrayEquals(value, mInterface.RepeatNullableBooleanArray(value));
        }
        {
            byte[] emptyValue = {};
            byte[] value = {1, 2, 3};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableByteArray(null));
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableByteArray(emptyValue));
            Assert.assertArrayEquals(value, mInterface.RepeatNullableByteArray(value));
        }
        {
            char[] emptyValue = {};
            char[] value = {'h', 'a', '!'};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableCharArray(null));
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableCharArray(emptyValue));
            Assert.assertArrayEquals(value, mInterface.RepeatNullableCharArray(value));
        }
        {
            int[] emptyValue = {};
            int[] value = {1, 2, 3};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableIntArray(null));
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableIntArray(emptyValue));
            Assert.assertArrayEquals(value, mInterface.RepeatNullableIntArray(value));
        }
        {
            long[] emptyValue = {};
            long[] value = {1, 2, 3};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableLongArray(null));
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableLongArray(emptyValue));
            Assert.assertArrayEquals(value, mInterface.RepeatNullableLongArray(value));
        }
        {
            float[] emptyValue = {};
            float[] value = {1.0f, 2.0f, 3.0f};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableFloatArray(null), 0.0f);
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableFloatArray(emptyValue), 0.0f);
            Assert.assertArrayEquals(value, mInterface.RepeatNullableFloatArray(value), 0.0f);
        }
        {
            double[] emptyValue = {};
            double[] value = {1.0, 2.0, 3.0};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableDoubleArray(null), 0.0);
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableDoubleArray(emptyValue), 0.0);
            Assert.assertArrayEquals(value, mInterface.RepeatNullableDoubleArray(value), 0.0);
        }
        {
            byte[] emptyValue = {};
            byte[] value = {ByteEnum.FOO, ByteEnum.BAR};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableByteEnumArray(null));
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableByteEnumArray(emptyValue));
            Assert.assertArrayEquals(value, mInterface.RepeatNullableByteEnumArray(value));
        }
        {
            int[] emptyValue = {};
            int[] value = {IntEnum.FOO, IntEnum.BAR};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableIntEnumArray(null));
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableIntEnumArray(emptyValue));
            Assert.assertArrayEquals(value, mInterface.RepeatNullableIntEnumArray(value));
        }
        {
            long[] emptyValue = {};
            long[] value = {LongEnum.FOO, LongEnum.BAR};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableLongEnumArray(null));
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableLongEnumArray(emptyValue));
            Assert.assertArrayEquals(value, mInterface.RepeatNullableLongEnumArray(value));
        }
        {
            String[] emptyValue = {};
            String[] value = {"", "aoeu", null, "brb"};
            Assert.assertArrayEquals(null, mInterface.RepeatNullableStringArray(null));
            Assert.assertArrayEquals(emptyValue, mInterface.RepeatNullableStringArray(emptyValue));
            Assert.assertArrayEquals(value, mInterface.RepeatNullableStringArray(value));
        }
        {
            String[] emptyValue = {};
            String[] value = {"", "aoeu", null, "brb"};
            String[] out1 = new String[value.length];
            String[] out2 = mInterface.DoubleRepeatNullableStringArray(value, out1);

            Assert.assertArrayEquals(value, out1);
            Assert.assertArrayEquals(value, out2);
        }
    }

    @Test
    public void testGetLastItem() throws RemoteException {
        Foo foo = new Foo();
        foo.d = new Bar();
        foo.e = new Bar();
        foo.f = 15;
        foo.shouldContainTwoByteFoos = new byte[]{};
        foo.shouldContainTwoIntFoos = new int[]{};
        foo.shouldContainTwoLongFoos = new long[]{};
        foo.bundle1 = new PersistableBundle();
        foo.bundleArray = new PersistableBundle[]{};

        assertEquals(foo.f, mInterface.getF(foo));
    }

    @Test
    public void testRepeatFoo() throws RemoteException {
        Foo foo = new Foo();

        foo.a = "NEW FOO";
        foo.b = 57;

        foo.d = new Bar();
        foo.d.b = "a";

        foo.e = new Bar();
        foo.e.d = 99;

        foo.shouldBeByteBar = ByteEnum.BAR;
        foo.shouldBeIntBar = IntEnum.BAR;
        foo.shouldBeLongBar = LongEnum.BAR;

        foo.shouldContainTwoByteFoos = new byte[]{ByteEnum.FOO, ByteEnum.FOO};
        foo.shouldContainTwoIntFoos = new int[]{IntEnum.FOO, IntEnum.FOO};
        foo.shouldContainTwoLongFoos = new long[]{LongEnum.FOO, LongEnum.FOO};

        foo.bundle1 = new PersistableBundle();
        foo.bundleArray = new PersistableBundle[]{};

        foo.u = SimpleUnion.e(new byte[]{ByteEnum.FOO, ByteEnum.FOO});

        foo.shouldSetBit0AndBit2 = Foo.BIT0 | Foo.BIT2;
        foo.shouldBeConstS1 = SimpleUnion.c(SimpleUnion.S1);

        Foo repeatedFoo = mInterface.repeatFoo(foo);

        assertEquals(foo.a, repeatedFoo.a);
        assertEquals(foo.b, repeatedFoo.b);
        assertEquals(foo.d.b, repeatedFoo.d.b);
        assertEquals(foo.e.d, repeatedFoo.e.d);
        assertEquals(foo.shouldBeByteBar, repeatedFoo.shouldBeByteBar);
        assertEquals(foo.shouldBeIntBar, repeatedFoo.shouldBeIntBar);
        assertEquals(foo.shouldBeLongBar, repeatedFoo.shouldBeLongBar);
        Assert.assertArrayEquals(foo.shouldContainTwoByteFoos, repeatedFoo.shouldContainTwoByteFoos);
        Assert.assertArrayEquals(foo.shouldContainTwoIntFoos, repeatedFoo.shouldContainTwoIntFoos);
        Assert.assertArrayEquals(foo.shouldContainTwoLongFoos, repeatedFoo.shouldContainTwoLongFoos);
        Assert.assertArrayEquals(foo.u.getE(), repeatedFoo.u.getE());
        assertEquals(foo.shouldSetBit0AndBit2, repeatedFoo.shouldSetBit0AndBit2);
        assertEquals(foo.shouldBeConstS1.getC(), repeatedFoo.shouldBeConstS1.getC());
    }

    @Test
    public void testRepeatGenericBar() throws RemoteException {
      GenericBar<Integer> bar = new GenericBar<Integer>();
      bar.shouldBeGenericFoo = new GenericFoo<Integer, Bar, Integer>();
      bar.shouldBeGenericFoo.a = 41;
      bar.shouldBeGenericFoo.b = 42;
      GenericBar<Integer> repeatedBar = new GenericBar<Integer>();
      repeatedBar = mInterface.repeatGenericBar(bar);

      assertEquals(bar.a, repeatedBar.a);
      assertEquals(bar.shouldBeGenericFoo.a, repeatedBar.shouldBeGenericFoo.a);
      assertEquals(bar.shouldBeGenericFoo.b, repeatedBar.shouldBeGenericFoo.b);
    }

    @Test
    public void testNewField() throws RemoteException {
        Baz baz = new Baz();
        baz.d = new String[]{"a", "b", "c"};
        ICompatTest compatTest = ICompatTest.Stub.asInterface(mInterface.getICompatTest());
        Baz newBaz = compatTest.repeatBaz(baz);
        if (mShouldBeOld) {
            assertEquals(null, newBaz.d);
        } else {
            Assert.assertArrayEquals(baz.d, newBaz.d);
        }
    }

    @Test
    public void testGetInterfaceVersion() throws RemoteException {
        ICompatTest compatTest = ICompatTest.Stub.asInterface(mInterface.getICompatTest());
        if (mShouldBeOld) {
            assertEquals(1, compatTest.getInterfaceVersion());
        } else {
            assertTrue(compatTest.getInterfaceVersion() >= 2);
        }
    }

    @Test
    public void testGetInterfaceHash() throws RemoteException {
        ICompatTest compatTest = ICompatTest.Stub.asInterface(mInterface.getICompatTest());
        if (mShouldBeOld) {
            assertEquals("b663b681b3e0d66f9b5428c2f23365031b7d4ba0", compatTest.getInterfaceHash());
        } else {
            if (compatTest.getInterfaceVersion() == 2) {
                assertEquals("2740afaf3b5a0e739c44165c49633a0af87369f2",
                        compatTest.getInterfaceHash());
            } else {
                assertEquals("notfrozen", compatTest.getInterfaceHash());
            }
        }
    }

    @Test
    public void testLegacyBinder() throws RemoteException {
        ILegacyBinder compatTest = ILegacyBinder.Stub.asInterface(mInterface.getLegacyBinderTest());
        assertEquals(42, compatTest.RepeatInt(42));
    }

    @Test
    public void testRenameFoo() throws RemoteException {
        Foo foo = new Foo();
        foo.d = new Bar();
        foo.e = new Bar();
        foo.shouldContainTwoByteFoos = new byte[]{};
        foo.shouldContainTwoIntFoos = new int[]{};
        foo.shouldContainTwoLongFoos = new long[]{};
        foo.bundle1 = new PersistableBundle();
        foo.bundleArray = new PersistableBundle[]{};
        mInterface.renameFoo(foo, "MYFOO");
        assertEquals("MYFOO", foo.a);
    }

    @Test
    public void testRenameBar() throws RemoteException {
        Foo foo = new Foo();
        foo.d = new Bar();
        foo.e = new Bar();
        foo.shouldContainTwoByteFoos = new byte[]{};
        foo.shouldContainTwoIntFoos = new int[]{};
        foo.shouldContainTwoLongFoos = new long[]{};
        foo.bundle1 = new PersistableBundle();
        foo.bundleArray = new PersistableBundle[]{};
        mInterface.renameBar(foo, "MYBAR");
        assertEquals("MYBAR", foo.d.a);
    }

    @Test
    public void testRepeatStringNullableLater() throws RemoteException {
        // see notes in native NdkBinderTest_Aidl RepeatStringNullableLater
        boolean handlesNull = !mShouldBeOld || mExpectedName == "JAVA";
        ICompatTest compatTest = ICompatTest.Stub.asInterface(mInterface.getICompatTest());

        try {
            assertEquals(null, compatTest.RepeatStringNullableLater(null));
            assertTrue("should reach here if null is handled", handlesNull);
        } catch (NullPointerException e) {
            assertFalse("should reach here if null isn't handled", handlesNull);
        }
        assertEquals("", compatTest.RepeatStringNullableLater(""));
        assertEquals("a", compatTest.RepeatStringNullableLater("a"));
        assertEquals("foo", compatTest.RepeatStringNullableLater("foo"));
    }

    @Test
    public void testParcelableHolder() throws RemoteException {
        ExtendableParcelable ep = new ExtendableParcelable();
        ep.c = 42L;
        MyExt myext1 = new MyExt();
        myext1.a = 42;
        myext1.b = "mystr";
        ep.ext.setParcelable(myext1);

        ExtendableParcelable ep2 = new ExtendableParcelable();
        mInterface.RepeatExtendableParcelable(ep, ep2);
        MyExt myext2 = ep2.ext.getParcelable(MyExt.class);
        assertEquals(42L, ep2.c);
        assertNotEquals(null, myext2);
        assertEquals(42, myext2.a);
        assertEquals("mystr", myext2.b);
    }

    @Test
    public void testEmptyParcelableHolder() throws RemoteException {
        ExtendableParcelable ep = new ExtendableParcelable();
        ep.c = 42L;
        ExtendableParcelable ep2 = new ExtendableParcelable();
        mInterface.RepeatExtendableParcelableWithoutExtension(ep, ep2);
        assertEquals(42L, ep2.c);
    }

    @Test
    public void testRepeatSimpleUnion() throws RemoteException {
        final int[] intArray = { 1, 2, 3 };
        SimpleUnion origin = SimpleUnion.b(intArray);
        SimpleUnion ret = mInterface.RepeatSimpleUnion(origin);
        assertEquals(SimpleUnion.b, ret.getTag());
        Assert.assertArrayEquals(intArray, ret.getB());
    }
}
