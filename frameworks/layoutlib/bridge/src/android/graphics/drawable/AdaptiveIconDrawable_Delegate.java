/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.graphics.drawable;

import com.android.internal.R;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.tools.layoutlib.annotations.LayoutlibDelegate;

import android.content.res.Resources;
import android.content.res.Resources_Delegate;
import android.graphics.Canvas;

public class AdaptiveIconDrawable_Delegate {
    public static String sPath;

    /**
     * Delegate that replaces a call to Resources.getString in
     * the constructor of AdaptiveIconDrawable.
     * This allows to pass a non-default value for the mask for adaptive icons.
     */
    @SuppressWarnings("unused")
    public static String getResourceString(Resources res, int resId) {
        if (resId == R.string.config_icon_mask) {
            return sPath;
        }
        return res.getString(resId);
    }

    @LayoutlibDelegate
    public static void draw(AdaptiveIconDrawable thisDrawable, Canvas canvas) {
        Resources res = Resources.getSystem();
        BridgeContext context = Resources_Delegate.getContext(res);
        if (context.useThemedIcon() && thisDrawable.getMonochrome() != null) {
            AdaptiveIconDrawable themedIcon =
                    createThemedVersionFromMonochrome(thisDrawable.getMonochrome(), res);
            themedIcon.onBoundsChange(thisDrawable.getBounds());
            themedIcon.draw_Original(canvas);
        } else {
            thisDrawable.draw_Original(canvas);
        }
    }

    /**
     * This builds the themed version of {@link AdaptiveIconDrawable}, copying what the
     * framework does in {@link com.android.launcher3.Utilities#getFullDrawable}
     */
    private static AdaptiveIconDrawable createThemedVersionFromMonochrome(Drawable mono,
            Resources resources) {
        mono = mono.mutate();
        int[] colors = getColors(resources);
        mono.setTint(colors[1]);
        return new AdaptiveIconDrawable(new ColorDrawable(colors[0]), mono);
    }

    private static int[] getColors(Resources resources) {
        int[] colors = new int[2];
        if (resources.getConfiguration().isNightModeActive()) {
            colors[0] = resources.getColor(android.R.color.system_neutral1_800, null);
            colors[1] = resources.getColor(android.R.color.system_accent1_100, null);
        } else {
            colors[0] = resources.getColor(android.R.color.system_accent1_100, null);
            colors[1] = resources.getColor(android.R.color.system_neutral2_700, null);
        }
        return colors;
    }
}
