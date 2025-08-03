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

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.BundlerType;

/**
 * Wrapper for reading & writing {@link Drawable} instances from and to {@link Parcel} instances.
 *
 * <p>Note that all {@link Drawable} instances are converted to {@link Bitmap} when parcelling.
 */
public class ParcelableDrawable implements Parcelable {

  private static final int NULL = -1;
  private static final int NOT_NULL = 1;

  private final Drawable drawable;

  /** Create a wrapper for a given drawable. */
  public static ParcelableDrawable of(Bundler bundler, BundlerType type, Drawable drawable) {
    return new ParcelableDrawable(drawable);
  }

  public Drawable get() {
    return drawable;
  }

  private ParcelableDrawable(Drawable drawable) {
    this.drawable = drawable;
  }

  private ParcelableDrawable(Parcel in) {
    int present = in.readInt();

    if (present == NULL) {
      drawable = null;
      return;
    }

    Bitmap bitmap = (Bitmap) in.readParcelable(Bundler.class.getClassLoader());
    drawable = new BitmapDrawable(bitmap);
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    if (drawable == null) {
      dest.writeInt(NULL);
      return;
    }

    dest.writeInt(NOT_NULL);

    Bitmap bitmap = drawableToBitmap(drawable);
    dest.writeParcelable(bitmap, /* flags= */ flags);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @SuppressWarnings("rawtypes")
  public static final Creator<ParcelableDrawable> CREATOR =
      new Creator<ParcelableDrawable>() {
        @Override
        public ParcelableDrawable createFromParcel(Parcel in) {
          return new ParcelableDrawable(in);
        }

        @Override
        public ParcelableDrawable[] newArray(int size) {
          return new ParcelableDrawable[size];
        }
      };

  private static Bitmap drawableToBitmap(Drawable drawable) {
    if (drawable instanceof BitmapDrawable) {
      return ((BitmapDrawable) drawable).getBitmap();
    }

    Bitmap bitmap =
        Bitmap.createBitmap(
            drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Config.ARGB_8888);
    drawable.draw(new Canvas(bitmap));

    return bitmap;
  }
}
