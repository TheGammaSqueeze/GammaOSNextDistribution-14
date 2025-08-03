/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.queryable.util;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/** A wrapper around a {@link Parcelable} which makes it {@link Serializable}. */
public class SerializableParcelWrapper<E extends Parcelable> implements Serializable, Parcelable {

    private static final long serialVersionUID = 0;

    private E mParcelable;

    public SerializableParcelWrapper(E parcelable)  {
        mParcelable = parcelable;
    }

    private SerializableParcelWrapper(Parcel in) {
        mParcelable = in.readParcelable(SerializableParcelWrapper.class.getClassLoader());
    }

    public E get() {
        return mParcelable;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SerializableParcelWrapper)) {
            return false;
        }
        SerializableParcelWrapper<E> other = (SerializableParcelWrapper<E>) obj;

        if (mParcelable == null) {
            return other.mParcelable == null;
        }

        return mParcelable.equals(other.mParcelable);
    }

    @Override
    public int hashCode() {
        return mParcelable.hashCode();
    }

    // Serializable readObject
    private void readObject(ObjectInputStream inputStream) throws ClassNotFoundException,
            IOException {
        int size = inputStream.readInt();
        byte[] bytes = new byte[size];
        inputStream.read(bytes);
        Parcel p = Parcel.obtain();
        p.unmarshall(bytes, 0, size);
        p.setDataPosition(0);
        mParcelable = p.readParcelable(Parcelable.class.getClassLoader());
        p.recycle();
    }

    // Serializable writeObject
    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        Parcel p = Parcel.obtain();
        p.writeParcelable(mParcelable, /* flags= */ 0);
        byte[] bytes = p.marshall();
        p.recycle();

        outputStream.writeInt(bytes.length);
        outputStream.write(bytes);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(mParcelable, flags);
    }

    public static final Parcelable.Creator<SerializableParcelWrapper> CREATOR =
            new Parcelable.Creator<SerializableParcelWrapper>() {
                public SerializableParcelWrapper createFromParcel(Parcel in) {
                    return new SerializableParcelWrapper(in);
                }

                public SerializableParcelWrapper[] newArray(int size) {
                    return new SerializableParcelWrapper[size];
                }
    };
}
