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

package android.backup.cts;

import android.app.GrammaticalInflectionManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.os.ParcelFileDescriptor;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.BackupUtils;

import org.junit.Before;

import java.io.IOException;
import java.io.InputStream;

public class AppGrammaticalGenderBackupTest extends BaseBackupCtsTest {
    private static final String RESTORE_TOKEN = "1";
    private static final String SYSTEM_PACKAGE = "android";

    private final BackupUtils mBackupUtils =
            new BackupUtils() {
                @Override
                protected InputStream executeShellCommand(String command) {
                    return executeInstrumentationShellCommand(getInstrumentation(), command);
                }
            };

    private GrammaticalInflectionManager mGrammaticalInflectionManager;

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getContext();
        mGrammaticalInflectionManager = context.getSystemService(
                GrammaticalInflectionManager.class);
    }

    /** Test installed app set gender before restoring */
    public void testBackupRestore_setGenderBeforeRestoring_doesNotRestore()
            throws IOException {
        if (!isBackupSupported()) {
            return;
        }
        setAndBackupAppGender(Configuration.GRAMMATICAL_GENDER_NEUTRAL);
        // fake the behavior that user set the gender before restoring
        setGrammaticalGender(Configuration.GRAMMATICAL_GENDER_FEMININE);

        mBackupUtils.restoreAndAssertSuccess(RESTORE_TOKEN, SYSTEM_PACKAGE);

        // verify that device won't be restored
        assertEquals(Configuration.GRAMMATICAL_GENDER_FEMININE,
                mGrammaticalInflectionManager.getApplicationGrammaticalGender());
    }

    private void setGrammaticalGender(int gender) {
        mGrammaticalInflectionManager.setRequestedApplicationGrammaticalGender(gender);
    }

    private void setAndBackupAppGender(int gender) throws IOException {
        mGrammaticalInflectionManager.setRequestedApplicationGrammaticalGender(gender);
        mBackupUtils.backupNowAndAssertSuccess(SYSTEM_PACKAGE);
    }

    private static InputStream executeInstrumentationShellCommand(
            Instrumentation instrumentation, String command) {
        final ParcelFileDescriptor pfd =
                instrumentation.getUiAutomation().executeShellCommand(command);
        return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
    }
}
