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

package android.security.cts;

import static org.junit.Assert.*;

import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.VoicemailContract;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import java.io.File;
import java.io.FileInputStream;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

@RunWith(AndroidJUnit4.class)
public class SQLiteTest extends StsExtraBusinessLogicTestCase {
    private static final String DATABASE_FILE_NAME = "database_test.db";

    private ContentResolver mResolver;
    private String mPackageName;
    private Context mContext;

    private SQLiteDatabase mDatabase;

    @Before
    public void setUp() throws Exception {
        mResolver = getContext().getContentResolver();
        mContext = InstrumentationRegistry.getTargetContext();
        mPackageName = mContext.getPackageName();

        mContext.deleteDatabase(DATABASE_FILE_NAME);
        File databaseFile = getContext().getDatabasePath(DATABASE_FILE_NAME);
        databaseFile.getParentFile().mkdirs(); // directory may not exist
        mDatabase = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
        assertNotNull(mDatabase);
    }

    /**
     * b/139186193
     */
    @AsbSecurityTest(cveBugId = 139186193)
    @Test
    public void test_android_cve_2019_2195() {
        Uri uri = VoicemailContract.Voicemails.CONTENT_URI;
        uri = uri.buildUpon().appendQueryParameter("source_package", mPackageName).build();

        try {
            mContext.grantUriPermission(mPackageName, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        } catch (Exception e) {
            if (e instanceof java.lang.SecurityException) {
                // this suggests com.android.providers.contacts.VoicemailContentProvider
                // does not allow granting of Uri permissions, hence return.
                return;
            }
        }

        try {
            String fileToDump = mContext.createPackageContext("com.android.providers.contacts", 0)
                    .getDatabasePath("contacts2.db").getAbsolutePath();
            try {
                mResolver.query(uri, null, null, null,
                   "_id ASC LIMIT _TOKENIZE('calls(_data,_data,_data,source_package,type) VALUES(''"
                                + fileToDump + "'',?,?,''" + mPackageName + "'',4);',0,'','Z')")
                        .close();
                fail("Vulnerable function exists");
            } catch (android.database.sqlite.SQLiteException e) {
                    // do nothing
            }
        } catch (NameNotFoundException n) {
            // do nothing
        }
    }

    /**
     * b/153352319
     */
    @AsbSecurityTest(cveBugId = 153352319)
    @Test
    public void test_android_float_to_text_conversion_overflow() {
        String create_cmd = "select (printf('%.2147483647G',0.01));";
        try (Cursor c = mDatabase.rawQuery(create_cmd, null)) {
            assertEquals(c.getCount(), 1);
        }
    }
}
