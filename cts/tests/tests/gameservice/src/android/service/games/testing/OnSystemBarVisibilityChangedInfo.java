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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public final class OnSystemBarVisibilityChangedInfo implements Parcelable {
    private int mTimesShown;
    private int mTimesHidden;

    public static final Creator<OnSystemBarVisibilityChangedInfo> CREATOR =
            new Creator<OnSystemBarVisibilityChangedInfo>() {
                @Override
                public OnSystemBarVisibilityChangedInfo createFromParcel(Parcel in) {
                    int timesShown = in.readInt();
                    int timesHidden = in.readInt();
                    return new OnSystemBarVisibilityChangedInfo(timesShown, timesHidden);
                }

                @Override
                public OnSystemBarVisibilityChangedInfo[] newArray(int size) {
                    return new OnSystemBarVisibilityChangedInfo[size];
                }
            };

    public OnSystemBarVisibilityChangedInfo() {
        this(0, 0);
    }

    public OnSystemBarVisibilityChangedInfo(int timesShown,
            int timesHidden) {
        mTimesShown = timesShown;
        mTimesHidden = timesHidden;
    }

    public void incrementTimesShown() {
        mTimesShown++;
    }

    public void incrementTimesHidden() {
        mTimesHidden++;
    }

    public int getTimesShown() {
        return mTimesShown;
    }

    public int getTimesHidden() {
        return mTimesHidden;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mTimesShown);
        dest.writeInt(mTimesHidden);
    }
}
