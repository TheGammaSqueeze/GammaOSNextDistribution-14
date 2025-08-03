/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.content.om.cts;

import static android.content.Context.OVERLAY_SERVICE;
import static android.content.om.cts.FabricatedOverlayFacilitator.OVERLAYABLE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeNonSdkSandbox;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This only tests the client API implementation of the OverlayManager
 * and not the service implementation.
 */
@AppModeNonSdkSandbox(reason = "SDK sandbox does not have access to OverlayManager")
@RunWith(AndroidJUnit4.class)
public class OverlayManagerTest {
    private OverlayManager mManager;
    private Context mContext;
    private String mOverlayName;
    private FabricatedOverlayFacilitator mFacilitator;

    @Mock
    private IOverlayManager mMockService;

    @Rule public Expect expect = Expect.create();

    @Rule public TestName testName = new TestName();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mManager = new OverlayManager(mContext, mMockService);

        mOverlayName = testName.getMethodName();
        mFacilitator = new FabricatedOverlayFacilitator(mContext);
        mFacilitator.removeAllOverlays();
    }

    @After
    public void tearDown() throws Exception {
        mFacilitator.removeAllOverlays();
    }

    @Test
    public void testSetEnabled() throws Exception {
        String packageName = "overlay source package name";
        int userId = UserHandle.myUserId();
        UserHandle user = UserHandle.of(userId);
        verify(mMockService, times(0)).setEnabled(packageName, true, userId);
        when(mMockService.setEnabled(anyString(), any(Boolean.class), anyInt()))
                .thenReturn(Boolean.TRUE);
        mManager.setEnabled(packageName, true, user);
        verify(mMockService, times(1)).setEnabled(packageName, true, userId);
    }

    @Test
    public void testSetEnabledExclusiveInCategory() throws Exception {
        String packageName = "overlay source package name";
        int userId = UserHandle.myUserId();
        UserHandle user = UserHandle.of(userId);
        verify(mMockService, times(0)).setEnabledExclusiveInCategory(packageName, userId);
        when(mMockService.setEnabledExclusiveInCategory(anyString(), anyInt()))
                .thenReturn(Boolean.TRUE);
        mManager.setEnabledExclusiveInCategory(packageName, user);
        verify(mMockService, times(1)).setEnabledExclusiveInCategory(packageName, userId);
    }

    @Test
    public void testGetOverlayInfosForTarget() throws Exception {
        String targetPackageName = "overlay target package name";
        int userId = UserHandle.myUserId();
        UserHandle user = UserHandle.of(userId);
        verify(mMockService, times(0)).getOverlayInfosForTarget(targetPackageName, userId);
        mManager.getOverlayInfosForTarget(targetPackageName, user);
        verify(mMockService, times(1)).getOverlayInfosForTarget(targetPackageName, userId);
    }

    @Test
    public void getOverlayManagerFromContext_shouldSucceed() {
        final Object overlayManager = mContext.getSystemService(OverlayManager.class);

        expect.that(overlayManager).isNotNull();
        expect.that(overlayManager).isInstanceOf(OverlayManager.class);
    }

    @Test
    public void getOverlayManager_ByServiceName_shouldSucceed() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();

        final Object overlayManager = context.getSystemService(OVERLAY_SERVICE);

        expect.that(overlayManager).isNotNull();
        expect.that(overlayManager).isInstanceOf(OverlayManager.class);
    }

    @Test
    public void getOverlayInfosForTarget_byDefault_returnEmptyList() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final OverlayManager overlayManager = context.getSystemService(OverlayManager.class);

        final List<OverlayInfo> overlayInfoList =
                overlayManager.getOverlayInfosForTarget(context.getPackageName());

        expect.that(overlayInfoList).isNotNull();
        expect.that(overlayInfoList).isEmpty();
    }

    @Test
    public void getOverlayInfosForTarget_registerMultipleOverlays_shouldReturnRegisteredOverlays()
            throws PackageManager.NameNotFoundException, IOException {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final OverlayManager overlayManager = context.getSystemService(OverlayManager.class);
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay1", OVERLAYABLE_NAME));
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay2", OVERLAYABLE_NAME));
        overlayManager.commit(transaction);

        final List<OverlayInfo> overlayInfoList =
                overlayManager.getOverlayInfosForTarget(context.getPackageName());

        expect.that(overlayInfoList.size()).isEqualTo(2);
        assertThat(expect.hasFailures()).isFalse();
        List<String> overlays =
                overlayInfoList.stream()
                        .map(OverlayInfo::getOverlayName)
                        .collect(Collectors.toList());
        expect.that(overlays).containsExactly("hello_overlay1", "hello_overlay2");
    }

    private void assertOverlayInfoList(List<OverlayInfo> overlayInfos, String overlayName,
            String overlayableName, String targetPackageName) {
        expect.that(overlayInfos.size()).isEqualTo(1);
        assertThat(expect.hasFailures()).isFalse();
        expect.that(overlayInfos.get(0).getOverlayName()).isEqualTo(overlayName);
        expect.that(overlayInfos.get(0).getTargetOverlayableName()).isEqualTo(overlayableName);
        expect.that(overlayInfos.get(0).getTargetPackageName()).isEqualTo(
                targetPackageName);
    }

    @ApiTest(apis = {"android.content.om.OverlayManagerTransaction#newInstance",
            "android.content.om.OverlayManager#commit"})
    @Test
    public void commit_selfTargetOverlay_isSelfTargetingTransaction_shouldSucceed() {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, true /* isSelfTarget */);
        final OverlayManager overlayManager = mContext.getSystemService(OverlayManager.class);
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(fabricatedOverlay);

        overlayManager.commit(transaction);

        assertOverlayInfoList(overlayManager.getOverlayInfosForTarget(mContext.getPackageName()),
                mOverlayName, OVERLAYABLE_NAME, mContext.getPackageName());
    }

    @Test
    public void commit_notSelfTargetOverlay_isSelfTargetingTransaction_shouldSucceed() {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, false /* isSelfTarget */);
        final OverlayManager overlayManager = mContext.getSystemService(OverlayManager.class);
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(fabricatedOverlay);

        overlayManager.commit(transaction);

        assertOverlayInfoList(overlayManager.getOverlayInfosForTarget(mContext.getPackageName()),
                mOverlayName, OVERLAYABLE_NAME, mContext.getPackageName());
    }

    @Test
    public void commit_selfTargetOverlay_notSelfTargetingTransaction_shouldSucceed() {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, true /* isSelfTarget */);
        final OverlayManager overlayManager = mContext.getSystemService(OverlayManager.class);
        final OverlayManagerTransaction transaction = new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(fabricatedOverlay).build();

        overlayManager.commit(transaction);

        assertOverlayInfoList(overlayManager.getOverlayInfosForTarget(mContext.getPackageName()),
                mOverlayName, OVERLAYABLE_NAME, mContext.getPackageName());
    }

    @Test
    public void commit_notSelfTargetOverlay_notSelfTargetingTransaction_shouldSucceed() {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, false /* isSelfTarget */);
        final OverlayManager overlayManager = mContext.getSystemService(OverlayManager.class);
        final OverlayManagerTransaction transaction = new OverlayManagerTransaction.Builder()
                .registerFabricatedOverlay(fabricatedOverlay).build();

        overlayManager.commit(transaction);

        assertOverlayInfoList(overlayManager.getOverlayInfosForTarget(mContext.getPackageName()),
                mOverlayName, OVERLAYABLE_NAME, mContext.getPackageName());
    }
}
