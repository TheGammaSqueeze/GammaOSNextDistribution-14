/*
 * Copyright 2020 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.modules.utils.build.SdkLevel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Test of {@link RangingManager}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RangingManagerTest {

    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final PersistableBundle PARAMS = new PersistableBundle();
    private static final @RangingChangeReason int REASON = RangingChangeReason.UNKNOWN;
    private static final UwbAddress ADDRESS = UwbAddress.fromBytes(new byte[] {0x0, 0x1});
    private static final byte[] DATA = new byte[] {0x0, 0x1};
    private static final int UID = Process.myUid();
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();
    private static final String VALID_CHIP_ID = "validChipId";
    private static final int HANDLE_ID = 12;
    private static final int PID = Process.myPid();

    @Test
    public void testOpenSession_OpenRangingInvoked() throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingManager rangingManager = new RangingManager(adapter);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback, /* chipIds= */ null);
        verify(adapter, times(1))
                .openRanging(eq(ATTRIBUTION_SOURCE), any(), any(), any(), eq(/* chipId= */ null));
    }

    @Test
    public void testOpenSession_validChipId_OpenRangingInvoked() throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        when(adapter.getChipIds()).thenReturn(List.of(VALID_CHIP_ID));
        RangingManager rangingManager = new RangingManager(adapter);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        rangingManager.openSession(ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback, VALID_CHIP_ID);
        verify(adapter, times(1))
                .openRanging(eq(ATTRIBUTION_SOURCE), any(), any(), any(), eq(VALID_CHIP_ID));
    }

    @Test
    public void testOpenSession_validChipId_RuntimeException() throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        doThrow(new RemoteException())
                .when(adapter)
                .openRanging(eq(ATTRIBUTION_SOURCE), any(), any(), any(), eq(VALID_CHIP_ID));
        Mockito.when(adapter.getChipIds()).thenReturn(List.of(VALID_CHIP_ID));
        RangingManager rangingManager = new RangingManager(adapter);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        assertThrows(
                RuntimeException.class,
                () ->
                        rangingManager.openSession(
                                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback, VALID_CHIP_ID));
    }

    @Test
    public void testOpenSession_invalidChipId_IllegalArgumentException() throws RemoteException {
        String invalidChipId = "invalidChipId";
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        Mockito.when(adapter.getChipIds()).thenReturn(List.of(VALID_CHIP_ID));
        RangingManager rangingManager = new RangingManager(adapter);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        rangingManager.openSession(
                                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback, invalidChipId));
        verify(adapter, times(0))
                .openRanging(eq(ATTRIBUTION_SOURCE), any(), any(), any(), eq(invalidChipId));
    }

    @Test
    public void testOnRangingOpened_InvalidSessionHandle() throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingManager rangingManager = new RangingManager(adapter);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);

        rangingManager.onRangingOpened(new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID));
        verify(callback, times(0)).onOpened(any());
    }

    @Test
    public void testOnRangingOpened_MultipleSessionsRegistered() throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession.Callback callback1 = mock(RangingSession.Callback.class);
        RangingSession.Callback callback2 = mock(RangingSession.Callback.class);
        ArgumentCaptor<SessionHandle> sessionHandleCaptor =
                ArgumentCaptor.forClass(SessionHandle.class);

        RangingManager rangingManager = new RangingManager(adapter);
        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback1, /* chipIds= */ null);
        verify(adapter, times(1))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        SessionHandle sessionHandle1 = sessionHandleCaptor.getValue();

        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback2, /* chipIds= */ null);
        verify(adapter, times(2))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        SessionHandle sessionHandle2 = sessionHandleCaptor.getValue();

        rangingManager.onRangingOpened(sessionHandle1);
        verify(callback1, times(1)).onOpened(any());
        verify(callback2, times(0)).onOpened(any());

        rangingManager.onRangingOpened(sessionHandle2);
        verify(callback1, times(1)).onOpened(any());
        verify(callback2, times(1)).onOpened(any());
    }

    @Test
    public void testCorrectCallbackInvoked() throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingManager rangingManager = new RangingManager(adapter);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);

        ArgumentCaptor<SessionHandle> sessionHandleCaptor =
                ArgumentCaptor.forClass(SessionHandle.class);

        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback, /* chipIds= */ null);
        verify(adapter, times(1))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        SessionHandle handle = sessionHandleCaptor.getValue();

        rangingManager.onRangingOpened(handle);
        verify(callback, times(1)).onOpened(any());

        rangingManager.onRangingStarted(handle, PARAMS);
        verify(callback, times(1)).onStarted(eq(PARAMS));

        rangingManager.onRangingStartFailed(handle, REASON, PARAMS);
        verify(callback, times(1)).onStartFailed(eq(REASON), eq(PARAMS));

        RangingReport report = UwbTestUtils.getRangingReports(1);
        rangingManager.onRangingResult(handle, report);
        verify(callback, times(1)).onReportReceived(eq(report));

        rangingManager.onRangingReconfigured(handle, PARAMS);
        verify(callback, times(1)).onReconfigured(eq(PARAMS));

        rangingManager.onRangingReconfigureFailed(handle, REASON, PARAMS);
        verify(callback, times(1)).onReconfigureFailed(eq(REASON), eq(PARAMS));

        rangingManager.onRangingStopped(handle, REASON, PARAMS);
        verify(callback, times(1)).onStopped(eq(REASON), eq(PARAMS));

        rangingManager.onRangingStopFailed(handle, REASON, PARAMS);
        verify(callback, times(1)).onStopFailed(eq(REASON), eq(PARAMS));

        rangingManager.onControleeAdded(handle, PARAMS);
        verify(callback, times(1)).onControleeAdded(eq(PARAMS));

        rangingManager.onControleeAddFailed(handle, REASON, PARAMS);
        verify(callback, times(1)).onControleeAddFailed(eq(REASON), eq(PARAMS));

        rangingManager.onControleeRemoved(handle, PARAMS);
        verify(callback, times(1)).onControleeRemoved(eq(PARAMS));

        rangingManager.onControleeRemoveFailed(handle, REASON, PARAMS);
        verify(callback, times(1)).onControleeRemoveFailed(eq(REASON), eq(PARAMS));

        rangingManager.onRangingPaused(handle, PARAMS);
        verify(callback, times(1)).onPaused(eq(PARAMS));

        rangingManager.onRangingPauseFailed(handle, REASON, PARAMS);
        verify(callback, times(1)).onPauseFailed(eq(REASON), eq(PARAMS));

        rangingManager.onRangingResumed(handle, PARAMS);
        verify(callback, times(1)).onResumed(eq(PARAMS));

        rangingManager.onRangingResumeFailed(handle, REASON, PARAMS);
        verify(callback, times(1)).onResumeFailed(eq(REASON), eq(PARAMS));

        rangingManager.onDataSent(handle, ADDRESS, PARAMS);
        verify(callback, times(1)).onDataSent(eq(ADDRESS), eq(PARAMS));

        rangingManager.onDataSendFailed(handle, ADDRESS, REASON, PARAMS);
        verify(callback, times(1)).onDataSendFailed(eq(ADDRESS), eq(REASON), eq(PARAMS));

        // Test should only run on V+ devices.
        if (SdkLevel.isAtLeastV()) {
            rangingManager.onDataTransferPhaseConfigured(handle, PARAMS);
            verify(callback, times(1)).onDataTransferPhaseConfigured(eq(PARAMS));

            rangingManager.onDataTransferPhaseConfigFailed(handle, PARAMS);
            verify(callback, times(1)).onDataTransferPhaseConfigFailed(eq(PARAMS));
        }

        rangingManager.onDataReceived(handle, ADDRESS, PARAMS, DATA);
        verify(callback, times(1)).onDataReceived(eq(ADDRESS), eq(PARAMS), eq(DATA));

        rangingManager.onDataReceiveFailed(handle, ADDRESS, REASON, PARAMS);
        verify(callback, times(1)).onDataReceiveFailed(eq(ADDRESS), eq(REASON), eq(PARAMS));

        rangingManager.onServiceDiscovered(handle, PARAMS);
        verify(callback, times(1)).onServiceDiscovered(eq(PARAMS));

        rangingManager.onServiceConnected(handle, PARAMS);
        verify(callback, times(1)).onServiceConnected(eq(PARAMS));

        // Test should only run on U+ devices.
        if (SdkLevel.isAtLeastU()) {
            rangingManager.onRangingRoundsUpdateDtTagStatus(handle, PARAMS);
            verify(callback, times(1))
                    .onRangingRoundsUpdateDtTagStatus(eq(PARAMS));
        }

        rangingManager.onRangingClosed(handle, REASON, PARAMS);
        verify(callback, times(1)).onClosed(eq(REASON), eq(PARAMS));
    }

    @Test
    public void testNoCallbackInvoked_sessionClosed() throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingManager rangingManager = new RangingManager(adapter);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);

        ArgumentCaptor<SessionHandle> sessionHandleCaptor =
                ArgumentCaptor.forClass(SessionHandle.class);

        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback, /* chipIds= */ null);
        verify(adapter, times(1))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        SessionHandle handle = sessionHandleCaptor.getValue();
        rangingManager.onRangingClosed(handle, REASON, PARAMS);
        verify(callback, times(1)).onClosed(eq(REASON), eq(PARAMS));

        rangingManager.onRangingOpened(handle);
        verify(callback, never()).onOpened(any());

        rangingManager.onRangingStarted(handle, PARAMS);
        verify(callback, never()).onStarted(eq(PARAMS));

        rangingManager.onRangingStartFailed(handle, REASON, PARAMS);
        verify(callback, never()).onStartFailed(eq(REASON), eq(PARAMS));

        RangingReport report = UwbTestUtils.getRangingReports(1);
        rangingManager.onRangingResult(handle, report);
        verify(callback, never()).onReportReceived(eq(report));

        rangingManager.onRangingReconfigured(handle, PARAMS);
        verify(callback, never()).onReconfigured(eq(PARAMS));

        rangingManager.onRangingReconfigureFailed(handle, REASON, PARAMS);
        verify(callback, never()).onReconfigureFailed(eq(REASON), eq(PARAMS));

        rangingManager.onRangingStopped(handle, REASON, PARAMS);
        verify(callback, never()).onStopped(eq(REASON), eq(PARAMS));

        rangingManager.onRangingStopFailed(handle, REASON, PARAMS);
        verify(callback, never()).onStopFailed(eq(REASON), eq(PARAMS));

        rangingManager.onControleeAdded(handle, PARAMS);
        verify(callback, never()).onControleeAdded(eq(PARAMS));

        rangingManager.onControleeAddFailed(handle, REASON, PARAMS);
        verify(callback, never()).onControleeAddFailed(eq(REASON), eq(PARAMS));

        rangingManager.onControleeRemoved(handle, PARAMS);
        verify(callback, never()).onControleeRemoved(eq(PARAMS));

        rangingManager.onControleeRemoveFailed(handle, REASON, PARAMS);
        verify(callback, never()).onControleeRemoveFailed(eq(REASON), eq(PARAMS));

        rangingManager.onRangingPaused(handle, PARAMS);
        verify(callback, never()).onPaused(eq(PARAMS));

        rangingManager.onRangingPauseFailed(handle, REASON, PARAMS);
        verify(callback, never()).onPauseFailed(eq(REASON), eq(PARAMS));

        rangingManager.onRangingResumed(handle, PARAMS);
        verify(callback, never()).onResumed(eq(PARAMS));

        rangingManager.onRangingResumeFailed(handle, REASON, PARAMS);
        verify(callback, never()).onResumeFailed(eq(REASON), eq(PARAMS));

        rangingManager.onDataSent(handle, ADDRESS, PARAMS);
        verify(callback, never()).onDataSent(eq(ADDRESS), eq(PARAMS));

        rangingManager.onDataSendFailed(handle, ADDRESS, REASON, PARAMS);
        verify(callback, never()).onDataSendFailed(eq(ADDRESS), eq(REASON), eq(PARAMS));

        rangingManager.onDataReceived(handle, ADDRESS, PARAMS, DATA);
        verify(callback, never()).onDataReceived(eq(ADDRESS), eq(PARAMS), eq(DATA));

        rangingManager.onDataReceiveFailed(handle, ADDRESS, REASON, PARAMS);
        verify(callback, never()).onDataReceiveFailed(eq(ADDRESS), eq(REASON), eq(PARAMS));

        // Test should only run on V+ devices.
        if (SdkLevel.isAtLeastV()) {
            rangingManager.onDataTransferPhaseConfigured(handle, PARAMS);
            verify(callback, never()).onDataTransferPhaseConfigured(eq(PARAMS));

            rangingManager.onDataTransferPhaseConfigFailed(handle, PARAMS);
            verify(callback, never()).onDataTransferPhaseConfigFailed(eq(PARAMS));
        }

        rangingManager.onServiceDiscovered(handle, PARAMS);
        verify(callback, never()).onServiceDiscovered(eq(PARAMS));

        rangingManager.onServiceConnected(handle, PARAMS);
        verify(callback, never()).onServiceConnected(eq(PARAMS));

        rangingManager.onRangingClosed(handle, REASON, PARAMS);
        verify(callback, times(1)).onClosed(eq(REASON), eq(PARAMS));
    }

    @Test
    public void testOnRangingClosed_MultipleSessionsRegistered() throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        // Verify that if multiple sessions are registered, only the session that is
        // requested to close receives the associated callbacks
        RangingSession.Callback callback1 = mock(RangingSession.Callback.class);
        RangingSession.Callback callback2 = mock(RangingSession.Callback.class);

        RangingManager rangingManager = new RangingManager(adapter);
        ArgumentCaptor<SessionHandle> sessionHandleCaptor =
                ArgumentCaptor.forClass(SessionHandle.class);

        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback1, /* chipIds= */ null);
        verify(adapter, times(1))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        SessionHandle sessionHandle1 = sessionHandleCaptor.getValue();

        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback2, /* chipIds= */ null);
        verify(adapter, times(2))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        SessionHandle sessionHandle2 = sessionHandleCaptor.getValue();

        rangingManager.onRangingClosed(sessionHandle1, REASON, PARAMS);
        verify(callback1, times(1)).onClosed(anyInt(), any());
        verify(callback2, times(0)).onClosed(anyInt(), any());

        rangingManager.onRangingClosed(sessionHandle2, REASON, PARAMS);
        verify(callback1, times(1)).onClosed(anyInt(), any());
        verify(callback2, times(1)).onClosed(anyInt(), any());
    }

    @Test
    public void testOnRangingReport_MultipleSessionsRegistered() throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession.Callback callback1 = mock(RangingSession.Callback.class);
        RangingSession.Callback callback2 = mock(RangingSession.Callback.class);

        ArgumentCaptor<SessionHandle> sessionHandleCaptor =
                ArgumentCaptor.forClass(SessionHandle.class);

        RangingManager rangingManager = new RangingManager(adapter);
        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback1, /* chipIds= */ null);
        verify(adapter, times(1))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        SessionHandle sessionHandle1 = sessionHandleCaptor.getValue();

        rangingManager.onRangingStarted(sessionHandle1, PARAMS);
        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback2, /* chipIds= */ null);
        verify(adapter, times(2))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        SessionHandle sessionHandle2 = sessionHandleCaptor.getValue();
        rangingManager.onRangingStarted(sessionHandle2, PARAMS);

        rangingManager.onRangingResult(sessionHandle1, UwbTestUtils.getRangingReports(1));
        verify(callback1, times(1)).onReportReceived(any());
        verify(callback2, times(0)).onReportReceived(any());

        rangingManager.onRangingResult(sessionHandle2, UwbTestUtils.getRangingReports(1));
        verify(callback1, times(1)).onReportReceived(any());
        verify(callback2, times(1)).onReportReceived(any());
    }

    @Test
    public void testReasons() throws RemoteException {
        runReason(RangingChangeReason.LOCAL_API, RangingSession.Callback.REASON_LOCAL_REQUEST);

        runReason(
                RangingChangeReason.MAX_SESSIONS_REACHED,
                RangingSession.Callback.REASON_MAX_SESSIONS_REACHED);

        runReason(
                RangingChangeReason.PROTOCOL_SPECIFIC,
                RangingSession.Callback.REASON_PROTOCOL_SPECIFIC_ERROR);

        runReason(
                RangingChangeReason.REMOTE_REQUEST, RangingSession.Callback.REASON_REMOTE_REQUEST);

        runReason(RangingChangeReason.SYSTEM_POLICY, RangingSession.Callback.REASON_SYSTEM_POLICY);

        runReason(
                RangingChangeReason.SYSTEM_REGULATION,
                RangingSession.Callback.REASON_SYSTEM_REGULATION);

        runReason(
                RangingChangeReason.BAD_PARAMETERS, RangingSession.Callback.REASON_BAD_PARAMETERS);

        runReason(RangingChangeReason.UNKNOWN, RangingSession.Callback.REASON_UNKNOWN);
    }

    private void runReason(
            @RangingChangeReason int reasonIn, @RangingSession.Callback.Reason int reasonOut)
            throws RemoteException {
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingManager rangingManager = new RangingManager(adapter);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);

        ArgumentCaptor<SessionHandle> sessionHandleCaptor =
                ArgumentCaptor.forClass(SessionHandle.class);

        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback, /* chipIds= */ null);
        verify(adapter, times(1))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        SessionHandle handle = sessionHandleCaptor.getValue();

        rangingManager.onRangingOpenFailed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onOpenFailed(eq(reasonOut), eq(PARAMS));

        // Open a new session
        rangingManager.openSession(
                ATTRIBUTION_SOURCE, PARAMS, EXECUTOR, callback, /* chipIds= */ null);
        verify(adapter, times(2))
                .openRanging(
                        eq(ATTRIBUTION_SOURCE),
                        sessionHandleCaptor.capture(),
                        any(),
                        any(),
                        eq(/* chipId= */ null));
        handle = sessionHandleCaptor.getValue();
        rangingManager.onRangingOpened(handle);

        rangingManager.onRangingStartFailed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onStartFailed(eq(reasonOut), eq(PARAMS));

        rangingManager.onRangingReconfigureFailed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onReconfigureFailed(eq(reasonOut), eq(PARAMS));

        rangingManager.onRangingStopFailed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onStopFailed(eq(reasonOut), eq(PARAMS));

        rangingManager.onRangingClosed(handle, reasonIn, PARAMS);
        verify(callback, times(1)).onClosed(eq(reasonOut), eq(PARAMS));
    }
}
