/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.audio;

import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;

public final class AudioFocusInfoBuilder {
    private int mUsage;
    private int mClientUid;
    private String mClientId;
    private int mGainRequest;
    private String mPackageName = "com.android.car.audio";
    private Bundle mBundle = null;
    private int mLossReceived = AudioManager.AUDIOFOCUS_NONE;
    private int mSdk = Build.VERSION.SDK_INT;
    private boolean mDelayedFocusRequestEnabled;
    private boolean mPausesOnDuckRequestEnabled;

    public AudioFocusInfoBuilder setUsage(int usage) {
        mUsage = usage;
        return this;
    }

    public AudioFocusInfoBuilder setClientUid(int clientUid) {
        mClientUid = clientUid;
        return this;
    }

    public AudioFocusInfoBuilder setClientId(String clientId) {
        mClientId = clientId;
        return this;
    }

    public AudioFocusInfoBuilder setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    public AudioFocusInfoBuilder setGainRequest(int gainRequest) {
        mGainRequest = gainRequest;
        return this;
    }

    public AudioFocusInfoBuilder setLossReceived(int lossReceived) {
        mLossReceived = lossReceived;
        return this;
    }

    public AudioFocusInfoBuilder setSdk(int sdk) {
        mSdk = sdk;
        return this;
    }

    public AudioFocusInfoBuilder setBundle(Bundle bundle) {
        mBundle = bundle;
        return this;
    }

    public AudioFocusInfoBuilder setDelayedFocusRequestEnable(boolean delayedFocusRequestEnabled) {
        mDelayedFocusRequestEnabled = delayedFocusRequestEnabled;
        return this;
    }

    public AudioFocusInfoBuilder setPausesOnDuckRequestEnable(boolean pausesOnDuckRequestEnabled) {
        mPausesOnDuckRequestEnabled = pausesOnDuckRequestEnabled;
        return this;
    }


    public AudioFocusInfo createAudioFocusInfo() {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        if (AudioAttributes.isSystemUsage(mUsage)) {
            builder.setSystemUsage(mUsage);
        } else {
            builder.setUsage(mUsage);
        }

        int flags = 0;
        if (mBundle != null) {
            builder = builder.addBundle(mBundle);
        }

        if (mDelayedFocusRequestEnabled) {
            flags |= AudioManager.AUDIOFOCUS_FLAG_DELAY_OK;
        }

        if (mPausesOnDuckRequestEnabled) {
            flags |= AudioManager.AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS;
        }

        return new AudioFocusInfo(builder.build(), mClientUid, mClientId,
                mPackageName, mGainRequest, mLossReceived, flags, mSdk);
    }
}
