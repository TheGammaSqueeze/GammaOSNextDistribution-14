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

package com.android.car.setupwizardlib;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.RuntimeEnvironment.application;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.StyleRes;

import com.android.car.setupwizardlib.partner.ExternalResources;
import com.android.car.setupwizardlib.partner.FakeOverrideContentProvider;
import com.android.car.setupwizardlib.partner.PartnerConfig;
import com.android.car.setupwizardlib.partner.ResourceEntry;
import com.android.car.setupwizardlib.robolectric.BaseRobolectricTest;
import com.android.car.setupwizardlib.robolectric.TestHelper;
import com.android.car.setupwizardlib.shadows.ShadowConfiguration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowTextView;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tests for the CarSetupWizardCompatLayout
 */
@RunWith(RobolectricTestRunner.class)
@Config(shadows = ShadowConfiguration.class)
public class CarSetupWizardCompatLayoutTest extends BaseRobolectricTest {
    private static final Locale LOCALE_EN_US = new Locale("en", "US");
    // Hebrew locale can be used to test RTL.
    private static final Locale LOCALE_IW_IL = new Locale("iw", "IL");

    private CarSetupWizardCompatLayout mCarSetupWizardCompatLayout;
    private CarSetupWizardLayoutInterface mCarSetupWizardLayoutInterface;

    private static final String TEST_PACKAGE_NAME = "test.packageName";

    private static final PartnerConfig TEST_TOOLBAR_BUTTON_TEXT_SIZE_RESOURCE_NAME =
            PartnerConfig.CONFIG_TOOLBAR_BUTTON_TEXT_SIZE;

    private static final float TOLERANCE = 0.001f;
    // A small value is picked so that it's not likely to coincide with the default font size
    private static final float EXCEPTED_TEXT_SIZE = 4;

