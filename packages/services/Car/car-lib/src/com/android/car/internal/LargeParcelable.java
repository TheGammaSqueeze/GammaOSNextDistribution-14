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

package com.android.car.internal;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.os.SharedMemoryHelper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.SharedMemory;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Utility to pass any {@code Parcelable} through binder with automatic conversion into shared
 * memory when data size is too big.
 *
 * <p> This class can work by itself but child class can be useful to use a custom class to
 * interface with C++ world. For such usage, child class will only add its own {@code CREATOR} impl
 * and a constructor taking {@code Parcel in}.
 * <p>For stable AIDL, this class also provides two methods for serialization {@link
 * #toLargeParcelable(Parcelable)} and deserialization
 * {@link #reconstructStableAIDLParcelable(Parcelable, boolean)}. Plz check included test for the
 * usage.
 *
 * @hide
 */
public class LargeParcelable extends LargeParcelableBase {
    /**
     * Stable AIDL Parcelable should have this member with {@code ParcelFileDescriptor} to support
     * bigger payload passing over shared memory.
     */
    public static final String STABLE_AIDL_SHARED_MEMORY_MEMBER = "sharedMemoryFd";
    /**
     * Stable AIDL Parcelable has {@code readFromParcel(Parcel)} public method.
     */
    public static final String STABLE_AIDL_PARCELABLE_READ_FROM_PARCEL = "readFromParcel";

    private static final String TAG = LargeParcelable.class.getSimpleName();
    private static final boolean DBG_PAYLOAD = false;
    private static final boolean DBG_STABLE_AIDL_CLASS = false;

    // cannot set this final even if it is set only once in constructor as set is done in
    // deserialize call.
    private @Nullable Parcelable mParcelable;

    // This is shared across thread. As this is per class, use volatile to avoid adding
    // separate lock. If additional static volatile is added, a lock should be added.
    private static volatile WeakReference<ClassLoader> sClassLoader = null;

    /**
     * Sets {@code ClassLoader} for loading the {@Code Parcelable}. This should be done before
     * getting binder call. Default classloader may not recognize the Parcelable passed and relevant
     * classloader like package classloader should be set before getting any binder data.
     */
    public static void setClassLoader(ClassLoader loader) {
        sClassLoader = new WeakReference<>(loader);
    }

    public LargeParcelable(Parcel in) {
        super(in);
    }

    public LargeParcelable(Parcelable parcelable) {
        mParcelable = parcelable;
    }

    /**
     * Returns {@code Parcelable} carried by this instance.
     */
    public Parcelable getParcelable() {
        return mParcelable;
    }

    @Override
    protected void serialize(@NonNull Parcel dest, int flags) {
        int startPosition;
        if (DBG_PAYLOAD) {
            startPosition = dest.dataPosition();
        }
        dest.writeParcelable(mParcelable, flags);
        if (DBG_PAYLOAD) {
            Log.d(TAG, "serialize-payload, start:" + startPosition
                    + " size:" + (dest.dataPosition() - startPosition));
        }
    }

    @Override
    protected void serializeNullPayload(@NonNull Parcel dest) {
        int startPosition;
        if (DBG_PAYLOAD) {
            startPosition = dest.dataPosition();
        }
        dest.writeParcelable(null, 0);
        if (DBG_PAYLOAD) {
            Log.d(TAG, "serializeNullPayload-payload, start:" + startPosition
                    + " size:" + (dest.dataPosition() - startPosition));
        }
    }

    @Override
    protected void deserialize(@NonNull Parcel src) {
        // default class loader does not work as it may not be in boot class path.
        ClassLoader loader = (sClassLoader == null) ? null : sClassLoader.get();
        int startPosition;
        if (DBG_PAYLOAD) {
            startPosition = src.dataPosition();
        }
        mParcelable = src.readParcelable(loader);
        if (DBG_PAYLOAD) {
            Log.d(TAG, "deserialize-payload, start:" + startPosition
                    + " size:" + (src.dataPosition() - startPosition)
                    + " mParcelable:" + mParcelable);
        }
    }

    public static final @NonNull Parcelable.Creator<LargeParcelable> CREATOR =
            new Parcelable.Creator<LargeParcelable>() {
                @Override
                public LargeParcelable[] newArray(int size) {
                    return new LargeParcelable[size];
                }

                @Override
                public LargeParcelable createFromParcel(@NonNull Parcel in) {
                    return new LargeParcelable(in);
                }
            };

    /**
     * @see #toLargeParcelable(Parcelable, Callable<Parcelable>)
     */
    @Nullable
    public static Parcelable toLargeParcelable(@Nullable Parcelable p) {
        return toLargeParcelable(p, null);
    }

    /**
     * Prepare a {@code Parcelable} defined from Stable AIDL to be able to sent through binder.
     *
     * <p>The {@code Parcelable} should have a public member having name of
     * {@link #STABLE_AIDL_SHARED_MEMORY_MEMBER} with {@code ParcelFileDescriptor} type.
     *
     * <p>If payload size is big, the input would be serialized to a shared memory file and a
     * an empty {@code Parcelable} with only the file descriptor set would be returned. If the
     * payload size is small enough to be sent across binder or the input already contains a shared
     * memory file, the original input would be returned.
     *
     * @param p {@code Parcelable} the input to convert that might contain large data.
     * @param constructEmptyParcelable a callable to create an empty Parcelable with the same type
     *      as input. If this is null, the default initializer for the input type would be used.
     * @return a {@code Parcelable} that could be sent through binder despite memory limitation.
     */
    @Nullable
    public static Parcelable toLargeParcelable(
            @Nullable Parcelable p, @Nullable Callable<Parcelable> constructEmptyParcelable) {
        if (p == null) {
            return null;
        }
        Class parcelableClass = p.getClass();
        if (DBG_STABLE_AIDL_CLASS) {
            Log.d(TAG, "toLargeParcelable stable AIDL Parcelable:"
                    + parcelableClass.getSimpleName());
        }
        Field field;
        ParcelFileDescriptor sharedMemoryFd;
        try {
            field = parcelableClass.getField(STABLE_AIDL_SHARED_MEMORY_MEMBER);
            sharedMemoryFd = (ParcelFileDescriptor) field.get(p);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot access " + STABLE_AIDL_SHARED_MEMORY_MEMBER,
                    e);
        }
        if (sharedMemoryFd != null) {
            return p;
        }
        Parcel dataParcel = Parcel.obtain();
        p.writeToParcel(dataParcel, 0);
        int payloadSize = dataParcel.dataSize();
        if (payloadSize <= LargeParcelableBase.MAX_DIRECT_PAYLOAD_SIZE) {
            // direct path, no re-write to shared memory.
            if (DBG_PAYLOAD) {
                Log.d(TAG, "toLargeParcelable send directly, payload size:" + payloadSize);
            }
            return p;
        }
        SharedMemory memory = LargeParcelableBase.serializeParcelToSharedMemory(dataParcel);
        dataParcel.recycle();

        try {
            sharedMemoryFd = SharedMemoryHelper.createParcelFileDescriptor(memory);
        } catch (IOException e) {
            throw new IllegalArgumentException("unable to duplicate shared memory fd", e);
        }

        Parcelable emptyPayload;
        if (constructEmptyParcelable != null) {
            try {
                emptyPayload = constructEmptyParcelable.call();
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot use Parcelable constructor", e);
            }
        } else {
            try {
                emptyPayload = (Parcelable) parcelableClass.newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot access Parcelable constructor", e);
            }
        }

        try {
            field.set(emptyPayload, sharedMemoryFd);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot access Parcelable member for FD", e);
        }

        return emptyPayload;
    }

    /**
     * Reconstructs {@code Parcelable} defined from Stable AIDL. It should have a {@link
     * ParcelFileDescriptor} member named {@link #STABLE_AIDL_SHARED_MEMORY_MEMBER} and will create
     * a new {@code Parcelable} if the shared memory portion is not null. If there is no shared
     * memory, it will return the original {@code Parcelable p} as it is.
     *
     * @param p                Original {@code Parcelable} containing the payload.
     * @param keepSharedMemory Whether to keep created shared memory in the returned {@code
     *                         Parcelable}. Set to {@code true} if this {@code Parcelable} is sent
     *                         across binder repeatedly.
     * @return a new {@code Parcelable} if payload read from shared memory or old one if payload
     * is small enough.
     */
    public static @Nullable Parcelable reconstructStableAIDLParcelable(@Nullable Parcelable p,
            boolean keepSharedMemory) {
        if (p == null) {
            return null;
        }
        Class parcelableClass = p.getClass();
        if (DBG_STABLE_AIDL_CLASS) {
            Log.d(TAG, "reconstructStableAIDLParcelable stable AIDL Parcelable:"
                    + parcelableClass.getSimpleName());
        }
        ParcelFileDescriptor sharedMemoryFd = null;
        Field fieldSharedMemory;
        try {
            fieldSharedMemory = parcelableClass.getField(STABLE_AIDL_SHARED_MEMORY_MEMBER);
            sharedMemoryFd = (ParcelFileDescriptor) fieldSharedMemory.get(p);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot access " + STABLE_AIDL_SHARED_MEMORY_MEMBER,
                    e);
        }
        if (sharedMemoryFd == null) {
            if (DBG_PAYLOAD) {
                Log.d(TAG, "reconstructStableAIDLParcelable null shared memory");
            }
            return p;
        }
        Parcel in = null;
        Parcelable retParcelable;
        try {
            // SharedMemory.fromFileDescriptor take ownership, so we need to dupe to keep
            // sharedMemoryFd the Parcelable valid.
            SharedMemory memory = SharedMemory.fromFileDescriptor(sharedMemoryFd.dup());
            in = LargeParcelableBase.copyFromSharedMemory(memory);
            retParcelable = (Parcelable) parcelableClass.newInstance();
            // runs retParcelable.readFromParcel(in)
            Method readMethod = parcelableClass.getMethod(STABLE_AIDL_PARCELABLE_READ_FROM_PARCEL,
                    new Class[]{Parcel.class});
            readMethod.invoke(retParcelable, in);
            if (keepSharedMemory) {
                fieldSharedMemory.set(retParcelable, sharedMemoryFd);
            }
            if (DBG_PAYLOAD) {
                Log.d(TAG, "reconstructStableAIDLParcelable read shared memory, data size:"
                        + in.dataPosition());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot access Parcelable constructor/method", e);
        } finally {
            if (in != null) {
                in.recycle();
            }
        }
        return retParcelable;
    }
}
