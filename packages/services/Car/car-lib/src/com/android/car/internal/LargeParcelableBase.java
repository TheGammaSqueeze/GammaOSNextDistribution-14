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

import static android.system.OsConstants.PROT_READ;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SharedMemory;
import android.system.ErrnoException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * Base class to allow passing {@code Parcelable} over binder directly or through shared memory if
 * payload size is too big.
 *
 * <p>Child class should inherit this to use this or use {@link LargeParcelable} class.
 *
 * <p>Parcelized data will have following elements
 * <ul>
 * <li>@Nullable Parcelable
 * <li>@Nullable SharedMemory which include serialized Parcelable if non-null. This will be set
 * only when the previous Parcelable is null or this also can be null for no data case.
 * </ul>
 *
 * @hide
 */
public abstract class LargeParcelableBase implements Parcelable, Closeable {
    /** Payload size bigger than this value will be passed over shared memory. */
    public static final int MAX_DIRECT_PAYLOAD_SIZE = 4096;
    private static final String TAG = LargeParcelable.class.getSimpleName();

    private static final boolean DBG_PAYLOAD = Log.isLoggable(TAG, Log.DEBUG);
    private static final int DBG_DUMP_LENGTH = 16;

    private static final int NULL_PAYLOAD = 0;
    private static final int NONNULL_PAYLOAD = 1;
    private static final int FD_HEADER = 0;

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private @Nullable SharedMemory mSharedMemory;

    /**
     * Serialize (=write Parcelable into given Parcel) a {@code Parcelable} child class wants to
     * pass over binder call.
     */
    protected abstract void serialize(@NonNull Parcel dest, int flags);

    /**
     * Serialize null payload to the given {@code Parcel}. For {@code Parcelable}, this can be
     * simply {@code dest.writeParcelable(null)} but non-Parcelable should have other way to
     * mark that there is no payload.
     */
    protected abstract void serializeNullPayload(@NonNull Parcel dest);

    /**
     * Read a {@code Parcelable} from the given {@code Parcel}.
     */
    protected abstract void deserialize(@NonNull Parcel src);

    public LargeParcelableBase() {
    }

    public LargeParcelableBase(Parcel in) {
        // Make this compatible with stable AIDL
        // payload size + Parcelable / payload + 1:has shared memory + 0 + file
        //                                       0:no shared memory
        // 0 + file makes it compatible with ParcelFileDescrpitor
        // file contains:
        // file size + Parcelable / payload + 0
        int startPosition = in.dataPosition();
        int totalPayloadSize = in.readInt();
        deserialize(in);
        int sharedMemoryPosition = in.dataPosition();
        boolean hasSharedMemory = (in.readInt() != NULL_PAYLOAD);
        if (hasSharedMemory) {
            int fdHeader = in.readInt();
            if (fdHeader != FD_HEADER) {
                throw new IllegalArgumentException(
                        "Invalid data, wrong fdHeader, expected 0 while got " + fdHeader);
            }
            SharedMemory memory = SharedMemory.CREATOR.createFromParcel(in);
            deserializeSharedMemoryAndClose(memory);
        }
        in.setDataPosition(startPosition + totalPayloadSize);
        if (DBG_PAYLOAD) {
            Log.d(TAG, "Read, start:" + startPosition + " totalPayloadSize:" + totalPayloadSize
                    + " sharedMemoryPosition:" + sharedMemoryPosition
                    + " hasSharedMemory:" + hasSharedMemory + " dataAvail:" + in.dataAvail());
        }
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        int startPosition = dest.dataPosition();
        SharedMemory sharedMemory;
        synchronized (mLock) {
            sharedMemory = mSharedMemory;
        }
        int totalPayloadSize = 0;
        if (sharedMemory != null) {
            // optimized path for resending the same Parcelable multiple times with already
            // created shared memory
            totalPayloadSize = serializeMemoryFdOrPayloadToParcel(dest, flags, sharedMemory);
            if (DBG_PAYLOAD) {
                Log.d(TAG, "Write, reusing shared memory, start:" + startPosition
                        + " totalPayloadSize:" + totalPayloadSize);
            }
            return;
        }

        // dataParcel is the parcel that would be serialized to the shared memory file.
        Parcel dataParcel = Parcel.obtain();
        totalPayloadSize = serializeMemoryFdOrPayloadToParcel(dataParcel, flags, null);

        boolean noSharedMemory = totalPayloadSize <= MAX_DIRECT_PAYLOAD_SIZE;
        boolean hasNonNullPayload = true;
        if (noSharedMemory) {
            if (DBG_PAYLOAD) {
                Log.d(TAG, "not using shared memory");
            }
            dest.appendFrom(dataParcel, 0, totalPayloadSize);
            dataParcel.recycle();
        } else {
            if (DBG_PAYLOAD) {
                Log.d(TAG, "using shared memory");
            }
            sharedMemory = serializeParcelToSharedMemory(dataParcel);
            dataParcel.recycle();
            synchronized (mLock) {
                // If it is already set, let sharedMemory go and GV will close it later.
                // This is ok as this kind of race should not happen often.
                if (mSharedMemory != null) {
                    mSharedMemory = sharedMemory;
                }
            }

            totalPayloadSize = serializeMemoryFdOrPayloadToParcel(dest, flags, sharedMemory);
        }
        if (DBG_PAYLOAD) {
            Log.d(TAG, "Write, start:" + startPosition + " totalPayloadSize:" + totalPayloadSize
                    + " hasNonNullPayload:" + hasNonNullPayload
                    + " hasSharedMemory:" + !noSharedMemory + " dataSize:" + dest.dataSize());
        }
    }

