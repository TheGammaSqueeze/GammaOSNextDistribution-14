/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.test.AndroidTestCase;

import com.google.common.util.concurrent.AbstractFuture;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ParcelIntegrationTest extends AndroidTestCase {
    public static class ParcelExceptionConnection extends AbstractFuture<IParcelExceptionService>
            implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            set(IParcelExceptionService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public IParcelExceptionService get() throws InterruptedException, ExecutionException {
            try {
                return get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void testExceptionOverwritesObject() throws Exception {
        final android.content.Intent intent = new android.content.Intent();
        intent.setComponent(new ComponentName(
                "android.os.cts", "android.os.cts.ParcelExceptionService"));

        final ParcelExceptionConnection connection = new ParcelExceptionConnection();

        mContext.startService(intent);
        assertTrue(mContext.bindService(intent, connection,
                Context.BIND_ABOVE_CLIENT | Context.BIND_EXTERNAL_SERVICE));


        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        data.writeInterfaceToken("android.os.cts.IParcelExceptionService");
        IParcelExceptionService service = connection.get();
        try {
            assertTrue("Transaction failed", service.asBinder().transact(
                    IParcelExceptionService.Stub.TRANSACTION_writeBinderThrowException, data, reply,
                    0));
        } catch (Exception e) {
            fail("Exception caught from transaction: " + e);
        }
        reply.setDataPosition(0);
        assertTrue("Exception should have occurred on service-side",
                reply.readExceptionCode() != 0);
        assertNull("Binder should have been overwritten by the exception",
                reply.readStrongBinder());
    }

    public static class ParcelObjectFreeService extends Service {

        @Override
        public IBinder onBind(Intent intent) {
            return new Binder();
        }

        @Override
        public void onCreate() {
            super.onCreate();

            Parcel parcel = Parcel.obtain();

            // Construct parcel with object in it.
            parcel.writeInt(1);
            final int pos = parcel.dataPosition();
            parcel.writeStrongBinder(new Binder());

            // wipe out the object by setting data size
            parcel.setDataSize(pos);

            // recycle the parcel. This should not cause a native segfault
            parcel.recycle();
        }

        public static class Connection extends AbstractFuture<IBinder>
                implements ServiceConnection {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                set(service);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }

            @Override
            public IBinder get() throws InterruptedException, ExecutionException {
                try {
                    return get(5, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    return null;
                }
            }
        }
    }

    public void testObjectDoubleFree() throws Exception {
        final Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "android.os.cts", "android.os.cts.ParcelIntegrationTest$ParcelObjectFreeService"));

        final ParcelObjectFreeService.Connection connection =
                new ParcelObjectFreeService.Connection();

        mContext.startService(intent);
        assertTrue(mContext.bindService(intent, connection,
                Context.BIND_ABOVE_CLIENT | Context.BIND_EXTERNAL_SERVICE));

        assertNotNull("Service should have started without crashing.", connection.get());
    }

    public void testWriteTypedList() {
        Parcel p = Parcel.obtain();
        ArrayList<ParcelTest.SimpleParcelable> list = new ArrayList<>();
        ParcelTest.SimpleParcelable spy = spy(new ParcelTest.SimpleParcelable(42));
        list.add(spy);
        int flags = Parcelable.PARCELABLE_WRITE_RETURN_VALUE;
        p.writeTypedList(list, flags);

        verify(spy).writeToParcel(p, flags);

        p.setDataPosition(0);
        ArrayList<ParcelTest.SimpleParcelable> read = p.createTypedArrayList(
                ParcelTest.SimpleParcelable.CREATOR);
        assertEquals(list.size(), read.size());
        assertEquals(list.get(0).getValue(), read.get(0).getValue());
        p.recycle();
    }
}
