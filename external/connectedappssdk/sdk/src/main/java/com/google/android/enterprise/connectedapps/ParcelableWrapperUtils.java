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
package com.google.android.enterprise.connectedapps;

import android.os.Parcel;
import com.google.android.enterprise.connectedapps.internal.Bundler;

/** Utility methods for use when writing a parcelable wrapper. */
public class ParcelableWrapperUtils {

  /**
   * Write a {@link Bundler} to a {@link Parcel}.
   *
   * <p>This should be called as the first line of your {@code #writeToParcel(Parcel, int)} method.
   */
  public void writeBundler(Parcel parcel, Bundler bundler, int flags) {
    parcel.writeParcelable(bundler, flags);
  }

  /**
   * Read a {@link Bundler} from a {@link Parcel}.
   *
   * <p>This should be called in the first line of your {@code createFromParcel(Parcel)} method.
   */
  public Bundler readBundler(Parcel parcel) {
    return parcel.readParcelable(Parcel.class.getClassLoader());
  }

  private ParcelableWrapperUtils() {}
}
