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

package com.android.server.healthconnect.permission;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.UserHandle;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Class for managing health permissions first grant time datastore.
 *
 * @hide
 */
public interface FirstGrantTimeDatastore {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({DATA_TYPE_CURRENT, DATA_TYPE_STAGED})
    public @interface DataType {}

    /* Type used for storing general grant time */
    int DATA_TYPE_CURRENT = 0;

    /* Type used for applying backup data when it's needed. */
    int DATA_TYPE_STAGED = 1;

    /**
     * Read {@link UserGrantTimeState for given user}.
     *
     * @hide
     */
    @NonNull
    UserGrantTimeState readForUser(@NonNull UserHandle user, @DataType int dataType);

    /**
     * Write {@link UserGrantTimeState for given user}.
     *
     * @hide
     */
    void writeForUser(
            @NonNull UserGrantTimeState grantTimesState,
            @NonNull UserHandle user,
            @DataType int dataType);

    /**
     * Returns the name of the files used by the store for the given user.
     *
     * @hide
     */
    File getFile(@NonNull UserHandle user, @DataType int dataType);

    /**
     * Create instance of the datastore class.
     *
     * @hide
     */
    @NonNull
    static FirstGrantTimeDatastore createInstance() {
        return new FirstGrantTimeDatastoreXmlPersistence();
    }
}
