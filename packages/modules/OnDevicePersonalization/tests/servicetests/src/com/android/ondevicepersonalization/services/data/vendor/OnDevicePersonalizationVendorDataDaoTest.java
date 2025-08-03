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

package com.android.ondevicepersonalization.services.data.vendor;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.services.data.DbUtils;
import com.android.ondevicepersonalization.services.data.OnDevicePersonalizationDbHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(JUnit4.class)
public class OnDevicePersonalizationVendorDataDaoTest {
    private static final ComponentName TEST_OWNER = new ComponentName("ownerPkg", "ownerCls");
    private static final String TEST_CERT_DIGEST = "certDigest";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private OnDevicePersonalizationVendorDataDao mDao;

    @Before
    public void setup() {
        mDao = OnDevicePersonalizationVendorDataDao.getInstanceForTest(mContext, TEST_OWNER,
                TEST_CERT_DIGEST);
    }

    @Test
    public void testBatchInsert() {
        long timestamp = System.currentTimeMillis();
        addTestData(timestamp);
        long timestampFromDB = mDao.getSyncToken();
        assertEquals(timestamp, timestampFromDB);

        Cursor cursor = mDao.readAllVendorData();
        assertEquals(5, cursor.getCount());
        cursor.close();

        List<VendorData> dataList = new ArrayList<>();
        dataList.add(new VendorData.Builder().setKey("key3").setData(new byte[10]).build());
        dataList.add(new VendorData.Builder().setKey("key4").setData(new byte[10]).build());

        List<String> retainedKeys = new ArrayList<>();
        retainedKeys.add("key2");
        retainedKeys.add("key3");
        retainedKeys.add("key4");
        retainedKeys.add("large");
        assertTrue(mDao.batchUpdateOrInsertVendorDataTransaction(dataList, retainedKeys,
                timestamp));
        cursor = mDao.readAllVendorData();
        assertEquals(4, cursor.getCount());
        cursor.close();
        assertArrayEquals(new byte[10], mDao.readSingleVendorDataRow("key2"));
        assertArrayEquals(new byte[111111], mDao.readSingleVendorDataRow("large"));

        File dir = new File(OnDevicePersonalizationVendorDataDao.getFileDir(
                OnDevicePersonalizationVendorDataDao.getTableName(TEST_OWNER, TEST_CERT_DIGEST),
                mContext.getFilesDir()));
        assertTrue(dir.isDirectory());
        assertTrue(new File(dir, "large_" + timestamp).exists());
    }

    @Test
    public void testReadSingleRow() {
        long timestamp = System.currentTimeMillis();
        addTestData(timestamp);

        assertArrayEquals(new byte[10], mDao.readSingleVendorDataRow("key"));
        assertArrayEquals(new byte[10], mDao.readSingleVendorDataRow("key2"));
        assertArrayEquals(new byte[111111], mDao.readSingleVendorDataRow("large"));
        assertArrayEquals(new byte[111111], mDao.readSingleVendorDataRow("large2"));
        assertArrayEquals(new byte[5555555], mDao.readSingleVendorDataRow("xlarge"));
    }

    @Test
    public void testGetAllVendorKeys() {
        addTestData(System.currentTimeMillis());
        Set<String> keys = mDao.readAllVendorDataKeys();
        Set<String> expectedKeys = new HashSet<>();
        expectedKeys.add("key");
        expectedKeys.add("key2");
        expectedKeys.add("large");
        expectedKeys.add("large2");
        expectedKeys.add("xlarge");
        assertEquals(expectedKeys, keys);
    }

    @Test
    public void testFailReadSyncToken() {
        long timestampFromDB = mDao.getSyncToken();
        assertEquals(-1L, timestampFromDB);
    }

    @Test
    public void testInsertNewSyncToken() {
        SQLiteDatabase db = OnDevicePersonalizationDbHelper.getInstanceForTest(
                mContext).getWritableDatabase();
        OnDevicePersonalizationVendorDataDao.insertNewSyncToken(db, TEST_OWNER, TEST_CERT_DIGEST,
                0);
        long timestampFromDB = mDao.getSyncToken();
        assertEquals(0L, timestampFromDB);
        // Insert does nothing on conflict.
        OnDevicePersonalizationVendorDataDao.insertNewSyncToken(db, TEST_OWNER, TEST_CERT_DIGEST,
                100);
        timestampFromDB = mDao.getSyncToken();
        assertEquals(0L, timestampFromDB);
    }

