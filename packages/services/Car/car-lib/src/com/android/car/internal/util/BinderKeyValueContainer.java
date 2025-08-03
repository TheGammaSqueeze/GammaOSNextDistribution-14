/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.car.internal.util;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * A map-like container to hold client's binder interface. The item in the container will be removed
 * automatically once the associated binder is unlinked (dies).
 *
 * @param <K> type of the key of the item
 * @param <V> type wrapped in the value of the item
 *
 * @hide
 */
public final class BinderKeyValueContainer<K, V extends IInterface> {

    private static final String TAG = BinderKeyValueContainer.class.getSimpleName();

    // BinderInterfaceHolder#binderDied() is called on binder thread, and it might change this
    // container, so guard this container with a lock to avoid racing condition between binder
    // thread and the calling thread of this container.
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final ArrayMap<K, BinderInterfaceHolder<K, V>> mBinderMap;

    @Nullable
    private BinderDeathCallback<K> mBinderDeathCallback;

    /**
     * Wrapper class for objects that want to be notified whenever they are unlinked from
     * the container ({@link BinderKeyValueContainer}).
     *
     * @param <K> type of the key of the item
     * @param <V> type of the value wrapped by this class
     */
    private static final class BinderInterfaceHolder<K, V extends IInterface> implements
            IBinder.DeathRecipient {

        private final V mBinderInterface;
        private final IBinder mBinder;
        private final BinderKeyValueContainer<K, V> mMap;

        private BinderInterfaceHolder(BinderKeyValueContainer<K, V> map, V binderInterface,
                IBinder binder) {
            mMap = map;
            this.mBinderInterface = binderInterface;
            this.mBinder = binder;
        }

        @Override
        public void binderDied() {
            mBinder.unlinkToDeath(this, 0);
            mMap.removeByBinderInterfaceHolder(this);
        }
    }

    /**
     * Interface to be implemented by object that wants to be notified whenever a binder is unlinked
     * (dies).
     *
     * @param <K> type of the key of the container
     */
    public interface BinderDeathCallback<K> {
        /** Callback to be invoked after a binder is unlinked and removed from the container. */
        void onBinderDied(K deadKey);
    }

    public BinderKeyValueContainer() {
        mBinderMap = new ArrayMap<>();
    }

    /**
     * Returns the {@link IInterface} object associated with the {@code key}, or {@code null} if
     * there is no such key.
     */
    @Nullable
    public V get(K key) {
        Objects.requireNonNull(key);
        synchronized (mLock) {
            BinderInterfaceHolder<K, V> holder = mBinderMap.get(key);
            return holder == null ? null : holder.mBinderInterface;
        }
    }

    /**
     * Adds the instance of {@link IInterface} representing the binder interface to this container.
     * <p>
     * Updates the value if the {@code key} exists already.
     * <p>
     * Internally, this {@code binderInterface} will be wrapped in a {@link BinderInterfaceHolder}
     * when added.
     */
    public void put(K key, V binderInterface) {
        IBinder binder = binderInterface.asBinder();
        BinderInterfaceHolder<K, V> holder =
                new BinderInterfaceHolder<>(this, binderInterface, binder);
        BinderInterfaceHolder<K, V> oldHolder;
        try {
            binder.linkToDeath(holder, 0);
        } catch (RemoteException e) {
            throw new IllegalArgumentException(e);
        }
        synchronized (mLock) {
            oldHolder = mBinderMap.put(key, holder);
        }
        if (oldHolder != null) {
            Slogf.i(TAG, "Replaced the existing callback %s", oldHolder.mBinderInterface);
        }
    }

    /**
     * Removes an item in the container by its key, if there is any.
     */
    public void remove(K key) {
        synchronized (mLock) {
            BinderInterfaceHolder<K, V> holder = mBinderMap.get(key);
            if (holder == null) {
                Slogf.i(TAG, "Failed to remove because there was no item with key %s", key);
                return;
            }
            holder.mBinder.unlinkToDeath(holder, 0);
            mBinderMap.remove(key);
        }
    }

    /**
     * Removes the item at the given index, if there is any.
     */
    public void removeAt(int index) {
        synchronized (mLock) {
            BinderInterfaceHolder<K, V> holder = mBinderMap.valueAt(index);
            if (holder == null) {
                Slogf.i(TAG, "Failed to remove because there was no item at index %d", index);
                return;
            }
            holder.mBinder.unlinkToDeath(holder, 0);
            mBinderMap.removeAt(index);
        }
    }

    /**
     * Returns the number of registered {@link BinderInterfaceHolder} objects in this container.
     */
    public int size() {
        synchronized (mLock) {
            return mBinderMap.size();
        }
    }

    /**
     * Returns the key at the given index in the container.
     *
     * @param index The desired index, must be between 0 and {@link #size()}-1.
     * @throws ArrayIndexOutOfBoundsException if the index is invalid
     */
    public K keyAt(int index) {
        synchronized (mLock) {
            return mBinderMap.keyAt(index);
        }
    }

    /**
     * Returns the {@link IInterface} at the given index in the container.
     *
     * @param index The desired index, must be between 0 and {@link #size()}-1.
     * @throws ArrayIndexOutOfBoundsException if the index is invalid
     */
    public V valueAt(int index) {
        synchronized (mLock) {
            BinderInterfaceHolder<K, V> holder = mBinderMap.valueAt(index);
            return holder.mBinderInterface;
        }
    }

    /**
     * Returns whether the {@code key} is stored in the container.
     */
    public boolean containsKey(K key) {
        synchronized (mLock) {
            return mBinderMap.containsKey(key);
        }
    }

    /**
     * Returns an unmodifiable copy of keys in the container, or an empty set if the container is
     * empty.
     */
    public Set<K> keySet() {
        synchronized (mLock) {
            return Collections.unmodifiableSet(mBinderMap.keySet());
        }
    }

    /**
     * Sets a death callback to be notified after a binder is unlinked and removed from the
     * container.
     */
    public void setBinderDeathCallback(@Nullable BinderDeathCallback<K> binderDeathCallback) {
        mBinderDeathCallback = binderDeathCallback;
    }

    private void removeByBinderInterfaceHolder(BinderInterfaceHolder<K, V> holder) {
        K deadKey = null;
        synchronized (mLock) {
            int index = mBinderMap.indexOfValue(holder);
            if (index >= 0) {
                deadKey = mBinderMap.keyAt(index);
                mBinderMap.removeAt(index);
                Slogf.i(TAG, "Binder died, so remove %s", holder.mBinderInterface);
            }
        }
        if (mBinderDeathCallback != null && deadKey != null) {
            mBinderDeathCallback.onBinderDied(deadKey);
        }
    }
}
