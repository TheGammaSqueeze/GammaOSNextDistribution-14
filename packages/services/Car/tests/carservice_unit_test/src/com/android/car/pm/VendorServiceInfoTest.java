/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.pm;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class VendorServiceInfoTest {
    private static final int MAX_RETRIES = 23;
    private static final String SERVICE_NAME = "com.andorid.car/.MyService";

    @Test
    public void emptyString() {
        assertThrows(IllegalArgumentException.class, () -> VendorServiceInfo.parse(""));
    }

    @Test
    public void multipleHashTags() {
        assertThrows(IllegalArgumentException.class,
                () -> VendorServiceInfo.parse(SERVICE_NAME + "#user=system#bind=bind"));
    }

    @Test
    public void unknownArg() {
        assertThrows(IllegalArgumentException.class,
                () -> VendorServiceInfo.parse(SERVICE_NAME + "#user=system,unknownKey=blah"));
    }

    @Test
    public void invalidComponentName() {
        assertThrows(IllegalArgumentException.class,
                () -> VendorServiceInfo.parse("invalidComponentName"));
    }

    @Test
    public void testParse_maxRetriesValueNotANumber() {
        assertThrows(IllegalArgumentException.class,
                () -> VendorServiceInfo.parse(SERVICE_NAME + "#maxRetries=seven"));
    }

    @Test
    public void testServiceNameWithDefaults() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME);

        assertThat(info.getIntent().getComponent())
                .isEqualTo(ComponentName.unflattenFromString(SERVICE_NAME));
        assertThat(info.shouldBeBound()).isFalse();
        assertThat(info.shouldBeStartedInForeground()).isFalse();
        assertThat(info.isSystemUserService()).isTrue();
        assertThat(info.isForegroundUserService()).isTrue();
        assertThat(info.shouldStartOnUnlock()).isTrue();
    }

    @Test
    public void startService() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#bind=start");

        assertThat(info.shouldBeBound()).isFalse();
        assertThat(info.shouldBeStartedInForeground()).isFalse();
        assertThat(info.getIntent().getComponent())
                .isEqualTo(ComponentName.unflattenFromString(SERVICE_NAME));
    }

    @Test
    public void bindService() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#bind=bind");

        assertThat(info.shouldBeBound()).isTrue();
        assertThat(info.shouldBeStartedInForeground()).isFalse();
    }

    @Test
    public void startServiceInForeground() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#bind=startForeground");

        assertThat(info.shouldBeBound()).isFalse();
        assertThat(info.shouldBeStartedInForeground()).isTrue();
    }

    @Test
    public void triggerAsap() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#trigger=asap");

        assertThat(info.shouldStartOnUnlock()).isFalse();
    }

    @Test
    public void triggerUnlocked() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#trigger=userUnlocked");

        assertThat(info.shouldStartOnUnlock()).isTrue();
    }

    @Test
    public void triggerPostUnlocked() {
        VendorServiceInfo info = VendorServiceInfo.parse(
                SERVICE_NAME + "#trigger=userPostUnlocked");

        assertThat(info.shouldStartOnPostUnlock()).isTrue();
    }

    @Test
    public void triggerResume() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#trigger=resume");

        assertThat(info.shouldStartOnResume()).isTrue();
    }

    @Test
    public void triggerUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> VendorServiceInfo.parse(SERVICE_NAME + "#trigger=whenever"));
    }

    @Test
    public void userScopeForeground() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#user=foreground");

        assertThat(info.isAllUserService()).isFalse();
        assertThat(info.isForegroundUserService()).isTrue();
        assertThat(info.isSystemUserService()).isFalse();
        assertThat(info.isVisibleUserService()).isFalse();
        assertThat(info.isBackgroundVisibleUserService()).isFalse();
    }

    @Test
    public void userScopeSystem() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#user=system");

        assertThat(info.isAllUserService()).isFalse();
        assertThat(info.isForegroundUserService()).isFalse();
        assertThat(info.isSystemUserService()).isTrue();
        assertThat(info.isVisibleUserService()).isFalse();
        assertThat(info.isBackgroundVisibleUserService()).isFalse();
    }

    @Test
    public void userScopeVisible() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#user=visible");

        assertThat(info.isAllUserService()).isFalse();
        assertThat(info.isForegroundUserService()).isFalse();
        assertThat(info.isSystemUserService()).isFalse();
        assertThat(info.isVisibleUserService()).isTrue();
        assertThat(info.isBackgroundVisibleUserService()).isFalse();
    }

    @Test
    public void userScopeBackgroundVisible() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#user=backgroundVisible");

        assertThat(info.isAllUserService()).isFalse();
        assertThat(info.isForegroundUserService()).isFalse();
        assertThat(info.isSystemUserService()).isFalse();
        assertThat(info.isVisibleUserService()).isFalse();
        assertThat(info.isBackgroundVisibleUserService()).isTrue();
    }

    @Test
    public void userScopeAll() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME + "#user=all");

        assertThat(info.isAllUserService()).isTrue();
        assertThat(info.isForegroundUserService()).isTrue();
        assertThat(info.isSystemUserService()).isTrue();
        assertThat(info.isVisibleUserService()).isTrue();
        assertThat(info.isBackgroundVisibleUserService()).isTrue();
    }

    @Test
    public void userUnknown() {
        assertThrows(IllegalArgumentException.class,
                () -> VendorServiceInfo.parse(SERVICE_NAME + "#user=whoever"));
    }

    @Test
    public void testGetMaxRetries() {
        VendorServiceInfo info =
                VendorServiceInfo.parse(SERVICE_NAME + "#maxRetries=" + MAX_RETRIES);

        assertThat(info.getMaxRetries()).isEqualTo(MAX_RETRIES);
    }

    @Test
    public void testGetMaxRetries_defaultMaxRetries() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME);

        assertThat(info.getMaxRetries()).isEqualTo(VendorServiceInfo.DEFAULT_MAX_RETRIES);
    }

    @Test
    public void allArgs() {
        VendorServiceInfo info = VendorServiceInfo.parse(SERVICE_NAME
                + "#bind=bind,user=foreground,trigger=userUnlocked,maxRetries=" + MAX_RETRIES);

        assertThat(info.getIntent().getComponent())
                .isEqualTo(ComponentName.unflattenFromString(SERVICE_NAME));
        assertThat(info.shouldBeBound()).isTrue();
        assertThat(info.isForegroundUserService()).isTrue();
        assertThat(info.isSystemUserService()).isFalse();
        assertThat(info.shouldStartOnUnlock()).isTrue();
        assertThat(info.shouldStartAsap()).isFalse();
        assertThat(info.getMaxRetries()).isEqualTo(MAX_RETRIES);
    }

    @Test
    public void testToString_bindForegroundUserPostUnlocked() {
        String result = VendorServiceInfo.parse(SERVICE_NAME
                + "#bind=bind,user=backgroundVisible,trigger=asap").toString();

        assertThat(result).contains("component=" + SERVICE_NAME);
        assertThat(result).contains("bind=BIND");
        assertThat(result).contains("userScope=BACKGROUND_VISIBLE");
        assertThat(result).contains("trigger=ASAP");
    }

    @Test
    public void testToString_bindBackgroundVisibleUserAsap() {
        String result = VendorServiceInfo.parse(SERVICE_NAME
                + "#bind=start,user=visible,trigger=userUnlocked").toString();

        assertThat(result).contains("component=" + SERVICE_NAME);
        assertThat(result).contains("bind=START");
        assertThat(result).contains("userScope=VISIBLE");
        assertThat(result).contains("trigger=UNLOCKED");
    }

    @Test
    public void testToString_startVisibleUserUnlocked() {
        String result = VendorServiceInfo.parse(SERVICE_NAME
                + "#bind=start,user=visible,trigger=userUnlocked").toString();

        assertThat(result).contains("component=" + SERVICE_NAME);
        assertThat(result).contains("bind=START");
        assertThat(result).contains("userScope=VISIBLE");
        assertThat(result).contains("trigger=UNLOCKED");
    }

    @Test
    public void testToString_startSystemUserResume() {
        String result = VendorServiceInfo.parse(SERVICE_NAME
                + "#bind=start,user=system,trigger=resume").toString();

        assertThat(result).contains("component=" + SERVICE_NAME);
        assertThat(result).contains("bind=START");
        assertThat(result).contains("userScope=SYSTEM");
        assertThat(result).contains("trigger=RESUME");
    }

    @Test
    public void testToString_maxRetries() {
        String result = VendorServiceInfo.parse(SERVICE_NAME + "#maxRetries=" + MAX_RETRIES)
                .toString();

        assertThat(result).contains("component=" + SERVICE_NAME);
        assertThat(result).contains("maxRetries=" + MAX_RETRIES);
    }

    @Test
    public void testToString_defaultMaxRetries() {
        String result = VendorServiceInfo.parse(SERVICE_NAME)
                .toString();

        assertThat(result).contains("component=" + SERVICE_NAME);
        assertThat(result).doesNotContain("maxRetries=");
    }
}
