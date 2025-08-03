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
package android.multiuser.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.graphics.Bitmap;
import android.os.NewUserRequest;
import android.os.PersistableBundle;
import android.os.UserManager;

import org.junit.Test;

public class NewUserRequestTest {

    private final NewUserRequest.Builder mBuilder = new NewUserRequest.Builder();

    private NewUserRequest build() {
        return mBuilder.build();
    }

    @Test
    public void testDefaultNameIsNull() {
        assertThat(build().getName()).isNull();
    }

    @Test
    public void testSetName() {
        final String name = "test_name";
        mBuilder.setName(name);
        assertThat(build().getName()).isEqualTo(name);
    }

    @Test
    public void testSetNameNull() {
        mBuilder.setName(null);
        assertThat(build().getName()).isNull();
    }

    @Test
    public void testDefaultAdminIsFalse() {
        assertThat(build().isAdmin()).isFalse();
    }

    @Test
    public void testSetAdmin() {
        mBuilder.setAdmin();
        // Admin user can only be USER_TYPE_FULL_SECONDARY and default value of userType is that
        assertThat(build().isAdmin()).isTrue();
    }

    @Test
    public void testBuildThrowsWhenAdminIsNotUserTypeFullSecondary() {
        mBuilder.setAdmin().setUserType("OTHER_THAN_" + UserManager.USER_TYPE_FULL_SECONDARY);
        assertThrows(IllegalStateException.class, this::build);
    }

    @Test
    public void testDefaultEphemeralIsFalse() {
        assertThat(build().isEphemeral()).isFalse();
    }

    @Test
    public void testSetEphemeral() {
        mBuilder.setEphemeral();
        assertThat(build().isEphemeral()).isTrue();
    }

    @Test
    public void testDefaultUserTypeIsNotNull() {
        assertThat(build().getUserType()).isNotNull();
    }

    @Test
    public void testSetUserType() {
        final String userType = "test_user_type";
        mBuilder.setUserType(userType);
        assertThat(build().getUserType()).isEqualTo(userType);
    }

    @Test
    public void testBuildThrowsWhenUserTypeIsNull() {
        mBuilder.setUserType(null);
        assertThrows(IllegalStateException.class, this::build);
    }

    @Test
    public void testDefaultUserIconIsNull() {
        assertThat(build().getUserIcon()).isNull();
    }

    @Test
    public void testSetUserIcon() {
        final Bitmap icon = Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565);
        mBuilder.setUserIcon(icon);
        assertThat(build().getUserIcon()).isEqualTo(icon);
    }

    @Test
    public void testSetUserIconNull() {
        mBuilder.setUserIcon(null);
        assertThat(build().getUserIcon()).isNull();
    }

    @Test
    public void testDefaultAccountNameAndAccountTypeAreNull() {
        final NewUserRequest request = build();
        assertThat(request.getAccountName()).isNull();
        assertThat(request.getAccountType()).isNull();
    }

    @Test
    public void testSetAccountNameAndAccountType() {
        final String accountName = "test_account_name";
        final String accountType = "test_account_type";
        mBuilder.setAccountName(accountName).setAccountType(accountType);
        final NewUserRequest request = build();
        assertThat(request.getAccountName()).isEqualTo(accountName);
        assertThat(request.getAccountType()).isEqualTo(accountType);
    }

    @Test
    public void testSetAccountNameAndAccountTypeNull() {
        mBuilder.setAccountName(null).setAccountType(null);
        final NewUserRequest request = build();
        assertThat(request.getAccountName()).isNull();
        assertThat(request.getAccountType()).isNull();
    }

    @Test
    public void testBuildThrowsWhenAccountNameProvidedWithoutAccountType() {
        mBuilder.setAccountName("test_account_name").setAccountType(null);
        assertThrows(IllegalStateException.class, this::build);
    }

    @Test
    public void testBuildThrowsWhenAccountTypeProvidedWithoutAccountName() {
        mBuilder.setAccountName(null).setAccountType("test_account_type");
        assertThrows(IllegalStateException.class, this::build);
    }

    @Test
    public void testDefaultAccountOptionsIsNull() {
        assertThat(build().getAccountOptions()).isNull();
    }

    @Test
    public void testSetAccountOptions() {
        final PersistableBundle accountOptions = new PersistableBundle();
        accountOptions.putString("test_account_option_key", "test_account_option_value");
        mBuilder.setAccountOptions(accountOptions);
        assertThat(build().getAccountOptions()).isEqualTo(accountOptions);
    }

    @Test
    public void testSetAccountOptionsNull() {
        mBuilder.setAccountOptions(null);
        assertThat(build().getAccountOptions()).isNull();
    }
}
