/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.layoutlib.bridge.bars;

import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.layoutlib.bridge.Bridge;
import com.android.layoutlib.bridge.android.BridgeContext;
import com.android.layoutlib.bridge.android.BridgeXmlBlockParser;
import com.android.layoutlib.bridge.impl.ParserFactory;
import com.android.layoutlib.bridge.impl.ResourceHelper;
import com.android.layoutlib.bridge.resources.IconLoader;
import com.android.resources.Density;

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static android.graphics.Color.WHITE;
import static android.os._Original_Build.VERSION_CODES.M;
import static com.android.layoutlib.bridge.bars.Config.getTimeColor;
import static com.android.layoutlib.bridge.bars.Config.isGreaterOrEqual;

public class StatusBar extends CustomBar {

    private final int mSimulatedPlatformVersion;
    /**
     * Color corresponding to light_mode_icon_color_single_tone
     * from frameworks/base/packages/SettingsLib/res/values/colors.xml
     */
    private static final int LIGHT_ICON_COLOR = 0xffffffff;
    /**
     * Color corresponding to dark_mode_icon_color_single_tone
     * from frameworks/base/packages/SettingsLib/res/values/colors.xml
     */
    private static final int DARK_ICON_COLOR = 0x99000000;
    /** Status bar background color attribute name. */
    private static final String ATTR_COLOR = "statusBarColor";
    /** Attribute for translucency property. */
    public static final String ATTR_TRANSLUCENT = "windowTranslucentStatus";

    /**
     * Constructor to be used when creating the {@link StatusBar} as a regular control. This
     * is currently used by the theme editor.
     */
    @SuppressWarnings("UnusedParameters")
    public StatusBar(Context context, AttributeSet attrs) {
        this((BridgeContext) context,
                Density.create(((BridgeContext) context).getMetrics().densityDpi),
                ((BridgeContext) context).getConfiguration().getLayoutDirection() ==
                        View.LAYOUT_DIRECTION_RTL,
                (context.getApplicationInfo().flags & ApplicationInfo.FLAG_SUPPORTS_RTL) != 0,
                context.getApplicationInfo().targetSdkVersion);
    }

    @SuppressWarnings("UnusedParameters")
    public StatusBar(BridgeContext context, Density density, boolean isRtl, boolean rtlEnabled,
            int simulatedPlatformVersion) {
        // FIXME: if direction is RTL but it's not enabled in application manifest, mirror this bar.
        super(context, LinearLayout.HORIZONTAL, "status_bar.xml", simulatedPlatformVersion);
        mSimulatedPlatformVersion = simulatedPlatformVersion;

        // FIXME: use FILL_H?
        setGravity(Gravity.START | Gravity.TOP | Gravity.RIGHT);

        int color = getBarColor(ATTR_COLOR, ATTR_TRANSLUCENT);
        setBackgroundColor(color == 0 ? Config.getStatusBarColor(simulatedPlatformVersion) : color);

        List<ImageView> icons = new ArrayList<>(2);
        TextView clockView = null;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);

            if (child instanceof ImageView) {
                icons.add((ImageView) child);
            } else if (child instanceof TextView) {
                clockView = (TextView) child;
            }
        }

        if (icons.size() != 2 || clockView == null) {
            Bridge.getLog().error(ILayoutLog.TAG_BROKEN, "Unable to initialize statusbar", null,
                    null, null);
            return;
        }

        int foregroundColor = getForegroundColor(simulatedPlatformVersion);
        // Cannot access the inside items through id because no R.id values have been
        // created for them.
        // We do know the order though.
        loadIcon(icons.get(0), "stat_sys_wifi_signal_4_fully."
                        + Config.getWifiIconType(simulatedPlatformVersion), density,foregroundColor);
        loadIcon(icons.get(1), "stat_sys_battery_100.png", density, foregroundColor);
        clockView.setText(Config.getTime(simulatedPlatformVersion));
        clockView.setTextColor(foregroundColor);
    }

    private int getForegroundColor(int platformVersion) {
        if (isGreaterOrEqual(platformVersion, M)) {
            RenderResources renderResources = getContext().getRenderResources();
            boolean translucentBackground =
                    ResourceHelper.getBooleanThemeFrameworkAttrValue(renderResources,
                            ATTR_TRANSLUCENT, false);
            if (translucentBackground) {
                return WHITE;
            }
            boolean drawnByWindow =
                    ResourceHelper.getBooleanThemeFrameworkAttrValue(renderResources,
                            "windowDrawsSystemBarBackgrounds", false);
            if (drawnByWindow) {
                boolean lightStatusBar =
                        ResourceHelper.getBooleanThemeFrameworkAttrValue(renderResources,
                                "windowLightStatusBar", false);
                return lightStatusBar ? DARK_ICON_COLOR : LIGHT_ICON_COLOR;
            }
            return WHITE;
        } else {
            return getTimeColor(platformVersion);
        }
    }

    @Override
    protected ImageView loadIcon(ImageView imageView, String iconName, Density density, int color) {
        if (!iconName.endsWith(".xml")) {
            return super.loadIcon(imageView, iconName, density, color);
        }

        // The xml is stored only in xhdpi.
        IconLoader iconLoader = new IconLoader(iconName, Density.XHIGH,
                mSimulatedPlatformVersion, null);
        InputStream stream = iconLoader.getIcon();

        if (stream != null) {
            try {
                BridgeXmlBlockParser parser =
                        new BridgeXmlBlockParser(
                                ParserFactory.create(stream, iconName),
                                (BridgeContext) mContext,
                                ResourceNamespace.ANDROID);
                Drawable drawable = Drawable.createFromXml(mContext.getResources(), parser);
                drawable.setTint(color);
                imageView.setImageDrawable(drawable);
            } catch (XmlPullParserException e) {
                Bridge.getLog().error(ILayoutLog.TAG_BROKEN, "Unable to draw wifi icon", e,
                        null, null);
            } catch (IOException e) {
                Bridge.getLog().error(ILayoutLog.TAG_BROKEN, "Unable to draw wifi icon", e,
                        null, null);
            }
        }

        return imageView;
    }

    @Override
    protected TextView getStyleableTextView() {
        return null;
    }
}
