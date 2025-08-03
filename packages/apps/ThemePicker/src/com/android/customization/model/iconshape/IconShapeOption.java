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
package com.android.customization.model.iconshape;

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;

import androidx.annotation.Dimension;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.core.graphics.ColorUtils;

import com.android.customization.model.theme.ShapeAppIcon;
import com.android.wallpaper.R;
import com.android.wallpaper.util.ResourceUtils;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.CustomizationOption;
import com.android.customization.model.theme.OverlayManagerCompat;

import java.util.List;
import java.util.Objects;

public class IconShapeOption implements CustomizationOption<IconShapeOption> {

    private final LayerDrawable mShape;
    private final List<ShapeAppIcon> mAppIcons;
    private final String mTitle;
    private final String mOverlayPackage;
    private final Path mPath;
    private final int mCornerRadius;
    private int[] mShapeIconIds = {
            R.id.shape_preview_icon_0, R.id.shape_preview_icon_1, R.id.shape_preview_icon_2,
            R.id.shape_preview_icon_3, R.id.shape_preview_icon_4, R.id.shape_preview_icon_5
    };

    public IconShapeOption(String packageName, String title, Path path,
                           @Dimension int cornerRadius, Drawable shapeDrawable,
                           List<ShapeAppIcon> appIcons) {
        mOverlayPackage = packageName;
        mTitle = title;
        mAppIcons = appIcons;
        mPath = path;
        mCornerRadius = cornerRadius;
        Drawable background = shapeDrawable.getConstantState().newDrawable();
        Drawable foreground = shapeDrawable.getConstantState().newDrawable();
        mShape = new LayerDrawable(new Drawable[]{background, foreground});
        mShape.setLayerGravity(0, Gravity.CENTER);
        mShape.setLayerGravity(1, Gravity.CENTER);
    }

    @Override
    public void bindThumbnailTile(View view) {
        Resources res = view.getContext().getResources();
        int resId = R.id.icon_section_tile;
        if (view.findViewById(R.id.shape_thumbnail) != null) {
            resId = R.id.shape_thumbnail;
        }

        Resources.Theme theme = view.getContext().getTheme();
        int borderWidth = 2 * res.getDimensionPixelSize(R.dimen.option_border_width);

        Drawable background = mShape.getDrawable(0);
        background.setTintList(res.getColorStateList(R.color.option_border_color, theme));

        ShapeDrawable foreground = (ShapeDrawable) mShape.getDrawable(1);

        foreground.setIntrinsicHeight(background.getIntrinsicHeight() - borderWidth);
        foreground.setIntrinsicWidth(background.getIntrinsicWidth() - borderWidth);
        TypedArray ta = view.getContext().obtainStyledAttributes(
                new int[]{android.R.attr.colorPrimary});
        int primaryColor = ta.getColor(0, 0);
        ta.recycle();
        int foregroundColor =
                ResourceUtils.getColorAttr(view.getContext(), android.R.attr.textColorPrimary);

        foreground.setTint(ColorUtils.blendARGB(primaryColor, foregroundColor, .05f));

        ((ImageView) view.findViewById(resId)).setImageDrawable(mShape);
        view.setContentDescription(mTitle);
    }

    @Override
    public boolean isActive(CustomizationManager<IconShapeOption> manager) {
        IconShapeManager iconManager = (IconShapeManager) manager;
        OverlayManagerCompat overlayManager = iconManager.getOverlayManager();

        return Objects.equals(mOverlayPackage,
                overlayManager.getEnabledPackageName(ANDROID_PACKAGE, OVERLAY_CATEGORY_SHAPE));
    }

    @Override
    public int getLayoutResId() {
        return R.layout.theme_shape_option;
    }

    @Override
    public String getTitle() {
        return mTitle;
    }

    public String getPackageName() {
        return mOverlayPackage;
    }

    public void bindPreview(ViewGroup container) {
        ViewGroup cardBody = container.findViewById(R.id.theme_preview_card_body_container);
        if (cardBody.getChildCount() == 0) {
            LayoutInflater.from(container.getContext()).inflate(
                    R.layout.preview_card_shape_content, cardBody, true);
        }
        for (int i = 0; i < mShapeIconIds.length && i < mAppIcons.size(); i++) {
            ImageView iconView = cardBody.findViewById(mShapeIconIds[i]);
            iconView.setBackground(mAppIcons.get(i).getDrawableCopy());
        }
    }
}
