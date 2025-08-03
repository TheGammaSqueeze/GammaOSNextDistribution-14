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

package com.android.mms.service.metrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import androidx.annotation.Nullable;

import com.android.mms.IncomingMms;
import com.android.mms.OutgoingMms;
import com.android.mms.PersistMmsAtoms;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PersistMmsAtomsStorageTest {
    private static final String TEST_FILE = "PersistMmsAtomsStorageTest.pb";
    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();
    private File mTestFile;
    private static final long START_TIME_MILLIS = 2000L;
    private static final int CARRIER1_ID = 1435;
    private static final int CARRIER2_ID = 1187;
    private TestablePersistMmsAtomsStorage mTestablePersistMmsAtomsStorage;
    // IncomingMms
    private List<IncomingMms> mIncomingMmsList;
    private IncomingMms mIncomingMms1Proto;
    private IncomingMms mIncomingMms2Proto;
    // OutgoingMms
    private List<OutgoingMms> mOutgoingMmsList;
    private OutgoingMms mOutgoingMms1Proto;
    private OutgoingMms mOutgoingMms2Proto;
    // Mocked classes
    private Context mContext;
    private PackageManager mPackageManager;
    private FileOutputStream mTestFileOutputStream;
    // Comparator to compare proto objects
    private static final Comparator<Object> sProtoComparator =
            new Comparator<Object>() {
                @Override
                public int compare(Object o1, Object o2) {
                    if (o1 == o2) {
                        return 0;
                    }
                    if (o1 == null) {
                        return -1;
                    }
                    if (o2 == null) {
                        return 1;
                    }
                    assertEquals(o1.getClass(), o2.getClass());
                    return o1.toString().compareTo(o2.toString());
                }
            };


    @Before
    public void setUp() throws Exception {
        mTestFileOutputStream = mock(FileOutputStream.class);
        mContext = mock(Context.class);
        mPackageManager = mock(PackageManager.class);
        makeTestData();

        // By default, test loading with real file IO and saving with mocks.
        mTestFile = mFolder.newFile(TEST_FILE);
        doReturn(false).when(mPackageManager).
                hasSystemFeature(PackageManager.FEATURE_RAM_LOW);
        doReturn(mPackageManager).when(mContext).getPackageManager();
        doReturn(mTestFileOutputStream).when(mContext).openFileOutput(anyString(), anyInt());
        doReturn(mTestFile).when(mContext).getFileStreamPath(anyString());
    }

    @After
    public void tearDown() {
        mTestFile.delete();
        mTestFile = null;
        mFolder = null;
        mIncomingMmsList = null;
        mIncomingMms1Proto = null;
        mIncomingMms2Proto = null;
        mOutgoingMmsList = null;
        mOutgoingMms1Proto = null;
        mOutgoingMms2Proto = null;
        mTestablePersistMmsAtomsStorage = null;
        mTestFileOutputStream = null;
        mPackageManager = null;
        mContext = null;
    }

    @Test
    public void loadAtoms_fileNotExist() {
        mTestFile.delete();
        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // No exception should be thrown, storage should be empty, pull time should be start time.
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertStorageIsEmptyForAllAtoms();
    }

    @Test
    public void loadAtoms_unreadable() throws Exception {
        createEmptyTestFile();
        mTestFile.setReadable(false);

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // No exception should be thrown, storage should be empty, pull time should be start time.
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertStorageIsEmptyForAllAtoms();
    }

    @Test
    public void loadAtoms_emptyProto() throws Exception {
        createEmptyTestFile();

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // No exception should be thrown, storage should be empty, pull time should be start time.
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertStorageIsEmptyForAllAtoms();
    }

    @Test
    public void loadAtoms_malformedFile() throws Exception {
        FileOutputStream stream = new FileOutputStream(mTestFile);
        stream.write("This is not a proto file.".getBytes(StandardCharsets.UTF_8));
        stream.close();

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // No exception should be thrown, storage should be empty, pull time should be start time.
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertStorageIsEmptyForAllAtoms();
    }

    @Test
    public void loadAtoms_pullTimeMissing() throws Exception {
        // Create test file with lastPullTimeMillis = 0L, i.e. default/unknown.
        createTestFile(0L);

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // No exception should be thrown, storage should be match, pull time should be start time.
        assertAllPullTimestampEquals(START_TIME_MILLIS);
        assertProtoListEqualsIgnoringOrder(mIncomingMmsList,
                mTestablePersistMmsAtomsStorage.getIncomingMms(0L));
        assertProtoListEqualsIgnoringOrder(mOutgoingMmsList,
                mTestablePersistMmsAtomsStorage.getOutgoingMms(0L));
    }

    @Test
    public void loadAtoms_validContents() throws Exception {
        createTestFile(100L);

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);

        // No exception should be thrown, storage and pull time should match.
        assertAllPullTimestampEquals(100L);
        assertProtoListEqualsIgnoringOrder(mIncomingMmsList,
                mTestablePersistMmsAtomsStorage.getIncomingMms(0L));
        assertProtoListEqualsIgnoringOrder(mOutgoingMmsList,
                mTestablePersistMmsAtomsStorage.getOutgoingMms(0L));
    }

    @Test
    public void addIncomingMms_emptyProto() throws Exception {
        createEmptyTestFile();

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.addIncomingMms(mIncomingMms1Proto);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // IncomingMms should be added successfully, there should not be any OutgoingMms,
        // changes should be saved.
        verifyCurrentStateSavedToFileOnce();
        assertProtoListIsEmpty(mTestablePersistMmsAtomsStorage.getOutgoingMms(0L));
        List<IncomingMms> expectedIncomingMmsList = new ArrayList<>();
        expectedIncomingMmsList.add(mIncomingMms1Proto);
        assertProtoListEquals(expectedIncomingMmsList,
                mTestablePersistMmsAtomsStorage.getIncomingMms(0L));
    }

    @Test
    public void addIncomingMms_withExistingEntries() throws Exception {
        createEmptyTestFile();

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.addIncomingMms(mIncomingMms1Proto);
        mTestablePersistMmsAtomsStorage.addIncomingMms(mIncomingMms2Proto);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // IncomingMms should be added successfully.
        verifyCurrentStateSavedToFileOnce();
        List<IncomingMms> expectedIncomingMmsList = Arrays.asList(mIncomingMms1Proto,
                mIncomingMms2Proto);
        assertProtoListEqualsIgnoringOrder(expectedIncomingMmsList,
                mTestablePersistMmsAtomsStorage.getIncomingMms(0L));
    }

    @Test
    public void addIncomingMms_updateExistingEntries() throws Exception {
        createTestFile(START_TIME_MILLIS);

        // Add copy of mIncomingMms1Proto.
        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.addIncomingMms(copyOf(mIncomingMms1Proto));
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // mIncomingMms1Proto's mms count should be increased by 1 and avgIntervalMillis
        // should be updated correctly.
        verifyCurrentStateSavedToFileOnce();
        IncomingMms newIncomingMm1Proto = copyOf(mIncomingMms1Proto);
        newIncomingMm1Proto = newIncomingMm1Proto.toBuilder()
                .setMmsCount(2)
                .setAvgIntervalMillis(mIncomingMms1Proto.getAvgIntervalMillis())
                .build();
        List<IncomingMms> expectedIncomingMmsList = Arrays.asList(newIncomingMm1Proto,
                mIncomingMms2Proto);
        assertProtoListEqualsIgnoringOrder(expectedIncomingMmsList,
                mTestablePersistMmsAtomsStorage.getIncomingMms(0L));
    }

    @Test
    public void addIncomingMms_tooManyEntries() throws Exception {
        createEmptyTestFile();

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        // Add 26 mms whereas max size is 25.
        IncomingMms mms = IncomingMms.newBuilder()
                .setRoaming(ServiceState.ROAMING_TYPE_DOMESTIC)
                .setSimSlotIndex(0)
                .setIsMultiSim(false)
                .setIsEsim(false)
                .setCarrierId(CARRIER1_ID)
                .setMmsCount(1)
                .setAvgIntervalMillis(500L)
                .setRetryId(0)
                .setHandledByCarrierApp(false)
                .build();
        for (int ratType = 0; ratType < 5; ratType++) {
            for (int resultType = 0; resultType < 5; resultType++) {
                mms = mms.toBuilder().setRat(ratType).setResult(resultType).build();
                mTestablePersistMmsAtomsStorage.addIncomingMms(mms);
                mTestablePersistMmsAtomsStorage.incTimeMillis(100L);
            }
        }

        // Add 26th mms 5 times
        IncomingMms lastMms = copyOf(mms);
        lastMms = lastMms.toBuilder().setRat(6).setResult(6).build();
        for (int i = 0; i < 5; i++) {
            mTestablePersistMmsAtomsStorage.addIncomingMms(lastMms);
            mTestablePersistMmsAtomsStorage.incTimeMillis(100L);
        }

        // Last mms should be present in storage.
        assertHasMmsAndCountAvg(mTestablePersistMmsAtomsStorage.getIncomingMms(0L),
                lastMms, 5L, lastMms.getAvgIntervalMillis());
    }

    @Test
    public void getIncomingMms_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        // Pull interval less than minimum.
        mTestablePersistMmsAtomsStorage.incTimeMillis(50L);

        List<IncomingMms> incomingMmsList = mTestablePersistMmsAtomsStorage
                .getIncomingMms(100L);
        // Should be denied.
        assertNull(incomingMmsList);
    }

    @Test
    public void getIncomingMms_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);
        List<IncomingMms> incomingMmsList1 = mTestablePersistMmsAtomsStorage
                .getIncomingMms(50L);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);
        List<IncomingMms> incomingMmsList2 = mTestablePersistMmsAtomsStorage
                .getIncomingMms(50L);

        // First set of results should be equal to file contents.
        List<IncomingMms> expectedIncomingMmsList = Arrays.asList(mIncomingMms1Proto,
                mIncomingMms2Proto);
        assertProtoListEqualsIgnoringOrder(expectedIncomingMmsList, incomingMmsList1);
        // Second set of results should be empty.
        expectedIncomingMmsList = new ArrayList<>();
        assertProtoListEqualsIgnoringOrder(expectedIncomingMmsList, incomingMmsList2);
        // Corresponding pull timestamp should be updated and saved.
        assertEquals(START_TIME_MILLIS + 200L, mTestablePersistMmsAtomsStorage
                .getAtomsProto().getIncomingMmsPullTimestampMillis());
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).getIncomingMmsPullTimestampMillis());
        assertEquals(START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).getIncomingMmsPullTimestampMillis());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void addOutgoingMms_emptyProto() throws Exception {
        createEmptyTestFile();

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.addOutgoingMms(mOutgoingMms1Proto);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // OutgoingMms should be added successfully, there should not be any IncomingMms,
        // changes should be saved.
        verifyCurrentStateSavedToFileOnce();
        assertProtoListIsEmpty(mTestablePersistMmsAtomsStorage.getIncomingMms(0L));
        List<OutgoingMms> expectedOutgoingMmsList = new ArrayList<>();
        expectedOutgoingMmsList.add(mOutgoingMms1Proto);
        assertProtoListEquals(expectedOutgoingMmsList,
                mTestablePersistMmsAtomsStorage.getOutgoingMms(0L));
    }

    @Test
    public void addOutgoingMms_withExistingEntries() throws Exception {
        createEmptyTestFile();

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.addOutgoingMms(mOutgoingMms1Proto);
        mTestablePersistMmsAtomsStorage.addOutgoingMms(mOutgoingMms2Proto);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // OutgoingMms should be added successfully
        verifyCurrentStateSavedToFileOnce();
        List<OutgoingMms> expectedOutgoingMmsList = Arrays.asList(mOutgoingMms1Proto,
                mOutgoingMms2Proto);
        assertProtoListEqualsIgnoringOrder(expectedOutgoingMmsList,
                mTestablePersistMmsAtomsStorage.getOutgoingMms(0L));
    }

    @Test
    public void addOutgoingMms_updateExistingEntries() throws Exception {
        createTestFile(START_TIME_MILLIS);

        // Add copy of mOutgoingMms1Proto
        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.addOutgoingMms(copyOf(mOutgoingMms1Proto));
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);

        // mOutgoingMms1Proto's mms count should be increased by 1 and avgIntervalMillis
        // should be updated correctly.
        verifyCurrentStateSavedToFileOnce();
        OutgoingMms newOutgoingMm1Proto = copyOf(mOutgoingMms1Proto);
        newOutgoingMm1Proto = newOutgoingMm1Proto.toBuilder()
                .setMmsCount(2)
                .setAvgIntervalMillis(mOutgoingMms1Proto.getAvgIntervalMillis())
                .build();
        List<OutgoingMms> expectedOutgoingMmsList = Arrays.asList(newOutgoingMm1Proto,
                mOutgoingMms2Proto);
        assertProtoListEqualsIgnoringOrder(expectedOutgoingMmsList,
                mTestablePersistMmsAtomsStorage.getOutgoingMms(0L));
    }

    @Test
    public void addOutgoingMms_tooManyEntries() throws Exception {
        createEmptyTestFile();

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        // Add 26 mms whereas max size is 25.
        OutgoingMms mms = OutgoingMms.newBuilder()
                .setRoaming(ServiceState.ROAMING_TYPE_DOMESTIC)
                .setSimSlotIndex(0)
                .setIsMultiSim(false)
                .setIsEsim(false)
                .setCarrierId(CARRIER1_ID)
                .setMmsCount(1)
                .setAvgIntervalMillis(500L)
                .setIsFromDefaultApp(true)
                .setHandledByCarrierApp(false)
                .setRetryId(0)
                .build();
        for (int ratType = 0; ratType < 5; ratType++) {
            for (int resultType = 0; resultType < 5; resultType++) {
                mms = mms.toBuilder().setRat(ratType).setResult(resultType).build();
                mTestablePersistMmsAtomsStorage.addOutgoingMms(mms);
                mTestablePersistMmsAtomsStorage.incTimeMillis(100L);
            }
        }

        // Add 26th mms 5 times
        OutgoingMms lastMms = copyOf(mms);
        lastMms = lastMms.toBuilder().setRat(6).setResult(6).build();
        for (int i = 0; i < 5; i++) {
            mTestablePersistMmsAtomsStorage.addOutgoingMms(lastMms);
            mTestablePersistMmsAtomsStorage.incTimeMillis(100L);
        }

        // Last mms should be present in storage.
        assertHasMmsAndCountAvg(mTestablePersistMmsAtomsStorage.getOutgoingMms(0L),
                lastMms, 5L, lastMms.getAvgIntervalMillis());
    }

    @Test
    public void getOutgoingMms_tooFrequent() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        // Pull interval less than minimum.
        mTestablePersistMmsAtomsStorage.incTimeMillis(50L);

        List<OutgoingMms> outgoingMmsList = mTestablePersistMmsAtomsStorage
                .getOutgoingMms(100L);
        // Should be denied.
        assertNull(outgoingMmsList);
    }

    @Test
    public void getOutgoingMms_withSavedAtoms() throws Exception {
        createTestFile(START_TIME_MILLIS);

        mTestablePersistMmsAtomsStorage = new TestablePersistMmsAtomsStorage(mContext);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);
        List<OutgoingMms> outgoingMmsList1 = mTestablePersistMmsAtomsStorage
                .getOutgoingMms(50L);
        mTestablePersistMmsAtomsStorage.incTimeMillis(100L);
        List<OutgoingMms> outgoingMmsList2 = mTestablePersistMmsAtomsStorage
                .getOutgoingMms(50L);

        // First set of results should be equal to file contents.
        List<OutgoingMms> expectedOutgoingMmsList = Arrays.asList(mOutgoingMms1Proto,
                mOutgoingMms2Proto);
        assertProtoListEqualsIgnoringOrder(expectedOutgoingMmsList, outgoingMmsList1);
        // Second set of results should be empty.
        expectedOutgoingMmsList = new ArrayList<>();
        assertProtoListEqualsIgnoringOrder(expectedOutgoingMmsList, outgoingMmsList2);
        // Corresponding pull timestamp should be updated and saved.
        assertEquals(START_TIME_MILLIS + 200L, mTestablePersistMmsAtomsStorage
                .getAtomsProto().getOutgoingMmsPullTimestampMillis());
        InOrder inOrder = inOrder(mTestFileOutputStream);
        assertEquals(START_TIME_MILLIS + 100L,
                getAtomsWritten(inOrder).getOutgoingMmsPullTimestampMillis());
        assertEquals(START_TIME_MILLIS + 200L,
                getAtomsWritten(inOrder).getOutgoingMmsPullTimestampMillis());
        inOrder.verifyNoMoreInteractions();
    }

    /** Utilities */

    private void assertAllPullTimestampEquals(long timestamp) {
        assertEquals(timestamp, mTestablePersistMmsAtomsStorage.getAtomsProto()
                .getIncomingMmsPullTimestampMillis());
        assertEquals(timestamp, mTestablePersistMmsAtomsStorage.getAtomsProto()
                .getOutgoingMmsPullTimestampMillis());
    }

    private void assertStorageIsEmptyForAllAtoms() {
        assertProtoListIsEmpty(mTestablePersistMmsAtomsStorage.getIncomingMms(0L));
        assertProtoListIsEmpty(mTestablePersistMmsAtomsStorage.getOutgoingMms(0L));
    }

    private static <T> void assertProtoListIsEmpty(@Nullable List<T> list) {
        assertNotNull(list);
        assertEquals(0, list.size());
    }

    private static <T> void assertProtoListEquals(@Nullable List<T> expected,
            @Nullable List<T> actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        String message =
                "Expected:\n" + expected.stream().map(Object::toString).collect(
                        Collectors.joining(", "))
                        + "\nGot:\n" + actual.stream().map(Object::toString).collect(
                        Collectors.joining(", "));
        assertEquals(message, expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertTrue(message, expected.get(i).equals(actual.get(i)));
        }
    }

    private static <T> void assertProtoListEqualsIgnoringOrder(@Nullable List<T> expected,
            @Nullable List<T> actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        expected = new ArrayList<>(expected);
        actual = new ArrayList<>(actual);
        Collections.sort(expected, sProtoComparator);
        Collections.sort(actual, sProtoComparator);
        assertProtoListEquals(expected, actual);
    }

    private static void assertHasMmsAndCountAvg(@Nullable List<IncomingMms> incomingMmsList,
            @Nullable IncomingMms expectedMms, long expectedCount, long expectedAvg) {
        assertNotNull(incomingMmsList);
        assertNotNull(expectedMms);
        long actualCount = -1;
        long actualAvg = -1;
        for (IncomingMms mms : incomingMmsList) {
            if (mms.getRat() == expectedMms.getRat()
                    && mms.getResult() == expectedMms.getResult()
                    && mms.getRoaming() == expectedMms.getRoaming()
                    && mms.getSimSlotIndex() == expectedMms.getSimSlotIndex()
                    && mms.getIsMultiSim() == expectedMms.getIsMultiSim()
                    && mms.getIsEsim() == expectedMms.getIsEsim()
                    && mms.getCarrierId() == expectedMms.getCarrierId()
                    && mms.getRetryId() == expectedMms.getRetryId()
                    && mms.getHandledByCarrierApp() == expectedMms.getHandledByCarrierApp()) {
                actualCount = mms.getMmsCount();
                actualAvg = mms.getAvgIntervalMillis();
            }
        }

        assertEquals(expectedCount, actualCount);
        assertEquals(expectedAvg, actualAvg);
    }

    private static void assertHasMmsAndCountAvg(@Nullable List<OutgoingMms> outgoingMmsList,
            @Nullable OutgoingMms expectedMms, long expectedCount, long expectedAvg) {
        assertNotNull(outgoingMmsList);
        assertNotNull(expectedMms);
        long actualCount = -1;
        long actualAvg = -1;
        for (OutgoingMms mms : outgoingMmsList) {
            if (mms.getRat() == expectedMms.getRat()
                    && mms.getResult() == expectedMms.getResult()
                    && mms.getRoaming() == expectedMms.getRoaming()
                    && mms.getSimSlotIndex() == expectedMms.getSimSlotIndex()
                    && mms.getIsMultiSim() == expectedMms.getIsMultiSim()
                    && mms.getIsEsim() == expectedMms.getIsEsim()
                    && mms.getCarrierId() == expectedMms.getCarrierId()
                    && mms.getIsFromDefaultApp() == expectedMms.getIsFromDefaultApp()
                    && mms.getRetryId() == expectedMms.getRetryId()
                    && mms.getHandledByCarrierApp() == expectedMms.getHandledByCarrierApp()) {
                actualCount = mms.getMmsCount();
                actualAvg = mms.getAvgIntervalMillis();
            }
        }

        assertEquals(expectedCount, actualCount);
        assertEquals(expectedAvg, actualAvg);
    }

    private void verifyCurrentStateSavedToFileOnce() throws Exception {
        InOrder inOrder = inOrder(mTestFileOutputStream);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write(eq(mTestablePersistMmsAtomsStorage.getAtomsProto().toByteArray()));
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        inOrder.verifyNoMoreInteractions();
    }

    private PersistMmsAtoms getAtomsWritten(@Nullable InOrder inOrder) throws Exception {
        if (inOrder == null) {
            inOrder = inOrder(mTestFileOutputStream);
        }
        ArgumentCaptor bytesCaptor = ArgumentCaptor.forClass(Object.class);
        inOrder.verify(mTestFileOutputStream, times(1))
                .write((byte[]) bytesCaptor.capture());
        PersistMmsAtoms savedAtoms = PersistMmsAtoms.parseFrom((byte[]) bytesCaptor.getValue());
        inOrder.verify(mTestFileOutputStream, times(1)).close();
        return savedAtoms;
    }

    private static IncomingMms copyOf(IncomingMms source) {
        return source.toBuilder().build();
    }

    private static OutgoingMms copyOf(OutgoingMms source) {
        return source.toBuilder().build();
    }

    private void makeTestData() {
        mIncomingMms1Proto = IncomingMms.newBuilder()
                .setRat(TelephonyManager.NETWORK_TYPE_LTE)
                .setResult(1)
                .setRoaming(ServiceState.ROAMING_TYPE_NOT_ROAMING)
                .setSimSlotIndex(0)
                .setIsMultiSim(true)
                .setIsEsim(false)
                .setCarrierId(CARRIER1_ID)
                .setAvgIntervalMillis(500L)
                .setMmsCount(1)
                .setRetryId(0)
                .setHandledByCarrierApp(false)
                .build();

        mIncomingMms2Proto = IncomingMms.newBuilder()
                .setRat(TelephonyManager.NETWORK_TYPE_LTE)
                .setResult(1)
                .setRoaming(ServiceState.ROAMING_TYPE_NOT_ROAMING)
                .setSimSlotIndex(1)
                .setIsMultiSim(false)
                .setIsEsim(false)
                .setCarrierId(CARRIER2_ID)
                .setAvgIntervalMillis(500L)
                .setMmsCount(1)
                .setRetryId(0)
                .setHandledByCarrierApp(false)
                .build();

        mIncomingMmsList = new ArrayList<>();
        mIncomingMmsList.add(mIncomingMms1Proto);
        mIncomingMmsList.add(mIncomingMms2Proto);

        mOutgoingMms1Proto = OutgoingMms.newBuilder()
                .setRat(0)
                .setResult(1)
                .setRoaming(0)
                .setSimSlotIndex(0)
                .setIsMultiSim(true)
                .setIsEsim(false)
                .setCarrierId(CARRIER1_ID)
                .setAvgIntervalMillis(500L)
                .setMmsCount(1)
                .setIsFromDefaultApp(true)
                .setRetryId(0)
                .setHandledByCarrierApp(false)
                .build();

        mOutgoingMms2Proto = OutgoingMms.newBuilder()
                .setRat(0)
                .setResult(1)
                .setRoaming(0)
                .setSimSlotIndex(0)
                .setIsMultiSim(false)
                .setIsEsim(false)
                .setCarrierId(CARRIER2_ID)
                .setAvgIntervalMillis(500L)
                .setMmsCount(1)
                .setIsFromDefaultApp(true)
                .setRetryId(0)
                .setHandledByCarrierApp(false)
                .build();

        mOutgoingMmsList = new ArrayList<>();
        mOutgoingMmsList.add(mOutgoingMms1Proto);
        mOutgoingMmsList.add(mOutgoingMms2Proto);
    }

    private void createEmptyTestFile() throws Exception {
        PersistMmsAtoms atoms = PersistMmsAtoms.newBuilder().build();
        FileOutputStream stream = new FileOutputStream(mTestFile);
        stream.write(atoms.toByteArray());
        stream.close();
    }

    private void createTestFile(long lastPullTimeMillis) throws Exception {
        PersistMmsAtoms atoms = PersistMmsAtoms.newBuilder()
                .setBuildFingerprint(Build.FINGERPRINT)
                .setIncomingMmsPullTimestampMillis(lastPullTimeMillis)
                .setOutgoingMmsPullTimestampMillis(lastPullTimeMillis)
                .addAllIncomingMms(mIncomingMmsList)
                .addAllOutgoingMms(mOutgoingMmsList)
                .build();

        FileOutputStream stream = new FileOutputStream(mTestFile);
        stream.write(atoms.toByteArray());
        stream.close();
    }

    private static class TestablePersistMmsAtomsStorage extends PersistMmsAtomsStorage {
        private long mTimeMillis = START_TIME_MILLIS;

        TestablePersistMmsAtomsStorage(Context context) {
            super(context);
            // Remove delay for saving to persistent storage during tests.
            mSaveImmediately = true;
        }

        @Override
        protected long getWallTimeMillis() {
            // NOTE: super class constructor will be executed before private field is set, which
            // gives the wrong start time (mTimeMillis will have its default value of 0L).
            return mTimeMillis == 0L ? START_TIME_MILLIS : mTimeMillis;
        }

        private void incTimeMillis(long timeMillis) {
            mTimeMillis += timeMillis;
        }

        private PersistMmsAtoms getAtomsProto() {
            // NOTE: unlike other methods in PersistAtomsStorage, this is not synchronized, but
            // should be fine since the test is single-threaded.
            return mPersistMmsAtoms;
        }
    }
}