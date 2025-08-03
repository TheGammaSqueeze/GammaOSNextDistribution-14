/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.users;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RunWith(JUnit4.class)
public class UserTypeTest {

    private static final int INT_VALUE = 1;
    private static final String STRING_VALUE = "String";

    @Test
    public void name_returnsName() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        mutableUserType.mName = STRING_VALUE;
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.name()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void name_notSet_returnsNull() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.name()).isNull();
    }

    @Test
    public void baseType_returnsBaseType() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        mutableUserType.mBaseType = new HashSet<>(Arrays.asList(UserType.BaseType.FULL));
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.baseType()).containsExactly(UserType.BaseType.FULL);
    }

    @Test
    public void baseType_notSet_returnsNull() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.baseType()).isNull();
    }

    @Test
    public void enabled_returnsEnabled() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        mutableUserType.mEnabled = true;
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.enabled()).isTrue();
    }

    @Test
    public void enabled_notSet_returnsNull() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.enabled()).isNull();
    }

    @Test
    public void maxAllowed_returnsMaxAllowed() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        mutableUserType.mMaxAllowed = INT_VALUE;
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.maxAllowed()).isEqualTo(INT_VALUE);
    }

    @Test
    public void maxAllowed_notSet_returnsNull() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.maxAllowed()).isNull();
    }

    @Test
    public void maxAllowedPerParent_returnsMaxAllowedPerParent() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        mutableUserType.mMaxAllowedPerParent = INT_VALUE;
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.maxAllowedPerParent()).isEqualTo(INT_VALUE);
    }

    @Test
    public void maxAllowedParParent_notSet_returnsNull() {
        UserType.MutableUserType mutableUserType = new UserType.MutableUserType();
        UserType userType = new UserType(mutableUserType);

        assertThat(userType.maxAllowedPerParent()).isNull();
    }
}
