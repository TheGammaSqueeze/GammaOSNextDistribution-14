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

package android.service.persistentdata;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class PersistentDataBlockManagerTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final PersistentDataBlockManager sPersistentDataBlockManager =
            sContext.getSystemService(PersistentDataBlockManager.class);

    @EnsureHasPermission(android.Manifest.permission.ACCESS_PDB_STATE)
    @Test
    public void getPersistentDataPackageName_returnsNonNullResult() {
        if (sPersistentDataBlockManager == null) {
            return;
        }
        assertThat(sPersistentDataBlockManager.getPersistentDataPackageName()).isNotNull();
    }

    @EnsureDoesNotHavePermission(android.Manifest.permission.ACCESS_PDB_STATE)
    @Test
    public void getPersistentDataPackageName_withoutPermission_throwsException() {
        if (sPersistentDataBlockManager == null) {
            return;
        }
        assertThrows(SecurityException.class,
                sPersistentDataBlockManager::getPersistentDataPackageName);
    }
}
