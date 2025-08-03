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
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Quick Control Slider included in {@link QCRow}
 */
public class QCSlider extends QCItem {
    private int mMin = 0;
    private int mMax = 100;
    private int mValue = 0;
    private PendingIntent mInputAction;
    private PendingIntent mDisabledClickAction;

    public QCSlider(int min, int max, int value, boolean enabled, boolean clickableWhileDisabled,
            @Nullable PendingIntent inputAction, @Nullable PendingIntent disabledClickAction) {
        super(QC_TYPE_SLIDER, enabled, clickableWhileDisabled);
        mMin = min;
        mMax = max;
        mValue = value;
        mInputAction = inputAction;
        mDisabledClickAction = disabledClickAction;
    }

    public QCSlider(@NonNull Parcel in) {
        super(in);
        mMin = in.readInt();
        mMax = in.readInt();
        mValue = in.readInt();
        boolean hasAction = in.readBoolean();
        if (hasAction) {
            mInputAction = PendingIntent.CREATOR.createFromParcel(in);
        }
        boolean hasDisabledClickAction = in.readBoolean();
        if (hasDisabledClickAction) {
            mDisabledClickAction = PendingIntent.CREATOR.createFromParcel(in);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(mMin);
        dest.writeInt(mMax);
        dest.writeInt(mValue);
        boolean hasAction = mInputAction != null;
        dest.writeBoolean(hasAction);
        if (hasAction) {
            mInputAction.writeToParcel(dest, flags);
        }
        boolean hasDisabledClickAction = mDisabledClickAction != null;
        dest.writeBoolean(hasDisabledClickAction);
        if (hasDisabledClickAction) {
            mDisabledClickAction.writeToParcel(dest, flags);
        }
    }

    @Override
    public PendingIntent getPrimaryAction() {
        return mInputAction;
    }

    @Override
    public PendingIntent getDisabledClickAction() {
        return mDisabledClickAction;
    }

    public int getMin() {
        return mMin;
    }

    public int getMax() {
        return mMax;
    }

    public int getValue() {
        return mValue;
    }

    public static Creator<QCSlider> CREATOR = new Creator<QCSlider>() {
        @Override
        public QCSlider createFromParcel(Parcel source) {
            return new QCSlider(source);
        }

        @Override
        public QCSlider[] newArray(int size) {
            return new QCSlider[size];
        }
    };

    /**
     * Builder for {@link QCSlider}.
     */
    public static class Builder {
        private int mMin = 0;
        private int mMax = 100;
        private int mValue = 0;
        private boolean mIsEnabled = true;
        private boolean mIsClickableWhileDisabled = false;
        private PendingIntent mInputAction;
        private PendingIntent mDisabledClickAction;

        /**
         * Set the minimum allowed value for the slider input.
         */
        public Builder setMin(int min) {
            mMin = min;
            return this;
        }

        /**
         * Set the maximum allowed value for the slider input.
         */
        public Builder setMax(int max) {
            mMax = max;
            return this;
        }

        /**
         * Set the current value for the slider input.
         */
        public Builder setValue(int value) {
            mValue = value;
            return this;
        }

        /**
         * Sets whether or not the slider is enabled.
         */
        public Builder setEnabled(boolean enabled) {
            mIsEnabled = enabled;
            return this;
        }

        /**
         * Sets whether or not a slider should be clickable while disabled.
         */
        public Builder setClickableWhileDisabled(boolean clickable) {
            mIsClickableWhileDisabled = clickable;
            return this;
        }


        /**
         * Set the PendingIntent to be sent when the slider value is changed.
         */
        public Builder setInputAction(@Nullable PendingIntent inputAction) {
            mInputAction = inputAction;
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
         * Builds the final {@link QCSlider}.
         */
        public QCSlider build() {
            return new QCSlider(mMin, mMax, mValue, mIsEnabled, mIsClickableWhileDisabled,
                    mInputAction, mDisabledClickAction);
        }
    }
}
