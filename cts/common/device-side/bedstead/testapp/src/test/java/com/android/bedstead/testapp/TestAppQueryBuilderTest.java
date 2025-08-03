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

package com.android.bedstead.testapp;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class TestAppQueryBuilderTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    public void get_emptyQueryBuilder_throwsException() {
        assertThrows(IllegalStateException.class, () -> TestAppQueryBuilder.queryBuilder().get());
    }

    @Test
    public void isEmptyQuery_isEmpty_returnsTrue() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();

        assertThat(b.isEmptyQuery()).isTrue();
    }

    @Test
    public void isEmptyQuery_hasPackageNameQuery_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.wherePackageName().isNotNull();

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasLabelQuery_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereLabel().isNotNull();

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasMetadata_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereMetadata().key("A").exists();

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasMinSdkVersion_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereMinSdkVersion().isEqualTo(0);

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasMaxSdkVersion_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereMaxSdkVersion().isEqualTo(0);

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasTargetSdkVersion_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereTargetSdkVersion().isEqualTo(0);

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasActivities_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereActivities().isNotEmpty();

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasServices_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereServices().isNotEmpty();

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasPermissions_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.wherePermissions().isNotEmpty();

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasTestOnly_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereTestOnly().isTrue();

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasIsDeviceAdmin_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereIsDeviceAdmin().isTrue();

        assertThat(b.isEmptyQuery()).isFalse();
    }

    @Test
    public void isEmptyQuery_hasSharedUserId_returnsFalse() {
        TestAppQueryBuilder b = TestAppQueryBuilder.queryBuilder();
        b.whereSharedUserId().isNotNull();

        assertThat(b.isEmptyQuery()).isFalse();
    }
}
