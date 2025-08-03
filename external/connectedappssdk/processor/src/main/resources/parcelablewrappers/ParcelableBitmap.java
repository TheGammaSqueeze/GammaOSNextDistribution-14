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
import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.enterprise.connectedapps.internal.Bundler;
import com.google.android.enterprise.connectedapps.internal.BundlerType;

/** Wrapper for reading & writing {@link Bitmap} instances from and to {@link Parcel} instances. */
// Though Bitmap is itself Parcelable, in some circumstances the Parcelling process can fail (see
// b/159895007).
public class ParcelableBitmap implements Parcelable {
  private final Bitmap bitmap;

  /** Create a wrapper for a given bitmap. */
  public static ParcelableBitmap of(Bundler bundler, BundlerType type, Bitmap bitmap) {
    return new ParcelableBitmap(bitmap);
  }

  private ParcelableBitmap(Bitmap bitmap) {
    this.bitmap = bitmap;
  }

  private ParcelableBitmap(Parcel in) {
    String configKey = in.readString();

    if (configKey == null) {
      bitmap = null;
      return;
    }

    Bitmap.Config config = Bitmap.Config.valueOf(configKey);
    int width = in.readInt();
    int height = in.readInt();
    int[] colors = new int[width * height];
    in.readIntArray(colors);

    bitmap = Bitmap.createBitmap(colors, width, height, config);
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    if (bitmap == null) {
      out.writeString(null);
      return;
    }

    Bitmap.Config config = bitmap.getConfig();
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    int[] colors = bitmapToPixelArray(bitmap);

    out.writeString(config.toString());
    out.writeInt(width);
    out.writeInt(height);
    out.writeIntArray(colors);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public Bitmap get() {
    return bitmap;
  }

  public static final Creator<ParcelableBitmap> CREATOR =
      new Creator<ParcelableBitmap>() {
        @Override
        public ParcelableBitmap createFromParcel(Parcel in) {
          return new ParcelableBitmap(in);
        }

        @Override
        public ParcelableBitmap[] newArray(int size) {
          return new ParcelableBitmap[size];
        }
      };

  private static int[] bitmapToPixelArray(Bitmap bitmap) {
    int[] pixels = new int[bitmap.getHeight() * bitmap.getWidth()];
    bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
    return pixels;
  }
}
