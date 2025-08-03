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
import com.google.android.enterprise.connectedapps.testapp.CustomWrapper;
import com.google.android.enterprise.connectedapps.testapp.CustomWrapper2;

@CustomParcelableWrapper(originalType = CustomWrapper2.class)
public class ParcelableCustomWrapper2<E> implements Parcelable {

  private static final int NULL = -1;
  private static final int NOT_NULL = 1;

  private final Bundler bundler;
  private final BundlerType type;
  private final CustomWrapper2<E> customWrapper;

  /**
   * Create a wrapper for a given {@link CustomWrapper}.
   *
   * <p>The passed in {@link Bundler} must be capable of bundling {@code F}.
   */
  public static <F> ParcelableCustomWrapper2<F> of(
      Bundler bundler, BundlerType type, CustomWrapper2<F> customWrapper) {
    return new ParcelableCustomWrapper2<>(bundler, type, customWrapper);
  }

  public CustomWrapper2<E> get() {
    return customWrapper;
  }

  private ParcelableCustomWrapper2(
      Bundler bundler, BundlerType type, CustomWrapper2<E> customWrapper) {
    if (bundler == null || type == null) {
      throw new NullPointerException();
    }
    this.bundler = bundler;
    this.type = type;
    this.customWrapper = customWrapper;
  }

  private ParcelableCustomWrapper2(Parcel in) {
    bundler = in.readParcelable(Bundler.class.getClassLoader());

    int presentValue = in.readInt();

    if (presentValue == NULL) {
      type = null;
      customWrapper = null;
      return;
    }

    type = (BundlerType) in.readParcelable(Bundler.class.getClassLoader());
    BundlerType valueType = type.typeArguments().get(0);

    @SuppressWarnings("unchecked")
    E value = (E) bundler.readFromParcel(in, valueType);

    customWrapper = new CustomWrapper2<>(value);
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(bundler, flags);

    if (customWrapper == null) {
      dest.writeInt(NULL);
      return;
    }

    dest.writeInt(NOT_NULL);
    dest.writeParcelable(type, flags);
    BundlerType valueType = type.typeArguments().get(0);
    bundler.writeToParcel(dest, customWrapper.value(), valueType, flags);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @SuppressWarnings("rawtypes")
  public static final Creator<ParcelableCustomWrapper2> CREATOR =
      new Creator<ParcelableCustomWrapper2>() {
        @Override
        public ParcelableCustomWrapper2 createFromParcel(Parcel in) {
          return new ParcelableCustomWrapper2(in);
        }

        @Override
        public ParcelableCustomWrapper2[] newArray(int size) {
          return new ParcelableCustomWrapper2[size];
        }
      };
}
