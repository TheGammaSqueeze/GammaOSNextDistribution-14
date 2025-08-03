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

package com.android.car.settings.sound;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View.BaseSavedState;

import com.android.car.settings.common.SeekBarPreference;

final class VolumeSeekBarPreference extends SeekBarPreference {
    private Drawable mMutedIcon;
    private Drawable mUnMutedIcon;
    private boolean mIsMuted = false;

    VolumeSeekBarPreference(Context context) {
        super(context);
    }

    Drawable getUnMutedIcon() {
        return mUnMutedIcon;
    }

    void setUnMutedIcon(Drawable unMutedIcon) {
        mUnMutedIcon = unMutedIcon;
        if (mIsMuted) {
            return;
        }
        setIcon(unMutedIcon);
    }

    Drawable getMutedIcon() {
        return mMutedIcon;
    }

    void setMutedIcon(Drawable mutedIcon) {
        mMutedIcon = mutedIcon;
        if (!mIsMuted) {
            return;
        }
        setIcon(mutedIcon);
    }

    void setIsMuted(boolean isMuted) {
        mIsMuted = isMuted;
        if (isMuted) {
            setIcon(mMutedIcon);
        } else {
            setIcon(mUnMutedIcon);
        }
    }

    public boolean isMuted() {
        return mIsMuted;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        // Save the instance state
        final SavedVolumeState myState = new SavedVolumeState(superState);
        myState.setIsMuted(mIsMuted);
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!state.getClass().equals(SavedVolumeState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        // Restore the instance state
        SavedVolumeState myState = (SavedVolumeState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setIsMuted(myState.isMuted());
        notifyChanged();
    }

    /**
     * SavedVolumeState, a subclass of {@link BaseSavedState}, will store the state
     * of volume seek bar preferences, a subclass of SeekBarPreference.
     * <p>
     * It is important to always call through to super methods.
     */
    public static class SavedVolumeState extends BaseSavedState {

        private boolean mIsMuted;

        public boolean isMuted() {
            return mIsMuted;
        }

        public void setIsMuted(boolean muted) {
            mIsMuted = muted;
        }

        SavedVolumeState(Parcel source) {
            super(source);

            mIsMuted = source.readBoolean();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);

            dest.writeBoolean(mIsMuted);
        }

        private SavedVolumeState(Parcelable superState) {
            super(superState);
        }

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<SavedVolumeState> CREATOR =
                new Parcelable.Creator<SavedVolumeState>() {
                    @Override
                    public SavedVolumeState createFromParcel(Parcel in) {
                        return new SavedVolumeState(in);
                    }

                    @Override
                    public SavedVolumeState[] newArray(int size) {
                        return new SavedVolumeState[size];
                    }
                };
    }
}
