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
package com.android.customization.picker.grid.ui.section;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.grid.GridOption;
import com.android.customization.model.grid.GridOptionsManager;
import com.android.customization.picker.grid.ui.fragment.GridFragment;
import com.android.customization.picker.grid.ui.view.GridSectionView;
import com.android.themepicker.R;
import com.android.wallpaper.model.CustomizationSectionController;

import java.util.List;

/** A {@link CustomizationSectionController} for app grid. */
public class GridSectionController implements CustomizationSectionController<GridSectionView> {

    private static final String TAG = "GridSectionController";

    private final GridOptionsManager mGridOptionsManager;
    private final CustomizationSectionNavigationController mSectionNavigationController;
    private final Observer<Object> mOptionChangeObserver;
    private final LifecycleOwner mLifecycleOwner;
    private TextView mSectionDescription;
    private View mSectionTile;

    public GridSectionController(
            GridOptionsManager gridOptionsManager,
            CustomizationSectionNavigationController sectionNavigationController,
            LifecycleOwner lifecycleOwner,
            boolean isRevampedUiEnabled) {
        mGridOptionsManager = gridOptionsManager;
        mSectionNavigationController = sectionNavigationController;
        mLifecycleOwner = lifecycleOwner;
        mOptionChangeObserver = o -> updateUi(/* reload= */ true);
    }

    @Override
    public boolean isAvailable(Context context) {
        return mGridOptionsManager.isAvailable();
    }

    @Override
    public GridSectionView createView(Context context) {
        final GridSectionView gridSectionView = (GridSectionView) LayoutInflater.from(context)
                .inflate(R.layout.grid_section_view, /* root= */ null);
        mSectionDescription = gridSectionView.findViewById(R.id.grid_section_description);
        mSectionTile = gridSectionView.findViewById(R.id.grid_section_tile);

        // Fetch grid options to show currently set grid.
        updateUi(/* The result is getting when calling isAvailable(), so reload= */ false);
        mGridOptionsManager.getOptionChangeObservable(/* handler= */ null).observe(
                mLifecycleOwner,
                mOptionChangeObserver);

        gridSectionView.setOnClickListener(
                v -> {
                    final Fragment gridFragment = new GridFragment();
                    mSectionNavigationController.navigateTo(gridFragment);
                });

        return gridSectionView;
    }

    @Override
    public void release() {
        if (mGridOptionsManager.isAvailable()) {
            mGridOptionsManager.getOptionChangeObservable(/* handler= */ null).removeObserver(
                    mOptionChangeObserver
            );
        }
    }

    @Override
    public void onTransitionOut() {
        CustomizationSectionController.super.onTransitionOut();
    }

    private void updateUi(final boolean reload) {
        mGridOptionsManager.fetchOptions(
                new OptionsFetchedListener<GridOption>() {
                    @Override
                    public void onOptionsLoaded(List<GridOption> options) {
                        final String title = getActiveOption(options).getTitle();
                        mSectionDescription.setText(title);
                    }

                    @Override
                    public void onError(@Nullable Throwable throwable) {
                        if (throwable != null) {
                            Log.e(TAG, "Error loading grid options", throwable);
                        }
                        mSectionDescription.setText(R.string.something_went_wrong);
                        mSectionTile.setVisibility(View.GONE);
                    }
                },
                reload);
    }

    private GridOption getActiveOption(List<GridOption> options) {
        return options.stream()
                .filter(option -> option.isActive(mGridOptionsManager))
                .findAny()
                // For development only, as there should always be a grid set.
                .orElse(options.get(0));
    }
}
