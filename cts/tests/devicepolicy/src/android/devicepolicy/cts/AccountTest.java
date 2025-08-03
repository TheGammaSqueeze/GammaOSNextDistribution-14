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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accounts.AccountReference;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class AccountTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String FEATURE_ALLOW =
            "android.account.DEVICE_OR_PROFILE_OWNER_ALLOWED";
    private static final String FEATURE_DISALLOW =
            "android.account.DEVICE_OR_PROFILE_OWNER_DISALLOWED";

    @Test
    public void noIncompatibleAccounts() {
        Set<AccountReference> incompatibleAccounts = TestApis.accounts().allOnDevice().stream()
                .filter(a -> a.hasFeature(FEATURE_DISALLOW) || !a.hasFeature(FEATURE_ALLOW))
                .collect(Collectors.toSet());

        assertWithMessage("Preconfigured accounts must have the feature "
                + FEATURE_ALLOW + " (and not have the feature " + FEATURE_DISALLOW
                + ") to enable compatibility with Android Enterprise")
                .that(incompatibleAccounts).isEmpty();
    }
}
