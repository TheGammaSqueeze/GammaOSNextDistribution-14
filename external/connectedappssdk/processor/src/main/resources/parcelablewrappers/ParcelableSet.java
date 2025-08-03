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
import java.util.HashSet;
import java.util.Set;

/** Wrapper for reading & writing {@link Set} instances from and to {@link Parcel} instances. */
public class ParcelableSet<E> implements Parcelable {

  private static final int NULL_SIZE = -1;

  private final Bundler bundler;
  private final BundlerType type;
  private final Set<E> set;

  /**
   * Create a wrapper for a given set.
   *
   * <p>The passed in {@link Bundler} must be capable of bundling {@code F}.
   */
  public static <F> ParcelableSet<F> of(Bundler bundler, BundlerType type, Set<F> set) {
    return new ParcelableSet<>(bundler, type, set);
  }

  public Set<E> get() {
    return set;
  }

  private ParcelableSet(Bundler bundler, BundlerType type, Set<E> set) {
    if (bundler == null || type == null) {
      throw new NullPointerException();
    }
    this.bundler = bundler;
    this.type = type;
    this.set = set;
  }

  private ParcelableSet(Parcel in) {
    bundler = in.readParcelable(Bundler.class.getClassLoader());
    int size = in.readInt();

    if (size == NULL_SIZE) {
      type = null;
      set = null;
      return;
    }

    set = new HashSet<>();
    type = (BundlerType) in.readParcelable(Bundler.class.getClassLoader());
    if (size > 0) {
      BundlerType valueType = type.typeArguments().get(0);
      for (int i = 0; i < size; i++) {
        @SuppressWarnings("unchecked")
        E value = (E) bundler.readFromParcel(in, valueType);
        set.add(value);
      }
    }
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(bundler, flags);

    if (set == null) {
      dest.writeInt(NULL_SIZE);
      return;
    }

    dest.writeInt(set.size());
    dest.writeParcelable(type, flags);
    if (!set.isEmpty()) {
      BundlerType valueType = type.typeArguments().get(0);

      for (E value : set) {
        bundler.writeToParcel(dest, value, valueType, flags);
      }
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @SuppressWarnings("rawtypes")
  public static final Creator<ParcelableSet> CREATOR =
      new Creator<ParcelableSet>() {
        @Override
        public ParcelableSet createFromParcel(Parcel in) {
          return new ParcelableSet(in);
        }

        @Override
        public ParcelableSet[] newArray(int size) {
          return new ParcelableSet[size];
        }
      };
}
