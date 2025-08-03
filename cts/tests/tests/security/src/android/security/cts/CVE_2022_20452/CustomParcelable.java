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

package android.security.cts.CVE_2022_20452;

import android.os.Parcel;
import android.os.Parcelable;

public class CustomParcelable implements Parcelable {
    private boolean mDummyValue = true;

    CustomParcelable() {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeBoolean(mDummyValue);
    }

    public static final Creator<CustomParcelable> CREATOR =
            new Creator<CustomParcelable>() {
                @Override
                public CustomParcelable createFromParcel(Parcel in) {
                    return new CustomParcelable();
                }

                @Override
                public CustomParcelable[] newArray(int size) {
                    return new CustomParcelable[size];
                }
            };
}
