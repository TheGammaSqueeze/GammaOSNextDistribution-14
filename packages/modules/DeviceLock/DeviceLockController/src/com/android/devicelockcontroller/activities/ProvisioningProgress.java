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

package com.android.devicelockcontroller.activities;

import static com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason.UNKNOWN_REASON;

import com.android.devicelockcontroller.R;
import com.android.devicelockcontroller.common.DeviceLockConstants.ProvisionFailureReason;

import java.util.Objects;

/**
 * Different stages of the provisioning progress.
 */
public final class ProvisioningProgress {

    public static final ProvisioningProgress GETTING_DEVICE_READY = new ProvisioningProgress(
            R.drawable.ic_smartphone_24px, R.string.getting_device_ready,
            R.string.this_may_take_a_few_minutes);
    public static final ProvisioningProgress INSTALLING_KIOSK_APP = new ProvisioningProgress(
            R.drawable.ic_downloading_24px, R.string.installing_kiosk_app);
    public static final ProvisioningProgress OPENING_KIOSK_APP = new ProvisioningProgress(
            R.drawable.ic_open_in_new_24px, R.string.opening_kiosk_app);

    final int mIconId;
    final int mHeaderId;
    final int mSubheaderId;
    final boolean mProgressBarVisible;
    final boolean mBottomViewVisible;

    final boolean mCountDownTimerVisible;

    @ProvisionFailureReason
    final int mFailureReason;

    ProvisioningProgress(int iconId, int headerId) {
        this(iconId, headerId, 0);
    }

    ProvisioningProgress(int iconId, int headerId, int subheaderId) {
        this(iconId, headerId, subheaderId, /* progressBarVisible= */ true,
                /* bottomViewVisible= */ false, /* countDownTimerVisible= */ false, UNKNOWN_REASON);
    }

    ProvisioningProgress(boolean bottomViewVisible, boolean countDownTimerVisible,
            @ProvisionFailureReason int failureReason) {
        this(R.drawable.ic_warning_24px, R.string.provisioning_failed,
                R.string.click_to_contact_financier, /* progressBarVisible=*/ false,
                bottomViewVisible, countDownTimerVisible, failureReason);
    }

    ProvisioningProgress(int iconId, int headerId, int subheaderId, boolean progressBarVisible,
            boolean bottomViewVisible, boolean countDownTimerVisible,
            @ProvisionFailureReason int failureReason) {
        mHeaderId = headerId;
        mIconId = iconId;
        mSubheaderId = subheaderId;
        mProgressBarVisible = progressBarVisible;
        mBottomViewVisible = bottomViewVisible;
        mCountDownTimerVisible = countDownTimerVisible;
        mFailureReason = failureReason;
    }

    /**
     * Get the provision failure progress item for mandatory case with the failure reason.
     *
     * @param failureReason one of {@link ProvisionFailureReason} The reason why provision failed.
     */
    public static ProvisioningProgress getMandatoryProvisioningFailedProgress(
            @ProvisionFailureReason int failureReason) {
        return new ProvisioningProgress(
                /* bottomViewVisible= */ false, /* countDownTimerVisible= */ true, failureReason);
    }

    /**
     * Get the provision failure progress item for non-mandatory case with the failure reason.
     *
     * @param failureReason one of {@link ProvisionFailureReason} The reason why provision failed.
     */
    public static ProvisioningProgress getNonMandatoryProvisioningFailedProgress(
            @ProvisionFailureReason int failureReason) {
        return new ProvisioningProgress(
                /* bottomViewVisible= */ true, /* countDownTimerVisible= */ false, failureReason);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProvisioningProgress that = (ProvisioningProgress) o;
        return mIconId == that.mIconId && mHeaderId == that.mHeaderId
                && mSubheaderId == that.mSubheaderId
                && mProgressBarVisible == that.mProgressBarVisible
                && mBottomViewVisible == that.mBottomViewVisible
                && mCountDownTimerVisible == that.mCountDownTimerVisible
                && mFailureReason == that.mFailureReason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mIconId, mHeaderId, mSubheaderId, mProgressBarVisible,
                mBottomViewVisible,
                mCountDownTimerVisible, mFailureReason);
    }

    @Override
    public String toString() {
        return "ProvisioningProgress{"
                + "mIconId=" + mIconId
                + ", mHeaderId=" + mHeaderId
                + ", mSubheaderId=" + mSubheaderId
                + ", mProgressBarVisible=" + mProgressBarVisible
                + ", mBottomViewVisible=" + mBottomViewVisible
                + ", mCountDownTimerVisible=" + mCountDownTimerVisible
                + ", mFailureReason=" + mFailureReason
                + '}';
    }
}
