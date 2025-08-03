/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen/android/aidl/tests/map/IMapTest.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-extras-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/map/IMapTest.aidl
 */
package android.aidl.tests.map;
public interface IMapTest extends android.os.IInterface
{
  /** Default implementation for IMapTest. */
  public static class Default implements android.aidl.tests.map.IMapTest
  {
    @Override public java.util.Map<java.lang.String,int[]> repeatIntEnumArrayMap(java.util.Map<java.lang.String,int[]> input, java.util.Map<java.lang.String,int[]> output) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.Map<java.lang.String,int[]> repeatIntArrayMap(java.util.Map<java.lang.String,int[]> input, java.util.Map<java.lang.String,int[]> output) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.Map<java.lang.String,android.aidl.tests.map.Bar> repeatBarMap(java.util.Map<java.lang.String,android.aidl.tests.map.Bar> input, java.util.Map<java.lang.String,android.aidl.tests.map.Bar> output) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> repeatBarArrayMap(java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> input, java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> output) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.Map<java.lang.String,java.lang.String> repeatStringMap(java.util.Map<java.lang.String,java.lang.String> input, java.util.Map<java.lang.String,java.lang.String> output) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.Map<java.lang.String,java.lang.String[]> repeatStringArrayMap(java.util.Map<java.lang.String,java.lang.String[]> input, java.util.Map<java.lang.String,java.lang.String[]> output) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> repeatInterfaceMap(java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> input, java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> output) throws android.os.RemoteException
    {
      return null;
    }
    @Override public java.util.Map<java.lang.String,android.os.IBinder> repeatIbinderMap(java.util.Map<java.lang.String,android.os.IBinder> input, java.util.Map<java.lang.String,android.os.IBinder> output) throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.tests.map.IMapTest
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.aidl.tests.map.IMapTest interface,
     * generating a proxy if needed.
     */
    public static android.aidl.tests.map.IMapTest asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.tests.map.IMapTest))) {
        return ((android.aidl.tests.map.IMapTest)iin);
      }
      return new android.aidl.tests.map.IMapTest.Stub.Proxy(obj);
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
        case TRANSACTION_repeatIntEnumArrayMap:
        {
          return "repeatIntEnumArrayMap";
        }
        case TRANSACTION_repeatIntArrayMap:
        {
          return "repeatIntArrayMap";
        }
        case TRANSACTION_repeatBarMap:
        {
          return "repeatBarMap";
        }
        case TRANSACTION_repeatBarArrayMap:
        {
          return "repeatBarArrayMap";
        }
        case TRANSACTION_repeatStringMap:
        {
          return "repeatStringMap";
        }
        case TRANSACTION_repeatStringArrayMap:
        {
          return "repeatStringArrayMap";
        }
        case TRANSACTION_repeatInterfaceMap:
        {
          return "repeatInterfaceMap";
        }
        case TRANSACTION_repeatIbinderMap:
        {
          return "repeatIbinderMap";
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
        case TRANSACTION_repeatIntEnumArrayMap:
        {
          java.util.Map<java.lang.String,int[]> _arg0;
          {
            int N = data.readInt();
            _arg0 = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = data.readString();
              int[] v;
              v = data.createIntArray();
              _arg0.put(k, v);
            });
          }
          java.util.Map<java.lang.String,int[]> _arg1;
          _arg1 = new java.util.HashMap<java.lang.String,int[]>();
          data.enforceNoDataAvail();
          java.util.Map<java.lang.String,int[]> _result = this.repeatIntEnumArrayMap(_arg0, _arg1);
          reply.writeNoException();
          if (_result == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_result.size());
            _result.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeIntArray(v);
            });
          }
          if (_arg1 == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_arg1.size());
            _arg1.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeIntArray(v);
            });
          }
          break;
        }
        case TRANSACTION_repeatIntArrayMap:
        {
          java.util.Map<java.lang.String,int[]> _arg0;
          {
            int N = data.readInt();
            _arg0 = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = data.readString();
              int[] v;
              v = data.createIntArray();
              _arg0.put(k, v);
            });
          }
          java.util.Map<java.lang.String,int[]> _arg1;
          _arg1 = new java.util.HashMap<java.lang.String,int[]>();
          data.enforceNoDataAvail();
          java.util.Map<java.lang.String,int[]> _result = this.repeatIntArrayMap(_arg0, _arg1);
          reply.writeNoException();
          if (_result == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_result.size());
            _result.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeIntArray(v);
            });
          }
          if (_arg1 == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_arg1.size());
            _arg1.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeIntArray(v);
            });
          }
          break;
        }
        case TRANSACTION_repeatBarMap:
        {
          java.util.Map<java.lang.String,android.aidl.tests.map.Bar> _arg0;
          {
            int N = data.readInt();
            _arg0 = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = data.readString();
              android.aidl.tests.map.Bar v;
              v = data.readTypedObject(android.aidl.tests.map.Bar.CREATOR);
              _arg0.put(k, v);
            });
          }
          java.util.Map<java.lang.String,android.aidl.tests.map.Bar> _arg1;
          _arg1 = new java.util.HashMap<java.lang.String,android.aidl.tests.map.Bar>();
          data.enforceNoDataAvail();
          java.util.Map<java.lang.String,android.aidl.tests.map.Bar> _result = this.repeatBarMap(_arg0, _arg1);
          reply.writeNoException();
          if (_result == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_result.size());
            _result.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeTypedObject(v, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            });
          }
          if (_arg1 == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_arg1.size());
            _arg1.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeTypedObject(v, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            });
          }
          break;
        }
        case TRANSACTION_repeatBarArrayMap:
        {
          java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> _arg0;
          {
            int N = data.readInt();
            _arg0 = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = data.readString();
              android.aidl.tests.map.Bar[] v;
              v = data.createTypedArray(android.aidl.tests.map.Bar.CREATOR);
              _arg0.put(k, v);
            });
          }
          java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> _arg1;
          _arg1 = new java.util.HashMap<java.lang.String,android.aidl.tests.map.Bar[]>();
          data.enforceNoDataAvail();
          java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> _result = this.repeatBarArrayMap(_arg0, _arg1);
          reply.writeNoException();
          if (_result == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_result.size());
            _result.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeTypedArray(v, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            });
          }
          if (_arg1 == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_arg1.size());
            _arg1.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeTypedArray(v, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
            });
          }
          break;
        }
        case TRANSACTION_repeatStringMap:
        {
          java.util.Map<java.lang.String,java.lang.String> _arg0;
          {
            int N = data.readInt();
            _arg0 = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = data.readString();
              java.lang.String v;
              v = data.readString();
              _arg0.put(k, v);
            });
          }
          java.util.Map<java.lang.String,java.lang.String> _arg1;
          _arg1 = new java.util.HashMap<java.lang.String,java.lang.String>();
          data.enforceNoDataAvail();
          java.util.Map<java.lang.String,java.lang.String> _result = this.repeatStringMap(_arg0, _arg1);
          reply.writeNoException();
          if (_result == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_result.size());
            _result.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeString(v);
            });
          }
          if (_arg1 == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_arg1.size());
            _arg1.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeString(v);
            });
          }
          break;
        }
        case TRANSACTION_repeatStringArrayMap:
        {
          java.util.Map<java.lang.String,java.lang.String[]> _arg0;
          {
            int N = data.readInt();
            _arg0 = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = data.readString();
              java.lang.String[] v;
              v = data.createStringArray();
              _arg0.put(k, v);
            });
          }
          java.util.Map<java.lang.String,java.lang.String[]> _arg1;
          _arg1 = new java.util.HashMap<java.lang.String,java.lang.String[]>();
          data.enforceNoDataAvail();
          java.util.Map<java.lang.String,java.lang.String[]> _result = this.repeatStringArrayMap(_arg0, _arg1);
          reply.writeNoException();
          if (_result == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_result.size());
            _result.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeStringArray(v);
            });
          }
          if (_arg1 == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_arg1.size());
            _arg1.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeStringArray(v);
            });
          }
          break;
        }
        case TRANSACTION_repeatInterfaceMap:
        {
          java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> _arg0;
          {
            int N = data.readInt();
            _arg0 = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = data.readString();
              android.aidl.tests.map.IEmpty v;
              v = android.aidl.tests.map.IEmpty.Stub.asInterface(data.readStrongBinder());
              _arg0.put(k, v);
            });
          }
          java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> _arg1;
          _arg1 = new java.util.HashMap<java.lang.String,android.aidl.tests.map.IEmpty>();
          data.enforceNoDataAvail();
          java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> _result = this.repeatInterfaceMap(_arg0, _arg1);
          reply.writeNoException();
          if (_result == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_result.size());
            _result.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeStrongInterface(v);
            });
          }
          if (_arg1 == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_arg1.size());
            _arg1.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeStrongInterface(v);
            });
          }
          break;
        }
        case TRANSACTION_repeatIbinderMap:
        {
          java.util.Map<java.lang.String,android.os.IBinder> _arg0;
          {
            int N = data.readInt();
            _arg0 = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = data.readString();
              android.os.IBinder v;
              v = data.readStrongBinder();
              _arg0.put(k, v);
            });
          }
          java.util.Map<java.lang.String,android.os.IBinder> _arg1;
          _arg1 = new java.util.HashMap<java.lang.String,android.os.IBinder>();
          data.enforceNoDataAvail();
          java.util.Map<java.lang.String,android.os.IBinder> _result = this.repeatIbinderMap(_arg0, _arg1);
          reply.writeNoException();
          if (_result == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_result.size());
            _result.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeStrongBinder(v);
            });
          }
          if (_arg1 == null) {
            reply.writeInt(-1);
          } else {
            reply.writeInt(_arg1.size());
            _arg1.forEach((k, v) -> {
              reply.writeString(k);
              reply.writeStrongBinder(v);
            });
          }
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements android.aidl.tests.map.IMapTest
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
      @Override public java.util.Map<java.lang.String,int[]> repeatIntEnumArrayMap(java.util.Map<java.lang.String,int[]> input, java.util.Map<java.lang.String,int[]> output) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.Map<java.lang.String,int[]> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if (input == null) {
            _data.writeInt(-1);
          } else {
            _data.writeInt(input.size());
            input.forEach((k, v) -> {
              _data.writeString(k);
              _data.writeIntArray(v);
            });
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatIntEnumArrayMap, _data, _reply, 0);
          _reply.readException();
          {
            int N = _reply.readInt();
            _result = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = _reply.readString();
              int[] v;
              v = _reply.createIntArray();
              _result.put(k, v);
            });
          }
          if (output != null) output.clear();
          java.util.stream.IntStream.range(0, _reply.readInt()).forEach(i -> {
            String k = _reply.readString();
            int[] v;
            v = _reply.createIntArray();
            output.put(k, v);
          });
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.Map<java.lang.String,int[]> repeatIntArrayMap(java.util.Map<java.lang.String,int[]> input, java.util.Map<java.lang.String,int[]> output) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.Map<java.lang.String,int[]> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if (input == null) {
            _data.writeInt(-1);
          } else {
            _data.writeInt(input.size());
            input.forEach((k, v) -> {
              _data.writeString(k);
              _data.writeIntArray(v);
            });
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatIntArrayMap, _data, _reply, 0);
          _reply.readException();
          {
            int N = _reply.readInt();
            _result = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = _reply.readString();
              int[] v;
              v = _reply.createIntArray();
              _result.put(k, v);
            });
          }
          if (output != null) output.clear();
          java.util.stream.IntStream.range(0, _reply.readInt()).forEach(i -> {
            String k = _reply.readString();
            int[] v;
            v = _reply.createIntArray();
            output.put(k, v);
          });
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.Map<java.lang.String,android.aidl.tests.map.Bar> repeatBarMap(java.util.Map<java.lang.String,android.aidl.tests.map.Bar> input, java.util.Map<java.lang.String,android.aidl.tests.map.Bar> output) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.Map<java.lang.String,android.aidl.tests.map.Bar> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if (input == null) {
            _data.writeInt(-1);
          } else {
            _data.writeInt(input.size());
            input.forEach((k, v) -> {
              _data.writeString(k);
              _data.writeTypedObject(v, 0);
            });
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatBarMap, _data, _reply, 0);
          _reply.readException();
          {
            int N = _reply.readInt();
            _result = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = _reply.readString();
              android.aidl.tests.map.Bar v;
              v = _reply.readTypedObject(android.aidl.tests.map.Bar.CREATOR);
              _result.put(k, v);
            });
          }
          if (output != null) output.clear();
          java.util.stream.IntStream.range(0, _reply.readInt()).forEach(i -> {
            String k = _reply.readString();
            android.aidl.tests.map.Bar v;
            v = _reply.readTypedObject(android.aidl.tests.map.Bar.CREATOR);
            output.put(k, v);
          });
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> repeatBarArrayMap(java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> input, java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> output) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if (input == null) {
            _data.writeInt(-1);
          } else {
            _data.writeInt(input.size());
            input.forEach((k, v) -> {
              _data.writeString(k);
              _data.writeTypedArray(v, 0);
            });
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatBarArrayMap, _data, _reply, 0);
          _reply.readException();
          {
            int N = _reply.readInt();
            _result = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = _reply.readString();
              android.aidl.tests.map.Bar[] v;
              v = _reply.createTypedArray(android.aidl.tests.map.Bar.CREATOR);
              _result.put(k, v);
            });
          }
          if (output != null) output.clear();
          java.util.stream.IntStream.range(0, _reply.readInt()).forEach(i -> {
            String k = _reply.readString();
            android.aidl.tests.map.Bar[] v;
            v = _reply.createTypedArray(android.aidl.tests.map.Bar.CREATOR);
            output.put(k, v);
          });
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.Map<java.lang.String,java.lang.String> repeatStringMap(java.util.Map<java.lang.String,java.lang.String> input, java.util.Map<java.lang.String,java.lang.String> output) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.Map<java.lang.String,java.lang.String> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if (input == null) {
            _data.writeInt(-1);
          } else {
            _data.writeInt(input.size());
            input.forEach((k, v) -> {
              _data.writeString(k);
              _data.writeString(v);
            });
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatStringMap, _data, _reply, 0);
          _reply.readException();
          {
            int N = _reply.readInt();
            _result = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = _reply.readString();
              java.lang.String v;
              v = _reply.readString();
              _result.put(k, v);
            });
          }
          if (output != null) output.clear();
          java.util.stream.IntStream.range(0, _reply.readInt()).forEach(i -> {
            String k = _reply.readString();
            java.lang.String v;
            v = _reply.readString();
            output.put(k, v);
          });
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.Map<java.lang.String,java.lang.String[]> repeatStringArrayMap(java.util.Map<java.lang.String,java.lang.String[]> input, java.util.Map<java.lang.String,java.lang.String[]> output) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.Map<java.lang.String,java.lang.String[]> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if (input == null) {
            _data.writeInt(-1);
          } else {
            _data.writeInt(input.size());
            input.forEach((k, v) -> {
              _data.writeString(k);
              _data.writeStringArray(v);
            });
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatStringArrayMap, _data, _reply, 0);
          _reply.readException();
          {
            int N = _reply.readInt();
            _result = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = _reply.readString();
              java.lang.String[] v;
              v = _reply.createStringArray();
              _result.put(k, v);
            });
          }
          if (output != null) output.clear();
          java.util.stream.IntStream.range(0, _reply.readInt()).forEach(i -> {
            String k = _reply.readString();
            java.lang.String[] v;
            v = _reply.createStringArray();
            output.put(k, v);
          });
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> repeatInterfaceMap(java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> input, java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> output) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if (input == null) {
            _data.writeInt(-1);
          } else {
            _data.writeInt(input.size());
            input.forEach((k, v) -> {
              _data.writeString(k);
              _data.writeStrongInterface(v);
            });
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatInterfaceMap, _data, _reply, 0);
          _reply.readException();
          {
            int N = _reply.readInt();
            _result = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = _reply.readString();
              android.aidl.tests.map.IEmpty v;
              v = android.aidl.tests.map.IEmpty.Stub.asInterface(_reply.readStrongBinder());
              _result.put(k, v);
            });
          }
          if (output != null) output.clear();
          java.util.stream.IntStream.range(0, _reply.readInt()).forEach(i -> {
            String k = _reply.readString();
            android.aidl.tests.map.IEmpty v;
            v = android.aidl.tests.map.IEmpty.Stub.asInterface(_reply.readStrongBinder());
            output.put(k, v);
          });
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public java.util.Map<java.lang.String,android.os.IBinder> repeatIbinderMap(java.util.Map<java.lang.String,android.os.IBinder> input, java.util.Map<java.lang.String,android.os.IBinder> output) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.util.Map<java.lang.String,android.os.IBinder> _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          if (input == null) {
            _data.writeInt(-1);
          } else {
            _data.writeInt(input.size());
            input.forEach((k, v) -> {
              _data.writeString(k);
              _data.writeStrongBinder(v);
            });
          }
          boolean _status = mRemote.transact(Stub.TRANSACTION_repeatIbinderMap, _data, _reply, 0);
          _reply.readException();
          {
            int N = _reply.readInt();
            _result = N < 0 ? null : new java.util.HashMap<>();
            java.util.stream.IntStream.range(0, N).forEach(i -> {
              String k = _reply.readString();
              android.os.IBinder v;
              v = _reply.readStrongBinder();
              _result.put(k, v);
            });
          }
          if (output != null) output.clear();
          java.util.stream.IntStream.range(0, _reply.readInt()).forEach(i -> {
            String k = _reply.readString();
            android.os.IBinder v;
            v = _reply.readStrongBinder();
            output.put(k, v);
          });
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_repeatIntEnumArrayMap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_repeatIntArrayMap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_repeatBarMap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_repeatBarArrayMap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
    static final int TRANSACTION_repeatStringMap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
    static final int TRANSACTION_repeatStringArrayMap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 5);
    static final int TRANSACTION_repeatInterfaceMap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 6);
    static final int TRANSACTION_repeatIbinderMap = (android.os.IBinder.FIRST_CALL_TRANSACTION + 7);
    /** @hide */
    public int getMaxTransactionId()
    {
      return 7;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.aidl.tests.map.IMapTest";
  public java.util.Map<java.lang.String,int[]> repeatIntEnumArrayMap(java.util.Map<java.lang.String,int[]> input, java.util.Map<java.lang.String,int[]> output) throws android.os.RemoteException;
  public java.util.Map<java.lang.String,int[]> repeatIntArrayMap(java.util.Map<java.lang.String,int[]> input, java.util.Map<java.lang.String,int[]> output) throws android.os.RemoteException;
  public java.util.Map<java.lang.String,android.aidl.tests.map.Bar> repeatBarMap(java.util.Map<java.lang.String,android.aidl.tests.map.Bar> input, java.util.Map<java.lang.String,android.aidl.tests.map.Bar> output) throws android.os.RemoteException;
  public java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> repeatBarArrayMap(java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> input, java.util.Map<java.lang.String,android.aidl.tests.map.Bar[]> output) throws android.os.RemoteException;
  public java.util.Map<java.lang.String,java.lang.String> repeatStringMap(java.util.Map<java.lang.String,java.lang.String> input, java.util.Map<java.lang.String,java.lang.String> output) throws android.os.RemoteException;
  public java.util.Map<java.lang.String,java.lang.String[]> repeatStringArrayMap(java.util.Map<java.lang.String,java.lang.String[]> input, java.util.Map<java.lang.String,java.lang.String[]> output) throws android.os.RemoteException;
  public java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> repeatInterfaceMap(java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> input, java.util.Map<java.lang.String,android.aidl.tests.map.IEmpty> output) throws android.os.RemoteException;
  public java.util.Map<java.lang.String,android.os.IBinder> repeatIbinderMap(java.util.Map<java.lang.String,android.os.IBinder> input, java.util.Map<java.lang.String,android.os.IBinder> output) throws android.os.RemoteException;
}
