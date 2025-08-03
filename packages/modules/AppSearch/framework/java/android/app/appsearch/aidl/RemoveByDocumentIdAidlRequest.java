/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.appsearch.aidl;

import android.annotation.ElapsedRealtimeLong;
import android.annotation.NonNull;
import android.app.appsearch.safeparcel.AbstractSafeParcelable;
import android.app.appsearch.safeparcel.SafeParcelable;
import android.os.Parcel;
import android.os.UserHandle;

import java.util.List;
import java.util.Objects;

/**
 * Encapsulates a request to make a binder call to remove documents by id.
 * @hide
 */
@SafeParcelable.Class(creator = "RemoveByDocumentIdAidlRequestCreator")
public class RemoveByDocumentIdAidlRequest extends AbstractSafeParcelable {
    @NonNull
    public static final RemoveByDocumentIdAidlRequestCreator CREATOR =
            new RemoveByDocumentIdAidlRequestCreator();

    @NonNull
    @Field(id = 1, getter = "getCallerAttributionSource")
    private final AppSearchAttributionSource mCallerAttributionSource;
    @NonNull
    @Field(id = 2, getter = "getDatabaseName")
    private final String mDatabaseName;
    @NonNull
    @Field(id = 3, getter = "getNamespace")
    private final String mNamespace;
    @NonNull
    @Field(id = 4, getter = "getIds")
    private final List<String> mIds;
    @NonNull
    @Field(id = 5, getter = "getUserHandle")
    private final UserHandle mUserHandle;
    @Field(id = 6, getter = "getBinderCallStartTimeMillis")
    private final @ElapsedRealtimeLong long mBinderCallStartTimeMillis;

    /**
     * Removes documents by ID.
     *
     * @param callerAttributionSource The permission identity of the package the document is in.
     * @param databaseName The databaseName the document is in.
     * @param namespace    Namespace of the document to remove.
     * @param ids The IDs of the documents to delete
     * @param userHandle Handle of the calling user
     * @param binderCallStartTimeMillis start timestamp of binder call in Millis
     */
    @Constructor
    public RemoveByDocumentIdAidlRequest(
            @Param(id = 1) @NonNull AppSearchAttributionSource callerAttributionSource,
            @Param(id = 2) @NonNull String databaseName,
            @Param(id = 3) @NonNull String namespace,
            @Param(id = 4) @NonNull List<String> ids,
            @Param(id = 5) @NonNull UserHandle userHandle,
            @Param(id = 6) @ElapsedRealtimeLong long binderCallStartTimeMillis) {
        mCallerAttributionSource = Objects.requireNonNull(callerAttributionSource);
        mDatabaseName = Objects.requireNonNull(databaseName);
        mNamespace = Objects.requireNonNull(namespace);
        mIds = Objects.requireNonNull(ids);
        mUserHandle = Objects.requireNonNull(userHandle);
        mBinderCallStartTimeMillis = binderCallStartTimeMillis;
    }

    @NonNull
    public AppSearchAttributionSource getCallerAttributionSource() {
        return mCallerAttributionSource;
    }

    @NonNull
    public String getDatabaseName() {
        return mDatabaseName;
    }

    @NonNull
    public String getNamespace() {
        return mNamespace;
    }

    @NonNull
    public List<String> getIds() {
        return mIds;
    }

    @NonNull
    public UserHandle getUserHandle() {
        return mUserHandle;
    }

    @ElapsedRealtimeLong
    public long getBinderCallStartTimeMillis() {
        return mBinderCallStartTimeMillis;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        RemoveByDocumentIdAidlRequestCreator.writeToParcel(this, dest, flags);
    }
}
