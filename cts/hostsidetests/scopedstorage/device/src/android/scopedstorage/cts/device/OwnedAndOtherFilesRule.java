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

package android.scopedstorage.cts.device;

import android.content.ContentResolver;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;

import org.junit.rules.ExternalResource;

/**
 * Introduces a few owned items and a few other app items.
 */
public class OwnedAndOtherFilesRule extends ExternalResource {
    private final ContentResolver mContentResolver;

    private final OwnedFilesRule mOwnedFilesRule;

    private final OtherAppFilesRule mOtherAppFilesRule;

    public OwnedAndOtherFilesRule(ContentResolver contentResolver) {
        this.mContentResolver = contentResolver;
        mOwnedFilesRule = new OwnedFilesRule(mContentResolver);
        mOtherAppFilesRule = new OtherAppFilesRule(mContentResolver);
    }

    @Override
    public void before() throws Exception {
        mOwnedFilesRule.before();
        mOtherAppFilesRule.before();
    }

    @Override
    public void after() {
        mOwnedFilesRule.after();
        mOtherAppFilesRule.after();
    }

    protected static Cursor getResultForFilesQuery(ContentResolver contentResolver,
            Bundle queryArgs) {
        return contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                null,
                queryArgs,
                null);
    }
}
