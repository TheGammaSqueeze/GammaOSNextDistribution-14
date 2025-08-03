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
package android.security.rkp.service.test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.AdditionalAnswers.answer;
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.OperationCanceledException;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.UserHandle;
import android.security.rkp.service.RegistrationProxy;
import android.security.rkp.service.RkpProxyException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.rkpdapp.IGetKeyCallback;
import com.android.rkpdapp.IGetRegistrationCallback;
import com.android.rkpdapp.IRegistration;
import com.android.rkpdapp.IRemoteProvisioning;
import com.android.rkpdapp.IStoreUpgradedKeyCallback;
import com.android.rkpdapp.RemotelyProvisionedKey;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidJUnit4.class)
public class RegistrationProxyTests {
    private static final String TAG = "RegistrationProxyTests";
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(1);
    private static final Duration BIND_TIMEOUT = MAX_TIMEOUT.dividedBy(2);
    private static final String FAKE_SERVICE = "fake-service-name";
    private static final String FAKE_PACKAGE = "fake-package-name";
    private static final ComponentName FAKE_COMPONENT =
            new ComponentName(FAKE_PACKAGE, FAKE_SERVICE);
    private static final int FAKE_CALLER_UID = 42;
    private static final String FAKE_IRPC = "fake irpc";

    ExecutorService mExecutor;

    @Before
    public void setUp() {
        mExecutor = Executors.newSingleThreadExecutor();
    }

    // Receiver that should only ever receive errors
    static class ErrorReceiver<ResultT> implements OutcomeReceiver<ResultT, Exception> {
        final CountDownLatch mLatch = new CountDownLatch(1);
        Exception mError;

        public Exception waitForError() throws InterruptedException {
            if (mLatch.await(MAX_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                return mError;
            }
            assertWithMessage("Timed out waiting for an error").fail();
            return null;
        }

        @Override
        public void onError(@NonNull Exception error) {
            Log.i(TAG, "ErrorReceiver.onError: " + error);
            mError = error;
            mLatch.countDown();
        }

        @Override
        public void onResult(@NonNull ResultT result) {
            assertWithMessage("should never be called").fail();
        }
    }

    // Receiver that should only ever receive valid results
    static class ResultReceiver<ResultT> implements OutcomeReceiver<ResultT, Exception> {
        final CountDownLatch mLatch = new CountDownLatch(1);
        ResultT mResult;

        public ResultT waitForResult() throws InterruptedException {
            if (mLatch.await(MAX_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                return mResult;
            }
            assertWithMessage(
                    "Timed out waiting for a " + mResult.getClass().getSimpleName()).fail();
            return null;
        }

        @Override
        public void onResult(@NonNull ResultT result) {
            Log.i(TAG, "ResultReceiver.onResult: " + result);
            mResult = result;
            mLatch.countDown();
        }

        @Override
        public void onError(@NonNull Exception error) {
            assertWithMessage("should never be called").fail();
        }
    }

    @Test
    public void serviceNotFound() throws Exception {
        final PackageManager packageManager = mock(PackageManager.class);
        doReturn(mock(List.class)).when(packageManager).queryIntentServices(any(), any());

        final Context context = mock(Context.class);
        doReturn(packageManager).when(context).getPackageManager();

        final var receiver = new ErrorReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                receiver);
        mExecutor.shutdown();
        assertThat(receiver.waitForError()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void tooManyServicesFound() throws Exception {
        final PackageManager packageManager = mock(PackageManager.class);
        List<ResolveInfo> resolveInfo = List.of(
                makeResolveInfo(FAKE_SERVICE, FAKE_PACKAGE),
                makeResolveInfo(FAKE_SERVICE, "another.package.name"));
        doReturn(resolveInfo).when(packageManager).queryIntentServices(any(), any());

        final Context context = mock(Context.class);
        doReturn(packageManager).when(context).getPackageManager();

        final var receiver = new ErrorReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                receiver);
        mExecutor.shutdown();
        assertThat(receiver.waitForError()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void bindServiceAsUserReturnsFalse() throws Exception {
        final Context context = mock(Context.class);
        final PackageManager packageManager = mock(PackageManager.class);
        doReturn(List.of(makeResolveInfo(FAKE_SERVICE, FAKE_PACKAGE))).when(
                packageManager).queryIntentServices(any(), any());
        doReturn(packageManager).when(context).getPackageManager();

        doReturn(false).when(context).bindServiceAsUser(any(), any(), anyInt(), any());

        final var receiver = new ErrorReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                receiver);
        mExecutor.shutdown();
        assertThat(receiver.waitForError()).isInstanceOf(RemoteException.class);
    }

    @Test
    public void boundRegistrationServiceIsNull() throws Exception {
        final Context context = mock(Context.class);
        final PackageManager packageManager = mock(PackageManager.class);
        doReturn(List.of(makeResolveInfo(FAKE_SERVICE, FAKE_PACKAGE))).when(
                packageManager).queryIntentServices(any(), any());
        doReturn(packageManager).when(context).getPackageManager();

        doAnswer(answer((intent, connection, flags, user) -> {
            ((ServiceConnection) connection).onNullBinding(FAKE_COMPONENT);
            return true;
        })).when(context).bindServiceAsUser(any(), any(), anyInt(), any());

        final var receiver = new ErrorReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                receiver);
        mExecutor.shutdown();
        assertThat(receiver.waitForError()).isInstanceOf(RemoteException.class);
    }

