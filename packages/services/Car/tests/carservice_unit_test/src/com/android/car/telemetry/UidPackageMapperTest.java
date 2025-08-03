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

package com.android.car.telemetry;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.car.test.FakeHandlerWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(MockitoJUnitRunner.class)
public class UidPackageMapperTest {
    private static final int PACKAGE_ALL_FLAGS =
            PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_ANY_USER;

    private static final int MAX_REMOVED_APPS_COUNT = 1;

    // List of PackageInfo for uid/packageName.
    private static final PackageInfo sPackageA = buildPackageInfo(1, "com.android.a", false);
    private static final PackageInfo sPackageB = buildPackageInfo(1, "com.android.b", false);
    private static final PackageInfo sPackageC = buildPackageInfo(2, "com.android.c", false);
    private static final PackageInfo sPackageApex = buildPackageInfo(4, "com.andr.apex", true);
    private static final PackageInfo sPackageN = buildPackageInfo(5, "com.android.n", false);
    private static final PackageInfo sPackageX = buildPackageInfo(10, "com.android.x", false);

    private static final UserHandle sUserHandle1 = new UserHandle(/* userId= */ 1);
    private static final UserHandle sUserHandle2New = new UserHandle(/* userId= */ 2);

    @Mock private PackageManager mMockPackageManager;
    @Mock private UserManager mMockUserManager;
    @Mock private Context mMockContext;

    private final AtomicReference<BroadcastReceiver> mUserUpdateReceiver = new AtomicReference<>();
    private final AtomicReference<BroadcastReceiver> mAppUpdateReceiver = new AtomicReference<>();

    private FakeHandlerWrapper mDirectHandler =
            new FakeHandlerWrapper(Looper.getMainLooper(), FakeHandlerWrapper.Mode.IMMEDIATE);
    private UidPackageMapper mUidMapper; // subject

    @Before
    public void setUp() {
        when(mMockUserManager.getUserHandles(anyBoolean())).thenReturn(List.of(sUserHandle1));

        // sUserHandle1 packages
        when(mMockPackageManager.getInstalledPackagesAsUser(
                        eq(PACKAGE_ALL_FLAGS), eq(sUserHandle1.getIdentifier())))
                .thenReturn(List.of(sPackageA, sPackageB, sPackageC));
        // sUserHandle2New packages
        when(mMockPackageManager.getInstalledPackagesAsUser(
                        eq(PACKAGE_ALL_FLAGS), eq(sUserHandle2New.getIdentifier())))
                .thenReturn(List.of(sPackageX));
        // Apex packages
        when(mMockPackageManager.getInstalledPackages(eq(PackageManager.MATCH_APEX)))
                .thenReturn(List.of(sPackageApex));

        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        doAnswer(this::onRegisterReceiverForAllUsers)
                .when(mMockContext)
                .registerReceiverForAllUsers(any(), any(), isNull(), isNull());

        mUidMapper =
                new UidPackageMapper(
                        mMockContext, mDirectHandler.getMockHandler(), MAX_REMOVED_APPS_COUNT);
    }

    private Intent onRegisterReceiverForAllUsers(InvocationOnMock invocation) {
        IntentFilter filter = invocation.getArgument(1);
        if (toList(filter.actionsIterator()).contains(Intent.ACTION_PACKAGE_ADDED)) {
            mAppUpdateReceiver.set(invocation.getArgument(0));
        }
        if (toList(filter.actionsIterator()).contains(Intent.ACTION_USER_INITIALIZE)) {
            mUserUpdateReceiver.set(invocation.getArgument(0));
        }
        return null; // should return Intent, but the result is not used.
    }

    @Test
    public void testInitPullsAllPackages() {
        mUidMapper.init();

        assertThat(mUidMapper.getPackagesForUid(1))
                .containsExactly("com.android.a", "com.android.b");
        assertThat(mUidMapper.getPackagesForUid(2)).containsExactly("com.android.c");
        assertThat(mUidMapper.getPackagesForUid(4)).containsExactly("com.andr.apex");
        assertThat(mUidMapper.getPackagesForUid(5)).isEmpty();
        assertThat(mUidMapper.getPackagesForUid(10)).isEmpty();
    }

    @Test
    public void testOnAppInstalled() {
        mUidMapper.init();

        broadcastAppAdded(sPackageN); // uid = 5

        assertThat(mUidMapper.getPackagesForUid(5)).containsExactly("com.android.n");
        assertThat(mUidMapper.getPackagesForUid(1)) // other apps are there too
                .containsExactly("com.android.a", "com.android.b");
    }

    @Test
    public void testOnAppRemoved() {
        mUidMapper.init();

        broadcastAppRemoved(sPackageB);

        broadcastAppRemoved(sPackageC); // To remove B from the cache, remove C too.

        // "com.android.b" is removed, and deleted from UidPackageMapper cache too,
        // because MAX_REMOVED_APPS_COUNT = 1.
        assertThat(mUidMapper.getPackagesForUid(1)).containsExactly("com.android.a");
        // "com.android.c" is still cached in UidPackageMapper, even if the app is removed.
        assertThat(mUidMapper.getPackagesForUid(2)).containsExactly("com.android.c");
    }

    // Tests if cached uninstalled apps are handled well, when installed back again.
    @Test
    public void testOnAppRemovedThenInstalled() {
        mUidMapper.init();

        broadcastAppRemoved(sPackageB);

        broadcastAppAdded(sPackageB);

        // Remove "sPackageC" to trigger cache cleanup, it should try to remove "sPackageB",
        // but mustn't, as B is installed again.
        broadcastAppRemoved(sPackageC);

        // Remove "sPackageApex" to trigger to cleanup "sPackageC".
        broadcastAppRemoved(sPackageApex);

        assertThat(mUidMapper.getPackagesForUid(1))
                .containsExactly("com.android.a", "com.android.b");
        assertThat(mUidMapper.getPackagesForUid(2)).isEmpty(); // sPackageC is cleaned-up from cache
    }

    @Test
    public void testOnUserAdded() {
        mUidMapper.init();
        when(mMockUserManager.getUserHandles(anyBoolean()))
                .thenReturn(List.of(sUserHandle1, sUserHandle2New));

        Intent intent = new Intent(Intent.ACTION_USER_INITIALIZE);
        mUserUpdateReceiver.get().onReceive(mMockContext, intent);

        assertThat(mUidMapper.getPackagesForUid(1))
                .containsExactly("com.android.a", "com.android.b");
        // "com.android.x" already exists in sUserHandle2New.
        assertThat(mUidMapper.getPackagesForUid(10)).containsExactly("com.android.x");
    }

    private void broadcastAppAdded(PackageInfo packageInfo) {
        Intent intent = new Intent(Intent.ACTION_PACKAGE_ADDED);
        intent.setData(Uri.fromParts("package", packageInfo.packageName, null));
        intent.putExtra(Intent.EXTRA_UID, packageInfo.applicationInfo.uid);
        mAppUpdateReceiver.get().onReceive(mMockContext, intent);
    }

    private void broadcastAppRemoved(PackageInfo packageInfo) {
        Intent intent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        intent.setData(Uri.fromParts("package", packageInfo.packageName, null));
        intent.putExtra(Intent.EXTRA_UID, packageInfo.applicationInfo.uid);
        mAppUpdateReceiver.get().onReceive(mMockContext, intent);
    }

    /** Converts iterator to a list. */
    private static <T> List<T> toList(Iterator<T> iterator) {
        ArrayList<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static PackageInfo buildPackageInfo(int uid, String packageName, boolean isApex) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.uid = uid;
        info.isApex = isApex;
        return info;
    }
}
