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

package android.content.om.cts;

import static android.content.om.cts.FabricatedOverlayFacilitator.OVERLAYABLE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.pm.PackageManager;
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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@AppModeNonSdkSandbox(reason = "SDK sandbox does not have access to OverlayManager")
@RunWith(AndroidJUnit4.class)
public class OverlayManagerTransactionTest {
    private Context mContext;
    private OverlayManager mOverlayManager;
    private String mOverlayName;
    private FabricatedOverlayFacilitator mFacilitator;

    @Rule public Expect expect = Expect.create();

    @Rule public TestName testName = new TestName();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mOverlayManager = mContext.getSystemService(OverlayManager.class);

        mOverlayName = testName.getMethodName();
        mFacilitator = new FabricatedOverlayFacilitator(mContext);
        mFacilitator.removeAllOverlays();
    }

    @After
    public void tearDown() throws Exception {
        mFacilitator.removeAllOverlays();
    }

    @Test
    public void newInstance_shouldSucceed() {
        assertThat(OverlayManagerTransaction.newInstance()).isNotNull();
    }

    @ApiTest(apis = {"android.content.om.OverlayManagerTransaction#registerFabricatedOverlay"})
    @Test
    public void registerFabricatedOverlay_withNull_shouldFail() {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();

        assertThrows(NullPointerException.class, () -> transaction.registerFabricatedOverlay(null));
    }

    @ApiTest(apis = {"android.content.om.OverlayManagerTransaction#unregisterFabricatedOverlay"})
    @Test
    public void unregisterFabricatedOverlay_withNull_shouldFail() {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();

        assertThrows(NullPointerException.class,
                () -> transaction.unregisterFabricatedOverlay(null));
    }

    @ApiTest(apis = {"android.content.om.OverlayManager#commit"})
    @Test
    public void commit_withNullOverlayable_shouldFail() {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay1", null /* overlayableName */));

        assertThrows(IllegalArgumentException.class, () -> mOverlayManager.commit(transaction));
    }

    @ApiTest(apis = {"android.content.om.OverlayManager#commit"})
    @Test
    public void commit_withEmptyOverlayable_shouldFail() {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay1", "" /* overlayableName */));

        assertThrows(IllegalArgumentException.class, () -> mOverlayManager.commit(transaction));
    }

    @Test
    public void commit_withNonExistOverlayable_shouldFail() {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay1", "not_exist"));

        assertThrows(RuntimeException.class, () -> mOverlayManager.commit(transaction));
    }

    @Test
    public void commit_withValidOverlayable_shouldSucceed() {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay1", OVERLAYABLE_NAME));

        mOverlayManager.commit(transaction);

        final List<OverlayInfo> overlayInfoList =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        expect.that(overlayInfoList.size()).isEqualTo(1);
    }

    @Test
    public void commit_multipleRequests_shouldSucceed() {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay1", OVERLAYABLE_NAME));
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay2", OVERLAYABLE_NAME));
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay3", OVERLAYABLE_NAME));
        mOverlayManager.commit(transaction);
        List<OverlayInfo> overlayInfos =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        final int numberOfOverlaysWhenStart = overlayInfos.size();

        final OverlayManagerTransaction modifyingTransaction =
                OverlayManagerTransaction.newInstance();
        for (OverlayInfo overlayInfo : overlayInfos) {
            modifyingTransaction.unregisterFabricatedOverlay(overlayInfo.getOverlayIdentifier());
        }
        modifyingTransaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay", OVERLAYABLE_NAME));
        mOverlayManager.commit(modifyingTransaction);

        overlayInfos = mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        expect.that(numberOfOverlaysWhenStart).isEqualTo(3);
        expect.that(overlayInfos.size()).isEqualTo(1);
        List<String> overlays =
                overlayInfos.stream()
                        .map(OverlayInfo::getOverlayName)
                        .collect(Collectors.toList());
        expect.that(overlays).containsExactly("hello_overlay");
    }

    @Test
    public void commit_notSelfTargetOverlay_isSelfTargetingTransaction_shouldSucceed() {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, false /* isSelfTarget */);
        final OverlayManager overlayManager = mContext.getSystemService(OverlayManager.class);
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(fabricatedOverlay);

        overlayManager.commit(transaction);

        List<OverlayInfo> overlayInfoList =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        assertThat(overlayInfoList.size()).isEqualTo(1);
    }

    @ApiTest(apis = {"android.content.om.OverlayManager#commit"})
    @Test
    public void commit_selfTargetOverlay_isSelfTargetingTransaction_shouldSucceed()
            throws PackageManager.NameNotFoundException, IOException {
        final FabricatedOverlay fabricatedOverlay =
                mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME, true /* isSelfTarget */);
        final OverlayManager overlayManager = mContext.getSystemService(OverlayManager.class);
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(fabricatedOverlay);

        overlayManager.commit(transaction);

        List<OverlayInfo> overlayInfoList =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName());
        assertThat(overlayInfoList.size()).isEqualTo(1);
    }
}
