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

package android.service.games.testing;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;

public final class ActivityResult implements Parcelable {
    private final String mGameSessionPackageName;
    @Nullable
    private final Success mSuccess;
    @Nullable
    private final Failure mFailure;

    public static final Creator<ActivityResult> CREATOR = new Creator<ActivityResult>() {
        @Override
        public ActivityResult createFromParcel(Parcel in) {
            String gameSessionPackageName = in.readString();
            Success success = in.readParcelable(Success.class.getClassLoader(), Success.class);
            Failure failure = in.readParcelable(Failure.class.getClassLoader(), Failure.class);
            return new ActivityResult(gameSessionPackageName, success, failure);
        }

        @Override
        public ActivityResult[] newArray(int size) {
            return new ActivityResult[size];
        }
    };

    public static ActivityResult forSuccess(String gameSessionPackageName, int resultCode,
            @Nullable Intent data) {
        return new ActivityResult(gameSessionPackageName, new Success(resultCode, data), null);
    }

    public static ActivityResult forError(String gameSessionPackageName, Throwable t) {
        return new ActivityResult(gameSessionPackageName, null,
                new Failure(t.getClass(), t.getMessage()));
    }

    private ActivityResult(String gameSessionPackageName, @Nullable Success success,
            @Nullable Failure failure) {
        mGameSessionPackageName = gameSessionPackageName;
        mSuccess = success;
        mFailure = failure;
    }

    public String getGameSessionPackageName() {
        return mGameSessionPackageName;
    }

    public boolean isSuccess() {
        return mSuccess != null;
    }

    public Success getSuccess() {
        Preconditions.checkState(isSuccess());
        return mSuccess;
    }

    public Failure getFailure() {
        Preconditions.checkState(!isSuccess());
        return mFailure;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mGameSessionPackageName);
        dest.writeParcelable(mSuccess, flags);
        dest.writeParcelable(mFailure, flags);
    }

    public static final class Success implements Parcelable {
        public static final Creator<Success> CREATOR = new Creator<Success>() {
            @Override
            public Success createFromParcel(Parcel source) {
                int resultCode = source.readInt();
                Intent data = source.readParcelable(Intent.class.getClassLoader(), Intent.class);
                return new Success(resultCode, data);
            }

            @Override
            public Success[] newArray(int size) {
                return new Success[0];
            }
        };

        private final int mResultCode;
        @Nullable
        private final Intent mData;

        Success(int resultCode, @Nullable Intent data) {
            mResultCode = resultCode;
            mData = data;
        }

        public int getResultCode() {
            return mResultCode;
        }

        @Nullable
        public Intent getData() {
            return mData;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mResultCode);
            dest.writeParcelable(mData, flags);
        }
    }

    public static final class Failure implements Parcelable {
        public static final Creator<Failure> CREATOR = new Creator<Failure>() {
            @Override
            public Failure createFromParcel(Parcel source) {
                Class<?> clazz = source.readSerializable(Class.class.getClassLoader(), Class.class);
                String message = source.readString();
                return new Failure(clazz, message);
            }

            @Override
            public Failure[] newArray(int size) {
                return new Failure[0];
            }
        };

        private final Class<?> mClazz;
        private final String mMessage;

        Failure(Class<?> clazz, String message) {
            mClazz = clazz;
            mMessage = message;
        }

        public Class<?> getClazz() {
            return mClazz;
        }

        public String getMessage() {
            return mMessage;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeSerializable(mClazz);
            dest.writeString(mMessage);
        }
    }
}
