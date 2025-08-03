/*
 * Copyright (C) 2020 Google Inc.
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

package com.android.car.carlauncher.homescreen.ui;

import android.graphics.drawable.Drawable;

/**
 * The header of a home app card displays the name and icon of the app that's providing
 * the data displayed.
 */
public final class CardHeader {
    private final CharSequence mCardTitle;
    private final Drawable mCardIcon;

    public CardHeader(CharSequence appName, Drawable cardIcon) {
        mCardTitle = appName;
        mCardIcon = cardIcon;
    }

    /**
     * Returns the name of the source app
     */
    public CharSequence getCardTitle() {
        return mCardTitle;
    }

    /**
     * Returns the icon for the source app
     */
    public Drawable getCardIcon() {
        return mCardIcon;
    }
}
