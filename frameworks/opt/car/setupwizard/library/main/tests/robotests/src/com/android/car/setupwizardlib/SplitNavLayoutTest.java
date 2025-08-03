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

package com.android.car.setupwizardlib;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import com.android.car.setupwizardlib.partner.ExternalResources;
import com.android.car.setupwizardlib.partner.FakeOverrideContentProvider;
import com.android.car.setupwizardlib.partner.PartnerConfig;
import com.android.car.setupwizardlib.partner.ResourceEntry;
import com.android.car.setupwizardlib.robolectric.BaseRobolectricTest;
import com.android.car.setupwizardlib.shadows.ShadowConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for split-nav layout
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowConfiguration.class)
public class SplitNavLayoutTest extends BaseRobolectricTest {
    private static final String TEST_PACKAGE_NAME = "test.packageName";

    private static final PartnerConfig ULTRA_WIDE_SCREEN_CONTENT_WIDTH_RESOURCE_NAME =
            PartnerConfig.CONFIG_ULTRA_WIDE_SCREEN_CONTENT_WIDTH;

    private static final int PARTNER_CONTENT_WIDTH = 1500;

    private FakeOverrideContentProvider mFakeOverrideContentProvider;

    @Before
    public void setUp() {
        mFakeOverrideContentProvider = FakeOverrideContentProvider.installEmptyProvider();
        FakeFeatureManagementProvider.installProvider();
    }

    @Test
    @Config(qualifiers = "w1760dp-land")
    public void test_UltraWideContentWidth_isSetToCustomWidth() {
        List<ResourceEntry> resourceEntries = prepareCustomContentWidth();
        for (ResourceEntry entry : resourceEntries) {
            mFakeOverrideContentProvider.injectResourceEntry(entry);
        }

        Activity activity = Robolectric.buildActivity(CarSetupWizardLayoutTestActivity.class)
                .create()
                .get();
        View contentContainer = activity.findViewById(R.id.ultra_wide_content_container);
        ViewGroup.LayoutParams layoutParams = contentContainer.getLayoutParams();
        assertThat(layoutParams.width).isEqualTo(PARTNER_CONTENT_WIDTH);
    }

    @Test
    @Config(qualifiers = "w1760dp-land")
    public void test_UltraWideContentWidthIsSetTo0_withoutCustomValue() {
        Activity activity = Robolectric.buildActivity(CarSetupWizardLayoutTestActivity.class)
                .create()
                .get();

        View contentContainer = activity.findViewById(R.id.ultra_wide_content_container);
        ViewGroup.LayoutParams layoutParams = contentContainer.getLayoutParams();
        assertThat(layoutParams.width).isEqualTo(0);
    }

    @Test
    @Config(qualifiers = "iw-w1250dp-land")
    public void test_layoutDirectionIsLtr_inRtrLocale() {
        Activity activity = Robolectric.buildActivity(CarSetupWizardLayoutTestActivity.class)
                .create()
                .get();

        View layout = activity.findViewById(R.id.car_setup_wizard_layout);
        assertThat(layout.getLayoutDirection()).isEqualTo(View.LAYOUT_DIRECTION_LTR);

        View buttonContainer = activity.findViewById(R.id.button_container);
        assertThat(buttonContainer.getLayoutDirection()).isEqualTo(View.LAYOUT_DIRECTION_LTR);
    }

    @Test
    @Config(qualifiers = "iw-w1760dp-land")
    public void test_layoutDirectionIsLtrInUltraWide_isRtrLocale() {
        Activity activity = Robolectric.buildActivity(CarSetupWizardLayoutTestActivity.class)
                .create()
                .get();

        View layout = activity.findViewById(R.id.car_setup_wizard_layout);
        assertThat(layout.getLayoutDirection()).isEqualTo(View.LAYOUT_DIRECTION_LTR);

        View buttonContainer = activity.findViewById(R.id.button_container);
        assertThat(buttonContainer.getLayoutDirection()).isEqualTo(View.LAYOUT_DIRECTION_LTR);
    }

    private List<ResourceEntry> prepareCustomContentWidth() {
        ExternalResources.Resources testResources =
                ExternalResources.injectExternalResources(TEST_PACKAGE_NAME);

        testResources.putDimension(
                ULTRA_WIDE_SCREEN_CONTENT_WIDTH_RESOURCE_NAME.getResourceName(),
                PARTNER_CONTENT_WIDTH);

        return Arrays.asList(
                new ResourceEntry(
                        TEST_PACKAGE_NAME,
                        ULTRA_WIDE_SCREEN_CONTENT_WIDTH_RESOURCE_NAME.getResourceName(),
                        testResources.getIdentifier(
                                ULTRA_WIDE_SCREEN_CONTENT_WIDTH_RESOURCE_NAME.getResourceName(),
                                /* defType= */ "dimen",
                                TEST_PACKAGE_NAME))
        );
    }
}
