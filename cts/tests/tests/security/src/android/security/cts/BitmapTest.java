/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.security.cts;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.BadParcelableException;
import android.os.IBinder;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import com.google.common.util.concurrent.AbstractFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class BitmapTest extends StsExtraBusinessLogicTestCase {

    private Instrumentation mInstrumentation;
    private PeerConnection mRemoteConnection;
    private IBitmapService mRemote;
    private Intent mIntent;

    public static class PeerConnection extends AbstractFuture<IBitmapService>
            implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            set(IBitmapService.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public IBitmapService get() throws InterruptedException, ExecutionException {
            try {
                return get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @After
    public void tearDown() {
        if (mRemoteConnection != null) {
            final Context context = mInstrumentation.getContext();
            context.unbindService(mRemoteConnection);
            try {
                mRemote.exit();
            } catch (Exception ex) { }
            mRemote = null;
            mRemoteConnection = null;
        }
    }

    IBitmapService getRemoteService() throws ExecutionException, InterruptedException {
        if (mRemote == null) {
            final Context context = mInstrumentation.getContext();
            mIntent = new Intent();
            mIntent.setComponent(new ComponentName(
                    "android.security.cts", "android.security.cts.BitmapService"));
            mRemoteConnection = new PeerConnection();
            context.bindService(mIntent, mRemoteConnection, Context.BIND_AUTO_CREATE);
            mRemote = mRemoteConnection.get();
        }
        return mRemote;
    }

    /**
     * Test Bitmap.createBitmap properly throws OOME on large inputs.
     *
     * A prior change in behavior resulted in throwing an NPE instead.
     * OOME is more appropriate.
     */
    @Test(expected=OutOfMemoryError.class)
    @AsbSecurityTest(cveBugId = 33846679)
    public void test_33846679() {
        // This size is based on the max size possible in a GIF file,
        // which might be passed to createBitmap from a Java decoder.
        Bitmap.createBitmap(65535, 65535, Bitmap.Config.ARGB_8888);
    }

    @Test
    @AsbSecurityTest(cveBugId = 213169612)
    public void test_inplace_213169612() throws Exception {
        IBitmapService remote = getRemoteService();
        Assert.assertTrue("Binder should be alive", remote.ping());
        BitmapWrapper wrapper = new BitmapWrapper(
                Bitmap.createBitmap(2, 4, Bitmap.Config.ARGB_8888));
        final int expectedAllocationSize = wrapper.getBitmap().getAllocationByteCount();
        int allocationSize = remote.getAllocationSize(wrapper);
        Assert.assertEquals(expectedAllocationSize, allocationSize);
        Assert.assertTrue("Binder should be alive", remote.ping());

        // Override the bitmap size to 500KiB; larger than the actual size
        wrapper.reset()
                .replace(BitmapWrapper.Field.DataSize, 500 * 1024);
        allocationSize = remote.getAllocationSize(wrapper);
        Assert.assertEquals(expectedAllocationSize, allocationSize);
        Assert.assertTrue("Binder should be alive", remote.ping());

        // Override the bitmap size to 2 bytes; smaller than the actual size
        wrapper.reset()
                .replace(BitmapWrapper.Field.DataSize, 2);
        try {
            Assert.assertFalse("Should have failed to unparcel",
                    remote.didReceiveBitmap(wrapper));
        } catch (BadParcelableException ex) {
            // We'll also accept a BadParcelableException
        }
        Assert.assertTrue("Binder should be alive", remote.ping());

        // Keep the blob size accurate, but change computed allocation size to be too large
        wrapper.reset()
                .replace(BitmapWrapper.Field.Height, 10_000)
                .replace(BitmapWrapper.Field.RowBytes, 50_000);
        try {
            Assert.assertFalse("Should have failed to unparcel",
                    remote.didReceiveBitmap(wrapper));
        } catch (BadParcelableException ex) {
            // We'll also accept a BadParcelableException
        }
        Assert.assertTrue("Binder should be alive", remote.ping());
    }

    @Test
    @AsbSecurityTest(cveBugId = 213169612)
    public void test_ashmem_213169612() throws Exception {
        IBitmapService remote = getRemoteService();
        Assert.assertTrue("Binder should be alive", remote.ping());
        BitmapWrapper wrapper = new BitmapWrapper(
                Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
                        .createAshmemBitmap());
        final int expectedAllocationSize = wrapper.getBitmap().getAllocationByteCount();
        int allocationSize = remote.getAllocationSize(wrapper);
        Assert.assertEquals(expectedAllocationSize, allocationSize);
        Assert.assertTrue("Binder should be alive", remote.ping());

        // Override the bitmap size to be larger than the initial size
        wrapper.reset()
                .replace(BitmapWrapper.Field.DataSize, expectedAllocationSize * 2);
        try {
            Assert.assertFalse("Should have failed to unparcel",
                    remote.didReceiveBitmap(wrapper));
        } catch (BadParcelableException ex) {
            // We'll also accept a BadParcelableException
        }
        Assert.assertTrue("Binder should be alive", remote.ping());

        // Override the bitmap size to 2 bytes; smaller than the actual size
        wrapper.reset()
                .replace(BitmapWrapper.Field.DataSize, 2);
        try {
            Assert.assertFalse("Should have failed to unparcel",
                    remote.didReceiveBitmap(wrapper));
        } catch (BadParcelableException ex) {
            // We'll also accept a BadParcelableException
        }
        Assert.assertTrue("Binder should be alive", remote.ping());

        // Keep the ashmem size accurate, but change computed allocation size to be too large
        wrapper.reset()
                .replace(BitmapWrapper.Field.Height, 10_000)
                .replace(BitmapWrapper.Field.RowBytes, 50_000);
        try {
            Assert.assertFalse("Should have failed to unparcel",
                    remote.didReceiveBitmap(wrapper));
        } catch (BadParcelableException ex) {
            // We'll also accept a BadParcelableException
        }
        Assert.assertTrue("Binder should be alive", remote.ping());

        // Keep the ashmem size accurate, but change computed allocation size to be smaller
        wrapper.reset()
                .replace(BitmapWrapper.Field.Height, 100);
        allocationSize = remote.getAllocationSize(wrapper);
        Assert.assertEquals(expectedAllocationSize, allocationSize);
        Assert.assertTrue("Binder should be alive", remote.ping());
    }
}
