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

package com.android.systemui.qs;

import static com.android.systemui.classifier.Classifier.QS_SWIPE_SIDE;
import static com.android.systemui.media.dagger.MediaModule.QS_PANEL;
import static com.android.systemui.qs.QSPanel.QS_SHOW_BRIGHTNESS;
import static com.android.systemui.qs.dagger.QSScopeModule.QS_USING_MEDIA_PLAYER;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.Arrays;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.UiEventLogger;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.media.controls.ui.controller.MediaHierarchyManager;
import com.android.systemui.media.controls.ui.view.MediaHost;
import com.android.systemui.media.controls.ui.view.MediaHostState;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.qs.customize.QSCustomizerController;
import com.android.systemui.qs.dagger.QSScope;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.scene.shared.flag.SceneContainerFlags;
import com.android.systemui.settings.brightness.BrightnessController;
import com.android.systemui.settings.brightness.BrightnessMirrorHandler;
import com.android.systemui.settings.brightness.BrightnessSliderController;
import com.android.systemui.statusbar.phone.StatusBarKeyguardViewManager;
import com.android.systemui.statusbar.policy.BrightnessMirrorController;
import com.android.systemui.statusbar.policy.SplitShadeStateController;

import lineageos.providers.LineageSettings;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Controller for {@link QSPanel}.
 */
@QSScope
public class QSPanelController extends QSPanelControllerBase<QSPanel> {

    private static final String PROP_SPLIT_BRIGHTNESS =
            "persist.gammaos.multidisplay.split_brightness";
    private static final String PROP_SPLIT_BRIGHTNESS_D0_DISPLAY_ID =
            "persist.gammaos.multidisplay.split_brightness.d0.display_id";
    private static final String PROP_SPLIT_BRIGHTNESS_D1_DISPLAY_ID =
            "persist.gammaos.multidisplay.split_brightness.d1.display_id";
    private static final int DEFAULT_SECONDARY_DISPLAY_ID = 2;

    private final QSCustomizerController mQsCustomizerController;
    private final QSTileRevealController.Factory mQsTileRevealControllerFactory;
    private final FalsingManager mFalsingManager;
    private final StatusBarKeyguardViewManager mStatusBarKeyguardViewManager;
    private final DisplayManager mDisplayManager;

    private BrightnessController mBrightnessController;
    private BrightnessController mSecondaryBrightnessController;
    private BrightnessSliderController mBrightnessSliderController;
    private BrightnessSliderController mSecondaryBrightnessSliderController;
    private BrightnessMirrorHandler mBrightnessMirrorHandler;

    private boolean mListening;
    private boolean mSplitBrightnessEnabled;
    private int mPrimaryBrightnessDisplayId;
    private int mSecondaryBrightnessDisplayId;
    private boolean mSplitBrightnessSyspropCallbackRegistered;
    private final Runnable mSplitBrightnessSyspropCallback = this::onSplitBrightnessSyspropChanged;

    // GammaOS: Some property changes (adb setprop) do not trigger SystemProperties callbacks.
    // Poll while QS is attached so the shade updates immediately even without reportSyspropChanged().
    private boolean mSplitBrightnessPollRunning;
    private final Runnable mSplitBrightnessPoll = new Runnable() {
        @Override
        public void run() {
            if (!mSplitBrightnessPollRunning || mView == null) return;
            final boolean enabled = SystemProperties.getBoolean(PROP_SPLIT_BRIGHTNESS, false);
            final int primaryDisplayId = SystemProperties.getInt(
                    PROP_SPLIT_BRIGHTNESS_D0_DISPLAY_ID, getContext().getDisplayId());
            final int secondaryDisplayId = SystemProperties.getInt(
                    PROP_SPLIT_BRIGHTNESS_D1_DISPLAY_ID, DEFAULT_SECONDARY_DISPLAY_ID);
            if (enabled != mSplitBrightnessEnabled
                    || primaryDisplayId != mPrimaryBrightnessDisplayId
                    || secondaryDisplayId != mSecondaryBrightnessDisplayId) {
                updateSplitBrightnessFromProperties();
            }
            mView.postDelayed(this, 200);
        }
    };

    private final boolean mSceneContainerEnabled;

    private int mLastDensity;
    private final BrightnessSliderController.Factory mBrightnessSliderControllerFactory;
    private final BrightnessController.Factory mBrightnessControllerFactory;

