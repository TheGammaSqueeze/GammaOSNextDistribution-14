/*
 * Copyright (C) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.aidl.tests;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray;
import android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable;
import android.aidl.tests.BadParcelable;
import android.aidl.tests.ByteEnum;
import android.aidl.tests.GenericStructuredParcelable;
import android.aidl.tests.INamedCallback;
import android.aidl.tests.ITestService;
import android.aidl.tests.ITestService.CompilerChecks;
import android.aidl.tests.IntEnum;
import android.aidl.tests.LongEnum;
import android.aidl.tests.SimpleParcelable;
import android.aidl.tests.StructuredParcelable;
import android.aidl.tests.Union;
import android.aidl.versioned.tests.IFooInterface;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Log;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestServiceClient {
    private ITestService service;
    private ICppJavaTests cpp_java_tests;

    @Before
    public void setUp() throws RemoteException {
        IBinder binder = ServiceManager.waitForService(ITestService.class.getName());
        assertNotNull(binder);
        service = ITestService.Stub.asInterface(binder);
        assertNotNull(service);

        IBinder binder2 = service.GetCppJavaTests();
        if (binder2 != null) {
            cpp_java_tests = ICppJavaTests.Stub.asInterface(binder2);
        }
    }

    @Test
    public void testBinderIdentity() throws RemoteException {
        IBinder binder = ServiceManager.waitForService(ITestService.class.getName());

        assertEquals(binder, service.asBinder());
    }

    @Test
    public void testOneway() throws RemoteException {
      service.TestOneway();
    }

    @Test
    public void testBooleanRepeat() throws RemoteException {
        boolean query = true;
        assertThat(service.RepeatBoolean(query), is(query));
    }

    @Test
    public void testCharRepeat() throws RemoteException {
        char query = 'A';
        assertThat(service.RepeatChar(query), is(query));
    }

    @Test
    public void testByteRepeat() throws RemoteException {
        byte query = -128;
        assertThat(service.RepeatByte(query), is(query));
    }

    @Test
    public void testIntRepeat() throws RemoteException {
        int query = 1 << 30;
        assertThat(service.RepeatInt(query), is(query));
    }

    @Test
    public void testConstRepeat() throws RemoteException {
        int query[] = {ITestService.CONSTANT, ITestService.CONSTANT2, ITestService.CONSTANT3,
            ITestService.CONSTANT4, ITestService.CONSTANT5, ITestService.CONSTANT6,
            ITestService.CONSTANT7, ITestService.CONSTANT8};
        for (int i = 0; i < query.length; i++) {
            assertThat(service.RepeatInt(query[i]), is(query[i]));
        }
    }

    @Test
    public void testConstFloatRepeat() throws RemoteException {
        float query[] = {ITestService.FLOAT_CONSTANT, ITestService.FLOAT_CONSTANT2,
            ITestService.FLOAT_CONSTANT3, ITestService.FLOAT_CONSTANT4,
            ITestService.FLOAT_CONSTANT5, ITestService.FLOAT_CONSTANT6,
            ITestService.FLOAT_CONSTANT7};
        for (int i = 0; i < query.length; i++) {
            assertThat(service.RepeatFloat(query[i]), is(query[i]));
        }
    }

    @Test
    public void testConstDoubleRepeat() throws RemoteException {
        double query[] = {ITestService.DOUBLE_CONSTANT, ITestService.DOUBLE_CONSTANT2,
            ITestService.DOUBLE_CONSTANT3, ITestService.DOUBLE_CONSTANT4,
            ITestService.DOUBLE_CONSTANT5, ITestService.DOUBLE_CONSTANT6,
            ITestService.DOUBLE_CONSTANT7};
        for (int i = 0; i < query.length; i++) {
            assertThat(service.RepeatDouble(query[i]), is(query[i]));
        }
    }

    @Test
    public void testLongRepeat() throws RemoteException {
        long query = 1L << 60;
        assertThat(service.RepeatLong(query), is(query));
    }

    @Test
    public void testFloatRepeat() throws RemoteException {
        float query = 1.0f/3.0f;
        assertThat(service.RepeatFloat(query), is(query));
    }

    @Test
    public void testDoubleRepeat() throws RemoteException {
        double query = 1.0/3.0;
        assertThat(service.RepeatDouble(query), is(query));
    }

    @Test
    public void testByteEnumRepeat() throws RemoteException {
        byte query = ByteEnum.FOO;
        assertThat(service.RepeatByteEnum(query), is(query));
    }

    @Test
    public void testIntEnumRepeat() throws RemoteException {
        int query = IntEnum.FOO;
        assertThat(service.RepeatIntEnum(query), is(query));
    }

    @Test
    public void testLongEnumRepeat() throws RemoteException {
        long query = LongEnum.FOO;
        assertThat(service.RepeatLongEnum(query), is(query));
    }

    @Test
    public void testStringListRepeat() throws RemoteException {
        List<String> queries = Arrays.asList(
            "not empty", "", "\0", ITestService.STRING_CONSTANT, ITestService.STRING_CONSTANT2);
        for (String query : queries) {
            assertThat(service.RepeatString(query), is(query));
        }
    }

    @Test
    public void testBooleanArrayReversal() throws RemoteException {
        boolean[] input = {true, false, false, false};
        boolean echoed[] = new boolean[input.length];
        boolean[] reversed = service.ReverseBoolean(input, echoed);
        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testByteArrayReversal() throws RemoteException {
        byte[] input = {0, 1, 2};
        byte echoed[] = new byte[input.length];
        byte[] reversed = service.ReverseByte(input, echoed);
        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testCharArrayReversal() throws RemoteException {
        char[] input = {'A', 'B', 'C', 'D', 'E'};
        char echoed[] = new char[input.length];
        char[] reversed = service.ReverseChar(input, echoed);
        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testIntArrayReversal() throws RemoteException {
        int[] input = {-1, 0, 1, 2, 3, 4, 5, 6};
        int echoed[] = new int[input.length];
        int[] reversed = service.ReverseInt(input, echoed);
        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testLongArrayReversal() throws RemoteException {
        long[] input = {-2, -1, 0, 1, 2, 3, 4, 5, 6, 7, 8};
        long echoed[] = new long[input.length];
        long[] reversed = service.ReverseLong(input, echoed);
        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testFloatArrayReversal() throws RemoteException {
        float[] input = {0.0f, 1.0f, -0.3f};
        float echoed[] = new float[input.length];
        float[] reversed = service.ReverseFloat(input, echoed);
        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testDoubleArrayReversal() throws RemoteException {
        double[] input = {-1.0, -4.0, -2.0};
        double echoed[] = new double[input.length];
        double[] reversed = service.ReverseDouble(input, echoed);
        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testStringArrayReversal() throws RemoteException {
        String[] input = {"For", "relaxing", "times"};
        String echoed[] = new String[input.length];
        String[] reversed = service.ReverseString(input, echoed);
        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testBinderExchange() throws RemoteException {
        INamedCallback got = service.GetOtherTestService("Smythe");
        assertThat(got.GetName(), is("Smythe"));

        assertThat(service.VerifyName(got, "Smythe"), is(true));
    }

    @Test
    public void testBinderArrayExchange() throws RemoteException {
      String[] names = {"Fizz", "Buzz"};
      INamedCallback[] got = service.GetInterfaceArray(names);
      assertThat(got[0].GetName(), is(names[0]));
      assertThat(got[1].GetName(), is(names[1]));

      assertThat(service.VerifyNamesWithInterfaceArray(got, names), is(true));
    }

    @Test
    public void testNullableBinderArrayExchange() throws RemoteException {
      String[] names = {"Fizz", null, "Buzz"};
      INamedCallback[] got = service.GetNullableInterfaceArray(names);
      assertThat(got[0].GetName(), is(names[0]));
      assertNull(got[1]);
      assertThat(got[2].GetName(), is(names[2]));

      assertThat(service.VerifyNamesWithNullableInterfaceArray(got, names), is(true));
    }

    @Test
    public void testInterfaceListExchange() throws RemoteException {
      String[] names = {"Fizz", null, "Buzz"};
      List<INamedCallback> got = service.GetInterfaceList(names);
      assertThat(got.get(0).GetName(), is(names[0]));
      assertNull(got.get(1));
      assertThat(got.get(2).GetName(), is(names[2]));

      assertThat(service.VerifyNamesWithInterfaceList(got, names), is(true));
    }

    @Test
    public void testListReversal() throws RemoteException {
        List<String> input = Arrays.asList("Walk", "into", "Córdoba");
        List<String> echoed = new ArrayList<String>();
        List<String> reversed = service.ReverseStringList(input, echoed);
        assertThat(echoed, is(input));

        Collections.reverse(input);
        assertThat(reversed, is(input));
    }

    @Test
    public void testRepeatGenericParcelable() throws RemoteException {
      assumeTrue(cpp_java_tests != null);

      GenericStructuredParcelable<Integer, StructuredParcelable, Integer> input =
          new GenericStructuredParcelable<Integer, StructuredParcelable, Integer>();
      GenericStructuredParcelable<Integer, StructuredParcelable, Integer> out_param =
          new GenericStructuredParcelable<Integer, StructuredParcelable, Integer>();
      input.a = 41;
      input.b = 42;
      GenericStructuredParcelable<Integer, StructuredParcelable, Integer> testing = input;
      assertThat(testing, is(input));
      GenericStructuredParcelable<Integer, StructuredParcelable, Integer> returned =
          cpp_java_tests.RepeatGenericParcelable(input, out_param);
      assertThat(out_param.a, is(input.a));
      assertThat(out_param.b, is(input.b));
      assertThat(returned.a, is(input.a));
      assertThat(returned.b, is(input.b));
    }

    @Test
    public void testBadParcelable() throws RemoteException {
      assumeTrue(cpp_java_tests != null);
      BadParcelable bad = new BadParcelable(/*bad=*/true, "foo", 42);
      Throwable error =
          assertThrows(BadParcelableException.class, () -> cpp_java_tests.RepeatBadParcelable(bad));
      assertTrue(error.getMessage().contains("Parcel data not fully consumed"));

      BadParcelable notBad = new BadParcelable(/*bad=*/false, "foo", 42);
      BadParcelable output = cpp_java_tests.RepeatBadParcelable(notBad);
      assertThat(notBad, is(output));
    }

    @Test
    public void testRepeatParcelable() throws RemoteException {
      SimpleParcelable input = new SimpleParcelable("foo", 42);
      SimpleParcelable out_param = new SimpleParcelable();
      SimpleParcelable returned = service.RepeatSimpleParcelable(input, out_param);
      assertThat(out_param, is(input));
      assertThat(returned, is(input));
    }

    @Test
    public void testReverseParcelable() throws RemoteException {
      SimpleParcelable[] input = new SimpleParcelable[3];
      input[0] = new SimpleParcelable("a", 1);
      input[1] = new SimpleParcelable("b", 2);
      input[2] = new SimpleParcelable("c", 3);
      SimpleParcelable[] repeated = new SimpleParcelable[3];
      SimpleParcelable[] reversed = service.ReverseSimpleParcelables(input, repeated);
      assertThat(repeated, is(input));
      assertThat(reversed.length, is(input.length));
      for (int i = 0, k = input.length - 1; i < input.length; ++i, --k) {
            assertThat(reversed[k], is(input[i]));
      }
    }

    @Test
    public void testRepeatEmptyPersistableBundle() throws RemoteException {
        assumeTrue(cpp_java_tests != null);

        PersistableBundle emptyBundle = new PersistableBundle();
        PersistableBundle returned = cpp_java_tests.RepeatPersistableBundle(emptyBundle);
        assertThat(returned.size(), is(emptyBundle.size()));
        assertThat(returned.toString(), is(emptyBundle.toString()));
    }

    @Test
    public void testRepeatNonEmptyPersistableBundle() throws RemoteException {
        assumeTrue(cpp_java_tests != null);

        PersistableBundle pb = new PersistableBundle();

        final String testBoolKey = "testBool";
        final String testIntKey = "testInt";
        final String testNestedIntKey = "testNestedInt";
        final String testLongKey = "testLong";
        final String testDoubleKey = "testDouble";
        final String testStringKey = "testString";
        final String testBoolArrayKey = "testBoolArray";
        final String testIntArrayKey = "testIntArray";
        final String testLongArrayKey = "testLongArray";
        final String testDoubleArrayKey = "testDoubleArray";
        final String testStringArrayKey = "testStringArray";
        final String testPersistableBundleKey = "testPersistableBundle";

        pb.putBoolean(testBoolKey, false);
        pb.putInt(testIntKey, 33);
        pb.putLong(testLongKey, 34359738368L);
        pb.putDouble(testDoubleKey, 1.1);
        pb.putString(testStringKey, new String("Woot!"));
        pb.putBooleanArray(testBoolArrayKey, new boolean[] {true, false, true});
        pb.putIntArray(testIntArrayKey, new int[] {33, 44, 55, 142});
        pb.putLongArray(testLongArrayKey, new long[] {34L, 8371L, 34359738375L});
        pb.putDoubleArray(testDoubleArrayKey, new double[] {2.2, 5.4});
        pb.putStringArray(testStringArrayKey, new String[] {"hello", "world!"});
        PersistableBundle testNestedPersistableBundle = new PersistableBundle();
        testNestedPersistableBundle.putInt(testNestedIntKey, 345);
        pb.putPersistableBundle(testPersistableBundleKey, testNestedPersistableBundle);

        PersistableBundle ret = cpp_java_tests.RepeatPersistableBundle(pb);

        assertThat(ret.size(), is(pb.size()));
        assertThat(ret.getBoolean(testBoolKey), is(pb.getBoolean(testBoolKey)));
        assertThat(ret.getInt(testIntKey), is(pb.getInt(testIntKey)));
        assertThat(ret.getLong(testLongKey), is(pb.getLong(testLongKey)));
        assertThat(ret.getDouble(testDoubleKey), is(pb.getDouble(testDoubleKey)));
        assertThat(ret.getString(testStringKey), is(pb.getString(testStringKey)));
        assertThat(ret.getBooleanArray(testBoolArrayKey), is(pb.getBooleanArray(testBoolArrayKey)));
        assertThat(ret.getIntArray(testIntArrayKey), is(pb.getIntArray(testIntArrayKey)));
        assertThat(ret.getLongArray(testLongArrayKey), is(pb.getLongArray(testLongArrayKey)));
        assertThat(ret.getDoubleArray(testDoubleArrayKey), is(pb.getDoubleArray(testDoubleArrayKey)));
        assertThat(ret.getStringArray(testStringArrayKey), is(pb.getStringArray(testStringArrayKey)));

        PersistableBundle nested = ret.getPersistableBundle(testPersistableBundleKey);
        assertThat(nested, is(notNullValue()));
        assertThat(nested.getInt(testNestedIntKey), is(testNestedPersistableBundle.getInt(testNestedIntKey)));
    }

    @Test
    public void testReversePersistableBundleArray() throws RemoteException {
        assumeTrue(cpp_java_tests != null);

        PersistableBundle[] input = new PersistableBundle[3];
        PersistableBundle first = new PersistableBundle();
        PersistableBundle second = new PersistableBundle();
        PersistableBundle third = new PersistableBundle();
        final String testIntKey = new String("testInt");
        final String testLongKey = new String("testLong");
        final String testDoubleKey = new String("testDouble");
        first.putInt(testIntKey, 1231);
        second.putLong(testLongKey, 222222L);
        third.putDouble(testDoubleKey, 10.8);
        input[0] = first;
        input[1] = second;
        input[2] = third;
        final int original_input_size = input.length;

        PersistableBundle[] repeated = new PersistableBundle[input.length];
        PersistableBundle[] reversed = cpp_java_tests.ReversePersistableBundles(input, repeated);

        assertThat(repeated.length, is(input.length));
        assertThat(input.length, is(original_input_size));
        assertThat(repeated[0].getInt(testIntKey), is(input[0].getInt(testIntKey)));
        assertThat(repeated[1].getLong(testLongKey), is(input[1].getLong(testLongKey)));
        assertThat(repeated[2].getDouble(testDoubleKey), is(input[2].getDouble(testDoubleKey)));

        assertThat(reversed.length, is(input.length));
        assertThat(reversed[0].getInt(testIntKey), is(input[2].getInt(testIntKey)));
        assertThat(reversed[1].getLong(testLongKey), is(input[1].getLong(testLongKey)));
        assertThat(reversed[2].getDouble(testDoubleKey), is(input[0].getDouble(testDoubleKey)));
    }

    private void writeToFd(FileDescriptor fd, byte[] testData) throws IOException {
      FileOutputStream fdStream = new FileOutputStream(fd);
      fdStream.write(testData);
      fdStream.close();
    }

    private void verifyFileContents(String file, byte[] testData) throws IOException {
      FileInputStream fis = new FileInputStream(file);
      byte[] readData = new byte[testData.length];

      assertThat(fis.read(readData), is(readData.length));
      assertThat(readData, is(testData));
      assertThat(fis.read(), is(-1));
    }

    @Test
    public void testFileDescriptorPassing() throws RemoteException, IOException {
        assumeTrue(cpp_java_tests != null);

        String file = "/data/local/tmp/aidl-test-file";
        FileOutputStream fos = new FileOutputStream(file, false /*append*/);

        FileDescriptor descriptor = fos.getFD();
        FileDescriptor journeyed = cpp_java_tests.RepeatFileDescriptor(descriptor);
        fos.close();

        String testData = "FrazzleSnazzleFlimFlamFlibbityGumboChops";
        writeToFd(journeyed, testData.getBytes());
        verifyFileContents(file, testData.getBytes());
    }

    @Test
    public void testFileDescriptorArrayPassing() throws RemoteException, IOException {
      assumeTrue(cpp_java_tests != null);

      final int kTestSize = 2;

      String fileBase = "/data/local/tmp/aidl-test-file_";
      String[] files = new String[kTestSize]; // any size to test
      FileOutputStream[] fos = new FileOutputStream[kTestSize];
      FileDescriptor[] descriptors = new FileDescriptor[kTestSize];
      for (int i = 0; i < kTestSize; i++) {
        files[i] = fileBase + i;
        fos[i] = new FileOutputStream(files[i], false /*append*/);
        descriptors[i] = fos[i].getFD();
      }

      FileDescriptor[] repeated = new FileDescriptor[kTestSize];
      FileDescriptor[] reversed = cpp_java_tests.ReverseFileDescriptorArray(descriptors, repeated);

      for (int i = 0; i < kTestSize; i++) {
        fos[i].close();
      }

      for (int i = 0; i < kTestSize; i++) {
        String testData = "Something " + i;

        writeToFd(reversed[kTestSize - 1 - i], testData.getBytes());
        verifyFileContents(files[i], testData.getBytes());

        writeToFd(repeated[i], testData.getBytes());
        verifyFileContents(files[i], (testData + testData).getBytes());
      }
    }

    @Test
    public void testParcelFileDescriptorPassing() throws RemoteException, IOException {
        String file = "/data/local/tmp/aidl-test-file";
        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                new File(file), ParcelFileDescriptor.MODE_CREATE |
                    ParcelFileDescriptor.MODE_WRITE_ONLY);
        ParcelFileDescriptor journeyed = service.RepeatParcelFileDescriptor(descriptor);

        FileOutputStream journeyedStream = new ParcelFileDescriptor.AutoCloseOutputStream(journeyed);

        String testData = "FrazzleSnazzleFlimFlamFlibbityGumboChops";
        byte[] output = testData.getBytes();
        journeyedStream.write(output);
        journeyedStream.close();

        FileInputStream fis = new FileInputStream(file);
        byte[] input = new byte[output.length];

        assertThat(fis.read(input), is(input.length));
        assertThat(input, is(output));
    }

    @Test
    public void testServiceSpecificExceptions() throws RemoteException {
        for (int i = -1; i < 2; ++i) {
            try {
                service.ThrowServiceException(i);
            } catch (ServiceSpecificException ex) {
                assertThat(ex.errorCode, is(i));
            }
        }
    }

    private static final List<String> utf8_queries = Arrays.asList(
            "typical string",
            "",
            "\0\0\0",
            // Java doesn't handle unicode code points above U+FFFF well.
            new String(Character.toChars(0x1F701)) + "\u03A9");

    @Test
    public void testRepeatUtf8String() throws RemoteException {
        for (String query : utf8_queries) {
            String response = service.RepeatUtf8CppString(query);
            assertThat(response, is(query));
        }
    }

    @Test
    public void testReverseUtf8StringArray() throws RemoteException {
        String[] input = utf8_queries.toArray(new String[0]);
        String echoed[] = new String[input.length];

        String[] reversed = service.ReverseUtf8CppString(input, echoed);

        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testReverseNullableUtf8StringArray() throws RemoteException {
        final List<String> utf8_queries_and_nulls = Arrays.asList(
                "typical string",
                null,
                "",
                "\0\0\0",
                null,
                // Java doesn't handle unicode code points above U+FFFF well.
                new String(Character.toChars(0x1F701)) + "\u03A9");
        String[] input = utf8_queries_and_nulls.toArray(new String[0]);
        String echoed[] = new String[input.length];

        String[] reversed = service.ReverseNullableUtf8CppString(input, echoed);

        assertThat(echoed, is(input));
        assertThat(reversed.length, is(input.length));
        for (int i = 0; i < input.length; ++i) {
            int j = reversed.length - (1 + i);
            if (input[i] == null && reversed[j] == null) {
                continue;
            }
            assertThat(reversed[j], is(input[i]));
        }
    }

    @Test
    public void testOutArrayWithNull() throws RemoteException {
        // In Java out-array parameter can't be null. The proxy code always throws NPE.
        assertThrows(NullPointerException.class, () -> service.ReverseUtf8CppString(null, null));
        assertThrows(NullPointerException.class,
            () -> service.ReverseNullableUtf8CppString(null, null));
    }

    private void shouldBeTheSame(StructuredParcelable a, StructuredParcelable b) {
        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertTrue(a.equals(a));
        assertTrue(b.equals(b));
        assertTrue(a.hashCode() == b.hashCode());
    }

    private void shouldBeDifferent(StructuredParcelable a, StructuredParcelable b) {
        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
        assertFalse(a.hashCode() == b.hashCode());
    }

    @Test
    public void testStructuredParcelableEquality() {
        StructuredParcelable p = new StructuredParcelable();
        p.shouldContainThreeFs = new int[] {1, 2, 3};
        p.shouldBeJerry = "Jerry";

        StructuredParcelable p2 = new StructuredParcelable();
        p2.shouldContainThreeFs = new int[] {1, 2, 3};
        p2.shouldBeJerry = "Jerry";
        shouldBeTheSame(p, p2);

        StructuredParcelable p3 = new StructuredParcelable();
        p3.shouldContainThreeFs = new int[] {1, 2, 3, 4};
        p3.shouldBeJerry = "Jerry";
        shouldBeDifferent(p, p3);

        StructuredParcelable p4 = new StructuredParcelable();
        p4.shouldContainThreeFs = new int[] {1, 2, 3};
        p4.shouldBeJerry = "Tom";
        shouldBeDifferent(p, p4);
    }

    @Test
    public void testStructuredParcelable() throws RemoteException {
        final int kDesiredFValue = 17;

        StructuredParcelable p = new StructuredParcelable();
        p.shouldContainThreeFs = new int[0];
        p.f = kDesiredFValue;
        p.shouldBeJerry = "";
        p.shouldContainTwoByteFoos = new byte[2];
        p.shouldContainTwoIntFoos = new int[2];
        p.shouldContainTwoLongFoos = new long[2];
        p.empty = new StructuredParcelable.Empty();

        // Check the default values
        assertThat(p.stringDefaultsToFoo, is("foo"));
        final byte byteFour = 4;
        assertThat(p.byteDefaultsToFour, is(byteFour));
        assertThat(p.intDefaultsToFive, is(5));
        assertThat(p.longDefaultsToNegativeSeven, is(-7L));
        assertThat(p.booleanDefaultsToTrue, is(true));
        assertThat(p.charDefaultsToC, is('C'));
        assertThat(p.floatDefaultsToPi, is(3.14f));
        assertThat(p.doubleWithDefault, is(-3.14e17));
        assertThat(p.arrayDefaultsTo123, is(new int[] {1, 2, 3}));
        assertThat(p.arrayDefaultsToEmpty.length, is(0));
        assertThat(p.defaultWithFoo, is(IntEnum.FOO));

        service.FillOutStructuredParcelable(p);

        // Check the returned values
        assertThat(p.shouldContainThreeFs, is(new int[] {kDesiredFValue, kDesiredFValue, kDesiredFValue}));
        assertThat(p.shouldBeJerry, is("Jerry"));
        assertThat(p.shouldBeByteBar, is(ByteEnum.BAR));
        assertThat(p.shouldBeIntBar, is(IntEnum.BAR));
        assertThat(p.shouldBeLongBar, is(LongEnum.BAR));
        assertThat(p.shouldContainTwoByteFoos, is(new byte[] {ByteEnum.FOO, ByteEnum.FOO}));
        assertThat(p.shouldContainTwoIntFoos, is(new int[] {IntEnum.FOO, IntEnum.FOO}));
        assertThat(p.shouldContainTwoLongFoos, is(new long[] {LongEnum.FOO, LongEnum.FOO}));
        assertThat(p.int32_min, is(Integer.MIN_VALUE));
        assertThat(p.int32_max, is(Integer.MAX_VALUE));
        assertThat(p.int64_max, is(Long.MAX_VALUE));
        assertThat(p.hexInt32_neg_1, is(-1));
        for (int ndx = 0; ndx < p.int8_1.length; ndx++) {
          assertThat(p.int8_1[ndx], is((byte) 1));
        }
        for (int ndx = 0; ndx < p.int32_1.length; ndx++) {
            assertThat(p.int32_1[ndx], is(1));
        }
        for (int ndx = 0; ndx < p.int64_1.length; ndx++) {
            assertThat(p.int64_1[ndx], is(1L));
        }
        assertThat(p.hexInt32_pos_1, is(1));
        assertThat(p.hexInt64_pos_1, is(1));
        assertThat(p.const_exprs_1, is(1));
        assertThat(p.const_exprs_2, is(1));
        assertThat(p.const_exprs_3, is(1));
        assertThat(p.const_exprs_4, is(1));
        assertThat(p.const_exprs_5, is(1));
        assertThat(p.const_exprs_6, is(1));
        assertThat(p.const_exprs_7, is(1));
        assertThat(p.const_exprs_8, is(1));
        assertThat(p.const_exprs_9, is(1));
        assertThat(p.const_exprs_10, is(1));

        assertThat(
            p.shouldSetBit0AndBit2, is(StructuredParcelable.BIT0 | StructuredParcelable.BIT2));

        assertThat(p.u.getNs(), is(new int[] {1, 2, 3}));
        assertThat(p.shouldBeConstS1.getS(), is(Union.S1));

        final String expected = "StructuredParcelable{"
            + "shouldContainThreeFs: [17, 17, 17], "
            + "f: 17, "
            + "shouldBeJerry: Jerry, "
            + "shouldBeByteBar: 2, "
            + "shouldBeIntBar: BAR, "
            + "shouldBeLongBar: 200000000000, "
            + "shouldContainTwoByteFoos: [1, 1], "
            + "shouldContainTwoIntFoos: [FOO, FOO], "
            + "shouldContainTwoLongFoos: [100000000000, 100000000000], "
            + "stringDefaultsToFoo: foo, "
            + "byteDefaultsToFour: 4, "
            + "intDefaultsToFive: 5, "
            + "longDefaultsToNegativeSeven: -7, "
            + "booleanDefaultsToTrue: true, "
            + "charDefaultsToC: C, "
            + "floatDefaultsToPi: 3.14, "
            + "doubleWithDefault: -3.14E17, "
            + "arrayDefaultsTo123: [1, 2, 3], "
            + "arrayDefaultsToEmpty: [], "
            + "boolDefault: false, "
            + "byteDefault: 0, "
            + "intDefault: 0, "
            + "longDefault: 0, "
            + "floatDefault: 0.0, "
            + "doubleDefault: 0.0, "
            + "checkDoubleFromFloat: 3.14, "
            + "checkStringArray1: [a, b], "
            + "checkStringArray2: [a, b], "
            + "int32_min: -2147483648, "
            + "int32_max: 2147483647, "
            + "int64_max: 9223372036854775807, "
            + "hexInt32_neg_1: -1, "
            + "ibinder: null, "
            + "empty: Empty{}, "
            + "int8_1: [1, 1, 1, 1, 1], "
            + "int32_1: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, "
            + "1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, "
            + "1, 1, 1, 1, 1, 1, 1, 1, 1, 1], "
            + "int64_1: [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1], "
            + "hexInt32_pos_1: 1, "
            + "hexInt64_pos_1: 1, "
            + "const_exprs_1: 1, "
            + "const_exprs_2: 1, "
            + "const_exprs_3: 1, "
            + "const_exprs_4: 1, "
            + "const_exprs_5: 1, "
            + "const_exprs_6: 1, "
            + "const_exprs_7: 1, "
            + "const_exprs_8: 1, "
            + "const_exprs_9: 1, "
            + "const_exprs_10: 1, "
            + "addString1: hello world!, "
            + "addString2: The quick brown fox jumps over the lazy dog., "
            + "shouldSetBit0AndBit2: 5, "
            + "u: Union.ns([1, 2, 3]), "
            + "shouldBeConstS1: Union.s(a string constant in union), "
            + "defaultWithFoo: FOO"
            + "}";
        assertThat(p.toString(), is(expected));
    }

    @Test
    public void testDefaultImpl() throws RemoteException {
      final int expectedArg = 100;
      final int expectedReturnValue = 200;

      boolean success = ITestService.Stub.setDefaultImpl(new ITestService.Default() {
        @Override
        public int UnimplementedMethod(int arg) throws RemoteException {
          if (arg != expectedArg) {
            throw new RemoteException("Argument for UnimplementedMethod is expected "
                + " to be " + expectedArg + ", but got " + arg);
          }
          return expectedReturnValue;
        }
      });
      assertThat(success, is(true));

      int ret = service.UnimplementedMethod(expectedArg);
      assertThat(ret, is(expectedReturnValue));
    }

    @Test
    public void testToString() {
        ParcelableForToString p = new ParcelableForToString();
        p.intValue = 10;
        p.intArray = new int[]{20, 30};
        p.longValue = 100L;
        p.longArray = new long[]{200L, 300L};
        p.doubleValue = 3.14d;
        p.doubleArray = new double[]{1.1d, 1.2d};
        p.floatValue = 3.14f;
        p.floatArray = new float[]{1.1f, 1.2f};
        p.byteValue = 3;
        p.byteArray = new byte[]{5, 6};
        p.booleanValue = true;
        p.booleanArray = new boolean[]{true, false};
        p.stringValue = "this is a string";
        p.stringArray = new String[]{"hello", "world"};
        p.stringList = Arrays.asList(new String[]{"alice", "bob"});
        OtherParcelableForToString op = new OtherParcelableForToString();
        op.field = "other";
        p.parcelableValue = op;
        p.parcelableArray = new OtherParcelableForToString[]{op, op};
        p.enumValue = IntEnum.FOO;
        p.enumArray = new int[]{IntEnum.FOO, IntEnum.BAR};
        p.nullArray = null;
        p.nullList = null;
        GenericStructuredParcelable<Integer, StructuredParcelable, Integer> gen =
            new GenericStructuredParcelable<Integer, StructuredParcelable, Integer>();
        gen.a = 1;
        gen.b = 2;
        p.parcelableGeneric = gen;
        p.unionValue = null; // for testing even though it is not @nullable in .aidl

        final String expected = "ParcelableForToString{"
            + "intValue: 10, "
            + "intArray: [20, 30], "
            + "longValue: 100, "
            + "longArray: [200, 300], "
            + "doubleValue: 3.14, "
            + "doubleArray: [1.1, 1.2], "
            + "floatValue: 3.14, "
            + "floatArray: [1.1, 1.2], "
            + "byteValue: 3, "
            + "byteArray: [5, 6], "
            + "booleanValue: true, "
            + "booleanArray: [true, false], "
            + "stringValue: this is a string, "
            + "stringArray: [hello, world], "
            + "stringList: [alice, bob], "
            + "parcelableValue: OtherParcelableForToString{field: other}, "
            + "parcelableArray: ["
            + "OtherParcelableForToString{field: other}, "
            + "OtherParcelableForToString{field: other}], "
            + "enumValue: FOO, "
            + "enumArray: [FOO, BAR], "
            + "nullArray: null, "
            + "nullList: null, "
            + "parcelableGeneric: GenericStructuredParcelable{a: 1, b: 2}, "
            + "unionValue: null"
            + "}";

        assertThat(p.toString(), is(expected));
    }

    @Test
    public void testEnumToString() {
      assertThat(IntEnum.$.toString(IntEnum.FOO), is("FOO"));
      assertThat(IntEnum.$.toString(0), is("0"));
      assertThat(IntEnum.$.arrayToString(null), is("null"));
      assertThat(IntEnum.$.arrayToString(new int[] {}), is("[]"));
      assertThat(IntEnum.$.arrayToString(new int[] {IntEnum.FOO, IntEnum.BAR}), is("[FOO, BAR]"));
      assertThat(IntEnum.$.arrayToString(new int[] {IntEnum.FOO, 0}), is("[FOO, 0]"));
      assertThat(IntEnum.$.arrayToString(new int[][] {{IntEnum.FOO, IntEnum.BAR}, {IntEnum.BAZ}}),
          is("[[FOO, BAR], [BAZ]]"));
      assertThrows(IllegalArgumentException.class, () -> IntEnum.$.arrayToString(IntEnum.FOO));
      assertThrows(
          IllegalArgumentException.class, () -> IntEnum.$.arrayToString(new long[] {LongEnum.FOO}));
    }

    @Test
    public void testRenamedInterface() throws RemoteException {
      IOldName oldAsOld = service.GetOldNameInterface();
      assertNotNull(oldAsOld);
      assertThat(oldAsOld.DESCRIPTOR, is("android.aidl.tests.IOldName"));
      assertThat(oldAsOld.RealName(), is("OldName"));

      INewName newAsNew = service.GetNewNameInterface();
      assertNotNull(newAsNew);
      assertThat(newAsNew.DESCRIPTOR, is("android.aidl.tests.IOldName"));
      assertThat(oldAsOld.RealName(), is("OldName"));

      IOldName newAsOld = IOldName.Stub.asInterface(service.GetNewNameInterface().asBinder());
      assertNotNull(newAsOld);
      assertThat(newAsOld.DESCRIPTOR, is("android.aidl.tests.IOldName"));
      assertThat(newAsOld.RealName(), is("NewName"));

      INewName oldAsNew = INewName.Stub.asInterface(service.GetOldNameInterface().asBinder());
      assertNotNull(oldAsNew);
      assertThat(oldAsNew.DESCRIPTOR, is("android.aidl.tests.IOldName"));
      assertThat(oldAsNew.RealName(), is("OldName"));
    }

    @Test
    public void testReverseUnion() throws RemoteException {
      assumeTrue(cpp_java_tests != null);

      Union original = Union.ns(new int[] {1, 2, 3});
      Union repeated = new Union();

      Union reversed = cpp_java_tests.ReverseUnion(original, repeated);

      assertNotNull(reversed);
      assertThat(repeated.getNs(), is(new int[] {1, 2, 3}));
      assertThat(reversed.getNs(), is(new int[] {3, 2, 1}));
    }

    @Test
    public void testReverseRecursiveList() throws RemoteException {
      RecursiveList head = null;
      for (int i = 0; i < 10; i++) {
        RecursiveList node = new RecursiveList();
        node.value = i;
        node.next = head;
        head = node;
      }
      // head: [9, 8, .. , 0]
      RecursiveList reversed = service.ReverseList(head);
      // reversed should be [0, 1, .. 9]
      for (int i = 0; i < 10; i++) {
        assertThat(reversed.value, is(i));
        reversed = reversed.next;
      }
      assertNull(reversed);
    }

    @Test
    public void testGetUnionTags() throws RemoteException {
      assertArrayEquals(new int[] {}, service.GetUnionTags(new Union[] {}));
      assertArrayEquals(new int[] {Union.n, Union.ns},
          service.GetUnionTags(new Union[] {Union.n(0), Union.ns(new int[] {})}));
    }

    @Test
    public void testDescribeContents() throws Exception {
      CompilerChecks cc = new CompilerChecks();
      cc.pfd_array = new ParcelFileDescriptor[] {null, null, null};
      assertThat(cc.describeContents(), is(0));

      String file = "/data/local/tmp/aidl-test-file";
      cc.pfd_array[1] = ParcelFileDescriptor.open(
          new File(file), ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_WRITE_ONLY);
      assertThat(cc.describeContents(), is(Parcelable.CONTENTS_FILE_DESCRIPTOR));
    }

    @Test
    public void testFixedSizeArrayOverBinder() throws Exception {
      IBinder binder = ServiceManager.waitForService(IRepeatFixedSizeArray.DESCRIPTOR);
      assertNotNull(binder);
      IRepeatFixedSizeArray service = IRepeatFixedSizeArray.Stub.asInterface(binder);
      assertNotNull(service);

      {
        byte[] input = new byte[] {1, 2, 3};
        byte[] repeated = new byte[3];
        byte[] output = service.RepeatBytes(input, repeated);
        assertArrayEquals(input, repeated);
        assertArrayEquals(input, output);
      }
      {
        int[] input = new int[] {1, 2, 3};
        int[] repeated = new int[3];
        int[] output = service.RepeatInts(input, repeated);
        assertArrayEquals(input, repeated);
        assertArrayEquals(input, output);
      }
      {
        IntParcelable p1 = new IntParcelable();
        p1.value = 1;
        IntParcelable p2 = new IntParcelable();
        p2.value = 1;
        IntParcelable p3 = new IntParcelable();
        p3.value = 1;
        IntParcelable[][] input = new IntParcelable[][] {{p1, p2, p3}, {p1, p2, p3}};
        IntParcelable[][] repeated = new IntParcelable[2][3];
        IntParcelable[][] output = service.Repeat2dParcelables(input, repeated);
        assertArrayEquals(input, repeated);
        assertArrayEquals(input, output);
      }
    }
}
