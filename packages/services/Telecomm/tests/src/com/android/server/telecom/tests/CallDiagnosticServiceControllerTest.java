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

package com.android.server.telecom.tests;

import static junit.framework.Assert.assertEquals;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.ParcelableCall;

import com.android.internal.telecom.ICallDiagnosticService;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallDiagnosticServiceController;
import com.android.server.telecom.TelecomSystem;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class CallDiagnosticServiceControllerTest {
    private static final String TEST_CDS_PACKAGE = "com.test.stuff";
    private static final String TEST_PACKAGE = "com.android.telecom.calldiagnosticservice";
    private static final String TEST_CLASS =
            "com.android.telecom.calldiagnosticservice.TestService";
    private static final ComponentName TEST_COMPONENT = new ComponentName(TEST_PACKAGE, TEST_CLASS);
    private static final List<ResolveInfo> RESOLVE_INFOS = new ArrayList<>();
    private static final ResolveInfo TEST_RESOLVE_INFO = new ResolveInfo();
    static {
        TEST_RESOLVE_INFO.serviceInfo = new ServiceInfo();
        TEST_RESOLVE_INFO.serviceInfo.packageName = TEST_PACKAGE;
        TEST_RESOLVE_INFO.serviceInfo.name = TEST_CLASS;
        TEST_RESOLVE_INFO.serviceInfo.permission = Manifest.permission.BIND_CALL_DIAGNOSTIC_SERVICE;
        RESOLVE_INFOS.add(TEST_RESOLVE_INFO);
    }
    private static final String ID_1 = "1";
    private static final String ID_2 = "2";

    @Mock
    private CallDiagnosticServiceController.ContextProxy mContextProxy;
    @Mock
    private Call mCall;
    @Mock
    private Call mCallTwo;
    @Mock
    private ICallDiagnosticService mICallDiagnosticService;
    private TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() { };

    private CallDiagnosticServiceController mCallDiagnosticService;
    private ServiceConnection mServiceConnection;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mCall.getId()).thenReturn(ID_1);
        when(mCall.isSimCall()).thenReturn(true);
        when(mCall.isExternalCall()).thenReturn(false);

        when(mCallTwo.getId()).thenReturn(ID_2);
        when(mCallTwo.isSimCall()).thenReturn(true);
        when(mCallTwo.isExternalCall()).thenReturn(false);
        mServiceConnection = null;

        // Mock out the context and other junk that we depend on.
        when(mContextProxy.queryIntentServicesAsUser(any(Intent.class), anyInt(), anyInt()))
                .thenReturn(RESOLVE_INFOS);
        when(mContextProxy.bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                anyInt(), any(UserHandle.class))).thenReturn(true);
        when(mContextProxy.getCurrentUserHandle()).thenReturn(UserHandle.CURRENT);

        mCallDiagnosticService = new CallDiagnosticServiceController(mContextProxy,
                TEST_PACKAGE, mLock);
    }

    /**
     * Verify no binding takes place for a non-sim call.
     */
    @Test
    public void testNoBindOnNonSimCall() {
        Call mockCall = Mockito.mock(Call.class);
        when(mockCall.isSimCall()).thenReturn(false);

        mCallDiagnosticService.onCallAdded(mockCall);

        verify(mContextProxy, never()).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
    }

    /**
     * Verify no binding takes place for a SIM external call.
     */
    @Test
    public void testNoBindOnExternalCall() {
        Call mockCall = Mockito.mock(Call.class);
        when(mockCall.isSimCall()).thenReturn(true);
        when(mockCall.isExternalCall()).thenReturn(true);

        mCallDiagnosticService.onCallAdded(mockCall);

        verify(mContextProxy, never()).bindServiceAsUser(any(Intent.class),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
    }

    /**
     * Verify a valid SIM call causes binding to initiate.
     */
    @Test
    public void testAddSimCallCausesBind() throws RemoteException {
        mCallDiagnosticService.onCallAdded(mCall);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<ServiceConnection> serviceConnectionCaptor = ArgumentCaptor.forClass(
                ServiceConnection.class);
        verify(mContextProxy).bindServiceAsUser(intentCaptor.capture(),
                serviceConnectionCaptor.capture(), anyInt(), any(UserHandle.class));
        assertEquals(TEST_PACKAGE, intentCaptor.getValue().getPackage());

        // Now we'll pretend bind completed and we sent back the binder.
        IBinder mockBinder = mock(IBinder.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mICallDiagnosticService);
        serviceConnectionCaptor.getValue().onServiceConnected(TEST_COMPONENT, mockBinder);
        mServiceConnection = serviceConnectionCaptor.getValue();

        // Make sure it's sent
        verify(mICallDiagnosticService).initializeDiagnosticCall(any(ParcelableCall.class));
    }

    /**
     * Verify removing the active call causes it to be removed from the CallDiagnosticService and
     * that an unbind takes place.
     */
    @Test
    public void testRemoveSimCallCausesRemoveAndUnbind() throws RemoteException {
        testAddSimCallCausesBind();
        mCallDiagnosticService.onCallRemoved(mCall);

        verify(mICallDiagnosticService).removeDiagnosticCall(eq(ID_1));
        verify(mContextProxy).unbindService(eq(mServiceConnection));
    }

    /**
     * Try to add and remove two and verify bind/unbind.
     */
    @Test
    public void testAddTwo() throws RemoteException {
        testAddSimCallCausesBind();
        mCallDiagnosticService.onCallAdded(mCallTwo);
        verify(mICallDiagnosticService, times(2)).initializeDiagnosticCall(
                any(ParcelableCall.class));

        mCallDiagnosticService.onCallRemoved(mCall);
        // Not yet!
        verify(mContextProxy, never()).unbindService(eq(mServiceConnection));

        mCallDiagnosticService.onCallRemoved(mCallTwo);

        verify(mICallDiagnosticService).removeDiagnosticCall(eq(ID_1));
        verify(mICallDiagnosticService).removeDiagnosticCall(eq(ID_2));
        verify(mContextProxy).unbindService(eq(mServiceConnection));
    }

    /**
     * Verifies we can override the call diagnostic service package to a test package (used by CTS
     * tests).
     */
    @Test
    public void testTestOverride() {
        mCallDiagnosticService.setTestCallDiagnosticService(TEST_CDS_PACKAGE);
        mCallDiagnosticService.onCallAdded(mCall);

        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContextProxy).bindServiceAsUser(intentCaptor.capture(),
                any(ServiceConnection.class), anyInt(), any(UserHandle.class));
        assertEquals(TEST_CDS_PACKAGE, intentCaptor.getValue().getPackage());
    }
}
