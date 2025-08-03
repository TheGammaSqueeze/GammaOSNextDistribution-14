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

package com.android.car.user;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DEBUGGING_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.ICarResultReceiver;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.UserLifecycleEventFilter;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper DTO to hold info about an app-based {@code UserLifecycleListener}
 */
final class AppLifecycleListener {

    private static final String TAG = CarLog.tagFor(AppLifecycleListener.class);

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final DeathRecipient mDeathRecipient;

    public final int uid;
    public final String packageName;
    public final ICarResultReceiver receiver;
    /**
     * List of filters that are associcated with the listeners of this receiver.
     * A null list means that the filters always pass thus the receiver is called for all events.
     */
    private @Nullable ArrayList<UserLifecycleEventFilter> mFilters;

    AppLifecycleListener(int uid, String packageName, ICarResultReceiver receiver,
            @Nullable UserLifecycleEventFilter filter, BinderDeathCallback binderDeathCallback) {
        this.uid = uid;
        this.packageName = packageName;
        this.receiver = receiver;
        if (filter != null) {
            if (DBG) {
                Slogf.d(TAG, "AppLifecycleListener: filter list for receiver %s of package %s"
                        + " is initialized with filter %s.", receiver, packageName, filter);
            }
            mFilters = new ArrayList<>(1);
            mFilters.add(filter);
        }

        mDeathRecipient = () -> binderDeathCallback.onBinderDeath(this);
        Slogf.v(TAG, "linking death recipient %s", mDeathRecipient);
        try {
            receiver.asBinder().linkToDeath(mDeathRecipient, /* flags= */ 0);
        } catch (RemoteException e) {
            Slogf.wtf(TAG, "Cannot listen to death of %s", mDeathRecipient);
        }
    }

    @VisibleForTesting
    @Nullable List<UserLifecycleEventFilter> getFilters() {
        return mFilters;
    }

    void addFilter(@Nullable UserLifecycleEventFilter filter) {
        // There has been a null filter added. Ignore any other filters.
        if (mFilters == null) {
            if (DBG) {
                Slogf.d(TAG, "addFilter: filter %s is ignored for receiver %s since it already has"
                        + " a null filter added before.", filter, this);
            }
            return;
        }
        if (filter == null) {
            if (DBG) {
                Slogf.d(TAG, "addFilter: Setting the filter list to null for receiver %s due to"
                        + " adding a null filter.", this);
            }
            // Adding a null filter will set the list to null, making all events pass.
            mFilters = null;
        } else {
            if (DBG) {
                Slogf.d(TAG, "addFilter: Adding a new filter %s to the filter list of receiver %s",
                        filter, this);
            }
            mFilters.add(filter);
        }
    }

    boolean applyFilters(UserLifecycleEvent event) {
        // Always return true when the list is null.
        if (mFilters == null) {
            if (DBG) {
                Slogf.d(TAG, "applyFilters: returns true for event %s since the filter list is null"
                        + " for receiver %s.", event, this);
            }
            return true;
        }

        // Only non-null filters. Returns true when any of the filters passes the event.
        for (int i = 0; i < mFilters.size(); i++) {
            // If any of the filters passes the event, then return true.
            UserLifecycleEventFilter filter = mFilters.get(i);
            if (filter.apply(event)) {
                if (DBG) {
                    Slogf.d(TAG, "applyFilters: returns true for event %s since the filter %s"
                            + " evaluates to true for receiver %s.", event, filter, this);
                }
                return true;
            }
        }

        if (DBG) {
            Slogf.d(TAG, "applyFilters: returns false for event %s since all filters in the list"
                    + " evaluates to false for receiver %s.", event, this);
        }
        return false;
    }

    void onDestroy() {
        Slogf.v(TAG, "onDestroy(): unlinking death recipient %s", mDeathRecipient);
        receiver.asBinder().unlinkToDeath(mDeathRecipient, /* flags= */ 0);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(PrintWriter writer) {
        writer.printf("uid=%d, pkg=%s\n", uid, packageName);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    String toShortString() {
        return uid + "-" + packageName;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DEBUGGING_CODE)
    public String toString() {
        return "AppLifecycleListener[uid=" + uid + ", pkg=" + packageName + "]";
    }

    interface BinderDeathCallback {
        void onBinderDeath(AppLifecycleListener listener);
    }
}
