/*
 * Copyright (C) 2021, The Android Open Source Project
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

package android.aidl.service;

import android.aidl.fixedsizearray.FixedSizeArrayExample.IRepeatFixedSizeArray;
import android.aidl.fixedsizearray.FixedSizeArrayExample.IntParcelable;
import android.aidl.test.trunk.ITrunkStableTest;
import android.aidl.test.trunk.ITrunkStableTest.IMyCallback;
import android.aidl.test.trunk.ITrunkStableTest.MyOtherParcelable;
import android.aidl.test.trunk.ITrunkStableTest.MyParcelable;
import android.aidl.test.trunk.ITrunkStableTest.MyUnion;
import android.aidl.tests.BackendType;
import android.aidl.tests.BadParcelable;
import android.aidl.tests.ByteEnum;
import android.aidl.tests.CircularParcelable;
import android.aidl.tests.ConstantExpressionEnum;
import android.aidl.tests.GenericStructuredParcelable;
import android.aidl.tests.ICircular;
import android.aidl.tests.ICppJavaTests;
import android.aidl.tests.INamedCallback;
import android.aidl.tests.INewName;
import android.aidl.tests.IOldName;
import android.aidl.tests.ITestService;
import android.aidl.tests.IntEnum;
import android.aidl.tests.LongEnum;
import android.aidl.tests.RecursiveList;
import android.aidl.tests.SimpleParcelable;
import android.aidl.tests.StructuredParcelable;
import android.aidl.tests.Union;
import android.aidl.tests.extension.ExtendableParcelable;
import android.aidl.tests.extension.MyExt;
import android.aidl.tests.nested.INestedService;
import android.aidl.tests.nested.ParcelableWithNested;
import android.aidl.versioned.tests.BazUnion;
import android.aidl.versioned.tests.Foo;
import android.aidl.versioned.tests.IFooInterface;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ServiceSpecificException;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class TestServiceServer extends ITestService.Stub {
  public static void main(String[] args) {
    // b/235006086: test with debug stack trace parceling feature
    // which has been broken in the past. This does mean that we
    // lose Java coverage for when this is false, but we do have
    // other tests which cover this including CtsNdkBinderTestCases
    // and other language-specific exception/Status unit tests.
    Parcel.setStackTraceParceling(true);

    TestServiceServer myServer = new TestServiceServer();
    ServiceManager.addService(ITestService.class.getName(), myServer);

    FooInterface foo = new FooInterface();
    ServiceManager.addService(IFooInterface.class.getName(), foo);

    NestedService nested = new NestedService();
    ServiceManager.addService(INestedService.class.getName(), nested);

    FixedSizeArrayService fixedSize = new FixedSizeArrayService();
    ServiceManager.addService(IRepeatFixedSizeArray.DESCRIPTOR, fixedSize);

    TrunkStableService trunkStable = new TrunkStableService();
    ServiceManager.addService(ITrunkStableTest.DESCRIPTOR, trunkStable);

    Binder.joinThreadPool();
  }

  private static class FooInterface extends IFooInterface.Stub {
    @Override
    public void originalApi() {}
    @Override
    public String acceptUnionAndReturnString(BazUnion b) {
      if (b.getTag() == BazUnion.intNum) {
        return "" + b.getIntNum();
      }
      throw new IllegalArgumentException();
    }
    @Override
    public int returnsLengthOfFooArray(Foo[] foos) {
      return foos.length;
    }
    @Override
    public int ignoreParcelablesAndRepeatInt(Foo inFoo, Foo inoutFoo, Foo outFoo, int value) {
      return value;
    }
    @Override
    public final int getInterfaceVersion() {
      return super.VERSION;
    }
    @Override
    public final String getInterfaceHash() {
      return super.HASH;
    }
  }

  private static class NestedService extends INestedService.Stub {
    @Override
    public final Result flipStatus(ParcelableWithNested p) {
      Result result = new Result();
      if (p.status == ParcelableWithNested.Status.OK) {
        result.status = ParcelableWithNested.Status.NOT_OK;
      } else {
        result.status = ParcelableWithNested.Status.OK;
      }
      return result;
    }
    @Override
    public final void flipStatusWithCallback(byte st, ICallback cb) throws RemoteException {
      if (st == ParcelableWithNested.Status.OK) {
        cb.done(ParcelableWithNested.Status.NOT_OK);
      } else {
        cb.done(ParcelableWithNested.Status.OK);
      }
    }
  }

  @Override
  public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
    // UnimplementedMethod
    if (code == 1)
      return false;
    return super.onTransact(code, data, reply, flags);
  }

  @Override
  public int UnimplementedMethod(int arg) throws RemoteException {
    throw new UnsupportedOperationException();
  }
  @Override
  public void TestOneway() throws RemoteException {
    throw new RemoteException();
  }
  @Override
  public void Deprecated() throws RemoteException {}
  @Override
  public boolean RepeatBoolean(boolean token) throws RemoteException {
    return token;
  }
  @Override
  public byte RepeatByte(byte token) throws RemoteException {
    return token;
  }
  @Override
  public char RepeatChar(char token) throws RemoteException {
    return token;
  }
  @Override
  public int RepeatInt(int token) throws RemoteException {
    return token;
  }
  @Override
  public long RepeatLong(long token) throws RemoteException {
    return token;
  }
  @Override
  public float RepeatFloat(float token) throws RemoteException {
    return token;
  }
  @Override
  public double RepeatDouble(double token) throws RemoteException {
    return token;
  }
  @Override
  public String RepeatString(String token) throws RemoteException {
    return token;
  }
  @Override
  public byte RepeatByteEnum(byte token) throws RemoteException {
    return token;
  }
  @Override
  public int RepeatIntEnum(int token) throws RemoteException {
    return token;
  }
  @Override
  public long RepeatLongEnum(long token) throws RemoteException {
    return token;
  }
  @Override
  public boolean[] ReverseBoolean(boolean[] input, boolean[] repeated) throws RemoteException {
    boolean[] reversed = new boolean[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public byte[] ReverseByte(byte[] input, byte[] repeated) throws RemoteException {
    byte[] reversed = new byte[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public char[] ReverseChar(char[] input, char[] repeated) throws RemoteException {
    char[] reversed = new char[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public int[] ReverseInt(int[] input, int[] repeated) throws RemoteException {
    int[] reversed = new int[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public long[] ReverseLong(long[] input, long[] repeated) throws RemoteException {
    long[] reversed = new long[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public float[] ReverseFloat(float[] input, float[] repeated) throws RemoteException {
    float[] reversed = new float[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public double[] ReverseDouble(double[] input, double[] repeated) throws RemoteException {
    double[] reversed = new double[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public String[] ReverseString(String[] input, String[] repeated) throws RemoteException {
    String[] reversed = new String[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public byte[] ReverseByteEnum(byte[] input, byte[] repeated) throws RemoteException {
    byte[] reversed = new byte[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public int[] ReverseIntEnum(int[] input, int[] repeated) throws RemoteException {
    int[] reversed = new int[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public long[] ReverseLongEnum(long[] input, long[] repeated) throws RemoteException {
    long[] reversed = new long[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }

  private static class MyNamedCallback extends INamedCallback.Stub {
    public MyNamedCallback(String name) { mName = name; }
    @Override
    public String GetName() {
      return mName;
    }
    private String mName;
  }

  private HashMap<String, MyNamedCallback> mNamedCallbacks = new HashMap<>();

  @Override
  public INamedCallback GetOtherTestService(String name) throws RemoteException {
    synchronized (mNamedCallbacks) {
      if (!mNamedCallbacks.containsKey(name)) {
        mNamedCallbacks.put(name, new MyNamedCallback(name));
      }
      return mNamedCallbacks.get(name);
    }
  }
  @Override
  public boolean SetOtherTestService(String name, INamedCallback service) throws RemoteException {
    synchronized (mNamedCallbacks) {
      if (mNamedCallbacks.containsKey(name) && mNamedCallbacks.get(name) == service) {
        return true;
      }
      try {
        // This restricts the client to only setting services that it gets from this server.
        mNamedCallbacks.put(name, (MyNamedCallback) service);
      } catch (Exception e) {
        Log.i("TestServiceServer", "Failed to cast service");
      }
      return false;
    }
  }
  @Override
  public boolean VerifyName(INamedCallback service, String name) throws RemoteException {
    return name.equals(service.GetName());
  }
  @Override
  public INamedCallback[] GetInterfaceArray(String[] names) throws RemoteException {
    return GetNullableInterfaceArray(names);
  }
  @Override
  public boolean VerifyNamesWithInterfaceArray(INamedCallback[] services, String[] names)
      throws RemoteException {
    return VerifyNamesWithNullableInterfaceArray(services, names);
  }
  @Override
  public INamedCallback[] GetNullableInterfaceArray(String[] names) throws RemoteException {
    if (names == null)
      return null;
    INamedCallback[] services = new INamedCallback[names.length];
    for (int i = 0; i < names.length; i++) {
      if (names[i] == null) {
        services[i] = null;
      } else {
        services[i] = GetOtherTestService(names[i]);
      }
    }
    return services;
  }
  @Override
  public boolean VerifyNamesWithNullableInterfaceArray(INamedCallback[] services, String[] names)
      throws RemoteException {
    if (services != null && names != null) {
      for (int i = 0; i < names.length; i++) {
        if (services[i] != null && names[i] != null) {
          if (!VerifyName(services[i], names[i])) {
            return false;
          }
        } else if (services[i] != null || names[i] != null) {
          return false;
        }
      }
      return true;
    } else if (services != null || names != null) {
      return false;
    } else {
      return true;
    }
  }
  @Override
  public List<INamedCallback> GetInterfaceList(String[] names) throws RemoteException {
    INamedCallback[] services = GetNullableInterfaceArray(names);
    return services == null ? null : Arrays.asList(services);
  }
  @Override
  public boolean VerifyNamesWithInterfaceList(List<INamedCallback> services, String[] names)
      throws RemoteException {
    return VerifyNamesWithNullableInterfaceArray(
        services == null ? null : services.toArray(new INamedCallback[0]), names);
  }
  @Override
  public List<String> ReverseStringList(List<String> input, List<String> repeated)
      throws RemoteException {
    ArrayList<String> reversed = new ArrayList<String>();
    for (String a : input) {
      repeated.add(a);
      reversed.add(0, a);
    }
    return reversed;
  }
  @Override
  public ParcelFileDescriptor RepeatParcelFileDescriptor(ParcelFileDescriptor read)
      throws RemoteException {
    return read;
  }
  @Override
  public ParcelFileDescriptor[] ReverseParcelFileDescriptorArray(
      ParcelFileDescriptor[] input, ParcelFileDescriptor[] repeated) throws RemoteException {
    ParcelFileDescriptor[] reversed = new ParcelFileDescriptor[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      try {
        // extra dup, because of PARCELABLE_WRITE_RETURN_VALUE
        reversed[i] = input[input.length - i - 1].dup();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return reversed;
  }
  @Override
  public void ThrowServiceException(int code) throws RemoteException {
    Log.i("TestServiceServer", "Throwing service specific exception " + code);
    throw new ServiceSpecificException(code);
  }
  @Override
  public int[] RepeatNullableIntArray(int[] input) throws RemoteException {
    return input;
  }
  @Override
  public byte[] RepeatNullableByteEnumArray(byte[] input) throws RemoteException {
    return input;
  }
  @Override
  public int[] RepeatNullableIntEnumArray(int[] input) throws RemoteException {
    return input;
  }
  @Override
  public long[] RepeatNullableLongEnumArray(long[] input) throws RemoteException {
    return input;
  }
  @Override
  public String RepeatNullableString(String input) throws RemoteException {
    return input;
  }
  @Override
  public List<String> RepeatNullableStringList(List<String> input) throws RemoteException {
    return input;
  }
  @Override
  public ITestService.Empty RepeatNullableParcelable(ITestService.Empty input)
      throws RemoteException {
    return input;
  }
  @Override
  public List<ITestService.Empty> RepeatNullableParcelableList(List<ITestService.Empty> input)
      throws RemoteException {
    return input;
  }
  @Override
  public ITestService.Empty[] RepeatNullableParcelableArray(ITestService.Empty[] input)
      throws RemoteException {
    return input;
  }
  @Override
  public void TakesAnIBinder(IBinder input) throws RemoteException {
    // do nothing
  }
  @Override
  public void TakesANullableIBinder(IBinder input) throws RemoteException {
    // do nothing
  }
  @Override
  public void TakesAnIBinderList(List<IBinder> input) throws RemoteException {
    // do nothing
  }
  @Override
  public void TakesANullableIBinderList(List<IBinder> input) throws RemoteException {
    // do nothing
  }

  @Override
  public String RepeatUtf8CppString(String token) throws RemoteException {
    return token;
  }
  @Override
  public String RepeatNullableUtf8CppString(String token) throws RemoteException {
    return token;
  }
  @Override
  public String[] ReverseUtf8CppString(String[] input, String[] repeated) throws RemoteException {
    String[] reversed = new String[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public String[] ReverseNullableUtf8CppString(String[] input, String[] repeated)
      throws RemoteException {
    if (input == null)
      return null;

    String[] reversed = new String[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public List<String> ReverseUtf8CppStringList(List<String> input, List<String> repeated)
      throws RemoteException {
    // note - cannot clear 'repeated' here, we can only change its length
    if (input == null)
      return null;

    ArrayList<String> reversed = new ArrayList<String>();
    for (String a : input) {
      repeated.add(a);
      reversed.add(0, a);
    }
    return reversed;
  }
  @Override
  public INamedCallback GetCallback(boolean return_null) throws RemoteException {
    if (return_null)
      return null;
    return new MyNamedCallback("a callback named GetCallback");
  }
  @Override
  public void FillOutStructuredParcelable(StructuredParcelable parcelable) throws RemoteException {
    parcelable.shouldBeJerry = "Jerry";
    parcelable.shouldContainThreeFs = new int[] {parcelable.f, parcelable.f, parcelable.f};
    parcelable.shouldBeByteBar = ByteEnum.BAR;
    parcelable.shouldBeIntBar = IntEnum.BAR;
    parcelable.shouldBeLongBar = LongEnum.BAR;
    parcelable.shouldContainTwoByteFoos = new byte[] {ByteEnum.FOO, ByteEnum.FOO};
    parcelable.shouldContainTwoIntFoos = new int[] {IntEnum.FOO, IntEnum.FOO};
    parcelable.shouldContainTwoLongFoos = new long[] {LongEnum.FOO, LongEnum.FOO};

    parcelable.const_exprs_1 = ConstantExpressionEnum.decInt32_1;
    parcelable.const_exprs_2 = ConstantExpressionEnum.decInt32_2;
    parcelable.const_exprs_3 = ConstantExpressionEnum.decInt64_1;
    parcelable.const_exprs_4 = ConstantExpressionEnum.decInt64_2;
    parcelable.const_exprs_5 = ConstantExpressionEnum.decInt64_3;
    parcelable.const_exprs_6 = ConstantExpressionEnum.decInt64_4;
    parcelable.const_exprs_7 = ConstantExpressionEnum.hexInt32_1;
    parcelable.const_exprs_8 = ConstantExpressionEnum.hexInt32_2;
    parcelable.const_exprs_9 = ConstantExpressionEnum.hexInt32_3;
    parcelable.const_exprs_10 = ConstantExpressionEnum.hexInt64_1;

    parcelable.shouldSetBit0AndBit2 = StructuredParcelable.BIT0 | StructuredParcelable.BIT2;

    parcelable.u = Union.ns(new int[] {1, 2, 3});
    parcelable.shouldBeConstS1 = Union.s(Union.S1);
  }
  @Override
  public void RepeatExtendableParcelable(ExtendableParcelable ep, ExtendableParcelable ep2)
      throws RemoteException {
    ep2.a = ep.a;
    ep2.b = ep.b;
    // no way to copy currently w/o unparceling
    ep2.ext.setParcelable(ep.ext.getParcelable(MyExt.class));
    ep2.c = ep.c;
    ep2.ext2.setParcelable(null);
  }
  @Override
  public RecursiveList ReverseList(RecursiveList list) throws RemoteException {
    RecursiveList reversed = null;
    while (list != null) {
      RecursiveList next = list.next;
      list.next = reversed;
      reversed = list;
      list = next;
    }
    return reversed;
  }
  @Override
  public IBinder[] ReverseIBinderArray(IBinder[] input, IBinder[] repeated) {
    IBinder[] reversed = new IBinder[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public IBinder[] ReverseNullableIBinderArray(IBinder[] input, IBinder[] repeated) {
    IBinder[] reversed = new IBinder[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  @Override
  public SimpleParcelable RepeatSimpleParcelable(SimpleParcelable input, SimpleParcelable repeat)
      throws RemoteException {
    repeat.set(input.getName(), input.getNumber());
    return input;
  }
  @Override
  public SimpleParcelable[] ReverseSimpleParcelables(
      SimpleParcelable[] input, SimpleParcelable[] repeated) throws RemoteException {
    SimpleParcelable[] reversed = new SimpleParcelable[input.length];
    for (int i = 0; i < input.length; i++) {
      repeated[i] = input[i];
      reversed[i] = input[input.length - i - 1];
    }
    return reversed;
  }
  private static class MyOldName extends IOldName.Stub {
    @Override
    public String RealName() {
      return "OldName";
    }
  }

  @Override
  public IOldName GetOldNameInterface() throws RemoteException {
    return new MyOldName();
  }

  private static class MyNewName extends INewName.Stub {
    @Override
    public String RealName() {
      return "NewName";
    }
  }

  @Override
  public INewName GetNewNameInterface() throws RemoteException {
    return new MyNewName();
  }

  @Override
  public int[] GetUnionTags(Union[] input) throws RemoteException {
    int[] tags = new int[input.length];
    for (int i = 0; i < input.length; i++) {
      tags[i] = input[i].getTag();
    }
    return tags;
  }

  class MyCppJavaTests extends ICppJavaTests.Stub {
    @Override
    public BadParcelable RepeatBadParcelable(BadParcelable input) throws RemoteException {
      return input;
    }
    @Override
    public GenericStructuredParcelable<Integer, StructuredParcelable, Integer>
    RepeatGenericParcelable(
        GenericStructuredParcelable<Integer, StructuredParcelable, Integer> input,
        GenericStructuredParcelable<Integer, StructuredParcelable, Integer> repeat)
        throws RemoteException {
      repeat.a = input.a;
      repeat.b = input.b;
      return input;
    }
    @Override
    public PersistableBundle RepeatPersistableBundle(PersistableBundle input)
        throws RemoteException {
      return input;
    }
    @Override
    public PersistableBundle[] ReversePersistableBundles(
        PersistableBundle[] input, PersistableBundle[] repeated) throws RemoteException {
      PersistableBundle[] reversed = new PersistableBundle[input.length];
      for (int i = 0; i < input.length; i++) {
        repeated[i] = input[i];
        reversed[i] = input[input.length - i - 1];
      }
      return reversed;
    }
    @Override
    public Union ReverseUnion(Union input, Union repeated) throws RemoteException {
      int[] repeatedArray = new int[input.getNs().length];
      int[] reversedArray = ReverseInt(input.getNs(), repeatedArray);
      repeated.setNs(repeatedArray);
      return Union.ns(reversedArray);
    }
    @Override
    public List<IBinder> ReverseNamedCallbackList(List<IBinder> input, List<IBinder> repeated)
        throws RemoteException {
      ArrayList<IBinder> reversed = new ArrayList<IBinder>();
      for (IBinder a : input) {
        repeated.add(a);
        reversed.add(0, a);
      }
      return reversed;
    }
    @Override
    public FileDescriptor RepeatFileDescriptor(FileDescriptor read) throws RemoteException {
      return read;
    }
    @Override
    public FileDescriptor[] ReverseFileDescriptorArray(
        FileDescriptor[] input, FileDescriptor[] repeated) throws RemoteException {
      FileDescriptor[] reversed = new FileDescriptor[input.length];
      for (int i = 0; i < input.length; i++) {
        repeated[i] = input[i];
        reversed[i] = input[input.length - i - 1];
      }
      return reversed;
    }
  }

  public static class FixedSizeArrayService extends IRepeatFixedSizeArray.Stub {
    @Override
    public byte[] RepeatBytes(byte[] input, byte[] repeated) throws RemoteException {
      for (int i = 0; i < input.length; i++) {
        repeated[i] = input[i];
      }
      return input;
    }
    @Override
    public int[] RepeatInts(int[] input, int[] repeated) throws RemoteException {
      for (int i = 0; i < input.length; i++) {
        repeated[i] = input[i];
      }
      return input;
    }
    @Override
    public IBinder[] RepeatBinders(IBinder[] input, IBinder[] repeated) throws RemoteException {
      for (int i = 0; i < input.length; i++) {
        repeated[i] = input[i];
      }
      return input;
    }
    @Override
    public IntParcelable[] RepeatParcelables(IntParcelable[] input, IntParcelable[] repeated)
        throws RemoteException {
      for (int i = 0; i < input.length; i++) {
        repeated[i] = input[i];
      }
      return input;
    }
    @Override
    public byte[][] Repeat2dBytes(byte[][] input, byte[][] repeated) throws RemoteException {
      for (int i = 0; i < input.length; i++) {
        for (int j = 0; j < input[i].length; j++) {
          repeated[i][j] = input[i][j];
        }
      }
      return input;
    }
    @Override
    public int[][] Repeat2dInts(int[][] input, int[][] repeated) throws RemoteException {
      for (int i = 0; i < input.length; i++) {
        for (int j = 0; j < input[i].length; j++) {
          repeated[i][j] = input[i][j];
        }
      }
      return input;
    }
    @Override
    public IBinder[][] Repeat2dBinders(IBinder[][] input, IBinder[][] repeated)
        throws RemoteException {
      for (int i = 0; i < input.length; i++) {
        for (int j = 0; j < input[i].length; j++) {
          repeated[i][j] = input[i][j];
        }
      }
      return input;
    }
    @Override
    public IntParcelable[][] Repeat2dParcelables(
        IntParcelable[][] input, IntParcelable[][] repeated) throws RemoteException {
      for (int i = 0; i < input.length; i++) {
        for (int j = 0; j < input[i].length; j++) {
          repeated[i][j] = input[i][j];
        }
      }
      return input;
    }
  }

  @Override
  public IBinder GetCppJavaTests() throws RemoteException {
    return new MyCppJavaTests();
  }
  @Override
  public byte getBackendType() throws RemoteException {
    return BackendType.JAVA;
  }

  private static class MyCircular extends ICircular.Stub {
    private ITestService mSrv;

    MyCircular(ITestService srv) { mSrv = srv; }

    @Override
    public ITestService GetTestService() {
      return mSrv;
    }
  }

  @Override
  public ICircular GetCircular(CircularParcelable cp) throws RemoteException {
    cp.testService = this;
    return new MyCircular(this);
  }

  public static class TrunkStableService extends ITrunkStableTest.Stub {
    @Override
    public MyParcelable repeatParcelable(MyParcelable input) throws android.os.RemoteException {
      return input;
    }
    @Override
    public byte repeatEnum(byte input) throws android.os.RemoteException {
      return input;
    }
    @Override
    public ITrunkStableTest.MyUnion repeatUnion(MyUnion input) throws android.os.RemoteException {
      return input;
    }
    @Override
    public void callMyCallback(IMyCallback cb) throws android.os.RemoteException {
      MyParcelable p1, p2;
      p1 = new MyParcelable();
      p2 = cb.repeatParcelable(p1);

      byte e1, e2;
      e1 = MyEnum.THREE;
      e2 = cb.repeatEnum(e1);

      MyUnion u1, u2;
      u1 = new MyUnion();
      u2 = cb.repeatUnion(u1);

      MyOtherParcelable o1, o2;
      o1 = new MyOtherParcelable();
      // expected to fail when not using the frozen version
      try {
        o2 = cb.repeatOtherParcelable(o1);
      } catch (RemoteException e) {
      }
    }
    @Override
    public MyOtherParcelable repeatOtherParcelable(MyOtherParcelable input)
        throws android.os.RemoteException {
      return input;
    }

    @Override
    public final int getInterfaceVersion() {
      return super.VERSION;
    }

    @Override
    public final String getInterfaceHash() {
      return super.HASH;
    }
  }
}
