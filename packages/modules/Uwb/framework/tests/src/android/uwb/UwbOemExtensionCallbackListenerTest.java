/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.uwb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.PersistableBundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

/**
 * Test of {@link UwbOemExtensionCallbackListener}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class UwbOemExtensionCallbackListenerTest {

    @Mock
    private IUwbAdapter mIUwbAdapter;
    @Mock
    private UwbManager.UwbOemExtensionCallback mUwbOemExtensionCallback;
    @Mock
    private UwbManager.UwbOemExtensionCallback mUwbOemExtensionCallback2;
    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();

    private UwbOemExtensionCallbackListener mUwbOemExtensionCallbackListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mUwbOemExtensionCallbackListener = new UwbOemExtensionCallbackListener(mIUwbAdapter);
    }

    @Test
    public void testRegisterUnregister() throws Exception {
        // Register callback
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        verify(mIUwbAdapter, times(1))
                .registerOemExtensionCallback(mUwbOemExtensionCallbackListener);
        // Unregister first callback
        mUwbOemExtensionCallbackListener.unregister(mUwbOemExtensionCallback);
        verify(mIUwbAdapter, times(1))
                .unregisterOemExtensionCallback(mUwbOemExtensionCallbackListener);

        // Register second callback
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback2);
        verify(mIUwbAdapter, times(2))
                .registerOemExtensionCallback(mUwbOemExtensionCallbackListener);
        // Unregister second callback
        mUwbOemExtensionCallbackListener.unregister(mUwbOemExtensionCallback2);
        verify(mIUwbAdapter, times(2))
                .unregisterOemExtensionCallback(mUwbOemExtensionCallbackListener);
    }

    @Test
    public void testRegister_failedThrowsRuntimeException() throws Exception {
        doThrow(new RuntimeException())
                .when(mIUwbAdapter)
                .registerOemExtensionCallback(mUwbOemExtensionCallbackListener);
        assertThrows(RuntimeException.class,
                () -> mUwbOemExtensionCallbackListener.register(EXECUTOR,
                        mUwbOemExtensionCallback));
    }

    @Test
    public void testUnregister_failedThrowsRuntimeException() throws Exception {
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        doThrow(new RuntimeException())
                .when(mIUwbAdapter)
                .unregisterOemExtensionCallback(mUwbOemExtensionCallbackListener);
        assertThrows(RuntimeException.class,
                () -> mUwbOemExtensionCallbackListener.unregister(mUwbOemExtensionCallback));
    }

    @Test
    public void testDuplicateRegister_IllegalArgumentException() throws Exception {
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        try {
            mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
            fail();
        } catch (IllegalArgumentException e) {
            /* pass */
        }
    }

    @Test
    public void testWrongCallback_IllegalArgumentException() throws Exception {
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        try {
            mUwbOemExtensionCallbackListener.unregister(mUwbOemExtensionCallback2);
            fail();
        } catch (IllegalArgumentException e) {
            /* pass */
        }
    }

    @Test
    public void testOnSessionStatusNotificationReceived() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        mUwbOemExtensionCallbackListener.onSessionStatusNotificationReceived(
                new PersistableBundle());
        verify(mUwbOemExtensionCallback).onSessionStatusNotificationReceived(any());
    }

    @Test
    public void testOnDeviceStatusNotificationReceived() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        mUwbOemExtensionCallbackListener.onDeviceStatusNotificationReceived(
                new PersistableBundle());
        verify(mUwbOemExtensionCallback).onDeviceStatusNotificationReceived(any());
    }

    @Test
    public void testOnSessionConfigurationReceived() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        int status = 0;
        when(mUwbOemExtensionCallback.onSessionConfigurationComplete(any())).thenReturn(status);
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        int retStatus = mUwbOemExtensionCallbackListener.onSessionConfigurationReceived(
                new PersistableBundle());
        verify(mUwbOemExtensionCallback).onSessionConfigurationComplete(any());
        assertEquals(status, retStatus);
    }

    @Test
    public void testOnRangingReportReceived() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        RangingReport mRangingReport = new RangingReport.Builder().build();
        RangingReport mRangingReport1 = new RangingReport.Builder().build();
        when(mUwbOemExtensionCallback.onRangingReportReceived(any())).thenReturn(mRangingReport);
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        RangingReport report = mUwbOemExtensionCallbackListener.onRangingReportReceived(
                mRangingReport1);
        verify(mUwbOemExtensionCallback).onRangingReportReceived(any());
        assertEquals(report, mRangingReport);
    }

    @Test
    public void testOnCheckPointedTarget() throws Exception {
        assumeTrue(SdkLevel.isAtLeastU());
        boolean status = true;
        when(mUwbOemExtensionCallback.onCheckPointedTarget(any())).thenReturn(status);
        mUwbOemExtensionCallbackListener.register(EXECUTOR, mUwbOemExtensionCallback);
        boolean retStatus = mUwbOemExtensionCallbackListener.onCheckPointedTarget(
                new PersistableBundle());
        verify(mUwbOemExtensionCallback).onCheckPointedTarget(any());
        assertEquals(status, retStatus);
    }
}
