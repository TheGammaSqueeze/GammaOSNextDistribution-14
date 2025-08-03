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
package com.android.customization.model.iconpack;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager.Callback;
import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.CustomizationOption;
import com.android.customization.picker.iconpack.IconPackFragment;
import com.android.customization.picker.iconpack.IconPackSectionView;
import com.android.customization.widget.OptionSelectorController;
import com.android.customization.widget.OptionSelectorController.OptionSelectedListener;

import com.android.wallpaper.R;
import com.android.wallpaper.model.CustomizationSectionController;
import com.android.wallpaper.util.LaunchUtils;

import java.util.List;

/** A {@link CustomizationSectionController} for system icons. */

public class IconPackSectionController implements CustomizationSectionController<IconPackSectionView> {

    private static final String TAG = "IconPackSectionController";

    private final IconPackManager mIconPackOptionsManager;
    private final CustomizationSectionNavigationController mSectionNavigationController;
    private final Callback mApplyIconCallback = new Callback() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onError(@Nullable Throwable throwable) {
        }
    };

    public IconPackSectionController(IconPackManager iconPackOptionsManager,
            CustomizationSectionNavigationController sectionNavigationController) {
        mIconPackOptionsManager = iconPackOptionsManager;
        mSectionNavigationController = sectionNavigationController;
    }

    @Override
    public boolean isAvailable(Context context) {
        return mIconPackOptionsManager.isAvailable();
    }

    @Override
    public IconPackSectionView createView(Context context) {
        IconPackSectionView iconPackSectionView = (IconPackSectionView) LayoutInflater.from(context)
                .inflate(R.layout.icon_section_view, /* root= */ null);

        TextView sectionDescription = iconPackSectionView.findViewById(R.id.icon_section_description);
        View sectionTile = iconPackSectionView.findViewById(R.id.icon_section_tile);

        mIconPackOptionsManager.fetchOptions(new OptionsFetchedListener<IconPackOption>() {
            @Override
            public void onOptionsLoaded(List<IconPackOption> options) {
                IconPackOption activeOption = getActiveOption(options);
                sectionDescription.setText(activeOption.getTitle());
                activeOption.bindThumbnailTile(sectionTile);
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                if (throwable != null) {
                    Log.e(TAG, "Error loading icon options", throwable);
                }
                sectionDescription.setText(R.string.something_went_wrong);
                sectionTile.setVisibility(View.GONE);
            }
        }, /* reload= */ true);

        iconPackSectionView.setOnClickListener(v -> mSectionNavigationController.navigateTo(
                IconPackFragment.newInstance(context.getString(R.string.preview_name_icon))));

        return iconPackSectionView;
    }

    private IconPackOption getActiveOption(List<IconPackOption> options) {
        return options.stream()
                .filter(option -> option.isActive(mIconPackOptionsManager))
                .findAny()
                // For development only, as there should always be a grid set.
                .orElse(options.get(0));
    }
}
