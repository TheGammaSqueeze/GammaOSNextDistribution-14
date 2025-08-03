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

package com.android.car.cluster;

import static android.car.settings.CarSettings.Global.DISABLE_INSTRUMENTATION_SERVICE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.Nullable;
import android.car.CarAppFocusManager;
import android.car.cluster.navigation.NavigationState.Maneuver;
import android.car.cluster.navigation.NavigationState.Maneuver.TypeV2;
import android.car.cluster.navigation.NavigationState.NavigationStateProto;
import android.car.cluster.navigation.NavigationState.Step;
import android.car.cluster.renderer.IInstrumentCluster;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.car.navigation.CarNavigationInstrumentCluster;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.AbstractExtendedMockitoTestCase.CustomMockitoSessionBuilder;
import android.car.test.mocks.MockSettings;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;

import com.android.car.AppFocusService;
import com.android.car.CarInputService;
import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.user.CarUserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

public final class InstrumentClusterServiceTest extends AbstractExtendedMockitoTestCase {

    private static final String DEFAULT_RENDERER_SERVICE =
            "com.android.car.carservice_unittest/.FakeService";
    private static final String NAV_STATE_PROTO_BUNDLE_KEY = "navstate2";

    private InstrumentClusterService mService;
    private ClusterNavigationService mNavigationService;

    @Mock
    private Context mContext;
    @Mock
    private AppFocusService mAppFocusService;
    @Mock
    private CarInputService mCarInputService;
    @Mock
    private CarUserService mCarUserService;

    private MockSettings mMockSettings;

    private final IInstrumentClusterNavigationImpl mInstrumentClusterNavigation =
            new IInstrumentClusterNavigationImpl();
    private final TestClusterRenderer mInstrumentClusterRenderer = new TestClusterRenderer();


    private class TestClusterRenderer extends IInstrumentCluster.Stub {
        @Nullable public KeyEvent mLastReceivedOnKeyEvent;

        public int mMaxFailuresToReturnNavigationService = 0;
        public boolean mThrowErrorInOnKeyEvent;
        private int mGetNavigationServiceCount = 0;

        @Override
        public IInstrumentClusterNavigation getNavigationService() {
            mGetNavigationServiceCount++;
            if (mGetNavigationServiceCount <= mMaxFailuresToReturnNavigationService) {
                return null;
            }
            return mInstrumentClusterNavigation;
        }

        @Override
        public void setNavigationContextOwner(int uid, int pid) {
        }

        @Override
        public void onKeyEvent(KeyEvent keyEvent) throws RemoteException {
            mLastReceivedOnKeyEvent = keyEvent;
            if (mThrowErrorInOnKeyEvent) {
                throw new RemoteException();
            }
        }
    }

    public InstrumentClusterServiceTest() {
        super(InstrumentClusterService.TAG, ClusterNavigationService.TAG);
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder builder) {
        mMockSettings = new MockSettings(builder);
        builder.spyStatic(BitmapFactory.class);
    }

    @Before
    public void setUp() {
        doReturn(DEFAULT_RENDERER_SERVICE).when(mContext).getString(
                R.string.instrumentClusterRendererService);
        doReturn(true).when(mContext).bindServiceAsUser(any(), any(), anyInt(), any());
        doNothing().when(mContext).unbindService(any());
        ContentResolver cr = InstrumentationRegistry.getTargetContext().getContentResolver();
        doReturn(cr).when(mContext).getContentResolver();
        mMockSettings.putString(DISABLE_INSTRUMENTATION_SERVICE, "false");
        doAnswer((Answer<Void>) invocationOnMock -> {
                    Runnable r = invocationOnMock.getArgument(0);
                    r.run();
                    return null;
                }
        ).when(mCarUserService).runOnUser0Unlock(any());
        CarLocalServices.removeServiceForTest(CarUserService.class);
        CarLocalServices.addService(CarUserService.class, mCarUserService);

        setNewService();
    }

