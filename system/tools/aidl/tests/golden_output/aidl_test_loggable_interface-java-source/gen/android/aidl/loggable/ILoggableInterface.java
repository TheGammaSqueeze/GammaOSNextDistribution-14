/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-java-source/gen/android/aidl/loggable/ILoggableInterface.java.d -o out/soong/.intermediates/system/tools/aidl/aidl_test_loggable_interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/loggable/ILoggableInterface.aidl
 */
package android.aidl.loggable;
public interface ILoggableInterface extends android.os.IInterface
{
  /** Default implementation for ILoggableInterface. */
  public static class Default implements android.aidl.loggable.ILoggableInterface
  {
    @Override public java.lang.String[] LogThis(boolean boolValue, boolean[] boolArray, byte byteValue, byte[] byteArray, char charValue, char[] charArray, int intValue, int[] intArray, long longValue, long[] longArray, float floatValue, float[] floatArray, double doubleValue, double[] doubleArray, java.lang.String stringValue, java.lang.String[] stringArray, java.util.List<java.lang.String> listValue, android.aidl.loggable.Data dataValue, android.os.IBinder binderValue, android.os.ParcelFileDescriptor pfdValue, android.os.ParcelFileDescriptor[] pfdArray) throws android.os.RemoteException
    {
      return null;
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.loggable.ILoggableInterface
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.aidl.loggable.ILoggableInterface interface,
     * generating a proxy if needed.
     */
    public static android.aidl.loggable.ILoggableInterface asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.loggable.ILoggableInterface))) {
        return ((android.aidl.loggable.ILoggableInterface)iin);
      }
      return new android.aidl.loggable.ILoggableInterface.Stub.Proxy(obj);
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
        case TRANSACTION_LogThis:
        {
          return "LogThis";
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
        case TRANSACTION_LogThis:
        {
          boolean _arg0;
          _arg0 = data.readBoolean();
          boolean[] _arg1;
          _arg1 = data.createBooleanArray();
          byte _arg2;
          _arg2 = data.readByte();
          byte[] _arg3;
          _arg3 = data.createByteArray();
          char _arg4;
          _arg4 = (char)data.readInt();
          char[] _arg5;
          _arg5 = data.createCharArray();
          int _arg6;
          _arg6 = data.readInt();
          int[] _arg7;
          _arg7 = data.createIntArray();
          long _arg8;
          _arg8 = data.readLong();
          long[] _arg9;
          _arg9 = data.createLongArray();
          float _arg10;
          _arg10 = data.readFloat();
          float[] _arg11;
          _arg11 = data.createFloatArray();
          double _arg12;
          _arg12 = data.readDouble();
          double[] _arg13;
          _arg13 = data.createDoubleArray();
          java.lang.String _arg14;
          _arg14 = data.readString();
          java.lang.String[] _arg15;
          _arg15 = data.createStringArray();
          java.util.List<java.lang.String> _arg16;
          _arg16 = data.createStringArrayList();
          android.aidl.loggable.Data _arg17;
          _arg17 = data.readTypedObject(android.aidl.loggable.Data.CREATOR);
          android.os.IBinder _arg18;
          _arg18 = data.readStrongBinder();
          android.os.ParcelFileDescriptor _arg19;
          _arg19 = data.readTypedObject(android.os.ParcelFileDescriptor.CREATOR);
          android.os.ParcelFileDescriptor[] _arg20;
          _arg20 = data.createTypedArray(android.os.ParcelFileDescriptor.CREATOR);
          data.enforceNoDataAvail();
          java.lang.String[] _result = this.LogThis(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5, _arg6, _arg7, _arg8, _arg9, _arg10, _arg11, _arg12, _arg13, _arg14, _arg15, _arg16, _arg17, _arg18, _arg19, _arg20);
          reply.writeNoException();
          reply.writeStringArray(_result);
          reply.writeBooleanArray(_arg1);
          reply.writeByteArray(_arg3);
          reply.writeCharArray(_arg5);
          reply.writeIntArray(_arg7);
          reply.writeLongArray(_arg9);
          reply.writeFloatArray(_arg11);
          reply.writeDoubleArray(_arg13);
          reply.writeStringArray(_arg15);
          reply.writeStringList(_arg16);
          reply.writeTypedObject(_arg19, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          reply.writeTypedArray(_arg20, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements android.aidl.loggable.ILoggableInterface
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
      @Override public java.lang.String[] LogThis(boolean boolValue, boolean[] boolArray, byte byteValue, byte[] byteArray, char charValue, char[] charArray, int intValue, int[] intArray, long longValue, long[] longArray, float floatValue, float[] floatArray, double doubleValue, double[] doubleArray, java.lang.String stringValue, java.lang.String[] stringArray, java.util.List<java.lang.String> listValue, android.aidl.loggable.Data dataValue, android.os.IBinder binderValue, android.os.ParcelFileDescriptor pfdValue, android.os.ParcelFileDescriptor[] pfdArray) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeBoolean(boolValue);
          _data.writeBooleanArray(boolArray);
          _data.writeByte(byteValue);
          _data.writeByteArray(byteArray);
          _data.writeInt(((int)charValue));
          _data.writeCharArray(charArray);
          _data.writeInt(intValue);
          _data.writeIntArray(intArray);
          _data.writeLong(longValue);
          _data.writeLongArray(longArray);
          _data.writeFloat(floatValue);
          _data.writeFloatArray(floatArray);
          _data.writeDouble(doubleValue);
          _data.writeDoubleArray(doubleArray);
          _data.writeString(stringValue);
          _data.writeStringArray(stringArray);
          _data.writeStringList(listValue);
          _data.writeTypedObject(dataValue, 0);
          _data.writeStrongBinder(binderValue);
          _data.writeTypedObject(pfdValue, 0);
          _data.writeTypedArray(pfdArray, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_LogThis, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createStringArray();
          _reply.readBooleanArray(boolArray);
          _reply.readByteArray(byteArray);
          _reply.readCharArray(charArray);
          _reply.readIntArray(intArray);
          _reply.readLongArray(longArray);
          _reply.readFloatArray(floatArray);
          _reply.readDoubleArray(doubleArray);
          _reply.readStringArray(stringArray);
          _reply.readStringList(listValue);
          if ((0!=_reply.readInt())) {
            pfdValue = android.os.ParcelFileDescriptor.CREATOR.createFromParcel(_reply);
          }
          _reply.readTypedArray(pfdArray, android.os.ParcelFileDescriptor.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
    }
    static final int TRANSACTION_LogThis = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    /** @hide */
    public int getMaxTransactionId()
    {
      return 0;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.aidl.loggable.ILoggableInterface";
  public java.lang.String[] LogThis(boolean boolValue, boolean[] boolArray, byte byteValue, byte[] byteArray, char charValue, char[] charArray, int intValue, int[] intArray, long longValue, long[] longArray, float floatValue, float[] floatArray, double doubleValue, double[] doubleArray, java.lang.String stringValue, java.lang.String[] stringArray, java.util.List<java.lang.String> listValue, android.aidl.loggable.Data dataValue, android.os.IBinder binderValue, android.os.ParcelFileDescriptor pfdValue, android.os.ParcelFileDescriptor[] pfdArray) throws android.os.RemoteException;
  public interface ISub extends android.os.IInterface
  {
    /** Default implementation for ISub. */
    public static class Default implements android.aidl.loggable.ILoggableInterface.ISub
    {
      @Override public void Log(int value) throws android.os.RemoteException
      {
      }
      @Override
      public android.os.IBinder asBinder() {
        return null;
      }
    }
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends android.os.Binder implements android.aidl.loggable.ILoggableInterface.ISub
    {
      /** Construct the stub at attach it to the interface. */
      @SuppressWarnings("this-escape")
      public Stub()
      {
        this.attachInterface(this, DESCRIPTOR);
      }
      /**
       * Cast an IBinder object into an android.aidl.loggable.ILoggableInterface.ISub interface,
       * generating a proxy if needed.
       */
      public static android.aidl.loggable.ILoggableInterface.ISub asInterface(android.os.IBinder obj)
      {
        if ((obj==null)) {
          return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (((iin!=null)&&(iin instanceof android.aidl.loggable.ILoggableInterface.ISub))) {
          return ((android.aidl.loggable.ILoggableInterface.ISub)iin);
        }
        return new android.aidl.loggable.ILoggableInterface.ISub.Stub.Proxy(obj);
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
          case TRANSACTION_Log:
          {
            return "Log";
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
          case TRANSACTION_Log:
          {
            int _arg0;
            _arg0 = data.readInt();
            data.enforceNoDataAvail();
            this.Log(_arg0);
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
      private static class Proxy implements android.aidl.loggable.ILoggableInterface.ISub
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
        @Override public void Log(int value) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeInt(value);
            boolean _status = mRemote.transact(Stub.TRANSACTION_Log, _data, _reply, 0);
            _reply.readException();
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
        }
      }
      static final int TRANSACTION_Log = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
      /** @hide */
      public int getMaxTransactionId()
      {
        return 0;
      }
    }
    /** @hide */
    public static final java.lang.String DESCRIPTOR = "android.aidl.loggable.ILoggableInterface.ISub";
    public void Log(int value) throws android.os.RemoteException;
  }
}
