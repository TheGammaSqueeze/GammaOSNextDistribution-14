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
import java.util.HashMap;
import java.util.Map;

/** Wrapper for reading & writing {@link Map} instances from and to {@link Parcel} instances. */
public class ParcelableMap<E, F> implements Parcelable {

  private static final int NULL_SIZE = -1;

  private final Bundler bundler;
  private final BundlerType type;
  private final Map<E, F> map;

  /**
   * Create a wrapper for a given map.
   *
   * <p>The passed in {@link Bundler} must be capable of bundling {@code E} and {@code F}.
   */
  public static <E, F> ParcelableMap<E, F> of(Bundler bundler, BundlerType type, Map<E, F> map) {
    return new ParcelableMap<>(bundler, type, map);
  }

  public Map<E, F> get() {
    return map;
  }

  private ParcelableMap(Bundler bundler, BundlerType type, Map<E, F> map) {
    if (bundler == null || type == null) {
      throw new NullPointerException();
    }
    this.bundler = bundler;
    this.type = type;
    this.map = map;
  }

  private ParcelableMap(Parcel in) {
    bundler = in.readParcelable(Bundler.class.getClassLoader());
    int size = in.readInt();

    if (size == NULL_SIZE) {
      type = null;
      map = null;
      return;
    }

    map = new HashMap<>();
    type = (BundlerType) in.readParcelable(Bundler.class.getClassLoader());
    if (size > 0) {
      BundlerType keyType = type.typeArguments().get(0);
      BundlerType valueType = type.typeArguments().get(1);
      for (int i = 0; i < size; i++) {
        @SuppressWarnings("unchecked")
        E key = (E) bundler.readFromParcel(in, keyType);
        @SuppressWarnings("unchecked")
        F value = (F) bundler.readFromParcel(in, valueType);
        map.put(key, value);
      }
    }
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(bundler, flags);

    if (map == null) {
      dest.writeInt(NULL_SIZE);
      return;
    }

    dest.writeInt(map.size());
    dest.writeParcelable(type, flags);
    if (!map.isEmpty()) {
      BundlerType keyType = type.typeArguments().get(0);
      BundlerType valueType = type.typeArguments().get(1);

      for (Map.Entry<E, F> entry : map.entrySet()) {
        bundler.writeToParcel(dest, entry.getKey(), keyType, flags);
        bundler.writeToParcel(dest, entry.getValue(), valueType, flags);
      }
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @SuppressWarnings("rawtypes")
  public static final Creator<ParcelableMap> CREATOR =
      new Creator<ParcelableMap>() {
        @Override
        public ParcelableMap createFromParcel(Parcel in) {
          return new ParcelableMap(in);
        }

        @Override
        public ParcelableMap[] newArray(int size) {
          return new ParcelableMap[size];
        }
      };
}
