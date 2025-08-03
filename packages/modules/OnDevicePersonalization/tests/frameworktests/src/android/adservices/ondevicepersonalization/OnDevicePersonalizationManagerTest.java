/*
 * Copyright 2024 The Android Open Source Project
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

package android.adservices.ondevicepersonalization;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.ExecuteResult;
import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.ShellUtils;
import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.ondevicepersonalization.internal.util.ByteArrayParceledSlice;
import com.android.ondevicepersonalization.internal.util.PersistableBundleUtils;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(Parameterized.class)
public final class OnDevicePersonalizationManagerTest {
    private static final String TAG = "OnDevicePersonalizationManagerTest";
    private static final String KEY_OP = "op";
    private static final String KEY_STATUS_CODE = "status";
    private static final String KEY_SERVICE_ERROR_CODE = "serviceerror";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final TestServiceBinder mTestBinder = new TestServiceBinder(
            IOnDevicePersonalizationManagingService.Stub.asInterface(new TestService()));
    private final OnDevicePersonalizationManager mManager =
            new OnDevicePersonalizationManager(mContext, mTestBinder);

    @Parameterized.Parameter(0)
    public boolean mIsSipFeatureEnabled;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {true}, {false}
                }
        );
    }

    @Before
    public void setUp() {
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + mIsSipFeatureEnabled);
    }

    @Test
    public void testExecuteSuccess() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "ok");
        var receiver = new ResultReceiver<ExecuteResult>();
        mManager.execute(
                ComponentName.createRelative("com.example.service", ".Example"),
                params,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.isSuccess());
        assertFalse(receiver.isError());
        assertNotNull(receiver.getResult());
        assertEquals(receiver.getResult().getSurfacePackageToken().getTokenString(), "aaaa");
        assertArrayEquals(receiver.getResult().getOutputData(), new byte[]{1, 2, 3});
    }

    @Test
    public void testExecuteUnknownError() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        var receiver = new ResultReceiver<ExecuteResult>();
        mManager.execute(
                ComponentName.createRelative("com.example.service", ".Example"),
                params,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof IllegalStateException);
    }

    @Test
    public void testExecuteServiceError() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        params.putInt(KEY_STATUS_CODE, Constants.STATUS_SERVICE_FAILED);
        var receiver = new ResultReceiver<ExecuteResult>();
        mManager.execute(
                ComponentName.createRelative("com.example.service", ".Example"),
                params,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
    }

    @Test
    public void testExecuteErrorWithCode() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "error");
        params.putInt(KEY_STATUS_CODE, Constants.STATUS_SERVICE_FAILED);
        params.putInt(KEY_SERVICE_ERROR_CODE, 42);
        var receiver = new ResultReceiver<ExecuteResult>();
        mManager.execute(
                ComponentName.createRelative("com.example.service", ".Example"),
                params,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        //assertEquals("a", receiver.getException().getClass().getSimpleName());
        assertTrue(receiver.getException() instanceof OnDevicePersonalizationException);
        assertEquals(OnDevicePersonalizationException.ERROR_ISOLATED_SERVICE_FAILED,
                ((OnDevicePersonalizationException) receiver.getException()).getErrorCode());
        assertTrue(receiver.getException().getCause() instanceof IsolatedServiceException);
        assertEquals(42,
                ((IsolatedServiceException) receiver.getException().getCause()).getErrorCode());
    }

    @Test
    public void testExecutePropagatesIae() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "iae");
        assertThrows(
                IllegalArgumentException.class,
                () -> mManager.execute(
                        ComponentName.createRelative("com.example.service", ".Example"),
                        params,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<ExecuteResult>()));
    }

    @Test
    public void testExecutePropagatesNpe() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "npe");
        assertThrows(
                NullPointerException.class,
                () -> mManager.execute(
                        ComponentName.createRelative("com.example.service", ".Example"),
                        params,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<ExecuteResult>()));
    }

    @Test
    public void testExecuteCatchesOtherExceptions() throws Exception {
        PersistableBundle params = new PersistableBundle();
        params.putString(KEY_OP, "ise");
        var receiver = new ResultReceiver<ExecuteResult>();
        mManager.execute(
                ComponentName.createRelative("com.example.service", ".Example"),
                params,
                Executors.newSingleThreadExecutor(),
                receiver);
        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
        assertTrue(receiver.getException() instanceof IllegalStateException);
    }

    class TestService extends IOnDevicePersonalizationManagingService.Stub {
        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public void execute(
                String callingPackageName,
                ComponentName handler,
                Bundle wrappedParams,
                CallerMetadata metadata,
                IExecuteCallback callback) {
            try {
                PersistableBundle params;
                String op;
                try {
                    ByteArrayParceledSlice paramsBuffer = wrappedParams.getParcelable(
                            Constants.EXTRA_APP_PARAMS_SERIALIZED, ByteArrayParceledSlice.class);
                    params = PersistableBundleUtils.fromByteArray(paramsBuffer.getByteArray());
                    op = params.getString(KEY_OP);
                } catch (Exception e) {
                    Log.e(TAG, "error extracting params", e);
                    throw new IllegalStateException(e);
                }
                if (op.equals("ok")) {
                    Bundle bundle = new Bundle();
                    bundle.putString(Constants.EXTRA_SURFACE_PACKAGE_TOKEN_STRING, "aaaa");
                    bundle.putByteArray(Constants.EXTRA_OUTPUT_DATA, new byte[]{1, 2, 3});
                    callback.onSuccess(bundle);
                } else if (op.equals("error")) {
                    int statusCode = params.getInt(KEY_STATUS_CODE,
                            Constants.STATUS_INTERNAL_ERROR);
                    int serviceErrorCode = params.getInt(KEY_SERVICE_ERROR_CODE, 0);
                    callback.onError(statusCode, serviceErrorCode);
                } else if (op.equals("iae")) {
                    throw new IllegalArgumentException();
                } else if (op.equals("npe")) {
                    throw new NullPointerException();
                } else if (op.equals("ise")) {
                    throw new IllegalStateException();
                } else {
                    throw new UnsupportedOperationException();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "callback error", e);
            }
        }

        @Override
        public void requestSurfacePackage(
                String surfacePackageToken,
                IBinder hostToken,
                int displayId,
                int width,
                int height,
                CallerMetadata metadata,
                IRequestSurfacePackageCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerMeasurementEvent(
                int eventType,
                Bundle params,
                CallerMetadata metadata,
                IRegisterMeasurementEventCallback callback) {
            throw new UnsupportedOperationException();
        }
    }

    class TestServiceBinder
            extends AbstractServiceBinder<IOnDevicePersonalizationManagingService> {
        private final IOnDevicePersonalizationManagingService mService;
        TestServiceBinder(IOnDevicePersonalizationManagingService service) {
            mService = service;
        }

        @Override
        public IOnDevicePersonalizationManagingService getService(Executor executor) {
            return mService;
        }

        @Override
        public void unbindFromService() {}
    }
}
