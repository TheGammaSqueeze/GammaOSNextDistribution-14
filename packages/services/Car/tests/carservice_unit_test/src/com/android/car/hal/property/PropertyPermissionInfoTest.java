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

package com.android.car.hal.property;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.car.Car;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.car.hal.property.PropertyPermissionInfo.AllOfPermissions;
import com.android.car.hal.property.PropertyPermissionInfo.AnyOfPermissions;
import com.android.car.hal.property.PropertyPermissionInfo.PermissionCondition;
import com.android.car.hal.property.PropertyPermissionInfo.PropertyPermissions;
import com.android.car.hal.property.PropertyPermissionInfo.SinglePermission;
// import com.google.common.testing.EqualsTester;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class PropertyPermissionInfoTest {
    private static final String TAG = PropertyPermissionInfoTest.class.getSimpleName();
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock
    private Context mContext;

    /**
     * Test {@link AllOfPermissions}
     */
    @Test
    public void testAllOfPermission() {
        AllOfPermissions testPermission = new AllOfPermissions(
                new SinglePermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS),
                new SinglePermission(Car.PERMISSION_VENDOR_EXTENSION));

        assertThat(testPermission.toString()).isEqualTo("(" + Car.PERMISSION_CONTROL_DISPLAY_UNITS
                + " && " + Car.PERMISSION_VENDOR_EXTENSION + ")");

        assertThat(testPermission.isMet(mContext)).isTrue();

        when(mContext.checkCallingOrSelfPermission(Car.PERMISSION_VENDOR_EXTENSION))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertThat(testPermission.isMet(mContext)).isFalse();

        when(mContext.checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertThat(testPermission.isMet(mContext)).isFalse();
    }

    /**
     * Test {@link AnyOfPermissions}
     */
    @Test
    public void testAnyOfPermission() {
        AnyOfPermissions testPermission = new AnyOfPermissions(
                new SinglePermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS),
                new SinglePermission(Car.PERMISSION_VENDOR_EXTENSION));

        assertThat(testPermission.toString()).isEqualTo("(" + Car.PERMISSION_CONTROL_DISPLAY_UNITS
                + " || " + Car.PERMISSION_VENDOR_EXTENSION + ")");

        assertThat(testPermission.isMet(mContext)).isTrue();

        when(mContext.checkCallingOrSelfPermission(Car.PERMISSION_VENDOR_EXTENSION))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertThat(testPermission.isMet(mContext)).isTrue();

        when(mContext.checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        assertThat(testPermission.isMet(mContext)).isFalse();
    }

    @Test
    public void testSinglePermissionHash() {
        PermissionCondition p1 = new SinglePermission("abc");
        PermissionCondition p2 = new SinglePermission("bcd");
        PermissionCondition p3 = new SinglePermission("bcd");

        assertThat(p1.hashCode()).isNotEqualTo(p2.hashCode());
        assertThat(p1.hashCode()).isNotEqualTo(p3.hashCode());
        assertThat(p2.hashCode()).isEqualTo(p3.hashCode());
    }

    @Test
    public void testAnyPermissionHash() {
        PermissionCondition p1 = new AnyOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"));
        PermissionCondition p2 = new AnyOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"));
        // Order does not matter.
        PermissionCondition p3 = new AnyOfPermissions(new SinglePermission("bcd"),
                new SinglePermission("abc"));
        PermissionCondition p4 = new AnyOfPermissions(new SinglePermission("bla"),
                new SinglePermission("blah"));
        PermissionCondition p5 = new AnyOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"), new SinglePermission("cde"));

        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
        assertThat(p1.hashCode()).isEqualTo(p3.hashCode());
        assertThat(p1.hashCode()).isNotEqualTo(p4.hashCode());
        assertThat(p1.hashCode()).isNotEqualTo(p5.hashCode());
    }

    @Test
    public void testAllPermissionHash() {
        PermissionCondition p1 = new AllOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"));
        PermissionCondition p2 = new AllOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"));
        // Order does not matter.
        PermissionCondition p3 = new AllOfPermissions(new SinglePermission("bcd"),
                new SinglePermission("abc"));
        PermissionCondition p4 = new AllOfPermissions(new SinglePermission("bla"),
                new SinglePermission("blah"));
        PermissionCondition p5 = new AllOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"), new SinglePermission("cde"));

        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
        assertThat(p1.hashCode()).isEqualTo(p3.hashCode());
        assertThat(p1.hashCode()).isNotEqualTo(p4.hashCode());
        assertThat(p1.hashCode()).isNotEqualTo(p5.hashCode());
    }

    @Test
    public void tesPropertyPermissionsHash() {
        PropertyPermissions p1 = new PropertyPermissions.Builder()
                .setReadPermission(new SinglePermission("abc"))
                .setWritePermission(new SinglePermission("bcd"))
                .build();
        PropertyPermissions p2 = new PropertyPermissions.Builder()
                .setReadPermission(new SinglePermission("abc"))
                .setWritePermission(new SinglePermission("bcd"))
                .build();
        PropertyPermissions p3 = new PropertyPermissions.Builder()
                .setReadPermission(new SinglePermission("abc"))
                .build();
        PropertyPermissions p4 = new PropertyPermissions.Builder()
                .setWritePermission(new SinglePermission("bcd"))
                .build();

        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
        assertThat(p1.hashCode()).isNotEqualTo(p3.hashCode());
        assertThat(p1.hashCode()).isNotEqualTo(p4.hashCode());
    }

    /* TODO(b/232458264): The EqualsTest will automatically include several test classes that are
    marked as ignore, this will cause test infra error.  Enable this test once test infra supports
    ignore on class.

    @Test
    public void testSinglePermissionEqual() {
        PermissionCondition p1 = new SinglePermission("abc");
        PermissionCondition p2 = new SinglePermission("bcd");
        PermissionCondition p3 = new SinglePermission("bcd");

        new EqualsTester().addEqualityGroup(p1).addEqualityGroup(p2, p3).testEquals();
    }

    @Test
    public void testAnyPermissionEqual() {
        PermissionCondition p1 = new AnyOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"));
        PermissionCondition p2 = new AnyOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"));
        // Order does not matter.
        PermissionCondition p3 = new AnyOfPermissions(new SinglePermission("bcd"),
                new SinglePermission("abc"));
        PermissionCondition p4 = new AnyOfPermissions(new SinglePermission("bla"),
                new SinglePermission("blah"));
        PermissionCondition p5 = new AnyOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"), new SinglePermission("cde"));

        new EqualsTester().addEqualityGroup(p1, p2, p3).addEqualityGroup(p4)
                .addEqualityGroup(p5).testEquals();
    }

    @Test
    public void testAllPermissionEqual() {
        PermissionCondition p1 = new AllOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"));
        PermissionCondition p2 = new AllOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"));
        // Order does not matter.
        PermissionCondition p3 = new AllOfPermissions(new SinglePermission("bcd"),
                new SinglePermission("abc"));
        PermissionCondition p4 = new AllOfPermissions(new SinglePermission("bla"),
                new SinglePermission("blah"));
        PermissionCondition p5 = new AllOfPermissions(new SinglePermission("abc"),
                new SinglePermission("bcd"), new SinglePermission("cde"));

        new EqualsTester().addEqualityGroup(p1, p2, p3).addEqualityGroup(p4)
                .addEqualityGroup(p5).testEquals();
    }

    @Test
    public void testPropertyPermissionsEqual() {
        PropertyPermissions p1 = new PropertyPermissions.Builder()
                .setReadPermission(new SinglePermission("abc"))
                .setWritePermission(new SinglePermission("bcd"))
                .build();
        PropertyPermissions p2 = new PropertyPermissions.Builder()
                .setReadPermission(new SinglePermission("abc"))
                .setWritePermission(new SinglePermission("bcd"))
                .build();
        PropertyPermissions p3 = new PropertyPermissions.Builder()
                .setReadPermission(new SinglePermission("abc"))
                .build();
        PropertyPermissions p4 = new PropertyPermissions.Builder()
                .setWritePermission(new SinglePermission("bcd"))
                .build();

       new EqualsTester().addEqualityGroup(p1, p2).addEqualityGroup(p3)
                .addEqualityGroup(p4).testEquals();
    }
    */
}
