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

package com.android.car.qc;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Quick Control Tile Element
 * ------------
 * | -------- |
 * | | Icon | |
 * | -------- |
 * | Subtitle |
 * ------------
 */
public class QCTile extends QCItem {
    private final boolean mIsChecked;
    private final boolean mIsAvailable;
    private final String mSubtitle;
    private Icon mIcon;
    private PendingIntent mAction;
    private PendingIntent mDisabledClickAction;

    public QCTile(boolean isChecked, boolean isEnabled, boolean isAvailable,
            boolean isClickableWhileDisabled, @Nullable String subtitle, @Nullable Icon icon,
            @Nullable PendingIntent action, @Nullable PendingIntent disabledClickAction) {
        super(QC_TYPE_TILE, isEnabled, isClickableWhileDisabled);
        mIsChecked = isChecked;
        mIsAvailable = isAvailable;
        mSubtitle = subtitle;
        mIcon = icon;
        mAction = action;
        mDisabledClickAction = disabledClickAction;
    }

    public QCTile(@NonNull Parcel in) {
        super(in);
        mIsChecked = in.readBoolean();
        mIsAvailable = in.readBoolean();
        mSubtitle = in.readString();
        boolean hasIcon = in.readBoolean();
        if (hasIcon) {
            mIcon = Icon.CREATOR.createFromParcel(in);
        }
        boolean hasAction = in.readBoolean();
        if (hasAction) {
            mAction = PendingIntent.CREATOR.createFromParcel(in);
        }
        boolean hasDisabledClickAction = in.readBoolean();
        if (hasDisabledClickAction) {
            mDisabledClickAction = PendingIntent.CREATOR.createFromParcel(in);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeBoolean(mIsChecked);
        dest.writeBoolean(mIsAvailable);
        dest.writeString(mSubtitle);
        boolean hasIcon = mIcon != null;
        dest.writeBoolean(hasIcon);
        if (hasIcon) {
            mIcon.writeToParcel(dest, flags);
        }
        boolean hasAction = mAction != null;
        dest.writeBoolean(hasAction);
        if (hasAction) {
            mAction.writeToParcel(dest, flags);
        }
        boolean hasDisabledClickAction = mDisabledClickAction != null;
        dest.writeBoolean(hasDisabledClickAction);
        if (hasDisabledClickAction) {
            mDisabledClickAction.writeToParcel(dest, flags);
        }
    }

    @Override
    public PendingIntent getPrimaryAction() {
        return mAction;
    }

    @Override
    public PendingIntent getDisabledClickAction() {
        return mDisabledClickAction;
    }

    public boolean isChecked() {
        return mIsChecked;
    }

    public boolean isAvailable() {
        return mIsAvailable;
    }

    @Nullable
    public String getSubtitle() {
        return mSubtitle;
    }

    @Nullable
    public Icon getIcon() {
        return mIcon;
    }

    public static Creator<QCTile> CREATOR = new Creator<QCTile>() {
        @Override
        public QCTile createFromParcel(Parcel source) {
            return new QCTile(source);
        }

        @Override
        public QCTile[] newArray(int size) {
            return new QCTile[size];
        }
    };

    /**
     * Builder for {@link QCTile}.
     */
    public static class Builder {
        private boolean mIsChecked;
        private boolean mIsEnabled = true;
        private boolean mIsAvailable = true;
        private boolean mIsClickableWhileDisabled = false;
        private String mSubtitle;
        private Icon mIcon;
        private PendingIntent mAction;
        private PendingIntent mDisabledClickAction;

        /**
         * Sets whether or not the tile should be checked.
         */
        public Builder setChecked(boolean checked) {
            mIsChecked = checked;
            return this;
        }

        /**
         * Sets whether or not the tile should be enabled.
         */
        public Builder setEnabled(boolean enabled) {
            mIsEnabled = enabled;
            return this;
        }

        /**
         * Sets whether or not the action item is available.
         */
        public Builder setAvailable(boolean available) {
            mIsAvailable = available;
            return this;
        }

        /**
         * Sets whether or not a tile should be clickable while disabled.
         */
        public Builder setClickableWhileDisabled(boolean clickable) {
            mIsClickableWhileDisabled = clickable;
            return this;
        }

        /**
         * Sets the tile's subtitle.
         */
        public Builder setSubtitle(@Nullable String subtitle) {
            mSubtitle = subtitle;
            return this;
        }

        /**
         * Sets the tile's icon.
         */
        public Builder setIcon(@Nullable Icon icon) {
            mIcon = icon;
            return this;
        }

        /**
         * Sets the PendingIntent to be sent when the tile is clicked.
         */
        public Builder setAction(@Nullable PendingIntent action) {
            mAction = action;
            return this;
        }

        /**
         * Sets the PendingIntent to be sent when the action item is clicked while disabled.
         */
        public Builder setDisabledClickAction(@Nullable PendingIntent action) {
            mDisabledClickAction = action;
            return this;
        }

        /**
         * Builds the final {@link QCTile}.
         */
        public QCTile build() {
            return new QCTile(mIsChecked, mIsEnabled, mIsAvailable, mIsClickableWhileDisabled,
                    mSubtitle, mIcon, mAction, mDisabledClickAction);
        }
    }
}
