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

package com.android.adservices.service.measurement.access;

import android.adservices.common.AdServicesStatusUtils;
import android.annotation.NonNull;
import android.content.Context;

/** An interface exposes capability to control measurement PPAPIs access by clients. */
public interface IAccessResolver {
    /**
     * @param context to retrieve contextual parameters. This method is chosen over constructor to
     *     pass context to avoid memory leak issues
     * @return AccessContainer
     */
    @NonNull
    AccessInfo getAccessInfo(@NonNull Context context);

    /** @return error status code to return in case the access was not granted. */
    @AdServicesStatusUtils.StatusCode
    int getErrorStatusCode();

    /** @return error message to throw in case access wasn't granted. */
    @NonNull
    String getErrorMessage();
}
