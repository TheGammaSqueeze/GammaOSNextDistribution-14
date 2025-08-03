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
package com.google.android.enterprise.connectedapps.parcelablewrappers;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Pair;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.BundlerType;

/** Wrapper for reading & writing {@link Pair} instances from and to {@link Parcel} instances. */
public class ParcelablePair<F, S> implements Parcelable {

  private static final int NULL = -1;
  private static final int NOT_NULL = 1;

  private final Bundler bundler;
  private final BundlerType type;
  private final Pair<F, S> pair;

  /**
   * Create a wrapper for a given pair.
   *
   * <p>The passed in {@link Bundler} must be capable of bundling {@code E} and {@code F}.
   */
  public static <F, S> ParcelablePair<F, S> of(Bundler bundler, BundlerType type, Pair<F, S> pair) {
    return new ParcelablePair<>(bundler, type, pair);
  }

  public Pair<F, S> get() {
    return pair;
  }

  private ParcelablePair(Bundler bundler, BundlerType type, Pair<F, S> pair) {
    if (bundler == null || type == null) {
      throw new NullPointerException();
    }
    this.bundler = bundler;
    this.type = type;
    this.pair = pair;
  }

  private ParcelablePair(Parcel in) {
    bundler = in.readParcelable(Bundler.class.getClassLoader());
    int present = in.readInt();

    if (present == NULL) {
      type = null;
      pair = null;
      return;
    }

    type = (BundlerType) in.readParcelable(Bundler.class.getClassLoader());
    BundlerType fType = type.typeArguments().get(0);
    BundlerType sType = type.typeArguments().get(1);

    @SuppressWarnings("unchecked")
    F first = (F) bundler.readFromParcel(in, fType);
    @SuppressWarnings("unchecked")
    S second = (S) bundler.readFromParcel(in, sType);

    pair = new Pair<>(first, second);
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(bundler, flags);

    if (pair == null) {
      dest.writeInt(NULL);
      return;
    }

    dest.writeInt(NOT_NULL);
    dest.writeParcelable(type, flags);

    BundlerType fType = type.typeArguments().get(0);
    BundlerType sType = type.typeArguments().get(1);

    bundler.writeToParcel(dest, pair.first, fType, flags);
    bundler.writeToParcel(dest, pair.second, sType, flags);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @SuppressWarnings("rawtypes")
  public static final Creator<ParcelablePair> CREATOR =
      new Creator<ParcelablePair>() {
        @Override
        public ParcelablePair createFromParcel(Parcel in) {
          return new ParcelablePair(in);
        }

        @Override
        public ParcelablePair[] newArray(int size) {
          return new ParcelablePair[size];
        }
      };
}
