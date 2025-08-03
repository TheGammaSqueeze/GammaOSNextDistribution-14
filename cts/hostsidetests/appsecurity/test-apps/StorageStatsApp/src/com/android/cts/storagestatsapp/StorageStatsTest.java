/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.storagestatsapp;

import static android.os.storage.StorageManager.UUID_DEFAULT;

import static com.android.cts.storageapp.Utils.CACHE_ALL;
import static com.android.cts.storageapp.Utils.CACHE_EXT;
import static com.android.cts.storageapp.Utils.CODE_ALL;
import static com.android.cts.storageapp.Utils.DATA_ALL;
import static com.android.cts.storageapp.Utils.MB_IN_BYTES;
import static com.android.cts.storageapp.Utils.PKG_A;
import static com.android.cts.storageapp.Utils.PKG_B;
import static com.android.cts.storageapp.Utils.REF_PROFILES_BASE_DIR;
import static com.android.cts.storageapp.Utils.CUR_PROFILES_BASE_DIR;
import static com.android.cts.storageapp.Utils.PROFILE_FILE_NAME;
import static com.android.cts.storageapp.Utils.TAG;
import static com.android.cts.storageapp.Utils.assertAtLeast;
import static com.android.cts.storageapp.Utils.assertMostlyEquals;
import static com.android.cts.storageapp.Utils.getSizeManual;
import static com.android.cts.storageapp.Utils.logCommand;
import static com.android.cts.storageapp.Utils.makeUniqueFile;
import static com.android.cts.storageapp.Utils.useFallocate;
import static com.android.cts.storageapp.Utils.useSpace;
import static com.android.cts.storageapp.Utils.useWrite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import org.junit.Before;
import org.junit.Rule;

import android.app.Activity;
import android.app.usage.ExternalStorageStats;
import android.app.usage.Flags;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.provider.MediaStore;
import android.test.InstrumentationTestCase;
import android.util.Log;
import android.util.MutableLong;

import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.storageapp.UtilsReceiver;

import junit.framework.AssertionFailedError;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests to verify {@link StorageStatsManager} behavior.
 */
