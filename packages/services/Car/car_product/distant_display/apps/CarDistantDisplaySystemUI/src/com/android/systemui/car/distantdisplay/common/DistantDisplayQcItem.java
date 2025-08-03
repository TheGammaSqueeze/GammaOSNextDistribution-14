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

package com.android.systemui.car.distantdisplay.common;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.qc.QCItem;

/**
 * Distant Display Quick Control Item.
 **/
public class DistantDisplayQcItem {
    private final String mTitle;
    private final String mSubtitle;
    private final Drawable mIcon;
    private final QCItem.ActionHandler mActionHandler;

    public DistantDisplayQcItem(@NonNull String title, @Nullable String subtitle,
            @Nullable Drawable icon, @Nullable QCItem.ActionHandler actionHandler) {
        mTitle = title;
        mSubtitle = subtitle;
        mIcon = icon;
        mActionHandler = actionHandler;
    }

    @NonNull
    public String getTitle() {
        return mTitle;
    }

    @Nullable
    public String getSubtitle() {
        return mSubtitle;
    }

    @Nullable
    public Drawable getIcon() {
        return mIcon;
    }

    @Nullable
    public QCItem.ActionHandler getActionHandler() {
        return mActionHandler;
    }

    /**
     * A builder of {@link DistantDisplayQcItem}.
     */
    public static final class Builder {
        private String mTitle;
        private String mSubtitle;
        private Drawable mIcon;
        private QCItem.ActionHandler mActionHandler;

        /**
         * Sets the row title.
         */
        public Builder setTitle(@NonNull String title) {
            mTitle = title;
            return this;
        }

        /**
         * Sets the row subtitle.
         */
        public Builder setSubtitle(@Nullable String subtitle) {
            mSubtitle = subtitle;
            return this;
        }

        /**
         * Sets the row icon.
         */
        public Builder setIcon(@Nullable Drawable icon) {
            mIcon = icon;
            return this;
        }

        /**
         * Sets the PendingIntent to be sent when the action item is clicked.
         */
        public Builder setActionHandler(@Nullable QCItem.ActionHandler actionHandler) {
            mActionHandler = actionHandler;
            return this;
        }

        /**
         * Builds the final {@link DistantDisplayQcItem}.
         */
        public DistantDisplayQcItem build() {
            return new DistantDisplayQcItem(mTitle, mSubtitle, mIcon, mActionHandler);
        }
    }
}
