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
import java.util.ArrayList;
import java.util.Collection;

/**
 * Wrapper for reading & writing {@link Collection} instances from and to {@link Parcel} instances.
 */
public class ParcelableCollection<E> implements Parcelable {

  private static final int NULL_SIZE = -1;

  private final Bundler bundler;
  private final BundlerType type;
  private final Collection<E> collection;

  /**
   * Create a wrapper for a given collection.
   *
   * <p>The passed in {@link Bundler} must be capable of bundling {@code F}.
   */
  public static <F> ParcelableCollection<F> of(
      Bundler bundler, BundlerType type, Collection<F> collection) {
    return new ParcelableCollection<>(bundler, type, collection);
  }

  public Collection<E> get() {
    return collection;
  }

  private ParcelableCollection(Bundler bundler, BundlerType type, Collection<E> collection) {
    if (bundler == null || type == null) {
      throw new NullPointerException();
    }
    this.bundler = bundler;
    this.type = type;
    this.collection = collection;
  }

  private ParcelableCollection(Parcel in) {
    bundler = in.readParcelable(Bundler.class.getClassLoader());
    int size = in.readInt();
    if (size == NULL_SIZE) {
      type = null;
      collection = null;
      return;
    }

    collection = new ArrayList<>();
    type = (BundlerType) in.readParcelable(Bundler.class.getClassLoader());
    if (size > 0) {
      BundlerType valueType = type.typeArguments().get(0);
      for (int i = 0; i < size; i++) {
        @SuppressWarnings("unchecked")
        E value = (E) bundler.readFromParcel(in, valueType);
        collection.add(value);
      }
    }
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(bundler, flags);

    if (collection == null) {
      dest.writeInt(NULL_SIZE);
      return;
    }

    dest.writeInt(collection.size());
    dest.writeParcelable(type, flags);
    if (!collection.isEmpty()) {
      BundlerType valueType = type.typeArguments().get(0);
      for (E value : collection) {
        bundler.writeToParcel(dest, value, valueType, flags);
      }
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @SuppressWarnings("rawtypes")
  public static final Creator<ParcelableCollection> CREATOR =
      new Creator<ParcelableCollection>() {
        @Override
        public ParcelableCollection createFromParcel(Parcel in) {
          return new ParcelableCollection(in);
        }

        @Override
        public ParcelableCollection[] newArray(int size) {
          return new ParcelableCollection[size];
        }
      };
}
