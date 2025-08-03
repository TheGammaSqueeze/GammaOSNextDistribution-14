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

package android.devicepolicy.cts;

import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.policies.AffiliationIds;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.remotedpc.RemotePolicyManager;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.testng.Assert;

import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class AffiliationIdsTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private ComponentName mAdmin;
    private RemoteDevicePolicyManager mDpm;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mAdmin = dpc.componentName();
        mDpm = dpc.devicePolicyManager();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AffiliationIds.class)
    public void setAffiliationIds_idTooLong_throws() {
        // String too long for id, cannot be serialized correctly
        String badId = new String(new char[100000]).replace('\0', 'A');
        Assert.assertThrows(IllegalArgumentException.class,
                () -> mDpm.setAffiliationIds(mAdmin, Set.of(badId)));
    }
}
