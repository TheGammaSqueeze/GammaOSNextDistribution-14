/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/nested/INestedService.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/nested/INestedService.aidl
 */
package android.aidl.tests.nested;
public interface INestedService extends android.os.IInterface
{
  /** Default implementation for INestedService. */
  public static class Default implements android.aidl.tests.nested.INestedService
  {
    @Override public android.aidl.tests.nested.INestedService.Result flipStatus(android.aidl.tests.nested.ParcelableWithNested p) throws android.os.RemoteException
    {
      return null;
    }
    @Override public void flipStatusWithCallback(byte status, android.aidl.tests.nested.INestedService.ICallback cb) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements android.aidl.tests.nested.INestedService
  {
    /** Construct the stub at attach it to the interface. */
    @SuppressWarnings("this-escape")
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an android.aidl.tests.nested.INestedService interface,
     * generating a proxy if needed.
     */
    public static android.aidl.tests.nested.INestedService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof android.aidl.tests.nested.INestedService))) {
        return ((android.aidl.tests.nested.INestedService)iin);
      }
      return new android.aidl.tests.nested.INestedService.Stub.Proxy(obj);
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
        case TRANSACTION_flipStatus:
        {
          return "flipStatus";
        }
        case TRANSACTION_flipStatusWithCallback:
        {
          return "flipStatusWithCallback";
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
        case TRANSACTION_flipStatus:
        {
          android.aidl.tests.nested.ParcelableWithNested _arg0;
          _arg0 = data.readTypedObject(android.aidl.tests.nested.ParcelableWithNested.CREATOR);
          data.enforceNoDataAvail();
          android.aidl.tests.nested.INestedService.Result _result = this.flipStatus(_arg0);
          reply.writeNoException();
          reply.writeTypedObject(_result, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
          break;
        }
        case TRANSACTION_flipStatusWithCallback:
        {
          byte _arg0;
          _arg0 = data.readByte();
          android.aidl.tests.nested.INestedService.ICallback _arg1;
          _arg1 = android.aidl.tests.nested.INestedService.ICallback.Stub.asInterface(data.readStrongBinder());
          data.enforceNoDataAvail();
          this.flipStatusWithCallback(_arg0, _arg1);
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
    private static class Proxy implements android.aidl.tests.nested.INestedService
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
      @Override public android.aidl.tests.nested.INestedService.Result flipStatus(android.aidl.tests.nested.ParcelableWithNested p) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        android.aidl.tests.nested.INestedService.Result _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeTypedObject(p, 0);
          boolean _status = mRemote.transact(Stub.TRANSACTION_flipStatus, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readTypedObject(android.aidl.tests.nested.INestedService.Result.CREATOR);
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      @Override public void flipStatusWithCallback(byte status, android.aidl.tests.nested.INestedService.ICallback cb) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeByte(status);
          _data.writeStrongInterface(cb);
          boolean _status = mRemote.transact(Stub.TRANSACTION_flipStatusWithCallback, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_flipStatus = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    static final int TRANSACTION_flipStatusWithCallback = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    /** @hide */
    public int getMaxTransactionId()
    {
      return 1;
    }
  }
  /** @hide */
  public static final java.lang.String DESCRIPTOR = "android.aidl.tests.nested.INestedService";
  public android.aidl.tests.nested.INestedService.Result flipStatus(android.aidl.tests.nested.ParcelableWithNested p) throws android.os.RemoteException;
  public void flipStatusWithCallback(byte status, android.aidl.tests.nested.INestedService.ICallback cb) throws android.os.RemoteException;
  public static class Result implements android.os.Parcelable
  {
    public byte status = android.aidl.tests.nested.ParcelableWithNested.Status.OK;
    public static final android.os.Parcelable.Creator<Result> CREATOR = new android.os.Parcelable.Creator<Result>() {
      @Override
      public Result createFromParcel(android.os.Parcel _aidl_source) {
        Result _aidl_out = new Result();
        _aidl_out.readFromParcel(_aidl_source);
        return _aidl_out;
      }
      @Override
      public Result[] newArray(int _aidl_size) {
        return new Result[_aidl_size];
      }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.writeInt(0);
      _aidl_parcel.writeByte(status);
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
        status = _aidl_parcel.readByte();
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
  public interface ICallback extends android.os.IInterface
  {
    /** Default implementation for ICallback. */
    public static class Default implements android.aidl.tests.nested.INestedService.ICallback
    {
      @Override public void done(byte status) throws android.os.RemoteException
      {
      }
      @Override
      public android.os.IBinder asBinder() {
        return null;
      }
    }
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends android.os.Binder implements android.aidl.tests.nested.INestedService.ICallback
    {
      /** Construct the stub at attach it to the interface. */
      @SuppressWarnings("this-escape")
      public Stub()
      {
        this.attachInterface(this, DESCRIPTOR);
      }
      /**
       * Cast an IBinder object into an android.aidl.tests.nested.INestedService.ICallback interface,
       * generating a proxy if needed.
       */
      public static android.aidl.tests.nested.INestedService.ICallback asInterface(android.os.IBinder obj)
      {
        if ((obj==null)) {
          return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (((iin!=null)&&(iin instanceof android.aidl.tests.nested.INestedService.ICallback))) {
          return ((android.aidl.tests.nested.INestedService.ICallback)iin);
        }
        return new android.aidl.tests.nested.INestedService.ICallback.Stub.Proxy(obj);
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
          case TRANSACTION_done:
          {
            return "done";
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
          case TRANSACTION_done:
          {
            byte _arg0;
            _arg0 = data.readByte();
            data.enforceNoDataAvail();
            this.done(_arg0);
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
      private static class Proxy implements android.aidl.tests.nested.INestedService.ICallback
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
        @Override public void done(byte status) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeByte(status);
            boolean _status = mRemote.transact(Stub.TRANSACTION_done, _data, _reply, 0);
            _reply.readException();
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
        }
      }
      static final int TRANSACTION_done = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
      /** @hide */
      public int getMaxTransactionId()
      {
        return 0;
      }
    }
    /** @hide */
    public static final java.lang.String DESCRIPTOR = "android.aidl.tests.nested.INestedService.ICallback";
    public void done(byte status) throws android.os.RemoteException;
  }
}
