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
import com.google.common.base.Optional;

/**
 * Wrapper for reading & writing {@link Optional} instances from and to {@link Parcel} instances.
 */
public class ParcelableGuavaOptional<E> implements Parcelable {

  private static final int NULL = -1;
  private static final int ABSENT = 0;
  private static final int PRESENT = 1;

  private final Bundler bundler;
  private final BundlerType type;
  private final Optional<E> optional;

  /**
   * Create a wrapper for a given optional.
   *
   * <p>The passed in {@link Bundler} must be capable of bundling {@code F}.
   */
  public static <F> ParcelableGuavaOptional<F> of(
      Bundler bundler, BundlerType type, Optional<F> optional) {
    return new ParcelableGuavaOptional<>(bundler, type, optional);
  }

  public Optional<E> get() {
    return optional;
  }

  private ParcelableGuavaOptional(Bundler bundler, BundlerType type, Optional<E> optional) {
    if (bundler == null || type == null) {
      throw new NullPointerException();
    }
    this.bundler = bundler;
    this.type = type;
    this.optional = optional;
  }

  private ParcelableGuavaOptional(Parcel in) {
    bundler = in.readParcelable(Bundler.class.getClassLoader());

    int presentValue = in.readInt();

    if (presentValue == NULL) {
      type = null;
      optional = null;
      return;
    }

    boolean isPresent = presentValue == PRESENT;
    type = (BundlerType) in.readParcelable(Bundler.class.getClassLoader());
    if (isPresent) {
      BundlerType valueType = type.typeArguments().get(0);

      @SuppressWarnings("unchecked")
      E value = (E) bundler.readFromParcel(in, valueType);

      optional = Optional.of(value);
    } else {
      optional = Optional.absent();
    }
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(bundler, flags);

    if (optional == null) {
      dest.writeInt(NULL);
      return;
    }

    dest.writeInt(optional.isPresent() ? PRESENT : ABSENT);
    dest.writeParcelable(type, flags);
    if (optional.isPresent()) {
      BundlerType valueType = type.typeArguments().get(0);
      bundler.writeToParcel(dest, optional.get(), valueType, flags);
    }
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @SuppressWarnings("rawtypes")
  public static final Creator<ParcelableGuavaOptional> CREATOR =
      new Creator<ParcelableGuavaOptional>() {
        @Override
        public ParcelableGuavaOptional createFromParcel(Parcel in) {
          return new ParcelableGuavaOptional(in);
        }

        @Override
        public ParcelableGuavaOptional[] newArray(int size) {
          return new ParcelableGuavaOptional[size];
        }
      };
}
