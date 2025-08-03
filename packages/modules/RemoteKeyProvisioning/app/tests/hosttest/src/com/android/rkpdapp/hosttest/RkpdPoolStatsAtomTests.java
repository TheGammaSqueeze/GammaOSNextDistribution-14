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
package com.android.rkpdapp.hosttest;

import static com.google.common.truth.Truth.assertThat;

import com.android.os.rkpd.RkpdExtensionAtoms;
import com.android.os.rkpd.RkpdPoolStats;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class RkpdPoolStatsAtomTests extends AtomsHostTest {
    private static final String DEFAULT_IRPC =
            "android.hardware.security.keymint.IRemotelyProvisionedComponent/default";

    public RkpdPoolStatsAtomTests() {
        super(RkpdExtensionAtoms.RKPD_POOL_STATS_FIELD_NUMBER);
    }

    @Test
    public void testKeyConsumed() throws Exception {
        runIntegrationTest("provisionThenUseKeyThenProvision", "RkpdHostTestHelperTests");
        List<RkpdPoolStats> atoms = getAtoms(RkpdExtensionAtoms.rkpdPoolStats);
        assertThat(atoms).hasSize(2);

        // Total count is controlled by the server.
        final int totalKeyCount = atoms.get(0).getTotal();
        assertThat(totalKeyCount).isAtLeast(1);

        assertThat(atoms.get(0).getRemotelyProvisionedComponent()).isEqualTo(DEFAULT_IRPC);
        assertThat(atoms.get(0).getExpiring()).isEqualTo(0);
        assertThat(atoms.get(0).getUnassigned()).isEqualTo(totalKeyCount);

        assertThat(atoms.get(1).getRemotelyProvisionedComponent()).isEqualTo(DEFAULT_IRPC);
        assertThat(atoms.get(1).getTotal()).isEqualTo(totalKeyCount);
        assertThat(atoms.get(1).getExpiring()).isEqualTo(0);
        assertThat(atoms.get(1).getUnassigned()).isEqualTo(totalKeyCount - 1);
    }

    @Test
    public void testExpiryTracking() throws Exception {
        runIntegrationTest("provisionThenExpireThenProvisionAgain", "RkpdHostTestHelperTests");
        List<RkpdPoolStats> atoms = getAtoms(RkpdExtensionAtoms.rkpdPoolStats);
        assertThat(atoms).hasSize(2);

        // Total count is controlled by the server.
        final int totalKeyCount = atoms.get(0).getTotal();
        assertThat(totalKeyCount).isAtLeast(1);

        assertThat(atoms.get(0).getRemotelyProvisionedComponent()).isEqualTo(DEFAULT_IRPC);
        assertThat(atoms.get(0).getExpiring()).isEqualTo(0);
        assertThat(atoms.get(0).getUnassigned()).isEqualTo(totalKeyCount);

        assertThat(atoms.get(1).getRemotelyProvisionedComponent()).isEqualTo(DEFAULT_IRPC);
        assertThat(atoms.get(1).getTotal()).isEqualTo(totalKeyCount - 1);
        assertThat(atoms.get(1).getExpiring()).isEqualTo(2);
        assertThat(atoms.get(1).getUnassigned()).isEqualTo(totalKeyCount - 1);
    }
}
