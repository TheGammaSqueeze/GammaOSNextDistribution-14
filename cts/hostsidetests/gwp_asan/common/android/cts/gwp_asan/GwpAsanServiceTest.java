/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.cts.gwp_asan;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;

import com.android.compatibility.common.util.DropBoxReceiver;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class GwpAsanServiceTest {
    // Increase service bind time to deal with slower coverage builds: b/310330730
    @Rule public final ServiceTestRule mServiceRule =
        ServiceTestRule.withTimeout(15, TimeUnit.SECONDS);
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = getApplicationContext();
    }

    private void runServiceAndCheckSuccess(Class<?> cls, int testNum) throws Exception {
        Intent serviceIntent = new Intent(getApplicationContext(), cls);
        IBinder binder = mServiceRule.bindService(serviceIntent);
        final Parcel reply = Parcel.obtain();
        if (!binder.transact(testNum, Parcel.obtain(), reply, 0)) {
            throw new Exception();
        }
        assertEquals(reply.readInt(), Utils.TEST_SUCCESS);
    }

    private void runService(Class<?> cls, int testNum) throws Exception {
        Intent serviceIntent = new Intent(getApplicationContext(), cls);
        IBinder binder = mServiceRule.bindService(serviceIntent);
        binder.transact(testNum, Parcel.obtain(), Parcel.obtain(), IBinder.FLAG_ONEWAY);
    }

    @Test
    public void testEnablement() throws Exception {
        runServiceAndCheckSuccess(GwpAsanEnabledService.class, Utils.TEST_IS_GWP_ASAN_ENABLED);
        runServiceAndCheckSuccess(GwpAsanDefaultService.class, Utils.TEST_IS_GWP_ASAN_ENABLED);
        runServiceAndCheckSuccess(GwpAsanDisabledService.class, Utils.TEST_IS_GWP_ASAN_DISABLED);
    }

    @Test
    public void testCrashToDropboxEnabled() throws Exception {
        DropBoxReceiver receiver = Utils.getDropboxReceiver(mContext, "gwp_asan_enabled");
        runService(GwpAsanEnabledService.class, Utils.TEST_USE_AFTER_FREE);
        assertTrue(receiver.await());
    }

    @Test
    public void testCrashToDropboxDefault() throws Exception {
        DropBoxReceiver receiver = Utils.getDropboxReceiver(mContext, "gwp_asan_default");
        runService(GwpAsanDefaultService.class, Utils.TEST_USE_AFTER_FREE);
        assertTrue(receiver.await());
    }

    @Test
    public void testCrashToDropboxRecoverableEnabled() throws Exception {
        DropBoxReceiver receiver = Utils.getDropboxReceiver(mContext, "gwp_asan_enabled");
        runService(GwpAsanEnabledService.class, Utils.TEST_USE_AFTER_FREE);
        assertTrue(receiver.await());
    }

    @Test
    public void testCrashToDropboxRecoverableDefault() throws Exception {
        DropBoxReceiver receiver =
                Utils.getDropboxReceiver(
                        mContext, "gwp_asan_default", Utils.DROPBOX_RECOVERABLE_TAG);
        runServiceAndCheckSuccess(GwpAsanDefaultService.class, Utils.TEST_USE_AFTER_FREE);
        assertTrue(receiver.await());
    }
}
