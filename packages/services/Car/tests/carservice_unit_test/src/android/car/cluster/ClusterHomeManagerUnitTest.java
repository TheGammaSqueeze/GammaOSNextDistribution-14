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

package android.car.cluster;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.car.Car;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;
import android.view.Window;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.Executor;

/**
 * Unit tests for {@link ClusterHomeManager}
 */
@RunWith(MockitoJUnitRunner.class)
public final class ClusterHomeManagerUnitTest {
    @Mock
    private Car mCar;

    @Mock
    private IBinder mBinder;

    @Mock
    private IClusterHomeService.Stub mService;

    @Mock
    private ClusterHomeManager.ClusterStateListener mClusterStateListener;

    @Mock
    private ClusterHomeManager.ClusterNavigationStateListener mClusterNavigationStateListener;

    @Mock
    private Activity mActivity;
    @Mock
    private Window mWindow;
    @Mock
    private View mDecorView;
    @Mock
    private ViewTreeObserver mViewTreeObserver;
    @Mock
    private ViewRootImpl mViewRoot;
    @Mock
    private SurfaceControl mSurfaceControl;

    private ClusterHomeManager mClusterHomeManager;
    private final Executor mCurrentThreadExecutor = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    @Before
    public void setup() {
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mService);
        mClusterHomeManager = new ClusterHomeManager(mCar, mBinder);
    }

    @Test
    public void getClusterState_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        when(mService.getClusterState()).thenThrow(thrownException);

        ClusterState clusterState = mClusterHomeManager.getClusterState();

        verify(mCar).handleRemoteExceptionFromCarService(thrownException);
        assertThat(clusterState).isNull();
    }

    @Test
    public void registerClusterStateListener_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        doThrow(thrownException)
                .when(mService).registerClusterStateListener(any());

        mClusterHomeManager.registerClusterStateListener(mCurrentThreadExecutor,
                mClusterStateListener);

        verify(mCar).handleRemoteExceptionFromCarService(thrownException);
    }

    @Test
    public void registerClusterNavigationStateListener_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        doThrow(thrownException)
                .when(mService).registerClusterNavigationStateListener(any());

        mClusterHomeManager.registerClusterNavigationStateListener(mCurrentThreadExecutor,
                mClusterNavigationStateListener);

        verify(mCar).handleRemoteExceptionFromCarService(thrownException);
    }

    @Test
    public void registerClusterStateListener_callbackAlreadyRegistered_doNothing()
            throws Exception {
        doNothing().when(mService).registerClusterStateListener(any());
        mClusterHomeManager.registerClusterStateListener(mCurrentThreadExecutor,
                mClusterStateListener);

        mClusterHomeManager.registerClusterStateListener(mCurrentThreadExecutor,
                mClusterStateListener);

        verify(mService, times(1)).registerClusterStateListener(any());
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void registerClusterNavigationStateListener_callbackAlreadyRegistered_doNothing()
            throws Exception {
        doNothing().when(mService).registerClusterNavigationStateListener(any());
        mClusterHomeManager.registerClusterNavigationStateListener(mCurrentThreadExecutor,
                mClusterNavigationStateListener);

        mClusterHomeManager.registerClusterNavigationStateListener(mCurrentThreadExecutor,
                mClusterNavigationStateListener);

        verify(mService, times(1)).registerClusterNavigationStateListener(any());
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void onNavigationStateChanged_callsCallbacks() throws Exception {
        byte[] newNavigationState = new byte[]{1};
        doAnswer(invocation -> {
            IClusterNavigationStateListener.Stub clusterHomeManagerNavigationStateListener =
                    (IClusterNavigationStateListener.Stub) invocation.getArgument(0);
            clusterHomeManagerNavigationStateListener.onNavigationStateChanged(newNavigationState);
            return null;
        }).when(mService).registerClusterNavigationStateListener(any());

        mClusterHomeManager.registerClusterNavigationStateListener(mCurrentThreadExecutor,
                mClusterNavigationStateListener);

        verify(mClusterNavigationStateListener).onNavigationStateChanged(eq(newNavigationState));
    }

    @Test
    public void reportState_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        doThrow(thrownException)
                .when(mService).reportState(anyInt(), anyInt(), any(byte[].class));

        mClusterHomeManager.reportState(1, 1, new byte[]{1});

        verify(mCar).handleRemoteExceptionFromCarService(thrownException);
    }

    @Test
    public void requestDisplay_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        doThrow(thrownException).when(mService).requestDisplay(anyInt());

        mClusterHomeManager.requestDisplay(1);
        verify(mCar).handleRemoteExceptionFromCarService(thrownException);
    }

    @Test
    public void startFixedActivityMode_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        doThrow(thrownException)
                .when(mService).startFixedActivityModeAsUser(any(), any(), anyInt());

        boolean launchedAsFixedActivity =
                mClusterHomeManager.startFixedActivityModeAsUser(new Intent(), new Bundle(), 1);
        verify(mCar).handleRemoteExceptionFromCarService(thrownException);
        assertThat(launchedAsFixedActivity).isFalse();
    }

    @Test
    public void stopFixedActivityMode_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        doThrow(thrownException).when(mService).stopFixedActivityMode();

        mClusterHomeManager.stopFixedActivityMode();

        verify(mCar).handleRemoteExceptionFromCarService(thrownException);
    }

    @Test
    public void unregisterClusterStateListener_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        doNothing().when(mService).registerClusterStateListener(any());
        doThrow(thrownException).when(mService).unregisterClusterStateListener(any());
        mClusterHomeManager.registerClusterStateListener(mCurrentThreadExecutor,
                mClusterStateListener);

        mClusterHomeManager.unregisterClusterStateListener(mClusterStateListener);

        verifyNoMoreInteractions(mCar);
    }

    @Test
    public void unregisterClusterStateListener_callbackNotPresent_doNothing() throws Exception {
        RemoteException thrownException = new RemoteException();
        doNothing().when(mService).registerClusterStateListener(any());
        doThrow(thrownException).when(mService).unregisterClusterStateListener(any());

        mClusterHomeManager.unregisterClusterStateListener(mClusterStateListener);

        verifyNoMoreInteractions(mCar);
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void unregisterClusterNavigationStateListener_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        doNothing().when(mService).registerClusterNavigationStateListener(any());
        doThrow(thrownException).when(mService).unregisterClusterNavigationStateListener(any());
        mClusterHomeManager.registerClusterNavigationStateListener(mCurrentThreadExecutor,
                mClusterNavigationStateListener);

        mClusterHomeManager
                .unregisterClusterNavigationStateListener(mClusterNavigationStateListener);

        verifyNoMoreInteractions(mCar);
    }

    @Test
    public void unregisterClusterNavigationStateListener_callbackNotPresent_doNothing()
            throws Exception {
        RemoteException thrownException = new RemoteException();
        doNothing().when(mService).registerClusterNavigationStateListener(any());
        doThrow(thrownException).when(mService).unregisterClusterNavigationStateListener(any());

        mClusterHomeManager
                .unregisterClusterNavigationStateListener(mClusterNavigationStateListener);
        verifyNoMoreInteractions(mCar);
        verifyNoMoreInteractions(mService);
    }

    @Test
    public void sendHeartbeat_serviceFailure() throws Exception {
        RemoteException thrownException = new RemoteException();
        doThrow(thrownException).when(mService).sendHeartbeat(anyLong(), any());

        mClusterHomeManager.sendHeartbeat(System.nanoTime(), /* appMetadata= */ null);

        verify(mCar).handleRemoteExceptionFromCarService(thrownException);
    }

    @Test
    public void startVisibilityMonitoring_startsOrStopsMonitoring_perSurfaceReadiness()
            throws Exception {
        setUpActivity();

        mClusterHomeManager.startVisibilityMonitoring(mActivity);

        // Checks whether the necessary callbacks are registered.
        ArgumentCaptor<ViewTreeObserver.OnPreDrawListener> onPreDrawListenerCaptor =
                ArgumentCaptor.forClass(ViewTreeObserver.OnPreDrawListener.class);
        verify(mViewTreeObserver).addOnPreDrawListener(onPreDrawListenerCaptor.capture());
        assertThat(onPreDrawListenerCaptor).isNotNull();
        ArgumentCaptor<ViewTreeObserver.OnWindowAttachListener> onWindowAttachListenerCaptor =
                ArgumentCaptor.forClass(ViewTreeObserver.OnWindowAttachListener.class);
        verify(mViewTreeObserver).addOnWindowAttachListener(onWindowAttachListenerCaptor.capture());
        assertThat(onWindowAttachListenerCaptor).isNotNull();
        verifyNoMoreInteractions(mService);

        // Starts monitoring when the Surface is ready.
        onPreDrawListenerCaptor.getValue().onPreDraw();
        verify(mService).startVisibilityMonitoring(mSurfaceControl);

        // Stops monitoring when the Surface is removed.
        onWindowAttachListenerCaptor.getValue().onWindowDetached();
        verify(mService).stopVisibilityMonitoring();
    }

    private void setUpActivity() {
        when(mCar.getContext()).thenReturn(mActivity);
        when(mActivity.checkCallingOrSelfPermission(anyString())).thenReturn(PERMISSION_GRANTED);
        when(mActivity.getWindow()).thenReturn(mWindow);
        when(mWindow.getDecorView()).thenReturn(mDecorView);
        when(mDecorView.getViewTreeObserver()).thenReturn(mViewTreeObserver);
        when(mDecorView.getViewRootImpl()).thenReturn(mViewRoot);
        when(mViewRoot.getSurfaceControl()).thenReturn(
                null,  // Supposed to be called in onCreate
                mSurfaceControl);  // Supposed to be called in onAttachedToWindow
    }
}
