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

package com.android.adservices.service.measurement.registration;

import android.net.Uri;

public class AsyncRedirect {
    public enum RedirectBehavior {
        /** Do not modify redirect path */
        AS_IS,
        /** Prepend well known path prefix to path of redirect url for Location type redirects */
        LOCATION_TO_WELL_KNOWN,
    }

    private final Uri mUri;
    private final RedirectBehavior mRedirectBehavior;

    public AsyncRedirect(Uri uri, RedirectBehavior redirectBehavior) {
        mUri = uri;
        mRedirectBehavior = redirectBehavior;
    }

    public Uri getUri() {
        return mUri;
    }

    public RedirectBehavior getRedirectBehavior() {
        return mRedirectBehavior;
    }




}