    @Test
    public void boundRegistrationServiceDisconnected() throws Exception {
        final Context context = mock(Context.class);
        final PackageManager packageManager = mock(PackageManager.class);
        doReturn(List.of(makeResolveInfo(FAKE_SERVICE, FAKE_PACKAGE))).when(
                packageManager).queryIntentServices(any(), any());
        doReturn(packageManager).when(context).getPackageManager();

        doAnswer(answer((intent, connection, flags, user) -> {
            ((ServiceConnection) connection).onServiceConnected(
                    FAKE_COMPONENT, mock(IRegistration.Stub.class));
            ((ServiceConnection) connection).onServiceDisconnected(FAKE_COMPONENT);
            return true;
        })).when(context).bindServiceAsUser(any(), any(), anyInt(), any());

        final var receiver = new ErrorReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                receiver);
        mExecutor.shutdown();
        final Exception error = receiver.waitForError();
        assertThat(error).isInstanceOf(RemoteException.class);
        assertThat(error).hasMessageThat().contains("disconnected");
    }

    @Test
    public void boundRegistrationServiceBindingDied() throws Exception {
        final Context context = mock(Context.class);
        final PackageManager packageManager = mock(PackageManager.class);
        doReturn(List.of(makeResolveInfo(FAKE_SERVICE, FAKE_PACKAGE))).when(
                packageManager).queryIntentServices(any(), any());
        doReturn(packageManager).when(context).getPackageManager();

        doAnswer(answer((intent, connection, flags, user) -> {
            ((ServiceConnection) connection).onServiceConnected(
                    FAKE_COMPONENT, mock(IRegistration.Stub.class));
            ((ServiceConnection) connection).onBindingDied(FAKE_COMPONENT);
            return true;
        })).when(context).bindServiceAsUser(any(), any(), anyInt(), any());

        final var receiver = new ErrorReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                receiver);
        mExecutor.shutdown();
        final Exception error = receiver.waitForError();
        assertThat(error).isInstanceOf(RemoteException.class);
        assertThat(error).hasMessageThat().contains("died");
    }

