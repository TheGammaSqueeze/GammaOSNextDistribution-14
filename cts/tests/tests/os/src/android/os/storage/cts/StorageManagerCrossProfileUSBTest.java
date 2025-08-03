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

package android.os.storage.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static java.util.stream.Collectors.joining;

import android.app.Instrumentation;
import android.content.Context;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.platform.test.annotations.AppModeFull;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Optional;


@RunWith(BedsteadJUnit4.class)
public class StorageManagerCrossProfileUSBTest {

    @ClassRule
    @Rule
    public static DeviceState sDeviceState = new DeviceState();

    private StorageManager mStorageManager;
    private Context mContext;
    private String mVolumeName;

    @Before
    public void setUp() throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        mContext = inst.getContext();
        mStorageManager = mContext.getSystemService(StorageManager.class);
        //Create a Virtual USB on the main system user
        mVolumeName = StorageManagerHelper.createUSBVirtualDisk();
    }

    @After
    public void teardown() throws Exception {
        StorageManagerHelper.removeVirtualDisk();
    }


    @Test
    @RequireRunOnWorkProfile
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testGetStorageVolumeUSBWorkProfile() throws Exception {
        List<StorageVolume> storageVolumes = mStorageManager.getStorageVolumes();
        Optional<StorageVolume> usbStorageVolume =
                storageVolumes.stream().filter(sv->sv.getPath().contains(mVolumeName)).findFirst();
        assertWithMessage("The USB storage volume: " + mVolumeName
                + " mounted on the main user is not present in "
                + storageVolumes.stream().map(StorageVolume::getPath).collect(joining("\n")))
                .that(usbStorageVolume.isPresent()).isTrue();
    }
}
