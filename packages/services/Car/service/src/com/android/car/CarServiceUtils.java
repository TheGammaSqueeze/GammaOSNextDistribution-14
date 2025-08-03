/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.car;

import static android.car.user.CarUserManager.lifecycleEventTypeToString;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.os.Process.INVALID_UID;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.car.Car;
import android.car.builtin.content.ContextHelper;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.common.CommonConstants.UserLifecycleEventType;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyStore;
import java.security.KeyStore.SecretKeyEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Utility class */
public final class CarServiceUtils {

    // https://developer.android.com/reference/java/util/UUID
    private static final int UUID_LENGTH = 16;
    private static final String TAG = CarLog.tagFor(CarServiceUtils.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final String COMMON_HANDLER_THREAD_NAME =
            "CarServiceUtils_COMMON_HANDLER_THREAD";
    private static final byte[] CHAR_POOL_FOR_RANDOM_STRING =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes();

    private static final String PACKAGE_NOT_FOUND = "Package not found:";
    private static final String ANDROID_KEYSTORE_NAME = "AndroidKeyStore";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    /** K: class name, V: HandlerThread */
    private static final ArrayMap<String, HandlerThread> sHandlerThreads = new ArrayMap<>();

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE,
            details = "private constructor")
    private CarServiceUtils() {
        throw new UnsupportedOperationException("contains only static methods");
    }

