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

package com.android.bluetooth.le_audio;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothLeAudio;
import android.os.ParcelUuid;
import android.util.Pair;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.btservice.ServiceFactory;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.UUID;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ContentControlIdKeeperTest {
    @Mock
    ServiceFactory mServiceFactoryMock;
    @Mock
    LeAudioService mLeAudioServiceMock;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        doReturn(mLeAudioServiceMock).when(mServiceFactoryMock).getLeAudioService();
        ContentControlIdKeeper.initForTesting(mServiceFactoryMock);
    }

    @After
    public void tearDown() throws Exception {
        ContentControlIdKeeper.initForTesting(null);
    }

    public int testCcidAcquire(ParcelUuid uuid, int context, int expectedListSize) {
        int ccid = ContentControlIdKeeper.acquireCcid(uuid, context);
        Assert.assertNotEquals(
                ccid,
                ContentControlIdKeeper.CCID_INVALID);

        verify(mLeAudioServiceMock).setCcidInformation(eq(uuid),
                        eq(ccid), eq(context));
        Map<ParcelUuid, Pair<Integer, Integer>> uuidToCcidContextPair =
                ContentControlIdKeeper.getUuidToCcidContextPairMap();
        Assert.assertEquals(expectedListSize, uuidToCcidContextPair.size());
        Assert.assertTrue(uuidToCcidContextPair.containsKey(uuid));
        Assert.assertEquals(ccid, (long)uuidToCcidContextPair.get(uuid).first);
        Assert.assertEquals(context, (long)uuidToCcidContextPair.get(uuid).second);

        return ccid;
    }

    public void testCcidRelease(ParcelUuid uuid, int ccid, int expectedListSize) {
        Map<ParcelUuid, Pair<Integer, Integer>> uuidToCcidContextPair =
                        ContentControlIdKeeper.getUuidToCcidContextPairMap();
        Assert.assertTrue(uuidToCcidContextPair.containsKey(uuid));

        ContentControlIdKeeper.releaseCcid(ccid);
        uuidToCcidContextPair = ContentControlIdKeeper.getUuidToCcidContextPairMap();
        Assert.assertFalse(uuidToCcidContextPair.containsKey(uuid));

        verify(mLeAudioServiceMock).setCcidInformation(eq(uuid),
                eq(ccid), eq(0));

        Assert.assertEquals(expectedListSize, uuidToCcidContextPair.size());
    }

    @Test
    public void testAcquireReleaseCcid() {
        ParcelUuid uuid_one = new ParcelUuid(UUID.randomUUID());
        ParcelUuid uuid_two = new ParcelUuid(UUID.randomUUID());

        int ccid_one = testCcidAcquire(uuid_one, BluetoothLeAudio.CONTEXT_TYPE_MEDIA, 1);
        int ccid_two = testCcidAcquire(uuid_two, BluetoothLeAudio.CONTEXT_TYPE_RINGTONE, 2);
        Assert.assertNotEquals(ccid_one, ccid_two);

        testCcidRelease(uuid_one, ccid_one, 1);
        testCcidRelease(uuid_two, ccid_two, 0);
    }

    @Test
    public void testAcquireReleaseCcidForCompoundContext() {
        ParcelUuid uuid = new ParcelUuid(UUID.randomUUID());
        int ccid = testCcidAcquire(uuid,
                BluetoothLeAudio.CONTEXT_TYPE_MEDIA | BluetoothLeAudio.CONTEXT_TYPE_RINGTONE, 1);
        testCcidRelease(uuid, ccid, 0);
    }

    @Test
    public void testAcquireInvalidContext() {
        ParcelUuid uuid = new ParcelUuid(UUID.randomUUID());

        int ccid = ContentControlIdKeeper.acquireCcid(uuid, 0);
        Assert.assertEquals(ccid, ContentControlIdKeeper.CCID_INVALID);

        verify(mLeAudioServiceMock,
                times(0)).setCcidInformation(any(ParcelUuid.class), any(int.class), any(int.class));
        Map<ParcelUuid, Pair<Integer, Integer>> uuidToCcidContextPair =
                ContentControlIdKeeper.getUuidToCcidContextPairMap();
        Assert.assertEquals(0, uuidToCcidContextPair.size());
    }

    @Test
    public void testAcquireContextMoreThanOnce() {
        ParcelUuid uuid = new ParcelUuid(UUID.randomUUID());

        int ccid_one = testCcidAcquire(uuid, BluetoothLeAudio.CONTEXT_TYPE_MEDIA, 1);
        int ccid_two = testCcidAcquire(uuid, BluetoothLeAudio.CONTEXT_TYPE_RINGTONE, 1);

        // This is implementation specific but verifies that the previous CCID was recycled
        Assert.assertEquals(ccid_one, ccid_two);
    }

}