public class StorageStatsTest extends InstrumentationTestCase {
    private PackageManager pm;
    private StorageStatsManager stats;
    private UserHandle user;

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        pm = getContext().getPackageManager();
        stats = getContext().getSystemService(StorageStatsManager.class);
        user = android.os.Process.myUserHandle();
    }
    private Context getContext() {
        return getInstrumentation().getContext();
    }

    /**
     * Require that quota support be fully enabled on devices that first ship
     * with P. This test verifies that both kernel options and the fstab 'quota'
     * option are enabled.
     */
    public void testVerify() throws Exception {
        if (Build.VERSION.DEVICE_INITIAL_SDK_INT >= Build.VERSION_CODES.P) {
            final StorageStatsManager stats = getContext()
                    .getSystemService(StorageStatsManager.class);
            assertTrue("Devices that first ship with P or newer must enable quotas to "
                    + "support StorageStatsManager APIs. You may need to enable the "
                    + "CONFIG_QUOTA, CONFIG_QFMT_V2, CONFIG_QUOTACTL kernel options "
                    + "and add the 'quota' fstab option on /data.",
                    stats.isQuotaSupported(UUID_DEFAULT));
            assertTrue("Devices that first ship with P or newer must enable resgid to "
                    + "preserve system stability in the face of abusive apps.",
                    stats.isReservedSupported(UUID_DEFAULT));
        }
    }

    public void testVerifySummary() throws Exception {
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);

        final long actualTotal = stats.getTotalBytes(UUID_DEFAULT);
        final long expectedTotal = Environment.getDataDirectory().getTotalSpace();
        assertAtLeast(expectedTotal, actualTotal);

        final long actualFree = stats.getFreeBytes(UUID_DEFAULT);
        final long expectedFree = Environment.getDataDirectory().getUsableSpace();
        assertAtLeast(expectedFree, actualFree);
    }

    public void testVerifyStats() throws Exception {
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);
        final int uid = android.os.Process.myUid();
        final UserHandle user = UserHandle.getUserHandleForUid(uid);

        final StorageStats beforeApp = stats.queryStatsForUid(UUID_DEFAULT, uid);
        final StorageStats beforeUser = stats.queryStatsForUser(UUID_DEFAULT, user);

        useSpace(getContext());

        final StorageStats afterApp = stats.queryStatsForUid(UUID_DEFAULT, uid);
        final StorageStats afterUser = stats.queryStatsForUser(UUID_DEFAULT, user);

        final long deltaCode = CODE_ALL;
        assertMostlyEquals(deltaCode, afterApp.getAppBytes() - beforeApp.getAppBytes());
        assertMostlyEquals(deltaCode, afterUser.getAppBytes() - beforeUser.getAppBytes());

        final long deltaData = DATA_ALL;
        assertMostlyEquals(deltaData, afterApp.getDataBytes() - beforeApp.getDataBytes());
        assertMostlyEquals(deltaData, afterUser.getDataBytes() - beforeUser.getDataBytes());

        final long deltaCache = CACHE_ALL;
        assertMostlyEquals(deltaCache, afterApp.getCacheBytes() - beforeApp.getCacheBytes());
        assertMostlyEquals(deltaCache, afterUser.getCacheBytes() - beforeUser.getCacheBytes());

        final long deltaExternalCache = CACHE_EXT;
        assertMostlyEquals(deltaExternalCache,
                afterApp.getExternalCacheBytes() - beforeApp.getExternalCacheBytes());
        assertMostlyEquals(deltaExternalCache,
                afterUser.getExternalCacheBytes() - beforeUser.getExternalCacheBytes());
    }

    @RequiresFlagsEnabled(Flags.FLAG_GET_APP_BYTES_BY_DATA_TYPE_API)
    public void testVerifyStatsByDataType() throws Exception {
        final ApplicationInfo appInfo = pm.getApplicationInfo(PKG_A, 0);
        useSpace(getContext());

        String appSrcPath = appInfo.sourceDir;
        File appSrcDir = new File(appInfo.sourceDir);
        // sourceDir could return the base.apk with path, in that case, use the parent directory.
        if (appSrcDir.isFile()) {
            appSrcDir = appSrcDir.getParentFile();
        }

        final StorageStats as = stats.queryStatsForPackage(UUID_DEFAULT, PKG_A, user);

        long apkSize = getSizeOfFilesEndWith(appSrcDir, ".apk");
        assertEquals(apkSize, as.getAppBytesByDataType(StorageStats.APP_DATA_TYPE_FILE_TYPE_APK));

        long dmSize = getSizeOfFilesEndWith(appSrcDir, ".dm");
        assertEquals(dmSize, as.getAppBytesByDataType(StorageStats.APP_DATA_TYPE_FILE_TYPE_DM));

        long libSize = getSizeOfDir(new File(appSrcPath + "/lib/"));
        assertEquals(libSize, as.getAppBytesByDataType(StorageStats.APP_DATA_TYPE_LIB));

        // Check the profile sizes if they are fetched by ArtManagedFileStats.
        if (SystemProperties.getBoolean("dalvik.vm.features.art_managed_file_stats", false)) {
            long curProfileBytes =
                as.getAppBytesByDataType(StorageStats.APP_DATA_TYPE_FILE_TYPE_CURRENT_PROFILE);
            File curProfile = new File(new File(CUR_PROFILES_BASE_DIR + appInfo.uid + "/", PKG_A),
                PROFILE_FILE_NAME);
            assertEquals(curProfile.length(), curProfileBytes);

            long refProfileBytes =
                as.getAppBytesByDataType(StorageStats.APP_DATA_TYPE_FILE_TYPE_REFERENCE_PROFILE);
            File refProfile = new File(new File(REF_PROFILES_BASE_DIR, PKG_A), PROFILE_FILE_NAME);
            assertEquals(refProfile.length(), refProfileBytes);
        }
    }

    public void testVerifyStatsMultiple() throws Exception {
        final ApplicationInfo a = pm.getApplicationInfo(PKG_A, 0);
        final ApplicationInfo b = pm.getApplicationInfo(PKG_B, 0);

        final StorageStats as = stats.queryStatsForUid(UUID_DEFAULT, a.uid);
        final StorageStats bs = stats.queryStatsForUid(UUID_DEFAULT, b.uid);

        assertMostlyEquals(DATA_ALL * 2, as.getDataBytes());
        assertMostlyEquals(CACHE_ALL * 2, as.getCacheBytes());

        assertMostlyEquals(DATA_ALL, bs.getDataBytes());
        assertMostlyEquals(CACHE_ALL, bs.getCacheBytes());

        // Since OBB storage space may be shared or isolated between users,
        // we'll accept either expected or double usage.
        try {
            assertMostlyEquals(CODE_ALL * 2, as.getAppBytes(), 5 * MB_IN_BYTES);
            assertMostlyEquals(CODE_ALL * 1, bs.getAppBytes(), 5 * MB_IN_BYTES);
        } catch (AssertionFailedError e) {
            assertMostlyEquals(CODE_ALL * 4, as.getAppBytes(), 5 * MB_IN_BYTES);
            assertMostlyEquals(CODE_ALL * 2, bs.getAppBytes(), 5 * MB_IN_BYTES);
        }
    }

    /**
     * Create some external files of specific media types and ensure that
     * they're tracked correctly.
     */
    public void testVerifyStatsExternal() throws Exception {
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);
        final int uid = android.os.Process.myUid();
        final UserHandle user = UserHandle.getUserHandleForUid(uid);

        final ExternalStorageStats before = stats.queryExternalStatsForUser(UUID_DEFAULT, user);

        final File dir = Environment.getExternalStorageDirectory();
        final File downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        downloadsDir.mkdirs();

        final File image = new File(dir, System.nanoTime() + ".jpg");
        final File video = new File(downloadsDir, System.nanoTime() + ".MP4");
        final File audio = new File(dir, System.nanoTime() + ".png.WaV");
        final File internal = new File(
                getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "test.jpg");

        useWrite(image, 2 * MB_IN_BYTES);
        useWrite(video, 3 * MB_IN_BYTES);
        useWrite(audio, 5 * MB_IN_BYTES);
        useWrite(internal, 7 * MB_IN_BYTES);

        final ExternalStorageStats afterInit = stats.queryExternalStatsForUser(UUID_DEFAULT, user);

        assertMostlyEquals(17 * MB_IN_BYTES, afterInit.getTotalBytes() - before.getTotalBytes());
        assertMostlyEquals(5 * MB_IN_BYTES, afterInit.getAudioBytes() - before.getAudioBytes());
        assertMostlyEquals(3 * MB_IN_BYTES, afterInit.getVideoBytes() - before.getVideoBytes());
        assertMostlyEquals(2 * MB_IN_BYTES, afterInit.getImageBytes() - before.getImageBytes());
        assertMostlyEquals(7 * MB_IN_BYTES, afterInit.getAppBytes() - before.getAppBytes());

        // Rename to ensure that stats are updated
        video.renameTo(new File(dir, System.nanoTime() + ".PnG"));

        // Since we have MANAGE_EXTERNAL_STORAGE, need to ask for a re-scan
        MediaStore.scanFile(getContext().getContentResolver(), dir);
        MediaStore.scanFile(getContext().getContentResolver(), downloadsDir);
        MediaStore.waitForIdle(getContext().getContentResolver());

        final ExternalStorageStats afterRename = stats.queryExternalStatsForUser(UUID_DEFAULT, user);

        assertMostlyEquals(17 * MB_IN_BYTES, afterRename.getTotalBytes() - before.getTotalBytes());
        assertMostlyEquals(5 * MB_IN_BYTES, afterRename.getAudioBytes() - before.getAudioBytes());
        assertMostlyEquals(0 * MB_IN_BYTES, afterRename.getVideoBytes() - before.getVideoBytes());
        assertMostlyEquals(5 * MB_IN_BYTES, afterRename.getImageBytes() - before.getImageBytes());
        assertMostlyEquals(7 * MB_IN_BYTES, afterRename.getAppBytes() - before.getAppBytes());
    }

    /**
     * Measuring external storage manually should always be consistent with
     * whatever the stats APIs are returning.
     */
    public void testVerifyStatsExternalConsistent() throws Exception {
        final StorageStatsManager stats = getContext().getSystemService(StorageStatsManager.class);
        final UserHandle user = android.os.Process.myUserHandle();

        // Since scoped storage, apps can't access the package-specific Android/
        // directories anymore. So we compute the current size, and assume the
        // delta is in Android/*, which unfortunately may have data from
        // test APKs that aren't cleaned up properly.
        //
        // Then, when we compute the new size and compare it with the stats,
        // we expect the same delta
        final long manualSizeBefore = getSizeManual(
                Environment.getExternalStorageDirectory(), true);
        final long statsSizeBefore = stats.queryExternalStatsForUser(
                UUID_DEFAULT, user).getTotalBytes();

        final long deltaBefore = statsSizeBefore - manualSizeBefore;

        useSpace(getContext());

        final File top = Environment.getExternalStorageDirectory();
        final File pics = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        pics.mkdirs();

        useWrite(makeUniqueFile(top), 5 * MB_IN_BYTES);
        useWrite(makeUniqueFile(pics), 5 * MB_IN_BYTES);
        useWrite(makeUniqueFile(pics), 5 * MB_IN_BYTES);

        // for fuse file system
        Thread.sleep(10000);

        // TODO: remove this once 34723223 is fixed
        logCommand("sync");

        long manualSize = getSizeManual(Environment.getExternalStorageDirectory(), true);
        // Since scoped storage, we can't walk the Android/ tree anymore; so pass in this
        // app's files and cache dirs directly.
        manualSize += getSizeManual(getContext().getExternalFilesDir(null), true);
        manualSize += getSizeManual(getContext().getExternalCacheDir(), true);
        final long statsSize = stats.queryExternalStatsForUser(UUID_DEFAULT, user).getTotalBytes();

        final long deltaAfter = statsSize - manualSize;
        assertMostlyEquals(deltaBefore, deltaAfter);
    }

    public void testVerifyCategory() throws Exception {
        final ApplicationInfo a = pm.getApplicationInfo(PKG_A, 0);
        final ApplicationInfo b = pm.getApplicationInfo(PKG_B, 0);

        assertEquals(ApplicationInfo.CATEGORY_VIDEO, a.category);
        assertEquals(ApplicationInfo.CATEGORY_UNDEFINED, b.category);
    }

    public void testCacheClearing() throws Exception {
        final int[] originalCacheReservePercents = new int[2];
        setCacheReservePercentsToZero(originalCacheReservePercents);

        try {
            testCacheClearing(originalCacheReservePercents);
        } finally {
            resetCacheReservePercents(originalCacheReservePercents);
        }
    }

    public void testCacheBehavior() throws Exception {
        final int[] originalCacheReservePercents = new int[2];
        setCacheReservePercentsToZero(originalCacheReservePercents);

        try {
            testCacheBehavior(originalCacheReservePercents);
        } finally {
            resetCacheReservePercents(originalCacheReservePercents);
        }
    }

    private void testCacheClearing(int[] originalCacheReservePercents) throws Exception {
        final Context context = getContext();
        final StorageManager sm = context.getSystemService(StorageManager.class);
        final StorageStatsManager stats = context.getSystemService(StorageStatsManager.class);
        final UserHandle user = android.os.Process.myUserHandle();

        final File filesDir = context.getFilesDir();
        final UUID filesUuid = sm.getUuidForPath(filesDir);
        final String pmUuid = filesUuid.equals(StorageManager.UUID_DEFAULT) ? "internal"
                : filesUuid.toString();

        final long beforeAllocatable = sm.getAllocatableBytes(filesUuid);
        final long beforeFree = stats.getFreeBytes(filesUuid);
        final long beforeRaw = filesDir.getUsableSpace();

        Log.d(TAG, "Before raw " + beforeRaw + ", free " + beforeFree + ", allocatable "
                + beforeAllocatable);

        assertMostlyEquals(0, getCacheBytes(PKG_A, user));
        assertMostlyEquals(0, getCacheBytes(PKG_B, user));

        // Ask apps to allocate some cached data
        final long targetA = doAllocateProvider(PKG_A, 0.5, 1262304000);
        final long targetB = doAllocateProvider(PKG_B, 2.0, 1420070400);
        final long totalAllocated = targetA + targetB;

        MediaStore.waitForIdle(getContext().getContentResolver());

        // Apps using up some cache space shouldn't change how much we can
        // allocate, or how much we think is free; but it should decrease real
        // disk space.
        if (stats.isQuotaSupported(filesUuid)) {
            assertMostlyEquals(beforeAllocatable,
                    sm.getAllocatableBytes(filesUuid), 10 * MB_IN_BYTES);
            assertMostlyEquals(beforeFree,
                    stats.getFreeBytes(filesUuid), 10 * MB_IN_BYTES);
        } else {
            assertMostlyEquals(beforeAllocatable - totalAllocated,
                    sm.getAllocatableBytes(filesUuid), 10 * MB_IN_BYTES);
            assertMostlyEquals(beforeFree - totalAllocated,
                    stats.getFreeBytes(filesUuid), 10 * MB_IN_BYTES);
        }
        assertMostlyEquals(beforeRaw - totalAllocated,
                filesDir.getUsableSpace(), 10 * MB_IN_BYTES);

        assertMostlyEquals(targetA, getCacheBytes(PKG_A, user));
        assertMostlyEquals(targetB, getCacheBytes(PKG_B, user));

        // Allocate some space for ourselves, which should trim away at
        // over-quota app first, even though its files are newer.
        final long clear1 = filesDir.getUsableSpace() + (targetB / 2);
        if (stats.isQuotaSupported(filesUuid)) {
            sm.allocateBytes(filesUuid, clear1);
        } else {
            UiDevice.getInstance(getInstrumentation())
                    .executeShellCommand("pm trim-caches " + clear1 + " " + pmUuid);
        }

        assertMostlyEquals(targetA, getCacheBytes(PKG_A, user));
        assertMostlyEquals(targetB / 2, getCacheBytes(PKG_B, user), 2 * MB_IN_BYTES);

        // Allocate some more space for ourselves, which should now start
        // trimming away at older app. Since we pivot between the two apps once
        // they're tied for cache ratios, we expect to clear about half of the
        // remaining space from each of them.
        final long clear2 = filesDir.getUsableSpace() + (targetB / 2);
        if (stats.isQuotaSupported(filesUuid)) {
            sm.allocateBytes(filesUuid, clear2);
        } else {
            UiDevice.getInstance(getInstrumentation())
                    .executeShellCommand("pm trim-caches " + clear2 + " " + pmUuid);
        }

        assertMostlyEquals(targetA / 2, getCacheBytes(PKG_A, user), 2 * MB_IN_BYTES);
        assertMostlyEquals(targetA / 2, getCacheBytes(PKG_B, user), 2 * MB_IN_BYTES);
    }

    private void testCacheBehavior(int[] originalCacheReservePercents) throws Exception {
        final Context context = getContext();
        final StorageManager sm = context.getSystemService(StorageManager.class);
        final StorageStatsManager stats = context.getSystemService(StorageStatsManager.class);

        final UUID filesUuid = sm.getUuidForPath(context.getFilesDir());
        final String pmUuid = filesUuid.equals(StorageManager.UUID_DEFAULT) ? "internal"
                : filesUuid.toString();

        final File normal = new File(context.getCacheDir(), "normal");
        final File group = new File(context.getCacheDir(), "group");
        final File tomb = new File(context.getCacheDir(), "tomb");

        final long size = 2 * MB_IN_BYTES;

        final long normalTime = 1262304000;
        final long groupTime = 1262303000;
        final long tombTime = 1262302000;

        normal.mkdir();
        group.mkdir();
        tomb.mkdir();

        sm.setCacheBehaviorGroup(group, true);
        sm.setCacheBehaviorTombstone(tomb, true);

        final File a = useFallocate(makeUniqueFile(normal), size, normalTime);
        final File b = useFallocate(makeUniqueFile(normal), size, normalTime);
        final File c = useFallocate(makeUniqueFile(normal), size, normalTime);

        final File d = useFallocate(makeUniqueFile(group), size, groupTime);
        final File e = useFallocate(makeUniqueFile(group), size, groupTime);
        final File f = useFallocate(makeUniqueFile(group), size, groupTime);

        final File g = useFallocate(makeUniqueFile(tomb), size, tombTime);
        final File h = useFallocate(makeUniqueFile(tomb), size, tombTime);
        final File i = useFallocate(makeUniqueFile(tomb), size, tombTime);

        normal.setLastModified(normalTime);
        group.setLastModified(groupTime);
        tomb.setLastModified(tombTime);

        final long clear1 = group.getUsableSpace() + (8 * MB_IN_BYTES);
        if (stats.isQuotaSupported(filesUuid)) {
            sm.allocateBytes(filesUuid, clear1);
        } else {
            UiDevice.getInstance(getInstrumentation())
                    .executeShellCommand("pm trim-caches " + clear1 + " " + pmUuid);
        }

        assertTrue(a.exists());
        assertTrue(b.exists());
        assertTrue(c.exists());
        assertFalse(group.exists());
        assertFalse(d.exists());
        assertFalse(e.exists());
        assertFalse(f.exists());
        assertTrue(g.exists()); assertEquals(0, g.length());
        assertTrue(h.exists()); assertEquals(0, h.length());
        assertTrue(i.exists()); assertEquals(0, i.length());
    }

    /* originalCacheReservePercents is an array of size 2 with CacheReservePercentHigh
    *  at index 0 and CacheReservePercentLow at index 1.
    */
    private void setCacheReservePercentsToZero(int[] originalCacheReservePercents) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            originalCacheReservePercents[0] = DeviceConfig.getInt(
                    DeviceConfig.NAMESPACE_STORAGE_NATIVE_BOOT,
                    StorageManager.CACHE_RESERVE_PERCENT_HIGH_KEY, -1);
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_STORAGE_NATIVE_BOOT,
                    StorageManager.CACHE_RESERVE_PERCENT_HIGH_KEY,
                    Integer.toString(0), /* makeDefault */ false);
            originalCacheReservePercents[1] = DeviceConfig.getInt(
                    DeviceConfig.NAMESPACE_STORAGE_NATIVE_BOOT,
                    StorageManager.CACHE_RESERVE_PERCENT_LOW_KEY, -1);
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_STORAGE_NATIVE_BOOT,
                    StorageManager.CACHE_RESERVE_PERCENT_LOW_KEY,
                    Integer.toString(0), /* makeDefault */ false);
        });
    }

    private void resetCacheReservePercents(int[] originalCacheReservePercents) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_STORAGE_NATIVE_BOOT,
                    StorageManager.CACHE_RESERVE_PERCENT_HIGH_KEY,
                    (originalCacheReservePercents[0] != -1)
                        ? Integer.toString(originalCacheReservePercents[0]) : null,
                    /* makeDefault */ false);
            DeviceConfig.setProperty(
                    DeviceConfig.NAMESPACE_STORAGE_NATIVE_BOOT,
                    StorageManager.CACHE_RESERVE_PERCENT_LOW_KEY,
                    (originalCacheReservePercents[1] != -1)
                        ? Integer.toString(originalCacheReservePercents[1]) : null,
                    /* makeDefault */ false);
        });
    }

    private long getCacheBytes(String pkg, UserHandle user) throws Exception {
        return getContext().getSystemService(StorageStatsManager.class)
                .queryStatsForPackage(UUID_DEFAULT, pkg, user).getCacheBytes();
    }

    private long doAllocateReceiver(String pkg, double fraction, long time) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.setComponent(new ComponentName(pkg, UtilsReceiver.class.getName()));
        intent.putExtra(UtilsReceiver.EXTRA_FRACTION, fraction);
        intent.putExtra(UtilsReceiver.EXTRA_TIME, time);
        final MutableLong bytes = new MutableLong(0);
        getInstrumentation().getTargetContext().sendOrderedBroadcast(intent, null,
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        bytes.value = getResultExtras(false).getLong(UtilsReceiver.EXTRA_BYTES);
                        latch.countDown();
                    }
                }, null, Activity.RESULT_CANCELED, null, null);
        latch.await(30, TimeUnit.SECONDS);
        return bytes.value;
    }

    private long doAllocateProvider(String pkg, double fraction, long time) throws Exception {
        final Bundle args = new Bundle();
        args.putDouble(UtilsReceiver.EXTRA_FRACTION, fraction);
        args.putLong(UtilsReceiver.EXTRA_TIME, time);

        try (final ContentProviderClient client = getContext().getContentResolver()
                .acquireContentProviderClient(pkg)) {
            final Bundle res = client.call(pkg, pkg, args);
            return res.getLong(UtilsReceiver.EXTRA_BYTES);
        }
    }

    private long getSizeOfFilesEndWith(File dir, String suffix) {
        if (!dir.isDirectory()) {
            return 0;
        }

        long size = 0;
        try {
            for (File file : dir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(suffix)) {
                    size += file.length();
                }
            }
        } catch (NullPointerException e) {
            size += 0;
        }

        return size;
    }

    private long getSizeOfDir(File dir) {
        if (!dir.isDirectory()) {
            return 0;
        }

        long size = 0;
        try {
            for (File file : dir.listFiles()) {
                if (file.isFile()) {
                    size += file.length();
                } else if (file.isDirectory()) {
                    size += getSizeOfDir(file);
                }
            }
        } catch (NullPointerException e) {
            size += 0;
        }

        return size;
    }
}
