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

package com.android.adservices.cts.dao;

import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema;

@Document
public final class AppSearchNotificationDao {
    public static final String NAMESPACE = "notifications";

    @Document.Id private final String mId;
    @Document.Namespace private final String mNamespace;
    @Document.BooleanProperty private final boolean mWasNotificationDisplayed;
    @Document.BooleanProperty private final boolean mWasGaUxNotificationDisplayed;

    @Document.StringProperty(
            indexingType = AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_EXACT_TERMS)
    private final String mUserId;

    public AppSearchNotificationDao(
            String id,
            String userId,
            String namespace,
            boolean wasNotificationDisplayed,
            boolean wasGaUxNotificationDisplayed) {
        this.mId = id;
        this.mUserId = userId;
        this.mNamespace = namespace;
        this.mWasNotificationDisplayed = wasNotificationDisplayed;
        this.mWasGaUxNotificationDisplayed = wasGaUxNotificationDisplayed;
    }

    public String getId() {
        return mId;
    }

    public String getUserId() {
        return mUserId;
    }

    public String getNamespace() {
        return mNamespace;
    }

    public Boolean getWasNotificationDisplayed() {
        return mWasNotificationDisplayed;
    }

    public Boolean getWasGaUxNotificationDisplayed() {
        return mWasGaUxNotificationDisplayed;
    }
}
