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

package android.content.pm.cts;

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_INSTALL_UNKNOWN_SOURCES;

import static com.google.common.truth.Truth.assertThat;

import android.os.Process;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeNonSdkSandbox;
import android.util.Log;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Test user restriction commands of PackageManagerShellCommand.
 */
@RunWith(BedsteadJUnit4.class)
@AppModeFull
@AppModeNonSdkSandbox
public class PackageManagerShellCommandUserRestrictionTest {

    private static final String TAG = "PackageManagerShellCommandUserRestrictionTest";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    /**
     * Test get-user-restriction command.
     */
    @Test
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_UNKNOWN_SOURCES)
    public void testGetUserRestriction() throws Exception {
        assertThat(hasUserRestriction(Process.myUserHandle().getIdentifier(),
                DISALLOW_INSTALL_UNKNOWN_SOURCES)).isTrue();

        assertThat(hasUserRestriction(100 /* userId */,
                DISALLOW_INSTALL_UNKNOWN_SOURCES)).isFalse();
    }

    /**
     * Test get-user-restriction with --all command.
     */
    @Test
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_UNKNOWN_SOURCES)
    public void testGetUserAllRestrictions() throws Exception {
        final Set<String> restrictions = getUserAllRestrictions(
                Process.myUserHandle().getIdentifier());
        Log.d(TAG, "testGetUserAllRestrictions restrictions=" + restrictions.toString());

        assertThat(restrictions).contains(DISALLOW_INSTALL_UNKNOWN_SOURCES + "=true");

        final Set<String> noRestrictions = getUserAllRestrictions(100 /* userId */);
        Log.d(TAG, "testGetUserAllRestrictions noRestrictions=" + noRestrictions.toString());

        assertThat(noRestrictions).doesNotContain(DISALLOW_INSTALL_UNKNOWN_SOURCES + "=true");
    }

    private boolean hasUserRestriction(int userId, String restriction) throws Exception {
        String output = SystemUtil.runShellCommand(
                String.format("pm get-user-restriction --user %d %s", userId, restriction)).trim();
        Log.d(TAG, "hasUserRestriction for userId=" + userId + " and restriction=" + restriction
                + ", output=" + output);

        return output.contains("true");
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    private Set<String> getUserAllRestrictions(int userId) throws Exception {
        String output = SystemUtil.runShellCommand(
                String.format("pm get-user-restriction --user %d --all", userId)).trim();
        Log.d(TAG, "getUserAllRestrictions for userId=" + userId + ", output=" + output);
        int restrictionsPosition = output.indexOf("Bundle[{");
        if (restrictionsPosition >= 0 && output.endsWith("}]")) {
            try {
                String[] split = output.substring(restrictionsPosition + 8,
                        output.length() - 2).split("\\s*,\\s*");
                return new LinkedHashSet<>(Arrays.asList(split));
            } catch (Exception e) {
                return Collections.emptySet();
            }
        }
        return Collections.emptySet();
    }
}
