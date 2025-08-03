/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.telecom;

public class RingerAttributes {
    public static class Builder {
        private boolean mEndEarly;
        private boolean mLetDialerHandleRinging;
        private boolean mAcquireAudioFocus;
        private boolean mRingerAudible;
        private String mInaudibleReason;
        private boolean mShouldRingForContact;
        private boolean mSilentRingingRequested;
        private boolean mWorkProfileQuietMode;

        public RingerAttributes.Builder setEndEarly(boolean endEarly) {
            mEndEarly = endEarly;
            return this;
        }

        public RingerAttributes.Builder setLetDialerHandleRinging(boolean letDialerHandleRinging) {
            mLetDialerHandleRinging = letDialerHandleRinging;
            return this;
        }

        public RingerAttributes.Builder setAcquireAudioFocus(boolean acquireAudioFocus) {
            mAcquireAudioFocus = acquireAudioFocus;
            return this;
        }

        public RingerAttributes.Builder setRingerAudible(boolean ringerAudible) {
            mRingerAudible = ringerAudible;
            return this;
        }

        public RingerAttributes.Builder setInaudibleReason(String inaudibleReason) {
            mInaudibleReason = inaudibleReason;
            return this;
        }

        public RingerAttributes.Builder setShouldRingForContact(boolean shouldRingForContact) {
            mShouldRingForContact = shouldRingForContact;
            return this;
        }

        public RingerAttributes.Builder setSilentRingingRequested(boolean silentRingingRequested) {
            mSilentRingingRequested = silentRingingRequested;
            return this;
        }

        public RingerAttributes.Builder setWorkProfileQuietMode(boolean workProfileQuietMode) {
            mWorkProfileQuietMode = workProfileQuietMode;
            return this;
        }

        public RingerAttributes build() {
            return new RingerAttributes(mEndEarly, mLetDialerHandleRinging, mAcquireAudioFocus,
                    mRingerAudible, mInaudibleReason, mShouldRingForContact,
                    mSilentRingingRequested, mWorkProfileQuietMode);
        }
    }

    private boolean mEndEarly;
    private boolean mLetDialerHandleRinging;
    private boolean mAcquireAudioFocus;
    private boolean mRingerAudible;
    private String mInaudibleReason;
    private boolean mShouldRingForContact;
    private boolean mSilentRingingRequested;
    private boolean mWorkProfileQuietMode;

    private RingerAttributes(boolean endEarly, boolean letDialerHandleRinging,
            boolean acquireAudioFocus, boolean ringerAudible, String inaudibleReason,
            boolean shouldRingForContact, boolean silentRingingRequested,
            boolean workProfileQuietMode) {
        mEndEarly = endEarly;
        mLetDialerHandleRinging = letDialerHandleRinging;
        mAcquireAudioFocus = acquireAudioFocus;
        mRingerAudible = ringerAudible;
        mInaudibleReason = inaudibleReason;
        mShouldRingForContact = shouldRingForContact;
        mSilentRingingRequested = silentRingingRequested;
        mWorkProfileQuietMode = workProfileQuietMode;
    }

    public boolean isEndEarly() {
        return mEndEarly;
    }

    public boolean letDialerHandleRinging() {
        return mLetDialerHandleRinging;
    }

    public boolean shouldAcquireAudioFocus() {
        return mAcquireAudioFocus;
    }

    public boolean isRingerAudible() {
        return mRingerAudible;
    }

    public String getInaudibleReason() {
        return mInaudibleReason;
    }

    public boolean shouldRingForContact() {
        return mShouldRingForContact;
    }

    public boolean isSilentRingingRequested() {
        return mSilentRingingRequested;
    }

    public boolean isWorkProfileInQuietMode() {
        return mWorkProfileQuietMode;
    }
}
