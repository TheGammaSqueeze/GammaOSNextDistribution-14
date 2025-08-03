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

package android.uwb;

import static android.uwb.RangingSession.Callback.REASON_BAD_PARAMETERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.Executor;

/**
 * Test of {@link RangingSession}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class RangingSessionTest {
    private static final Executor EXECUTOR = UwbTestUtils.getExecutor();
    private static final PersistableBundle PARAMS = new PersistableBundle();
    private static final UwbAddress UWB_ADDRESS = UwbAddress.fromBytes(new byte[] {0x00, 0x56});
    private static final @RangingSession.Callback.Reason int REASON =
            RangingSession.Callback.REASON_GENERIC_ERROR;
    private static final int UID = Process.myUid();
    private static final String PACKAGE_NAME = "com.uwb.test";
    private static final AttributionSource ATTRIBUTION_SOURCE =
            new AttributionSource.Builder(UID).setPackageName(PACKAGE_NAME).build();
    private static final int HANDLE_ID = 12;
    private static final int PID = Process.myPid();
    private static final int MAX_DATA_SIZE = 100;
    public static final int STATUS_OK = 0;

    @Test
    public void testOnRangingOpened_OnOpenSuccessCalled() {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        verifyOpenState(session, false);

        session.onRangingOpened();
        verifyOpenState(session, true);

        // Verify that the onOpenSuccess callback was invoked
        verify(callback, times(1)).onOpened(eq(session));
        verify(callback, times(0)).onClosed(anyInt(), any());
    }

    @Test
    public void testOnRangingOpened_OnServiceDiscoveredConnectedCalled() {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        verifyOpenState(session, false);

        session.onRangingOpened();
        verifyOpenState(session, true);

        // Verify that the onOpenSuccess callback was invoked
        verify(callback, times(1)).onOpened(eq(session));
        verify(callback, times(0)).onClosed(anyInt(), any());

        session.onServiceDiscovered(PARAMS);
        verify(callback, times(1)).onServiceDiscovered(eq(PARAMS));

        session.onServiceConnected(PARAMS);
        verify(callback, times(1)).onServiceConnected(eq(PARAMS));
    }


    @Test
    public void testOnRangingOpened_CannotOpenClosedSession() {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);

        session.onRangingOpened();
        verifyOpenState(session, true);
        verify(callback, times(1)).onOpened(eq(session));
        verify(callback, times(0)).onClosed(anyInt(), any());

        session.onRangingClosed(REASON, PARAMS);
        verifyOpenState(session, false);
        verify(callback, times(1)).onOpened(eq(session));
        verify(callback, times(1)).onClosed(anyInt(), any());

        // Now invoke the ranging started callback and ensure the session remains closed
        session.onRangingOpened();
        verifyOpenState(session, false);
        verify(callback, times(1)).onOpened(eq(session));
        verify(callback, times(1)).onClosed(anyInt(), any());
    }

    @Test
    public void testOnRangingClosed_OnClosedCalledWhenSessionNotOpen() {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        verifyOpenState(session, false);

        session.onRangingClosed(REASON, PARAMS);
        verifyOpenState(session, false);

        // Verify that the onOpenSuccess callback was invoked
        verify(callback, times(0)).onOpened(eq(session));
        verify(callback, times(1)).onClosed(anyInt(), any());
    }

    @Test
    public void testOnRangingClosed_OnClosedCalled() {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        session.onRangingStarted(PARAMS);
        session.onRangingClosed(REASON, PARAMS);
        verify(callback, times(1)).onClosed(anyInt(), any());

        verifyOpenState(session, false);
        session.onRangingClosed(REASON, PARAMS);
        verify(callback, times(2)).onClosed(anyInt(), any());
    }

    @Test
    public void testOnRangingResult_OnReportReceivedCalled() {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        verifyOpenState(session, false);

        session.onRangingStarted(PARAMS);
        verifyOpenState(session, true);

        RangingReport report = UwbTestUtils.getRangingReports(1);
        session.onRangingResult(report);
        verify(callback, times(1)).onReportReceived(eq(report));
    }

    @Test
    public void testStart_CannotStartIfAlreadyStarted() throws RemoteException {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        doAnswer(new StartAnswer(session)).when(adapter).startRanging(any(), any());
        session.onRangingOpened();

        session.start(PARAMS);
        verify(callback, times(1)).onStarted(any());

        // Calling start again should throw an illegal state
        verifyThrowIllegalState(() -> session.start(PARAMS));
        verify(callback, times(1)).onStarted(any());
    }

    @Test
    public void testStop_CannotStopIfAlreadyStopped() throws RemoteException {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        doAnswer(new StartAnswer(session)).when(adapter).startRanging(any(), any());
        doAnswer(new StopAnswer(session)).when(adapter).stopRanging(any());
        session.onRangingOpened();
        session.start(PARAMS);

        verifyNoThrowIllegalState(session::stop);
        verify(callback, times(1)).onStopped(anyInt(), any());

        // Calling stop again should throw an illegal state
        verifyThrowIllegalState(session::stop);
        verify(callback, times(1)).onStopped(anyInt(), any());
    }

    @Test
    public void testStop_CannotStopIfOpenFailed() throws RemoteException {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        doAnswer(new StartAnswer(session)).when(adapter).startRanging(any(), any());
        doAnswer(new StopAnswer(session)).when(adapter).stopRanging(any());
        session.onRangingOpened();
        session.start(PARAMS);

        verifyNoThrowIllegalState(() -> session.onRangingOpenFailed(REASON_BAD_PARAMETERS, PARAMS));
        verify(callback, times(1)).onOpenFailed(
                REASON_BAD_PARAMETERS, PARAMS);

        // Calling stop again should throw an illegal state
        verifyThrowIllegalState(session::stop);
        verify(callback, times(0)).onStopped(anyInt(), any());
    }

    @Test
    public void testCallbacks_OnlyWhenOpened() throws RemoteException {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        doAnswer(new OpenAnswer(session)).when(adapter).openRanging(
                any(), any(), any(), any(), any());
        doAnswer(new StartAnswer(session)).when(adapter).startRanging(any(), any());
        doAnswer(new ReconfigureAnswer(session)).when(adapter).reconfigureRanging(any(), any());
        doAnswer(new PauseAnswer(session)).when(adapter).pause(any(), any());
        doAnswer(new ResumeAnswer(session)).when(adapter).resume(any(), any());
        doAnswer(new ControleeAddAnswer(session)).when(adapter).addControlee(any(), any());
        doAnswer(new ControleeRemoveAnswer(session)).when(adapter).removeControlee(any(), any());
        doAnswer(new DataSendAnswer(session)).when(adapter).sendData(any(), any(), any(), any());
        doAnswer(new StopAnswer(session)).when(adapter).stopRanging(any());
        doAnswer(new CloseAnswer(session)).when(adapter).closeRanging(any());

        verifyThrowIllegalState(() -> session.reconfigure(PARAMS));
        verify(callback, times(0)).onReconfigured(any());
        verifyOpenState(session, false);

        session.onRangingOpened();
        verifyOpenState(session, true);
        verify(callback, times(1)).onOpened(any());
        verifyNoThrowIllegalState(() -> session.reconfigure(PARAMS));
        verify(callback, times(1)).onReconfigured(any());
        verifyThrowIllegalState(() -> session.pause(PARAMS));
        verify(callback, times(0)).onPaused(any());
        verifyThrowIllegalState(() -> session.resume(PARAMS));
        verify(callback, times(0)).onResumed(any());
        verifyNoThrowIllegalState(() -> session.addControlee(PARAMS));
        verify(callback, times(1)).onControleeAdded(any());
        verifyNoThrowIllegalState(() -> session.removeControlee(PARAMS));
        verify(callback, times(1)).onControleeRemoved(any());
        verifyThrowIllegalState(() -> session.sendData(
                UWB_ADDRESS, PARAMS, new byte[] {0x05, 0x1}));
        verify(callback, times(0)).onDataSent(any(), any());

        session.onRangingStartFailed(REASON_BAD_PARAMETERS, PARAMS);
        verifyOpenState(session, true);
        verify(callback, times(1)).onStartFailed(
                REASON_BAD_PARAMETERS, PARAMS);

        session.onRangingStarted(PARAMS);
        verifyOpenState(session, true);
        verifyNoThrowIllegalState(() -> session.reconfigure(PARAMS));
        verify(callback, times(2)).onReconfigured(any());
        verifyNoThrowIllegalState(() -> session.reconfigure(null));
        verify(callback, times(1)).onReconfigureFailed(
                eq(REASON_BAD_PARAMETERS), any());
        verifyNoThrowIllegalState(() -> session.pause(PARAMS));
        verify(callback, times(1)).onPaused(any());
        verifyNoThrowIllegalState(() -> session.pause(null));
        verify(callback, times(1)).onPauseFailed(
                eq(REASON_BAD_PARAMETERS), any());
        verifyNoThrowIllegalState(() -> session.resume(PARAMS));
        verify(callback, times(1)).onResumed(any());
        verifyNoThrowIllegalState(() -> session.resume(null));
        verify(callback, times(1)).onResumeFailed(
                eq(REASON_BAD_PARAMETERS), any());
        verifyNoThrowIllegalState(() -> session.addControlee(PARAMS));
        verify(callback, times(2)).onControleeAdded(any());
        verifyNoThrowIllegalState(() -> session.addControlee(null));
        verify(callback, times(1)).onControleeAddFailed(
                eq(REASON_BAD_PARAMETERS), any());
        verifyNoThrowIllegalState(() -> session.removeControlee(PARAMS));
        verify(callback, times(2)).onControleeRemoved(any());
        verifyNoThrowIllegalState(() -> session.removeControlee(null));
        verify(callback, times(1)).onControleeRemoveFailed(
                eq(REASON_BAD_PARAMETERS), any());
        verifyNoThrowIllegalState(() -> session.sendData(
                UWB_ADDRESS, PARAMS, new byte[] {0x05, 0x1}));
        verify(callback, times(1)).onDataSent(any(), any());
        verifyNoThrowIllegalState(() -> session.sendData(
                null, PARAMS, new byte[] {0x05, 0x1}));
        verify(callback, times(1)).onDataSendFailed(
                eq(null), eq(REASON_BAD_PARAMETERS), any());

        session.onDataReceived(UWB_ADDRESS, PARAMS, new byte[] {0x5, 0x7});
        verify(callback, times(1)).onDataReceived(
                UWB_ADDRESS, PARAMS, new byte[] {0x5, 0x7});
        session.onDataReceiveFailed(UWB_ADDRESS, REASON_BAD_PARAMETERS, PARAMS);
        verify(callback, times(1)).onDataReceiveFailed(
                UWB_ADDRESS, REASON_BAD_PARAMETERS, PARAMS);

        session.onDataTransferPhaseConfigured(PARAMS);
        verify(callback, times(1)).onDataTransferPhaseConfigured(any());

        session.stop();
        verifyOpenState(session, true);
        verify(callback, times(1)).onStopped(REASON, PARAMS);

        verifyNoThrowIllegalState(() -> session.reconfigure(PARAMS));
        verify(callback, times(3)).onReconfigured(any());
        verifyThrowIllegalState(() -> session.pause(PARAMS));
        verify(callback, times(1)).onPaused(any());
        verifyThrowIllegalState(() -> session.resume(PARAMS));
        verify(callback, times(1)).onResumed(any());
        verifyNoThrowIllegalState(() -> session.addControlee(PARAMS));
        verify(callback, times(3)).onControleeAdded(any());
        verifyNoThrowIllegalState(() -> session.removeControlee(PARAMS));
        verify(callback, times(3)).onControleeRemoved(any());
        verifyThrowIllegalState(() -> session.sendData(
                UWB_ADDRESS, PARAMS, new byte[] {0x05, 0x1}));
        verify(callback, times(1)).onDataSent(any(), any());

        session.close();
        verifyOpenState(session, false);
        verify(callback, times(1)).onClosed(REASON, PARAMS);

        verifyThrowIllegalState(() -> session.reconfigure(PARAMS));
        verify(callback, times(3)).onReconfigured(any());
        verifyThrowIllegalState(() -> session.pause(PARAMS));
        verify(callback, times(1)).onPaused(any());
        verifyThrowIllegalState(() -> session.resume(PARAMS));
        verify(callback, times(1)).onResumed(any());
        verifyThrowIllegalState(() -> session.addControlee(PARAMS));
        verify(callback, times(3)).onControleeAdded(any());
        verifyThrowIllegalState(() -> session.removeControlee(PARAMS));
        verify(callback, times(3)).onControleeRemoved(any());
        verifyThrowIllegalState(() -> session.sendData(
                UWB_ADDRESS, PARAMS, new byte[] {0x05, 0x1}));
        verify(callback, times(1)).onDataSent(any(), any());
    }

    @Test
    public void testClose_NoCallbackUntilInvoked() throws RemoteException {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        session.onRangingOpened();

        // Calling close multiple times should invoke closeRanging until the session receives
        // the onClosed callback.
        int totalCallsBeforeOnRangingClosed = 3;
        for (int i = 1; i <= totalCallsBeforeOnRangingClosed; i++) {
            session.close();
            verifyOpenState(session, true);
            verify(adapter, times(i)).closeRanging(handle);
            verify(callback, times(0)).onClosed(anyInt(), any());
        }

        // After onClosed is invoked, then the adapter should no longer be called for each call to
        // the session's close.
        final int totalCallsAfterOnRangingClosed = 2;
        for (int i = 1; i <= totalCallsAfterOnRangingClosed; i++) {
            session.onRangingClosed(REASON, PARAMS);
            verifyOpenState(session, false);
            verify(adapter, times(totalCallsBeforeOnRangingClosed)).closeRanging(handle);
            verify(callback, times(i)).onClosed(anyInt(), any());
        }
    }

    @Test
    public void testClose_OnClosedCalled() throws RemoteException {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        doAnswer(new CloseAnswer(session)).when(adapter).closeRanging(any());
        session.onRangingOpened();

        session.close();
        verify(callback, times(1)).onClosed(anyInt(), any());
    }

    @Test
    public void testClose_CannotInteractFurther() throws RemoteException {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        doAnswer(new CloseAnswer(session)).when(adapter).closeRanging(any());
        session.close();

        verifyThrowIllegalState(() -> session.start(PARAMS));
        verifyThrowIllegalState(() -> session.reconfigure(PARAMS));
        verifyThrowIllegalState(() -> session.stop());
        verifyNoThrowIllegalState(() -> session.close());
    }

    @Test
    public void testQueryDataSize() throws RemoteException {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);

        when(adapter.queryMaxDataSizeBytes(handle)).thenReturn(MAX_DATA_SIZE);

        session.onRangingStarted(PARAMS);
        assertThat(session.queryMaxDataSizeBytes()).isEqualTo(MAX_DATA_SIZE);
    }

    @Test
    public void testOnRangingResult_OnReportReceivedCalledWhenOpen() {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);

        assertFalse(session.isOpen());
        session.onRangingStarted(PARAMS);
        assertTrue(session.isOpen());

        // Verify that the onReportReceived callback was invoked
        RangingReport report = UwbTestUtils.getRangingReports(1);
        session.onRangingResult(report);
        verify(callback, times(1)).onReportReceived(report);
    }

    @Test
    public void testOnRangingResult_OnReportReceivedNotCalledWhenNotOpen() {
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);

        assertFalse(session.isOpen());

        // Verify that the onReportReceived callback was invoked
        RangingReport report = UwbTestUtils.getRangingReports(1);
        session.onRangingResult(report);
        verify(callback, times(0)).onReportReceived(report);
    }

    @Test
    public void testOnRangingRoundsUpdateDtTag() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU()); // Test should only run on U+ devices.
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        PersistableBundle params = new PersistableBundle();
        assertFalse(session.isOpen());

        session.onRangingOpened();
        session.onRangingStarted(params);
        session.updateRangingRoundsDtTag(params);

        verify(adapter, times(1)).updateRangingRoundsDtTag(handle, params);
    }

    @Test
    public void testOnRangingRoundsUpdateDtTagStatus() {
        assumeTrue(SdkLevel.isAtLeastU()); // Test should only run on U+ devices.
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        PersistableBundle params = new PersistableBundle();
        assertFalse(session.isOpen());

        session.onRangingOpened();
        session.onRangingRoundsUpdateDtTagStatus(params);

        verify(callback, times(1)).onRangingRoundsUpdateDtTagStatus(params);
    }

    @Test
    public void testQueryMaxDataSizeBytes() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU()); // Test should only run on U+ devices.
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);

        when(adapter.queryMaxDataSizeBytes(handle)).thenReturn(MAX_DATA_SIZE);

        // Confirm that queryMaxDataSizeBytes() throws an IllegalStateException when the ranging
        // session is not open.
        assertFalse(session.isOpen());
        verifyThrowIllegalState(() -> session.queryMaxDataSizeBytes());

        // Confirm that queryMaxDataSizeBytes() returns a value when the ranging session has been
        // opened.
        session.onRangingOpened();
        assertEquals(session.queryMaxDataSizeBytes(), MAX_DATA_SIZE);

        // Confirm that queryMaxDataSizeBytes() returns a value when the ranging session has been
        // started.
        session.onRangingStarted(PARAMS);
        assertEquals(session.queryMaxDataSizeBytes(), MAX_DATA_SIZE);

        // Confirm that queryMaxDataSizeBytes() still returns a value, when the ranging session
        // was stopped.
        session.onRangingStopped(REASON, PARAMS);
        assertEquals(session.queryMaxDataSizeBytes(), MAX_DATA_SIZE);

        // Confirm that queryMaxDataSizeBytes() throws an IllegalStateException when the ranging
        // session has now been closed.
        session.onRangingClosed(REASON, PARAMS);
        verifyThrowIllegalState(() -> session.queryMaxDataSizeBytes());
    }

    @Test
    public void testSetHybridSessionConfiguration() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV()); // Test should only run on V+ devices.
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);

        // Confirm that setHybridSessionConfiguration() throws an IllegalStateException
        // when the ranging session is not open.
        assertFalse(session.isOpen());
        verifyThrowIllegalState(() -> session.setHybridSessionConfiguration(PARAMS));

        // Confirm that setHybridSessionConfiguration() returns a value when the ranging
        // session has been opened.
        session.onRangingOpened();
        assertEquals(session.setHybridSessionConfiguration(PARAMS), STATUS_OK);

        // Confirm that setHybridSessionConfiguration() returns a value when the ranging
        // session has been started.
        session.onRangingStarted(PARAMS);
        assertEquals(session.setHybridSessionConfiguration(PARAMS), STATUS_OK);

        // Confirm that setHybridSessionConfiguration() still returns a value, when the ranging
        // session was stopped.
        session.onRangingStopped(REASON, PARAMS);
        assertEquals(session.setHybridSessionConfiguration(PARAMS), STATUS_OK);

        // Confirm that setHybridSessionConfiguration() throws an IllegalStateException when the
        // ranging session has now been closed.
        session.onRangingClosed(REASON, PARAMS);
        verifyThrowIllegalState(() -> session.setHybridSessionConfiguration(PARAMS));
    }

    @Test
    public void testSetDataTransferPhaseConfig() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastV()); // Test should only run on V+ devices.
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.class);
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);

        // Confirm that setDataTransferPhaseConfig() throws an IllegalStateException
        // when the ranging session is not open.
        assertFalse(session.isOpen());
        verifyThrowIllegalState(() -> session.setDataTransferPhaseConfig(PARAMS));

        // Confirm that setDataTransferPhaseConfig() returns a value when the ranging
        // session has been opened.
        session.onRangingOpened();
        verifyNoThrowIllegalState(() -> session.setDataTransferPhaseConfig(PARAMS));

        // Confirm that setDataTransferPhaseConfig() throws an IllegalStateException when the
        // ranging session has now been closed.
        session.onRangingClosed(REASON, PARAMS);
        verifyThrowIllegalState(() -> session.setDataTransferPhaseConfig(PARAMS));
    }

    @Test
    public void testPoseUpdate() throws RemoteException {
        assumeTrue(SdkLevel.isAtLeastU()); // Test should only run on U+ devices.
        SessionHandle handle = new SessionHandle(HANDLE_ID, ATTRIBUTION_SOURCE, PID);
        RangingSession.Callback callback = mock(RangingSession.Callback.class);
        IUwbAdapter adapter = mock(IUwbAdapter.Stub.class);
        doNothing().when(adapter).updatePose(any(), any());
        RangingSession session = new RangingSession(EXECUTOR, callback, adapter, handle);
        assertFalse(session.isOpen());

        session.onRangingOpened();
        session.updatePose(PARAMS);

        ArgumentCaptor<SessionHandle> shCaptor = ArgumentCaptor.forClass(SessionHandle.class);
        ArgumentCaptor<PersistableBundle> bundleCaptor = ArgumentCaptor.forClass(
                PersistableBundle.class);

        verify(adapter, times(1))
                .updatePose(shCaptor.capture(), bundleCaptor.capture());
        assertEquals(handle.getId(), shCaptor.getValue().getId());
    }

    private void verifyOpenState(RangingSession session, boolean expected) {
        assertEquals(expected, session.isOpen());
    }

    private void verifyThrowIllegalState(Runnable runnable) {
        try {
            runnable.run();
            fail();
        } catch (IllegalStateException e) {
            // Pass
        }
    }

    private void verifyNoThrowIllegalState(Runnable runnable) {
        try {
            runnable.run();
        } catch (IllegalStateException e) {
            fail();
        }
    }

    abstract class AdapterAnswer implements Answer {
        protected RangingSession mSession;

        protected AdapterAnswer(RangingSession session) {
            mSession = session;
        }
    }

    class OpenAnswer extends AdapterAnswer {
        OpenAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            PersistableBundle argParams = invocation.getArgument(1);
            if (argParams != null) {
                mSession.onRangingOpened();
            } else {
                mSession.onRangingOpenFailed(REASON_BAD_PARAMETERS, PARAMS);
            }
            return null;
        }
    }

    class StartAnswer extends AdapterAnswer {
        StartAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            PersistableBundle argParams = invocation.getArgument(1);
            if (argParams != null) {
                mSession.onRangingStarted(PARAMS);
            } else {
                mSession.onRangingStartFailed(REASON_BAD_PARAMETERS, PARAMS);
            }
            return null;
        }
    }

    class ReconfigureAnswer extends AdapterAnswer {
        ReconfigureAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            PersistableBundle argParams = invocation.getArgument(1);
            if (argParams != null) {
                mSession.onRangingReconfigured(PARAMS);
            } else {
                mSession.onRangingReconfigureFailed(REASON_BAD_PARAMETERS, PARAMS);
            }
            return null;
        }
    }

    class PauseAnswer extends AdapterAnswer {
        PauseAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            PersistableBundle argParams = invocation.getArgument(1);
            if (argParams != null) {
                mSession.onRangingPaused(PARAMS);
            } else {
                mSession.onRangingPauseFailed(REASON_BAD_PARAMETERS, PARAMS);
            }
            return null;
        }
    }

    class ResumeAnswer extends AdapterAnswer {
        ResumeAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            PersistableBundle argParams = invocation.getArgument(1);
            if (argParams != null) {
                mSession.onRangingResumed(PARAMS);
            } else {
                mSession.onRangingResumeFailed(REASON_BAD_PARAMETERS, PARAMS);
            }
            return null;
        }
    }

    class ControleeAddAnswer extends AdapterAnswer {
        ControleeAddAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            PersistableBundle argParams = invocation.getArgument(1);
            if (argParams != null) {
                mSession.onControleeAdded(PARAMS);
            } else {
                mSession.onControleeAddFailed(REASON_BAD_PARAMETERS, PARAMS);
            }
            return null;
        }
    }

    class ControleeRemoveAnswer extends AdapterAnswer {
        ControleeRemoveAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            PersistableBundle argParams = invocation.getArgument(1);
            if (argParams != null) {
                mSession.onControleeRemoved(PARAMS);
            } else {
                mSession.onControleeRemoveFailed(REASON_BAD_PARAMETERS, PARAMS);
            }
            return null;
        }
    }

    class DataSendAnswer extends AdapterAnswer {
        DataSendAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            UwbAddress argParams = invocation.getArgument(1);
            if (argParams != null) {
                mSession.onDataSent(UWB_ADDRESS, PARAMS);
            } else {
                mSession.onDataSendFailed(null, REASON_BAD_PARAMETERS, PARAMS);
            }
            return null;
        }
    }

    class StopAnswer extends AdapterAnswer {
        StopAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            mSession.onRangingStopped(REASON, PARAMS);
            return null;
        }
    }

    class CloseAnswer extends AdapterAnswer {
        CloseAnswer(RangingSession session) {
            super(session);
        }

        @Override
        public Object answer(InvocationOnMock invocation) {
            mSession.onRangingClosed(REASON, PARAMS);
            return null;
        }
    }
}
