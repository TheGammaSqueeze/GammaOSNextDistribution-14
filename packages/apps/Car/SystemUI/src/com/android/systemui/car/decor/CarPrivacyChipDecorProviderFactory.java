/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.car.decor;

import android.content.Context;
import android.view.DisplayCutout;

import com.android.systemui.R;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.decor.DecorProvider;
import com.android.systemui.decor.PrivacyDotCornerDecorProviderImpl;
import com.android.systemui.decor.PrivacyDotDecorProviderFactory;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

/**
 * Provides privacy dot views for each orientation. The PrivacyDot orientation and visibility
 * of the privacy dot views are controlled by the PrivacyDotViewController.
 */
@SysUISingleton
public class CarPrivacyChipDecorProviderFactory extends PrivacyDotDecorProviderFactory {

    private Context mContext;
    private boolean mHasProviders;

    @Inject
    public CarPrivacyChipDecorProviderFactory(Context context) {
        super(context.getResources());
        mContext = context;
        mHasProviders = mContext.getResources().getBoolean(
                R.bool.config_enableImmersivePrivacyChip);
    }

    @Override
    public boolean getHasProviders() {
        return mHasProviders;
    }

    @NotNull
    @Override
    public List<DecorProvider> getProviders() {
        if (!mHasProviders) {
            return Collections.emptyList();
        }
        // TODO(b/248145997): check with UX about the customized position.
        List<DecorProvider> providers = new ArrayList<>();
        providers.add(new PrivacyDotCornerDecorProviderImpl(
                R.id.privacy_dot_top_left_container,
                DisplayCutout.BOUNDS_POSITION_TOP,
                DisplayCutout.BOUNDS_POSITION_LEFT,
                R.layout.privacy_dot_top_left));
        providers.add(new PrivacyDotCornerDecorProviderImpl(
                R.id.privacy_dot_top_right_container,
                DisplayCutout.BOUNDS_POSITION_TOP,
                DisplayCutout.BOUNDS_POSITION_RIGHT,
                R.layout.privacy_dot_top_right));
        providers.add(new PrivacyDotCornerDecorProviderImpl(
                R.id.privacy_dot_bottom_left_container,
                DisplayCutout.BOUNDS_POSITION_BOTTOM,
                DisplayCutout.BOUNDS_POSITION_LEFT,
                R.layout.privacy_dot_bottom_left));
        providers.add(new PrivacyDotCornerDecorProviderImpl(
                R.id.privacy_dot_bottom_right_container,
                DisplayCutout.BOUNDS_POSITION_BOTTOM,
                DisplayCutout.BOUNDS_POSITION_RIGHT,
                R.layout.privacy_dot_bottom_right));
        return providers;
    }
}