    @Test
    public void timeoutWaitingForBoundService() throws Exception {
        final PackageManager packageManager = mock(PackageManager.class);
        doReturn(List.of(makeResolveInfo(FAKE_SERVICE, FAKE_PACKAGE))).when(
                packageManager).queryIntentServices(any(), any());

        final Context context = mock(Context.class);
        doReturn(packageManager).when(context).getPackageManager();

        // This sets up the mock to report that it's binding the service, but it's not. So the
        // RegistrationProxy class should timeout while waiting for callbacks that will never come.
        doReturn(true).when(context).bindServiceAsUser(any(), any(), anyInt(), any());

        final var receiver = new ErrorReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                receiver);
        mExecutor.shutdown();
        assertThat(receiver.waitForError()).isInstanceOf(TimeoutException.class);
    }

    @Test
    public void getRegistrationBinderReturnsSuccess() throws Exception {
        final Context context = mock(Context.class);
        createMockRegistrationForComponent(context, FAKE_COMPONENT, FAKE_IRPC, FAKE_CALLER_UID);

        final var receiver = new ResultReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                receiver);
        mExecutor.shutdown();
        assertThat(receiver.waitForResult()).isNotNull();
    }

    @Test
    public void getRegistrationBinderReturnsError() throws Exception {
        final String errorString = "oh noes!";
        final IRemoteProvisioning.Stub remoteProvisioning = mock(IRemoteProvisioning.Stub.class);
        doAnswer(answerVoid((unusedCallerUid, unusedIrpcName, callbackHandler) -> {
            ((IGetRegistrationCallback) callbackHandler).onError(errorString);
        })).when(remoteProvisioning).getRegistration(eq(FAKE_CALLER_UID), eq(FAKE_IRPC), any());

        final Context context = mock(Context.class);
        addBoundSystemServiceToContext(context, FAKE_COMPONENT, remoteProvisioning);

        final var receiver = new ErrorReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                receiver);
        mExecutor.shutdown();
        final Exception error = receiver.waitForError();
        assertThat(error).isInstanceOf(RemoteException.class);
        assertThat(error).hasMessageThat().isEqualTo(errorString);
    }

    @Test
    public void getKeyAsyncSuccess() throws Exception {
        final Context context = mock(Context.class);
        final IRegistration mockIRegistration =
                createMockRegistrationForComponent(context, FAKE_COMPONENT, FAKE_IRPC,
                        FAKE_CALLER_UID);

        final int fakeKeyId = 31415;
        final RemotelyProvisionedKey fakeKey = new RemotelyProvisionedKey();
        fakeKey.keyBlob = new byte[]{1, 2, 3, 4, 5, 6};
        fakeKey.encodedCertChain = new byte[]{8, 6, 7, 5, 3, 0, 9};

        doAnswer(answerVoid((keyId, callback) -> ((IGetKeyCallback) callback).onSuccess(fakeKey)))
                .when(mockIRegistration).getKey(eq(fakeKeyId), any());

        final var registrationReceiver = new ResultReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                registrationReceiver);
        final RegistrationProxy registration = registrationReceiver.waitForResult();

        final var keyReceiver =
                new ResultReceiver<android.security.rkp.service.RemotelyProvisionedKey>();
        registration.getKeyAsync(fakeKeyId, new CancellationSignal(), mExecutor, keyReceiver);
        mExecutor.shutdown();
        final android.security.rkp.service.RemotelyProvisionedKey key = keyReceiver.waitForResult();
        assertThat(key.getKeyBlob()).isEqualTo(fakeKey.keyBlob);
        assertThat(key.getEncodedCertChain()).isEqualTo(fakeKey.encodedCertChain);
    }

    @Test
    public void getKeyAsyncCancelRequest() throws Exception {
        final Context context = mock(Context.class);
        final IRegistration mockIRegistration =
                createMockRegistrationForComponent(context, FAKE_COMPONENT, FAKE_IRPC,
                        FAKE_CALLER_UID);

        doAnswer(answerVoid(callback -> ((IGetKeyCallback) callback).onCancel()))
                .when(mockIRegistration).cancelGetKey(any());

        final var registrationReceiver = new ResultReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                registrationReceiver);
        final RegistrationProxy registration = registrationReceiver.waitForResult();

        final var cancelSignal = new CancellationSignal();
        final var errorReceiver =
                new ErrorReceiver<android.security.rkp.service.RemotelyProvisionedKey>();
        registration.getKeyAsync(123, cancelSignal, mExecutor, errorReceiver);
        cancelSignal.cancel();
        mExecutor.shutdown();
        assertThat(errorReceiver.waitForError()).isInstanceOf(OperationCanceledException.class);
    }

    @Test
    public void getKeyAsyncCancelAfterComplete() throws Exception {
        final Context context = mock(Context.class);
        final IRegistration mockIRegistration =
                createMockRegistrationForComponent(context, FAKE_COMPONENT, FAKE_IRPC,
                        FAKE_CALLER_UID);

        doAnswer(answerVoid((keyId, callback) ->
                ((IGetKeyCallback) callback).onSuccess(new RemotelyProvisionedKey())))
                .when(mockIRegistration).getKey(anyInt(), any());

        final var registrationReceiver = new ResultReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                registrationReceiver);
        final RegistrationProxy registration = registrationReceiver.waitForResult();

        final var cancelSignal = new CancellationSignal();
        final var keyReceiver =
                new ResultReceiver<android.security.rkp.service.RemotelyProvisionedKey>();
        registration.getKeyAsync(123, cancelSignal, mExecutor, keyReceiver);
        assertThat(keyReceiver.waitForResult()).isNotNull();

        cancelSignal.cancel();

        // Ensure we don't miss any queued up cancellation tasks that might be in flight
        mExecutor.shutdown();

        verify(mockIRegistration, never()).cancelGetKey(any());
    }

    @Test
    public void getKeyAsyncHandleError() throws Exception {
        final Context context = mock(Context.class);
        final IRegistration mockIRegistration =
                createMockRegistrationForComponent(context, FAKE_COMPONENT, FAKE_IRPC,
                        FAKE_CALLER_UID);

        final byte errorCode = IGetKeyCallback.Error.ERROR_PERMANENT;
        final String errorMsg = "oopsie, it didn't work";
        doAnswer(answerVoid((keyId, callback) ->
                ((IGetKeyCallback) callback).onError(errorCode, errorMsg)))
                .when(mockIRegistration).getKey(anyInt(), any());

        final var registrationReceiver = new ResultReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                registrationReceiver);
        final RegistrationProxy registration = registrationReceiver.waitForResult();

        final var errorReceiver =
                new ErrorReceiver<android.security.rkp.service.RemotelyProvisionedKey>();
        registration.getKeyAsync(1234, new CancellationSignal(), mExecutor, errorReceiver);
        mExecutor.shutdown();
        final Exception error = errorReceiver.waitForError();
        assertThat(error).isInstanceOf(RkpProxyException.class);
        assertThat(error).hasMessageThat().contains("ERROR_PERMANENT");
        assertThat(error).hasMessageThat().contains(errorMsg);
    }

    @Test
    public void getKeyAsyncCorrectlyMapsErrorCodes() throws Exception {
        final Context context = mock(Context.class);
        final IRegistration mockIRegistration =
                createMockRegistrationForComponent(context, FAKE_COMPONENT, FAKE_IRPC,
                        FAKE_CALLER_UID);

        Map<Byte, Integer> errorConversions = Map.of(
                IGetKeyCallback.Error.ERROR_UNKNOWN,
                RkpProxyException.ERROR_UNKNOWN,
                IGetKeyCallback.Error.ERROR_PENDING_INTERNET_CONNECTIVITY,
                RkpProxyException.ERROR_PENDING_INTERNET_CONNECTIVITY,
                IGetKeyCallback.Error.ERROR_REQUIRES_SECURITY_PATCH,
                RkpProxyException.ERROR_REQUIRES_SECURITY_PATCH,
                IGetKeyCallback.Error.ERROR_PERMANENT,
                RkpProxyException.ERROR_PERMANENT);
        for (Map.Entry<Byte, Integer> entry: errorConversions.entrySet()) {
            doAnswer(answerVoid((keyId, callback) ->
                    ((IGetKeyCallback) callback).onError(entry.getKey(), "")))
                    .when(mockIRegistration).getKey(anyInt(), any());

            final var registrationReceiver = new ResultReceiver<RegistrationProxy>();
            RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT,
                    mExecutor,
                    registrationReceiver);
            final RegistrationProxy registration = registrationReceiver.waitForResult();

            final var errorReceiver =
                    new ErrorReceiver<android.security.rkp.service.RemotelyProvisionedKey>();
            registration.getKeyAsync(0, new CancellationSignal(), mExecutor, errorReceiver);
            final RkpProxyException exception = (RkpProxyException) errorReceiver.waitForError();
            assertThat(exception.getError()).isEqualTo(entry.getValue());
        }
    }

    @Test
    public void storeUpgradedKeyAsyncSuccess() throws Exception {
        final byte[] oldKeyBlob = {1, 3, 5, 7, 9};
        final byte[] newKeyBlob = {2, 4, 6, 8};

        final Context context = mock(Context.class);
        final IRegistration mockIRegistration =
                createMockRegistrationForComponent(context, FAKE_COMPONENT, FAKE_IRPC,
                        FAKE_CALLER_UID);
        doAnswer(
                answerVoid((byte[] oldBlob, byte[] newBlob, IStoreUpgradedKeyCallback callback) ->
                        callback.onSuccess()))
                .when(mockIRegistration)
                .storeUpgradedKeyAsync(eq(oldKeyBlob), eq(newKeyBlob), any());

        final var registrationReceiver = new ResultReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                registrationReceiver);
        final RegistrationProxy registration = registrationReceiver.waitForResult();

        final OutcomeReceiver<Void, Exception> receiver = mock(OutcomeReceiver.class);
        registration.storeUpgradedKeyAsync(oldKeyBlob, newKeyBlob, mExecutor, receiver);
        verify(receiver, timeout(MAX_TIMEOUT.toMillis())).onResult(null);
        verifyNoMoreInteractions(receiver);
    }

    @Test
    public void storeUpgradedKeyAsyncHandleErrorCallback() throws Exception {
        final byte[] oldKeyBlob = {42};
        final byte[] newKeyBlob = {};

        final Context context = mock(Context.class);
        final IRegistration mockIRegistration =
                createMockRegistrationForComponent(context, FAKE_COMPONENT, FAKE_IRPC,
                        FAKE_CALLER_UID);
        doAnswer(
                answerVoid((byte[] oldBlob, byte[] newBlob, IStoreUpgradedKeyCallback callback) ->
                        callback.onError("BAD BAD NOT GOOD")))
                .when(mockIRegistration)
                .storeUpgradedKeyAsync(eq(oldKeyBlob), eq(newKeyBlob), any());

        final var registrationReceiver = new ResultReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                registrationReceiver);
        final RegistrationProxy registration = registrationReceiver.waitForResult();

        final OutcomeReceiver<Void, Exception> receiver = mock(OutcomeReceiver.class);
        registration.storeUpgradedKeyAsync(oldKeyBlob, newKeyBlob, mExecutor, receiver);
        verify(receiver, timeout(MAX_TIMEOUT.toMillis()))
                .onError(argThat(e -> e.getMessage().equals("BAD BAD NOT GOOD")));
        verifyNoMoreInteractions(receiver);
    }

    @Test
    public void storeUpgradedKeyAsyncHandlesRemoteExceptions() throws Exception {
        final Context context = mock(Context.class);
        final IRegistration mockIRegistration =
                createMockRegistrationForComponent(context, FAKE_COMPONENT, FAKE_IRPC,
                        FAKE_CALLER_UID);
        doThrow(new RemoteException("FAIL"))
                .when(mockIRegistration)
                .storeUpgradedKeyAsync(any(), any(), any());

        final var registrationReceiver = new ResultReceiver<RegistrationProxy>();
        RegistrationProxy.createAsync(context, FAKE_CALLER_UID, FAKE_IRPC, BIND_TIMEOUT, mExecutor,
                registrationReceiver);
        final RegistrationProxy registration = registrationReceiver.waitForResult();

        final OutcomeReceiver<Void, Exception> receiver = mock(OutcomeReceiver.class);
        assertThrows(RuntimeException.class,
                () -> registration.storeUpgradedKeyAsync(new byte[0], new byte[0], mExecutor,
                        receiver));
    }

    /** Mock up the given binder as a bound service for the given name. */
    private void addBoundSystemServiceToContext(
            Context context, ComponentName name, IBinder binder) {
        // This ensures that the caller sees the binder as local. Otherwise, it may get wrapped in
        // a proxy, which just doesn't work (cause it's not a remote binder).
        doReturn(binder).when(binder).queryLocalInterface(any());

        final PackageManager packageManager = mock(PackageManager.class);
        doReturn(List.of(makeResolveInfo(name.getClassName(), name.getPackageName()))).when(
                packageManager).queryIntentServices(any(), any());
        doReturn(packageManager).when(context).getPackageManager();

        doAnswer(answer((intent, connection, flags, user) -> {
            ((ServiceConnection) connection).onServiceConnected(name, binder);
            return true;
        })).when(context).bindServiceAsUser(
                argThat(intent -> intent.getComponent().equals(name)),
                any(ServiceConnection.class),
                eq(Context.BIND_AUTO_CREATE),
                eq(UserHandle.SYSTEM));
    }

    /**
     * Create a mock IRegistration for the given irpcName that will be returned by an
     * IRemoteProvisioning service that has the given component name. This shortcut allows tests
     * to quickly add a mock registration to their context.
     */
    private IRegistration createMockRegistrationForComponent(Context context,
            ComponentName componentName, String irpcName, int callerUid) throws Exception {
        final IRegistration mockIRegistration = mock(IRegistration.Stub.class);

        final IRemoteProvisioning.Stub remoteProvisioning = mock(IRemoteProvisioning.Stub.class);
        doAnswer(answerVoid((unusedCallerUid, unusedIrpcName, callbackHandler) ->
                ((IGetRegistrationCallback) callbackHandler).onSuccess(mockIRegistration)
        )).when(remoteProvisioning).getRegistration(eq(callerUid), eq(irpcName), any());

        addBoundSystemServiceToContext(context, componentName, remoteProvisioning);

        return mockIRegistration;
    }

    private ResolveInfo makeResolveInfo(String name, String packageName) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.name = name;
        resolveInfo.serviceInfo.applicationInfo = new ApplicationInfo();
        resolveInfo.serviceInfo.applicationInfo.packageName = packageName;
        return resolveInfo;
    }
}
