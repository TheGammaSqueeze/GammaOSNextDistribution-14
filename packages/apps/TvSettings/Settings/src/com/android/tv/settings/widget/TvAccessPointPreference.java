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

package com.android.tv.settings.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.preference.PreferenceViewHolder;

import com.android.tv.settings.library.network.AccessPoint;
import com.android.tv.settings.R;
import com.android.tv.settings.overlay.FlavorUtils;

/**
 * Preference for overriding wifi icons
 */
public class TvAccessPointPreference extends AccessPointPreference {

    private AccessPoint mAccessPoint;

    public TvAccessPointPreference(AccessPoint accessPoint, Context context,
            AccessPointPreference.UserBadgeCache cache, boolean forSavedNetworks) {
        super(accessPoint, context, cache, forSavedNetworks);
        mAccessPoint = accessPoint;
    }

    @Override
    protected void updateIcon(int level, Context context) {
        if (FlavorUtils.isTwoPanel(getContext())) {
            switch (level) {
                case 4:
                    setIcon(R.drawable.ic_wifi_signal_4_white);
                    return;
                case 3:
                    setIcon(R.drawable.ic_wifi_signal_3_white);
                    return;
                case 2:
                    setIcon(R.drawable.ic_wifi_signal_2_white);
                    return;
                case 1:
                    setIcon(R.drawable.ic_wifi_signal_1_white);
                    return;
                case 0:
                    // fall through
                default:
                    setIcon(R.drawable.ic_wifi_signal_0_white);
                    return;
            }
        } else {
            super.updateIcon(level, context);
        }
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder view) {
        super.onBindViewHolder(view);
        if (FlavorUtils.isTwoPanel(getContext())) {
            ImageView frictionImageView = (ImageView) view.findViewById(R.id.friction_icon);
            if (frictionImageView == null || mAccessPoint == null) {
                return;
            }
            // Update the lock icon when WiFi network is secured
            if ((mAccessPoint.getSecurity() != AccessPoint.SECURITY_NONE)
                    && (mAccessPoint.getSecurity() != AccessPoint.SECURITY_OWE)) {
                Drawable drawable = getContext()
                        .getDrawable(R.drawable.ic_wifi_signal_lock_outline);
                frictionImageView.setImageDrawable(drawable);
            }
        }
    }
}
