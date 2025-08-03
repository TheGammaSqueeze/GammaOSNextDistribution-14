/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wifi;

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.os.WorkSource;

import androidx.test.filters.SmallTest;

import com.android.server.wifi.ActiveModeWarden.ModeChangeCallback;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/** Unit tests for {@link MakeBeforeBreakManager}. */
@SmallTest
public class MakeBeforeBreakManagerTest extends WifiBaseTest {

    @Mock private ActiveModeWarden mActiveModeWarden;
    @Mock private FrameworkFacade mFrameworkFacade;
    @Mock private Context mContext;
    @Mock private ConcreteClientModeManager mOldPrimaryCmm;
    @Mock private ConcreteClientModeManager mNewPrimaryCmm;
    @Mock private ConcreteClientModeManager mUnrelatedCmm;
    @Mock private WorkSource mSettingsWorkSource;
    @Mock private ClientModeImplMonitor mCmiMonitor;
    @Mock private ClientModeManagerBroadcastQueue mBroadcastQueue;
    @Mock private WifiMetrics mWifiMetrics;
    @Mock private Runnable mOnStoppedListener;
    @Captor private ArgumentCaptor<ModeChangeCallback> mModeChangeCallbackCaptor;
    @Captor private ArgumentCaptor<ClientModeImplListener> mCmiListenerCaptor;

