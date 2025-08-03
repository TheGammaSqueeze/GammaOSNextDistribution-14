/*
 * Copyright 2022 The Android Open Source Project
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

import static android.uwb.UwbManager.MESSAGE_TYPE_COMMAND;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.uwb.UwbManager.AdapterStateCallback;
import android.uwb.UwbManager.AdfProvisionStateCallback;
import android.uwb.UwbManager.UwbVendorUciCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Test of {@link UwbManager}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class UwbManagerTest {

    @Mock private Context mContext;
    @Mock private IUwbAdapter mIUwbAdapter;
    @Mock private AdapterStateCallback mAdapterStateCallback;
    @Mock private AdapterStateCallback mAdapterStateCallback2;
    @Mock private UwbVendorUciCallback mUwbVendorUciCallback;
    @Mock private UwbVendorUciCallback mUwbVendorUciCallback2;

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final String CHIP_ID = "CHIP_ID";
    private static final PersistableBundle PARAMS = new PersistableBundle();
    private static final PersistableBundle PARAMS2 = new PersistableBundle();
    private static final byte[] PAYLOAD = new byte[] {0x0, 0x1};
    private static final int MT = 1;
    private static final int GID = 9;
    private static final int OID = 1;
    private static final int UID = 343453;
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();
    private static final long TIME_NANOS = 1001;
    private static final long TIME_NANOS2 = 1002;

    private UwbManager mUwbManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getAttributionSource()).thenReturn(ATTRIBUTION_SOURCE);

        mUwbManager = new UwbManager(mContext, mIUwbAdapter);
    }

    @Test
    public void testRegisterUnregisterCallbacks() throws Exception {
        // Register/unregister AdapterStateCallbacks
        mUwbManager.registerAdapterStateCallback(EXECUTOR, mAdapterStateCallback);
        verify(mIUwbAdapter, times(1)).registerAdapterStateCallbacks(any());
        mUwbManager.registerAdapterStateCallback(EXECUTOR, mAdapterStateCallback2);
        verify(mIUwbAdapter, times(1)).registerAdapterStateCallbacks(any());
        mUwbManager.unregisterAdapterStateCallback(mAdapterStateCallback);
        verify(mIUwbAdapter, never()).unregisterAdapterStateCallbacks(any());
        mUwbManager.unregisterAdapterStateCallback(mAdapterStateCallback2);
        verify(mIUwbAdapter, times(1)).unregisterAdapterStateCallbacks(any());

        // Register/unregister UwbVendorUciCallback
        mUwbManager.registerUwbVendorUciCallback(EXECUTOR, mUwbVendorUciCallback);
        verify(mIUwbAdapter, times(1)).registerVendorExtensionCallback(any());
        mUwbManager.registerUwbVendorUciCallback(EXECUTOR, mUwbVendorUciCallback2);
        verify(mIUwbAdapter, times(1)).registerVendorExtensionCallback(any());
        mUwbManager.unregisterUwbVendorUciCallback(mUwbVendorUciCallback);
        verify(mIUwbAdapter, never()).unregisterVendorExtensionCallback(any());
        mUwbManager.unregisterUwbVendorUciCallback(mUwbVendorUciCallback2);
        verify(mIUwbAdapter, times(1)).unregisterVendorExtensionCallback(any());
    }

    @Test
    public void testGettersAndSetters() throws Exception {
        // Get SpecificationInfo
        when(mIUwbAdapter.getSpecificationInfo(/*chipId=*/ null)).thenReturn(PARAMS);
        assertThat(mUwbManager.getSpecificationInfo()).isEqualTo(PARAMS);
        when(mIUwbAdapter.getSpecificationInfo(CHIP_ID)).thenReturn(PARAMS2);
        assertThat(mUwbManager.getSpecificationInfo(CHIP_ID)).isEqualTo(PARAMS2);
        doThrow(new RemoteException()).when(mIUwbAdapter).getSpecificationInfo(/*chipId=*/ null);
        assertThrows(RuntimeException.class, () -> mUwbManager.getSpecificationInfo());

        // Get elapsedRealtimeResolutionNanos
        when(mIUwbAdapter.getTimestampResolutionNanos(/*chipId=*/ null)).thenReturn(TIME_NANOS);
        assertThat(mUwbManager.elapsedRealtimeResolutionNanos()).isEqualTo(TIME_NANOS);
        when(mIUwbAdapter.getTimestampResolutionNanos(CHIP_ID)).thenReturn(TIME_NANOS2);
        assertThat(mUwbManager.elapsedRealtimeResolutionNanos(CHIP_ID)).isEqualTo(TIME_NANOS2);
        doThrow(new RemoteException())
                .when(mIUwbAdapter)
                .getTimestampResolutionNanos(/*chipId=*/ null);
        assertThrows(RuntimeException.class, () -> mUwbManager.elapsedRealtimeResolutionNanos());

        // setUwbEnabled
        mUwbManager.setUwbEnabled(/*enabled=*/ true);
        verify(mIUwbAdapter, times(1)).setEnabled(true);

        // Get IsUwbEnabled
        when(mIUwbAdapter.getAdapterState()).thenReturn(AdapterState.STATE_ENABLED_ACTIVE);
        assertThat(mUwbManager.isUwbEnabled()).isTrue();
        when(mIUwbAdapter.getAdapterState()).thenReturn(AdapterState.STATE_ENABLED_INACTIVE);
        assertThat(mUwbManager.isUwbEnabled()).isTrue();
        when(mIUwbAdapter.getAdapterState()).thenReturn(AdapterState.STATE_DISABLED);
        assertThat(mUwbManager.isUwbEnabled()).isFalse();
        doThrow(new RemoteException()).when(mIUwbAdapter).getAdapterState();
        assertThrows(RuntimeException.class, () -> mUwbManager.isUwbEnabled());

        // getChipInfos
        when(mIUwbAdapter.getChipInfos()).thenReturn(List.of(PARAMS));
        assertThat(mUwbManager.getChipInfos()).isEqualTo(List.of(PARAMS));
        doThrow(new RemoteException()).when(mIUwbAdapter).getChipInfos();
        assertThrows(RuntimeException.class, () -> mUwbManager.getChipInfos());

        // getDefaultChipId
        when(mIUwbAdapter.getDefaultChipId()).thenReturn(CHIP_ID);
        assertThat(mUwbManager.getDefaultChipId()).isEqualTo(CHIP_ID);
        doThrow(new RemoteException()).when(mIUwbAdapter).getDefaultChipId();
        assertThrows(RuntimeException.class, () -> mUwbManager.getDefaultChipId());

        // getAllServiceProfiles
        when(mIUwbAdapter.getAllServiceProfiles()).thenReturn(PARAMS);
        assertThat(mUwbManager.getAllServiceProfiles()).isEqualTo(PARAMS);
        doThrow(new RemoteException()).when(mIUwbAdapter).getAllServiceProfiles();
        assertThrows(RuntimeException.class, () -> mUwbManager.getAllServiceProfiles());

        // getAllServiceProfiles
        when(mIUwbAdapter.getAdfProvisioningAuthorities(PARAMS)).thenReturn(PARAMS);
        assertThat(mUwbManager.getAdfProvisioningAuthorities(PARAMS)).isEqualTo(PARAMS);
        doThrow(new RemoteException()).when(mIUwbAdapter).getAdfProvisioningAuthorities(PARAMS);
        assertThrows(
                RuntimeException.class, () -> mUwbManager.getAdfProvisioningAuthorities(PARAMS));

        // getAdfCertificateInfo
        when(mIUwbAdapter.getAdfCertificateAndInfo(PARAMS)).thenReturn(PARAMS);
        assertThat(mUwbManager.getAdfCertificateInfo(PARAMS)).isEqualTo(PARAMS);
        doThrow(new RemoteException()).when(mIUwbAdapter).getAdfCertificateAndInfo(PARAMS);
        assertThrows(RuntimeException.class, () -> mUwbManager.getAdfCertificateInfo(PARAMS));
    }

    @Test
    public void testOpenRangingSession() throws Exception {
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        // null chip id
        mUwbManager.openRangingSession(PARAMS, EXECUTOR, callback);
        verify(mIUwbAdapter, times(1))
                .openRanging(eq(ATTRIBUTION_SOURCE), any(), any(), eq(PARAMS), eq(null));

        // Chip id not on valid list
        assertThrows(
                IllegalArgumentException.class,
                () -> mUwbManager.openRangingSession(PARAMS, EXECUTOR, callback, CHIP_ID));

        // Chip id on valid list
        when(mIUwbAdapter.getChipIds()).thenReturn(List.of(CHIP_ID));
        mUwbManager.openRangingSession(PARAMS, EXECUTOR, callback, CHIP_ID);
        verify(mIUwbAdapter, times(1))
                .openRanging(eq(ATTRIBUTION_SOURCE), any(), any(), eq(PARAMS), eq(CHIP_ID));
    }

    @Test
    public void testAddServiceProfile() throws Exception {
        when(mIUwbAdapter.addServiceProfile(PARAMS)).thenReturn(PARAMS);
        assertThat(mUwbManager.addServiceProfile(PARAMS)).isEqualTo(PARAMS);
        doThrow(new RemoteException()).when(mIUwbAdapter).addServiceProfile(PARAMS);
        assertThrows(RuntimeException.class, () -> mUwbManager.addServiceProfile(PARAMS));
    }

    @Test
    public void testRemoveServiceProfile() throws Exception {
        when(mIUwbAdapter.removeServiceProfile(PARAMS))
                .thenReturn(UwbManager.REMOVE_SERVICE_PROFILE_SUCCESS);
        assertThat(mUwbManager.removeServiceProfile(PARAMS))
                .isEqualTo(UwbManager.REMOVE_SERVICE_PROFILE_SUCCESS);
        doThrow(new RemoteException()).when(mIUwbAdapter).removeServiceProfile(PARAMS);
        assertThrows(RuntimeException.class, () -> mUwbManager.removeServiceProfile(PARAMS));
    }

    @Test
    public void testProvisionProfileAdfByScript() throws Exception {
        AdfProvisionStateCallback cb = mock(AdfProvisionStateCallback.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> mUwbManager.provisionProfileAdfByScript(PARAMS, /*executor=*/ null, cb));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mUwbManager.provisionProfileAdfByScript(
                                PARAMS, EXECUTOR, /*callback=*/ null));
        doThrow(new RemoteException())
                .when(mIUwbAdapter)
                .provisionProfileAdfByScript(eq(PARAMS), any());
        assertThrows(
                RuntimeException.class,
                () -> mUwbManager.provisionProfileAdfByScript(PARAMS, EXECUTOR, cb));
    }

    @Test
    public void testRemoveProfileAdf() throws Exception {
        when(mIUwbAdapter.removeProfileAdf(PARAMS))
                .thenReturn(UwbManager.REMOVE_PROFILE_ADF_SUCCESS);
        assertThat(mUwbManager.removeProfileAdf(PARAMS))
                .isEqualTo(UwbManager.REMOVE_PROFILE_ADF_SUCCESS);
        doThrow(new RemoteException()).when(mIUwbAdapter).removeProfileAdf(PARAMS);
        assertThrows(RuntimeException.class, () -> mUwbManager.removeProfileAdf(PARAMS));
    }

    @Test
    public void testSendVendorUciMessage() throws Exception {
        when(mIUwbAdapter.sendVendorUciMessage(MESSAGE_TYPE_COMMAND, GID, OID, PAYLOAD))
                .thenReturn(UwbManager.SEND_VENDOR_UCI_SUCCESS);
        assertThat(mUwbManager.sendVendorUciMessage(GID, OID, PAYLOAD))
                .isEqualTo(UwbManager.SEND_VENDOR_UCI_SUCCESS);
        doThrow(new RemoteException()).when(mIUwbAdapter).sendVendorUciMessage(MESSAGE_TYPE_COMMAND,
                GID, OID, PAYLOAD);
        assertThrows(
                RuntimeException.class, () -> mUwbManager.sendVendorUciMessage(GID, OID, PAYLOAD));
    }

    @Test
    public void testSendVendorUciMessageWithMessageType() throws Exception {
        when(mIUwbAdapter.sendVendorUciMessage(MT, GID, OID, PAYLOAD))
                .thenReturn(UwbManager.SEND_VENDOR_UCI_SUCCESS);
        assertThat(mUwbManager.sendVendorUciMessage(MT, GID, OID, PAYLOAD))
                .isEqualTo(UwbManager.SEND_VENDOR_UCI_SUCCESS);
        doThrow(new RemoteException()).when(mIUwbAdapter).sendVendorUciMessage(MT, GID, OID,
                PAYLOAD);
        assertThrows(
                RuntimeException.class, () -> mUwbManager.sendVendorUciMessage(MT, GID, OID,
                        PAYLOAD));
    }

    @Test
    public void testGetUwbActivityEnergyInfoAsync() throws Exception {
        Consumer<UwbActivityEnergyInfo> listener = mock(Consumer.class);
        // null Executor
        assertThrows(NullPointerException.class,
                () -> mUwbManager.getUwbActivityEnergyInfoAsync(null, listener));
        // null listener
        assertThrows(NullPointerException.class,
                () -> mUwbManager.getUwbActivityEnergyInfoAsync(EXECUTOR, null));

        mUwbManager.getUwbActivityEnergyInfoAsync(EXECUTOR, listener);
        verify(mIUwbAdapter).getUwbActivityEnergyInfoAsync(
                any(IOnUwbActivityEnergyInfoListener.Stub.class));
    }
}
