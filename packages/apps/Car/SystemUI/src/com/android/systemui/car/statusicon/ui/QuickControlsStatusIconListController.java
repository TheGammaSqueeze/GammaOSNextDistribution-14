/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.car.statusicon.ui;

import android.annotation.ArrayRes;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.StatusIconController;
import com.android.systemui.dagger.qualifiers.Main;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * A controller for quick controls status icon with multiple icons combined.
 */
public class QuickControlsStatusIconListController extends StatusIconController {

    private final Context mContext;
    private final Map<Class<?>, Provider<StatusIconController>> mIconControllerCreators;
    private final String[] mSubStatusIconControllerNames;
    private List<StatusIconController> mSubControllers = new ArrayList<>();

    private OnStatusUpdatedListener mOnStatusUpdatedListener = controller -> updateStatus();

    @Inject
    QuickControlsStatusIconListController(Context context, @Main Resources resources,
            Map<Class<?>, Provider<StatusIconController>> iconControllerCreators) {
        mContext = context;
        mIconControllerCreators = iconControllerCreators;
        mSubStatusIconControllerNames = resources.getStringArray(
                getSubStatusIconControllers());
        for (String clsName : mSubStatusIconControllerNames) {
            StatusIconController subStatusIconController = getStatusIconControllerByName(clsName);
            subStatusIconController.setOnStatusUpdatedListener(mOnStatusUpdatedListener);
            mSubControllers.add(subStatusIconController);
        }
        updateStatus();
    }

    private static <T> T resolve(String className, Map<Class<?>, Provider<T>> creators) {
        try {
            Class<?> clazz = Class.forName(className);
            Provider<T> provider = creators.get(clazz);
            return provider == null ? null : provider.get();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private StatusIconController getStatusIconControllerByName(String className) {
        try {
            StatusIconController statusIconController = resolveStatusIconController(className);
            if (statusIconController == null) {
                Constructor constructor = Class.forName(className).getConstructor(Context.class);
                statusIconController = (StatusIconController) constructor.newInstance(this);
            }

            return statusIconController;
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | IllegalAccessException
                | InstantiationException
                | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    private StatusIconController resolveStatusIconController(String className) {
        return resolve(className, mIconControllerCreators);
    }


    @Override
    protected void updateStatus() {
        // Combine the icons.
        Drawable[] layers = new Drawable[mSubControllers.size()];
        int iconWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.car_quick_controls_entry_points_icon_width);
        int iconSpace = mContext.getResources().getDimensionPixelSize(
                R.dimen.car_quick_controls_entry_points_icon_space);
        for (int i = 0; i < mSubControllers.size(); i++) {
            // Convert to BitmapDrawable in order to apply insets to drawable that reflects
            // the result of drawing on canvas. Otherwise, insets can only be applied to the
            // original static drawable, not the result drawn to the canvas.
            Drawable origDrawable = mSubControllers.get(i).getIconDrawableToDisplay();
            if (origDrawable == null) {
                continue;
            }
            Bitmap icon = Bitmap.createBitmap(/* width= */ iconWidth, /* height= */ iconWidth,
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(icon);
            origDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            origDrawable.draw(canvas);

            layers[i] = new BitmapDrawable(mContext.getResources(), icon);
        }
        LayerDrawable drawable = new LayerDrawable(layers);

        // Align the icons.
        int layerInset = 0;
        for (int i = 1; i < mSubControllers.size(); i++) {
            layerInset += iconWidth + iconSpace;
            drawable.setLayerInsetStart(i, layerInset);
        }
        drawable.setLayerInsetEnd(0, layerInset);

        // Set the combined icon drawable to display.
        setIconDrawableToDisplay(drawable);

        onStatusUpdated();
    }

    @Override
    protected int getPanelContentLayout() {
        return R.layout.car_quick_controls_panel;
    }

    @Override
    protected int getPanelWidth() {
        return R.dimen.car_quick_controls_panel_default_width;
    }

    @Override
    protected int getId() {
        return R.id.qc_status_icon_list;
    }

    @ArrayRes
    private int getSubStatusIconControllers() {
        return R.array.config_quickControlsSubIconControllers;
    }
}