    private void setNewService() {
        // Must prepare Looper (once) otherwise InstrumentClusterService constructor will fail.
        Looper looper = Looper.myLooper();
        if (looper == null) {
            Looper.prepare();
        }
        mNavigationService = new ClusterNavigationService(mContext, mAppFocusService);
        mService = new InstrumentClusterService(mContext, mNavigationService, mCarInputService,
                /* rendererServiceWaitTimeoutMs= */ 1000);
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarUserService.class);
    }

    private void initService(boolean connect) {
        mService.init();
        if (connect) {
            notifyRendererServiceConnection();
        }
        // Give nav focus to the test
        mNavigationService.onFocusAcquired(CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION,
                Process.myUid(), Process.myPid());
    }

    private void notifyRendererServiceConnection() {
        mService.mRendererServiceConnection.onServiceConnected(null,
                mInstrumentClusterRenderer.asBinder());
    }

    private void notifyRendererServiceDisconnected() {
        mService.mRendererServiceConnection.onServiceDisconnected(null);
    }

    @Test
    public void testOnServiceDisconnected() {
        initService(/* connect= */ true);

        notifyRendererServiceDisconnected();

        verify(mContext).unbindService(any());
    }

    @Test
    public void testGetInstrumentClusterInfo_navigationRendererInitialFailures() {
        initService(/* connect= */ true);

        mInstrumentClusterRenderer.mMaxFailuresToReturnNavigationService = 1;
        CarNavigationInstrumentCluster clusterInfo = mService.getInstrumentClusterInfo();

        assertThat(clusterInfo).isEqualTo(mInstrumentClusterNavigation.mClusterInfo);
    }

    @Test
    public void testGetInstrumentClusterInfo_navigationBinderError() {
        initService(/* connect= */ true);
        mInstrumentClusterNavigation.mThrowExceptionInGetInstrumentClusterInfo = true;

        assertThrows(IllegalStateException.class, () -> {
            mService.getInstrumentClusterInfo();
        });
    }

    @Test
    public void testOnKeyEvent() {
        initService(/* connect= */ true);
        KeyEvent event = new KeyEvent(1, 1);

        mService.onKeyEvent(event);

        assertThat(mInstrumentClusterRenderer.mLastReceivedOnKeyEvent).isEqualTo(event);
    }

    @Test
    public void testOnKeyEvent_rendererThrowsError() {
        initService(/* connect= */ true);
        KeyEvent event = new KeyEvent(1, 1);
        mInstrumentClusterRenderer.mThrowErrorInOnKeyEvent = true;

        mService.onKeyEvent(event);
        // Expect no exception to be thrown
    }

    @Test
    public void testNonNullManager() throws Exception {
        initService(/* connect= */ true);
        checkValidClusterNavigation();
        verify(mContext).bindServiceAsUser(any(), any(), anyInt(), any());
    }

    @Test
    public void testDelayedConnection() throws Exception {
        initService(/* connect= */ false);
        CarServiceUtils.runOnMain(() -> {
            // need to delay notification
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
            }
            notifyRendererServiceConnection();
        });
        checkValidClusterNavigation();
        verify(mContext).bindServiceAsUser(any(), any(), anyInt(), any());
    }

    @Test
    public void testNoConnection_throwsIllegalStateException() throws Exception {
        initService(/* connect= */ false);
        assertThrows(IllegalStateException.class, () -> {
            mService.getInstrumentClusterInfo();
        });
    }

    @Test
    public void testNoConfig() throws Exception {
        doReturn("").when(mContext).getString(R.string.instrumentClusterRendererService);
        setNewService();
        initService(/* connect= */ false);
        verify(mContext, times(0)).bindServiceAsUser(any(), any(), anyInt(), any());
    }

    @Test
    public void sendNavigationState_navStateProtoNull_throwsException() throws Exception {
        initService(/* connect= */ true);

        assertThrows(
                IllegalArgumentException.class,
                () -> mNavigationService.onNavigationStateChanged(new Bundle()));
    }

    @Test
    public void sendNavigationState_navStateProtoNotPopulatedCorrectly_throwsException()
            throws Exception {
        initService(/* connect= */ true);

        // Create a non valid NavigationStateProto instance where Maneuver#typeV2 is populated but
        // Maneuver#type is not.
        Bundle bundle = new Bundle();
        Maneuver.Builder maneuverBuilder =
                Maneuver.newBuilder().setTypeV2(TypeV2.ROUNDABOUT_ENTER_CW);
        Step.Builder stepBuilder = Step.newBuilder().setManeuver(maneuverBuilder);
        NavigationStateProto.Builder navigationStateProtoBuilder =
                NavigationStateProto.newBuilder().addSteps(stepBuilder);
        bundle.putByteArray(
                NAV_STATE_PROTO_BUNDLE_KEY,
                navigationStateProtoBuilder.build().toByteArray());

        assertThrows(IllegalArgumentException.class,
                () -> mNavigationService.onNavigationStateChanged(bundle));
    }

    @Test
    public void sendNavigationState_navStateProtoInvalid_throwsException() throws Exception {
        initService(/* connect= */ true);

        Bundle bundle = new Bundle();
        bundle.putByteArray(
                NAV_STATE_PROTO_BUNDLE_KEY,
                new byte[] {0});

        assertThrows(IllegalArgumentException.class,
                () -> mNavigationService.onNavigationStateChanged(bundle));
    }

    private void checkValidClusterNavigation() throws Exception {
        assertThat(mNavigationService.getInstrumentClusterInfo()).isEqualTo(
                mInstrumentClusterNavigation.mClusterInfo);
        Bundle bundle = new Bundle();
        bundle.putByteArray(
                NAV_STATE_PROTO_BUNDLE_KEY,
                NavigationStateProto.getDefaultInstance().toByteArray());
        mNavigationService.onNavigationStateChanged(bundle);
        assertThat(bundle).isEqualTo(mInstrumentClusterNavigation.mLastBundle);
    }

    private static class IInstrumentClusterNavigationImpl
            extends IInstrumentClusterNavigation.Stub {

        private final CarNavigationInstrumentCluster mClusterInfo =
                CarNavigationInstrumentCluster.createCustomImageCluster(/*minIntervalMs= */ 100,
                        /* imageWidth= */ 800, /* imageHeight= */ 480,
                        /* imageColorDepthBits= */ 32);

        private Bundle mLastBundle;
        public boolean mThrowExceptionInGetInstrumentClusterInfo;

        @Override
        public void onNavigationStateChanged(Bundle bundle) {
            mLastBundle = bundle;
        }

        @Override
        public CarNavigationInstrumentCluster getInstrumentClusterInfo() throws RemoteException {
            if (mThrowExceptionInGetInstrumentClusterInfo) {
                throw new RemoteException();
            }
            return mClusterInfo;
        }
    }
}
