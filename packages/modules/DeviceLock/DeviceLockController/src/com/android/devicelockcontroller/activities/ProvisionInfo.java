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

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * A data model class which is used to hold the data needed to render the RecyclerView.
 */
public final class ProvisionInfo {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {ProvisionInfoType.REGULAR, ProvisionInfoType.TERMS_AND_CONDITIONS,
            ProvisionInfoType.SUPPORT})
    public @interface ProvisionInfoType {
        // The general type of provision info without any link
        int REGULAR = 0;
        // The type of provision info with a link for terms and conditions.
        int TERMS_AND_CONDITIONS = 1;
        // The type of provision info with a link for custom support.
        int SUPPORT = 2;
    }

    @ProvisionInfoType
    private final int mType;
    private final int mDrawableId;
    private final int mTextId;

    private String mProviderName;
    private String mUrl;

    public ProvisionInfo(int drawableId, int textId, @ProvisionInfoType int type) {
        mDrawableId = drawableId;
        mTextId = textId;
        mType = type;
    }

    public int getDrawableId() {
        return mDrawableId;
    }

    public int getTextId() {
        return mTextId;
    }

    @ProvisionInfoType
    public int getType() {
        return mType;
    }

    public String getProviderName() {
        return mProviderName;
    }

    public void setProviderName(String providerName) {
        mProviderName = providerName;
    }

    public String getUrl() {
        return mUrl;
    }

    public void setUrl(String url) {
        mUrl = url;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ProvisionInfo that)) {
            return false;
        }
        return this.mDrawableId == that.mDrawableId && this.mTextId == that.mTextId
                && this.mType == that.mType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDrawableId, mTextId, mType);
    }
}
