/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.content.cts.contenturitestapp;

import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

public class TestService extends Service {
    private int mContentUriId = 1;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new IContentUriTestService.Stub() {
        @Override
        public Uri getContentUriForContext(int pkgAccessType, int modeFlags) {
            // Construct the URI
            Uri baseContentUri = switch (pkgAccessType) {
                case PKG_ACCESS_TYPE_NONE, PKG_ACCESS_TYPE_GRANT -> TestProvider.CONTENT_URI_BASE;
                case PKG_ACCESS_TYPE_GENERAL -> TestProvider.getSubsetContentUri(modeFlags);
                default -> throw new RuntimeException("Invalid PKG_ACCESS_TYPE: " + pkgAccessType);
            };
            Uri uri = ContentUris.withAppendedId(baseContentUri, mContentUriId++);

            // Grant modeFlags if pkgAccessType is grant
            if (pkgAccessType == PKG_ACCESS_TYPE_GRANT && modeFlags != 0) {
                grantUriPermission(RECIPIENT, uri, modeFlags);
            }
            return uri;
        }

        @Override
        public Uri[] getContentUrisForManifest() {
            Uri[] uris = new Uri[URI_COUNT];
            uris[URI_NO_PERMISSION_ID] = TestProvider.CONTENT_URI_NONE;
            uris[URI_READ_PERMISSION_ID] = TestProvider.getSubsetContentUri(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            uris[URI_WRITE_PERMISSION_ID] = TestProvider.getSubsetContentUri(
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            uris[URI_READ_WRITE_PERMISSION_ID] = TestProvider.getSubsetContentUri(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return uris;
        }
    };
}
