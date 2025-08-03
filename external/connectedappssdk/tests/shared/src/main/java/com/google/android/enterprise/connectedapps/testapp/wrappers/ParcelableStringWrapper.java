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
package com.google.android.enterprise.connectedapps.testapp.wrappers;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.enterprise.connectedapps.annotations.CustomParcelableWrapper;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.BundlerType;
import com.google.android.enterprise.connectedapps.testapp.StringWrapper;

@CustomParcelableWrapper(originalType = StringWrapper.class)
public class ParcelableStringWrapper implements Parcelable {

  private static final int NULL = -1;
  private static final int NOT_NULL = 1;

  private final StringWrapper stringWrapper;

  /**
   * Create a wrapper for a given {@link StringWrapper}.
   *
   * <p>The passed in {@link Bundler} and {@link BundlerType} are ignored.
   */
  public static ParcelableStringWrapper of(
      Bundler bundler, BundlerType type, StringWrapper stringWrapper) {
    return new ParcelableStringWrapper(bundler, type, stringWrapper);
  }

  public StringWrapper get() {
    return stringWrapper;
  }

  private ParcelableStringWrapper(Bundler bundler, BundlerType type, StringWrapper stringWrapper) {
    // Ignore bundler and type as we aren't generic
    this.stringWrapper = stringWrapper;
  }

  private ParcelableStringWrapper(Parcel in) {
    int presentValue = in.readInt();

    if (presentValue == NULL) {
      stringWrapper = null;
      return;
    }

    String value = in.readString();

    stringWrapper = new StringWrapper(value);
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    if (stringWrapper == null) {
      dest.writeInt(NULL);
      return;
    }

    dest.writeInt(NOT_NULL);
    dest.writeString(stringWrapper.value());
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<ParcelableStringWrapper> CREATOR =
      new Creator<ParcelableStringWrapper>() {
        @Override
        public ParcelableStringWrapper createFromParcel(Parcel in) {
          return new ParcelableStringWrapper(in);
        }

        @Override
        public ParcelableStringWrapper[] newArray(int size) {
          return new ParcelableStringWrapper[size];
        }
      };
}
