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
package com.android.server.uwb.correction.pose;

import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.android.server.uwb.correction.math.Pose;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Optional base implementation for a PoseSource. Provides help to register listeners and
 * publishing.
 */
public abstract class PoseSourceBase implements IPoseSource {
    private final Lock mLockObject = new ReentrantLock();
    @GuardedBy("mLockObject")
    private final List<PoseEventListener> mListeners;
    private static final String TAG = "PoseSourceBase";
    private final AtomicReference<Pose> mPose = new AtomicReference<>();

    public PoseSourceBase() {
        mListeners = new ArrayList<>();
    }

    /**
     * Starts the pose source. Called by the {@link PoseSourceBase} when the first
     * listener subscribes.
     */
    protected abstract void start();

    /**
     * Stops the pose source. Called by the {@link PoseSourceBase} when the last
     * listener unsubscribes.
     */
    protected abstract void stop();

     /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        mLockObject.lock();
        try {
            if (mListeners.size() > 0) {
                mListeners.clear();
                stop(); // Run inside the lock to make sure stops and starts are sequential.
            }
        } finally {
            mLockObject.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerListener(@NonNull PoseEventListener listener) {
        Objects.requireNonNull(listener);
        mLockObject.lock();
        try {
            mListeners.add(listener);
            if (mListeners.size() == 1) {
                start(); // Run inside the lock to make sure starts and stops are sequential.
            }
        } finally {
            mLockObject.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean unregisterListener(@NonNull PoseEventListener listener) {
        Objects.requireNonNull(listener);
        mLockObject.lock();
        try {
            boolean removed = mListeners.remove(listener);
            if (removed && mListeners.size() == 0) {
                stop(); // Run inside the lock to make sure starts and stops are sequential.
            }
            return removed;
        } finally {
            mLockObject.unlock();
        }
    }

    /**
     * Publishes the pose to all listeners.
     *
     * @param pose The updated device pose.
     */
    protected void publish(@NonNull Pose pose) {
        Objects.requireNonNull(pose);
        ArrayList<PoseEventListener> listeners;
        mLockObject.lock();
        try {
            // Copy snapshot to minimize lock time and allow changes to listeners while
            // we report pose changes.
            listeners = new ArrayList<>(this.mListeners);
        } finally {
            mLockObject.unlock();
        }
        this.mPose.set(pose);
        for (int i = 0; i < listeners.size(); i++) {
            try {
                listeners.get(i).onPoseChanged(pose);
            } catch (Exception ex) {
                Log.e(TAG, ex.toString());

                // Remove the listener, so it doesn't become a persistent problem.
                listeners.remove(i--);
            }
        }
    }

    @Override
    public Pose getPose() {
        return mPose.get();
    }
}
