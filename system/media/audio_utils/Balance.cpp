/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <audio_utils/Balance.h>

namespace android::audio_utils {

void Balance::setChannelMask(audio_channel_mask_t channelMask)
{
    using namespace ::android::audio_utils::channels;
    channelMask = static_cast<audio_channel_mask_t>(channelMask & ~AUDIO_CHANNEL_HAPTIC_ALL);
    if (!audio_is_output_channel(channelMask) // invalid mask
            || mChannelMask == channelMask) { // no need to do anything
        return;
    }

    mChannelMask = channelMask;
    mChannelCount = audio_channel_count_from_out_mask(channelMask);

    // save mBalance into balance for later restoring, then reset
    const float balance = mBalance;
    mBalance = 0.f;

    // reset mVolumes
    mVolumes.resize(mChannelCount);
    std::fill(mVolumes.begin(), mVolumes.end(), 1.f);

    // reset ramping variables
    mRampBalance = 0.f;
    mRampVolumes.clear();

    if (audio_channel_mask_get_representation(mChannelMask)
            == AUDIO_CHANNEL_REPRESENTATION_INDEX) {
        mSides.clear();       // mSides unused for channel index masks.
        setBalance(balance);  // recompute balance
        return;
    }

    mSides.resize(mChannelCount);
    // If LFE and LFE2 both exist, it should be L and R in 22.2
    int lfe = -1;
    int lfe2 = -1;
    constexpr unsigned LFE_CHANNEL_INDEX = 3;
    constexpr unsigned LFE2_CHANNEL_INDEX = 23;
    for (unsigned i = 0, channel = channelMask; channel != 0; ++i) {
        const int index = __builtin_ctz(channel);
        mSides[i] = sideFromChannelIdx(index);
        // Keep track of LFE indices
        if (index == LFE_CHANNEL_INDEX) {
            lfe = i;
        } else if (index == LFE2_CHANNEL_INDEX) {
            lfe2 = i;
        }
        channel &= ~(1 << index);
    }
    if (lfe >= 0 && lfe2 >= 0) { // if both LFEs exist assign to L and R.
        mSides[lfe] = AUDIO_GEOMETRY_SIDE_LEFT;
        mSides[lfe2] = AUDIO_GEOMETRY_SIDE_RIGHT;
    }
    setBalance(balance); // recompute balance
}

void Balance::process(float *buffer, size_t frames)
{
    if (mBalance == 0.f || mChannelCount < 2) {
        return;
    }

    if (mRamp) {
        if (mRampVolumes.size() != mVolumes.size()) {
            // If mRampVolumes is empty, we do not ramp in this process() but directly
            // apply the existing mVolumes. We save the balance and volume state here
            // and fall through to non-ramping code below. The next process() will ramp if needed.
            mRampBalance = mBalance;
            mRampVolumes = mVolumes;
        } else if (mRampBalance != mBalance) {
            if (frames > 0) {
                std::vector<float> mDeltas(mVolumes.size());
                const float r = 1.f / frames;
                for (size_t j = 0; j < mChannelCount; ++j) {
                    mDeltas[j] = (mVolumes[j] - mRampVolumes[j]) * r;
                }

                // ramped balance
                for (size_t i = 0; i < frames; ++i) {
                    const float findex = i;
                    for (size_t j = 0; j < mChannelCount; ++j) { // better precision: delta * i
                        *buffer++ *= mRampVolumes[j] + mDeltas[j] * findex;
                    }
                }
            }
            mRampBalance = mBalance;
            mRampVolumes = mVolumes;
            return;
        }
        // fall through
    }

    // non-ramped balance
    for (size_t i = 0; i < frames; ++i) {
        for (size_t j = 0; j < mChannelCount; ++j) {
            *buffer++ *= mVolumes[j];
        }
    }
}

void Balance::computeStereoBalance(float balance, float *left, float *right) const
{
    if (balance > 0.f) {
        *left = mCurve(1.f - balance);
        *right = 1.f;
    } else if (balance < 0.f) {
        *left = 1.f;
        *right = mCurve(1.f + balance);
    } else {
        *left = 1.f;
        *right = 1.f;
    }

    // Functionally:
    // *left = balance > 0.f ? mCurve(1.f - balance) : 1.f;
    // *right = balance < 0.f ? mCurve(1.f + balance) : 1.f;
}

std::string Balance::toString() const
{
    std::stringstream ss;
    ss << "balance " << mBalance << " channelCount " << mChannelCount << " volumes:";
    for (float volume : mVolumes) {
        ss << " " << volume;
    }
    // we do not show mSides, which is only valid for channel position masks.
    return ss.str();
}

void Balance::setBalance(float balance)
{
    using namespace ::android::audio_utils::channels;
    if (mBalance == balance                         // no change
        || isnan(balance) || fabs(balance) > 1.f) { // balance out of range
        return;
    }

    mBalance = balance;

    if (mChannelCount < 2) { // if channel count is 1, mVolumes[0] is already set to 1.f
        return;              // and if channel count < 2, we don't do anything in process().
    }

    // Handle the common cases:
    // stereo and channel index masks only affect the first two channels as left and right.
    if (mChannelMask == AUDIO_CHANNEL_OUT_STEREO
            || audio_channel_mask_get_representation(mChannelMask)
                    == AUDIO_CHANNEL_REPRESENTATION_INDEX) {
        computeStereoBalance(balance, &mVolumes[0], &mVolumes[1]);
        return;
    }

    // For position masks with more than 2 channels, we consider which side the
    // speaker position is on to figure the volume used.
    float balanceVolumes[3]; // left, right, center (we don't care the order)
    static_assert(AUDIO_GEOMETRY_SIDE_LEFT >= 0
            && AUDIO_GEOMETRY_SIDE_LEFT <= std::size(balanceVolumes));
    static_assert(AUDIO_GEOMETRY_SIDE_RIGHT >= 0
            && AUDIO_GEOMETRY_SIDE_RIGHT <= std::size(balanceVolumes));
    static_assert(AUDIO_GEOMETRY_SIDE_CENTER >= 0
            && AUDIO_GEOMETRY_SIDE_CENTER <= std::size(balanceVolumes));
    computeStereoBalance(balance, &balanceVolumes[AUDIO_GEOMETRY_SIDE_LEFT],
            &balanceVolumes[AUDIO_GEOMETRY_SIDE_RIGHT]);
    balanceVolumes[AUDIO_GEOMETRY_SIDE_CENTER] = 1.f; // center  TODO: consider center scaling.

    for (size_t i = 0; i < mVolumes.size(); ++i) {
        mVolumes[i] = balanceVolumes[mSides[i]];
    }
}

} // namespace android::audio_utils