    private MakeBeforeBreakManager mMbbManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mActiveModeWarden.isStaStaConcurrencySupportedForMbb()).thenReturn(true);
        when(mFrameworkFacade.getSettingsWorkSource(mContext)).thenReturn(mSettingsWorkSource);

        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(mNewPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);

        when(mActiveModeWarden.getPrimaryClientModeManagerNullable()).thenReturn(mOldPrimaryCmm);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm));

        mMbbManager = new MakeBeforeBreakManager(mActiveModeWarden, mFrameworkFacade, mContext,
                mCmiMonitor, mBroadcastQueue, mWifiMetrics);
        mMbbManager.setVerboseLoggingEnabled(true);

        verify(mActiveModeWarden).registerModeChangeCallback(mModeChangeCallbackCaptor.capture());
        verify(mCmiMonitor).registerListener(mCmiListenerCaptor.capture());
    }

    @Test
    public void makeBeforeBreakDisabled_noOp() {
        when(mActiveModeWarden.isStaStaConcurrencySupportedForMbb()).thenReturn(false);

        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mNewPrimaryCmm);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mNewPrimaryCmm);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mNewPrimaryCmm);

        verify(mNewPrimaryCmm, never()).setRole(any(), any());
    }

    @Test
    public void onL3ValidatedSecondaryTransientThatWasPreviouslyPrimary_noOp() {
        when(mNewPrimaryCmm.getPreviousRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);

        verify(mNewPrimaryCmm, never()).setRole(any(), any());
    }

    @Test
    public void onL3ValidatedNonSecondaryTransient_noOp() {
        when(mNewPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);

        verify(mNewPrimaryCmm, never()).setRole(any(), any());
    }

    @Test
    public void onL3Validated_noPrimary_immediatelyMakeValidatedNetworkPrimary() {
        when(mActiveModeWarden.getPrimaryClientModeManagerNullable()).thenReturn(null);
        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);

        verify(mNewPrimaryCmm).setRole(ROLE_CLIENT_PRIMARY, mSettingsWorkSource);
    }

    @Test
    public void makeBeforeBreakSuccess() {
        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);

        verify(mOldPrimaryCmm).setRole(ROLE_CLIENT_SECONDARY_TRANSIENT,
                ActiveModeWarden.INTERNAL_REQUESTOR_WS);
        verify(mBroadcastQueue).fakeDisconnectionBroadcasts();

        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm, mOldPrimaryCmm));
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);

        verify(mNewPrimaryCmm).setRole(ROLE_CLIENT_PRIMARY, mSettingsWorkSource);
        verify(mOldPrimaryCmm).setShouldReduceNetworkScore(true);
    }

    @Test
    public void makeBeforeBreakEnded_mMakeBeforeBreakInfoCleared() {
        makeBeforeBreakSuccess();
        // only called once
        verify(mOldPrimaryCmm).setRole(any(), any());
        verify(mNewPrimaryCmm).setRole(any(), any());

        when(mActiveModeWarden.getPrimaryClientModeManagerNullable()).thenReturn(mNewPrimaryCmm);
        when(mNewPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mOldPrimaryCmm));
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);

        // still only called once
        verify(mOldPrimaryCmm).setRole(any(), any());
        verify(mNewPrimaryCmm).setRole(any(), any());
    }

    @Test
    public void modeChanged_anotherCmm_noOp() {
        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);

        verify(mOldPrimaryCmm).setRole(ROLE_CLIENT_SECONDARY_TRANSIENT,
                ActiveModeWarden.INTERNAL_REQUESTOR_WS);

        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm, mOldPrimaryCmm));
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mUnrelatedCmm);

        verify(mUnrelatedCmm, never()).setRole(any(), any());
        verify(mOldPrimaryCmm).setRole(any(), any());
        verify(mNewPrimaryCmm, never()).setRole(any(), any());
    }

    @Test
    public void modeChanged_noMakeBeforeBreak_noOp() {
        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm, mOldPrimaryCmm));
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);

        verify(mOldPrimaryCmm, never()).setRole(any(), any());
        verify(mNewPrimaryCmm, never()).setRole(any(), any());
    }

    @Test
    public void modeChanged_oldPrimaryDidntBecomeSecondaryTransient_abortMbb() {
        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);

        verify(mOldPrimaryCmm).setRole(ROLE_CLIENT_SECONDARY_TRANSIENT,
                ActiveModeWarden.INTERNAL_REQUESTOR_WS);

        // didn't become SECONDARY_TRANSIENT
        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm));
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);

        // no-op, abort MBB
        verify(mOldPrimaryCmm).setRole(any(), any());
        verify(mNewPrimaryCmm, never()).setRole(any(), any());

        // became SECONDARY_TRANSIENT
        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm, mOldPrimaryCmm));
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);

        // but since aborted, still no-op
        verify(mOldPrimaryCmm).setRole(any(), any());
        verify(mNewPrimaryCmm, never()).setRole(any(), any());
    }

    @Test
    public void modeChanged_newPrimaryNoLongerSecondaryTransient_abortMbb() {
        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);

        verify(mOldPrimaryCmm).setRole(ROLE_CLIENT_SECONDARY_TRANSIENT,
                ActiveModeWarden.INTERNAL_REQUESTOR_WS);

        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        // new primary's role became something else
        when(mNewPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_LONG_LIVED);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mOldPrimaryCmm));
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);

        // no-op, abort MBB
        verify(mOldPrimaryCmm).setRole(any(), any());
        verify(mNewPrimaryCmm, never()).setRole(any(), any());

        // both became SECONDARY_TRANSIENT
        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mNewPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm, mOldPrimaryCmm));
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);

        // but since aborted, still no-op
        verify(mOldPrimaryCmm).setRole(any(), any());
        verify(mNewPrimaryCmm, never()).setRole(any(), any());
    }

    @Test
    public void recovery() {
        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm, mOldPrimaryCmm));
        when(mActiveModeWarden.getPrimaryClientModeManagerNullable()).thenReturn(null);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);

        verify(mNewPrimaryCmm).setRole(ROLE_CLIENT_PRIMARY, mSettingsWorkSource);
        verify(mOldPrimaryCmm).stop();
    }

    @Test
    public void captivePortalDetected_disconnectOldPrimary_makeCaptivePortalPrimary() {
        // captive portal network detected on new primary CMM
        mCmiListenerCaptor.getValue().onCaptivePortalDetected(mNewPrimaryCmm);

        // we should disable wifi state change broadcast before stopping it.
        verify(mOldPrimaryCmm).setWifiStateChangeBroadcastEnabled(false);

        // we should stop the old primary
        verify(mOldPrimaryCmm).stop();

        // old primary destroyed
        when(mOldPrimaryCmm.getRole()).thenReturn(null);
        // currently no primary
        when(mActiveModeWarden.getPrimaryClientModeManagerNullable()).thenReturn(null);

        // trigger old primary removed callback
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mOldPrimaryCmm);

        // ensure we make the captive portal network primary
        verify(mNewPrimaryCmm).setRole(ROLE_CLIENT_PRIMARY, mSettingsWorkSource);
    }

    @Test
    public void stopAllSecondaryTransientCmms_noSecondaryTransientCmm_triggerImmediately() {
        when(mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(null);

        mMbbManager.stopAllSecondaryTransientClientModeManagers(mOnStoppedListener);

        verify(mOnStoppedListener).run();
    }

    @Test
    public void stopAllSecondaryTransientCmms_hasSecondaryTransientCmm_triggerAfterStopped() {
        when(mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(mNewPrimaryCmm);

        mMbbManager.stopAllSecondaryTransientClientModeManagers(mOnStoppedListener);

        verify(mOnStoppedListener, never()).run();

        when(mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(null);

        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mNewPrimaryCmm);

        verify(mOnStoppedListener).run();
    }

    @Test
    public void onInternetValidationSuccessful() {
        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm, mOldPrimaryCmm));

        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mNewPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_SECONDARY_TRANSIENT_CREATED,
                mMbbManager.getInternalState());
        mCmiListenerCaptor.getValue().onCaptivePortalDetected(mNewPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_CAPTIVE_PORTAL_DETECTED,
                mMbbManager.getInternalState());
        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_INTERNET_VALIDATED,
                mMbbManager.getInternalState());
        verify(mOldPrimaryCmm).setRole(ROLE_CLIENT_SECONDARY_TRANSIENT,
                ActiveModeWarden.INTERNAL_REQUESTOR_WS);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_ROLES_BEING_SWITCHED_BOTH_SECONDARY_TRANSIENT,
                mMbbManager.getInternalState());
        verify(mNewPrimaryCmm).setRole(ROLE_CLIENT_PRIMARY, mSettingsWorkSource);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_ROLE_SWITCH_COMPLETE,
                mMbbManager.getInternalState());
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mOldPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_NONE,
                mMbbManager.getInternalState());

        verify(mOldPrimaryCmm).setShouldReduceNetworkScore(true);
    }

    @Test
    public void onInternetValidationFailedSecondaryTransient() {
        when(mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(mNewPrimaryCmm);
        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        mCmiListenerCaptor.getValue().onInternetValidationFailed(mNewPrimaryCmm, true);
        verify(mNewPrimaryCmm).disconnect();
        verify(mActiveModeWarden).removeClientModeManager(eq(mNewPrimaryCmm));
        assertEquals(MakeBeforeBreakManager.MBB_STATE_VALIDATION_FAILED,
                mMbbManager.getInternalState());
    }

    @Test
    public void onInternetValidationFailedWhenLingering() {
        when(mOldPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_SECONDARY_TRANSIENT);
        when(mActiveModeWarden.getClientModeManagersInRoles(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(List.of(mNewPrimaryCmm, mOldPrimaryCmm));

        mModeChangeCallbackCaptor.getValue().onActiveModeManagerAdded(mNewPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_SECONDARY_TRANSIENT_CREATED,
                mMbbManager.getInternalState());
        mCmiListenerCaptor.getValue().onCaptivePortalDetected(mNewPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_CAPTIVE_PORTAL_DETECTED,
                mMbbManager.getInternalState());
        mCmiListenerCaptor.getValue().onInternetValidated(mNewPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_INTERNET_VALIDATED,
                mMbbManager.getInternalState());
        verify(mOldPrimaryCmm).setRole(ROLE_CLIENT_SECONDARY_TRANSIENT,
                ActiveModeWarden.INTERNAL_REQUESTOR_WS);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_ROLES_BEING_SWITCHED_BOTH_SECONDARY_TRANSIENT,
                mMbbManager.getInternalState());
        verify(mNewPrimaryCmm).setRole(ROLE_CLIENT_PRIMARY, mSettingsWorkSource);
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRoleChanged(mOldPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_ROLE_SWITCH_COMPLETE,
                mMbbManager.getInternalState());
        // Internet was lost when lingering
        when(mActiveModeWarden.getClientModeManagerInRole(ROLE_CLIENT_SECONDARY_TRANSIENT))
                .thenReturn(mOldPrimaryCmm);
        when(mNewPrimaryCmm.getRole()).thenReturn(ROLE_CLIENT_PRIMARY);
        mCmiListenerCaptor.getValue().onInternetValidationFailed(mNewPrimaryCmm, true);
        verify(mOldPrimaryCmm).setRole(eq(ROLE_CLIENT_PRIMARY), any());
        verify(mNewPrimaryCmm).setRole(eq(ROLE_CLIENT_SECONDARY_TRANSIENT), any());
        verify(mNewPrimaryCmm).disconnect();
        verify(mActiveModeWarden).removeClientModeManager(eq(mNewPrimaryCmm));
        mModeChangeCallbackCaptor.getValue().onActiveModeManagerRemoved(mOldPrimaryCmm);
        assertEquals(MakeBeforeBreakManager.MBB_STATE_NONE,
                mMbbManager.getInternalState());

        verify(mOldPrimaryCmm).setShouldReduceNetworkScore(true);
    }
}
