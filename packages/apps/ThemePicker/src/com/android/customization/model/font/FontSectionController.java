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
package com.android.customization.model.font;

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
import com.android.customization.widget.OptionSelectorController;
import com.android.customization.widget.OptionSelectorController.OptionSelectedListener;

import com.android.wallpaper.R;
import com.android.wallpaper.model.CustomizationSectionController;
import com.android.wallpaper.util.LaunchUtils;

import com.android.customization.picker.font.FontFragment;
import com.android.customization.picker.font.FontSectionView;

import java.util.List;

/** A {@link CustomizationSectionController} for system fonts. */

public class FontSectionController implements CustomizationSectionController<FontSectionView> {

    private static final String TAG = "FontSectionController";

    private final FontManager mFontOptionsManager;
    private final CustomizationSectionNavigationController mSectionNavigationController;
    private final Callback mApplyFontCallback = new Callback() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onError(@Nullable Throwable throwable) {
        }
    };

    public FontSectionController(FontManager fontOptionsManager,
            CustomizationSectionNavigationController sectionNavigationController) {
        mFontOptionsManager = fontOptionsManager;
        mSectionNavigationController = sectionNavigationController;
    }

    @Override
    public boolean isAvailable(Context context) {
        return mFontOptionsManager.isAvailable();
    }

    @Override
    public FontSectionView createView(Context context) {
        FontSectionView fontSectionView = (FontSectionView) LayoutInflater.from(context)
                .inflate(R.layout.font_section_view, /* root= */ null);

        TextView sectionDescription = fontSectionView.findViewById(R.id.font_section_description);
        View sectionTile = fontSectionView.findViewById(R.id.font_section_tile);

        mFontOptionsManager.fetchOptions(new OptionsFetchedListener<FontOption>() {
            @Override
            public void onOptionsLoaded(List<FontOption> options) {
                FontOption activeOption = getActiveOption(options);
                sectionDescription.setText(activeOption.getTitle());
                activeOption.bindThumbnailTile(sectionTile);
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                if (throwable != null) {
                    Log.e(TAG, "Error loading font options", throwable);
                }
                sectionDescription.setText(R.string.something_went_wrong);
                sectionTile.setVisibility(View.GONE);
            }
        }, /* reload= */ true);

        fontSectionView.setOnClickListener(v -> mSectionNavigationController.navigateTo(
                FontFragment.newInstance(context.getString(R.string.preview_name_font))));

        return fontSectionView;
    }

    private FontOption getActiveOption(List<FontOption> options) {
        return options.stream()
                .filter(option -> mFontOptionsManager.isActive(option))
                .findAny()
                // For development only, as there should always be a grid set.
                .orElse(options.get(0));
    }
}
