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

package com.android.car.settings.qc;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.LayerDrawable;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;

import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.car.settings.common.DrawableUtil;
import com.android.settingslib.Utils;
import com.android.settingslib.graph.SignalDrawable;
import com.android.settingslib.net.SignalStrengthUtil;
import com.android.settingslib.utils.ThreadUtils;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper methods for Mobile network quick controls.
 */
public class MobileNetworkQCUtils {
    private static final String TAG = "MobileNetworkQCUtils";
    // Timeout for acquiring a signal drawable (in ms)
    private static final long SIGNAL_DRAWABLE_TIMEOUT = 1000L;
    private static final Drawable EMPTY_DRAWABLE = new ColorDrawable(Color.TRANSPARENT);

    private MobileNetworkQCUtils() {
    }

    /**
     * Retrieve the network subtitle for the current quick control network state.
     */
    @Nullable
    public static String getMobileNetworkSummary(Context context, boolean dataEnabled) {
        if (!dataEnabled) {
            return context.getString(R.string.mobile_network_state_off);
        }
        TelephonyManager manager = context.getSystemService(TelephonyManager.class);
        if (manager == null) {
            return null;
        }
        String subtitle = manager.getNetworkOperatorName();
        if (TextUtils.isEmpty(subtitle)) {
            return null;
        }
        return subtitle;
    }

    /**
     * Retrieve an icon representing the current network symbol.
     */
    public static Icon getMobileNetworkSignalIcon(Context context) {
        // Set default drawable in case the SignalDrawable cannot be loaded
        Drawable drawable = context.getDrawable(R.drawable.ic_qc_mobile_data);
        try {
            Semaphore lock = new Semaphore(/* permits= */0);
            AtomicReference<Drawable> shared = new AtomicReference<>();
            ThreadUtils.postOnMainThread(() -> {
                shared.set(getSignalStrengthDrawable(context));
                lock.release();
            });
            boolean success = lock.tryAcquire(SIGNAL_DRAWABLE_TIMEOUT, TimeUnit.MILLISECONDS);
            if (success) {
                drawable = shared.get();
            } else {
                Log.d(TAG, "Timed out getting signal drawable");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while obtaining signal drawable", e);
        }

        return DrawableUtil.createIconFromDrawable(drawable);
    }

    private static Drawable getSignalStrengthDrawable(Context context) {
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        SignalStrength strength = telephonyManager.getSignalStrength();
        int level = (strength == null) ? 0 : strength.getLevel();
        int numLevels = SignalStrength.NUM_SIGNAL_STRENGTH_BINS;
        if (SignalStrengthUtil.shouldInflateSignalStrength(context,
                SubscriptionManager.getDefaultDataSubscriptionId())) {
            level += 1;
            numLevels += 1;
        }
        return createSignalDrawable(context, level, numLevels);
    }

    private static Drawable createSignalDrawable(Context context, int level, int numLevels) {
        SignalDrawable signalDrawable = new SignalDrawable(context);
        signalDrawable.setLevel(
                SignalDrawable.getState(level, numLevels, /* cutOut= */ false));

        Drawable[] layers = {EMPTY_DRAWABLE, signalDrawable};
        int iconSize = context.getResources().getDimensionPixelSize(R.dimen.icon_size);

        LayerDrawable icons = new LayerDrawable(layers);
        // Set the network type icon at the top left
        icons.setLayerGravity(/* index= */ 0, Gravity.TOP | Gravity.LEFT);
        // Set the signal strength icon at the bottom right
        icons.setLayerGravity(/* index= */ 1, Gravity.BOTTOM | Gravity.RIGHT);
        icons.setLayerSize(/* index= */ 1, iconSize, iconSize);
        icons.setTintList(Utils.getColorAttr(context, android.R.attr.colorControlNormal));
        return icons;
    }
}
