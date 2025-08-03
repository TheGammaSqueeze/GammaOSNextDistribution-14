/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.security.cts;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArrayMap;

import androidx.annotation.NonNull;

import org.junit.Assert;

public class BitmapWrapper implements Parcelable {
    enum Field {
        DataSize,
        Height,
        RowBytes,
    }

    private final Bitmap mBitmap;
    private final ArrayMap<Field, Integer> mReplaceFields = new ArrayMap<>();

    public BitmapWrapper(Bitmap bitmap) {
        mBitmap = bitmap;
    }

    private BitmapWrapper(Parcel in) {
        mBitmap = Bitmap.CREATOR.createFromParcel(in);
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public BitmapWrapper reset() {
        mReplaceFields.clear();
        return this;
    }

    public BitmapWrapper replace(Field field, int newValue) {
        mReplaceFields.put(field, newValue);
        return this;
    }

    @Override
    public int describeContents() {
        return mBitmap.describeContents();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        final int before = dest.dataPosition();
        mBitmap.writeToParcel(dest, flags);
        final int oldEnd = dest.dataPosition();
        if (!mReplaceFields.isEmpty()) {
            dest.setDataPosition(before
                    + 4 /* immutable */
                    + 4 /* colortype */
                    + 4 /* alpha type */);
            // Skip sizeof colorspace
            int colorSpaceLen = dest.readInt();
            dest.setDataPosition(dest.dataPosition() + colorSpaceLen);
            Assert.assertEquals(mBitmap.getWidth(), dest.readInt());
            Assert.assertEquals(mBitmap.getHeight(), dest.readInt());
            if (mReplaceFields.containsKey(Field.Height)) {
                dest.setDataPosition(dest.dataPosition() - 4);
                dest.writeInt(mReplaceFields.get(Field.Height));
            }
            Assert.assertEquals(mBitmap.getRowBytes(), dest.readInt());
            if (mReplaceFields.containsKey(Field.RowBytes)) {
                dest.setDataPosition(dest.dataPosition() - 4);
                dest.writeInt(mReplaceFields.get(Field.RowBytes));
            }
            Assert.assertEquals(mBitmap.getDensity(), dest.readInt());
            int type = dest.readInt();
            if (type == 0) { // in-place
                if (mReplaceFields.containsKey(Field.DataSize)) {
                    int dataSize = mReplaceFields.get(Field.DataSize);
                    dest.writeInt(dataSize);
                    int newEnd = dest.dataPosition() + dataSize;
                    dest.setDataSize(newEnd);
                    dest.setDataPosition(newEnd);
                } else {
                    int skip = dest.readInt();
                    dest.setDataPosition(dest.dataPosition() + skip);
                }
            } else if (type == 1) { // ashmem
                if (mReplaceFields.containsKey(Field.DataSize)) {
                    int dataSize = mReplaceFields.get(Field.DataSize);
                    dest.writeInt(dataSize);
                }
                dest.setDataPosition(oldEnd);
            } else {
                Assert.fail("Unknown type " + type);
            }
        }
    }

    public static final Parcelable.Creator<BitmapWrapper> CREATOR =
            new Parcelable.Creator<BitmapWrapper>() {
        public BitmapWrapper createFromParcel(Parcel in) {
            return new BitmapWrapper(in);
        }

        public BitmapWrapper[] newArray(int size) {
            return new BitmapWrapper[size];
        }
    };

}