    private View.OnTouchListener mTileLayoutTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                mFalsingManager.isFalseTouch(QS_SWIPE_SIDE);
            }
            return false;
        }
    };

    @Inject
    QSPanelController(QSPanel view,
            QSHost qsHost, QSCustomizerController qsCustomizerController,
            @Named(QS_USING_MEDIA_PLAYER) boolean usingMediaPlayer,
            @Named(QS_PANEL) MediaHost mediaHost,
            QSTileRevealController.Factory qsTileRevealControllerFactory,
            DumpManager dumpManager, MetricsLogger metricsLogger, UiEventLogger uiEventLogger,
            QSLogger qsLogger, BrightnessController.Factory brightnessControllerFactory,
            BrightnessSliderController.Factory brightnessSliderFactory,
            FalsingManager falsingManager,
            StatusBarKeyguardViewManager statusBarKeyguardViewManager,
            SplitShadeStateController splitShadeStateController,
            SceneContainerFlags sceneContainerFlags) {
        super(view, qsHost, qsCustomizerController, usingMediaPlayer, mediaHost,
                metricsLogger, uiEventLogger, qsLogger, dumpManager, splitShadeStateController);
        mQsCustomizerController = qsCustomizerController;
        mQsTileRevealControllerFactory = qsTileRevealControllerFactory;
        mFalsingManager = falsingManager;
        mBrightnessSliderControllerFactory = brightnessSliderFactory;
        mBrightnessControllerFactory = brightnessControllerFactory;

        mDisplayManager = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);

        mPrimaryBrightnessDisplayId = SystemProperties.getInt(
                PROP_SPLIT_BRIGHTNESS_D0_DISPLAY_ID, getContext().getDisplayId());
        mSecondaryBrightnessDisplayId = SystemProperties.getInt(
                PROP_SPLIT_BRIGHTNESS_D1_DISPLAY_ID, DEFAULT_SECONDARY_DISPLAY_ID);

        mBrightnessSliderController = brightnessSliderFactory.create(getContext(), mView);
        mSecondaryBrightnessSliderController = brightnessSliderFactory.create(getContext(), mView);

        // Keep primary brightness view as the actual slider view so QSAnimator can animate
        // sliderScaleY without crashing. Secondary is added as a sibling view below it.
        mView.setBrightnessView(mBrightnessSliderController.getRootView());
        mView.setSecondaryBrightnessView(mSecondaryBrightnessSliderController.getRootView());

        mBrightnessController = brightnessControllerFactory.create(
                mBrightnessSliderController, mPrimaryBrightnessDisplayId);
        mSecondaryBrightnessController = brightnessControllerFactory.create(
                mSecondaryBrightnessSliderController, mSecondaryBrightnessDisplayId);

        // Only the primary brightness slider participates in the standard brightness mirror flow.
        // The secondary slider must remain fully independent to avoid cross-coupling.
        mBrightnessMirrorHandler = new BrightnessMirrorHandler(Arrays.asList(
                mBrightnessController));

        if (!mSplitBrightnessSyspropCallbackRegistered) {
            mSplitBrightnessSyspropCallbackRegistered = true;
            SystemProperties.addChangeCallback(mSplitBrightnessSyspropCallback);
        }
        updateSplitBrightnessFromProperties();

        mStatusBarKeyguardViewManager = statusBarKeyguardViewManager;
        mLastDensity = view.getResources().getConfiguration().densityDpi;
        mSceneContainerEnabled = sceneContainerFlags.isEnabled();
    }

    @Override
    public void onInit() {
        super.onInit();
        mMediaHost.setExpansion(MediaHostState.EXPANDED);
        mMediaHost.setShowsOnlyActiveMedia(false);
        mMediaHost.init(MediaHierarchyManager.LOCATION_QS);
        mQsCustomizerController.init();
        mBrightnessSliderController.init();
        mSecondaryBrightnessSliderController.init();
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();

        updateMediaDisappearParameters();

        getContext().getContentResolver().registerContentObserver(
                LineageSettings.Secure.getUriFor(LineageSettings.Secure.QS_SHOW_AUTO_BRIGHTNESS),
                false, mView.getContentObserver());
        mView.getContentObserver().onChange(true,
                LineageSettings.Secure.getUriFor(LineageSettings.Secure.QS_SHOW_AUTO_BRIGHTNESS));
        getContext().getContentResolver().registerContentObserver(
                LineageSettings.Secure.getUriFor(LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER),
                false, mView.getContentObserver());
        mView.getContentObserver().onChange(true,
                LineageSettings.Secure.getUriFor(LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER));
        getContext().getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(QS_SHOW_BRIGHTNESS), false, mView.getContentObserver());
        mView.getContentObserver().onChange(true,
                Settings.Secure.getUriFor(QS_SHOW_BRIGHTNESS));
        mView.updateResources();
        mView.setSceneContainerEnabled(mSceneContainerEnabled);
        if (mView.isListening()) {
            refreshAllTiles();
        }
        switchTileLayout(true);
        mBrightnessMirrorHandler.onQsPanelAttached();
        PagedTileLayout pagedTileLayout= ((PagedTileLayout) mView.getOrCreateTileLayout());
        pagedTileLayout.setOnTouchListener(mTileLayoutTouchListener);

        // Start polling while attached so the shade reacts live to prop toggles.
        mSplitBrightnessPollRunning = true;
        mView.removeCallbacks(mSplitBrightnessPoll);
        mView.post(mSplitBrightnessPoll);
    }

    @Override
    protected QSTileRevealController createTileRevealController() {
        return mQsTileRevealControllerFactory.create(
                this, (PagedTileLayout) mView.getOrCreateTileLayout());
    }

    @Override
    protected void onViewDetached() {
        getContext().getContentResolver().unregisterContentObserver(mView.getContentObserver());
        mBrightnessMirrorHandler.onQsPanelDettached();
        mSplitBrightnessPollRunning = false;
        if (mView != null) {
            mView.removeCallbacks(mSplitBrightnessPoll);
        }
        super.onViewDetached();
    }

    @Override
    protected void onConfigurationChanged() {
        mView.updateResources();
        int newDensity = mView.getResources().getConfiguration().densityDpi;
        if (newDensity != mLastDensity) {
            mLastDensity = newDensity;
            reinflateBrightnessSlider();
        }

        if (mView.isListening()) {
            refreshAllTiles();
        }
    }


    private void onSplitBrightnessSyspropChanged() {
        // SystemProperties callbacks are global; always marshal back to the QS view thread.
        if (mView == null) {
            return;
        }
        mView.post(this::updateSplitBrightnessFromProperties);
    }

    private void updateSplitBrightnessFromProperties() {
        final boolean enabled = SystemProperties.getBoolean(PROP_SPLIT_BRIGHTNESS, false);

        final int primaryDisplayId = SystemProperties.getInt(
                PROP_SPLIT_BRIGHTNESS_D0_DISPLAY_ID, getContext().getDisplayId());
        final int secondaryDisplayId = SystemProperties.getInt(
                PROP_SPLIT_BRIGHTNESS_D1_DISPLAY_ID, DEFAULT_SECONDARY_DISPLAY_ID);

        mSplitBrightnessEnabled = enabled;
        mPrimaryBrightnessDisplayId = primaryDisplayId;
        mSecondaryBrightnessDisplayId = secondaryDisplayId;

        if (mBrightnessController != null) {
            mBrightnessController.setDisplayId(primaryDisplayId);
        }
        if (mSecondaryBrightnessController != null) {
            mSecondaryBrightnessController.setDisplayId(secondaryDisplayId);
        }

        final boolean hasSecondaryDisplay = enabled
                && mDisplayManager != null
                && mDisplayManager.getDisplay(secondaryDisplayId) != null;

        if (mSecondaryBrightnessSliderController != null) {
            mSecondaryBrightnessSliderController.getRootView().setVisibility(
                    hasSecondaryDisplay ? View.VISIBLE : View.GONE);
        }
 
        // GammaOS: force QS to re-measure and update margins immediately while shade is open.
        mView.updateResources();
        mView.requestLayout();
        mView.invalidate();

        if (mSecondaryBrightnessController == null) {
            return;
        }

        // Register callbacks whenever the secondary slider is visible/eligible.
        // Relying on QS "listening" state causes the secondary slider to become inert (no listener
        // attached) on some devices/flows, which matches the observed behavior (no logcat, no sysfs).
        if (hasSecondaryDisplay) {
            mSecondaryBrightnessController.registerCallbacks();
        } else {
            mSecondaryBrightnessController.unregisterCallbacks();
        }
    }

    private void reinflateBrightnessSlider() {
        mBrightnessController.unregisterCallbacks();
        mSecondaryBrightnessController.unregisterCallbacks();

        mPrimaryBrightnessDisplayId = SystemProperties.getInt(
                PROP_SPLIT_BRIGHTNESS_D0_DISPLAY_ID, getContext().getDisplayId());
        mSecondaryBrightnessDisplayId = SystemProperties.getInt(
                PROP_SPLIT_BRIGHTNESS_D1_DISPLAY_ID, DEFAULT_SECONDARY_DISPLAY_ID);

        mBrightnessSliderController =
                mBrightnessSliderControllerFactory.create(getContext(), mView);
        mSecondaryBrightnessSliderController =
                mBrightnessSliderControllerFactory.create(getContext(), mView);

        mView.setBrightnessView(mBrightnessSliderController.getRootView());
        mView.setSecondaryBrightnessView(mSecondaryBrightnessSliderController.getRootView());

        mBrightnessController = mBrightnessControllerFactory.create(
                mBrightnessSliderController, mPrimaryBrightnessDisplayId);
        mSecondaryBrightnessController = mBrightnessControllerFactory.create(
                mSecondaryBrightnessSliderController, mSecondaryBrightnessDisplayId);

        // Keep mirror attached only to the primary controller.
        mBrightnessMirrorHandler.setBrightnessControllers(Arrays.asList(
                mBrightnessController));

        mBrightnessSliderController.init();
        mSecondaryBrightnessSliderController.init();

        updateSplitBrightnessFromProperties();

        if (mListening) {
            mBrightnessController.registerCallbacks();
            // Secondary callbacks are registered conditionally by updateSplitBrightnessFromProperties().
        }
    }


    @Override
    protected void onSplitShadeChanged(boolean shouldUseSplitNotificationShade) {
        ((PagedTileLayout) mView.getOrCreateTileLayout())
                .forceTilesRedistribution("Split shade state changed");
        mView.setCanCollapse(!shouldUseSplitNotificationShade);
    }

    /** */
    public void setVisibility(int visibility) {
        mView.setVisibility(visibility);
    }

    /** */
    public void setListening(boolean listening, boolean expanded) {
        setListening(listening && expanded);

        if (listening != mListening) {
            mListening = listening;
            // Set the listening as soon as the QS fragment starts listening regardless of the
            //expansion, so it will update the current brightness before the slider is visible.
            if (listening) {
                mBrightnessController.registerCallbacks();
            } else {
                mBrightnessController.unregisterCallbacks();
            }
            updateSplitBrightnessFromProperties();
        }
    }

    public void setBrightnessMirror(BrightnessMirrorController brightnessMirrorController) {
        mBrightnessMirrorHandler.setController(brightnessMirrorController);
    }

    /** Update appearance of QSPanel. */
    public void updateResources() {
        mView.updateResources();
    }

    /** Update state of all tiles. */
    public void refreshAllTiles() {
        mBrightnessController.checkRestrictionAndSetEnabled();
        mSecondaryBrightnessController.checkRestrictionAndSetEnabled();
        super.refreshAllTiles();
    }

    /** Start customizing the Quick Settings. */
    public void showEdit(View view) {
        view.post(() -> {
            if (!mQsCustomizerController.isCustomizing()) {
                int[] loc = view.getLocationOnScreen();
                int x = loc[0] + view.getWidth() / 2;
                int y = loc[1] + view.getHeight() / 2;
                mQsCustomizerController.show(x, y, false);
            }
        });
    }

    public boolean isLayoutRtl() {
        return mView.isLayoutRtl();
    }

    /** */
    public void setPageListener(PagedTileLayout.PageListener listener) {
        mView.setPageListener(listener);
    }

    public boolean isShown() {
        return mView.isShown();
    }

    /** */
    public void setContentMargins(int startMargin, int endMargin) {
        mView.setContentMargins(startMargin, endMargin, mMediaHost.getHostView());
    }

    /** */
    public void setFooterPageIndicator(PageIndicator pageIndicator) {
        mView.setFooterPageIndicator(pageIndicator);
    }

    /** */
    public boolean isExpanded() {
        return mView.isExpanded();
    }

    void setPageMargin(int pageMargin) {
        mView.setPageMargin(pageMargin);
    }

    /**
     * Determines if bouncer expansion is between 0 and 1 non-inclusive.
     *
     * @return if bouncer is in transit
     */
    public boolean isBouncerInTransit() {
        return mStatusBarKeyguardViewManager.isPrimaryBouncerInTransit();
    }

    public int getPaddingBottom() {
        return mView.getPaddingBottom();
    }

    int getViewBottom() {
        return mView.getBottom();
    }
}

