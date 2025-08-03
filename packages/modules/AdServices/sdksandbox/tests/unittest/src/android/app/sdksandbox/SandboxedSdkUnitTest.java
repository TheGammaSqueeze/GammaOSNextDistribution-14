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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.pm.SharedLibraryInfo;
import android.content.pm.VersionedPackage;
import android.os.Binder;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;

@RunWith(JUnit4.class)
public class SandboxedSdkUnitTest {

    public static final SharedLibraryInfo SHARED_LIBRARY_INFO =
            new SharedLibraryInfo(
                    "testpath",
                    "test",
                    new ArrayList<>(),
                    "test",
                    0L,
                    SharedLibraryInfo.TYPE_STATIC,
                    new VersionedPackage("test", 0L),
                    null,
                    null,
                    false /* isNative */);

    @Test
    public void testAttachSharedLibraryInfoFailsWhenCalledAgain() {
        SandboxedSdk sandboxedSdk = new SandboxedSdk(new Binder());
        sandboxedSdk.attachSharedLibraryInfo(SHARED_LIBRARY_INFO);
        // Only one update should be received
        assertThrows(
                "SharedLibraryInfo already set",
                IllegalStateException.class,
                () -> sandboxedSdk.attachSharedLibraryInfo(SHARED_LIBRARY_INFO));
    }

    @Test
    public void testGetInterface() {
        Binder binder = new Binder();
        SandboxedSdk sandboxedSdk = new SandboxedSdk(binder);
        assertThat(sandboxedSdk.getInterface()).isSameInstanceAs(binder);
    }

    @Test
    public void testGetSharedLibraryInfo() {
        SandboxedSdk sandboxedSdk = new SandboxedSdk(new Binder());
        sandboxedSdk.attachSharedLibraryInfo(SHARED_LIBRARY_INFO);
        assertThat(sandboxedSdk.getSharedLibraryInfo()).isSameInstanceAs(SHARED_LIBRARY_INFO);
    }

    @Test
    public void testGetSharedLibraryInfoNull() {
        SandboxedSdk sandboxedSdk = new SandboxedSdk(new Binder());
        assertThrows(IllegalStateException.class, () -> sandboxedSdk.getSharedLibraryInfo());
    }

    @Test
    public void testDescribeContents() {
        SandboxedSdk sandboxedSdk = new SandboxedSdk(new Binder());
        int descriptor = sandboxedSdk.describeContents();
        assertThat(descriptor).isEqualTo(0);
    }

    @Test
    public void testWriteToParcel() {
        SandboxedSdk sandboxedSdk = new SandboxedSdk(new Binder());

        Parcel parcel = Parcel.obtain();
        sandboxedSdk.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        SandboxedSdk fromParcel = SandboxedSdk.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(sandboxedSdk.getInterface()).isEqualTo(fromParcel.getInterface());
    }

    @Test
    public void testWriteToParcelWithSharedLibraryInfo() {
        SandboxedSdk sandboxedSdk = new SandboxedSdk(new Binder());
        sandboxedSdk.attachSharedLibraryInfo(SHARED_LIBRARY_INFO);

        Parcel parcel = Parcel.obtain();
        sandboxedSdk.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        SandboxedSdk fromParcel = SandboxedSdk.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertThat(sandboxedSdk.getInterface()).isEqualTo(fromParcel.getInterface());
        assertThat(sandboxedSdk.getSharedLibraryInfo().getName())
                .isEqualTo(fromParcel.getSharedLibraryInfo().getName());
        assertThat(sandboxedSdk.getSharedLibraryInfo().getLongVersion())
                .isEqualTo(fromParcel.getSharedLibraryInfo().getLongVersion());
    }
}
