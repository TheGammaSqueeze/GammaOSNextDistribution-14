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

package com.android.managedprovisioning.provisioning;

import android.annotation.DrawableRes;
import android.annotation.RawRes;
import android.annotation.StringRes;

import com.android.managedprovisioning.util.LazyStringResource;
import com.android.managedprovisioning.util.LazyStringResource.Empty;

/**
 * A wrapper describing the contents of an education screen.
 */
final class TransitionScreenWrapper {
    public final LazyStringResource header;
    public final LazyStringResource description;
    public final @RawRes int drawable;
    public final LazyStringResource subHeaderTitle;
    public final LazyStringResource subHeader;
    public final @DrawableRes int subHeaderIcon;
    public final boolean shouldLoop;
    public final LazyStringResource secondarySubHeaderTitle;
    public final LazyStringResource secondarySubHeader;
    public final @DrawableRes int secondarySubHeaderIcon;

    TransitionScreenWrapper(LazyStringResource header, @RawRes int drawable) {
        this(header, /* description= */ Empty.INSTANCE, drawable, /* shouldLoop= */ true);
    }

    TransitionScreenWrapper(@StringRes int headerId, @RawRes int drawable) {
        this(LazyStringResource.of(headerId), drawable);
    }

    TransitionScreenWrapper(
            LazyStringResource header,
            LazyStringResource description,
            @RawRes int drawable,
            boolean shouldLoop) {
        this(
                header,
                description,
                drawable,
                /* subHeaderTitle= */ Empty.INSTANCE,
                /* subHeader= */ Empty.INSTANCE,
                /* subHeaderIcon= */ 0,
                shouldLoop,
                /* secondarySubHeaderTitle= */ Empty.INSTANCE,
                /* secondarySubHeader= */ Empty.INSTANCE,
                /* secondarySubHeaderIcon= */ 0);
    }

    TransitionScreenWrapper(
            @StringRes int headerId,
            @StringRes int descriptionId,
            @RawRes int drawable,
            boolean shouldLoop) {
        this(LazyStringResource.of(headerId), LazyStringResource.of(descriptionId),
                drawable, shouldLoop);
    }

    private TransitionScreenWrapper(
            LazyStringResource header,
            LazyStringResource description,
            int drawable,
            LazyStringResource subHeaderTitle,
            LazyStringResource subHeader,
            int subHeaderIcon,
            boolean shouldLoop,
            LazyStringResource secondarySubHeaderTitle,
            LazyStringResource secondarySubHeader,
            int secondarySubHeaderIcon) {
        this.header = header;
        this.description = description;
        this.drawable = drawable;
        this.subHeaderTitle = subHeaderTitle;
        this.subHeader = subHeader;
        this.subHeaderIcon = subHeaderIcon;
        this.shouldLoop = shouldLoop;
        this.secondarySubHeaderTitle = secondarySubHeaderTitle;
        this.secondarySubHeader = secondarySubHeader;
        this.secondarySubHeaderIcon = secondarySubHeaderIcon;

        validateFields();
    }

    private void validateFields() {
        final boolean isItemProvided =
                !(subHeader instanceof Empty)
                        || subHeaderIcon != 0
                        || !(subHeaderTitle instanceof Empty)
                        || !(secondarySubHeader instanceof Empty)
                        || secondarySubHeaderIcon != 0
                        || !(secondarySubHeaderTitle instanceof Empty);
        if (isItemProvided && drawable != 0) {
            throw new IllegalArgumentException("Cannot show items and animation at the same time.");
        }
        if (header instanceof Empty) {
            throw new IllegalArgumentException("Header resource id must be a positive number.");
        }
    }

    public static final class Builder {
        private LazyStringResource mHeader = Empty.INSTANCE;
        private LazyStringResource mDescription = Empty.INSTANCE;
        @RawRes
        private int mDrawable;
        private LazyStringResource mSubHeaderTitle = Empty.INSTANCE;
        private LazyStringResource mSubHeader = Empty.INSTANCE;
        @DrawableRes
        private int mSubHeaderIcon;
        private boolean mShouldLoop;
        private LazyStringResource mSecondarySubHeaderTitle = Empty.INSTANCE;
        private LazyStringResource mSecondarySubHeader = Empty.INSTANCE;
        @DrawableRes
        private int mSecondarySubHeaderIcon;

        public Builder setHeader(LazyStringResource header) {
            this.mHeader = header;
            return this;
        }

        public Builder setHeader(@StringRes int headerId) {
            return setHeader(LazyStringResource.of(headerId));
        }

        public Builder setDescription(LazyStringResource description) {
            this.mDescription = description;
            return this;
        }

        public Builder setDescription(@StringRes int descriptionId) {
            return setDescription(LazyStringResource.of(descriptionId));
        }

        public Builder setAnimation(int drawable) {
            this.mDrawable = drawable;
            return this;
        }

        public Builder setSubHeaderTitle(LazyStringResource subHeaderTitle) {
            this.mSubHeaderTitle = subHeaderTitle;
            return this;
        }

        public Builder setSubHeaderTitle(@StringRes int subHeaderTitleId) {
            return setSubHeaderTitle(LazyStringResource.of(subHeaderTitleId));
        }

        public Builder setSubHeader(LazyStringResource subHeader) {
            this.mSubHeader = subHeader;
            return this;
        }

        public Builder setSubHeader(@StringRes int subHeaderId) {
            return setSubHeader(LazyStringResource.of(subHeaderId));
        }

        public Builder setSubHeaderIcon(int subHeaderIcon) {
            this.mSubHeaderIcon = subHeaderIcon;
            return this;
        }

        public Builder setShouldLoop(boolean shouldLoop) {
            this.mShouldLoop = shouldLoop;
            return this;
        }

        public Builder setSecondarySubHeaderTitle(LazyStringResource secondarySubHeaderTitle) {
            this.mSecondarySubHeaderTitle = secondarySubHeaderTitle;
            return this;
        }

        public Builder setSecondarySubHeaderTitle(@StringRes int secondarySubHeaderTitleId) {
            return setSecondarySubHeaderTitle(LazyStringResource.of(secondarySubHeaderTitleId));
        }

        public Builder setSecondarySubHeader(LazyStringResource secondarySubHeader) {
            this.mSecondarySubHeader = secondarySubHeader;
            return this;
        }

        public Builder setSecondarySubHeader(@StringRes int secondarySubHeaderId) {
            return setSecondarySubHeader(LazyStringResource.of(secondarySubHeaderId));
        }

        public Builder setSecondarySubHeaderIcon(int secondarySubHeaderIcon) {
            this.mSecondarySubHeaderIcon = secondarySubHeaderIcon;
            return this;
        }

        public TransitionScreenWrapper build() {
            return new TransitionScreenWrapper(
                    mHeader,
                    mDescription,
                    mDrawable,
                    mSubHeaderTitle,
                    mSubHeader,
                    mSubHeaderIcon,
                    mShouldLoop,
                    mSecondarySubHeaderTitle,
                    mSecondarySubHeader,
                    mSecondarySubHeaderIcon);
        }
    }
}