    /**
     * Returns a byte buffer corresponding to the passed long argument.
     *
     * @param primitive data to convert format.
     */
    public static byte[] longToBytes(long primitive) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(primitive);
        return buffer.array();
    }

    /**
     * Returns a byte buffer corresponding to the passed long argument.
     *
     * @param array data to convert format.
     */
    public static long bytesToLong(byte[] array) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
        buffer.put(array);
        buffer.flip();
        long value = buffer.getLong();
        return value;
    }

    /**
     * Returns a String in Hex format that is formed from the bytes in the byte array
     * Useful for debugging
     *
     * @param array the byte array
     * @return the Hex string version of the input byte array
     */
    public static String byteArrayToHexString(byte[] array) {
        StringBuilder sb = new StringBuilder(array.length * 2);
        for (byte b : array) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert UUID to Big Endian byte array
     *
     * @param uuid UUID to convert
     * @return the byte array representing the UUID
     */
    @NonNull
    public static byte[] uuidToBytes(@NonNull UUID uuid) {

        return ByteBuffer.allocate(UUID_LENGTH)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    /**
     * Convert Big Endian byte array to UUID
     *
     * @param bytes byte array to convert
     * @return the UUID representing the byte array, or null if not a valid UUID
     */
    @Nullable
    public static UUID bytesToUUID(@NonNull byte[] bytes) {
        if (bytes.length != UUID_LENGTH) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * Generate a random zero-filled string of given length
     *
     * @param length of string
     * @return generated string
     */
    @SuppressLint("DefaultLocale")  // Should always have the same format regardless of locale
    public static String generateRandomNumberString(int length) {
        return String.format("%0" + length + "d",
                ThreadLocalRandom.current().nextInt((int) Math.pow(10, length)));
    }

    /**
     * Concatentate the given 2 byte arrays
     *
     * @param a input array 1
     * @param b input array 2
     * @return concatenated array of arrays 1 and 2
     */
    @Nullable
    public static byte[] concatByteArrays(@Nullable byte[] a, @Nullable byte[] b) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            if (a != null) {
                outputStream.write(a);
            }
            if (b != null) {
                outputStream.write(b);
            }
        } catch (IOException e) {
            return null;
        }
        return outputStream.toByteArray();
    }

    /**
     * Returns the content resolver for the given user. This can be used to put/get the
     * user's settings.
     *
     * @param context The context of the package.
     * @param userId The id of the user which the content resolver is being requested for. It also
     * accepts {@link UserHandle#USER_CURRENT}.
     */
    public static ContentResolver getContentResolverForUser(Context context,
            @UserIdInt int userId) {
        if (userId == UserHandle.CURRENT.getIdentifier()) {
            userId = ActivityManager.getCurrentUser();
        }
        return context
                .createContextAsUser(
                        UserHandle.of(userId), /* flags= */ 0)
                .getContentResolver();
    }

    /**
     * Checks if the type of the {@code event} matches {@code expectedType}.
     *
     * @param tag The tag for logging.
     * @param event The event to check the type against {@code expectedType}.
     * @param expectedType The expected event type.
     * @return true if {@code event}'s type matches {@code expectedType}.
     *         Otherwise, log a wtf and return false.
     */
    public static boolean isEventOfType(String tag, UserLifecycleEvent event,
            @UserLifecycleEventType int expectedType) {
        if (event.getEventType() == expectedType) {
            return true;
        }
        Slogf.wtf(tag, "Received an unexpected event: %s. Expected type: %s.", event,
                lifecycleEventTypeToString(expectedType));
        return false;
    }

    /**
     * Checks if the type of the {@code event} is one of the types in {@code expectedTypes}.
     *
     * @param tag The tag for logging.
     * @param event The event to check the type against {@code expectedTypes}.
     * @param expectedTypes The expected event types. Must not be empty.
     * @return true if {@code event}'s type can be found in {@code expectedTypes}.
     *         Otherwise, log a wtf and return false.
     */
    public static boolean isEventAnyOfTypes(String tag, UserLifecycleEvent event,
            @UserLifecycleEventType int... expectedTypes) {
        for (int i = 0; i < expectedTypes.length; i++) {
            if (event.getEventType() == expectedTypes[i]) {
                return true;
            }
        }
        Slogf.wtf(tag, "Received an unexpected event: %s. Expected types: [%s]", event,
                Arrays.stream(expectedTypes).mapToObj(t -> lifecycleEventTypeToString(t)).collect(
                        Collectors.joining(",")));
        return false;
    }

    /**
     * Checks if the calling UID owns the give package.
     *
     * @throws SecurityException if the calling UID doesn't own the given package.
     */
    public static void checkCalledByPackage(Context context, String packageName) {
        int callingUid = Binder.getCallingUid();
        PackageManager pm = context.getPackageManager();
        int uidFromPm = INVALID_UID;
        try {
            uidFromPm = PackageManagerHelper.getPackageUidAsUser(pm, packageName,
                    UserManagerHelper.getUserId(callingUid));
        } catch (PackageManager.NameNotFoundException e) {
            String msg = PACKAGE_NOT_FOUND + packageName;
            throw new SecurityException(msg, e);
        }

        if (uidFromPm != callingUid) {
            throw new SecurityException(
                    "Package " + packageName + " is not associated to UID " + callingUid);
        }
    }

    /**
     * Execute a runnable on the main thread
     *
     * @param action The code to run on the main thread.
     */
    public static void runOnMain(Runnable action) {
        runOnLooper(Looper.getMainLooper(), action);
    }

    /**
     * Execute a runnable in the given looper
     * @param looper Looper to run the action.
     * @param action The code to run.
     */
    public static void runOnLooper(Looper looper, Runnable action) {
        new Handler(looper).post(action);
    }

    /**
     * Execute an empty runnable in the looper of the handler thread
     * specified by the name.
     *
     * @param name Name of the handler thread in which to run the empty
     *             runnable.
     */
    public static void runEmptyRunnableOnLooperSync(String name) {
        runOnLooperSync(getHandlerThread(name).getLooper(), () -> {});
    }

    /**
     * Execute a call on the application's main thread, blocking until it is
     * complete.  Useful for doing things that are not thread-safe, such as
     * looking at or modifying the view hierarchy.
     *
     * @param action The code to run on the main thread.
     */
    public static void runOnMainSync(Runnable action) {
        runOnLooperSync(Looper.getMainLooper(), action);
    }

    /**
     * Execute a delayed call on the application's main thread, blocking until it is
     * complete. See {@link #runOnMainSync(Runnable)}
     *
     * @param action The code to run on the main thread.
     * @param delayMillis The delay (in milliseconds) until the Runnable will be executed.
     */
    public static void runOnMainSyncDelayed(Runnable action, long delayMillis) {
        runOnLooperSyncDelayed(Looper.getMainLooper(), action, delayMillis);
    }

    /**
     * Execute a call on the given Looper thread, blocking until it is
     * complete.
     *
     * @param looper Looper to run the action.
     * @param action The code to run on the looper thread.
     */
    public static void runOnLooperSync(Looper looper, Runnable action) {
        runOnLooperSyncDelayed(looper, action, /* delayMillis */ 0L);
    }

    /**
     * Executes a delayed call on the given Looper thread, blocking until it is complete.
     *
     * @param looper Looper to run the action.
     * @param action The code to run on the looper thread.
     * @param delayMillis The delay (in milliseconds) until the Runnable will be executed.
     */
    public static void runOnLooperSyncDelayed(Looper looper, Runnable action, long delayMillis) {
        if (Looper.myLooper() == looper) {
            // requested thread is the same as the current thread. call directly.
            action.run();
        } else {
            Handler handler = new Handler(looper);
            SyncRunnable sr = new SyncRunnable(action);
            handler.postDelayed(sr, delayMillis);
            sr.waitForComplete();
        }
    }

    /**
     * Executes a runnable on the common thread. Useful for doing any kind of asynchronous work
     * across the car related code that doesn't need to be on the main thread.
     *
     * @param action The code to run on the common thread.
     */
    public static void runOnCommon(Runnable action) {
        runOnLooper(getCommonHandlerThread().getLooper(), action);
    }

    private static final class SyncRunnable implements Runnable {
        private final Runnable mTarget;
        private volatile boolean mComplete = false;

        public SyncRunnable(Runnable target) {
            mTarget = target;
        }

        @Override
        public void run() {
            mTarget.run();
            synchronized (this) {
                mComplete = true;
                notifyAll();
            }
        }

        public void waitForComplete() {
            synchronized (this) {
                while (!mComplete) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    public static float[] toFloatArray(List<Float> list) {
        int size = list.size();
        float[] array = new float[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    public static long[] toLongArray(List<Long> list) {
        int size = list.size();
        long[] array = new long[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    public static int[] toIntArray(List<Integer> list) {
        int size = list.size();
        int[] array = new int[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    /**
     * Converts array to an array list
     */
    public static ArrayList<Integer> asList(int[] array) {
        Preconditions.checkArgument(array != null, "Array to convert to list can not be null");
        int size = array.length;
        ArrayList<Integer> results = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            results.add(array[i]);
        }
        return results;
    }

    public static byte[] toByteArray(List<Byte> list) {
        int size = list.size();
        byte[] array = new byte[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    /**
     * Converts values array to array set
     */
    public static ArraySet<Integer> toIntArraySet(int[] values) {
        Preconditions.checkArgument(values != null,
                "Values to convert to array set must not be null");
        ArraySet<Integer> set = new ArraySet<>(values.length);
        for (int c = 0; c < values.length; c++) {
            set.add(values[c]);
        }

        return set;
    }

    /**
     * Returns delta between elapsed time to uptime = {@link SystemClock#elapsedRealtime()} -
     * {@link SystemClock#uptimeMillis()}. Note that this value will be always >= 0.
     */
    public static long getUptimeToElapsedTimeDeltaInMillis() {
        int retry = 0;
        int max_retry = 2; // try only up to twice
        while (true) {
            long elapsed1 = SystemClock.elapsedRealtime();
            long uptime = SystemClock.uptimeMillis();
            long elapsed2 = SystemClock.elapsedRealtime();
            if (elapsed1 == elapsed2) { // avoid possible 1 ms fluctuation.
                return elapsed1 - uptime;
            }
            retry++;
            if (retry >= max_retry) {
                return elapsed1 - uptime;
            }
        }
    }

    /**
     * Gets a static instance of {@code HandlerThread} for the given {@code name}. If the thread
     * does not exist, create one and start it before returning.
     */
    public static HandlerThread getHandlerThread(String name) {
        synchronized (sHandlerThreads) {
            HandlerThread thread = sHandlerThreads.get(name);
            if (thread == null || !thread.isAlive()) {
                Slogf.i(TAG, "Starting HandlerThread:" + name);
                thread = new HandlerThread(name);
                thread.start();
                sHandlerThreads.put(name, thread);
            }
            return thread;
        }
    }

    /**
     * Gets the static instance of the common {@code HandlerThread} meant to be used across
     * CarService.
     */
    public static HandlerThread getCommonHandlerThread() {
        return getHandlerThread(COMMON_HANDLER_THREAD_NAME);
    }

    /**
     * Finishes all queued {@code Handler} tasks for {@code HandlerThread} created via
     * {@link#getHandlerThread(String)}. This is useful only for testing.
     */
    @VisibleForTesting
    public static void finishAllHandlerTasks() {
        ArrayList<HandlerThread> threads;
        synchronized (sHandlerThreads) {
            threads = new ArrayList<>(sHandlerThreads.values());
        }
        ArrayList<SyncRunnable> syncs = new ArrayList<>(threads.size());
        for (int i = 0; i < threads.size(); i++) {
            if (!threads.get(i).isAlive()) {
                continue;
            }
            Handler handler = new Handler(threads.get(i).getLooper());
            SyncRunnable sr = new SyncRunnable(() -> { });
            if (handler.post(sr)) {
                // Track the threads only where SyncRunnable is posted successfully.
                syncs.add(sr);
            }
        }
        for (int i = 0; i < syncs.size(); i++) {
            syncs.get(i).waitForComplete();
        }
    }

    /**
     * Assert if binder call is coming from system process like system server or if it is called
     * from its own process even if it is not system. The latter can happen in test environment.
     * Note that car service runs as system user but test like car service test will not.
     */
    public static void assertCallingFromSystemProcessOrSelf() {
        if (isCallingFromSystemProcessOrSelf()) {
            throw new SecurityException("Only allowed from system or self");
        }
    }

    /**
     * @return true if binder call is coming from system process like system server or if it is
     * called from its own process even if it is not system.
     */
    public static boolean isCallingFromSystemProcessOrSelf() {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        return uid != Process.SYSTEM_UID && pid != Process.myPid();
    }


    /** Utility for checking permission */
    public static void assertVehicleHalMockPermission(Context context) {
        assertPermission(context, Car.PERMISSION_MOCK_VEHICLE_HAL);
    }

    /** Utility for checking permission */
    public static void assertNavigationManagerPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_NAVIGATION_MANAGER);
    }

    /** Utility for checking permission */
    public static void assertClusterManagerPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL);
    }

    /** Utility for checking permission */
    public static void assertPowerPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_POWER);
    }

    /** Utility for checking permission */
    public static void assertProjectionPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_PROJECTION);
    }

    /** Verify the calling context has the {@link Car#PERMISSION_CAR_PROJECTION_STATUS} */
    public static void assertProjectionStatusPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_PROJECTION_STATUS);
    }

    /** Utility for checking permission */
    public static void assertAnyDiagnosticPermission(Context context) {
        assertAnyPermission(context,
                Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL,
                Car.PERMISSION_CAR_DIAGNOSTIC_CLEAR);
    }

    /** Utility for checking permission */
    public static void assertDrivingStatePermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_DRIVING_STATE);
    }

    /**
     * Verify the calling context has either {@link Car#PERMISSION_VMS_SUBSCRIBER} or
     * {@link Car#PERMISSION_VMS_PUBLISHER}
     */
    public static void assertAnyVmsPermission(Context context) {
        assertAnyPermission(context,
                Car.PERMISSION_VMS_SUBSCRIBER,
                Car.PERMISSION_VMS_PUBLISHER);
    }

    /** Utility for checking permission */
    public static void assertVmsPublisherPermission(Context context) {
        assertPermission(context, Car.PERMISSION_VMS_PUBLISHER);
    }

    /** Utility for checking permission */
    public static void assertVmsSubscriberPermission(Context context) {
        assertPermission(context, Car.PERMISSION_VMS_SUBSCRIBER);
    }

    /** Utility for checking permission */
    public static void assertPermission(Context context, String permission) {
        if (context.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("requires " + permission);
        }
    }

    /**
     * Checks to see if the caller has a permission.
     *
     * @return boolean TRUE if caller has the permission.
     */
    public static boolean hasPermission(Context context, String permission) {
        return context.checkCallingOrSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Utility for checking permission */
    public static void assertAnyPermission(Context context, String... permissions) {
        for (String permission : permissions) {
            if (context.checkCallingOrSelfPermission(permission)
                    == PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        throw new SecurityException("requires any of " + Arrays.toString(permissions));
    }

    /**
     * Turns a {@code SubscribeOptions} to {@code
     * android.hardware.automotive.vehicle.V2_0.SubscribeOptions}
     */
    public static android.hardware.automotive.vehicle.V2_0.SubscribeOptions subscribeOptionsToHidl(
            SubscribeOptions options) {
        android.hardware.automotive.vehicle.V2_0.SubscribeOptions hidlOptions =
                new android.hardware.automotive.vehicle.V2_0.SubscribeOptions();
        hidlOptions.propId = options.propId;
        hidlOptions.sampleRate = options.sampleRate;
        // HIDL backend requires flags to be set although it is not used any more.
        hidlOptions.flags = android.hardware.automotive.vehicle.V2_0.SubscribeFlags.EVENTS_FROM_CAR;
        // HIDL backend does not support area IDs, so we ignore options.areaId field.
        return hidlOptions;
    }

    /**
     * Returns {@code true} if the current configuration supports multiple users on multiple
     * displays.
     */
    public static boolean isMultipleUsersOnMultipleDisplaysSupported(UserManager userManager) {
        return UserManagerHelper.isVisibleBackgroundUsersSupported(userManager);
    }

    /**
     * Returns {@code true} if the current configuration supports visible background users on
     * default display.
     */
    public static boolean isVisibleBackgroundUsersOnDefaultDisplaySupported(
            UserManager userManager) {
        return UserManagerHelper.isVisibleBackgroundUsersOnDefaultDisplaySupported(userManager);
    }

    /**
     * Starts Activity for the given {@code userId} and {@code displayId}.
     *
     * @return {@code true} when starting activity succeeds. It can fail in situation like secondary
     *         home package not existing.
     */
    public static boolean startHomeForUserAndDisplay(Context context,
            @UserIdInt int userId, int displayId) {
        if (DBG) {
            Slogf.d(TAG, "Starting HOME for user: %d, display:%d", userId, displayId);
        }
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        ActivityOptions activityOptions = ActivityOptions.makeBasic()
                .setLaunchDisplayId(displayId);
        try {
            ContextHelper.startActivityAsUser(context, homeIntent, activityOptions.toBundle(),
                    UserHandle.of(userId));
            if (DBG) {
                Slogf.d(TAG, "Started HOME for user: %d, display:%d", userId, displayId);
            }
            return true;
        } catch (Exception e) {
            Slogf.w(TAG, e, "Cannot start HOME for user: %d, display:%d", userId, displayId);
            return false;
        }
    }

    /**
     * Starts SystemUI component for a particular user - should be called for non-current user only.
     *
     * @return {@code true} when starting service succeeds. It can fail in situation like the
     * SystemUI service component not being defined.
     */
    public static boolean startSystemUiForUser(Context context, @UserIdInt int userId) {
        if (DBG) Slogf.d(TAG, "Start SystemUI for user: %d", userId);
        Preconditions.checkArgument(userId != UserHandle.SYSTEM.getIdentifier(),
                "Cannot start SystemUI for the system user");
        Preconditions.checkArgument(userId != ActivityManager.getCurrentUser(),
                "Cannot start SystemUI for the current foreground user");

        // TODO (b/261192740): add EventLog for SystemUI starting
        ComponentName sysuiComponent = PackageManagerHelper.getSystemUiServiceComponent(context);
        Intent sysUIIntent = new Intent().setComponent(sysuiComponent);
        try {
            context.bindServiceAsUser(sysUIIntent, sEmptyServiceConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT, UserHandle.of(userId));
            return true;
        } catch (Exception e) {
            Slogf.w(TAG, e, "Cannot start SysUI component %s for user %d", sysuiComponent,
                    userId);
            return false;
        }
    }

    // The callbacks are not called actually, because SystemUI returns null for IBinder.
    private static final ServiceConnection sEmptyServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {}

        @Override
        public void onServiceDisconnected(ComponentName name) {}
    };

    /**
     * Stops the SystemUI component for a particular user - this function should not be called
     * for the system user.
     */
    public static void stopSystemUiForUser(Context context, @UserIdInt int userId) {
        Preconditions.checkArgument(userId != UserHandle.SYSTEM.getIdentifier(),
                "Cannot stop SystemUI for the system user");
        // TODO (b/261192740): add EventLog for SystemUI stopping
        String sysUiPackage = PackageManagerHelper.getSystemUiPackageName(context);
        PackageManagerHelper.forceStopPackageAsUserEvenWhenStopping(context, sysUiPackage, userId);
    }

    /**
     * Starts UserPickerActivity for the given {@code userId} and {@code displayId}.
     *
     * @return {@code true} when starting activity succeeds. It can fail in situation like
     * package not existing.
     */
    public static boolean startUserPickerOnDisplay(Context context,
            int displayId, String userPickerActivityPackage) {
        if (DBG) {
            Slogf.d(TAG, "Starting user picker on display:%d", displayId);
        }
        // FLAG_ACTIVITY_MULTIPLE_TASK ensures the user picker can show up on multiple displays.
        Intent intent = new Intent()
                .setComponent(ComponentName.unflattenFromString(
                    userPickerActivityPackage))
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("data://com.android.car/userpicker/display" + displayId));
        ActivityOptions activityOptions = ActivityOptions.makeBasic()
                .setLaunchDisplayId(displayId);
        try {
            // Start the user picker as user 0.
            ContextHelper.startActivityAsUser(context, intent, activityOptions.toBundle(),
                    UserHandle.SYSTEM);
            return true;
        } catch (Exception e) {
            Slogf.w(TAG, e, "Cannot start user picker as user 0 on display:%d", displayId);
            return false;
        }
    }

    /**
     * Generates a random string which consists of captial letters and numbers.
     */
    @SuppressLint("DefaultLocale")  // Should always have the same format regardless of locale
    public static String generateRandomAlphaNumericString(int length) {
        StringBuilder sb = new StringBuilder();

        int poolSize = CHAR_POOL_FOR_RANDOM_STRING.length;
        for (int i = 0; i < length; i++) {
            sb.append(CHAR_POOL_FOR_RANDOM_STRING[ThreadLocalRandom.current().nextInt(poolSize)]);
        }
        return sb.toString();
    }

    /**
     * Encrypts byte array with the keys stored in {@code keyAlias} using AES.
     *
     * @return Encrypted data and initialization vector in {@link EncryptedData}. {@code null} in
     *         case of errors.
     */
    @Nullable
    public static EncryptedData encryptData(byte[] data, String keyAlias) {
        SecretKey secretKey = getOrCreateSecretKey(keyAlias);
        if (secretKey == null) {
            Slogf.e(TAG, "Failed to encrypt data: cannot get a secret key (keyAlias: %s)",
                    keyAlias);
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return new EncryptedData(cipher.doFinal(data), cipher.getIV());
        } catch (Exception e) {
            Slogf.e(TAG, e, "Failed to encrypt data: keyAlias=%s", keyAlias);
            return null;
        }
    }

    /**
     * Decrypts byte array with the keys stored in {@code keyAlias} using AES.
     *
     * @return Decrypted data in byte array. {@code null} in case of errors.
     */
    @Nullable
    public static byte[] decryptData(EncryptedData data, String keyAlias) {
        SecretKey secretKey = getOrCreateSecretKey(keyAlias);
        if (secretKey == null) {
            Slogf.e(TAG, "Failed to decrypt data: cannot get a secret key (keyAlias: %s)",
                    keyAlias);
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, data.getIv());
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            return cipher.doFinal(data.getEncryptedData());
        } catch (Exception e) {
            Slogf.e(TAG, e, "Failed to decrypt data: keyAlias=%s", keyAlias);
            return null;
        }
    }

    /**
     * Class to hold encrypted data and its initialization vector.
     */
    public static final class EncryptedData {
        private final byte[] mEncryptedData;
        private final byte[] mIv;

        public EncryptedData(byte[] encryptedData, byte[] iv) {
            mEncryptedData = encryptedData;
            mIv = iv;
        }

        public byte[] getEncryptedData() {
            return mEncryptedData;
        }

        public byte[] getIv() {
            return mIv;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof EncryptedData)) return false;
            EncryptedData data = (EncryptedData) other;
            return Arrays.equals(mEncryptedData, data.mEncryptedData)
                    && Arrays.equals(mIv, data.mIv);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(mEncryptedData), Arrays.hashCode(mIv));
        }
    }

    @Nullable
    private static SecretKey getOrCreateSecretKey(String keyAlias) {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_NAME);
            keyStore.load(/* KeyStore.LoadStoreParameter= */ null);
            if (keyStore.containsAlias(keyAlias)) {
                SecretKeyEntry secretKeyEntry = (SecretKeyEntry) keyStore.getEntry(keyAlias,
                        /* protParam= */ null);
                if (secretKeyEntry != null) {
                    return secretKeyEntry.getSecretKey();
                }
                Slogf.e(TAG, "Android key store contains the alias (%s) but the secret key "
                        + "entry is null", keyAlias);
                return null;
            }
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_NAME);
            KeyGenParameterSpec keyGenParameterSpec =
                    new KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT
                            | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build();
            keyGenerator.init(keyGenParameterSpec);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            Slogf.e(TAG, "Failed to get or create a secret key for the alias (%s)", keyAlias);
            return null;
        }
    }
}