    private int updatePayloadSize(Parcel dest, int startPosition) {
        int lastPosition = dest.dataPosition();
        int totalPayloadSize = lastPosition - startPosition;
        dest.setDataPosition(startPosition);
        dest.writeInt(totalPayloadSize);
        dest.setDataPosition(lastPosition);
        dest.setDataSize(lastPosition);
        return totalPayloadSize;
    }

    // Write shared memory in compatible way with ParcelFileDescriptor
    private void writeSharedMemoryCompatibleToParcel(Parcel dest, SharedMemory memory, int flags) {
        // dest.writeParcelable() adds class type which makes it incompatible with C++.
        if (memory == null) {
            dest.writeInt(NULL_PAYLOAD);
            return;
        }
        // non-null case
        dest.writeInt(NONNULL_PAYLOAD);
        dest.writeInt(FD_HEADER); // additional header for ParcelFileDescriptor
        memory.writeToParcel(dest, flags);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public int describeContents() {
        return 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Close the underlying shared memory for this. This can be called multiple times safely.
     * When this is not called explicitly, it will be closed when this instance is GCed.
     * Calling this can be useful when many instances are created frequently.
     *
     * <p>If underlying payload is changed, the client should call this before sending it over
     * binder as sending it over binder can keep shared memory generated from the previous binder
     * call.
     */
    @Override
    public void close() {
        SharedMemory sharedMemory = null;
        synchronized (mLock) {
            sharedMemory = mSharedMemory;
            mSharedMemory = null;
        }
        if (sharedMemory != null) {
            sharedMemory.close();
        }
    }

    protected static SharedMemory serializeParcelToSharedMemory(Parcel p) {
        SharedMemory memory = null;
        ByteBuffer buffer = null;
        int size = p.dataSize();
        try {
            memory = SharedMemory.create(LargeParcelableBase.class.getSimpleName(), size);
            buffer = memory.mapReadWrite();
            byte[] data = p.marshall();
            buffer.put(data, 0, size);
            if (DBG_PAYLOAD) {
                int dumpSize = Math.min(DBG_DUMP_LENGTH, data.length);
                StringBuilder bd = new StringBuilder();
                bd.append("marshalled:");
                for (int i = 0; i < dumpSize; i++) {
                    bd.append(data[i]);
                    if (i != dumpSize - 1) {
                        bd.append(',');
                    }
                }
                bd.append("=memory:");
                for (int i = 0; i < dumpSize; i++) {
                    bd.append(buffer.get(i));
                    if (i != dumpSize - 1) {
                        bd.append(',');
                    }
                }
                Log.d(TAG, bd.toString());
            }
            if (!memory.setProtect(PROT_READ)) {
                memory.close();
                throw new SecurityException("Failed to set read-only protection on shared memory");
            }
        } catch (ErrnoException e) {
            throw new IllegalArgumentException("Failed to use shared memory", e);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to serialize", e);
        } finally {
            if (buffer != null) {
                SharedMemory.unmap(buffer);
            }
        }

        return memory;
    }

    protected static Parcel copyFromSharedMemory(SharedMemory memory) {
        ByteBuffer buffer = null;
        Parcel in = Parcel.obtain();
        try {
            buffer = memory.mapReadOnly();
            // TODO(b/188781089) find way to avoid this additional copy
            byte[] payload = new byte[buffer.limit()];
            buffer.get(payload);
            in.unmarshall(payload, 0, payload.length);
            in.setDataPosition(0);
            if (DBG_PAYLOAD) {
                int dumpSize = Math.min(DBG_DUMP_LENGTH, payload.length);
                StringBuilder bd = new StringBuilder();
                bd.append("unmarshalled:");
                int parcelStartPosition = in.dataPosition();
                byte[] fromParcel = in.marshall();
                for (int i = 0; i < dumpSize; i++) {
                    bd.append(fromParcel[i]);
                    if (i != dumpSize - 1) bd.append(',');
                }
                bd.append("=startPosition:");
                bd.append(parcelStartPosition);
                bd.append("=memory:");
                for (int i = 0; i < dumpSize; i++) {
                    bd.append(buffer.get(i));
                    if (i != dumpSize - 1) bd.append(',');
                }
                bd.append("=interim_payload:");
                for (int i = 0; i < dumpSize; i++) {
                    bd.append(payload[i]);
                    if (i != dumpSize - 1) bd.append(',');
                }
                Log.d(TAG, bd.toString());
                in.setDataPosition(parcelStartPosition);
            }
        } catch (ErrnoException e) {
            throw new IllegalArgumentException("cannot create Parcelable from SharedMemory", e);
        } catch (Exception e) {
            throw new IllegalArgumentException("failed to deserialize", e);
        } finally {
            if (buffer != null) {
                SharedMemory.unmap(buffer);
            }
        }
        return in;
    }

    private void deserializeSharedMemoryAndClose(SharedMemory memory) {
        // The shared memory file contains a serialized largeParcelable.
        // size + payload + 0 (no shared memory).
        Parcel in = null;
        try {
            in = copyFromSharedMemory(memory);
            // Even if we don't need the file size, we have to read it from the parcel to advance
            // the data position.
            int fileSize = in.readInt();
            if (DBG_PAYLOAD) {
                Log.d(TAG, "file size in shared memory file: " + fileSize);
            }
            deserialize(in);
            // There is an additional 0 in the parcel, but we ignore that.
        } finally {
            memory.close();
            if (in != null) {
                in.recycle();
            }
        }
    }

    // If sharedMemory is not null, serialize null payload and shared memory to parcel.
    // Otherwise, serialize the actual payload to parcel.
    private int serializeMemoryFdOrPayloadToParcel(
            Parcel dest, int flags, @Nullable SharedMemory sharedMemory) {
        int startPosition = dest.dataPosition();
        dest.writeInt(0); // payload size

        if (sharedMemory != null) {
            serializeNullPayload(dest);
            writeSharedMemoryCompatibleToParcel(dest, sharedMemory, flags);
        } else {
            serialize(dest, flags);
            writeSharedMemoryCompatibleToParcel(dest, null, flags);
        }

        return updatePayloadSize(dest, startPosition);
    }
}
