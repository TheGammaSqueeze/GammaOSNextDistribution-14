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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IExecuteCallback;
import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public final class OnDevicePersonalizationSystemEventManagerTest {
    private static final String TAG = "OnDevicePersonalizationManagerTest";
    private static final String KEY_OP = "op";
    private Context mContext = ApplicationProvider.getApplicationContext();
    private TestServiceBinder mTestBinder = new TestServiceBinder(
            IOnDevicePersonalizationManagingService.Stub.asInterface(new TestService()));
    private OnDevicePersonalizationSystemEventManager mManager =
            new OnDevicePersonalizationSystemEventManager(mContext, mTestBinder);

    @Test
    public void testnotifyMeasurementEventSuccess() throws Exception {
        var receiver = new ResultReceiver<Void>();
        mManager.notifyMeasurementEvent(
                new MeasurementWebTriggerEventParams.Builder(
                        Uri.parse("http://ok"),
                        "com.example.browser",
                        ComponentName.createRelative("com.example", ".Example"))
                            .build(),
                Executors.newSingleThreadExecutor(),
                receiver);
        assertTrue(receiver.isSuccess());
        assertFalse(receiver.isError());
    }

    @Test
    public void testnotifyMeasurementEventError() throws Exception {
        var receiver = new ResultReceiver<Void>();
        mManager.notifyMeasurementEvent(
                new MeasurementWebTriggerEventParams.Builder(
                        Uri.parse("http://error"),
                        "com.example.browser",
                        ComponentName.createRelative("com.example", ".Example"))
                            .build(),
                Executors.newSingleThreadExecutor(),
                receiver);
        assertFalse(receiver.isSuccess());
        assertTrue(receiver.isError());
    }

    @Test
    public void testnotifyMeasurementEventPropagatesIae() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> mManager.notifyMeasurementEvent(
                        new MeasurementWebTriggerEventParams.Builder(
                            Uri.parse("http://iae"),
                            "com.example.browser",
                            ComponentName.createRelative("com.example", ".Example"))
                                .build(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<Void>()));
    }

    @Test
    public void testnotifyMeasurementEventPropagatesNpe() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mManager.notifyMeasurementEvent(
                        new MeasurementWebTriggerEventParams.Builder(
                            Uri.parse("http://npe"),
                            "com.example.browser",
                            ComponentName.createRelative("com.example", ".Example"))
                                .build(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<Void>()));
    }

    @Test
    public void testnotifyMeasurementEventCatchesExceptions() throws Exception {
        var receiver = new ResultReceiver<Void>();
        mManager.notifyMeasurementEvent(
                new MeasurementWebTriggerEventParams.Builder(
                        Uri.parse("http://ise"),
                        "com.example.browser",
                        ComponentName.createRelative("com.example", ".Example"))
                            .build(),
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
                Bundle params,
                CallerMetadata metadata,
                IExecuteCallback callback) {
            throw new UnsupportedOperationException();
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
            try {
                MeasurementWebTriggerEventParamsParcel wtparams = params.getParcelable(
                        Constants.EXTRA_MEASUREMENT_WEB_TRIGGER_PARAMS,
                        MeasurementWebTriggerEventParamsParcel.class);
                String url = wtparams.getDestinationUrl().toString();
                if (url.equals("http://error")) {
                    callback.onError(Constants.STATUS_INTERNAL_ERROR);
                } else if (url.equals("http://iae")) {
                    throw new IllegalArgumentException();
                } else if (url.equals("http://npe")) {
                    throw new NullPointerException();
                } else if (url.equals("http://ise")) {
                    throw new IllegalStateException();
                } else {
                    callback.onSuccess();
                }
            } catch (RemoteException e) {
                Log.e(TAG, "callback error", e);
            }
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
