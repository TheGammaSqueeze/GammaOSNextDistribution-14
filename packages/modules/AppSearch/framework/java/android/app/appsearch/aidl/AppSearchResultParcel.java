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

package android.app.appsearch.aidl;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.ParcelableUtil;
import android.app.appsearch.safeparcel.AbstractSafeParcelable;
import android.app.appsearch.safeparcel.SafeParcelable;
import android.os.Parcel;

/**
 * Parcelable wrapper around {@link AppSearchResult}.
 *
 * <p>{@link AppSearchResult} can contain any value, including non-parcelable values. For the
 * specific case of sending {@link AppSearchResult} across Binder, this class wraps an
 * {@link AppSearchResult} that contains a parcelable type and provides parcelability of the whole
 * structure.
 *
 * @param <ValueType> The type of result object for successful calls. Must be a parcelable type.
 * @hide
 */
@SafeParcelable.Class(creator = "AppSearchResultParcelCreator", creatorIsFinal = false)
public final class AppSearchResultParcel<ValueType> extends AbstractSafeParcelable {

    @NonNull
    public static final AppSearchResultParcelCreator CREATOR =
            new AppSearchResultParcelCreator() {
                @Override
                public AppSearchResultParcel createFromParcel(Parcel in) {
                    // We pass the result we get from ParcelableUtil#readBlob to
                    // AppSearchResultParcelCreator to decode.
                    byte[] dataBlob = ParcelableUtil.readBlob(in);
                    // Create a parcel object to un-serialize the byte array we are reading from
                    // Parcel.readBlob(). Parcel.WriteBlob() could take care of whether to pass
                    // data via binder directly or Android shared memory if the data is large.
                    Parcel unmarshallParcel = Parcel.obtain();
                    try {
                        unmarshallParcel.unmarshall(dataBlob, 0, dataBlob.length);
                        unmarshallParcel.setDataPosition(0);
                        return super.createFromParcel(unmarshallParcel);
                    } finally {
                        unmarshallParcel.recycle();
                    }
                }
            };

    @NonNull
    private static final AppSearchResultParcelCreator CREATOR_WITHOUT_BLOB =
            new AppSearchResultParcelCreator();

    @Field(id = 1)
    final int mResultCode;
    @Field(id = 2)
    @Nullable final ValueParcel mValue;
    @Field(id = 3)
    @Nullable final String mErrorMessage;

    @NonNull AppSearchResult<ValueType> mResultCached;

    @Constructor
    AppSearchResultParcel(
            @Param(id = 1) @AppSearchResult.ResultCode int resultCode,
            @Param(id = 2) @Nullable ValueParcel<ValueType> value,
            @Param(id = 3) @Nullable String errorMessage) {
        mResultCode = resultCode;
        mValue = value;
        mErrorMessage = errorMessage;
        if (mResultCode == AppSearchResult.RESULT_OK) {
            mResultCached = AppSearchResult.newSuccessfulResult((ValueType) mValue.getValue());
        } else {
            mResultCached = AppSearchResult.newFailedResult(mResultCode, mErrorMessage);
        }
    }

    /** Creates a new {@link AppSearchResultParcel} from the given result. */
    public AppSearchResultParcel(@NonNull AppSearchResult<ValueType> result) {
        mResultCached = result;
        mResultCode = result.getResultCode();
        if (mResultCode == AppSearchResult.RESULT_OK) {
            mValue = new ValueParcel<>(result.getResultValue());
            mErrorMessage = null;
        } else {
            mErrorMessage = result.getErrorMessage();
            mValue = null;
        }
    }

    @NonNull
    public AppSearchResult<ValueType> getResult() {
        return mResultCached;
    }

    /** @hide */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        // Serializes the whole object, So that we can use Parcel.writeBlob() to send data.
        // WriteBlob() could take care of whether to pass data via binder directly or Android shared
        // memory if the data is large.
        byte[] bytes;
        Parcel data = Parcel.obtain();
        try {
            // We pass encoded result from AppSearchResultParcelCreator to ParcelableUtil#writeBlob.
            directlyWriteToParcel(this, data, flags);
            bytes = data.marshall();
        } finally {
            data.recycle();
        }
        ParcelableUtil.writeBlob(dest, bytes);
    }

    static void directlyWriteToParcel(@NonNull AppSearchResultParcel result, @NonNull Parcel data,
            int flags) {
        AppSearchResultParcelCreator.writeToParcel(result, data, flags);
    }

    static AppSearchResultParcel directlyReadFromParcel(@NonNull Parcel data) {
        return CREATOR_WITHOUT_BLOB.createFromParcel(data);
    }
}