    @Test
    public void testGetVendors() {
        addTestData(System.currentTimeMillis());
        List<Map.Entry<String, String>> vendors = OnDevicePersonalizationVendorDataDao.getVendors(
                mContext);
        assertEquals(1, vendors.size());
        assertEquals(DbUtils.toTableValue(TEST_OWNER), vendors.get(0).getKey());
        assertEquals(TEST_CERT_DIGEST, vendors.get(0).getValue());
        assertEquals(new AbstractMap.SimpleEntry<>(
                DbUtils.toTableValue(TEST_OWNER), TEST_CERT_DIGEST), vendors.get(0));
    }

    @Test
    public void testGetNoVendors() {
        List<Map.Entry<String, String>> vendors = OnDevicePersonalizationVendorDataDao.getVendors(
                mContext);
        assertEquals(0, vendors.size());
    }

    @Test
    public void testDeleteVendor() {
        File dir = new File(OnDevicePersonalizationVendorDataDao.getFileDir(
                OnDevicePersonalizationVendorDataDao.getTableName(TEST_OWNER, TEST_CERT_DIGEST),
                mContext.getFilesDir()));
        File localDir = new File(OnDevicePersonalizationLocalDataDao.getFileDir(
                OnDevicePersonalizationLocalDataDao.getTableName(TEST_OWNER, TEST_CERT_DIGEST),
                mContext.getFilesDir()));

        addTestData(System.currentTimeMillis());
        assertTrue(dir.isDirectory());
        assertTrue(localDir.isDirectory());
        OnDevicePersonalizationVendorDataDao.deleteVendorData(mContext, TEST_OWNER,
                TEST_CERT_DIGEST);
        List<Map.Entry<String, String>> vendors = OnDevicePersonalizationVendorDataDao.getVendors(
                mContext);
        assertEquals(0, vendors.size());
        long timestampFromDB = mDao.getSyncToken();
        assertEquals(-1L, timestampFromDB);
        assertFalse(dir.exists());
        assertFalse(localDir.exists());
    }

    @Test
    public void testGetInstance() {
        ComponentName owner1 = new ComponentName("owner1", "cls1");
        OnDevicePersonalizationVendorDataDao instance1Owner1 =
                OnDevicePersonalizationVendorDataDao.getInstance(mContext, owner1,
                        TEST_CERT_DIGEST);
        OnDevicePersonalizationVendorDataDao instance2Owner1 =
                OnDevicePersonalizationVendorDataDao.getInstance(mContext, owner1,
                        TEST_CERT_DIGEST);
        assertEquals(instance1Owner1, instance2Owner1);
        ComponentName owner2 = new ComponentName("owner2", "cls2");
        OnDevicePersonalizationVendorDataDao instance1Owner2 =
                OnDevicePersonalizationVendorDataDao.getInstance(mContext, owner2,
                        TEST_CERT_DIGEST);
        assertNotEquals(instance1Owner1, instance1Owner2);
    }

    @After
    public void cleanup() {
        OnDevicePersonalizationDbHelper dbHelper =
                OnDevicePersonalizationDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();

        File vendorDir = new File(mContext.getFilesDir(), "VendorData");
        File localDir = new File(mContext.getFilesDir(), "LocalData");
        FileUtils.deleteDirectory(vendorDir);
        FileUtils.deleteDirectory(localDir);
    }

    private void addTestData(long timestamp) {
        List<VendorData> dataList = new ArrayList<>();
        dataList.add(new VendorData.Builder().setKey("key").setData(new byte[10]).build());
        dataList.add(new VendorData.Builder().setKey("key2").setData(new byte[10]).build());
        dataList.add(new VendorData.Builder().setKey("large").setData(new byte[111111]).build());
        dataList.add(new VendorData.Builder().setKey("large2").setData(new byte[111111]).build());
        dataList.add(new VendorData.Builder().setKey("xlarge").setData(new byte[5555555]).build());

        List<String> retainedKeys = new ArrayList<>();
        retainedKeys.add("key");
        retainedKeys.add("key2");
        retainedKeys.add("large");
        retainedKeys.add("large2");
        retainedKeys.add("xlarge");
        assertTrue(mDao.batchUpdateOrInsertVendorDataTransaction(dataList, retainedKeys,
                timestamp));
    }
}
