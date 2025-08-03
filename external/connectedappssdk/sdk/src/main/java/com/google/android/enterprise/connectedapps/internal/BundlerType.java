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
package com.google.android.enterprise.connectedapps.internal;

import static java.util.Collections.emptyList;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

// This does not make a copy of the lists in setter/getter as the only caller is generated code

/** Type used internally by the SDK to record the declared types of method calls. */
public final class BundlerType implements Parcelable {
  private final String rawTypeQualifiedName;
  private final List<BundlerType> typeArguments;

  public String rawTypeQualifiedName() {
    return rawTypeQualifiedName;
  }

  public List<BundlerType> typeArguments() {
    return typeArguments;
  }

  private BundlerType(String rawTypeQualifiedName, List<BundlerType> typeArguments) {
    this.rawTypeQualifiedName = rawTypeQualifiedName;
    this.typeArguments = typeArguments;
  }

  private BundlerType(Parcel in) {
    rawTypeQualifiedName = in.readString();
    typeArguments = in.createTypedArrayList(BundlerType.CREATOR);
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(rawTypeQualifiedName);
    dest.writeTypedList(typeArguments);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<BundlerType> CREATOR =
      new Creator<BundlerType>() {
        @Override
        public BundlerType createFromParcel(Parcel in) {
          return new BundlerType(in);
        }

        @Override
        public BundlerType[] newArray(int size) {
          return new BundlerType[size];
        }
      };

  public static BundlerType of(String rawTypeQualifiedName, BundlerType... typeArguments) {
    return new BundlerType(rawTypeQualifiedName, Arrays.asList(typeArguments));
  }

  public static BundlerType of(String rawTypeQualifiedName) {
    return new BundlerType(rawTypeQualifiedName, emptyList());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BundlerType that = (BundlerType) o;
    return rawTypeQualifiedName.equals(that.rawTypeQualifiedName)
        && typeArguments.equals(that.typeArguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rawTypeQualifiedName, typeArguments);
  }
}
