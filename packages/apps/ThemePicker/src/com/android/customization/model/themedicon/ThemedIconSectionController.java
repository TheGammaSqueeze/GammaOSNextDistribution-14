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
package com.android.customization.model.themedicon;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;

import com.android.customization.model.themedicon.domain.interactor.ThemedIconInteractor;
import com.android.customization.model.themedicon.domain.interactor.ThemedIconSnapshotRestorer;
import com.android.customization.module.logging.ThemesUserEventLogger;
import com.android.customization.picker.themedicon.ThemedIconSectionView;
import com.android.themepicker.R;
import com.android.wallpaper.model.CustomizationSectionController;

// TODO (b/311712452): Refactor CustomizationSectionController to use recommended arch UI components
/** The {@link CustomizationSectionController} for themed icon section. */
public class ThemedIconSectionController implements
        CustomizationSectionController<ThemedIconSectionView> {

    private static final String KEY_THEMED_ICON_ENABLED = "SAVED_THEMED_ICON_ENABLED";

    private final ThemedIconSwitchProvider mThemedIconOptionsProvider;
    private final ThemedIconInteractor mInteractor;
    private final ThemedIconSnapshotRestorer mSnapshotRestorer;
    private final Observer<Boolean> mIsActivatedChangeObserver;
    private final ThemesUserEventLogger mThemesUserEventLogger;

    private ThemedIconSectionView mThemedIconSectionView;
    private boolean mSavedThemedIconEnabled = false;

    public ThemedIconSectionController(
            ThemedIconSwitchProvider themedIconOptionsProvider,
            ThemedIconInteractor interactor,
            @Nullable Bundle savedInstanceState,
            ThemedIconSnapshotRestorer snapshotRestorer,
            ThemesUserEventLogger themesUserEventLogger) {
        mThemedIconOptionsProvider = themedIconOptionsProvider;
        mInteractor = interactor;
        mSnapshotRestorer = snapshotRestorer;
        mIsActivatedChangeObserver = isActivated -> {
            if (mThemedIconSectionView.isAttachedToWindow()) {
                mThemedIconSectionView.getSwitch().setChecked(isActivated);
            }
        };
        mThemesUserEventLogger = themesUserEventLogger;

        if (savedInstanceState != null) {
            mSavedThemedIconEnabled = savedInstanceState.getBoolean(
                    KEY_THEMED_ICON_ENABLED, /* defaultValue= */ false);
        }
    }

    @Override
    public boolean isAvailable(@Nullable Context context) {
        return context != null && mThemedIconOptionsProvider.isThemedIconAvailable();
    }

    @Override
    public ThemedIconSectionView createView(Context context) {
        mThemedIconSectionView =
                (ThemedIconSectionView) LayoutInflater.from(context).inflate(
                        R.layout.themed_icon_section_view, /* root= */ null);
        mThemedIconSectionView.setViewListener(this::onViewActivated);
        mThemedIconSectionView.getSwitch().setChecked(mSavedThemedIconEnabled);
        mThemedIconOptionsProvider.fetchThemedIconEnabled(
                enabled -> {
                    mInteractor.setActivated(enabled);
                    mThemedIconSectionView.getSwitch().setChecked(enabled);
                });
        mInteractor.isActivatedAsLiveData().observeForever(mIsActivatedChangeObserver);
        return mThemedIconSectionView;
    }

    @Override
    public void release() {
        mInteractor.isActivatedAsLiveData().removeObserver(mIsActivatedChangeObserver);
    }

    private void onViewActivated(Context context, boolean viewActivated) {
        if (context == null) {
            return;
        }
        mThemedIconOptionsProvider.setThemedIconEnabled(viewActivated);
        mInteractor.setActivated(viewActivated);
        mThemesUserEventLogger.logThemedIconApplied(viewActivated);
        mSnapshotRestorer.store(viewActivated);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (mThemedIconSectionView != null) {
            savedInstanceState.putBoolean(KEY_THEMED_ICON_ENABLED,
                    mThemedIconSectionView.getSwitch().isChecked());
        }
    }
}
