/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.testapp;

import static com.google.common.base.Preconditions.checkNotNull;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ParcelableObject implements Parcelable {

  @SuppressWarnings("rawtypes")
  public static final Parcelable.Creator CREATOR =
      new Parcelable.Creator() {
        @Override
        public ParcelableObject createFromParcel(Parcel in) {
          return new ParcelableObject(in);
        }

        @Override
        public ParcelableObject[] newArray(int size) {
          return new ParcelableObject[size];
        }
      };

  private final String value;

  public String value() {
    return value;
  }

  public ParcelableObject(Parcel in) {
    this(in.readString());
  }

  public ParcelableObject(String value) {
    this.value = checkNotNull(value);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(value);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ParcelableObject that = (ParcelableObject) o;
    return value.equals(that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
