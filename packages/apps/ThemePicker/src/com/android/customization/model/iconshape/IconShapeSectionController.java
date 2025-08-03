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
package com.android.customization.model.iconshape;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.customization.model.CustomizationManager.Callback;
import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.picker.iconshape.IconShapeFragment;
import com.android.customization.picker.iconshape.IconShapeSectionView;

import com.android.wallpaper.R;
import com.android.wallpaper.model.CustomizationSectionController;

import java.util.List;

/** A {@link CustomizationSectionController} for system icons. */

public class IconShapeSectionController implements CustomizationSectionController<IconShapeSectionView> {

    private static final String TAG = "IconShapeSectionController";

    private final IconShapeManager mIconShapeOptionsManager;
    private final CustomizationSectionNavigationController mSectionNavigationController;

    public IconShapeSectionController(IconShapeManager iconShapeOptionsManager,
            CustomizationSectionNavigationController sectionNavigationController) {
        mIconShapeOptionsManager = iconShapeOptionsManager;
        mSectionNavigationController = sectionNavigationController;
    }

    @Override
    public boolean isAvailable(Context context) {
        return mIconShapeOptionsManager.isAvailable();
    }

    @Override
    public IconShapeSectionView createView(Context context) {
        IconShapeSectionView iconShapeSectionView = (IconShapeSectionView) LayoutInflater.from(context)
                .inflate(R.layout.icon_shape_section_view, /* root= */ null);

        TextView sectionDescription = iconShapeSectionView.findViewById(R.id.icon_section_description);
        View sectionTile = iconShapeSectionView.findViewById(R.id.icon_section_tile);

        mIconShapeOptionsManager.fetchOptions(new OptionsFetchedListener<IconShapeOption>() {
            @Override
            public void onOptionsLoaded(List<IconShapeOption> options) {
                IconShapeOption activeOption = getActiveOption(options);
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

        iconShapeSectionView.setOnClickListener(v -> mSectionNavigationController.navigateTo(
                IconShapeFragment.newInstance(context.getString(R.string.preview_name_shape))));

        return iconShapeSectionView;
    }

    private IconShapeOption getActiveOption(List<IconShapeOption> options) {
        return options.stream()
                .filter(option -> option.isActive(mIconShapeOptionsManager))
                .findAny()
                // For development only, as there should always be a grid set.
                .orElse(options.get(0));
    }
}
