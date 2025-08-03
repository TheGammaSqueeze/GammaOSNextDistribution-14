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

package com.android.adservices.data.signals;

import static com.android.adservices.data.signals.EncoderPersistenceDao.ADSERVICES_PREFIX;
import static com.android.adservices.data.signals.EncoderPersistenceDao.ENCODERS_DIR;
import static com.android.adservices.data.signals.EncoderPersistenceDao.ENCODER_FILE_SUFFIX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

public class EncoderPersistenceDaoTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private static final AdTechIdentifier BUYER_1 = CommonFixture.VALID_BUYER_1;
    private static final String ENCODER =
            "function hello() {\n" + "  console.log(\"Hello World!\");\n" + "}";
    private static final String ENCODER_2 =
            "function bye() {\n" + "  console.log(\"Goodbye World!\");\n" + "}";

    private EncoderPersistenceDao mEncoderPersistenceDao;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mEncoderPersistenceDao = EncoderPersistenceDao.getInstance(CONTEXT);
    }

    @After
    public void tearDown() {
        mEncoderPersistenceDao.deleteAllEncoders();
    }

    @Test
    public void testPersistenceIsSingleton() {
        assertEquals(
                "Both objects should have been the same instance",
                mEncoderPersistenceDao,
                EncoderPersistenceDao.getInstance(CONTEXT.getApplicationContext()));
    }

    @Test
    public void testGenerateFileName() {
        assertEquals(
                ADSERVICES_PREFIX + BUYER_1.toString() + ENCODER_FILE_SUFFIX,
                mEncoderPersistenceDao.generateFileNameForBuyer(BUYER_1));
    }

    @Test
    public void testCreateEncoderDirectory() {
        File encoderDir = new File(CONTEXT.getFilesDir(), ENCODERS_DIR);
        assertFalse("Directory should not have existed so far", encoderDir.exists());

        encoderDir = mEncoderPersistenceDao.createEncodersDirectoryIfDoesNotExist();
        assertTrue("Directory should have been created", encoderDir.exists());
        assertEquals(
                "Created Directory name does not match expected",
                ENCODERS_DIR,
                encoderDir.getName());
    }

    @Test
    public void testCreateFileInDirectory() {
        File encoderDir = mEncoderPersistenceDao.createEncodersDirectoryIfDoesNotExist();
        String fileName = mEncoderPersistenceDao.generateFileNameForBuyer(BUYER_1);
        File encoderFile = new File(encoderDir, fileName);

        assertFalse("File should not have existed", encoderFile.exists());

        encoderFile = mEncoderPersistenceDao.createFileInDirectory(encoderDir, fileName);
        assertTrue("File should have been created", encoderFile.exists());
        assertEquals(
                "Created name does not match expected",
                ADSERVICES_PREFIX + BUYER_1 + ENCODER_FILE_SUFFIX,
                encoderFile.getName());
    }

    @Test
    public void testPersistAndGetEncoder() {
        assertTrue(mEncoderPersistenceDao.persistEncoder(BUYER_1, ENCODER));
        assertEquals(
                "Encoder read should have matched the encoder persisted",
                ENCODER,
                mEncoderPersistenceDao.getEncoder(BUYER_1));
    }

    @Test
    public void testPersistWipeAndThenGetEmpty() {
        assertNull(
                "Persisted encoder should have been null",
                mEncoderPersistenceDao.getEncoder(BUYER_1));

        assertTrue(mEncoderPersistenceDao.persistEncoder(BUYER_1, ENCODER));
        assertEquals(
                "Persisted encoder should have matched the encoder read back",
                ENCODER,
                mEncoderPersistenceDao.getEncoder(BUYER_1));

        assertTrue(mEncoderPersistenceDao.deleteAllEncoders());
        assertNull(
                "Persisted encoder should have been null again",
                mEncoderPersistenceDao.getEncoder(BUYER_1));
    }

    @Test
    public void testPersistOverwrites() {
        assertTrue(mEncoderPersistenceDao.persistEncoder(BUYER_1, ENCODER));
        assertEquals(
                "Persisted encoder should have matched the encoder read back",
                ENCODER,
                mEncoderPersistenceDao.getEncoder(BUYER_1));

        assertTrue(mEncoderPersistenceDao.persistEncoder(BUYER_1, ENCODER_2));
        assertEquals(
                "Persisted encoder should have been replaced",
                ENCODER_2,
                mEncoderPersistenceDao.getEncoder(BUYER_1));
    }

    @Test
    public void testWipeAllEncoders() {
        File encoderDir = mEncoderPersistenceDao.createEncodersDirectoryIfDoesNotExist();
        String fileName = mEncoderPersistenceDao.generateFileNameForBuyer(BUYER_1);
        File encoderFile = new File(encoderDir, fileName);

        assertFalse("File should not have existed", encoderFile.exists());

        encoderFile = mEncoderPersistenceDao.createFileInDirectory(encoderDir, fileName);
        assertTrue("File should have been created", encoderFile.exists());

        assertTrue(
                "All encoders and directory should have been deleted",
                mEncoderPersistenceDao.deleteAllEncoders());
        encoderDir = new File(CONTEXT.getFilesDir(), ENCODERS_DIR);
        assertFalse("Directory should have been wiped", encoderDir.exists());
    }

    @Test
    public void testDeleteEmptyDirectory() {
        File encoderDir = new File(CONTEXT.getFilesDir(), ENCODERS_DIR);
        assertFalse("Directory should not have existed", encoderDir.exists());

        assertTrue(
                "The deletion of non-existing directory should have been true",
                mEncoderPersistenceDao.deleteAllEncoders());
    }

    @Test
    public void testWriteAndReadForFile() {
        File tempFile = new File(CONTEXT.getFilesDir(), ADSERVICES_PREFIX + "temp_file");
        mEncoderPersistenceDao.writeDataToFile(tempFile, ENCODER);
        String readData =
                mEncoderPersistenceDao.readDataFromFile(CONTEXT.getFilesDir(), tempFile.getName());
        assertEquals(
                "Data written to the file should have matched data read from file",
                ENCODER,
                readData);
        tempFile.delete();
    }
}
