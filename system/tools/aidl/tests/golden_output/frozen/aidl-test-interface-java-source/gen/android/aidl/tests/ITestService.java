/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/ITestService.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ITestService.aidl
 */
package android.aidl.tests;
/** interface comment */
/** interface comment 2 */
// interface comment 3
// interface comment 4
public interface ITestService extends android.os.IInterface
{
  /** Default implementation for ITestService. */
  public static class Default implements android.aidl.tests.ITestService
  {
    // This is to emulate a method that is added after the service is implemented.
    // So the client cannot assume that a call to this method will be successful
    // or not. However, inside the test environment, we can't build client and
    // the server with different version of this AIDL file. So, we let the server
    // actually implement this and intercept the dispatch to the method
    // inside onTransact().
    // WARNING: Must be first method.
    // This requires hard coding the transaction number. As long as this method is
    // the first in this interface, it can keep the
    // "::android::IBinder::FIRST_CALL_TRANSACTION + 0" value and allow
    // methods to be added and removed.
    @Override public int UnimplementedMethod(int arg) throws android.os.RemoteException
    {
      return 0;
    }
    /**
     * @deprecated to make sure we have something in system/tools/aidl which does a compile check
     *     of deprecated and make sure this is reflected in goldens
     */
    @Override public void Deprecated() throws android.os.RemoteException
    {
    }
    @Override public void TestOneway() throws android.os.RemoteException
    {
    }
    // Test that primitives work as parameters and return types.
    @Override public boolean RepeatBoolean(boolean token) throws android.os.RemoteException
    {
      return false;
    }
    @Override public byte RepeatByte(byte token) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public char RepeatChar(char token) throws android.os.RemoteException
    {
      return '\u0000';
    }
    @Override public int RepeatInt(int token) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public long RepeatLong(long token) throws android.os.RemoteException
    {
      return 0L;
    }
    @Override public float RepeatFloat(float token) throws android.os.RemoteException
    {
      return 0.0f;
    }
    @Override public double RepeatDouble(double token) throws android.os.RemoteException
    {
      return 0.0d;
    }
    @Override public java.lang.String RepeatString(java.lang.String token) throws android.os.RemoteException
    {
      return null;
    }
    @Override public byte RepeatByteEnum(byte token) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public int RepeatIntEnum(int token) throws android.os.RemoteException
    {
      return 0;
    }
    @Override public long RepeatLongEnum(long token) throws android.os.RemoteException
    {
      return 0L;
    }
    // Test that arrays work as parameters and return types.
    @Override public boolean[] ReverseBoolean(boolean[] input, boolean[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public byte[] ReverseByte(byte[] input, byte[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public char[] ReverseChar(char[] input, char[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public int[] ReverseInt(int[] input, int[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public long[] ReverseLong(long[] input, long[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public float[] ReverseFloat(float[] input, float[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public double[] ReverseDouble(double[] input, double[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String[] ReverseString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public byte[] ReverseByteEnum(byte[] input, byte[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public int[] ReverseIntEnum(int[] input, int[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public long[] ReverseLongEnum(long[] input, long[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    // Test that clients can send and receive Binders.
    @Override public android.aidl.tests.INamedCallback GetOtherTestService(java.lang.String name) throws android.os.RemoteException
    {
      return null;
    }
    // returns true if the same service is already registered with the same name
    @Override public boolean SetOtherTestService(java.lang.String name, android.aidl.tests.INamedCallback service) throws android.os.RemoteException
    {
      return false;
    }
    @Override public boolean VerifyName(android.aidl.tests.INamedCallback service, java.lang.String name) throws android.os.RemoteException
    {
      return false;
    }
    @Override public android.aidl.tests.INamedCallback[] GetInterfaceArray(java.lang.String[] names) throws android.os.RemoteException
    {
      return null;
    }
    @Override public boolean VerifyNamesWithInterfaceArray(android.aidl.tests.INamedCallback[] services, java.lang.String[] names) throws android.os.RemoteException
    {
      return false;
    }
    @Override public android.aidl.tests.INamedCallback[] GetNullableInterfaceArray(java.lang.String[] names) throws android.os.RemoteException
    {
      return null;
    }
    @Override public boolean VerifyNamesWithNullableInterfaceArray(android.aidl.tests.INamedCallback[] services, java.lang.String[] names) throws android.os.RemoteException
    {
      return false;
    }
    @Override public java.util.List<android.aidl.tests.INamedCallback> GetInterfaceList(java.lang.String[] names) throws android.os.RemoteException
    {
      return null;
    }
    @Override public boolean VerifyNamesWithInterfaceList(java.util.List<android.aidl.tests.INamedCallback> services, java.lang.String[] names) throws android.os.RemoteException
    {
      return false;
    }
    // Test that List<T> types work correctly.
    @Override public java.util.List<java.lang.String> ReverseStringList(java.util.List<java.lang.String> input, java.util.List<java.lang.String> repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.os.ParcelFileDescriptor RepeatParcelFileDescriptor(android.os.ParcelFileDescriptor read) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.os.ParcelFileDescriptor[] ReverseParcelFileDescriptorArray(android.os.ParcelFileDescriptor[] input, android.os.ParcelFileDescriptor[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    // Test that service specific exceptions work correctly.
    @Override public void ThrowServiceException(int code) throws android.os.RemoteException
    {
    }
    // Test nullability
    @Override public int[] RepeatNullableIntArray(int[] input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public byte[] RepeatNullableByteEnumArray(byte[] input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public int[] RepeatNullableIntEnumArray(int[] input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public long[] RepeatNullableLongEnumArray(long[] input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String RepeatNullableString(java.lang.String input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.List<java.lang.String> RepeatNullableStringList(java.util.List<java.lang.String> input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.ITestService.Empty RepeatNullableParcelable(android.aidl.tests.ITestService.Empty input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.ITestService.Empty[] RepeatNullableParcelableArray(android.aidl.tests.ITestService.Empty[] input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.List<android.aidl.tests.ITestService.Empty> RepeatNullableParcelableList(java.util.List<android.aidl.tests.ITestService.Empty> input) throws android.os.RemoteException
    {
      return null;
    }
    @Override public void TakesAnIBinder(android.os.IBinder input) throws android.os.RemoteException
    {
    }
    @Override public void TakesANullableIBinder(android.os.IBinder input) throws android.os.RemoteException
    {
    }
    @Override public void TakesAnIBinderList(java.util.List<android.os.IBinder> input) throws android.os.RemoteException
    {
    }
    @Override public void TakesANullableIBinderList(java.util.List<android.os.IBinder> input) throws android.os.RemoteException
    {
    }
    // Test utf8 decoding from utf16 wire format
    @Override public java.lang.String RepeatUtf8CppString(java.lang.String token) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String RepeatNullableUtf8CppString(java.lang.String token) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String[] ReverseUtf8CppString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.lang.String[] ReverseNullableUtf8CppString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.List<java.lang.String> ReverseUtf8CppStringList(java.util.List<java.lang.String> input, java.util.List<java.lang.String> repeated) throws android.os.RemoteException
    {
      return null;
    }
    /** comment before annotation */
    @Override public android.aidl.tests.INamedCallback GetCallback(boolean return_null) throws android.os.RemoteException
    {
      return null;
    }
    // Since this paracelable has clearly defined default values, it would be
    // inefficient to use an IPC to fill it out in practice.
    @Override public void FillOutStructuredParcelable(android.aidl.tests.StructuredParcelable parcel) throws android.os.RemoteException
    {
    }
    @Override public void RepeatExtendableParcelable(android.aidl.tests.extension.ExtendableParcelable ep, android.aidl.tests.extension.ExtendableParcelable ep2) throws android.os.RemoteException
    {
    }
    @Override public android.aidl.tests.RecursiveList ReverseList(android.aidl.tests.RecursiveList list) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.os.IBinder[] ReverseIBinderArray(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.os.IBinder[] ReverseNullableIBinderArray(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.SimpleParcelable RepeatSimpleParcelable(android.aidl.tests.SimpleParcelable input, android.aidl.tests.SimpleParcelable repeat) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.SimpleParcelable[] ReverseSimpleParcelables(android.aidl.tests.SimpleParcelable[] input, android.aidl.tests.SimpleParcelable[] repeated) throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.IOldName GetOldNameInterface() throws android.os.RemoteException
    {
      return null;
    }
    @Override public android.aidl.tests.INewName GetNewNameInterface() throws android.os.RemoteException
    {
      return null;
    }
    @Override public int[] GetUnionTags(android.aidl.tests.Union[] input) throws android.os.RemoteException
    {
      return null;
    }
    // Retrieve the ICppJavaTests if the server supports it
    @Override public android.os.IBinder GetCppJavaTests() throws android.os.RemoteException
    {
      return null;
    }
    @Override public byte getBackendType() throws android.os.RemoteException
    {
      return 0;
    }
    @Override public android.aidl.tests.ICircular GetCircular(android.aidl.tests.CircularParcelable cp) throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Delegator implementation for ITestService. */
  public static class Delegator extends android.aidl.tests.ITestService.Stub
  {
    public Delegator(android.aidl.tests.ITestService impl) {
      this.mImpl = impl;
    }
    // This is to emulate a method that is added after the service is implemented.
    // So the client cannot assume that a call to this method will be successful
    // or not. However, inside the test environment, we can't build client and
    // the server with different version of this AIDL file. So, we let the server
    // actually implement this and intercept the dispatch to the method
    // inside onTransact().
    // WARNING: Must be first method.
    // This requires hard coding the transaction number. As long as this method is
    // the first in this interface, it can keep the
    // "::android::IBinder::FIRST_CALL_TRANSACTION + 0" value and allow
    // methods to be added and removed.
    @Override public int UnimplementedMethod(int arg) throws android.os.RemoteException
    {
      return mImpl.UnimplementedMethod(arg);
    }
    /**
     * @deprecated to make sure we have something in system/tools/aidl which does a compile check
     *     of deprecated and make sure this is reflected in goldens
     */
    @Override public void Deprecated() throws android.os.RemoteException
    {
      mImpl.Deprecated();
    }
    @Override public void TestOneway() throws android.os.RemoteException
    {
      mImpl.TestOneway();
    }
    // Test that primitives work as parameters and return types.
    @Override public boolean RepeatBoolean(boolean token) throws android.os.RemoteException
    {
      return mImpl.RepeatBoolean(token);
    }
    @Override public byte RepeatByte(byte token) throws android.os.RemoteException
    {
      return mImpl.RepeatByte(token);
    }
    @Override public char RepeatChar(char token) throws android.os.RemoteException
    {
      return mImpl.RepeatChar(token);
    }
    @Override public int RepeatInt(int token) throws android.os.RemoteException
    {
      return mImpl.RepeatInt(token);
    }
    @Override public long RepeatLong(long token) throws android.os.RemoteException
    {
      return mImpl.RepeatLong(token);
    }
    @Override public float RepeatFloat(float token) throws android.os.RemoteException
    {
      return mImpl.RepeatFloat(token);
    }
    @Override public double RepeatDouble(double token) throws android.os.RemoteException
    {
      return mImpl.RepeatDouble(token);
    }
    @Override public java.lang.String RepeatString(java.lang.String token) throws android.os.RemoteException
    {
      return mImpl.RepeatString(token);
    }
    @Override public byte RepeatByteEnum(byte token) throws android.os.RemoteException
    {
      return mImpl.RepeatByteEnum(token);
    }
    @Override public int RepeatIntEnum(int token) throws android.os.RemoteException
    {
      return mImpl.RepeatIntEnum(token);
    }
    @Override public long RepeatLongEnum(long token) throws android.os.RemoteException
    {
      return mImpl.RepeatLongEnum(token);
    }
    // Test that arrays work as parameters and return types.
    @Override public boolean[] ReverseBoolean(boolean[] input, boolean[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseBoolean(input,repeated);
    }
    @Override public byte[] ReverseByte(byte[] input, byte[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseByte(input,repeated);
    }
    @Override public char[] ReverseChar(char[] input, char[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseChar(input,repeated);
    }
    @Override public int[] ReverseInt(int[] input, int[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseInt(input,repeated);
    }
    @Override public long[] ReverseLong(long[] input, long[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseLong(input,repeated);
    }
    @Override public float[] ReverseFloat(float[] input, float[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseFloat(input,repeated);
    }
    @Override public double[] ReverseDouble(double[] input, double[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseDouble(input,repeated);
    }
    @Override public java.lang.String[] ReverseString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseString(input,repeated);
    }
    @Override public byte[] ReverseByteEnum(byte[] input, byte[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseByteEnum(input,repeated);
    }
    @Override public int[] ReverseIntEnum(int[] input, int[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseIntEnum(input,repeated);
    }
    @Override public long[] ReverseLongEnum(long[] input, long[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseLongEnum(input,repeated);
    }
    // Test that clients can send and receive Binders.
    @Override public android.aidl.tests.INamedCallback GetOtherTestService(java.lang.String name) throws android.os.RemoteException
    {
      return mImpl.GetOtherTestService(name);
    }
    // returns true if the same service is already registered with the same name
    @Override public boolean SetOtherTestService(java.lang.String name, android.aidl.tests.INamedCallback service) throws android.os.RemoteException
    {
      return mImpl.SetOtherTestService(name,service);
    }
    @Override public boolean VerifyName(android.aidl.tests.INamedCallback service, java.lang.String name) throws android.os.RemoteException
    {
      return mImpl.VerifyName(service,name);
    }
    @Override public android.aidl.tests.INamedCallback[] GetInterfaceArray(java.lang.String[] names) throws android.os.RemoteException
    {
      return mImpl.GetInterfaceArray(names);
    }
    @Override public boolean VerifyNamesWithInterfaceArray(android.aidl.tests.INamedCallback[] services, java.lang.String[] names) throws android.os.RemoteException
    {
      return mImpl.VerifyNamesWithInterfaceArray(services,names);
    }
    @Override public android.aidl.tests.INamedCallback[] GetNullableInterfaceArray(java.lang.String[] names) throws android.os.RemoteException
    {
      return mImpl.GetNullableInterfaceArray(names);
    }
    @Override public boolean VerifyNamesWithNullableInterfaceArray(android.aidl.tests.INamedCallback[] services, java.lang.String[] names) throws android.os.RemoteException
    {
      return mImpl.VerifyNamesWithNullableInterfaceArray(services,names);
    }
    @Override public java.util.List<android.aidl.tests.INamedCallback> GetInterfaceList(java.lang.String[] names) throws android.os.RemoteException
    {
      return mImpl.GetInterfaceList(names);
    }
    @Override public boolean VerifyNamesWithInterfaceList(java.util.List<android.aidl.tests.INamedCallback> services, java.lang.String[] names) throws android.os.RemoteException
    {
      return mImpl.VerifyNamesWithInterfaceList(services,names);
    }
    // Test that List<T> types work correctly.
    @Override public java.util.List<java.lang.String> ReverseStringList(java.util.List<java.lang.String> input, java.util.List<java.lang.String> repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseStringList(input,repeated);
    }
    @Override public android.os.ParcelFileDescriptor RepeatParcelFileDescriptor(android.os.ParcelFileDescriptor read) throws android.os.RemoteException
    {
      return mImpl.RepeatParcelFileDescriptor(read);
    }
    @Override public android.os.ParcelFileDescriptor[] ReverseParcelFileDescriptorArray(android.os.ParcelFileDescriptor[] input, android.os.ParcelFileDescriptor[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseParcelFileDescriptorArray(input,repeated);
    }
    // Test that service specific exceptions work correctly.
    @Override public void ThrowServiceException(int code) throws android.os.RemoteException
    {
      mImpl.ThrowServiceException(code);
    }
    // Test nullability
    @Override public int[] RepeatNullableIntArray(int[] input) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableIntArray(input);
    }
    @Override public byte[] RepeatNullableByteEnumArray(byte[] input) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableByteEnumArray(input);
    }
    @Override public int[] RepeatNullableIntEnumArray(int[] input) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableIntEnumArray(input);
    }
    @Override public long[] RepeatNullableLongEnumArray(long[] input) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableLongEnumArray(input);
    }
    @Override public java.lang.String RepeatNullableString(java.lang.String input) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableString(input);
    }
    @Override public java.util.List<java.lang.String> RepeatNullableStringList(java.util.List<java.lang.String> input) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableStringList(input);
    }
    @Override public android.aidl.tests.ITestService.Empty RepeatNullableParcelable(android.aidl.tests.ITestService.Empty input) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableParcelable(input);
    }
    @Override public android.aidl.tests.ITestService.Empty[] RepeatNullableParcelableArray(android.aidl.tests.ITestService.Empty[] input) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableParcelableArray(input);
    }
    @Override public java.util.List<android.aidl.tests.ITestService.Empty> RepeatNullableParcelableList(java.util.List<android.aidl.tests.ITestService.Empty> input) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableParcelableList(input);
    }
    @Override public void TakesAnIBinder(android.os.IBinder input) throws android.os.RemoteException
    {
      mImpl.TakesAnIBinder(input);
    }
    @Override public void TakesANullableIBinder(android.os.IBinder input) throws android.os.RemoteException
    {
      mImpl.TakesANullableIBinder(input);
    }
    @Override public void TakesAnIBinderList(java.util.List<android.os.IBinder> input) throws android.os.RemoteException
    {
      mImpl.TakesAnIBinderList(input);
    }
    @Override public void TakesANullableIBinderList(java.util.List<android.os.IBinder> input) throws android.os.RemoteException
    {
      mImpl.TakesANullableIBinderList(input);
    }
    // Test utf8 decoding from utf16 wire format
    @Override public java.lang.String RepeatUtf8CppString(java.lang.String token) throws android.os.RemoteException
    {
      return mImpl.RepeatUtf8CppString(token);
    }
    @Override public java.lang.String RepeatNullableUtf8CppString(java.lang.String token) throws android.os.RemoteException
    {
      return mImpl.RepeatNullableUtf8CppString(token);
    }
    @Override public java.lang.String[] ReverseUtf8CppString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseUtf8CppString(input,repeated);
    }
    @Override public java.lang.String[] ReverseNullableUtf8CppString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseNullableUtf8CppString(input,repeated);
    }
    @Override public java.util.List<java.lang.String> ReverseUtf8CppStringList(java.util.List<java.lang.String> input, java.util.List<java.lang.String> repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseUtf8CppStringList(input,repeated);
    }
    /** comment before annotation */
    @Override public android.aidl.tests.INamedCallback GetCallback(boolean return_null) throws android.os.RemoteException
    {
      return mImpl.GetCallback(return_null);
    }
    // Since this paracelable has clearly defined default values, it would be
    // inefficient to use an IPC to fill it out in practice.
    @Override public void FillOutStructuredParcelable(android.aidl.tests.StructuredParcelable parcel) throws android.os.RemoteException
    {
      mImpl.FillOutStructuredParcelable(parcel);
    }
    @Override public void RepeatExtendableParcelable(android.aidl.tests.extension.ExtendableParcelable ep, android.aidl.tests.extension.ExtendableParcelable ep2) throws android.os.RemoteException
    {
      mImpl.RepeatExtendableParcelable(ep,ep2);
    }
    @Override public android.aidl.tests.RecursiveList ReverseList(android.aidl.tests.RecursiveList list) throws android.os.RemoteException
    {
      return mImpl.ReverseList(list);
    }
    @Override public android.os.IBinder[] ReverseIBinderArray(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseIBinderArray(input,repeated);
    }
    @Override public android.os.IBinder[] ReverseNullableIBinderArray(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseNullableIBinderArray(input,repeated);
    }
    @Override public android.aidl.tests.SimpleParcelable RepeatSimpleParcelable(android.aidl.tests.SimpleParcelable input, android.aidl.tests.SimpleParcelable repeat) throws android.os.RemoteException
    {
      return mImpl.RepeatSimpleParcelable(input,repeat);
    }
    @Override public android.aidl.tests.SimpleParcelable[] ReverseSimpleParcelables(android.aidl.tests.SimpleParcelable[] input, android.aidl.tests.SimpleParcelable[] repeated) throws android.os.RemoteException
    {
      return mImpl.ReverseSimpleParcelables(input,repeated);
    }
    @Override public android.aidl.tests.IOldName GetOldNameInterface() throws android.os.RemoteException
    {
      return mImpl.GetOldNameInterface();
    }
    @Override public android.aidl.tests.INewName GetNewNameInterface() throws android.os.RemoteException
    {
      return mImpl.GetNewNameInterface();
    }
    @Override public int[] GetUnionTags(android.aidl.tests.Union[] input) throws android.os.RemoteException
    {
      return mImpl.GetUnionTags(input);
    }
    // Retrieve the ICppJavaTests if the server supports it
    @Override public android.os.IBinder GetCppJavaTests() throws android.os.RemoteException
    {
      return mImpl.GetCppJavaTests();
    }
    @Override public byte getBackendType() throws android.os.RemoteException
    {
      return mImpl.getBackendType();
    }
    @Override public android.aidl.tests.ICircular GetCircular(android.aidl.tests.CircularParcelable cp) throws android.os.RemoteException
    {
      return mImpl.GetCircular(cp);
    }
    android.aidl.tests.ITestService mImpl;
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.tests.ITestService
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.aidl.tests.ITestService interface,
     * generating a proxy if needed.
     */
    public static android.aidl.tests.ITestService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.tests.ITestService))) {
        return ((android.aidl.tests.ITestService)iin);
      }
      return new android.aidl.tests.ITestService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    /** @hide */
    public static java.lang.String getDefaultTransactionName(int transactionCode)
    {
      switch (transactionCode)
      {
        case TRANSACTION_UnimplementedMethod:
        {
          return "UnimplementedMethod";
        }
        case TRANSACTION_Deprecated:
        {
          return "Deprecated";
        }
        case TRANSACTION_TestOneway:
        {
          return "TestOneway";
        }
        case TRANSACTION_RepeatBoolean:
        {
          return "RepeatBoolean";
        }
        case TRANSACTION_RepeatByte:
        {
          return "RepeatByte";
        }
        case TRANSACTION_RepeatChar:
        {
          return "RepeatChar";
        }
        case TRANSACTION_RepeatInt:
        {
          return "RepeatInt";
        }
        case TRANSACTION_RepeatLong:
        {
          return "RepeatLong";
        }
        case TRANSACTION_RepeatFloat:
        {
          return "RepeatFloat";
        }
        case TRANSACTION_RepeatDouble:
        {
          return "RepeatDouble";
        }
        case TRANSACTION_RepeatString:
        {
          return "RepeatString";
        }
        case TRANSACTION_RepeatByteEnum:
        {
          return "RepeatByteEnum";
        }
        case TRANSACTION_RepeatIntEnum:
        {
          return "RepeatIntEnum";
        }
        case TRANSACTION_RepeatLongEnum:
        {
          return "RepeatLongEnum";
        }
        case TRANSACTION_ReverseBoolean:
        {
          return "ReverseBoolean";
        }
        case TRANSACTION_ReverseByte:
        {
          return "ReverseByte";
        }
        case TRANSACTION_ReverseChar:
        {
          return "ReverseChar";
        }
        case TRANSACTION_ReverseInt:
        {
          return "ReverseInt";
        }
        case TRANSACTION_ReverseLong:
        {
          return "ReverseLong";
        }
        case TRANSACTION_ReverseFloat:
        {
          return "ReverseFloat";
        }
        case TRANSACTION_ReverseDouble:
        {
          return "ReverseDouble";
        }
        case TRANSACTION_ReverseString:
        {
          return "ReverseString";
        }
        case TRANSACTION_ReverseByteEnum:
        {
          return "ReverseByteEnum";
        }
        case TRANSACTION_ReverseIntEnum:
        {
          return "ReverseIntEnum";
        }
        case TRANSACTION_ReverseLongEnum:
        {
          return "ReverseLongEnum";
        }
        case TRANSACTION_GetOtherTestService:
        {
          return "GetOtherTestService";
        }
        case TRANSACTION_SetOtherTestService:
        {
          return "SetOtherTestService";
        }
        case TRANSACTION_VerifyName:
        {
          return "VerifyName";
        }
        case TRANSACTION_GetInterfaceArray:
        {
          return "GetInterfaceArray";
        }
        case TRANSACTION_VerifyNamesWithInterfaceArray:
        {
          return "VerifyNamesWithInterfaceArray";
        }
        case TRANSACTION_GetNullableInterfaceArray:
        {
          return "GetNullableInterfaceArray";
        }
        case TRANSACTION_VerifyNamesWithNullableInterfaceArray:
        {
          return "VerifyNamesWithNullableInterfaceArray";
        }
        case TRANSACTION_GetInterfaceList:
        {
          return "GetInterfaceList";
        }
        case TRANSACTION_VerifyNamesWithInterfaceList:
        {
          return "VerifyNamesWithInterfaceList";
        }
        case TRANSACTION_ReverseStringList:
        {
          return "ReverseStringList";
        }
        case TRANSACTION_RepeatParcelFileDescriptor:
        {
          return "RepeatParcelFileDescriptor";
        }
        case TRANSACTION_ReverseParcelFileDescriptorArray:
        {
          return "ReverseParcelFileDescriptorArray";
        }
        case TRANSACTION_ThrowServiceException:
        {
          return "ThrowServiceException";
        }
        case TRANSACTION_RepeatNullableIntArray:
        {
          return "RepeatNullableIntArray";
        }
        case TRANSACTION_RepeatNullableByteEnumArray:
        {
          return "RepeatNullableByteEnumArray";
        }
        case TRANSACTION_RepeatNullableIntEnumArray:
        {
          return "RepeatNullableIntEnumArray";
        }
        case TRANSACTION_RepeatNullableLongEnumArray:
        {
          return "RepeatNullableLongEnumArray";
        }
        case TRANSACTION_RepeatNullableString:
        {
          return "RepeatNullableString";
        }
        case TRANSACTION_RepeatNullableStringList:
        {
          return "RepeatNullableStringList";
        }
        case TRANSACTION_RepeatNullableParcelable:
        {
          return "RepeatNullableParcelable";
        }
        case TRANSACTION_RepeatNullableParcelableArray:
        {
          return "RepeatNullableParcelableArray";
        }
        case TRANSACTION_RepeatNullableParcelableList:
        {
          return "RepeatNullableParcelableList";
        }
        case TRANSACTION_TakesAnIBinder:
        {
          return "TakesAnIBinder";
        }
        case TRANSACTION_TakesANullableIBinder:
        {
          return "TakesANullableIBinder";
        }
        case TRANSACTION_TakesAnIBinderList:
        {
          return "TakesAnIBinderList";
        }
        case TRANSACTION_TakesANullableIBinderList:
        {
          return "TakesANullableIBinderList";
        }
        case TRANSACTION_RepeatUtf8CppString:
        {
          return "RepeatUtf8CppString";
        }
        case TRANSACTION_RepeatNullableUtf8CppString:
        {
          return "RepeatNullableUtf8CppString";
        }
        case TRANSACTION_ReverseUtf8CppString:
        {
          return "ReverseUtf8CppString";
        }
        case TRANSACTION_ReverseNullableUtf8CppString:
        {
          return "ReverseNullableUtf8CppString";
        }
        case TRANSACTION_ReverseUtf8CppStringList:
        {
          return "ReverseUtf8CppStringList";
        }
        case TRANSACTION_GetCallback:
        {
          return "GetCallback";
        }
        case TRANSACTION_FillOutStructuredParcelable:
        {
          return "FillOutStructuredParcelable";
        }
        case TRANSACTION_RepeatExtendableParcelable:
        {
          return "RepeatExtendableParcelable";
        }
        case TRANSACTION_ReverseList:
        {
          return "ReverseList";
        }
        case TRANSACTION_ReverseIBinderArray:
        {
          return "ReverseIBinderArray";
        }
        case TRANSACTION_ReverseNullableIBinderArray:
        {
          return "ReverseNullableIBinderArray";
        }
        case TRANSACTION_RepeatSimpleParcelable:
        {
          return "RepeatSimpleParcelable";
        }
        case TRANSACTION_ReverseSimpleParcelables:
        {
          return "ReverseSimpleParcelables";
        }
        case TRANSACTION_GetOldNameInterface:
        {
          return "GetOldNameInterface";
        }
        case TRANSACTION_GetNewNameInterface:
        {
          return "GetNewNameInterface";
        }
        case TRANSACTION_GetUnionTags:
        {
          return "GetUnionTags";
        }
        case TRANSACTION_GetCppJavaTests:
        {
          return "GetCppJavaTests";
        }
        case TRANSACTION_getBackendType:
        {
          return "getBackendType";
        }
        case TRANSACTION_GetCircular:
        {
          return "GetCircular";
        }
        default:
        {
          return null;
        }
      }
    }
    /** @hide */
    public java.lang.String getTransactionName(int transactionCode)
    {
      return this.getDefaultTransactionName(transactionCode);
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      if (code == INTERFACE_TRANSACTION) {
        reply.writeString(descriptor);
        return true;
      }
      switch (code)
      {
        case TRANSACTION_UnimplementedMethod:
        {
          int _arg0;
          _arg0 = data.readInt();
          data.enforceNoDataAvail();
          int _result = this.UnimplementedMethod(_arg0);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_Deprecated:
        {
          this.Deprecated();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_TestOneway:
        {
          this.TestOneway();
          break;
        }
        case TRANSACTION_RepeatBoolean:
        {
          boolean _arg0;
          _arg0 = data.readBoolean();
          data.enforceNoDataAvail();
          boolean _result = this.RepeatBoolean(_arg0);
          reply.writeNoException();
          reply.writeBoolean(_result);
          break;
        }
        case TRANSACTION_RepeatByte:
        {
          byte _arg0;
          _arg0 = data.readByte();
          data.enforceNoDataAvail();
          byte _result = this.RepeatByte(_arg0);
          reply.writeNoException();
          reply.writeByte(_result);
          break;
        }
        case TRANSACTION_RepeatChar:
        {
          char _arg0;
          _arg0 = (char)data.readInt();
          data.enforceNoDataAvail();
          char _result = this.RepeatChar(_arg0);
          reply.writeNoException();
          reply.writeInt(((int)_result));
          break;
        }
        case TRANSACTION_RepeatInt:
        {
          int _arg0;
          _arg0 = data.readInt();
          data.enforceNoDataAvail();
          int _result = this.RepeatInt(_arg0);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_RepeatLong:
        {
          long _arg0;
          _arg0 = data.readLong();
          data.enforceNoDataAvail();
          long _result = this.RepeatLong(_arg0);
          reply.writeNoException();
          reply.writeLong(_result);
          break;
        }
        case TRANSACTION_RepeatFloat:
        {
          float _arg0;
          _arg0 = data.readFloat();
          data.enforceNoDataAvail();
          float _result = this.RepeatFloat(_arg0);
          reply.writeNoException();
          reply.writeFloat(_result);
          break;
        }
        case TRANSACTION_RepeatDouble:
        {
          double _arg0;
          _arg0 = data.readDouble();
          data.enforceNoDataAvail();
          double _result = this.RepeatDouble(_arg0);
          reply.writeNoException();
          reply.writeDouble(_result);
          break;
        }
        case TRANSACTION_RepeatString:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          data.enforceNoDataAvail();
          java.lang.String _result = this.RepeatString(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_RepeatByteEnum:
        {
          byte _arg0;
          _arg0 = data.readByte();
          data.enforceNoDataAvail();
          byte _result = this.RepeatByteEnum(_arg0);
          reply.writeNoException();
          reply.writeByte(_result);
          break;
        }
        case TRANSACTION_RepeatIntEnum:
        {
          int _arg0;
          _arg0 = data.readInt();
          data.enforceNoDataAvail();
          int _result = this.RepeatIntEnum(_arg0);
          reply.writeNoException();
          reply.writeInt(_result);
          break;
        }
        case TRANSACTION_RepeatLongEnum:
        {
          long _arg0;
          _arg0 = data.readLong();
          data.enforceNoDataAvail();
          long _result = this.RepeatLongEnum(_arg0);
          reply.writeNoException();
          reply.writeLong(_result);
          break;
        }
        case TRANSACTION_ReverseBoolean:
        {
          boolean[] _arg0;
          _arg0 = data.createBooleanArray();
          boolean[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new boolean[_arg1_length];
          }
          data.enforceNoDataAvail();
          boolean[] _result = this.ReverseBoolean(_arg0, _arg1);
          reply.writeNoException();
          reply.writeBooleanArray(_result);
          reply.writeBooleanArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseByte:
        {
          byte[] _arg0;
          _arg0 = data.createByteArray();
          byte[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new byte[_arg1_length];
          }
          data.enforceNoDataAvail();
          byte[] _result = this.ReverseByte(_arg0, _arg1);
          reply.writeNoException();
          reply.writeByteArray(_result);
          reply.writeByteArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseChar:
        {
          char[] _arg0;
          _arg0 = data.createCharArray();
          char[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new char[_arg1_length];
          }
          data.enforceNoDataAvail();
          char[] _result = this.ReverseChar(_arg0, _arg1);
          reply.writeNoException();
          reply.writeCharArray(_result);
          reply.writeCharArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseInt:
        {
          int[] _arg0;
          _arg0 = data.createIntArray();
          int[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new int[_arg1_length];
          }
          data.enforceNoDataAvail();
          int[] _result = this.ReverseInt(_arg0, _arg1);
          reply.writeNoException();
          reply.writeIntArray(_result);
          reply.writeIntArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseLong:
        {
          long[] _arg0;
          _arg0 = data.createLongArray();
          long[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new long[_arg1_length];
          }
          data.enforceNoDataAvail();
          long[] _result = this.ReverseLong(_arg0, _arg1);
          reply.writeNoException();
          reply.writeLongArray(_result);
          reply.writeLongArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseFloat:
        {
          float[] _arg0;
          _arg0 = data.createFloatArray();
          float[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new float[_arg1_length];
          }
          data.enforceNoDataAvail();
          float[] _result = this.ReverseFloat(_arg0, _arg1);
          reply.writeNoException();
          reply.writeFloatArray(_result);
          reply.writeFloatArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseDouble:
        {
          double[] _arg0;
          _arg0 = data.createDoubleArray();
          double[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new double[_arg1_length];
          }
          data.enforceNoDataAvail();
          double[] _result = this.ReverseDouble(_arg0, _arg1);
          reply.writeNoException();
          reply.writeDoubleArray(_result);
          reply.writeDoubleArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseString:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          java.lang.String[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new java.lang.String[_arg1_length];
          }
          data.enforceNoDataAvail();
          java.lang.String[] _result = this.ReverseString(_arg0, _arg1);
          reply.writeNoException();
          reply.writeStringArray(_result);
          reply.writeStringArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseByteEnum:
        {
          byte[] _arg0;
          _arg0 = data.createByteArray();
          byte[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new byte[_arg1_length];
          }
          data.enforceNoDataAvail();
          byte[] _result = this.ReverseByteEnum(_arg0, _arg1);
          reply.writeNoException();
          reply.writeByteArray(_result);
          reply.writeByteArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseIntEnum:
        {
          int[] _arg0;
          _arg0 = data.createIntArray();
          int[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new int[_arg1_length];
          }
          data.enforceNoDataAvail();
          int[] _result = this.ReverseIntEnum(_arg0, _arg1);
          reply.writeNoException();
          reply.writeIntArray(_result);
          reply.writeIntArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseLongEnum:
        {
          long[] _arg0;
          _arg0 = data.createLongArray();
          long[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new long[_arg1_length];
          }
          data.enforceNoDataAvail();
          long[] _result = this.ReverseLongEnum(_arg0, _arg1);
          reply.writeNoException();
          reply.writeLongArray(_result);
          reply.writeLongArray(_arg1);
          break;
        }
        case TRANSACTION_GetOtherTestService:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          data.enforceNoDataAvail();
          android.aidl.tests.INamedCallback _result = this.GetOtherTestService(_arg0);
          reply.writeNoException();
          reply.writeStrongInterface(_result);
          break;
        }
        case TRANSACTION_SetOtherTestService:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          android.aidl.tests.INamedCallback _arg1;
          _arg1 = android.aidl.tests.INamedCallback.Stub.asInterface(data.readStrongBinder());
          data.enforceNoDataAvail();
          boolean _result = this.SetOtherTestService(_arg0, _arg1);
          reply.writeNoException();
          reply.writeBoolean(_result);
          break;
        }
        case TRANSACTION_VerifyName:
        {
          android.aidl.tests.INamedCallback _arg0;
          _arg0 = android.aidl.tests.INamedCallback.Stub.asInterface(data.readStrongBinder());
          java.lang.String _arg1;
          _arg1 = data.readString();
          data.enforceNoDataAvail();
          boolean _result = this.VerifyName(_arg0, _arg1);
          reply.writeNoException();
          reply.writeBoolean(_result);
          break;
        }
        case TRANSACTION_GetInterfaceArray:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          data.enforceNoDataAvail();
          android.aidl.tests.INamedCallback[] _result = this.GetInterfaceArray(_arg0);
          reply.writeNoException();
          reply.writeInterfaceArray(_result);
          break;
        }
        case TRANSACTION_VerifyNamesWithInterfaceArray:
        {
          android.aidl.tests.INamedCallback[] _arg0;
          _arg0 = data.createInterfaceArray(android.aidl.tests.INamedCallback[]::new, android.aidl.tests.INamedCallback.Stub::asInterface);
          java.lang.String[] _arg1;
          _arg1 = data.createStringArray();
          data.enforceNoDataAvail();
          boolean _result = this.VerifyNamesWithInterfaceArray(_arg0, _arg1);
          reply.writeNoException();
          reply.writeBoolean(_result);
          break;
        }
        case TRANSACTION_GetNullableInterfaceArray:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          data.enforceNoDataAvail();
          android.aidl.tests.INamedCallback[] _result = this.GetNullableInterfaceArray(_arg0);
          reply.writeNoException();
          reply.writeInterfaceArray(_result);
          break;
        }
        case TRANSACTION_VerifyNamesWithNullableInterfaceArray:
        {
          android.aidl.tests.INamedCallback[] _arg0;
          _arg0 = data.createInterfaceArray(android.aidl.tests.INamedCallback[]::new, android.aidl.tests.INamedCallback.Stub::asInterface);
          java.lang.String[] _arg1;
          _arg1 = data.createStringArray();
          data.enforceNoDataAvail();
          boolean _result = this.VerifyNamesWithNullableInterfaceArray(_arg0, _arg1);
          reply.writeNoException();
          reply.writeBoolean(_result);
          break;
        }
        case TRANSACTION_GetInterfaceList:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          data.enforceNoDataAvail();
          java.util.List<android.aidl.tests.INamedCallback> _result = this.GetInterfaceList(_arg0);
          reply.writeNoException();
          reply.writeInterfaceList(_result);
          break;
        }
        case TRANSACTION_VerifyNamesWithInterfaceList:
        {
          java.util.List<android.aidl.tests.INamedCallback> _arg0;
          _arg0 = data.createInterfaceArrayList(android.aidl.tests.INamedCallback.Stub::asInterface);
          java.lang.String[] _arg1;
          _arg1 = data.createStringArray();
          data.enforceNoDataAvail();
          boolean _result = this.VerifyNamesWithInterfaceList(_arg0, _arg1);
          reply.writeNoException();
          reply.writeBoolean(_result);
          break;
        }
        case TRANSACTION_ReverseStringList:
        {
          java.util.List<java.lang.String> _arg0;
          _arg0 = data.createStringArrayList();
          java.util.List<java.lang.String> _arg1;
          _arg1 = new java.util.ArrayList<java.lang.String>();
          data.enforceNoDataAvail();
          java.util.List<java.lang.String> _result = this.ReverseStringList(_arg0, _arg1);
          reply.writeNoException();
          reply.writeStringList(_result);
          reply.writeStringList(_arg1);
          break;
        }
        case TRANSACTION_RepeatParcelFileDescriptor:
        {
          android.os.ParcelFileDescriptor _arg0;
          _arg0 = data.readTypedObject(android.os.ParcelFileDescriptor.CREATOR);
          data.enforceNoDataAvail();
          android.os.ParcelFileDescriptor _result = this.RepeatParcelFileDescriptor(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_ReverseParcelFileDescriptorArray:
        {
          android.os.ParcelFileDescriptor[] _arg0;
          _arg0 = data.createTypedArray(android.os.ParcelFileDescriptor.CREATOR);
          android.os.ParcelFileDescriptor[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new android.os.ParcelFileDescriptor[_arg1_length];
          }
          data.enforceNoDataAvail();
          android.os.ParcelFileDescriptor[] _result = this.ReverseParcelFileDescriptorArray(_arg0, _arg1);
          reply.writeNoException();
          reply.writeTypedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          reply.writeTypedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_ThrowServiceException:
        {
          int _arg0;
          _arg0 = data.readInt();
          data.enforceNoDataAvail();
          this.ThrowServiceException(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_RepeatNullableIntArray:
        {
          int[] _arg0;
          _arg0 = data.createIntArray();
          data.enforceNoDataAvail();
          int[] _result = this.RepeatNullableIntArray(_arg0);
          reply.writeNoException();
          reply.writeIntArray(_result);
          break;
        }
        case TRANSACTION_RepeatNullableByteEnumArray:
        {
          byte[] _arg0;
          _arg0 = data.createByteArray();
          data.enforceNoDataAvail();
          byte[] _result = this.RepeatNullableByteEnumArray(_arg0);
          reply.writeNoException();
          reply.writeByteArray(_result);
          break;
        }
        case TRANSACTION_RepeatNullableIntEnumArray:
        {
          int[] _arg0;
          _arg0 = data.createIntArray();
          data.enforceNoDataAvail();
          int[] _result = this.RepeatNullableIntEnumArray(_arg0);
          reply.writeNoException();
          reply.writeIntArray(_result);
          break;
        }
        case TRANSACTION_RepeatNullableLongEnumArray:
        {
          long[] _arg0;
          _arg0 = data.createLongArray();
          data.enforceNoDataAvail();
          long[] _result = this.RepeatNullableLongEnumArray(_arg0);
          reply.writeNoException();
          reply.writeLongArray(_result);
          break;
        }
        case TRANSACTION_RepeatNullableString:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          data.enforceNoDataAvail();
          java.lang.String _result = this.RepeatNullableString(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_RepeatNullableStringList:
        {
          java.util.List<java.lang.String> _arg0;
          _arg0 = data.createStringArrayList();
          data.enforceNoDataAvail();
          java.util.List<java.lang.String> _result = this.RepeatNullableStringList(_arg0);
          reply.writeNoException();
          reply.writeStringList(_result);
          break;
        }
        case TRANSACTION_RepeatNullableParcelable:
        {
          android.aidl.tests.ITestService.Empty _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.ITestService.Empty.CREATOR);
          data.enforceNoDataAvail();
          android.aidl.tests.ITestService.Empty _result = this.RepeatNullableParcelable(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_RepeatNullableParcelableArray:
        {
          android.aidl.tests.ITestService.Empty[] _arg0;
          _arg0 = data.createTypedArray(android.aidl.tests.ITestService.Empty.CREATOR);
          data.enforceNoDataAvail();
          android.aidl.tests.ITestService.Empty[] _result = this.RepeatNullableParcelableArray(_arg0);
          reply.writeNoException();
          reply.writeTypedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_RepeatNullableParcelableList:
        {
          java.util.List<android.aidl.tests.ITestService.Empty> _arg0;
          _arg0 = data.createTypedArrayList(android.aidl.tests.ITestService.Empty.CREATOR);
          data.enforceNoDataAvail();
          java.util.List<android.aidl.tests.ITestService.Empty> _result = this.RepeatNullableParcelableList(_arg0);
          reply.writeNoException();
          reply.writeTypedList(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_TakesAnIBinder:
        {
          android.os.IBinder _arg0;
          _arg0 = data.readStrongBinder();
          data.enforceNoDataAvail();
          this.TakesAnIBinder(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_TakesANullableIBinder:
        {
          android.os.IBinder _arg0;
          _arg0 = data.readStrongBinder();
          data.enforceNoDataAvail();
          this.TakesANullableIBinder(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_TakesAnIBinderList:
        {
          java.util.List<android.os.IBinder> _arg0;
          _arg0 = data.createBinderArrayList();
          data.enforceNoDataAvail();
          this.TakesAnIBinderList(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_TakesANullableIBinderList:
        {
          java.util.List<android.os.IBinder> _arg0;
          _arg0 = data.createBinderArrayList();
          data.enforceNoDataAvail();
          this.TakesANullableIBinderList(_arg0);
          reply.writeNoException();
          break;
        }
        case TRANSACTION_RepeatUtf8CppString:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          data.enforceNoDataAvail();
          java.lang.String _result = this.RepeatUtf8CppString(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_RepeatNullableUtf8CppString:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          data.enforceNoDataAvail();
          java.lang.String _result = this.RepeatNullableUtf8CppString(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_ReverseUtf8CppString:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          java.lang.String[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new java.lang.String[_arg1_length];
          }
          data.enforceNoDataAvail();
          java.lang.String[] _result = this.ReverseUtf8CppString(_arg0, _arg1);
          reply.writeNoException();
          reply.writeStringArray(_result);
          reply.writeStringArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseNullableUtf8CppString:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          java.lang.String[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new java.lang.String[_arg1_length];
          }
          data.enforceNoDataAvail();
          java.lang.String[] _result = this.ReverseNullableUtf8CppString(_arg0, _arg1);
          reply.writeNoException();
          reply.writeStringArray(_result);
          reply.writeStringArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseUtf8CppStringList:
        {
          java.util.List<java.lang.String> _arg0;
          _arg0 = data.createStringArrayList();
          java.util.List<java.lang.String> _arg1;
          _arg1 = new java.util.ArrayList<java.lang.String>();
          data.enforceNoDataAvail();
          java.util.List<java.lang.String> _result = this.ReverseUtf8CppStringList(_arg0, _arg1);
          reply.writeNoException();
          reply.writeStringList(_result);
          reply.writeStringList(_arg1);
          break;
        }
        case TRANSACTION_GetCallback:
        {
          boolean _arg0;
          _arg0 = data.readBoolean();
          data.enforceNoDataAvail();
          android.aidl.tests.INamedCallback _result = this.GetCallback(_arg0);
          reply.writeNoException();
          reply.writeStrongInterface(_result);
          break;
        }
        case TRANSACTION_FillOutStructuredParcelable:
        {
          android.aidl.tests.StructuredParcelable _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.StructuredParcelable.CREATOR);
          data.enforceNoDataAvail();
          this.FillOutStructuredParcelable(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_arg0, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_RepeatExtendableParcelable:
        {
          android.aidl.tests.extension.ExtendableParcelable _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.extension.ExtendableParcelable.CREATOR);
          android.aidl.tests.extension.ExtendableParcelable _arg1;
          _arg1 = new android.aidl.tests.extension.ExtendableParcelable();
          data.enforceNoDataAvail();
          this.RepeatExtendableParcelable(_arg0, _arg1);
          reply.writeNoException();
          reply.writeTypedObject(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_ReverseList:
        {
          android.aidl.tests.RecursiveList _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.RecursiveList.CREATOR);
          data.enforceNoDataAvail();
          android.aidl.tests.RecursiveList _result = this.ReverseList(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_ReverseIBinderArray:
        {
          android.os.IBinder[] _arg0;
          _arg0 = data.createBinderArray();
          android.os.IBinder[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new android.os.IBinder[_arg1_length];
          }
          data.enforceNoDataAvail();
          android.os.IBinder[] _result = this.ReverseIBinderArray(_arg0, _arg1);
          reply.writeNoException();
          reply.writeBinderArray(_result);
          reply.writeBinderArray(_arg1);
          break;
        }
        case TRANSACTION_ReverseNullableIBinderArray:
        {
          android.os.IBinder[] _arg0;
          _arg0 = data.createBinderArray();
          android.os.IBinder[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new android.os.IBinder[_arg1_length];
          }
          data.enforceNoDataAvail();
          android.os.IBinder[] _result = this.ReverseNullableIBinderArray(_arg0, _arg1);
          reply.writeNoException();
          reply.writeBinderArray(_result);
          reply.writeBinderArray(_arg1);
          break;
        }
        case TRANSACTION_RepeatSimpleParcelable:
        {
          android.aidl.tests.SimpleParcelable _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.SimpleParcelable.CREATOR);
          android.aidl.tests.SimpleParcelable _arg1;
          _arg1 = new android.aidl.tests.SimpleParcelable();
          data.enforceNoDataAvail();
          android.aidl.tests.SimpleParcelable _result = this.RepeatSimpleParcelable(_arg0, _arg1);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          reply.writeTypedObject(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_ReverseSimpleParcelables:
        {
          android.aidl.tests.SimpleParcelable[] _arg0;
          _arg0 = data.createTypedArray(android.aidl.tests.SimpleParcelable.CREATOR);
          android.aidl.tests.SimpleParcelable[] _arg1;
          int _arg1_length = data.readInt();
          if (_arg1_length < 0) {
            _arg1 = null;
          } else {
            _arg1 = new android.aidl.tests.SimpleParcelable[_arg1_length];
          }
          data.enforceNoDataAvail();
          android.aidl.tests.SimpleParcelable[] _result = this.ReverseSimpleParcelables(_arg0, _arg1);
          reply.writeNoException();
          reply.writeTypedArray(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          reply.writeTypedArray(_arg1, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_GetOldNameInterface:
        {
          android.aidl.tests.IOldName _result = this.GetOldNameInterface();
          reply.writeNoException();
          reply.writeStrongInterface(_result);
          break;
        }
        case TRANSACTION_GetNewNameInterface:
        {
          android.aidl.tests.INewName _result = this.GetNewNameInterface();
          reply.writeNoException();
          reply.writeStrongInterface(_result);
          break;
        }
        case TRANSACTION_GetUnionTags:
        {
          android.aidl.tests.Union[] _arg0;
          _arg0 = data.createTypedArray(android.aidl.tests.Union.CREATOR);
          data.enforceNoDataAvail();
          int[] _result = this.GetUnionTags(_arg0);
          reply.writeNoException();
          reply.writeIntArray(_result);
          break;
        }
        case TRANSACTION_GetCppJavaTests:
        {
          android.os.IBinder _result = this.GetCppJavaTests();
          reply.writeNoException();
          reply.writeStrongBinder(_result);
          break;
        }
        case TRANSACTION_getBackendType:
        {
          byte _result = this.getBackendType();
          reply.writeNoException();
          reply.writeByte(_result);
          break;
        }
        case TRANSACTION_GetCircular:
        {
          android.aidl.tests.CircularParcelable _arg0;
          _arg0 = new android.aidl.tests.CircularParcelable();
          data.enforceNoDataAvail();
          android.aidl.tests.ICircular _result = this.GetCircular(_arg0);
          reply.writeNoException();
          reply.writeStrongInterface(_result);
          reply.writeTypedObject(_arg0, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements android.aidl.tests.ITestService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      // This is to emulate a method that is added after the service is implemented.
      // So the client cannot assume that a call to this method will be successful
      // or not. However, inside the test environment, we can't build client and
      // the server with different version of this AIDL file. So, we let the server
      // actually implement this and intercept the dispatch to the method
      // inside onTransact().
      // WARNING: Must be first method.
      // This requires hard coding the transaction number. As long as this method is
      // the first in this interface, it can keep the
      // "::android::IBinder::FIRST_CALL_TRANSACTION + 0" value and allow
      // methods to be added and removed.
      @Override public int UnimplementedMethod(int arg) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(arg);
          boolean _status = mRemote.transact(Stub.TRANSACTION_UnimplementedMethod, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().UnimplementedMethod(arg);
            }
          }
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /**
       * @deprecated to make sure we have something in system/tools/aidl which does a compile check
       *     of deprecated and make sure this is reflected in goldens
       */
      @Override public void Deprecated() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_Deprecated, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              getDefaultImpl().Deprecated();
              return;
            }
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void TestOneway() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_TestOneway, _data, null, android.os.IBinder.FLAG_ONEWAY | android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              getDefaultImpl().TestOneway();
              return;
            }
          }
        }
        finally {
          _data.recycle();
        }
      }
      // Test that primitives work as parameters and return types.
      @Override public boolean RepeatBoolean(boolean token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeBoolean(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatBoolean, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatBoolean(token);
            }
          }
          _reply.readException();
          _result = _reply.readBoolean();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public byte RepeatByte(byte token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        byte _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByte(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatByte, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatByte(token);
            }
          }
          _reply.readException();
          _result = _reply.readByte();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public char RepeatChar(char token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        char _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(((int)token));
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatChar, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatChar(token);
            }
          }
          _reply.readException();
          _result = (char)_reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int RepeatInt(int token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatInt, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatInt(token);
            }
          }
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public long RepeatLong(long token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        long _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLong(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatLong, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatLong(token);
            }
          }
          _reply.readException();
          _result = _reply.readLong();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public float RepeatFloat(float token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        float _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeFloat(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatFloat, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatFloat(token);
            }
          }
          _reply.readException();
          _result = _reply.readFloat();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public double RepeatDouble(double token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        double _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeDouble(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatDouble, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatDouble(token);
            }
          }
          _reply.readException();
          _result = _reply.readDouble();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String RepeatString(java.lang.String token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatString, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatString(token);
            }
          }
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public byte RepeatByteEnum(byte token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        byte _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByte(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatByteEnum, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatByteEnum(token);
            }
          }
          _reply.readException();
          _result = _reply.readByte();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int RepeatIntEnum(int token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatIntEnum, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatIntEnum(token);
            }
          }
          _reply.readException();
          _result = _reply.readInt();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public long RepeatLongEnum(long token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        long _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLong(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatLongEnum, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatLongEnum(token);
            }
          }
          _reply.readException();
          _result = _reply.readLong();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Test that arrays work as parameters and return types.
      @Override public boolean[] ReverseBoolean(boolean[] input, boolean[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeBooleanArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseBoolean, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseBoolean(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createBooleanArray();
          _reply.readBooleanArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public byte[] ReverseByte(byte[] input, byte[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        byte[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByteArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseByte, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseByte(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createByteArray();
          _reply.readByteArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public char[] ReverseChar(char[] input, char[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        char[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeCharArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseChar, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseChar(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createCharArray();
          _reply.readCharArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int[] ReverseInt(int[] input, int[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeIntArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseInt, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseInt(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createIntArray();
          _reply.readIntArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public long[] ReverseLong(long[] input, long[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        long[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLongArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseLong, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseLong(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createLongArray();
          _reply.readLongArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public float[] ReverseFloat(float[] input, float[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        float[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeFloatArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseFloat, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseFloat(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createFloatArray();
          _reply.readFloatArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public double[] ReverseDouble(double[] input, double[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        double[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeDoubleArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseDouble, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseDouble(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createDoubleArray();
          _reply.readDoubleArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String[] ReverseString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseString, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseString(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createStringArray();
          _reply.readStringArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public byte[] ReverseByteEnum(byte[] input, byte[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        byte[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByteArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseByteEnum, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseByteEnum(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createByteArray();
          _reply.readByteArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int[] ReverseIntEnum(int[] input, int[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeIntArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseIntEnum, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseIntEnum(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createIntArray();
          _reply.readIntArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public long[] ReverseLongEnum(long[] input, long[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        long[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLongArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseLongEnum, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseLongEnum(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createLongArray();
          _reply.readLongArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Test that clients can send and receive Binders.
      @Override public android.aidl.tests.INamedCallback GetOtherTestService(java.lang.String name) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.INamedCallback _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(name);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetOtherTestService, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          _reply.setPropagateAllowBlocking();
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetOtherTestService(name);
            }
          }
          _reply.readException();
          _result = android.aidl.tests.INamedCallback.Stub.asInterface(_reply.readStrongBinder());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // returns true if the same service is already registered with the same name
      @Override public boolean SetOtherTestService(java.lang.String name, android.aidl.tests.INamedCallback service) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(name);
          _data.writeStrongInterface(service);
          boolean _status = mRemote.transact(Stub.TRANSACTION_SetOtherTestService, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().SetOtherTestService(name, service);
            }
          }
          _reply.readException();
          _result = _reply.readBoolean();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean VerifyName(android.aidl.tests.INamedCallback service, java.lang.String name) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongInterface(service);
          _data.writeString(name);
          boolean _status = mRemote.transact(Stub.TRANSACTION_VerifyName, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().VerifyName(service, name);
            }
          }
          _reply.readException();
          _result = _reply.readBoolean();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.INamedCallback[] GetInterfaceArray(java.lang.String[] names) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.INamedCallback[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(names);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetInterfaceArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetInterfaceArray(names);
            }
          }
          _reply.readException();
          _result = _reply.createInterfaceArray(android.aidl.tests.INamedCallback[]::new, android.aidl.tests.INamedCallback.Stub::asInterface);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean VerifyNamesWithInterfaceArray(android.aidl.tests.INamedCallback[] services, java.lang.String[] names) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInterfaceArray(services);
          _data.writeStringArray(names);
          boolean _status = mRemote.transact(Stub.TRANSACTION_VerifyNamesWithInterfaceArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().VerifyNamesWithInterfaceArray(services, names);
            }
          }
          _reply.readException();
          _result = _reply.readBoolean();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.INamedCallback[] GetNullableInterfaceArray(java.lang.String[] names) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.INamedCallback[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(names);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetNullableInterfaceArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetNullableInterfaceArray(names);
            }
          }
          _reply.readException();
          _result = _reply.createInterfaceArray(android.aidl.tests.INamedCallback[]::new, android.aidl.tests.INamedCallback.Stub::asInterface);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean VerifyNamesWithNullableInterfaceArray(android.aidl.tests.INamedCallback[] services, java.lang.String[] names) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInterfaceArray(services);
          _data.writeStringArray(names);
          boolean _status = mRemote.transact(Stub.TRANSACTION_VerifyNamesWithNullableInterfaceArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().VerifyNamesWithNullableInterfaceArray(services, names);
            }
          }
          _reply.readException();
          _result = _reply.readBoolean();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.List<android.aidl.tests.INamedCallback> GetInterfaceList(java.lang.String[] names) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<android.aidl.tests.INamedCallback> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(names);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetInterfaceList, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetInterfaceList(names);
            }
          }
          _reply.readException();
          _result = _reply.createInterfaceArrayList(android.aidl.tests.INamedCallback.Stub::asInterface);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public boolean VerifyNamesWithInterfaceList(java.util.List<android.aidl.tests.INamedCallback> services, java.lang.String[] names) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        boolean _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInterfaceList(services);
          _data.writeStringArray(names);
          boolean _status = mRemote.transact(Stub.TRANSACTION_VerifyNamesWithInterfaceList, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().VerifyNamesWithInterfaceList(services, names);
            }
          }
          _reply.readException();
          _result = _reply.readBoolean();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Test that List<T> types work correctly.
      @Override public java.util.List<java.lang.String> ReverseStringList(java.util.List<java.lang.String> input, java.util.List<java.lang.String> repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<java.lang.String> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringList(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseStringList, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseStringList(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createStringArrayList();
          _reply.readStringList(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.os.ParcelFileDescriptor RepeatParcelFileDescriptor(android.os.ParcelFileDescriptor read) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.ParcelFileDescriptor _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(read, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatParcelFileDescriptor, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatParcelFileDescriptor(read);
            }
          }
          _reply.readException();
          _result = _reply.readTypedObject(android.os.ParcelFileDescriptor.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.os.ParcelFileDescriptor[] ReverseParcelFileDescriptorArray(android.os.ParcelFileDescriptor[] input, android.os.ParcelFileDescriptor[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.ParcelFileDescriptor[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedArray(input, 0);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseParcelFileDescriptorArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseParcelFileDescriptorArray(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createTypedArray(android.os.ParcelFileDescriptor.CREATOR);
          _reply.readTypedArray(repeated, android.os.ParcelFileDescriptor.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Test that service specific exceptions work correctly.
      @Override public void ThrowServiceException(int code) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeInt(code);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ThrowServiceException, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              getDefaultImpl().ThrowServiceException(code);
              return;
            }
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Test nullability
      @Override public int[] RepeatNullableIntArray(int[] input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeIntArray(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableIntArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableIntArray(input);
            }
          }
          _reply.readException();
          _result = _reply.createIntArray();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public byte[] RepeatNullableByteEnumArray(byte[] input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        byte[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByteArray(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableByteEnumArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableByteEnumArray(input);
            }
          }
          _reply.readException();
          _result = _reply.createByteArray();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int[] RepeatNullableIntEnumArray(int[] input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeIntArray(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableIntEnumArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableIntEnumArray(input);
            }
          }
          _reply.readException();
          _result = _reply.createIntArray();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public long[] RepeatNullableLongEnumArray(long[] input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        long[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeLongArray(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableLongEnumArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableLongEnumArray(input);
            }
          }
          _reply.readException();
          _result = _reply.createLongArray();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String RepeatNullableString(java.lang.String input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableString, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableString(input);
            }
          }
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.List<java.lang.String> RepeatNullableStringList(java.util.List<java.lang.String> input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<java.lang.String> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringList(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableStringList, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableStringList(input);
            }
          }
          _reply.readException();
          _result = _reply.createStringArrayList();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.ITestService.Empty RepeatNullableParcelable(android.aidl.tests.ITestService.Empty input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.ITestService.Empty _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableParcelable, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableParcelable(input);
            }
          }
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.ITestService.Empty.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.ITestService.Empty[] RepeatNullableParcelableArray(android.aidl.tests.ITestService.Empty[] input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.ITestService.Empty[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedArray(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableParcelableArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableParcelableArray(input);
            }
          }
          _reply.readException();
          _result = _reply.createTypedArray(android.aidl.tests.ITestService.Empty.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.List<android.aidl.tests.ITestService.Empty> RepeatNullableParcelableList(java.util.List<android.aidl.tests.ITestService.Empty> input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<android.aidl.tests.ITestService.Empty> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedList(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableParcelableList, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableParcelableList(input);
            }
          }
          _reply.readException();
          _result = _reply.createTypedArrayList(android.aidl.tests.ITestService.Empty.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void TakesAnIBinder(android.os.IBinder input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongBinder(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_TakesAnIBinder, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              getDefaultImpl().TakesAnIBinder(input);
              return;
            }
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void TakesANullableIBinder(android.os.IBinder input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStrongBinder(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_TakesANullableIBinder, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              getDefaultImpl().TakesANullableIBinder(input);
              return;
            }
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void TakesAnIBinderList(java.util.List<android.os.IBinder> input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeBinderList(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_TakesAnIBinderList, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              getDefaultImpl().TakesAnIBinderList(input);
              return;
            }
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void TakesANullableIBinderList(java.util.List<android.os.IBinder> input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeBinderList(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_TakesANullableIBinderList, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              getDefaultImpl().TakesANullableIBinderList(input);
              return;
            }
          }
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Test utf8 decoding from utf16 wire format
      @Override public java.lang.String RepeatUtf8CppString(java.lang.String token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatUtf8CppString, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatUtf8CppString(token);
            }
          }
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String RepeatNullableUtf8CppString(java.lang.String token) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(token);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatNullableUtf8CppString, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatNullableUtf8CppString(token);
            }
          }
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String[] ReverseUtf8CppString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseUtf8CppString, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseUtf8CppString(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createStringArray();
          _reply.readStringArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.lang.String[] ReverseNullableUtf8CppString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseNullableUtf8CppString, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseNullableUtf8CppString(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createStringArray();
          _reply.readStringArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.List<java.lang.String> ReverseUtf8CppStringList(java.util.List<java.lang.String> input, java.util.List<java.lang.String> repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.List<java.lang.String> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringList(input);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseUtf8CppStringList, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseUtf8CppStringList(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createStringArrayList();
          _reply.readStringList(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      /** comment before annotation */
      @Override public android.aidl.tests.INamedCallback GetCallback(boolean return_null) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.INamedCallback _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeBoolean(return_null);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetCallback, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetCallback(return_null);
            }
          }
          _reply.readException();
          _result = android.aidl.tests.INamedCallback.Stub.asInterface(_reply.readStrongBinder());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Since this paracelable has clearly defined default values, it would be
      // inefficient to use an IPC to fill it out in practice.
      @Override public void FillOutStructuredParcelable(android.aidl.tests.StructuredParcelable parcel) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(parcel, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_FillOutStructuredParcelable, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              getDefaultImpl().FillOutStructuredParcelable(parcel);
              return;
            }
          }
          _reply.readException();
          if ((0!=_reply.readInt())) {
            parcel.readFromParcel(_reply);
          }
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public void RepeatExtendableParcelable(android.aidl.tests.extension.ExtendableParcelable ep, android.aidl.tests.extension.ExtendableParcelable ep2) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(ep, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatExtendableParcelable, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              getDefaultImpl().RepeatExtendableParcelable(ep, ep2);
              return;
            }
          }
          _reply.readException();
          if ((0!=_reply.readInt())) {
            ep2.readFromParcel(_reply);
          }
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      @Override public android.aidl.tests.RecursiveList ReverseList(android.aidl.tests.RecursiveList list) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.RecursiveList _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(list, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseList, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseList(list);
            }
          }
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.RecursiveList.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.os.IBinder[] ReverseIBinderArray(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.IBinder[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeBinderArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseIBinderArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseIBinderArray(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createBinderArray();
          _reply.readBinderArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.os.IBinder[] ReverseNullableIBinderArray(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.IBinder[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeBinderArray(input);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseNullableIBinderArray, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseNullableIBinderArray(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createBinderArray();
          _reply.readBinderArray(repeated);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.SimpleParcelable RepeatSimpleParcelable(android.aidl.tests.SimpleParcelable input, android.aidl.tests.SimpleParcelable repeat) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.SimpleParcelable _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_RepeatSimpleParcelable, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().RepeatSimpleParcelable(input, repeat);
            }
          }
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.SimpleParcelable.CREATOR);
          if ((0!=_reply.readInt())) {
            repeat.readFromParcel(_reply);
          }
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.SimpleParcelable[] ReverseSimpleParcelables(android.aidl.tests.SimpleParcelable[] input, android.aidl.tests.SimpleParcelable[] repeated) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.SimpleParcelable[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedArray(input, 0);
          _data.writeInt(repeated.length);
          boolean _status = mRemote.transact(Stub.TRANSACTION_ReverseSimpleParcelables, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().ReverseSimpleParcelables(input, repeated);
            }
          }
          _reply.readException();
          _result = _reply.createTypedArray(android.aidl.tests.SimpleParcelable.CREATOR);
          _reply.readTypedArray(repeated, android.aidl.tests.SimpleParcelable.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.IOldName GetOldNameInterface() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.IOldName _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetOldNameInterface, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetOldNameInterface();
            }
          }
          _reply.readException();
          _result = android.aidl.tests.IOldName.Stub.asInterface(_reply.readStrongBinder());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.INewName GetNewNameInterface() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.INewName _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetNewNameInterface, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetNewNameInterface();
            }
          }
          _reply.readException();
          _result = android.aidl.tests.INewName.Stub.asInterface(_reply.readStrongBinder());
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public int[] GetUnionTags(android.aidl.tests.Union[] input) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        int[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedArray(input, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetUnionTags, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetUnionTags(input);
            }
          }
          _reply.readException();
          _result = _reply.createIntArray();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Retrieve the ICppJavaTests if the server supports it
      @Override public android.os.IBinder GetCppJavaTests() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.os.IBinder _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetCppJavaTests, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetCppJavaTests();
            }
          }
          _reply.readException();
          _result = _reply.readStrongBinder();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public byte getBackendType() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        byte _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_getBackendType, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().getBackendType();
            }
          }
          _reply.readException();
          _result = _reply.readByte();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public android.aidl.tests.ICircular GetCircular(android.aidl.tests.CircularParcelable cp) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        _data.markSensitive();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.ICircular _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_GetCircular, _data, _reply, android.os.IBinder.FLAG_CLEAR_BUF);
          if (!_status) {
            if (getDefaultImpl() != null) {
              return getDefaultImpl().GetCircular(cp);
            }
          }
          _reply.readException();
          _result = android.aidl.tests.ICircular.Stub.asInterface(_reply.readStrongBinder());
          if ((0!=_reply.readInt())) {
            cp.readFromParcel(_reply);
          }
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      public static android.aidl.tests.ITestService sDefaultImpl;
    }
    static final int TRANSACTION_UnimplementedMethod = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_Deprecated = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_TestOneway = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_RepeatBoolean = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_RepeatByte = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_RepeatChar = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_RepeatInt = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_RepeatLong = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    static final int TRANSACTION_RepeatFloat = (android.os.IBinder.FIRST_CALL_TRANSACTION + 8);
    static final int TRANSACTION_RepeatDouble = (android.os.IBinder.FIRST_CALL_TRANSACTION + 9);
    static final int TRANSACTION_RepeatString = (android.os.IBinder.FIRST_CALL_TRANSACTION + 10);
    static final int TRANSACTION_RepeatByteEnum = (android.os.IBinder.FIRST_CALL_TRANSACTION + 11);
    static final int TRANSACTION_RepeatIntEnum = (android.os.IBinder.FIRST_CALL_TRANSACTION + 12);
    static final int TRANSACTION_RepeatLongEnum = (android.os.IBinder.FIRST_CALL_TRANSACTION + 13);
    static final int TRANSACTION_ReverseBoolean = (android.os.IBinder.FIRST_CALL_TRANSACTION + 14);
    static final int TRANSACTION_ReverseByte = (android.os.IBinder.FIRST_CALL_TRANSACTION + 15);
    static final int TRANSACTION_ReverseChar = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16);
    static final int TRANSACTION_ReverseInt = (android.os.IBinder.FIRST_CALL_TRANSACTION + 17);
    static final int TRANSACTION_ReverseLong = (android.os.IBinder.FIRST_CALL_TRANSACTION + 18);
    static final int TRANSACTION_ReverseFloat = (android.os.IBinder.FIRST_CALL_TRANSACTION + 19);
    static final int TRANSACTION_ReverseDouble = (android.os.IBinder.FIRST_CALL_TRANSACTION + 20);
    static final int TRANSACTION_ReverseString = (android.os.IBinder.FIRST_CALL_TRANSACTION + 21);
    static final int TRANSACTION_ReverseByteEnum = (android.os.IBinder.FIRST_CALL_TRANSACTION + 22);
    static final int TRANSACTION_ReverseIntEnum = (android.os.IBinder.FIRST_CALL_TRANSACTION + 23);
    static final int TRANSACTION_ReverseLongEnum = (android.os.IBinder.FIRST_CALL_TRANSACTION + 24);
    static final int TRANSACTION_GetOtherTestService = (android.os.IBinder.FIRST_CALL_TRANSACTION + 25);
    static final int TRANSACTION_SetOtherTestService = (android.os.IBinder.FIRST_CALL_TRANSACTION + 26);
    static final int TRANSACTION_VerifyName = (android.os.IBinder.FIRST_CALL_TRANSACTION + 27);
    static final int TRANSACTION_GetInterfaceArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 28);
    static final int TRANSACTION_VerifyNamesWithInterfaceArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 29);
    static final int TRANSACTION_GetNullableInterfaceArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 30);
    static final int TRANSACTION_VerifyNamesWithNullableInterfaceArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 31);
    static final int TRANSACTION_GetInterfaceList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 32);
    static final int TRANSACTION_VerifyNamesWithInterfaceList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 33);
    static final int TRANSACTION_ReverseStringList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 34);
    static final int TRANSACTION_RepeatParcelFileDescriptor = (android.os.IBinder.FIRST_CALL_TRANSACTION + 35);
    static final int TRANSACTION_ReverseParcelFileDescriptorArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 36);
    static final int TRANSACTION_ThrowServiceException = (android.os.IBinder.FIRST_CALL_TRANSACTION + 37);
    static final int TRANSACTION_RepeatNullableIntArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 38);
    static final int TRANSACTION_RepeatNullableByteEnumArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 39);
    static final int TRANSACTION_RepeatNullableIntEnumArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 40);
    static final int TRANSACTION_RepeatNullableLongEnumArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 41);
    static final int TRANSACTION_RepeatNullableString = (android.os.IBinder.FIRST_CALL_TRANSACTION + 42);
    static final int TRANSACTION_RepeatNullableStringList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 43);
    static final int TRANSACTION_RepeatNullableParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 44);
    static final int TRANSACTION_RepeatNullableParcelableArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 45);
    static final int TRANSACTION_RepeatNullableParcelableList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 46);
    static final int TRANSACTION_TakesAnIBinder = (android.os.IBinder.FIRST_CALL_TRANSACTION + 47);
    static final int TRANSACTION_TakesANullableIBinder = (android.os.IBinder.FIRST_CALL_TRANSACTION + 48);
    static final int TRANSACTION_TakesAnIBinderList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 49);
    static final int TRANSACTION_TakesANullableIBinderList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 50);
    static final int TRANSACTION_RepeatUtf8CppString = (android.os.IBinder.FIRST_CALL_TRANSACTION + 51);
    static final int TRANSACTION_RepeatNullableUtf8CppString = (android.os.IBinder.FIRST_CALL_TRANSACTION + 52);
    static final int TRANSACTION_ReverseUtf8CppString = (android.os.IBinder.FIRST_CALL_TRANSACTION + 53);
    static final int TRANSACTION_ReverseNullableUtf8CppString = (android.os.IBinder.FIRST_CALL_TRANSACTION + 54);
    static final int TRANSACTION_ReverseUtf8CppStringList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 55);
    static final int TRANSACTION_GetCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 56);
    static final int TRANSACTION_FillOutStructuredParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 57);
    static final int TRANSACTION_RepeatExtendableParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 58);
    static final int TRANSACTION_ReverseList = (android.os.IBinder.FIRST_CALL_TRANSACTION + 59);
    static final int TRANSACTION_ReverseIBinderArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 60);
    static final int TRANSACTION_ReverseNullableIBinderArray = (android.os.IBinder.FIRST_CALL_TRANSACTION + 61);
    static final int TRANSACTION_RepeatSimpleParcelable = (android.os.IBinder.FIRST_CALL_TRANSACTION + 62);
    static final int TRANSACTION_ReverseSimpleParcelables = (android.os.IBinder.FIRST_CALL_TRANSACTION + 63);
    static final int TRANSACTION_GetOldNameInterface = (android.os.IBinder.FIRST_CALL_TRANSACTION + 64);
    static final int TRANSACTION_GetNewNameInterface = (android.os.IBinder.FIRST_CALL_TRANSACTION + 65);
    static final int TRANSACTION_GetUnionTags = (android.os.IBinder.FIRST_CALL_TRANSACTION + 66);
    static final int TRANSACTION_GetCppJavaTests = (android.os.IBinder.FIRST_CALL_TRANSACTION + 67);
    static final int TRANSACTION_getBackendType = (android.os.IBinder.FIRST_CALL_TRANSACTION + 68);
    static final int TRANSACTION_GetCircular = (android.os.IBinder.FIRST_CALL_TRANSACTION + 69);
    /** @hide */
    public int getMaxTransactionId()
    {
      return 69;
    }
    public static boolean setDefaultImpl(android.aidl.tests.ITestService impl) {
      // Only one user of this interface can use this function
      // at a time. This is a heuristic to detect if two different
      // users in the same process use this function.
      if (Stub.Proxy.sDefaultImpl != null) {
        throw new IllegalStateException("setDefaultImpl() called twice");
      }
      if (impl != null) {
        Stub.Proxy.sDefaultImpl = impl;
        return true;
      }
      return false;
    }
    public static android.aidl.tests.ITestService getDefaultImpl() {
      return Stub.Proxy.sDefaultImpl;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.aidl.tests.ITestService";
  // Test that constants are accessible
  /** extra doc comment */
  // extra line comment
  /* extra regular comment */
  /** const comment */
  public static final int CONSTANT = 42;
  public static final int CONSTANT2 = -42;
  public static final int CONSTANT3 = 42;
  public static final int CONSTANT4 = 4;
  public static final int CONSTANT5 = -4;
  public static final int CONSTANT6 = 0;
  public static final int CONSTANT7 = 0;
  public static final int CONSTANT8 = 0;
  public static final int CONSTANT9 = 86;
  public static final int CONSTANT10 = 165;
  public static final int CONSTANT11 = 250;
  public static final int CONSTANT12 = -1;
  public static final byte BYTE_CONSTANT = 17;
  public static final long LONG_CONSTANT = 1099511627776L;
  public static final String STRING_CONSTANT = "foo";
  public static final String STRING_CONSTANT2 = "bar";
  public static final float FLOAT_CONSTANT = 1.000000f;
  public static final float FLOAT_CONSTANT2 = -1.000000f;
  public static final float FLOAT_CONSTANT3 = 1.000000f;
  public static final float FLOAT_CONSTANT4 = 2.200000f;
  public static final float FLOAT_CONSTANT5 = -2.200000f;
  public static final float FLOAT_CONSTANT6 = -0.000000f;
  public static final float FLOAT_CONSTANT7 = 0.000000f;
  public static final double DOUBLE_CONSTANT = 1.000000;
  public static final double DOUBLE_CONSTANT2 = -1.000000;
  public static final double DOUBLE_CONSTANT3 = 1.000000;
  public static final double DOUBLE_CONSTANT4 = 2.200000;
  public static final double DOUBLE_CONSTANT5 = -2.200000;
  public static final double DOUBLE_CONSTANT6 = -0.000000;
  public static final double DOUBLE_CONSTANT7 = 0.000000;
  public static final double DOUBLE_CONSTANT8 = 1.100000;
  public static final double DOUBLE_CONSTANT9 = -1.100000;
  public static final String STRING_CONSTANT_UTF8 = "baz";
  // All these constant expressions should be equal to 1
  public static final int A1 = 1;
  public static final int A2 = 1;
  public static final int A3 = 1;
  public static final int A4 = 1;
  public static final int A5 = 1;
  public static final int A6 = 1;
  public static final int A7 = 1;
  public static final int A8 = 1;
  public static final int A9 = 1;
  public static final int A10 = 1;
  public static final int A11 = 1;
  public static final int A12 = 1;
  public static final int A13 = 1;
  public static final int A14 = 1;
  public static final int A15 = 1;
  public static final int A16 = 1;
  public static final int A17 = 1;
  public static final int A18 = 1;
  public static final int A19 = 1;
  public static final int A20 = 1;
  public static final int A21 = 1;
  public static final int A22 = 1;
  public static final int A23 = 1;
  public static final int A24 = 1;
  public static final int A25 = 1;
  public static final int A26 = 1;
  public static final int A27 = 1;
  public static final int A28 = 1;
  public static final int A29 = 1;
  public static final int A30 = 1;
  public static final int A31 = 1;
  public static final int A32 = 1;
  public static final int A33 = 1;
  public static final int A34 = 1;
  public static final int A35 = 1;
  public static final int A36 = 1;
  public static final int A37 = 1;
  public static final int A38 = 1;
  public static final int A39 = 1;
  public static final int A40 = 1;
  public static final int A41 = 1;
  public static final int A42 = 1;
  public static final int A43 = 1;
  public static final int A44 = 1;
  // && higher than ||
  public static final int A45 = 1;
  public static final int A46 = 1;
  public static final int A47 = 1;
  public static final int A48 = 1;
  public static final int A49 = 1;
  public static final int A50 = 1;
  public static final int A51 = 1;
  public static final int A52 = 1;
  public static final int A53 = 1;
  // & higher than ^
  public static final int A54 = 1;
  public static final int A55 = 1;
  public static final int A56 = 1;
  public static final int A57 = 1;
  // This is to emulate a method that is added after the service is implemented.
  // So the client cannot assume that a call to this method will be successful
  // or not. However, inside the test environment, we can't build client and
  // the server with different version of this AIDL file. So, we let the server
  // actually implement this and intercept the dispatch to the method
  // inside onTransact().
  // WARNING: Must be first method.
  // This requires hard coding the transaction number. As long as this method is
  // the first in this interface, it can keep the
  // "::android::IBinder::FIRST_CALL_TRANSACTION + 0" value and allow
  // methods to be added and removed.
  public int UnimplementedMethod(int arg) throws android.os.RemoteException;
  /**
   * @deprecated to make sure we have something in system/tools/aidl which does a compile check
   *     of deprecated and make sure this is reflected in goldens
   */
  @Deprecated
  public void Deprecated() throws android.os.RemoteException;
  public void TestOneway() throws android.os.RemoteException;
  // Test that primitives work as parameters and return types.
  public boolean RepeatBoolean(boolean token) throws android.os.RemoteException;
  public byte RepeatByte(byte token) throws android.os.RemoteException;
  public char RepeatChar(char token) throws android.os.RemoteException;
  public int RepeatInt(int token) throws android.os.RemoteException;
  public long RepeatLong(long token) throws android.os.RemoteException;
  public float RepeatFloat(float token) throws android.os.RemoteException;
  public double RepeatDouble(double token) throws android.os.RemoteException;
  public java.lang.String RepeatString(java.lang.String token) throws android.os.RemoteException;
  public byte RepeatByteEnum(byte token) throws android.os.RemoteException;
  public int RepeatIntEnum(int token) throws android.os.RemoteException;
  public long RepeatLongEnum(long token) throws android.os.RemoteException;
  // Test that arrays work as parameters and return types.
  public boolean[] ReverseBoolean(boolean[] input, boolean[] repeated) throws android.os.RemoteException;
  public byte[] ReverseByte(byte[] input, byte[] repeated) throws android.os.RemoteException;
  public char[] ReverseChar(char[] input, char[] repeated) throws android.os.RemoteException;
  public int[] ReverseInt(int[] input, int[] repeated) throws android.os.RemoteException;
  public long[] ReverseLong(long[] input, long[] repeated) throws android.os.RemoteException;
  public float[] ReverseFloat(float[] input, float[] repeated) throws android.os.RemoteException;
  public double[] ReverseDouble(double[] input, double[] repeated) throws android.os.RemoteException;
  public java.lang.String[] ReverseString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException;
  public byte[] ReverseByteEnum(byte[] input, byte[] repeated) throws android.os.RemoteException;
  public int[] ReverseIntEnum(int[] input, int[] repeated) throws android.os.RemoteException;
  public long[] ReverseLongEnum(long[] input, long[] repeated) throws android.os.RemoteException;
  // Test that clients can send and receive Binders.
  public android.aidl.tests.INamedCallback GetOtherTestService(java.lang.String name) throws android.os.RemoteException;
  // returns true if the same service is already registered with the same name
  public boolean SetOtherTestService(java.lang.String name, android.aidl.tests.INamedCallback service) throws android.os.RemoteException;
  public boolean VerifyName(android.aidl.tests.INamedCallback service, java.lang.String name) throws android.os.RemoteException;
  public android.aidl.tests.INamedCallback[] GetInterfaceArray(java.lang.String[] names) throws android.os.RemoteException;
  public boolean VerifyNamesWithInterfaceArray(android.aidl.tests.INamedCallback[] services, java.lang.String[] names) throws android.os.RemoteException;
  public android.aidl.tests.INamedCallback[] GetNullableInterfaceArray(java.lang.String[] names) throws android.os.RemoteException;
  public boolean VerifyNamesWithNullableInterfaceArray(android.aidl.tests.INamedCallback[] services, java.lang.String[] names) throws android.os.RemoteException;
  public java.util.List<android.aidl.tests.INamedCallback> GetInterfaceList(java.lang.String[] names) throws android.os.RemoteException;
  public boolean VerifyNamesWithInterfaceList(java.util.List<android.aidl.tests.INamedCallback> services, java.lang.String[] names) throws android.os.RemoteException;
  // Test that List<T> types work correctly.
  public java.util.List<java.lang.String> ReverseStringList(java.util.List<java.lang.String> input, java.util.List<java.lang.String> repeated) throws android.os.RemoteException;
  public android.os.ParcelFileDescriptor RepeatParcelFileDescriptor(android.os.ParcelFileDescriptor read) throws android.os.RemoteException;
  public android.os.ParcelFileDescriptor[] ReverseParcelFileDescriptorArray(android.os.ParcelFileDescriptor[] input, android.os.ParcelFileDescriptor[] repeated) throws android.os.RemoteException;
  // Test that service specific exceptions work correctly.
  public void ThrowServiceException(int code) throws android.os.RemoteException;
  // Test nullability
  public int[] RepeatNullableIntArray(int[] input) throws android.os.RemoteException;
  public byte[] RepeatNullableByteEnumArray(byte[] input) throws android.os.RemoteException;
  public int[] RepeatNullableIntEnumArray(int[] input) throws android.os.RemoteException;
  public long[] RepeatNullableLongEnumArray(long[] input) throws android.os.RemoteException;
  public java.lang.String RepeatNullableString(java.lang.String input) throws android.os.RemoteException;
  public java.util.List<java.lang.String> RepeatNullableStringList(java.util.List<java.lang.String> input) throws android.os.RemoteException;
  public android.aidl.tests.ITestService.Empty RepeatNullableParcelable(android.aidl.tests.ITestService.Empty input) throws android.os.RemoteException;
  public android.aidl.tests.ITestService.Empty[] RepeatNullableParcelableArray(android.aidl.tests.ITestService.Empty[] input) throws android.os.RemoteException;
  public java.util.List<android.aidl.tests.ITestService.Empty> RepeatNullableParcelableList(java.util.List<android.aidl.tests.ITestService.Empty> input) throws android.os.RemoteException;
  public void TakesAnIBinder(android.os.IBinder input) throws android.os.RemoteException;
  public void TakesANullableIBinder(android.os.IBinder input) throws android.os.RemoteException;
  public void TakesAnIBinderList(java.util.List<android.os.IBinder> input) throws android.os.RemoteException;
  public void TakesANullableIBinderList(java.util.List<android.os.IBinder> input) throws android.os.RemoteException;
  // Test utf8 decoding from utf16 wire format
  public java.lang.String RepeatUtf8CppString(java.lang.String token) throws android.os.RemoteException;
  public java.lang.String RepeatNullableUtf8CppString(java.lang.String token) throws android.os.RemoteException;
  public java.lang.String[] ReverseUtf8CppString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException;
  public java.lang.String[] ReverseNullableUtf8CppString(java.lang.String[] input, java.lang.String[] repeated) throws android.os.RemoteException;
  public java.util.List<java.lang.String> ReverseUtf8CppStringList(java.util.List<java.lang.String> input, java.util.List<java.lang.String> repeated) throws android.os.RemoteException;
  /** comment before annotation */
  public android.aidl.tests.INamedCallback GetCallback(boolean return_null) throws android.os.RemoteException;
  // Since this paracelable has clearly defined default values, it would be
  // inefficient to use an IPC to fill it out in practice.
  public void FillOutStructuredParcelable(android.aidl.tests.StructuredParcelable parcel) throws android.os.RemoteException;
  public void RepeatExtendableParcelable(android.aidl.tests.extension.ExtendableParcelable ep, android.aidl.tests.extension.ExtendableParcelable ep2) throws android.os.RemoteException;
  public android.aidl.tests.RecursiveList ReverseList(android.aidl.tests.RecursiveList list) throws android.os.RemoteException;
  public android.os.IBinder[] ReverseIBinderArray(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException;
  public android.os.IBinder[] ReverseNullableIBinderArray(android.os.IBinder[] input, android.os.IBinder[] repeated) throws android.os.RemoteException;
  public android.aidl.tests.SimpleParcelable RepeatSimpleParcelable(android.aidl.tests.SimpleParcelable input, android.aidl.tests.SimpleParcelable repeat) throws android.os.RemoteException;
  public android.aidl.tests.SimpleParcelable[] ReverseSimpleParcelables(android.aidl.tests.SimpleParcelable[] input, android.aidl.tests.SimpleParcelable[] repeated) throws android.os.RemoteException;
  public android.aidl.tests.IOldName GetOldNameInterface() throws android.os.RemoteException;
  public android.aidl.tests.INewName GetNewNameInterface() throws android.os.RemoteException;
  public int[] GetUnionTags(android.aidl.tests.Union[] input) throws android.os.RemoteException;
  // Retrieve the ICppJavaTests if the server supports it
  public android.os.IBinder GetCppJavaTests() throws android.os.RemoteException;
  public byte getBackendType() throws android.os.RemoteException;
  public android.aidl.tests.ICircular GetCircular(android.aidl.tests.CircularParcelable cp) throws android.os.RemoteException;
  // Small empty parcelable for nullability check
  public static class Empty implements android.os.Parcelable
  {
    public static final android.os.Parcelable.Creator<Empty> CREATOR = new android.os.Parcelable.Creator<Empty>() {
      @Override
      public Empty createFromParcel(android.os.Parcel _aidl_source) {
        Empty _aidl_out = new Empty();
        _aidl_out.readFromParcel(_aidl_source);
        return _aidl_out;
      }
      @Override
      public Empty[] newArray(int _aidl_size) {
        return new Empty[_aidl_size];
      }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.writeInt(0);
      int _aidl_end_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.setDataPosition(_aidl_start_pos);
      _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
      _aidl_parcel.setDataPosition(_aidl_end_pos);
    }
    public final void readFromParcel(android.os.Parcel _aidl_parcel)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      int _aidl_parcelable_size = _aidl_parcel.readInt();
      try {
        if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
      } finally {
        if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
          throw new android.os.BadParcelableException("Overflow in the size of parcelable");
        }
        _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
      }
    }
    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null) return false;
      if (!(other instanceof Empty)) return false;
      Empty that = (Empty)other;
      return true;
    }

    @Override
    public int hashCode() {
      return java.util.Arrays.deepHashCode(java.util.Arrays.asList().toArray());
    }
    @Override
    public int describeContents() {
      int _mask = 0;
      return _mask;
    }
  }
  public static class CompilerChecks implements android.os.Parcelable
  {
    // IBinder
    public android.os.IBinder binder;
    public android.os.IBinder nullable_binder;
    public android.os.IBinder[] binder_array;
    public android.os.IBinder[] nullable_binder_array;
    public java.util.List<android.os.IBinder> binder_list;
    public java.util.List<android.os.IBinder> nullable_binder_list;
    // ParcelFileDescriptor
    public android.os.ParcelFileDescriptor pfd;
    public android.os.ParcelFileDescriptor nullable_pfd;
    public android.os.ParcelFileDescriptor[] pfd_array;
    public android.os.ParcelFileDescriptor[] nullable_pfd_array;
    public java.util.List<android.os.ParcelFileDescriptor> pfd_list;
    public java.util.List<android.os.ParcelFileDescriptor> nullable_pfd_list;
    // parcelable
    public android.aidl.tests.ITestService.Empty parcel;
    public android.aidl.tests.ITestService.Empty nullable_parcel;
    public android.aidl.tests.ITestService.Empty[] parcel_array;
    public android.aidl.tests.ITestService.Empty[] nullable_parcel_array;
    public java.util.List<android.aidl.tests.ITestService.Empty> parcel_list;
    public java.util.List<android.aidl.tests.ITestService.Empty> nullable_parcel_list;
    public static final android.os.Parcelable.Creator<CompilerChecks> CREATOR = new android.os.Parcelable.Creator<CompilerChecks>() {
      @Override
      public CompilerChecks createFromParcel(android.os.Parcel _aidl_source) {
        CompilerChecks _aidl_out = new CompilerChecks();
        _aidl_out.readFromParcel(_aidl_source);
        return _aidl_out;
      }
      @Override
      public CompilerChecks[] newArray(int _aidl_size) {
        return new CompilerChecks[_aidl_size];
      }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.writeInt(0);
      _aidl_parcel.writeStrongBinder(binder);
      _aidl_parcel.writeStrongBinder(nullable_binder);
      _aidl_parcel.writeBinderArray(binder_array);
      _aidl_parcel.writeBinderArray(nullable_binder_array);
      _aidl_parcel.writeBinderList(binder_list);
      _aidl_parcel.writeBinderList(nullable_binder_list);
      _aidl_parcel.writeTypedObject(pfd, _aidl_flag);
      _aidl_parcel.writeTypedObject(nullable_pfd, _aidl_flag);
      _aidl_parcel.writeTypedArray(pfd_array, _aidl_flag);
      _aidl_parcel.writeTypedArray(nullable_pfd_array, _aidl_flag);
      _aidl_parcel.writeTypedList(pfd_list, _aidl_flag);
      _aidl_parcel.writeTypedList(nullable_pfd_list, _aidl_flag);
      _aidl_parcel.writeTypedObject(parcel, _aidl_flag);
      _aidl_parcel.writeTypedObject(nullable_parcel, _aidl_flag);
      _aidl_parcel.writeTypedArray(parcel_array, _aidl_flag);
      _aidl_parcel.writeTypedArray(nullable_parcel_array, _aidl_flag);
      _aidl_parcel.writeTypedList(parcel_list, _aidl_flag);
      _aidl_parcel.writeTypedList(nullable_parcel_list, _aidl_flag);
      int _aidl_end_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.setDataPosition(_aidl_start_pos);
      _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
      _aidl_parcel.setDataPosition(_aidl_end_pos);
    }
    public final void readFromParcel(android.os.Parcel _aidl_parcel)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      int _aidl_parcelable_size = _aidl_parcel.readInt();
      try {
        if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        binder = _aidl_parcel.readStrongBinder();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_binder = _aidl_parcel.readStrongBinder();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        binder_array = _aidl_parcel.createBinderArray();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_binder_array = _aidl_parcel.createBinderArray();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        binder_list = _aidl_parcel.createBinderArrayList();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_binder_list = _aidl_parcel.createBinderArrayList();
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        pfd = _aidl_parcel.readTypedObject(android.os.ParcelFileDescriptor.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_pfd = _aidl_parcel.readTypedObject(android.os.ParcelFileDescriptor.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        pfd_array = _aidl_parcel.createTypedArray(android.os.ParcelFileDescriptor.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_pfd_array = _aidl_parcel.createTypedArray(android.os.ParcelFileDescriptor.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        pfd_list = _aidl_parcel.createTypedArrayList(android.os.ParcelFileDescriptor.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_pfd_list = _aidl_parcel.createTypedArrayList(android.os.ParcelFileDescriptor.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        parcel = _aidl_parcel.readTypedObject(android.aidl.tests.ITestService.Empty.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_parcel = _aidl_parcel.readTypedObject(android.aidl.tests.ITestService.Empty.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        parcel_array = _aidl_parcel.createTypedArray(android.aidl.tests.ITestService.Empty.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_parcel_array = _aidl_parcel.createTypedArray(android.aidl.tests.ITestService.Empty.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        parcel_list = _aidl_parcel.createTypedArrayList(android.aidl.tests.ITestService.Empty.CREATOR);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_parcel_list = _aidl_parcel.createTypedArrayList(android.aidl.tests.ITestService.Empty.CREATOR);
      } finally {
        if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
          throw new android.os.BadParcelableException("Overflow in the size of parcelable");
        }
        _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
      }
    }
    @Override
    public int describeContents() {
      int _mask = 0;
      _mask |= describeContents(pfd);
      _mask |= describeContents(nullable_pfd);
      _mask |= describeContents(pfd_array);
      _mask |= describeContents(nullable_pfd_array);
      _mask |= describeContents(pfd_list);
      _mask |= describeContents(nullable_pfd_list);
      _mask |= describeContents(parcel);
      _mask |= describeContents(nullable_parcel);
      _mask |= describeContents(parcel_array);
      _mask |= describeContents(nullable_parcel_array);
      _mask |= describeContents(parcel_list);
      _mask |= describeContents(nullable_parcel_list);
      return _mask;
    }
    private int describeContents(Object _v) {
      if (_v == null) return 0;
      if (_v instanceof Object[]) {
        int _mask = 0;
        for (Object o : (Object[]) _v) {
          _mask |= describeContents(o);
        }
        return _mask;
      }
      if (_v instanceof java.util.Collection) {
        int _mask = 0;
        for (Object o : (java.util.Collection) _v) {
          _mask |= describeContents(o);
        }
        return _mask;
      }
      if (_v instanceof android.os.Parcelable) {
        return ((android.os.Parcelable) _v).describeContents();
      }
      return 0;
    }
    // interface without I-
    public interface Foo extends android.os.IInterface
    {
      /** Default implementation for Foo. */
      public static class Default implements android.aidl.tests.ITestService.CompilerChecks.Foo
      {
        @Override
        public android.os.IBinder asBinder() {
          return null;
        }
      }
      /** Local-side IPC implementation stub class. */
      public static abstract class Stub extends android.os.Binder implements android.aidl.tests.ITestService.CompilerChecks.Foo
      {
        /** Construct the stub at attach it to the interface. */
        @SuppressWarnings("this-escape")
        public Stub()
        {
          this.attachInterface(this, DESCRIPTOR);
        }
        /**
         * Cast an IBinder object into an android.aidl.tests.ITestService.CompilerChecks.Foo interface,
         * generating a proxy if needed.
         */
        public static android.aidl.tests.ITestService.CompilerChecks.Foo asInterface(android.os.IBinder obj)
        {
          if ((obj==null)) {
            return null;
          }
          android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
          if (((iin!=null)&&(iin instanceof android.aidl.tests.ITestService.CompilerChecks.Foo))) {
            return ((android.aidl.tests.ITestService.CompilerChecks.Foo)iin);
          }
          return new android.aidl.tests.ITestService.CompilerChecks.Foo.Stub.Proxy(obj);
        }
        @Override public android.os.IBinder asBinder()
        {
          return this;
        }
        /** @hide */
        public static java.lang.String getDefaultTransactionName(int transactionCode)
        {
          switch (transactionCode)
          {
            default:
            {
              return null;
            }
          }
        }
        /** @hide */
        public java.lang.String getTransactionName(int transactionCode)
        {
          return this.getDefaultTransactionName(transactionCode);
        }
        @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
        {
          java.lang.String descriptor = DESCRIPTOR;
          if (code == INTERFACE_TRANSACTION) {
            reply.writeString(descriptor);
            return true;
          }
          switch (code)
          {
            default:
            {
              return super.onTransact(code, data, reply, flags);
            }
          }
        }
        private static class Proxy implements android.aidl.tests.ITestService.CompilerChecks.Foo
        {
          private android.os.IBinder mRemote;
          Proxy(android.os.IBinder remote)
          {
            mRemote = remote;
          }
          @Override public android.os.IBinder asBinder()
          {
            return mRemote;
          }
          public java.lang.String getInterfaceDescriptor()
          {
            return DESCRIPTOR;
          }
        }
        /** @hide */
        public int getMaxTransactionId()
        {
          return 0;
        }
      }
      /** @hide */
      public static final java.lang.String DESCRIPTOR = "android.aidl.tests.ITestService.CompilerChecks.Foo";
    }
    public static class HasDeprecated implements android.os.Parcelable
    {
      /** @deprecated field */
      @Deprecated
      public int deprecated = 0;
      public static final android.os.Parcelable.Creator<HasDeprecated> CREATOR = new android.os.Parcelable.Creator<HasDeprecated>() {
        @Override
        public HasDeprecated createFromParcel(android.os.Parcel _aidl_source) {
          HasDeprecated _aidl_out = new HasDeprecated();
          _aidl_out.readFromParcel(_aidl_source);
          return _aidl_out;
        }
        @Override
        public HasDeprecated[] newArray(int _aidl_size) {
          return new HasDeprecated[_aidl_size];
        }
      };
      @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
      {
        int _aidl_start_pos = _aidl_parcel.dataPosition();
        _aidl_parcel.writeInt(0);
        _aidl_parcel.writeInt(deprecated);
        int _aidl_end_pos = _aidl_parcel.dataPosition();
        _aidl_parcel.setDataPosition(_aidl_start_pos);
        _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
        _aidl_parcel.setDataPosition(_aidl_end_pos);
      }
      public final void readFromParcel(android.os.Parcel _aidl_parcel)
      {
        int _aidl_start_pos = _aidl_parcel.dataPosition();
        int _aidl_parcelable_size = _aidl_parcel.readInt();
        try {
          if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
          if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
          deprecated = _aidl_parcel.readInt();
        } finally {
          if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
            throw new android.os.BadParcelableException("Overflow in the size of parcelable");
          }
          _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
        }
      }
      @Override
      public int describeContents() {
        int _mask = 0;
        return _mask;
      }
    }
    public static final class UsingHasDeprecated implements android.os.Parcelable {
      // tags for union fields
      public final static int n = 0;  // int n;
      public final static int m = 1;  // android.aidl.tests.ITestService.CompilerChecks.HasDeprecated m;

      private int _tag;
      private Object _value;

      public UsingHasDeprecated() {
        int _value = 0;
        this._tag = n;
        this._value = _value;
      }

      private UsingHasDeprecated(android.os.Parcel _aidl_parcel) {
        readFromParcel(_aidl_parcel);
      }

      private UsingHasDeprecated(int _tag, Object _value) {
        this._tag = _tag;
        this._value = _value;
      }

      public int getTag() {
        return _tag;
      }

      // int n;

      public static UsingHasDeprecated n(int _value) {
        return new UsingHasDeprecated(n, _value);
      }

      public int getN() {
        _assertTag(n);
        return (int) _value;
      }

      public void setN(int _value) {
        _set(n, _value);
      }

      // android.aidl.tests.ITestService.CompilerChecks.HasDeprecated m;

      public static UsingHasDeprecated m(android.aidl.tests.ITestService.CompilerChecks.HasDeprecated _value) {
        return new UsingHasDeprecated(m, _value);
      }

      public android.aidl.tests.ITestService.CompilerChecks.HasDeprecated getM() {
        _assertTag(m);
        return (android.aidl.tests.ITestService.CompilerChecks.HasDeprecated) _value;
      }

      public void setM(android.aidl.tests.ITestService.CompilerChecks.HasDeprecated _value) {
        _set(m, _value);
      }

      public static final android.os.Parcelable.Creator<UsingHasDeprecated> CREATOR = new android.os.Parcelable.Creator<UsingHasDeprecated>() {
        @Override
        public UsingHasDeprecated createFromParcel(android.os.Parcel _aidl_source) {
          return new UsingHasDeprecated(_aidl_source);
        }
        @Override
        public UsingHasDeprecated[] newArray(int _aidl_size) {
          return new UsingHasDeprecated[_aidl_size];
        }
      };

      @Override
      public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
        _aidl_parcel.writeInt(_tag);
        switch (_tag) {
        case n:
          _aidl_parcel.writeInt(getN());
          break;
        case m:
          _aidl_parcel.writeTypedObject(getM(), _aidl_flag);
          break;
        }
      }

      public void readFromParcel(android.os.Parcel _aidl_parcel) {
        int _aidl_tag;
        _aidl_tag = _aidl_parcel.readInt();
        switch (_aidl_tag) {
        case n: {
          int _aidl_value;
          _aidl_value = _aidl_parcel.readInt();
          _set(_aidl_tag, _aidl_value);
          return; }
        case m: {
          android.aidl.tests.ITestService.CompilerChecks.HasDeprecated _aidl_value;
          _aidl_value = _aidl_parcel.readTypedObject(android.aidl.tests.ITestService.CompilerChecks.HasDeprecated.CREATOR);
          _set(_aidl_tag, _aidl_value);
          return; }
        }
        throw new IllegalArgumentException("union: unknown tag: " + _aidl_tag);
      }

      @Override
      public int describeContents() {
        int _mask = 0;
        switch (getTag()) {
        case m:
          _mask |= describeContents(getM());
          break;
        }
        return _mask;
      }
      private int describeContents(Object _v) {
        if (_v == null) return 0;
        if (_v instanceof android.os.Parcelable) {
          return ((android.os.Parcelable) _v).describeContents();
        }
        return 0;
      }

      private void _assertTag(int tag) {
        if (getTag() != tag) {
          throw new IllegalStateException("bad access: " + _tagString(tag) + ", " + _tagString(getTag()) + " is available.");
        }
      }

      private String _tagString(int _tag) {
        switch (_tag) {
        case n: return "n";
        case m: return "m";
        }
        throw new IllegalStateException("unknown field: " + _tag);
      }

      private void _set(int _tag, Object _value) {
        this._tag = _tag;
        this._value = _value;
      }
      public static @interface Tag {
        public static final int n = 0;
        public static final int m = 1;
      }
    }
    public interface NoPrefixInterface extends android.os.IInterface
    {
      /** Default implementation for NoPrefixInterface. */
      public static class Default implements android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface
      {
        @Override public void foo() throws android.os.RemoteException
        {
        }
        @Override
        public android.os.IBinder asBinder() {
          return null;
        }
      }
      /** Local-side IPC implementation stub class. */
      public static abstract class Stub extends android.os.Binder implements android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface
      {
        /** Construct the stub at attach it to the interface. */
        @SuppressWarnings("this-escape")
        public Stub()
        {
          this.attachInterface(this, DESCRIPTOR);
        }
        /**
         * Cast an IBinder object into an android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface interface,
         * generating a proxy if needed.
         */
        public static android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface asInterface(android.os.IBinder obj)
        {
          if ((obj==null)) {
            return null;
          }
          android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
          if (((iin!=null)&&(iin instanceof android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface))) {
            return ((android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface)iin);
          }
          return new android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.Stub.Proxy(obj);
        }
        @Override public android.os.IBinder asBinder()
        {
          return this;
        }
        /** @hide */
        public static java.lang.String getDefaultTransactionName(int transactionCode)
        {
          switch (transactionCode)
          {
            case TRANSACTION_foo:
            {
              return "foo";
            }
            default:
            {
              return null;
            }
          }
        }
        /** @hide */
        public java.lang.String getTransactionName(int transactionCode)
        {
          return this.getDefaultTransactionName(transactionCode);
        }
        @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
        {
          java.lang.String descriptor = DESCRIPTOR;
          if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
            data.enforceInterface(descriptor);
          }
          if (code == INTERFACE_TRANSACTION) {
            reply.writeString(descriptor);
            return true;
          }
          switch (code)
          {
            case TRANSACTION_foo:
            {
              this.foo();
              reply.writeNoException();
              break;
            }
            default:
            {
              return super.onTransact(code, data, reply, flags);
            }
          }
          return true;
        }
        private static class Proxy implements android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface
        {
          private android.os.IBinder mRemote;
          Proxy(android.os.IBinder remote)
          {
            mRemote = remote;
          }
          @Override public android.os.IBinder asBinder()
          {
            return mRemote;
          }
          public java.lang.String getInterfaceDescriptor()
          {
            return DESCRIPTOR;
          }
          @Override public void foo() throws android.os.RemoteException
          {
            android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
            android.os.Parcel _reply = android.os.Parcel.obtain();
            try {
              _data.writeInterfaceToken(DESCRIPTOR);
              boolean _status = mRemote.transact(Stub.TRANSACTION_foo, _data, _reply, 0);
              _reply.readException();
            }
            finally {
              _reply.recycle();
              _data.recycle();
            }
          }
        }
        static final int TRANSACTION_foo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
        /** @hide */
        public int getMaxTransactionId()
        {
          return 0;
        }
      }
      /** @hide */
      public static final java.lang.String DESCRIPTOR = "android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface";
      public void foo() throws android.os.RemoteException;
      public static class Nested implements android.os.Parcelable
      {
        public static final android.os.Parcelable.Creator<Nested> CREATOR = new android.os.Parcelable.Creator<Nested>() {
          @Override
          public Nested createFromParcel(android.os.Parcel _aidl_source) {
            Nested _aidl_out = new Nested();
            _aidl_out.readFromParcel(_aidl_source);
            return _aidl_out;
          }
          @Override
          public Nested[] newArray(int _aidl_size) {
            return new Nested[_aidl_size];
          }
        };
        @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
        {
          int _aidl_start_pos = _aidl_parcel.dataPosition();
          _aidl_parcel.writeInt(0);
          int _aidl_end_pos = _aidl_parcel.dataPosition();
          _aidl_parcel.setDataPosition(_aidl_start_pos);
          _aidl_parcel.writeInt(_aidl_end_pos - _aidl_start_pos);
          _aidl_parcel.setDataPosition(_aidl_end_pos);
        }
        public final void readFromParcel(android.os.Parcel _aidl_parcel)
        {
          int _aidl_start_pos = _aidl_parcel.dataPosition();
          int _aidl_parcelable_size = _aidl_parcel.readInt();
          try {
            if (_aidl_parcelable_size < 4) throw new android.os.BadParcelableException("Parcelable too small");;
          } finally {
            if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
              throw new android.os.BadParcelableException("Overflow in the size of parcelable");
            }
            _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
          }
        }
        @Override
        public int describeContents() {
          int _mask = 0;
          return _mask;
        }
      }
      public interface NestedNoPrefixInterface extends android.os.IInterface
      {
        /** Default implementation for NestedNoPrefixInterface. */
        public static class Default implements android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface
        {
          @Override public void foo() throws android.os.RemoteException
          {
          }
          @Override
          public android.os.IBinder asBinder() {
            return null;
          }
        }
        /** Local-side IPC implementation stub class. */
        public static abstract class Stub extends android.os.Binder implements android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface
        {
          /** Construct the stub at attach it to the interface. */
          @SuppressWarnings("this-escape")
          public Stub()
          {
            this.attachInterface(this, DESCRIPTOR);
          }
          /**
           * Cast an IBinder object into an android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface interface,
           * generating a proxy if needed.
           */
          public static android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface asInterface(android.os.IBinder obj)
          {
            if ((obj==null)) {
              return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin!=null)&&(iin instanceof android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface))) {
              return ((android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface)iin);
            }
            return new android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface.Stub.Proxy(obj);
          }
          @Override public android.os.IBinder asBinder()
          {
            return this;
          }
          /** @hide */
          public static java.lang.String getDefaultTransactionName(int transactionCode)
          {
            switch (transactionCode)
            {
              case TRANSACTION_foo:
              {
                return "foo";
              }
              default:
              {
                return null;
              }
            }
          }
          /** @hide */
          public java.lang.String getTransactionName(int transactionCode)
          {
            return this.getDefaultTransactionName(transactionCode);
          }
          @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
          {
            java.lang.String descriptor = DESCRIPTOR;
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
              data.enforceInterface(descriptor);
            }
            if (code == INTERFACE_TRANSACTION) {
              reply.writeString(descriptor);
              return true;
            }
            switch (code)
            {
              case TRANSACTION_foo:
              {
                this.foo();
                reply.writeNoException();
                break;
              }
              default:
              {
                return super.onTransact(code, data, reply, flags);
              }
            }
            return true;
          }
          private static class Proxy implements android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface
          {
            private android.os.IBinder mRemote;
            Proxy(android.os.IBinder remote)
            {
              mRemote = remote;
            }
            @Override public android.os.IBinder asBinder()
            {
              return mRemote;
            }
            public java.lang.String getInterfaceDescriptor()
            {
              return DESCRIPTOR;
            }
            @Override public void foo() throws android.os.RemoteException
            {
              android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
              android.os.Parcel _reply = android.os.Parcel.obtain();
              try {
                _data.writeInterfaceToken(DESCRIPTOR);
                boolean _status = mRemote.transact(Stub.TRANSACTION_foo, _data, _reply, 0);
                _reply.readException();
              }
              finally {
                _reply.recycle();
                _data.recycle();
              }
            }
          }
          static final int TRANSACTION_foo = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
          /** @hide */
          public int getMaxTransactionId()
          {
            return 0;
          }
        }
        /** @hide */
        public static final java.lang.String DESCRIPTOR = "android.aidl.tests.ITestService.CompilerChecks.NoPrefixInterface.NestedNoPrefixInterface";
        public void foo() throws android.os.RemoteException;
      }
    }
  }
}
