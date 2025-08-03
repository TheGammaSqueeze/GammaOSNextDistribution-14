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

package healthconnect.exportimport;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.os.Environment;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.exportimport.ExportManager;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.utils.FilesUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class ExportManagerTest {
    private static final String DATABASE_NAME = "healthconnect.db";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(Environment.class)
                    .mockStatic(TransactionManager.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Mock private Context mContext;
    @Mock private TransactionManager mTransactionManager;

    private ExportManager mExportManager;
    private File mMockExportDataDirectory;
    private File mMockDataDirectory;
    private final UserHandle mUserHandle = UserHandle.of(UserHandle.myUserId());

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mMockDataDirectory = mContext.getDir("mock_data", Context.MODE_PRIVATE);
        mMockExportDataDirectory = mContext.getDir("mock_export_data", Context.MODE_PRIVATE);
        when(Environment.getDataDirectory()).thenReturn(mMockExportDataDirectory);
        when(TransactionManager.getInitialisedInstance()).thenReturn(mTransactionManager);
        mExportManager = new ExportManager();
    }

    @After
    public void tearDown() {
        FilesUtil.deleteDir(mMockDataDirectory);
        FilesUtil.deleteDir(mMockExportDataDirectory);
        clearInvocations(mTransactionManager);
    }

    @Test
    public void testExportLocally_copiesAllData() throws Exception {
        File originalDbFile = createAndGetNonEmptyFile(mMockDataDirectory, DATABASE_NAME);
        when(mTransactionManager.getDatabasePath()).thenReturn(originalDbFile);

        String exportFilePath = mExportManager.exportLocally(mUserHandle);

        assertThat(getFileContentFromPath(exportFilePath).length())
                .isEqualTo(originalDbFile.length());
    }

    private static File createAndGetNonEmptyFile(File dir, String fileName) throws IOException {
        File file = new File(dir, fileName);
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("Contents of file " + fileName);
        fileWriter.close();
        return file;
    }

    private static String getFileContentFromPath(String path) throws Exception {
        FileReader reader = new FileReader(path);
        StringBuilder stringBuilder = new StringBuilder();
        int nextChar;
        while ((nextChar = reader.read()) != -1) {
            stringBuilder.append((char) nextChar);
        }
        return String.valueOf(stringBuilder);
    }
}
