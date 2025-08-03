/*
 * Copyright (C) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.aidl.tests;

import android.os.Parcel;
import android.os.Parcelable;

public class BadParcelable implements Parcelable {
  private boolean mBad;
  private String mName;
  private int mNumber;

  BadParcelable() {}
  BadParcelable(boolean bad, String name, int number) {
    this.mBad = bad;
    this.mName = name;
    this.mNumber = number;
  }

  public int describeContents() { return 0; }

  public void writeToParcel(Parcel dest, int flags) {
    dest.writeBoolean(mBad);
    dest.writeString(mName);
    dest.writeInt(mNumber);
    // BAD! write superfluous data
    if (mBad) {
      dest.writeInt(42);
    }
  }

  public void readFromParcel(Parcel source) {
    mBad = source.readBoolean();
    mName = source.readString();
    mNumber = source.readInt();
  }

  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof BadParcelable)) {
      return false;
    }
    BadParcelable p = (BadParcelable) o;
    if (mBad != p.mBad) {
      return false;
    }
    if ((mName == null && p.mName != null) || (mName != null && !mName.equals(p.mName))) {
      return false;
    }
    return mNumber == p.mNumber;
  }

  public String toString() {
    return "BadParcelable(bad=" + mBad + ",name=" + mName + ",number=" + mNumber + "}";
  }

  public static final Parcelable.Creator<BadParcelable> CREATOR =
      new Parcelable.Creator<BadParcelable>() {
        public BadParcelable createFromParcel(Parcel source) {
          BadParcelable p = new BadParcelable();
          p.readFromParcel(source);
          return p;
        }

        public BadParcelable[] newArray(int size) { return new BadParcelable[size]; }
      };
}
