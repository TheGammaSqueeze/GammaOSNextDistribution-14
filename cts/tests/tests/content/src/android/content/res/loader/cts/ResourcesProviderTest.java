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

package android.content.res.loader.cts;

import static android.content.om.cts.FabricatedOverlayFacilitator.OVERLAYABLE_NAME;
import static android.content.om.cts.FabricatedOverlayFacilitator.TARGET_COLOR_RES;
import static android.content.om.cts.FabricatedOverlayFacilitator.TARGET_STRING_RES;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.content.cts.R;
import android.content.om.FabricatedOverlay;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.om.cts.FabricatedOverlayFacilitator;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.graphics.Color;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.util.TypedValue;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.FileNotFoundException;
import java.io.IOException;

@AppModeNonSdkSandbox(reason = "SDK sandbox does not have access to OverlayManager.")
public class ResourcesProviderTest {
    private Context mContext;
    private OverlayManager mOverlayManager;
    private FabricatedOverlayFacilitator mFacilitator;
    private String mOverlayName;

    @Rule public TestName mTestName = new TestName();

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mOverlayManager = mContext.getSystemService(OverlayManager.class);
        mFacilitator = new FabricatedOverlayFacilitator(mContext);
        mFacilitator.removeAllOverlays();
        mOverlayName = mTestName.getMethodName();
    }

    @After
    public void tearDown() throws Exception {
        mFacilitator.removeAllOverlays();
    }

    @Test
    public void loaderOverlay_withRegisteredOverlay_shouldSucceed()
            throws PackageManager.NameNotFoundException, IOException {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay1", OVERLAYABLE_NAME));
        mOverlayManager.commit(transaction);
        OverlayInfo overlayInfo =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName()).get(0);

        final ResourcesProvider provider = ResourcesProvider.loadOverlay(overlayInfo);

        assertThat(provider).isNotNull();
    }

    @Test
    public void loaderOverlay_withNullOverlay_shouldFail() {
        assertThrows(NullPointerException.class, () -> ResourcesProvider.loadOverlay(null));
    }

    private OverlayInfo mockOverlayInfo(
            String overlayName,
            String targetOverlayableName,
            String baseCodePath,
            boolean isFabricated) {
        return new OverlayInfo(
                mContext.getPackageName(),
                overlayName,
                mContext.getPackageName(),
                targetOverlayableName,
                null /* category */,
                baseCodePath,
                0 /* state */,
                UserHandle.myUserId(),
                0 /* priority */,
                false /* isMutable */,
                isFabricated);
    }

    @Test
    public void loaderOverlay_withNonFabricateOverlay_shouldFail() {
        final OverlayInfo overlayInfo =
                mockOverlayInfo(
                        "overlayName",
                        "targetOverlayableName",
                        "baseCodePath",
                        false /* isFabricated */);

        assertThrows(
                IllegalArgumentException.class, () -> ResourcesProvider.loadOverlay(overlayInfo));
    }

    @Test
    public void loaderOverlay_withNullOverlayableName_shouldFail() {
        final OverlayInfo overlayInfo =
                mockOverlayInfo(
                        "overlayName",
                        null /* targetOverlayableName */,
                        "baseCodePath",
                        true /* isFabricated */);

        assertThrows(
                IllegalArgumentException.class, () -> ResourcesProvider.loadOverlay(overlayInfo));
    }

    @Test
    public void loaderOverlay_withEmptyOverlayableName_shouldFail() {
        final OverlayInfo overlayInfo =
                mockOverlayInfo(
                        "overlayName",
                        "" /* targetOverlayableName */,
                        "baseCodePath",
                        true /* isFabricated */);

        assertThrows(
                IllegalArgumentException.class, () -> ResourcesProvider.loadOverlay(overlayInfo));
    }

    @Test
    public void loaderOverlay_withNullOverlayName_shouldFail() {
        final OverlayInfo overlayInfo =
                mockOverlayInfo(
                        null /* overlayName */,
                        "targetOverlayableName",
                        "baseCodePath",
                        true /* isFabricated */);

        assertThrows(
                IllegalArgumentException.class, () -> ResourcesProvider.loadOverlay(overlayInfo));
    }

    @Test
    public void loaderOverlay_withEmptyOverlayName_shouldFail() {
        final OverlayInfo overlayInfo =
                mockOverlayInfo(
                        "" /* overlayName */,
                        "targetOverlayableName",
                        "baseCodePath",
                        true /* isFabricated */);

        assertThrows(
                IllegalArgumentException.class, () -> ResourcesProvider.loadOverlay(overlayInfo));
    }

    @Test
    public void loaderOverlay_withEmptyBasePath_shouldFail() {
        final OverlayInfo overlayInfo =
                mockOverlayInfo(
                        "overlayName",
                        "targetOverlayableName",
                        "" /* baseCodePath */,
                        true /* isFabricated */);

        assertThrows(
                IllegalArgumentException.class, () -> ResourcesProvider.loadOverlay(overlayInfo));
    }

    @Test
    public void loaderOverlay_withInvalidBasePath_shouldFail() {
        final OverlayInfo overlayInfo =
                mockOverlayInfo(
                        "overlayName",
                        "targetOverlayableName",
                        "baseCodePath",
                        true /* isFabricated */);

        assertThrows(FileNotFoundException.class, () -> ResourcesProvider.loadOverlay(overlayInfo));
    }

    @Test
    public void loaderOverlay_withNotExistedOverlay_shouldFail() throws Exception {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(
                mFacilitator.prepare("hello_overlay1", OVERLAYABLE_NAME));
        mOverlayManager.commit(transaction);
        OverlayInfo overlayInfo =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName()).get(0);
        final OverlayManagerTransaction unregisterTransaction =
                OverlayManagerTransaction.newInstance();
        unregisterTransaction.unregisterFabricatedOverlay(overlayInfo.getOverlayIdentifier());
        mOverlayManager.commit(unregisterTransaction);

        assertThrows(IOException.class, () -> ResourcesProvider.loadOverlay(overlayInfo));
    }

    private Context createNewResourcesContext() {
        Configuration configuration = new Configuration(mContext.getResources().getConfiguration());
        return mContext.createConfigurationContext(configuration);
    }

    @Test
    public void loaderOverlay_applyOnResources_shouldSucceed()
            throws PackageManager.NameNotFoundException, IOException {
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(mFacilitator.prepare(mOverlayName, OVERLAYABLE_NAME));
        mOverlayManager.commit(transaction);
        OverlayInfo overlayInfo =
                mOverlayManager.getOverlayInfosForTarget(mContext.getPackageName()).get(0);
        final ResourcesLoader loader = new ResourcesLoader();
        loader.addProvider(ResourcesProvider.loadOverlay(overlayInfo));

        final Resources resources = createNewResourcesContext().getResources();
        resources.addLoaders(loader);

        assertThat(resources.getColor(R.color.target_overlayable_color1)).isEqualTo(Color.WHITE);
        assertThat(resources.getString(R.string.target_overlayable_string1)).isEqualTo("HELLO");
    }

    @Test
    public void loaderOverlay_applyMultipleOverlays_shouldHaveUnionResult() throws IOException {
        final String packageName = mContext.getPackageName();
        FabricatedOverlay fabricatedOverlayForColor =
                new FabricatedOverlay("helloOverlayForColor", packageName);
        fabricatedOverlayForColor.setTargetOverlayable(OVERLAYABLE_NAME);
        fabricatedOverlayForColor.setResourceValue(
                TARGET_COLOR_RES, TypedValue.TYPE_INT_COLOR_ARGB8, Color.WHITE,
                null /* configuration */);
        FabricatedOverlay fabricatedOverlayForString =
                new FabricatedOverlay("helloOverlayForString", packageName);
        fabricatedOverlayForString.setTargetOverlayable(OVERLAYABLE_NAME);
        fabricatedOverlayForString.setResourceValue(
                TARGET_STRING_RES, TypedValue.TYPE_STRING, "HELLO", null /* configuration */);
        final OverlayManagerTransaction transaction = OverlayManagerTransaction.newInstance();
        transaction.registerFabricatedOverlay(fabricatedOverlayForColor);
        transaction.registerFabricatedOverlay(fabricatedOverlayForString);
        mOverlayManager.commit(transaction);

        final ResourcesLoader loader = new ResourcesLoader();
        for (OverlayInfo overlayInfo : mOverlayManager.getOverlayInfosForTarget(packageName)) {
            loader.addProvider(ResourcesProvider.loadOverlay(overlayInfo));
        }
        final Resources resources = createNewResourcesContext().getResources();
        resources.addLoaders(loader);

        assertThat(resources.getColor(R.color.target_overlayable_color1)).isEqualTo(Color.WHITE);
        assertThat(resources.getString(R.string.target_overlayable_string1)).isEqualTo("HELLO");
    }
}
