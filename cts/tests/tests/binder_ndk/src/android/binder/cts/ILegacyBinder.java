/*
 * Copyright (C) 2021 The Android Open Source Project
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

/**
 * Manual implementation of AIDL service. Warning: it is strongly recommended to use AIDL directly.
 * This interface does not do any type checking or handle errors.
 */
public interface ILegacyBinder extends android.os.IInterface {
  public static class Stub extends android.os.Binder implements ILegacyBinder {
    public Stub() {
      super(DESCRIPTOR);
      this.attachInterface(this, DESCRIPTOR);
    }
    public static ILegacyBinder asInterface(android.os.IBinder obj) {
      if (obj == null) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin != null) && (iin instanceof ILegacyBinder))) {
        return ((ILegacyBinder) iin);
      }
      return new ILegacyBinder.Stub.Proxy(obj);
    }
    @Override
    public int RepeatInt(int in) throws android.os.RemoteException {
      return in;
    }
    @Override
    public android.os.IBinder asBinder() {
      return this;
    }
    @Override
    public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags)
        throws android.os.RemoteException {
      switch (code) {
        case TRANSACTION_RepeatInt: {
          reply.writeInt(RepeatInt(data.readInt()));
          return true;
        }
        default: {
          return super.onTransact(code, data, reply, flags);
        }
      }
    }
    private static class Proxy implements ILegacyBinder {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote) {
        mRemote = remote;
      }
      @Override
      public android.os.IBinder asBinder() {
        return mRemote;
      }
      public String getInterfaceDescriptor() {
        return DESCRIPTOR;
      }
      @Override
      public int RepeatInt(int in) throws android.os.RemoteException {
        android.os.Parcel data = android.os.Parcel.obtain();
        android.os.Parcel reply = android.os.Parcel.obtain();
        try {
          data.writeInt(in);
          mRemote.transact(Stub.TRANSACTION_RepeatInt, data, reply, 0);
          return reply.readInt();
        } finally {
          reply.recycle();
          data.recycle();
        }
      }
    }
    static final int TRANSACTION_RepeatInt = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
  }
  public static final String DESCRIPTOR = "LegacyBinder";
  public int RepeatInt(int in) throws android.os.RemoteException;
}
