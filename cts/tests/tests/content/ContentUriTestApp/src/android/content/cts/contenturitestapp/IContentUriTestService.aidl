/**
 * Copyright (c) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.content.cts.contenturitestapp;

import android.net.Uri;

interface IContentUriTestService {
    // Constants for #getContentUriForContext
    const int PKG_ACCESS_TYPE_NONE = 0;
    const int PKG_ACCESS_TYPE_GRANT = 1;
    const int PKG_ACCESS_TYPE_GENERAL = 2;
    const String RECIPIENT = "android.content.cts";

    // Constants for #getContentUrisForManifest
    const int URI_NO_PERMISSION_ID = 0;
    const int URI_READ_PERMISSION_ID = 1;
    const int URI_WRITE_PERMISSION_ID = 2;
    const int URI_READ_WRITE_PERMISSION_ID = 3;
    const int URI_COUNT = 4;

    // Sends a content Uri to the recipient (should be {@link android.content.cts}) for testing
    // {@link android.content.Context#checkContentUriPermissionFull}.
    // If pkgAccessType is PKG_ACCESS_TYPE_GRANT, then grants modeFlags permission to the recipient.
    Uri getContentUriForContext(int pkgAccessType, int modeFlags);

    // Sends an array of content Uris for testing the Activity Manifest attribute
    // {@link android.R.attr#requireContentUriPermissionFromCaller}.
    Uri[] getContentUrisForManifest();
}
