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

package com.android.adservices.service.ui.data;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.internal.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.Objects;

/** DAO for interacting with local storage on behalf of the UxStatesManager. */
@RequiresApi(Build.VERSION_CODES.S)
public class UxStatesDao {
    @VisibleForTesting public static final int DATASTORE_VERSION = 1;

    @VisibleForTesting
    public static final String DATASTORE_NAME =
            FileCompatUtils.getAdservicesFilename("adservices.uxstates.xml");

    @VisibleForTesting
    public static final String TEST_DATASTORE_NAME =
            FileCompatUtils.getAdservicesFilename("adservices.uxstates.test.xml");

    private static final Object LOCK = new Object();

    private static volatile UxStatesDao sUxStatesDao;

    private final BooleanFileDatastore mDatastore;

    @VisibleForTesting
    public UxStatesDao(@NonNull BooleanFileDatastore datastore) {
        Objects.requireNonNull(datastore);

        mDatastore = datastore;
    }

    /** Returns an instance of the UxStatesDao. */
    public static UxStatesDao getInstance(@NonNull Context context) {

        if (sUxStatesDao == null) {
            synchronized (LOCK) {
                if (sUxStatesDao == null) {
                    BooleanFileDatastore datastore =
                            new BooleanFileDatastore(context, DATASTORE_NAME, DATASTORE_VERSION);
                    sUxStatesDao = new UxStatesDao(datastore);
                }
            }
        }

        return sUxStatesDao;
    }

    /** Return the current UX. */
    public PrivacySandboxUxCollection getUx() {
        for (PrivacySandboxUxCollection uxCollection : PrivacySandboxUxCollection.values()) {
            if (Boolean.TRUE.equals(mDatastore.get(uxCollection.toString()))) {
                return uxCollection;
            }
        }
        return PrivacySandboxUxCollection.UNSUPPORTED_UX;
    }

    /** Set the current UX. */
    public void setUx(@NonNull PrivacySandboxUxCollection ux) {
        try {
            for (PrivacySandboxUxCollection uxCollection : PrivacySandboxUxCollection.values()) {
                mDatastore.put(uxCollection.toString(), ux.equals(uxCollection));
            }
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException("UxStatesDao: setUx operation failed.", e);
        }
    }

    /** Return the enrollment channel. */
    public PrivacySandboxEnrollmentChannelCollection getEnrollmentChannel(
            @NonNull PrivacySandboxUxCollection ux) {
        for (PrivacySandboxEnrollmentChannelCollection enrollmentChannelCollection :
                ux.getEnrollmentChannelCollection()) {
            if (Boolean.TRUE.equals(mDatastore.get(enrollmentChannelCollection.toString()))) {
                return enrollmentChannelCollection;
            }
        }
        return null;
    }

    /**
     * Set the enrollment channel. Trying to set an enrollment channel that does not belong to the
     * particular UX would be a no-op.
     */
    public void setEnrollmentChannel(
            @NonNull PrivacySandboxUxCollection ux,
            @NonNull PrivacySandboxEnrollmentChannelCollection enrollmentChannel) {
        try {
            for (PrivacySandboxEnrollmentChannelCollection enrollmentChannelCollection :
                    ux.getEnrollmentChannelCollection()) {
                mDatastore.put(
                        enrollmentChannelCollection.toString(),
                        enrollmentChannelCollection.equals(enrollmentChannel));
            }
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException("UxStatesDao: setEnrollmentChannel operation failed.", e);
        }
    }
}
