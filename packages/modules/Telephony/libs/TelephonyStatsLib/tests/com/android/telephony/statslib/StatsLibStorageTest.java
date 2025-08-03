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

package com.android.telephony.statslib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class StatsLibStorageTest {

    private StatsLibStorage mStorage;
    private ConcurrentHashMap<Integer, List<AtomsPushed>> mPushed;
    private ConcurrentHashMap<Integer, ConcurrentHashMap<String, AtomsPulled>> mPulled;
    private CountDownLatch mLatch;
    private StorageTestHandler mStorageTestHandler;

    @Before
    public void setUp() {
        Context context = spy(ApplicationProvider.getApplicationContext());
        mPushed = new ConcurrentHashMap<>();
        mPulled = new ConcurrentHashMap<>();
        HandlerThread handlerThread = new HandlerThread("StatsLibStorage");
        handlerThread.start();
        mStorageTestHandler = new StorageTestHandler(handlerThread.getLooper());
        mLatch = new CountDownLatch(1);
        mStorage = new StatsLibStorage(context, mPushed, mPulled, mStorageTestHandler);
    }

    private class StorageTestHandler extends Handler {
        StorageTestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void dispatchMessage(Message msg) {
            super.dispatchMessage(msg);
            mLatch.countDown();
        }
    }

    @After
    public void tearDown() {
        mStorage = null;
    }

    private void testPushedAppendAndPop(AtomsPushed first, AtomsPushed second, AtomsPushed third) {
        assertNotEquals(first, second);
        assertNotEquals(first, third);
        assertNotEquals(second, third);

        mStorage.appendPushedAtoms(first);
        assertEquals(1, mPushed.get(first.getStatsId()).size());

        mStorage.appendPushedAtoms(second);
        assertEquals(2, mPushed.get(first.getStatsId()).size());

        mStorage.appendPushedAtoms(third);
        assertEquals(3, mPushed.get(first.getStatsId()).size());

        AtomsPushed[] infos = mStorage.popPushedAtoms(first.getStatsId());
        assertEquals(0, mPushed.get(first.getStatsId()).size());

        assertEquals(first, infos[0]);
        assertEquals(second, infos[1]);
        assertEquals(third, infos[2]);
    }

    @Test
    public void testPushedAtomsBaseInfo() {
        AtomsPushedTestInfo first = new AtomsPushedTestInfo();
        AtomsPushedTestInfo second = new AtomsPushedTestInfo();
        AtomsPushedTestInfo third = new AtomsPushedTestInfo(3);

        first.setTestAtom(1);
        second.setTestAtom(2);

        assertEquals(1, first.getTestAtom());
        assertEquals(2, second.getTestAtom());

        testPushedAppendAndPop(first, second, third);
    }

    @Test
    public void testModifyInMiddleForAtomsTestInfo() {
        AtomsPushedTestInfo first = new AtomsPushedTestInfo();
        first.setTestAtom(1);
        mStorage.appendPushedAtoms(first);
        assertEquals(1, mPushed.get(first.getStatsId()).size());

        AtomsPushedTestInfo copyFirst = new AtomsPushedTestInfo(first);
        mStorage.appendPushedAtoms(copyFirst);
        assertEquals(2, mPushed.get(first.getStatsId()).size());

        // modified after append. storage should not be applied this.
        copyFirst.setTestAtom(10);

        AtomsPushed[] infos = mStorage.popPushedAtoms(first.getStatsId());
        assertEquals(0, mPushed.get(first.getStatsId()).size());

        assertEquals(first, infos[0]);
        assertNotEquals(copyFirst, infos[1]);
    }

    @Test
    public void testPulledAtomsBaseInfo() {
        AtomsPulledTestInfo first = new AtomsPulledTestInfo();
        AtomsPulledTestInfo second = new AtomsPulledTestInfo();
        AtomsPulledTestInfo third = new AtomsPulledTestInfo(2, 300);

        first.setType(1);
        second.setType(1);

        first.setCount(10);
        second.setCount(20);

        assertNotEquals(first, second);
        assertNotEquals(first, third);
        assertNotEquals(second, third);

        int statsId = first.getStatsId();
        assertEquals(statsId, second.getStatsId());
        assertEquals(statsId, third.getStatsId());

        int expectedLen = 1;
        mStorage.appendPulledAtoms(first);
        assertEquals(expectedLen, mPulled.get(statsId).size());

        if (!first.getDimension().equals(second.getDimension())) {
            expectedLen++;
        }
        mStorage.appendPulledAtoms(second);
        assertEquals(expectedLen, mPulled.get(statsId).size());

        if (!first.getDimension().equals(third.getDimension())) {
            expectedLen++;
        }
        mStorage.appendPulledAtoms(third);
        assertEquals(expectedLen, mPulled.get(statsId).size());

        AtomsPulled[] infos = mStorage.popPulledAtoms(statsId);
        assertEquals(0, mPulled.get(statsId).size());

        for (AtomsPulled info : infos) {
            if (!(info instanceof AtomsPulledTestInfo)) {
                Assert.fail();
                continue;
            }
            AtomsPulledTestInfo atomsPulledTestInfo = (AtomsPulledTestInfo) info;
            if (atomsPulledTestInfo.getDimension().equals(first.getDimension())) {
                assertEquals(atomsPulledTestInfo.getCount(), first.getCount() + second.getCount());
            } else {
                assertEquals(atomsPulledTestInfo.getCount(), third.getCount());
            }
        }
    }

    @Test
    public void testSerializableAtoms() throws InterruptedException {
        AtomsSerializablePulledTestInfo first = new AtomsSerializablePulledTestInfo();
        AtomsSerializablePulledTestInfo second = new AtomsSerializablePulledTestInfo();
        AtomsSerializablePulledTestInfo third = new AtomsSerializablePulledTestInfo(1, 10);

        first.setType(1);
        first.setCount(10);

        second.setType(1);
        second.setCount(10);

        // third.setType(1);
        // third.setCount(10);
        third.setTransient(5000);

        assertEquals(first, second);
        assertNotEquals(first, third);

        int statsId = first.getStatsId();
        assertEquals(statsId, second.getStatsId());
        assertEquals(statsId, third.getStatsId());

        int expectedLen = 1;
        // All dimensions are same in this test.
        mLatch = new CountDownLatch(1);
        mStorage.appendPulledAtoms(first);
        assertEquals(expectedLen, mPulled.get(statsId).size());
        mLatch.await(1, TimeUnit.SECONDS);

        mLatch = new CountDownLatch(1);
        mStorage.appendPulledAtoms(second);
        assertEquals(expectedLen, mPulled.get(statsId).size());
        mLatch.await(1, TimeUnit.SECONDS);

        mLatch = new CountDownLatch(1);
        mStorage.appendPulledAtoms(third);
        assertEquals(expectedLen, mPulled.get(statsId).size());
        mLatch.await(1, TimeUnit.SECONDS);

        mLatch = new CountDownLatch(1);
        mStorage.saveToFile(statsId);
        mLatch.await(1, TimeUnit.SECONDS);

        mLatch = new CountDownLatch(1);
        mStorage.init(statsId);
        mStorage.loadFromFile(statsId);
        mLatch.await(1, TimeUnit.SECONDS);

        AtomsPulled[] infos = mStorage.popPulledAtoms(statsId);
        assertEquals(0, mPulled.get(statsId).size());
        assertEquals(1, infos.length);

        assertTrue(infos[0] instanceof AtomsSerializablePulledTestInfo);
        AtomsSerializablePulledTestInfo result = (AtomsSerializablePulledTestInfo) infos[0];

        assertEquals(30, result.getCount());
        assertEquals(0, result.getTransient());
    }
}
