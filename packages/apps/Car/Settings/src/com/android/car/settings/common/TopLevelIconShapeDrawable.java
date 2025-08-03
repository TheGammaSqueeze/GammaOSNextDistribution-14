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

package com.android.car.settings.common;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;

import com.android.car.settings.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Top-level icon background shape.
 *
 * Adapted from {@link com.android.settingslib.widget.AdaptiveIconShapeDrawable}
 */
public class TopLevelIconShapeDrawable extends GradientDrawable {
    public TopLevelIconShapeDrawable() {
        super();
    }

    public TopLevelIconShapeDrawable(Resources resources) {
        super();
        init(resources);
    }

    @Override
    public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
            throws XmlPullParserException, IOException {
        super.inflate(r, parser, attrs, theme);
        init(r);
    }

    private void init(Resources resources) {
        setShape(resources.getInteger(R.integer.config_top_level_icon_shape));

        boolean showBackground = resources.getBoolean(R.bool.config_show_top_level_icon_background);
        if (!showBackground) {
            setColor(Color.TRANSPARENT);
        }
    }
}
