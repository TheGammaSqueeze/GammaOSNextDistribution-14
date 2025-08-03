/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Using: out/host/linux-x86/bin/aidl --lang=java -Weverything -Wno-missing-permission-annotation -Werror -t --min_sdk_version platform_apis --ninja -d out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen/android/aidl/tests/ListOfInterfaces.java.d -o out/soong/.intermediates/system/tools/aidl/aidl-test-interface-java-source/gen -Nsystem/tools/aidl/tests system/tools/aidl/tests/android/aidl/tests/ListOfInterfaces.aidl
 */
package android.aidl.tests;
public class ListOfInterfaces implements android.os.Parcelable
{
  public static final android.os.Parcelable.Creator<ListOfInterfaces> CREATOR = new android.os.Parcelable.Creator<ListOfInterfaces>() {
    @Override
    public ListOfInterfaces createFromParcel(android.os.Parcel _aidl_source) {
      ListOfInterfaces _aidl_out = new ListOfInterfaces();
      _aidl_out.readFromParcel(_aidl_source);
      return _aidl_out;
    }
    @Override
    public ListOfInterfaces[] newArray(int _aidl_size) {
      return new ListOfInterfaces[_aidl_size];
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
  public interface IEmptyInterface extends android.os.IInterface
  {
    /** Default implementation for IEmptyInterface. */
    public static class Default implements android.aidl.tests.ListOfInterfaces.IEmptyInterface
    {
      @Override
      public android.os.IBinder asBinder() {
        return null;
      }
    }
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends android.os.Binder implements android.aidl.tests.ListOfInterfaces.IEmptyInterface
    {
      /** Construct the stub at attach it to the interface. */
      @SuppressWarnings("this-escape")
      public Stub()
      {
        this.attachInterface(this, DESCRIPTOR);
      }
      /**
       * Cast an IBinder object into an android.aidl.tests.ListOfInterfaces.IEmptyInterface interface,
       * generating a proxy if needed.
       */
      public static android.aidl.tests.ListOfInterfaces.IEmptyInterface asInterface(android.os.IBinder obj)
      {
        if ((obj==null)) {
          return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (((iin!=null)&&(iin instanceof android.aidl.tests.ListOfInterfaces.IEmptyInterface))) {
          return ((android.aidl.tests.ListOfInterfaces.IEmptyInterface)iin);
        }
        return new android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub.Proxy(obj);
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
      private static class Proxy implements android.aidl.tests.ListOfInterfaces.IEmptyInterface
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
    public static final java.lang.String DESCRIPTOR = "android.aidl.tests.ListOfInterfaces.IEmptyInterface";
  }
  public interface IMyInterface extends android.os.IInterface
  {
    /** Default implementation for IMyInterface. */
    public static class Default implements android.aidl.tests.ListOfInterfaces.IMyInterface
    {
      @Override public java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> methodWithInterfaces(android.aidl.tests.ListOfInterfaces.IEmptyInterface iface, android.aidl.tests.ListOfInterfaces.IEmptyInterface nullable_iface, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list_in, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list_out, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list_inout, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list_in, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list_out, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list_inout) throws android.os.RemoteException
      {
        return null;
      }
      @Override
      public android.os.IBinder asBinder() {
        return null;
      }
    }
    /** Local-side IPC implementation stub class. */
    public static abstract class Stub extends android.os.Binder implements android.aidl.tests.ListOfInterfaces.IMyInterface
    {
      /** Construct the stub at attach it to the interface. */
      @SuppressWarnings("this-escape")
      public Stub()
      {
        this.attachInterface(this, DESCRIPTOR);
      }
      /**
       * Cast an IBinder object into an android.aidl.tests.ListOfInterfaces.IMyInterface interface,
       * generating a proxy if needed.
       */
      public static android.aidl.tests.ListOfInterfaces.IMyInterface asInterface(android.os.IBinder obj)
      {
        if ((obj==null)) {
          return null;
        }
        android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
        if (((iin!=null)&&(iin instanceof android.aidl.tests.ListOfInterfaces.IMyInterface))) {
          return ((android.aidl.tests.ListOfInterfaces.IMyInterface)iin);
        }
        return new android.aidl.tests.ListOfInterfaces.IMyInterface.Stub.Proxy(obj);
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
          case TRANSACTION_methodWithInterfaces:
          {
            return "methodWithInterfaces";
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
          case TRANSACTION_methodWithInterfaces:
          {
            android.aidl.tests.ListOfInterfaces.IEmptyInterface _arg0;
            _arg0 = android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub.asInterface(data.readStrongBinder());
            android.aidl.tests.ListOfInterfaces.IEmptyInterface _arg1;
            _arg1 = android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub.asInterface(data.readStrongBinder());
            java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _arg2;
            _arg2 = data.createInterfaceArrayList(android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
            java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _arg3;
            _arg3 = new java.util.ArrayList<android.aidl.tests.ListOfInterfaces.IEmptyInterface>();
            java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _arg4;
            _arg4 = data.createInterfaceArrayList(android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
            java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _arg5;
            _arg5 = data.createInterfaceArrayList(android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
            java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _arg6;
            _arg6 = new java.util.ArrayList<android.aidl.tests.ListOfInterfaces.IEmptyInterface>();
            java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _arg7;
            _arg7 = data.createInterfaceArrayList(android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
            data.enforceNoDataAvail();
            java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _result = this.methodWithInterfaces(_arg0, _arg1, _arg2, _arg3, _arg4, _arg5, _arg6, _arg7);
            reply.writeNoException();
            reply.writeInterfaceList(_result);
            reply.writeInterfaceList(_arg3);
            reply.writeInterfaceList(_arg4);
            reply.writeInterfaceList(_arg6);
            reply.writeInterfaceList(_arg7);
            break;
          }
          default:
          {
            return super.onTransact(code, data, reply, flags);
          }
        }
        return true;
      }
      private static class Proxy implements android.aidl.tests.ListOfInterfaces.IMyInterface
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
        @Override public java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> methodWithInterfaces(android.aidl.tests.ListOfInterfaces.IEmptyInterface iface, android.aidl.tests.ListOfInterfaces.IEmptyInterface nullable_iface, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list_in, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list_out, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list_inout, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list_in, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list_out, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list_inout) throws android.os.RemoteException
        {
          android.os.Parcel _data = android.os.Parcel.obtain(asBinder());
          android.os.Parcel _reply = android.os.Parcel.obtain();
          java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _result;
          try {
            _data.writeInterfaceToken(DESCRIPTOR);
            _data.writeStrongInterface(iface);
            _data.writeStrongInterface(nullable_iface);
            _data.writeInterfaceList(iface_list_in);
            _data.writeInterfaceList(iface_list_inout);
            _data.writeInterfaceList(nullable_iface_list_in);
            _data.writeInterfaceList(nullable_iface_list_inout);
            boolean _status = mRemote.transact(Stub.TRANSACTION_methodWithInterfaces, _data, _reply, 0);
            _reply.readException();
            _result = _reply.createInterfaceArrayList(android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
            _reply.readInterfaceList(iface_list_out, android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
            _reply.readInterfaceList(iface_list_inout, android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
            _reply.readInterfaceList(nullable_iface_list_out, android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
            _reply.readInterfaceList(nullable_iface_list_inout, android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
          }
          finally {
            _reply.recycle();
            _data.recycle();
          }
          return _result;
        }
      }
      static final int TRANSACTION_methodWithInterfaces = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
      /** @hide */
      public int getMaxTransactionId()
      {
        return 0;
      }
    }
    /** @hide */
    public static final java.lang.String DESCRIPTOR = "android.aidl.tests.ListOfInterfaces.IMyInterface";
    public java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> methodWithInterfaces(android.aidl.tests.ListOfInterfaces.IEmptyInterface iface, android.aidl.tests.ListOfInterfaces.IEmptyInterface nullable_iface, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list_in, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list_out, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list_inout, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list_in, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list_out, java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list_inout) throws android.os.RemoteException;
  }
  public static class MyParcelable implements android.os.Parcelable
  {
    public android.aidl.tests.ListOfInterfaces.IEmptyInterface iface;
    public android.aidl.tests.ListOfInterfaces.IEmptyInterface nullable_iface;
    public java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list;
    public java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list;
    public static final android.os.Parcelable.Creator<MyParcelable> CREATOR = new android.os.Parcelable.Creator<MyParcelable>() {
      @Override
      public MyParcelable createFromParcel(android.os.Parcel _aidl_source) {
        MyParcelable _aidl_out = new MyParcelable();
        _aidl_out.readFromParcel(_aidl_source);
        return _aidl_out;
      }
      @Override
      public MyParcelable[] newArray(int _aidl_size) {
        return new MyParcelable[_aidl_size];
      }
    };
    @Override public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag)
    {
      int _aidl_start_pos = _aidl_parcel.dataPosition();
      _aidl_parcel.writeInt(0);
      _aidl_parcel.writeStrongInterface(iface);
      _aidl_parcel.writeStrongInterface(nullable_iface);
      _aidl_parcel.writeInterfaceList(iface_list);
      _aidl_parcel.writeInterfaceList(nullable_iface_list);
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
        iface = android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub.asInterface(_aidl_parcel.readStrongBinder());
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_iface = android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub.asInterface(_aidl_parcel.readStrongBinder());
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        iface_list = _aidl_parcel.createInterfaceArrayList(android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
        if (_aidl_parcel.dataPosition() - _aidl_start_pos >= _aidl_parcelable_size) return;
        nullable_iface_list = _aidl_parcel.createInterfaceArrayList(android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
      } finally {
        if (_aidl_start_pos > (Integer.MAX_VALUE - _aidl_parcelable_size)) {
          throw new android.os.BadParcelableException("Overflow in the size of parcelable");
        }
        _aidl_parcel.setDataPosition(_aidl_start_pos + _aidl_parcelable_size);
      }
    }
    @Override
    public String toString() {
      java.util.StringJoiner _aidl_sj = new java.util.StringJoiner(", ", "{", "}");
      _aidl_sj.add("iface: " + (java.util.Objects.toString(iface)));
      _aidl_sj.add("nullable_iface: " + (java.util.Objects.toString(nullable_iface)));
      _aidl_sj.add("iface_list: " + (java.util.Objects.toString(iface_list)));
      _aidl_sj.add("nullable_iface_list: " + (java.util.Objects.toString(nullable_iface_list)));
      return "MyParcelable" + _aidl_sj.toString()  ;
    }
    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null) return false;
      if (!(other instanceof MyParcelable)) return false;
      MyParcelable that = (MyParcelable)other;
      if (!java.util.Objects.deepEquals(iface, that.iface)) return false;
      if (!java.util.Objects.deepEquals(nullable_iface, that.nullable_iface)) return false;
      if (!java.util.Objects.deepEquals(iface_list, that.iface_list)) return false;
      if (!java.util.Objects.deepEquals(nullable_iface_list, that.nullable_iface_list)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return java.util.Arrays.deepHashCode(java.util.Arrays.asList(iface, nullable_iface, iface_list, nullable_iface_list).toArray());
    }
    @Override
    public int describeContents() {
      int _mask = 0;
      return _mask;
    }
  }
  public static final class MyUnion implements android.os.Parcelable {
    // tags for union fields
    public final static int iface = 0;  // android.aidl.tests.ListOfInterfaces.IEmptyInterface iface;
    public final static int nullable_iface = 1;  // android.aidl.tests.ListOfInterfaces.IEmptyInterface nullable_iface;
    public final static int iface_list = 2;  // List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list;
    public final static int nullable_iface_list = 3;  // List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list;

    private int _tag;
    private Object _value;

    public MyUnion() {
      android.aidl.tests.ListOfInterfaces.IEmptyInterface _value = null;
      this._tag = iface;
      this._value = _value;
    }

    private MyUnion(android.os.Parcel _aidl_parcel) {
      readFromParcel(_aidl_parcel);
    }

    private MyUnion(int _tag, Object _value) {
      this._tag = _tag;
      this._value = _value;
    }

    public int getTag() {
      return _tag;
    }

    // android.aidl.tests.ListOfInterfaces.IEmptyInterface iface;

    public static MyUnion iface(android.aidl.tests.ListOfInterfaces.IEmptyInterface _value) {
      return new MyUnion(iface, _value);
    }

    public android.aidl.tests.ListOfInterfaces.IEmptyInterface getIface() {
      _assertTag(iface);
      return (android.aidl.tests.ListOfInterfaces.IEmptyInterface) _value;
    }

    public void setIface(android.aidl.tests.ListOfInterfaces.IEmptyInterface _value) {
      _set(iface, _value);
    }

    // android.aidl.tests.ListOfInterfaces.IEmptyInterface nullable_iface;

    public static MyUnion nullable_iface(android.aidl.tests.ListOfInterfaces.IEmptyInterface _value) {
      return new MyUnion(nullable_iface, _value);
    }

    public android.aidl.tests.ListOfInterfaces.IEmptyInterface getNullable_iface() {
      _assertTag(nullable_iface);
      return (android.aidl.tests.ListOfInterfaces.IEmptyInterface) _value;
    }

    public void setNullable_iface(android.aidl.tests.ListOfInterfaces.IEmptyInterface _value) {
      _set(nullable_iface, _value);
    }

    // List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> iface_list;

    public static MyUnion iface_list(java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _value) {
      return new MyUnion(iface_list, _value);
    }

    @SuppressWarnings("unchecked")
    public java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> getIface_list() {
      _assertTag(iface_list);
      return (java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface>) _value;
    }

    public void setIface_list(java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _value) {
      _set(iface_list, _value);
    }

    // List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> nullable_iface_list;

    public static MyUnion nullable_iface_list(java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _value) {
      return new MyUnion(nullable_iface_list, _value);
    }

    @SuppressWarnings("unchecked")
    public java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> getNullable_iface_list() {
      _assertTag(nullable_iface_list);
      return (java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface>) _value;
    }

    public void setNullable_iface_list(java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _value) {
      _set(nullable_iface_list, _value);
    }

    public static final android.os.Parcelable.Creator<MyUnion> CREATOR = new android.os.Parcelable.Creator<MyUnion>() {
      @Override
      public MyUnion createFromParcel(android.os.Parcel _aidl_source) {
        return new MyUnion(_aidl_source);
      }
      @Override
      public MyUnion[] newArray(int _aidl_size) {
        return new MyUnion[_aidl_size];
      }
    };

    @Override
    public final void writeToParcel(android.os.Parcel _aidl_parcel, int _aidl_flag) {
      _aidl_parcel.writeInt(_tag);
      switch (_tag) {
      case iface:
        _aidl_parcel.writeStrongInterface(getIface());
        break;
      case nullable_iface:
        _aidl_parcel.writeStrongInterface(getNullable_iface());
        break;
      case iface_list:
        _aidl_parcel.writeInterfaceList(getIface_list());
        break;
      case nullable_iface_list:
        _aidl_parcel.writeInterfaceList(getNullable_iface_list());
        break;
      }
    }

    public void readFromParcel(android.os.Parcel _aidl_parcel) {
      int _aidl_tag;
      _aidl_tag = _aidl_parcel.readInt();
      switch (_aidl_tag) {
      case iface: {
        android.aidl.tests.ListOfInterfaces.IEmptyInterface _aidl_value;
        _aidl_value = android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub.asInterface(_aidl_parcel.readStrongBinder());
        _set(_aidl_tag, _aidl_value);
        return; }
      case nullable_iface: {
        android.aidl.tests.ListOfInterfaces.IEmptyInterface _aidl_value;
        _aidl_value = android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub.asInterface(_aidl_parcel.readStrongBinder());
        _set(_aidl_tag, _aidl_value);
        return; }
      case iface_list: {
        java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _aidl_value;
        _aidl_value = _aidl_parcel.createInterfaceArrayList(android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
        _set(_aidl_tag, _aidl_value);
        return; }
      case nullable_iface_list: {
        java.util.List<android.aidl.tests.ListOfInterfaces.IEmptyInterface> _aidl_value;
        _aidl_value = _aidl_parcel.createInterfaceArrayList(android.aidl.tests.ListOfInterfaces.IEmptyInterface.Stub::asInterface);
        _set(_aidl_tag, _aidl_value);
        return; }
      }
      throw new IllegalArgumentException("union: unknown tag: " + _aidl_tag);
    }

    @Override
    public int describeContents() {
      int _mask = 0;
      switch (getTag()) {
      }
      return _mask;
    }

    @Override
    public String toString() {
      switch (_tag) {
      case iface: return "MyUnion.iface(" + (java.util.Objects.toString(getIface())) + ")";
      case nullable_iface: return "MyUnion.nullable_iface(" + (java.util.Objects.toString(getNullable_iface())) + ")";
      case iface_list: return "MyUnion.iface_list(" + (java.util.Objects.toString(getIface_list())) + ")";
      case nullable_iface_list: return "MyUnion.nullable_iface_list(" + (java.util.Objects.toString(getNullable_iface_list())) + ")";
      }
      throw new IllegalStateException("unknown field: " + _tag);
    }
    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      if (other == null) return false;
      if (!(other instanceof MyUnion)) return false;
      MyUnion that = (MyUnion)other;
      if (_tag != that._tag) return false;
      if (!java.util.Objects.deepEquals(_value, that._value)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return java.util.Arrays.deepHashCode(java.util.Arrays.asList(_tag, _value).toArray());
    }

    private void _assertTag(int tag) {
      if (getTag() != tag) {
        throw new IllegalStateException("bad access: " + _tagString(tag) + ", " + _tagString(getTag()) + " is available.");
      }
    }

    private String _tagString(int _tag) {
      switch (_tag) {
      case iface: return "iface";
      case nullable_iface: return "nullable_iface";
      case iface_list: return "iface_list";
      case nullable_iface_list: return "nullable_iface_list";
      }
      throw new IllegalStateException("unknown field: " + _tag);
    }

    private void _set(int _tag, Object _value) {
      this._tag = _tag;
      this._value = _value;
    }
    public static @interface Tag {
      public static final int iface = 0;
      public static final int nullable_iface = 1;
      public static final int iface_list = 2;
      public static final int nullable_iface_list = 3;
    }
  }
}
