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
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.BundlerType;

/** Wrapper for reading & writing arrays from and to {@link Parcel} instances. */
public class ParcelableArray<E> implements Parcelable {

  private static final int NULL_SIZE = -1;

  private final Bundler bundler;
  private final BundlerType type;
  private final E[] array;

  /**
   * Create a wrapper for a given array.
   *
   * <p>The passed in {@link Bundler} must be capable of bundling {@code F}.
   */
  public static <F> ParcelableArray<F> of(Bundler bundler, BundlerType type, F[] array) {
    return new ParcelableArray<>(bundler, type, array);
  }

  public E[] get() {
    return array;
  }

  private ParcelableArray(Bundler bundler, BundlerType type, E[] array) {
    if (bundler == null || type == null) {
      throw new NullPointerException();
    }
    this.bundler = bundler;
    this.type = type;
    this.array = array;
  }

  private ParcelableArray(Parcel in) {
    bundler = in.readParcelable(Bundler.class.getClassLoader());
    int size = in.readInt();

    if (size == NULL_SIZE) {
      type = null;
      array = null;
      return;
    }

    type = in.readParcelable(Bundler.class.getClassLoader());
    BundlerType valueType = type.typeArguments().get(0);

    @SuppressWarnings("unchecked")
    E[] a = (E[]) bundler.createArray(valueType, size);
    array = a;

    if (size > 0) {
      for (int i = 0; i < size; i++) {
        @SuppressWarnings("unchecked")
        E value = (E) bundler.readFromParcel(in, valueType);
        array[i] = value;
      }
    }
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(bundler, flags);

    if (array == null) {
      dest.writeInt(NULL_SIZE);
      return;
    }

    dest.writeInt(array.length);
    dest.writeParcelable(type, flags);
    if (array.length > 0) {
      BundlerType valueType = type.typeArguments().get(0);

      for (E value : array) {
        bundler.writeToParcel(dest, value, valueType, flags);
      }
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @SuppressWarnings("rawtypes")
  public static final Creator<ParcelableArray> CREATOR =
      new Creator<ParcelableArray>() {
        @Override
        public ParcelableArray createFromParcel(Parcel in) {
          return new ParcelableArray(in);
        }

        @Override
        public ParcelableArray[] newArray(int size) {
          return new ParcelableArray[size];
        }
      };
}
