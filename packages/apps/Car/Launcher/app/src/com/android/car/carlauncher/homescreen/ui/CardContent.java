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
 * Defines the content displayed below the header on a home app's card.
 */
public abstract class CardContent {

    /**
     * The finite set of layouts supported by the home app each of which corresponds to an XML file.
     * DESCRIPTIVE_TEXT: card_content_descriptive_text_only.xml
     * DESCRIPTIVE_TEXT_WITH_CONTROLS: card_content_descriptive_text_with_controls.xml
     * TEXT_BLOCK: card_content_text_block.xml
     */
    public enum HomeCardContentType {
        DESCRIPTIVE_TEXT,
        DESCRIPTIVE_TEXT_WITH_CONTROLS,
        TEXT_BLOCK,
    }

    /**
     * Returns the type of content layout
     */
    public abstract HomeCardContentType getType();

    /**
     * Defines contents of Card background Image.
     */
    public static class CardBackgroundImage {

        private Drawable mBackground;
        private Drawable mForeground;

        public CardBackgroundImage(Drawable foreground, Drawable background) {
            mForeground = foreground;
            mBackground = background;
        }

        /**
         * Returns the drawable to use as background for the Image.
         */
        public Drawable getBackground() {
            return mBackground;
        }

        /**
         * Returns the drawable to use as content for the Image.
         */
        public Drawable getForeground() {
            return mForeground;
        }
    }
}
