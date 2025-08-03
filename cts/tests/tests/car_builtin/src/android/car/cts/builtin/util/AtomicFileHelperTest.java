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

package android.car.cts.builtin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.car.builtin.util.AtomicFileHelper;
import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;

@RunWith(AndroidJUnit4.class)
public final class AtomicFileHelperTest {
    private static final String TAG = AtomicFileHelperTest.class.getSimpleName();

    private static final String PRE_EXIST_TEST_FILE_NAME = "TestFilePreExist";
    private static final String NEWLY_CREATED_TEST_FILE_NAME = "TestFileNewlyCreated";
    private static final String FAILED_TEST_FILE_NAME = "TestFileFailed";
    private static final String REWRITE_TEST_FILE_NAME = "TestFileRewriteCreated";
    private static final String TEST_FILE_CONTENT = "AtomicFileHelper CTS TEST FILE CONTENT";
    private static final String REWRITE_CONTENT = "CTS TEST ATOMIC FILE REWRITE";

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    @Test
    public void testAtomicFileWithPreExistedBaseFile() throws Exception {
        File appPrivateFileDir = mContext.getFilesDir();

        // create a private file
        mContext.openFileOutput(PRE_EXIST_TEST_FILE_NAME, Context.MODE_PRIVATE);

        // create a File object to point to the newly created private file
        File baseFile = new File(/* parent= */appPrivateFileDir, PRE_EXIST_TEST_FILE_NAME);
        AtomicFile atomicFile = new AtomicFile(baseFile);

        // execution and assertion
        assertTrue(AtomicFileHelper.exists(atomicFile));
        baseFile.delete();
        assertFalse(AtomicFileHelper.exists(atomicFile));
    }

    @Test
    public void testAtomicFileWithNonExistedBaseFile() throws Exception {
        // setup
        File appPrivateFileDir = mContext.getFilesDir();
        File baseFile = new File(/* parent= */appPrivateFileDir, NEWLY_CREATED_TEST_FILE_NAME);
        if (baseFile.exists()) {
            baseFile.delete();
        }
        AtomicFile atomicFile = new AtomicFile(baseFile);

        // execution and assertion
        // 1. the base file does not exist yet
        assertFalse(AtomicFileHelper.exists(atomicFile));

        // 2. write into the atomic file and result a new base file
        FileOutputStream fos = atomicFile.startWrite();
        fos.write(TEST_FILE_CONTENT.getBytes());
        atomicFile.finishWrite(fos);
        assertTrue(AtomicFileHelper.exists(atomicFile));

        // 3. check if the content match by directly read from the base file
        BufferedReader br = new BufferedReader(new FileReader(baseFile));
        String fileContent = br.readLine();
        Log.d(TAG, fileContent);
        assertEquals(TEST_FILE_CONTENT, fileContent);
        br.close();

        // 4. delete the base file;
        baseFile.delete();
        assertFalse(AtomicFileHelper.exists(atomicFile));
    }

    @Test
    public void testAtomicFileWithFailedWrite() throws Exception {
        // setup
        File appPrivateFileDir = mContext.getFilesDir();
        File baseFile = new File(/* parent= */appPrivateFileDir, REWRITE_TEST_FILE_NAME);
        if (baseFile.exists()) {
            baseFile.delete();
        }
        AtomicFile atomicFile = new AtomicFile(baseFile);

        // execution and assertion
        // 1. the base file does not exist yet
        assertFalse(AtomicFileHelper.exists(atomicFile));

        // 2. write into the atomic file and result a new base file
        FileOutputStream fos = atomicFile.startWrite();
        fos.write(TEST_FILE_CONTENT.getBytes());
        atomicFile.failWrite(fos);
        assertFalse(AtomicFileHelper.exists(atomicFile));
    }

    @Test
    public void testAtomicFileRewrite() throws Exception {
        // setup
        File appPrivateFileDir = mContext.getFilesDir();
        File baseFile = new File(/* parent= */appPrivateFileDir, NEWLY_CREATED_TEST_FILE_NAME);
        if (baseFile.exists()) {
            baseFile.delete();
        }

        // execution and assertion
        // 1. write into the atomic file and result a new base file
        AtomicFile atomicFile1 = new AtomicFile(baseFile);
        FileOutputStream fos1 = atomicFile1.startWrite();
        fos1.write(TEST_FILE_CONTENT.getBytes());
        atomicFile1.finishWrite(fos1);
        assertTrue(AtomicFileHelper.exists(atomicFile1));
        fos1.close();

        // 2. create a new AtomicFile and rewrite new content
        AtomicFile atomicFile2 = new AtomicFile(baseFile);
        FileOutputStream fos2 = atomicFile2.startWrite();
        fos2.write(REWRITE_CONTENT.getBytes());
        atomicFile2.finishWrite(fos2);
        assertTrue(AtomicFileHelper.exists(atomicFile1));
        fos2.close();

        // 3. reopen the atomic file and read out the content
        AtomicFile atomicFile3 = new AtomicFile(baseFile);
        FileInputStream fis = atomicFile3.openRead();
        String atomicFileReadContent = new String(atomicFile3.readFully());
        Log.d(TAG, atomicFileReadContent);
        assertEquals(atomicFileReadContent, REWRITE_CONTENT);
        fis.close();

        // 4. test if AtomicFile read is the same as base file read
        BufferedReader br = new BufferedReader(new FileReader(baseFile));
        String baseFileReadContent = br.readLine();
        Log.d(TAG, baseFileReadContent);
        assertEquals(atomicFileReadContent, baseFileReadContent);
        br.close();

        // 5. delete the base file;
        baseFile.delete();
    }
}