    @Before
    public void setUp() {
        FakeOverrideContentProvider fakeOverrideDataProvider =
                FakeOverrideContentProvider.installEmptyProvider();
        List<ResourceEntry> resourceEntries = prepareFakeData();
        for (ResourceEntry entry : resourceEntries) {
            fakeOverrideDataProvider.injectResourceEntry(entry);
        }

        mCarSetupWizardCompatLayout = createCarSetupWizardCompatLayout();
        mCarSetupWizardLayoutInterface =
                (CarSetupWizardLayoutInterface) mCarSetupWizardCompatLayout;
        // Have to make this call first to ensure secondaryActionButton is created from stub.
        mCarSetupWizardLayoutInterface.setSecondaryActionButtonVisible(true);
        mCarSetupWizardLayoutInterface.setSecondaryActionButtonVisible(false);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setBackButtonListener} does set the back button
     * listener.
     */
    @Test
    public void testSetBackButtonListener() {
        View.OnClickListener spyListener = TestHelper.createSpyListener();

        mCarSetupWizardCompatLayout.setBackButtonListener(spyListener);
        mCarSetupWizardCompatLayout.getBackButton().performClick();
        Mockito.verify(spyListener).onClick(mCarSetupWizardCompatLayout.getBackButton());
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setCloseButtonListener} does set the close button
     * listener.
     */
    @Test
    public void testSetCloseButtonListener() {
        View.OnClickListener spyListener = TestHelper.createSpyListener();

        mCarSetupWizardCompatLayout.setCloseButtonListener(spyListener);
        mCarSetupWizardCompatLayout.getCloseButton().performClick();
        Mockito.verify(spyListener).onClick(mCarSetupWizardCompatLayout.getCloseButton());
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setBackButtonVisible} does set the view
     * visible/not visible and calls
     * {@link CarSetupWizardDesignLayout#updateNavigationButtonTouchDelegate(View, boolean)}.
     */
    @Test
    public void testSetBackButtonVisibleTrue() {
        CarSetupWizardCompatLayout spyCarSetupWizardCompatLayout =
                Mockito.spy(mCarSetupWizardCompatLayout);

        spyCarSetupWizardCompatLayout.setBackButtonVisible(true);
        View backButton = spyCarSetupWizardCompatLayout.getBackButton();
        TestHelper.assertViewVisible(backButton);
        Mockito.verify(spyCarSetupWizardCompatLayout)
                .updateNavigationButtonTouchDelegate(backButton, true);
        View closeButton = spyCarSetupWizardCompatLayout.getCloseButton();
        TestHelper.assertViewNotVisible(closeButton);
        Mockito.verify(spyCarSetupWizardCompatLayout)
                .updateNavigationButtonTouchDelegate(closeButton, false);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setBackButtonVisible} does set the view
     * visible/not visible and calls
     * {@link CarSetupWizardDesignLayout#updateNavigationButtonTouchDelegate(View, boolean)}.
     */
    @Test
    public void testSetBackButtonVisibleFalse() {
        CarSetupWizardCompatLayout spyCarSetupWizardCompatLayout =
                Mockito.spy(mCarSetupWizardCompatLayout);

        spyCarSetupWizardCompatLayout.setBackButtonVisible(false);
        View backButton = spyCarSetupWizardCompatLayout.getBackButton();
        TestHelper.assertViewNotVisible(backButton);
        Mockito.verify(spyCarSetupWizardCompatLayout)
                .updateNavigationButtonTouchDelegate(backButton, false);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setCloseButtonVisible} does set the view
     * visible/not visible and calls
     * {@link CarSetupWizardDesignLayout#updateNavigationButtonTouchDelegate(View, boolean)}.
     */
    @Test
    public void testSetCloseButtonVisibleTrue() {
        CarSetupWizardCompatLayout spyCarSetupWizardCompatLayout =
                Mockito.spy(mCarSetupWizardCompatLayout);

        spyCarSetupWizardCompatLayout.setCloseButtonVisible(true);
        View closeButton = spyCarSetupWizardCompatLayout.getCloseButton();
        TestHelper.assertViewVisible(closeButton);
        Mockito.verify(spyCarSetupWizardCompatLayout)
                .updateNavigationButtonTouchDelegate(closeButton, true);
        View backButton = spyCarSetupWizardCompatLayout.getBackButton();
        TestHelper.assertViewNotVisible(backButton);
        Mockito.verify(spyCarSetupWizardCompatLayout)
                .updateNavigationButtonTouchDelegate(backButton, false);
    }


    /**
     * Test that {@link CarSetupWizardCompatLayout#setCloseButtonVisible} does set the view
     * visible/not visible and calls
     * {@link CarSetupWizardDesignLayout#updateNavigationButtonTouchDelegate(View, boolean)}.
     */
    @Test
    public void testSetCloseButtonVisibleFalse() {
        CarSetupWizardCompatLayout spyCarSetupWizardCompatLayout =
                Mockito.spy(mCarSetupWizardCompatLayout);

        spyCarSetupWizardCompatLayout.setCloseButtonVisible(false);
        View closeButton = spyCarSetupWizardCompatLayout.getCloseButton();
        TestHelper.assertViewNotVisible(closeButton);
        Mockito.verify(spyCarSetupWizardCompatLayout)
                .updateNavigationButtonTouchDelegate(closeButton, false);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setToolbarTitleVisible} does set the view
     * visible/not visible.
     */
    @Test
    public void testSetToolbarTitleVisibleTrue() {
        View toolbarTitle = mCarSetupWizardCompatLayout.getToolbarTitle();

        mCarSetupWizardCompatLayout.setToolbarTitleVisible(true);
        TestHelper.assertViewVisible(toolbarTitle);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setToolbarTitleVisible} does set the view
     * visible/not visible.
     */
    @Test
    public void testSetToolbarTitleVisibleFalse() {
        View toolbarTitle = mCarSetupWizardCompatLayout.getToolbarTitle();

        mCarSetupWizardCompatLayout.setToolbarTitleVisible(false);
        TestHelper.assertViewNotVisible(toolbarTitle);
    }

    /**
     * Tests that {@link CarSetupWizardCompatLayout#setToolbarTitleText(String)} does set the
     * toolbar title text.
     */
    @Test
    public void testSetToolbarTitleText() {
        mCarSetupWizardCompatLayout.setToolbarTitleText("test title");
        TestHelper.assertTextEqual(mCarSetupWizardCompatLayout.getToolbarTitle(), "test title");
    }

    /**
     * Test that a call to setToolbarTitleStyle sets the text appearance on the toolbar title.
     */
    @Test
    public void testSetToolbarStyle() {
        @StyleRes int newStyle = R.style.TextAppearance_Car_Body2;
        mCarSetupWizardCompatLayout.setToolbarTitleStyle(newStyle);
        ShadowTextView shadowTextView =
                Shadows.shadowOf(mCarSetupWizardCompatLayout.getToolbarTitle());
        assertThat(shadowTextView.getTextAppearanceId()).isEqualTo(newStyle);
    }

    /**
     * Test that any call to setToolbarTitle calls toolbar's setText when split-nav is enabled.
     */
    @Test
    public void testSetToolbarTitleWhenSplitNavEnabled() {
        CarSetupWizardCompatLayout spyCarSetupWizardCompatLayout =
                Mockito.spy(mCarSetupWizardCompatLayout);
        TextView spyToolbar = Mockito.spy(mCarSetupWizardCompatLayout.getToolbarTitle());
        spyCarSetupWizardCompatLayout.setToolbarTitle(null);

        spyCarSetupWizardCompatLayout.setToolbarTitleText("test title");

        Mockito.verify(spyToolbar, Mockito.never()).setText("test title");
    }

    /**
     * Test that any call to setToolbarTitleStyle calls toolbar's setTextAppearance when split-nav
     * is enabled.
     */
    @Test
    public void testSetToolbarStyleWhenSplitNavEnabled() {
        @StyleRes int newStyle = R.style.TextAppearance_Car_Body2;
        CarSetupWizardCompatLayout spyCarSetupWizardCompatLayout =
                Mockito.spy(mCarSetupWizardCompatLayout);
        TextView spyToolbar = Mockito.spy(mCarSetupWizardCompatLayout.getToolbarTitle());
        spyCarSetupWizardCompatLayout.setToolbarTitle(null);

        spyCarSetupWizardCompatLayout.setToolbarTitleStyle(newStyle);

        Mockito.verify(spyToolbar, Mockito.never()).setTextAppearance(newStyle);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setPrimaryActionButtonVisible} does set the view
     * visible/not visible.
     */
    @Test
    public void testSetPrimaryActionButtonVisibleTrue() {
        View primaryButton = mCarSetupWizardLayoutInterface.getPrimaryActionButton();

        mCarSetupWizardLayoutInterface.setPrimaryActionButtonVisible(true);
        TestHelper.assertViewVisible(primaryButton);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setPrimaryActionButtonVisible} does set the view
     * visible/not visible.
     */
    @Test
    public void testSetPrimaryActionButtonVisibleFalse() {
        View primaryButton = mCarSetupWizardLayoutInterface.getPrimaryActionButton();

        mCarSetupWizardLayoutInterface.setPrimaryActionButtonVisible(false);
        TestHelper.assertViewNotVisible(primaryButton);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setPrimaryActionButtonEnabled} does set the view
     * enabled/not enabled.
     */
    @Test
    public void testSetPrimaryActionButtonEnabledTrue() {
        View primaryButton = mCarSetupWizardLayoutInterface.getPrimaryActionButton();

        mCarSetupWizardLayoutInterface.setPrimaryActionButtonEnabled(true);
        TestHelper.assertViewEnabled(primaryButton);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setPrimaryActionButtonEnabled} does set the view
     * enabled/not enabled.
     */
    @Test
    public void testSetPrimaryActionButtonEnabledFalse() {
        View primaryButton = mCarSetupWizardLayoutInterface.getPrimaryActionButton();

        mCarSetupWizardLayoutInterface.setPrimaryActionButtonEnabled(false);
        TestHelper.assertViewNotEnabled(primaryButton);
    }

    /**
     * Tests that {@link CarSetupWizardCompatLayout#setPrimaryActionButtonText(String)} does set
     * the primary action button text.
     */
    @Test
    public void testSetPrimaryActionButtonText() {
        mCarSetupWizardLayoutInterface.setPrimaryActionButtonText("test title");
        TestHelper.assertTextEqual(
                mCarSetupWizardLayoutInterface.getPrimaryActionButton(), "test title");
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setPrimaryActionButtonListener} does set the
     * primary action button listener.
     */
    @Test
    public void testSetPrimaryActionButtonListener() {
        View.OnClickListener spyListener = TestHelper.createSpyListener();

        mCarSetupWizardLayoutInterface.setPrimaryActionButtonListener(spyListener);
        mCarSetupWizardLayoutInterface.getPrimaryActionButton().performClick();
        Mockito.verify(spyListener).onClick(
                mCarSetupWizardLayoutInterface.getPrimaryActionButton());
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#createPrimaryToolbarButton} creates a new button
     * but holds over the correct attributes.
     */
    @Test
    public void testCreatePrimaryButtonTrue() {
        Button currPrimaryActionButton = mCarSetupWizardLayoutInterface.getPrimaryActionButton();
        Button primaryActionButton = mCarSetupWizardCompatLayout.createPrimaryToolbarButton(true);

        assertThat(primaryActionButton.getVisibility()).isEqualTo(
                currPrimaryActionButton.getVisibility());
        assertThat(primaryActionButton.isEnabled()).isEqualTo(
                currPrimaryActionButton.isEnabled());
        assertThat(primaryActionButton.getText()).isEqualTo(currPrimaryActionButton.getText());
        assertThat(primaryActionButton.getLayoutParams()).isEqualTo(
                currPrimaryActionButton.getLayoutParams());
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setSecondaryActionButtonVisible} does set the
     * view visible/not visible.
     */
    @Test
    public void testSetSecondaryActionButtonVisibleTrue() {
        View secondaryButton = mCarSetupWizardLayoutInterface.getSecondaryActionButton();

        mCarSetupWizardLayoutInterface.setSecondaryActionButtonVisible(true);
        TestHelper.assertViewVisible(secondaryButton);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setSecondaryActionButtonVisible} does set the
     * view visible/not visible.
     */
    @Test
    public void testSetSecondaryActionButtonVisibleFalse() {
        View secondaryButton = mCarSetupWizardLayoutInterface.getSecondaryActionButton();

        mCarSetupWizardLayoutInterface.setSecondaryActionButtonVisible(false);
        TestHelper.assertViewNotVisible(secondaryButton);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setSecondaryActionButtonEnabled} does set the
     * view enabled/not enabled.
     */
    @Test
    public void testSetSecondaryActionButtonEnabledTrue() {
        View secondaryButton = mCarSetupWizardLayoutInterface.getSecondaryActionButton();

        mCarSetupWizardLayoutInterface.setSecondaryActionButtonEnabled(true);
        TestHelper.assertViewEnabled(secondaryButton);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setSecondaryActionButtonEnabled} does set the
     * view enabled/not enabled.
     */
    @Test
    public void testSetSecondaryActionButtonEnabledFalse() {
        View secondaryButton = mCarSetupWizardLayoutInterface.getSecondaryActionButton();

        mCarSetupWizardLayoutInterface.setSecondaryActionButtonEnabled(false);
        TestHelper.assertViewNotEnabled(secondaryButton);
    }

    /**
     * Tests that {@link CarSetupWizardCompatLayout#setSecondaryActionButtonText(String)} does set
     * the secondary action button text.
     */
    @Test
    public void testSetSecondaryActionButtonText() {
        mCarSetupWizardLayoutInterface.setSecondaryActionButtonText("test title");
        TestHelper.assertTextEqual(
                mCarSetupWizardLayoutInterface.getSecondaryActionButton(), "test title");
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setSecondaryActionButtonListener} does set the
     * secondary action button listener.
     */
    @Test
    public void testSetSecondaryActionButtonListener() {
        View.OnClickListener spyListener = TestHelper.createSpyListener();

        mCarSetupWizardLayoutInterface.setSecondaryActionButtonListener(spyListener);
        mCarSetupWizardLayoutInterface.getSecondaryActionButton().performClick();
        Mockito.verify(spyListener)
                .onClick(mCarSetupWizardLayoutInterface.getSecondaryActionButton());
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setProgressBarVisible} does set the view
     * visible/not visible.
     */
    @Test
    public void testSetProgressBarVisibleTrue() {
        View progressBar = mCarSetupWizardCompatLayout.getProgressBar();

        mCarSetupWizardLayoutInterface.setProgressBarVisible(true);
        TestHelper.assertViewVisible(progressBar);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setProgressBarVisible} does set the view
     * visible/not visible.
     */
    @Test
    public void testSetProgressBarVisibleFalse() {
        View progressBar = mCarSetupWizardCompatLayout.getProgressBar();

        mCarSetupWizardLayoutInterface.setProgressBarVisible(false);
        TestHelper.assertViewNotVisible(progressBar);
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setProgressBarIndeterminate(boolean)}
     * does set the progress bar intermediate/not indeterminate.
     */
    @Test
    public void testSetProgressBarIndeterminateTrue() {
        mCarSetupWizardLayoutInterface.setProgressBarIndeterminate(true);
        assertThat(mCarSetupWizardCompatLayout.getProgressBar().isIndeterminate()).isTrue();
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setProgressBarIndeterminate(boolean)}
     * does set the progress bar intermediate/not indeterminate.
     */
    @Test
    public void testSetProgressBarIndeterminateFalse() {
        mCarSetupWizardLayoutInterface.setProgressBarIndeterminate(false);
        assertThat(mCarSetupWizardCompatLayout.getProgressBar().isIndeterminate()).isFalse();
    }

    /**
     * Test that {@link CarSetupWizardCompatLayout#setProgressBarProgress} does set the progress.
     */
    @Test
    public void testSetProgressBarProgress() {
        mCarSetupWizardLayoutInterface.setProgressBarProgress(80);
        assertThat(mCarSetupWizardCompatLayout.getProgressBar().getProgress()).isEqualTo(80);
    }

    @Test
    public void testApplyUpdatedLocale() {
        mCarSetupWizardCompatLayout.applyLocale(LOCALE_IW_IL);
        TextView toolbarTitle = mCarSetupWizardCompatLayout.getToolbarTitle();
        Button primaryActionButton = mCarSetupWizardLayoutInterface.getPrimaryActionButton();
        Button secondaryActionButton = mCarSetupWizardLayoutInterface.getSecondaryActionButton();

        assertThat(toolbarTitle.getTextLocale()).isEqualTo(LOCALE_IW_IL);
        assertThat(primaryActionButton.getTextLocale()).isEqualTo(LOCALE_IW_IL);
        assertThat(secondaryActionButton.getTextLocale()).isEqualTo(LOCALE_IW_IL);

        mCarSetupWizardCompatLayout.applyLocale(LOCALE_EN_US);
        assertThat(toolbarTitle.getTextLocale()).isEqualTo(LOCALE_EN_US);
        assertThat(primaryActionButton.getTextLocale()).isEqualTo(LOCALE_EN_US);
        assertThat(secondaryActionButton.getTextLocale()).isEqualTo(LOCALE_EN_US);
    }

    @Test
    public void testApplyUpdatedLocaleWhenSplitNavEnabled() {
        CarSetupWizardCompatLayout spyCarSetupWizardCompatLayout =
                Mockito.spy(mCarSetupWizardCompatLayout);
        TextView spyToolbar = Mockito.spy(mCarSetupWizardCompatLayout.getToolbarTitle());
        spyCarSetupWizardCompatLayout.setToolbarTitle(null);

        spyCarSetupWizardCompatLayout.applyLocale(LOCALE_EN_US);

        Mockito.verify(spyToolbar, Mockito.never()).setTextLocale(LOCALE_EN_US);
        Mockito.verify(spyToolbar, Mockito.never())
                .setLayoutDirection(TextUtils.getLayoutDirectionFromLocale(LOCALE_EN_US));
    }

    @Test
    public void testGetBackButton() {
        assertThat(mCarSetupWizardLayoutInterface.getPrimaryActionButton()).isEqualTo(
                mCarSetupWizardCompatLayout.findViewById(R.id.primary_toolbar_button));
    }

    @Test
    public void testGetToolBarTitle() {
        assertThat(mCarSetupWizardCompatLayout.getToolbarTitle()).isEqualTo(
                mCarSetupWizardCompatLayout.findViewById(R.id.toolbar_title));
    }

    @Test
    public void testGetPrimaryActionButton() {
        assertThat(mCarSetupWizardLayoutInterface.getPrimaryActionButton()).isEqualTo(
                mCarSetupWizardCompatLayout.findViewById(R.id.primary_toolbar_button));
    }

    @Test
    public void testGetSecondaryActionButton() {
        assertThat(mCarSetupWizardLayoutInterface.getSecondaryActionButton()).isEqualTo(
                mCarSetupWizardCompatLayout.findViewById(R.id.secondary_toolbar_button));
    }

    @Test
    public void testGetProgressBar() {
        assertThat(mCarSetupWizardCompatLayout.getProgressBar()).isEqualTo(
                mCarSetupWizardCompatLayout.findViewById(R.id.progress_bar));
    }

    @Test
    public void testPartnerResourcesAreApplied() {
        setupFakeContentProvider();

        CarSetupWizardCompatLayout layout = createCarSetupWizardCompatLayout();

        // Verify primary button background
        Button primary = layout.getPrimaryActionButton();
        Drawable expected = application.getResources().getDrawable(R.drawable.button_ripple_bg);
        assertThat(getDrawableDefaultColor(primary.getBackground()))
                .isEqualTo(getDrawableDefaultColor(expected));

        // Verify primary button text size
        assertThat(primary.getTextSize())
                .isEqualTo(FakeOverrideContentProvider.DEFAULT_DIMENSION);

        // Verify paddings
        assertThat(primary.getPaddingStart())
                .isEqualTo(FakeOverrideContentProvider.DEFAULT_H_PADDING);

        assertThat(primary.getPaddingEnd())
                .isEqualTo(FakeOverrideContentProvider.DEFAULT_H_PADDING);

        assertThat(primary.getPaddingTop())
                .isEqualTo(FakeOverrideContentProvider.DEFAULT_V_PADDING);

        assertThat(primary.getPaddingBottom())
                .isEqualTo(FakeOverrideContentProvider.DEFAULT_V_PADDING);
    }

    @Test
    public void testShouldNotApplyLayoutBackground() {
        setupFakeContentProvider();
        CarSetupWizardCompatLayout layout = createCarSetupWizardCompatLayout();

        ColorDrawable bg = (ColorDrawable) layout.getBackground();
        assertThat(bg.getColor()).isEqualTo(
                application.getResources().getColor(R.color.suw_color_background));
    }

    @Test
    public void testSetButtonTextColor() {
        setupFakeContentProvider();
        CarSetupWizardCompatLayout layout = createCarSetupWizardCompatLayout();
        Button primary = layout.getPrimaryActionButton();

        layout.setButtonTextColor(
                primary, PartnerConfig.CONFIG_LAYOUT_BG_COLOR);

        assertThat(primary.getCurrentTextColor())
                .isEqualTo(FakeOverrideContentProvider.ANDROID_COLOR_DARK_GRAY);
    }

    @Test
    public void testSetBackground() {
        setupFakeContentProvider();
        CarSetupWizardCompatLayout layout = createCarSetupWizardCompatLayout();
        layout.setSecondaryActionButtonVisible(true);
        Button secondary = layout.getSecondaryActionButton();

        layout.setBackground(
                secondary,
                PartnerConfig.CONFIG_TOOLBAR_PRIMARY_BUTTON_BG,
                PartnerConfig.CONFIG_TOOLBAR_SECONDARY_BUTTON_BG_COLOR);

        Drawable expected = application.getResources().getDrawable(R.drawable.button_ripple_bg);
        assertThat(getDrawableDefaultColor(secondary.getBackground()))
                .isEqualTo(getDrawableDefaultColor(expected));
    }

    @Test
    public void test_bothButtons_areStyled_inDefaultLayout() {
        Button primaryButton = mCarSetupWizardLayoutInterface.getPrimaryActionButton();
        Button secondaryButton = mCarSetupWizardLayoutInterface.getSecondaryActionButton();

        assertThat(primaryButton.getTextSize()).isWithin(TOLERANCE).of(EXCEPTED_TEXT_SIZE);
        assertThat(secondaryButton.getTextSize()).isWithin(TOLERANCE).of(EXCEPTED_TEXT_SIZE);
    }

    @Test
    public void test_bothButtons_areStyled_inAlternativeLayout() {
        Activity activity = Robolectric
                .buildActivity(CarSetupWizardLayoutAlternativeActivity.class)
                .create()
                .get();
        CarSetupWizardCompatLayout layout = activity.findViewById(R.id.car_setup_wizard_layout);

        Button primaryButton = layout.getPrimaryActionButton();
        Button secondaryButton = layout.getSecondaryActionButton();

        assertThat(primaryButton.getTextSize()).isWithin(TOLERANCE).of(EXCEPTED_TEXT_SIZE);
        assertThat(secondaryButton.getTextSize()).isWithin(TOLERANCE).of(EXCEPTED_TEXT_SIZE);
    }

    @Test
    public void test_shouldNotMirrorNavIcons_inLtr() {
        Activity activity = Robolectric.buildActivity(CarSetupWizardLayoutTestActivity.class)
                .create()
                .get();

        CarSetupWizardCompatLayout layout = activity.findViewById(R.id.car_setup_wizard_layout);
        assertThat(layout.shouldMirrorNavIcons()).isFalse();
    }

    @Test
    public void test_shouldMirrorNavIcons_inRtl() {
        application.getResources().getConfiguration().setLocale(LOCALE_IW_IL);

        Activity activity = Robolectric.buildActivity(CarSetupWizardLayoutTestActivity.class)
                .create()
                .get();

        CarSetupWizardCompatLayout layout = activity.findViewById(R.id.car_setup_wizard_layout);
        View toolbar = layout.findViewById(R.id.application_bar);
        assertThat(toolbar.getLayoutDirection()).isEqualTo(View.LAYOUT_DIRECTION_LTR);
        assertThat(layout.shouldMirrorNavIcons()).isTrue();
    }

    private void setupFakeContentProvider() {
        FakeOverrideContentProvider.installDefaultProvider();
    }

    private CarSetupWizardCompatLayout createCarSetupWizardCompatLayout() {
        Activity activity = Robolectric
                .buildActivity(CarSetupWizardLayoutTestActivity.class)
                .create()
                .get();

        return activity.findViewById(R.id.car_setup_wizard_layout);
    }

    private @ColorRes int getDrawableDefaultColor(Drawable drawable) {
        Drawable.ConstantState state = drawable.getConstantState();
        ColorStateList colorStateList = ReflectionHelpers.getField(state, "mColor");
        return colorStateList.getDefaultColor();
    }

    private List<ResourceEntry> prepareFakeData() {
        ExternalResources.Resources testResources =
                ExternalResources.injectExternalResources(TEST_PACKAGE_NAME);

        testResources.putDimension(
                TEST_TOOLBAR_BUTTON_TEXT_SIZE_RESOURCE_NAME.getResourceName(), EXCEPTED_TEXT_SIZE);

        return Arrays.asList(
                new ResourceEntry(
                        TEST_PACKAGE_NAME,
                        TEST_TOOLBAR_BUTTON_TEXT_SIZE_RESOURCE_NAME.getResourceName(),
                        testResources.getIdentifier(
                                TEST_TOOLBAR_BUTTON_TEXT_SIZE_RESOURCE_NAME.getResourceName(),
                                /* defType= */ "dimen",
                                TEST_PACKAGE_NAME))
        );
    }
}
