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
package com.android.car;

import static android.car.CarOccupantZoneManager.INVALID_USER_ID;
import static android.car.CarOccupantZoneManager.OccupantZoneInfo.INVALID_ZONE_ID;
import static android.car.builtin.os.UserManagerHelper.getMaxRunningUsers;
import static android.car.media.CarMediaIntents.EXTRA_MEDIA_COMPONENT;
import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_BROWSE;
import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_INVISIBLE;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_VISIBLE;

import static com.android.car.CarServiceUtils.assertPermission;
import static com.android.car.CarServiceUtils.getCommonHandlerThread;
import static com.android.car.CarServiceUtils.getHandlerThread;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TestApi;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.usage.UsageStatsManager;
import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimeUtils;
import android.car.builtin.util.UsageStatsManagerHelper;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.PowerComponent;
import android.car.media.CarMediaManager;
import android.car.media.CarMediaManager.MediaSourceMode;
import android.car.media.ICarMedia;
import android.car.media.ICarMediaSourceListener;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.session.MediaController;
import android.media.session.MediaController.TransportControls;
import android.media.session.MediaSession;
import android.media.session.MediaSession.Token;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;
import android.view.KeyEvent;

import com.android.car.CarInputService.KeyEventListener;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.os.HandlerExecutor;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;
import com.android.car.user.UserHandleHelper;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CarMediaService manages the currently active media source for car apps. This is different from
 * the MediaSessionManager's active sessions, as there can only be one active source in the car,
 * through both browse and playback.
 *
 * In the car, the active media source does not necessarily have an active MediaSession, e.g. if
 * it were being browsed only. However, that source is still considered the active source, and
 * should be the source displayed in any Media related UIs (Media Center, home screen, etc).
 */
public final class CarMediaService extends ICarMedia.Stub implements CarServiceBase {

    private static final String TAG = CarLog.TAG_MEDIA;
    private static final boolean DEBUG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final String SOURCE_KEY = "media_source_component";
    private static final String SOURCE_KEY_SEPARATOR = "_";
    private static final String PLAYBACK_STATE_KEY = "playback_state";
    private static final String SHARED_PREF = "com.android.car.media.car_media_service";
    private static final String COMPONENT_NAME_SEPARATOR = ",";
    private static final String MEDIA_CONNECTION_ACTION = "com.android.car.media.MEDIA_CONNECTION";
    private static final String EXTRA_AUTOPLAY = "com.android.car.media.autoplay";
    private static final String LAST_UPDATE_KEY = "last_update";

    private static final int MEDIA_SOURCE_MODES = 2;

    // XML configuration options for autoplay on media source change.
    private static final int AUTOPLAY_CONFIG_NEVER = 0;
    private static final int AUTOPLAY_CONFIG_ALWAYS = 1;
    // This mode uses the current source's last stored playback state to resume playback
    private static final int AUTOPLAY_CONFIG_RETAIN_PER_SOURCE = 2;
    // This mode uses the previous source's playback state to resume playback
    private static final int AUTOPLAY_CONFIG_RETAIN_PREVIOUS = 3;

    private final Context mContext;
    private final CarOccupantZoneService mOccupantZoneService;
    private final CarUserService mUserService;
    private final CarPowerManagementService mPowerManagementService;
    private final UserManager mUserManager;
    private final MediaSessionManager mMediaSessionManager;
    private final UsageStatsManager mUsageStatsManager;

    /**
     * An array to store all per-user media data.
     *
     * <p>In most cases there will be one entry for the current user.
     * On a {@link UserManager#isUsersOnSecondaryDisplaysSupported() MUMD} (multi-user
     * multi-display) device, there will be multiple entries, one per each visible user.
     */
    // TODO(b/262734537) Specify the initial capacity.
    @GuardedBy("mLock")
    private final SparseArray<UserMediaPlayContext> mUserMediaPlayContexts;

    // NOTE: must use getSharedPrefsForWriting() to write to it
    private SharedPreferences mSharedPrefs;
    private int mPlayOnMediaSourceChangedConfig;
    private int mPlayOnBootConfig;
    private boolean mDefaultIndependentPlaybackConfig;

    private final Handler mCommonThreadHandler = new Handler(
            getCommonHandlerThread().getLooper());

    private final HandlerThread mHandlerThread  = getHandlerThread(
            getClass().getSimpleName());
    // Handler to receive PlaybackState callbacks from the active media controller.
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    private final Object mLock = new Object();

    private final IntentFilter mPackageUpdateFilter;

    /**
     * Listens to {@link Intent#ACTION_PACKAGE_REMOVED}, so we can fall back to a previously used
     * media source when the active source is uninstalled.
     */
    // TODO(b/262734537) Refactor this receiver using PackageMonitor.
    private final BroadcastReceiver mPackageUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getData() == null) {
                return;
            }
            String intentPackage = intent.getData().getSchemeSpecificPart();
            if (DEBUG) {
                Slogf.d(TAG, "Received a package update for package: %s, action: %s",
                        intentPackage, intent.getAction());
            }
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                synchronized (mLock) {
                    int userArraySize = mUserMediaPlayContexts.size();
                    for (int i = 0; i < userArraySize; i++) {
                        int userId = mUserMediaPlayContexts.keyAt(i);
                        UserMediaPlayContext userMediaContext = mUserMediaPlayContexts.valueAt(i);
                        ComponentName[] primaryComponents =
                                userMediaContext.mPrimaryMediaComponents;
                        for (int j = 0; j < MEDIA_SOURCE_MODES; j++) {
                            if (primaryComponents[j] != null
                                    && primaryComponents[j].getPackageName().equals(
                                    intentPackage)) {
                                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                                    // If package is being replaced, it may not be removed from
                                    // PackageManager queries when we check for available
                                    // MediaBrowseServices, so we iterate to find the next available
                                    // source.
                                    for (ComponentName component
                                            : getLastMediaSourcesInternal(j, userId)) {
                                        if (!primaryComponents[j].getPackageName()
                                                .equals(component.getPackageName())) {
                                            userMediaContext.mRemovedMediaSourceComponents[j] =
                                                    primaryComponents[j];
                                            if (DEBUG) {
                                                Slogf.d(TAG, "temporarily replacing updated media "
                                                                + "source %s for user %d with "
                                                                + "backup source: %s",
                                                        primaryComponents[j], userId, component);
                                            }
                                            setPrimaryMediaSource(component, j, userId);
                                            return;
                                        }
                                    }
                                    Slogf.e(TAG, "No available backup media source for user %d",
                                            userId);
                                } else {
                                    if (DEBUG) {
                                        Slogf.d(TAG, "replacing removed media source"
                                                + " %s with backup source: %s for user %d",
                                                primaryComponents[j],
                                                getLastMediaSource(j, userId), userId);
                                    }
                                    userMediaContext.mRemovedMediaSourceComponents[j] = null;
                                    setPrimaryMediaSource(getLastMediaSource(j, userId), j, userId);
                                }
                            }
                        }
                    }
                }
            } else if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())
                    || Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                synchronized (mLock) {
                    int userArraySize = mUserMediaPlayContexts.size();
                    for (int i = 0; i < userArraySize; i++) {
                        int userId = mUserMediaPlayContexts.keyAt(i);
                        ComponentName[] removedComponents =
                                getRemovedMediaSourceComponentsForUser(userId);
                        for (int j = 0; j < MEDIA_SOURCE_MODES; j++) {
                            if (removedComponents[j] != null && removedComponents[j]
                                    .getPackageName().equals(intentPackage)) {
                                if (DEBUG) {
                                    Slogf.d(TAG, "restoring removed source: %s for user %d",
                                            removedComponents[j], userId);
                                }
                                setPrimaryMediaSource(removedComponents[j], j, userId);
                            }
                        }
                    }
                }
            }
        }
    };

    private final UserLifecycleListener mUserLifecycleListener = event -> {
        if (DEBUG) {
            Slogf.d(TAG, "CarMediaService.onEvent(%s)", event);
        }

        // Note that we receive different event types based on the platform version, beacause of
        // the way we build the filter when registering the listener.
        //
        // Before U:
        //   Receives USER_SWITCHING and USER UNLOCKED
        // U and after:
        //   Receives USER_VISIBLE, USER_INVISIBLE, and USER_UNLOCKED
        //
        // See the constructor of this class to see how the UserLifecycleEventFilter is built
        // differently based on the platform version.
        switch (event.getEventType()) {
            case USER_LIFECYCLE_EVENT_TYPE_SWITCHING:
                onUserSwitch(event.getPreviousUserId(), event.getUserId());
                break;
            case USER_LIFECYCLE_EVENT_TYPE_VISIBLE:
                onUserVisible(event.getUserId());
                break;
            case USER_LIFECYCLE_EVENT_TYPE_UNLOCKED:
                onUserUnlocked(event.getUserId());
                break;
            case USER_LIFECYCLE_EVENT_TYPE_INVISIBLE:
                onUserInvisible(event.getUserId());
                break;
            default:
                break;
        }
    };

    private final ICarPowerPolicyListener mPowerPolicyListener =
            new ICarPowerPolicyListener.Stub() {
                @Override
                public void onPolicyChanged(CarPowerPolicy appliedPolicy,
                        CarPowerPolicy accumulatedPolicy) {
                    boolean shouldBePlaying;
                    MediaController mediaController;
                    boolean isOff = !accumulatedPolicy.isComponentEnabled(PowerComponent.MEDIA);
                    Slogf.i(TAG, "onPolicyChanged(); MEDIA is " + (isOff ? "OFF" : "ON"));
                    synchronized (mLock) {
                        int userArraySize = mUserMediaPlayContexts.size();
                        // Apply power policy to all users.
                        for (int i = 0; i < userArraySize; i++) {
                            int userId = mUserMediaPlayContexts.keyAt(i);
                            UserMediaPlayContext userMediaContext =
                                    mUserMediaPlayContexts.valueAt(i);
                            boolean isUserPlaying = (userMediaContext.mCurrentPlaybackState
                                    == PlaybackState.STATE_PLAYING);
                            userMediaContext.mIsDisabledByPowerPolicy = isOff;
                            if (isOff) {
                                if (!userMediaContext.mWasPreviouslyDisabledByPowerPolicy) {
                                    // We're disabling media component.
                                    // Remember if we are playing at this transition.
                                    userMediaContext.mWasPlayingBeforeDisabled = isUserPlaying;
                                    userMediaContext.mWasPreviouslyDisabledByPowerPolicy = true;
                                }
                                shouldBePlaying = false;
                            } else {
                                userMediaContext.mWasPreviouslyDisabledByPowerPolicy = false;
                                shouldBePlaying = userMediaContext.mWasPlayingBeforeDisabled;
                            }
                            if (shouldBePlaying == isUserPlaying) {
                                return;
                            }
                            ComponentName source = userMediaContext.mPrimaryMediaComponents[
                                    MEDIA_SOURCE_MODE_PLAYBACK];
                            try {
                                if (DEBUG) {
                                    Slogf.d(TAG, "Starting media connector service from power "
                                            + "policy listener. source:%s, playing:%b, userId:%d",
                                            source, shouldBePlaying, userId);
                                }
                                startMediaConnectorServiceLocked(
                                        source, shouldBePlaying, userId);
                            } catch (Exception e) {
                                Slogf.e(TAG, e, "onPolicyChanged(): Failed to "
                                        + "startMediaConnectorService. Source:%s user:%d",
                                        source, userId);
                            }
                            // Should still pause the media when shouldBePlaying is false.
                            if (!shouldBePlaying) {
                                mediaController = userMediaContext.mActiveMediaController;
                                if (mediaController == null) {
                                    if (DEBUG) {
                                        Slogf.d(TAG,
                                                "No active media controller for user %d. "
                                                + "Power policy change does not affect this user's "
                                                + "media.", userId);
                                    }
                                    return;
                                }
                                mediaController.getTransportControls().pause();
                            }
                        }
                    }
                }
    };

    private final UserHandleHelper mUserHandleHelper;

    public CarMediaService(Context context, CarOccupantZoneService occupantZoneService,
            CarUserService userService, CarPowerManagementService powerManagementService) {
        this(context, occupantZoneService, userService, powerManagementService,
                new UserHandleHelper(context, context.getSystemService(UserManager.class)));
    }

    @VisibleForTesting
    public CarMediaService(Context context, CarOccupantZoneService occupantZoneService,
            CarUserService userService, CarPowerManagementService powerManagementService,
            @NonNull UserHandleHelper userHandleHelper) {
        mContext = context;
        mUserManager = mContext.getSystemService(UserManager.class);
        mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);
        mUsageStatsManager = mContext.getSystemService(UsageStatsManager.class);
        mDefaultIndependentPlaybackConfig = mContext.getResources().getBoolean(
                R.bool.config_mediaSourceIndependentPlayback);
        mUserMediaPlayContexts =
                new SparseArray<UserMediaPlayContext>(getMaxRunningUsers(context));

        mPackageUpdateFilter = new IntentFilter();
        mPackageUpdateFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        mPackageUpdateFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        mPackageUpdateFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        mPackageUpdateFilter.addDataScheme("package");

        mOccupantZoneService = occupantZoneService;
        mUserService = userService;
        mPowerManagementService = powerManagementService;

        // Before U, only listen to USER_SWITCHING and USER_UNLOCKED.
        // U and after, only listen to USER_VISIBLE, USER_INVISIBLE, and USER_UNLOCKED.
        UserLifecycleEventFilter.Builder userLifecycleEventFilterBuilder =
                new UserLifecycleEventFilter.Builder()
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED);
        userLifecycleEventFilterBuilder.addEventType(USER_LIFECYCLE_EVENT_TYPE_INVISIBLE)
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_VISIBLE);
        mUserService.addUserLifecycleListener(userLifecycleEventFilterBuilder.build(),
                mUserLifecycleListener);

        mPlayOnMediaSourceChangedConfig =
                mContext.getResources().getInteger(R.integer.config_mediaSourceChangedAutoplay);
        mPlayOnBootConfig = mContext.getResources().getInteger(R.integer.config_mediaBootAutoplay);
        mUserHandleHelper = userHandleHelper;
    }

    @Override
    // This method is called from ICarImpl after CarMediaService is created.
    public void init() {
        int currentUserId = ActivityManager.getCurrentUser();
        if (DEBUG) {
            Slogf.d(TAG, "init(): currentUser=%d", currentUserId);
        }
        // Initialize media service for the current user.
        maybeInitUser(currentUserId);
        setKeyEventListener();
        setPowerPolicyListener();
    }

    private void maybeInitUser(@UserIdInt int userId) {
        if (userId == UserHandle.SYSTEM.getIdentifier() && UserManager.isHeadlessSystemUserMode()) {
            if (DEBUG) {
                Slogf.d(TAG, "maybeInitUser(%d): No need to initialize for the"
                        + " headless system user", userId);
            }
            return;
        }
        if (mUserManager.isUserUnlocked(UserHandle.of(userId))) {
            initUser(userId);
        } else {
            synchronized (mLock) {
                getOrCreateUserMediaPlayContextLocked(userId).mPendingInit = true;
            }
        }
    }

    /** Initializes car media service data for the specified user. */
    private void initUser(@UserIdInt int userId) {
        if (DEBUG) {
            Slogf.d(TAG, "initUser(): userId=%d, mSharedPrefs=%s", userId, mSharedPrefs);
        }
        UserHandle userHandle = UserHandle.of(userId);

        maybeInitSharedPrefs(userId);

        ComponentName playbackSource;
        synchronized (mLock) {
            UserMediaPlayContext userMediaContext = getOrCreateUserMediaPlayContextLocked(userId);
            if (userMediaContext.mContext != null) {
                userMediaContext.mContext.unregisterReceiver(mPackageUpdateReceiver);
            }
            userMediaContext.mContext = mContext.createContextAsUser(userHandle, /* flags= */ 0);
            userMediaContext.mContext.registerReceiver(mPackageUpdateReceiver, mPackageUpdateFilter,
                    Context.RECEIVER_NOT_EXPORTED);

            boolean isEphemeral = isUserEphemeral(userHandle);
            if (isEphemeral) {
                ComponentName defaultMediaSource = getDefaultMediaSource(userId);
                userMediaContext.mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK] =
                        defaultMediaSource;
                userMediaContext.mPrimaryMediaComponents[MEDIA_SOURCE_MODE_BROWSE] =
                        defaultMediaSource;
                playbackSource = defaultMediaSource;
            } else {
                playbackSource = getLastMediaSource(MEDIA_SOURCE_MODE_PLAYBACK, userId);
                userMediaContext.mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK] =
                        playbackSource;
                userMediaContext.mPrimaryMediaComponents[MEDIA_SOURCE_MODE_BROWSE] =
                        getLastMediaSource(MEDIA_SOURCE_MODE_BROWSE, userId);
            }
            userMediaContext.mActiveMediaController = null;

            updateMediaSessionCallbackForUserLocked(userHandle);
        }
        notifyListeners(MEDIA_SOURCE_MODE_PLAYBACK, userId);
        notifyListeners(MEDIA_SOURCE_MODE_BROWSE, userId);

        boolean shouldPlay = shouldStartPlayback(mPlayOnBootConfig, userId);
        if (isMediaPowerEnabled()) {
            try {
                startMediaConnectorService(playbackSource, shouldPlay, userId);
            } catch (Exception e) {
                Slogf.e(TAG, e, "Failed to startMediaConnectorService. Source:%s user:%d",
                        playbackSource, userId);
            }
        } else {
            synchronized (mLock) {
                UserMediaPlayContext userMediaContext =
                        getOrCreateUserMediaPlayContextLocked(userId);
                // When media is disabled by power policy, mark the necessary fields such that later
                // the media play can be resumed when power policy is enabled.
                userMediaContext.mWasPlayingBeforeDisabled = shouldPlay;
                userMediaContext.mWasPreviouslyDisabledByPowerPolicy = true;
            }
        }
    }

    @GuardedBy("mLock")
    private ComponentName[] getPrimaryMediaComponentsForUserLocked(@UserIdInt int userId) {
        UserMediaPlayContext userMediaContext = getOrCreateUserMediaPlayContextLocked(userId);
        return userMediaContext.mPrimaryMediaComponents;
    }

    private ComponentName[] getRemovedMediaSourceComponentsForUser(@UserIdInt int userId) {
        synchronized (mLock) {
            UserMediaPlayContext userMediaContext = getOrCreateUserMediaPlayContextLocked(userId);
            return userMediaContext.mRemovedMediaSourceComponents;
        }
    }

    private void maybeInitSharedPrefs(@UserIdInt int userId) {
        // SharedPreferences are shared among different users thus only need initialized once. And
        // they should be initialized after user 0 is unlocked because SharedPreferences in
        // credential encrypted storage are not available until after user 0 is unlocked.
        // initUser() is called when the current foreground user is unlocked, and by that time user
        // 0 has been unlocked already, so initializing SharedPreferences in initUser() is fine.
        if (mSharedPrefs != null) {
            Slogf.i(TAG, "Shared preferences already set (on directory %s)"
                    + " when initializing user %d", mContext.getDataDir(), userId);
            return;
        }
        Slogf.i(TAG, "Getting shared preferences when initializing user %d", userId);
        mSharedPrefs = mContext.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);

        // Try to access the properties to make sure they were properly open
        if (DEBUG) {
            Slogf.d(TAG, "Number of prefs: %d", mSharedPrefs.getAll().size());
        }
    }

    /** Checks if {@link PowerComponent.MEDIA} is currently enabled. */
    private boolean isMediaPowerEnabled() {
        CarPowerPolicy currentPolicy = mPowerManagementService.getCurrentPowerPolicy();
        return currentPolicy.isComponentEnabled(PowerComponent.MEDIA);
    }

    /**
     * Starts a service on the current user that binds to the media browser of the current media
     * source. We start a new service because this one runs on user 0, and MediaBrowser doesn't
     * provide an API to connect on a specific user. Additionally, this service will attempt to
     * resume playback using the MediaSession obtained via the media browser connection, which
     * is more reliable than using active MediaSessions from MediaSessionManager.
     */
    private void startMediaConnectorService(@Nullable ComponentName playbackMediaSource,
            boolean startPlayback, @UserIdInt int userId) {
        synchronized (mLock) {
            startMediaConnectorServiceLocked(playbackMediaSource, startPlayback, userId);
        }
    }

    @GuardedBy("mLock")
    private void startMediaConnectorServiceLocked(@Nullable ComponentName playbackMediaSource,
            boolean startPlayback, @UserIdInt int userId) {
        UserMediaPlayContext userMediaPlayContext = getOrCreateUserMediaPlayContextLocked(userId);
        Context userContext = userMediaPlayContext.mContext;
        if (userContext == null) {
            Slogf.wtf(TAG,
                    "Cannot start MediaConnection service. User %d has not been initialized",
                    userId);
            return;
        }
        Intent serviceStart = new Intent(MEDIA_CONNECTION_ACTION);
        serviceStart.setPackage(
                mContext.getResources().getString(R.string.serviceMediaConnection));
        serviceStart.putExtra(EXTRA_AUTOPLAY, startPlayback);
        if (playbackMediaSource != null) {
            serviceStart.putExtra(EXTRA_MEDIA_COMPONENT, playbackMediaSource.flattenToString());
        }

        ComponentName result = userContext.startForegroundService(serviceStart);
        userMediaPlayContext.mStartedMediaConnectorService = result;
        Slogf.i(TAG, "startMediaConnectorService user: %d, source: %s, result: %s", userId,
                playbackMediaSource, result);
    }

    private boolean sharedPrefsInitialized() {
        if (mSharedPrefs != null) return true;

        // It shouldn't reach this but let's be cautious.
        Slogf.e(TAG, "SharedPreferences are not initialized!");
        String className = getClass().getName();
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            // Let's print the useful logs only.
            String log = ste.toString();
            if (log.contains(className)) {
                Slogf.e(TAG, log);
            }
        }
        return false;
    }

    private boolean isUserEphemeral(UserHandle userHandle) {
        return mUserHandleHelper.isEphemeralUser(userHandle);
    }

    private void setKeyEventListener() {
        int maxKeyCode = KeyEvent.getMaxKeyCode();
        ArrayList<Integer> mediaKeyCodes = new ArrayList<>(15);
        for (int key = 1; key <= maxKeyCode; key++) {
            if (KeyEvent.isMediaSessionKey(key)) {
                mediaKeyCodes.add(key);
            }
        }

        CarLocalServices.getService(CarInputService.class)
                .registerKeyEventListener(new MediaKeyEventListener(), mediaKeyCodes);
    }

    // Sets a listener to be notified when the current power policy changes.
    // Basically, the listener pauses the audio when a media component is disabled and resumes
    // the audio when a media component is enabled.
    // This is called only from init().
    private void setPowerPolicyListener() {
        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.MEDIA).build();
        mPowerManagementService.addPowerPolicyListener(filter, mPowerPolicyListener);
    }

    @Override
    public void release() {
        synchronized (mLock) {
            int userArraySize = mUserMediaPlayContexts.size();
            for (int i = 0; i < userArraySize; i++) {
                clearUserDataLocked(mUserMediaPlayContexts.keyAt(i));
            }
        }
        mUserService.removeUserLifecycleListener(mUserLifecycleListener);
        mPowerManagementService.removePowerPolicyListener(mPowerPolicyListener);
    }

    /** Clears the user data for {@code userId}. */
    @GuardedBy("mLock")
    private void clearUserDataLocked(@UserIdInt int userId) {
        if (DEBUG) {
            Slogf.d(TAG, "clearUserDataLocked() for user %d", userId);
        }
        UserMediaPlayContext userMediaContext = mUserMediaPlayContexts.get(userId);
        if (userMediaContext == null) {
            return;
        }

        if (userMediaContext.mContext != null) {
            userMediaContext.mContext.unregisterReceiver(mPackageUpdateReceiver);
            userMediaContext.mContext = null;
        }
        userMediaContext.mMediaSessionUpdater.unregisterCallbacks();
        mMediaSessionManager.removeOnActiveSessionsChangedListener(
                userMediaContext.mSessionsListener);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarMediaService*");
        writer.increaseIndent();
        writer.printf("DEBUG=%b\n", DEBUG);
        writer.printf("mPlayOnBootConfig=%d\n", mPlayOnBootConfig);
        writer.printf("mPlayOnMediaSourceChangedConfig=%d\n", mPlayOnMediaSourceChangedConfig);
        writer.printf("mDefaultIndependentPlaybackConfig=%b\n", mDefaultIndependentPlaybackConfig);
        writer.println();

        boolean hasSharedPrefs = mSharedPrefs != null;
        synchronized (mLock) {
            int userArraySize = mUserMediaPlayContexts.size();
            for (int i = 0; i < userArraySize; i++) {
                int userId = mUserMediaPlayContexts.keyAt(i);
                writer.printf("For user %d:\n", userId);
                writer.increaseIndent();
                UserMediaPlayContext userMediaContext = mUserMediaPlayContexts.valueAt(i);
                writer.printf("Pending init: %b\n", userMediaContext.mPendingInit);
                writer.printf("MediaConnectorService: %s\n",
                        userMediaContext.mStartedMediaConnectorService != null
                                ? userMediaContext.mStartedMediaConnectorService
                                        .flattenToShortString()  : "");
                dumpCurrentMediaComponentLocked(writer, "playback", MEDIA_SOURCE_MODE_PLAYBACK,
                        userId);
                dumpCurrentMediaComponentLocked(writer, "browse", MEDIA_SOURCE_MODE_BROWSE,
                        userId);
                MediaController mediaController = userMediaContext.mActiveMediaController;
                if (mediaController != null) {
                    writer.printf("Current media controller: %s\n",
                            mediaController.getPackageName());
                    writer.printf("Current browse service extra: %s\n",
                            getClassName(mediaController));
                } else {
                    writer.println("no active user media controller");
                }
                writer.printf("Number of active media sessions (for user %d): %d\n", userId,
                        mMediaSessionManager.getActiveSessionsForUser(
                                /* notificationListener= */ null, UserHandle.of(userId)).size());

                writer.printf("Disabled by power policy: %b\n",
                        userMediaContext.mIsDisabledByPowerPolicy);
                if (userMediaContext.mIsDisabledByPowerPolicy) {
                    writer.printf("Before being disabled by power policy, audio was %s\n",
                            userMediaContext.mWasPlayingBeforeDisabled ? "active" : "inactive");
                }
                if (hasSharedPrefs) {
                    dumpLastUpdateTime(writer, userId);
                    dumpLastMediaSources(writer, "Playback", MEDIA_SOURCE_MODE_PLAYBACK, userId);
                    dumpLastMediaSources(writer, "Browse", MEDIA_SOURCE_MODE_BROWSE, userId);
                    dumpPlaybackState(writer, userId);
                }
                writer.decreaseIndent();
            }
        }

        if (hasSharedPrefs) {
            dumpSharedPrefs(writer);
        } else {
            writer.println("No shared preferences");
        }

        writer.decreaseIndent();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    @GuardedBy("mLock")
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpCurrentMediaComponentLocked(IndentingPrintWriter writer, String name,
            @CarMediaManager.MediaSourceMode int mode, @UserIdInt int userId) {
        ComponentName componentName = getPrimaryMediaComponentsForUserLocked(userId)[mode];
        writer.printf("For user %d, current %s media component: %s\n", userId,  name,
                (componentName == null ? "-" : componentName.flattenToString()));
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpLastUpdateTime(IndentingPrintWriter writer, @UserIdInt int userId) {
        long lastUpdate = mSharedPrefs.getLong(getLastUpdateKey(userId), -1);
        writer.printf("For user %d, shared preference last updated on %d / ", userId, lastUpdate);
        TimeUtils.dumpTime(writer, lastUpdate);
        writer.println();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpLastMediaSources(IndentingPrintWriter writer, String name,
            @CarMediaManager.MediaSourceMode int mode, @UserIdInt int userId) {
        writer.printf("%s media source history:\n", name);
        writer.increaseIndent();
        List<ComponentName> lastMediaSources = getLastMediaSourcesInternal(mode, userId);
        for (int i = 0; i < lastMediaSources.size(); i++) {
            ComponentName componentName = lastMediaSources.get(i);
            if (componentName == null) {
                Slogf.e(TAG, "dump(): for user %d, empty last media source of %s"
                                + " at index %d: %s",
                        userId, mediaModeToString(mode), i, lastMediaSources);
                continue;
            }
            writer.println(componentName.flattenToString());
        }
        writer.decreaseIndent();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpPlaybackState(IndentingPrintWriter writer, @UserIdInt int userId) {
        String key = getPlaybackStateKey(userId);
        int playbackState = mSharedPrefs.getInt(key, PlaybackState.STATE_NONE);
        writer.printf("media playback state: %d\n", playbackState);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    private void dumpSharedPrefs(IndentingPrintWriter writer) {
        Map<String, ?> allPrefs = mSharedPrefs.getAll();
        writer.printf("%d shared preferences (saved on directory %s)",
                allPrefs.size(), mContext.getDataDir());
        if (!Slogf.isLoggable(TAG, Log.VERBOSE) || allPrefs.isEmpty()) {
            writer.println();
            return;
        }
        writer.println(':');
    }

    /**
     * @see {@link CarMediaManager#setMediaSource(ComponentName)}
     */
    @Override
    public void setMediaSource(@NonNull ComponentName componentName,
            @MediaSourceMode int mode, @UserIdInt int userId) {
        enforceValidCallingUser(userId);
        assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        if (DEBUG) {
            Slogf.d(TAG, "Changing media source to: %s for user %d",
                    componentName.getPackageName(), userId);
        }
        setPrimaryMediaSource(componentName, mode, userId);
    }

    /**
     * @see {@link CarMediaManager#getMediaSource()}
     */
    @Override
    public ComponentName getMediaSource(@CarMediaManager.MediaSourceMode int mode,
            @UserIdInt int userId) {
        enforceValidCallingUser(userId);
        assertPermission(mContext, android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        ComponentName componentName;
        synchronized (mLock) {
            componentName = getPrimaryMediaComponentsForUserLocked(userId)[mode];
        }
        if (componentName == null) {
            componentName = getDefaultMediaSource(userId);
            Slogf.e(TAG, "User %d has not been initialized. Returning the default media "
                            + "source %s.", userId, componentName);
        }
        if (DEBUG) {
            Slogf.d(TAG, "Getting media source mode %d for user %d: %s ",
                    mode, userId, componentName);
        }
        return componentName;
    }

    /**
     * @see {@link CarMediaManager#registerMediaSourceListener(MediaSourceChangedListener)}
     */
    @Override
    public void registerMediaSourceListener(ICarMediaSourceListener callback,
            @MediaSourceMode int mode, @UserIdInt int userId) {
        enforceValidCallingUser(userId);
        assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        UserMediaPlayContext userMediaContext;
        synchronized (mLock) {
            userMediaContext = getOrCreateUserMediaPlayContextLocked(userId);
        }
        userMediaContext.mMediaSourceListeners[mode].register(callback);
    }

    /**
     * @see {@link CarMediaManager#unregisterMediaSourceListener(ICarMediaSourceListener)}
     */
    @Override
    public void unregisterMediaSourceListener(ICarMediaSourceListener callback,
            @MediaSourceMode int mode, @UserIdInt int userId) {
        enforceValidCallingUser(userId);
        assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        UserMediaPlayContext userMediaContext;
        synchronized (mLock) {
            userMediaContext = getOrCreateUserMediaPlayContextLocked(userId);
        }
        userMediaContext.mMediaSourceListeners[mode].unregister(callback);
    }

    @Override
    public List<ComponentName> getLastMediaSources(@CarMediaManager.MediaSourceMode int mode,
            @UserIdInt int userId) {
        enforceValidCallingUser(userId);
        assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        ArrayList<ComponentName> results = getLastMediaSourcesInternal(mode, userId);
        // Always add the default media source at the end, if it is not already in the list.
        // This is to match the results of getLastMediaSources() with what getMediaSource() returns.
        // For example, when a user is first initialized and no media source has been stored yet,
        // getLastMediaSources() will return the default media source, instead of an empty list.
        ComponentName defaultMediaSource = getDefaultMediaSource(userId);
        if (defaultMediaSource != null && results.indexOf(defaultMediaSource) < 0) {
            results.add(defaultMediaSource);
        }
        return results;
    }

    /**
     * Returns the last media sources for the specified {@code mode} and {@code userId}.
     *
     * <p>Anywhere in this file, do not use the public {@link #getLastMediaSources(int, int)}
     * method. Use this method instead.
     */
    private ArrayList<ComponentName> getLastMediaSourcesInternal(
            @CarMediaManager.MediaSourceMode int mode, @UserIdInt int userId) {
        String key = getMediaSourceKey(mode, userId);
        String serialized =
                mSharedPrefs == null ? null : mSharedPrefs.getString(key, "");
        List<String> componentNames = getComponentNameList(serialized);
        // Set the initial capacity to componentNames.size() + 1, to avoid re-allocation in case
        // the default media source needs to be added by getLastMediaSources().
        ArrayList<ComponentName> results = new ArrayList<>(componentNames.size() + 1);
        for (int i = 0; i < componentNames.size(); i++) {
            results.add(ComponentName.unflattenFromString(componentNames.get(i)));
        }
        return results;
    }

    /** See {@link CarMediaManager#isIndependentPlaybackConfig}. */
    @Override
    @TestApi
    public boolean isIndependentPlaybackConfig(@UserIdInt int userId) {
        enforceValidCallingUser(userId);
        assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        return isIndependentPlaybackConfigInternal(userId);
    }

    /**
     * Returns independent playback config for the specified {@code userId}.
     *
     * <p>Anywhere in this file, do not use the public {@link isIndependentPlaybackConfig(int)}
     * method.
     */
    private boolean isIndependentPlaybackConfigInternal(@UserIdInt int userId) {
        synchronized (mLock) {
            return getOrCreateUserMediaPlayContextLocked(userId).mIndependentPlaybackConfig;
        }
    }

    /** See {@link CarMediaManager#setIndependentPlaybackConfig}. */
    @Override
    @TestApi
    public void setIndependentPlaybackConfig(boolean independent, @UserIdInt int userId) {
        enforceValidCallingUser(userId);
        assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        synchronized (mLock) {
            getOrCreateUserMediaPlayContextLocked(userId).mIndependentPlaybackConfig = independent;
        }
    }

    private void enforceValidCallingUser(@UserIdInt int userId) throws SecurityException {
        UserHandle callingUser = getCallingUserHandle();

        // The calling user is valid if it is the system user or the specified userId matches
        // the calling user.
        if (!callingUser.isSystem() && callingUser.getIdentifier() != userId) {
            throw new SecurityException("The calling user " + callingUser.getIdentifier()
                    + " cannot access media data of user " + userId);
        }
    }

    /** Clears data for {@code fromUserId}, and initializes data for {@code toUserId}. */
    private void onUserSwitch(@UserIdInt int fromUserId, @UserIdInt int toUserId) {
        if (DEBUG) {
            Slogf.d(TAG, "onUserSwitch() fromUserId=%d, toUserId=%d", fromUserId, toUserId);
        }
        // Clean up the data of the fromUser.
        if (fromUserId != UserHandle.SYSTEM.getIdentifier()) {
            onUserInvisible(fromUserId);
        }
        // Initialize the data of the toUser.
        onUserVisible(toUserId);
    }

    private void onUserVisible(@UserIdInt int userId) {
        if (DEBUG) {
            Slogf.d(TAG, "onUserVisible() for user=%d", userId);
        }
        maybeInitUser(userId);
    }

    /** Clears the user data when the user becomes invisible. */
    private void onUserInvisible(@UserIdInt int userId) {
        if (DEBUG) {
            Slogf.d(TAG, "onUserInvisible(): userId=%d. Clearing data for the user.", userId);
        }
        synchronized (mLock) {
            clearUserDataLocked(userId);
            mUserMediaPlayContexts.delete(userId);
        }
    }

    // TODO(b/153115826): this method was used to be called from the ICar binder thread, but it's
    // now called by UserCarService. Currently UserCarService is calling every listener in one
    // non-main thread, but it's not clear how the final behavior will be. So, for now it's ok
    // to post it to mMainHandler, but once b/145689885 is fixed, we might not need it.
    private void onUserUnlocked(@UserIdInt int userId) {
        if (DEBUG) {
            Slogf.d(TAG, "onUserUnlocked(): userId=%d", userId);
        }
        mCommonThreadHandler.post(() -> {
            // No need to handle system user, but still need to handle background users.
            if (userId == UserHandle.SYSTEM.getIdentifier()) {
                return;
            }
            UserMediaPlayContext userMediaPlayContext;
            boolean isPendingInit;
            synchronized (mLock) {
                userMediaPlayContext = getOrCreateUserMediaPlayContextLocked(userId);
                isPendingInit = userMediaPlayContext.mPendingInit;
            }
            if (DEBUG) {
                Slogf.d(TAG, "onUserUnlocked(): userId=%d pendingInit=%b", userId,
                        userMediaPlayContext.mPendingInit);
            }
            if (isPendingInit) {
                initUser(userId);
                synchronized (mLock) {
                    userMediaPlayContext.mPendingInit = false;
                }
                if (DEBUG) {
                    Slogf.d(TAG, "User %d is now unlocked", userId);
                }
            }
        });
    }

    @GuardedBy("mLock")
    private void updateMediaSessionCallbackForUserLocked(UserHandle userHandle) {
        int userId = userHandle.getIdentifier();
        UserMediaPlayContext userMediaContext = getOrCreateUserMediaPlayContextLocked(userId);
        SessionChangedListener sessionsListener = userMediaContext.mSessionsListener;
        if (sessionsListener != null) {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener);
        }
        sessionsListener = new SessionChangedListener(userId);
        userMediaContext.mSessionsListener = sessionsListener;
        mMediaSessionManager.addOnActiveSessionsChangedListener(null, userHandle,
                new HandlerExecutor(mHandler), sessionsListener);

        MediaSessionUpdater sessionUpdater = new MediaSessionUpdater(userId);
        userMediaContext.mMediaSessionUpdater = sessionUpdater;
        sessionUpdater.registerCallbacks(mMediaSessionManager.getActiveSessionsForUser(null,
                userHandle));
    }

    /**
     * Attempts to stop the current source using MediaController.TransportControls.stop()
     * This method also unregisters callbacks to the active media controller before calling stop(),
     * to preserve the PlaybackState before stopping.
     */
    private void stopAndUnregisterCallback(@UserIdInt int userId) {
        UserMediaPlayContext userMediaContext;
        MediaController mediaController;
        synchronized (mLock) {
            userMediaContext = getOrCreateUserMediaPlayContextLocked(userId);
            mediaController = userMediaContext.mActiveMediaController;
        }
        if (mediaController == null) {
            if (DEBUG) {
                Slogf.d(TAG, "stopAndUnregisterCallback() for user %d."
                        + " Do nothing as there is no active media controller.", userId);
            }
            return;
        }

        mediaController.unregisterCallback(userMediaContext.mMediaControllerCallback);
        if (DEBUG) {
            Slogf.d(TAG, "stopping %s", mediaController.getPackageName());
        }
        TransportControls controls = mediaController.getTransportControls();
        if (controls != null) {
            // In order to prevent some apps from taking back the audio focus after being stopped,
            // first call pause, if the app supports pause. This does not affect the saved source
            // or the playback state, because the callback has already been unregistered.
            PlaybackState playbackState = mediaController.getPlaybackState();
            if (playbackState != null
                    && (playbackState.getActions() & PlaybackState.ACTION_PAUSE) != 0) {
                if (DEBUG) {
                    Slogf.d(TAG, "Call pause before stop");
                }
                controls.pause();
            }
            controls.stop();
        } else {
            Slogf.e(TAG, "Can't stop playback, transport controls unavailable %s",
                    mediaController.getPackageName());
        }
    }

    @GuardedBy("mLock")
    private UserMediaPlayContext getOrCreateUserMediaPlayContextLocked(@UserIdInt int userId) {
        UserMediaPlayContext userMediaContext = mUserMediaPlayContexts.get(userId);
        if (userMediaContext == null) {
            userMediaContext = new UserMediaPlayContext(userId, mDefaultIndependentPlaybackConfig);
            mUserMediaPlayContexts.set(userId, userMediaContext);
            if (DEBUG) {
                Slogf.d(TAG, "Create a UserMediaPlayContext for user %d", userId);
            }
        }

        return userMediaContext;
    }

    /** A container to store per-user media play context data. */
    private final class UserMediaPlayContext {

        @Nullable
        private Context mContext;
        // MediaController for the user's active media session. This controller can be null
        // if playback has not been started yet.
        private MediaController mActiveMediaController;
        private int mCurrentPlaybackState;
        private boolean mIsDisabledByPowerPolicy;
        private boolean mWasPreviouslyDisabledByPowerPolicy;
        private boolean mWasPlayingBeforeDisabled;
        private boolean mIndependentPlaybackConfig;
        private final ComponentName[] mPrimaryMediaComponents;
        // The component name of the last media source that was removed while being primary.
        private final ComponentName[] mRemovedMediaSourceComponents;
        private boolean mPendingInit;
        // This field is used for test/debugging.
        private ComponentName mStartedMediaConnectorService;

        private final RemoteCallbackList<ICarMediaSourceListener>[] mMediaSourceListeners;
        private MediaSessionUpdater mMediaSessionUpdater;
        private SessionChangedListener mSessionsListener;
        private final MediaController.Callback mMediaControllerCallback;

        UserMediaPlayContext(@UserIdInt int userId, boolean independentPlaybackConfig) {
            mIndependentPlaybackConfig = independentPlaybackConfig;
            mPrimaryMediaComponents = new ComponentName[MEDIA_SOURCE_MODES];
            mRemovedMediaSourceComponents = new ComponentName[MEDIA_SOURCE_MODES];
            mMediaSourceListeners = new RemoteCallbackList[] {new RemoteCallbackList(),
                    new RemoteCallbackList()};
            mMediaSessionUpdater = new MediaSessionUpdater(userId);
            mSessionsListener = new SessionChangedListener(userId);
            mMediaControllerCallback = new ActiveMediaControllerCallback(userId);
        }
    }

    private final class ActiveMediaControllerCallback extends MediaController.Callback {

        private final @UserIdInt int mUserId;

        ActiveMediaControllerCallback(@UserIdInt int userId) {
            mUserId = userId;
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            savePlaybackState(state, mUserId);
        }
    }

    private class SessionChangedListener implements OnActiveSessionsChangedListener {
        private final @UserIdInt int mUserId;

        SessionChangedListener(int userId) {
            mUserId = userId;
        }

        @Override
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            if (DEBUG) {
                Slogf.d(TAG, "onActiveSessionsChanged() for user %d, controllers: %s",
                        mUserId, controllers);
            }
            // Filter controllers based on their user ids.
            ArrayList<MediaController> userControllers = new ArrayList<>(controllers.size());
            for (int i = 0; i < controllers.size(); i++) {
                MediaController controller = controllers.get(i);
                int userId = UserHandle.getUserHandleForUid(
                        controller.getSessionToken().getUid()).getIdentifier();
                if (userId == mUserId) {
                    userControllers.add(controller);
                } else {
                    Slogf.w(TAG, "onActiveSessionsChanged() received a change for "
                            + "a different user: listener user %d, controller %s for user %d",
                            mUserId, controller, userId);
                }
            }
            UserMediaPlayContext userMediaContext;
            synchronized (mLock) {
                userMediaContext = getOrCreateUserMediaPlayContextLocked(mUserId);
            }
            userMediaContext.mMediaSessionUpdater.registerCallbacks(userControllers);
        }
    }

    private class MediaControllerCallback extends MediaController.Callback {

        private final @UserIdInt int mUserId;
        private final MediaController mMediaController;
        private PlaybackState mPreviousPlaybackState;

        private MediaControllerCallback(MediaController mediaController, @UserIdInt int userId) {
            mUserId = userId;
            mMediaController = mediaController;
            PlaybackState state = mediaController.getPlaybackState();
            mPreviousPlaybackState = state;
        }

        private void register() {
            mMediaController.registerCallback(this);
        }

        private void unregister() {
            mMediaController.unregisterCallback(this);
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            if (DEBUG) {
                Slogf.d(TAG, "onPlaybackStateChanged() for user %d; previous state: %s,"
                        + " new state: %s", mUserId, mPreviousPlaybackState, state.getState());
            }
            if (state != null && state.isActive()
                    && (mPreviousPlaybackState == null || !mPreviousPlaybackState.isActive())) {
                ComponentName mediaSource = getMediaSource(mMediaController.getPackageName(),
                        getClassName(mMediaController), mUserId);
                if (mediaSource != null && Slogf.isLoggable(TAG, Log.INFO)) {
                    synchronized (mLock) {
                        if (!mediaSource.equals(getPrimaryMediaComponentsForUserLocked(mUserId)
                                [MEDIA_SOURCE_MODE_PLAYBACK])) {
                            Slogf.i(TAG, "Changing media source for user %d due to playback state "
                                    + "change: %s", mUserId, mediaSource.flattenToString());
                        }
                    }
                }
                setPrimaryMediaSource(mediaSource, MEDIA_SOURCE_MODE_PLAYBACK, mUserId);
            }
            mPreviousPlaybackState = state;
        }
    }

    private class MediaSessionUpdater {

        private final @UserIdInt int mUserId;
        private Map<Token, MediaControllerCallback> mCallbacks = new HashMap<>();

        MediaSessionUpdater(@UserIdInt int userId) {
            mUserId = userId;
        }

        /**
         * Register a {@link MediaControllerCallback} for each given controller. Note that if a
         * controller was already watched, we don't register a callback again. This prevents an
         * undesired revert of the primary media source. Callbacks for previously watched
         * controllers that are not present in the given list are unregistered.
         */
        private void registerCallbacks(List<MediaController> newControllers) {

            List<MediaController> additions = new ArrayList<>(newControllers.size());
            Map<MediaSession.Token, MediaControllerCallback> updatedCallbacks =
                    new HashMap<>(newControllers.size());
            for (MediaController controller : newControllers) {
                MediaSession.Token token = controller.getSessionToken();
                MediaControllerCallback callback = mCallbacks.get(token);
                if (callback == null) {
                    callback = new MediaControllerCallback(controller, mUserId);
                    callback.register();
                    additions.add(controller);
                }
                updatedCallbacks.put(token, callback);
            }

            for (MediaSession.Token token : mCallbacks.keySet()) {
                if (!updatedCallbacks.containsKey(token)) {
                    mCallbacks.get(token).unregister();
                }
            }

            mCallbacks = updatedCallbacks;
            updatePrimaryMediaSourceWithCurrentlyPlaying(additions, mUserId);
            // If there are no playing media sources, and we don't currently have the controller
            // for the active source, check the active sessions for a matching controller. If this
            // is called after a user switch, its possible for a matching controller to already be
            // active before the user is unlocked, so we check all of the current controllers
            synchronized (mLock) {
                UserMediaPlayContext userMediaContext =
                        getOrCreateUserMediaPlayContextLocked(mUserId);
                if (userMediaContext.mActiveMediaController == null) {
                    updateActiveMediaControllerLocked(newControllers, mUserId);
                }
            }
        }

        /**
         * Unregister all MediaController callbacks
         */
        private void unregisterCallbacks() {
            for (Map.Entry<Token, MediaControllerCallback> entry : mCallbacks.entrySet()) {
                entry.getValue().unregister();
            }
        }
    }

    /**
     * Updates the primary media source, then notifies content observers of the change
     * Will update both the playback and browse sources if independent playback is not supported
     */
    private void setPrimaryMediaSource(ComponentName componentName,
            @CarMediaManager.MediaSourceMode int mode, @UserIdInt int userId) {
        if (DEBUG) {
            Slogf.d(TAG, "setPrimaryMediaSource(component=%s, mode=%d, userId=%d)",
                    componentName, mode, userId);
        }
        ComponentName mediaComponent;
        synchronized (mLock) {
            mediaComponent = getPrimaryMediaComponentsForUserLocked(userId)[mode];
        }
        if (mediaComponent != null && mediaComponent.equals((componentName))) {
            return;
        }

        if (!isIndependentPlaybackConfigInternal(userId)) {
            setPlaybackMediaSource(componentName, userId);
            setBrowseMediaSource(componentName, userId);
        } else if (mode == MEDIA_SOURCE_MODE_PLAYBACK) {
            setPlaybackMediaSource(componentName, userId);
        } else if (mode == MEDIA_SOURCE_MODE_BROWSE) {
            setBrowseMediaSource(componentName, userId);
        }
        // Android logs app usage into UsageStatsManager. ACTIVITY_RESUMED and ACTIVITY_STOPPED
        // events do not capture media app usage on AAOS because apps are hosted by a proxy such as
        // Media Center. Reporting a USER_INTERACTION event in setPrimaryMediaSource allows
        // attribution of non-foreground media app interactions to the app's package name
        if (componentName != null) {
            UsageStatsManagerHelper.reportUserInteraction(mUsageStatsManager,
                    componentName.getPackageName(), userId);
        }
    }

    private void setPlaybackMediaSource(ComponentName playbackMediaSource, @UserIdInt int userId) {
        stopAndUnregisterCallback(userId);
        UserMediaPlayContext userMediaContext;

        synchronized (mLock) {
            userMediaContext = getOrCreateUserMediaPlayContextLocked(userId);
            userMediaContext.mActiveMediaController = null;
            userMediaContext.mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK] =
                    playbackMediaSource;
        }

        UserHandle userHandle = UserHandle.of(userId);
        if (playbackMediaSource != null
                && !TextUtils.isEmpty(playbackMediaSource.flattenToString())) {
            if (!isUserEphemeral(userHandle)) {
                saveLastMediaSource(playbackMediaSource, MEDIA_SOURCE_MODE_PLAYBACK, userId);
            }
            if (playbackMediaSource
                    .equals(getRemovedMediaSourceComponentsForUser(userId)
                            [MEDIA_SOURCE_MODE_PLAYBACK])) {
                getRemovedMediaSourceComponentsForUser(userId)[MEDIA_SOURCE_MODE_PLAYBACK] = null;
            }
            notifyListeners(MEDIA_SOURCE_MODE_PLAYBACK, userId);
            startMediaConnectorService(playbackMediaSource,
                    shouldStartPlayback(mPlayOnMediaSourceChangedConfig, userId), userId);
        } else {
            Slogf.i(TAG, "Media source is null for user %d, skip starting media "
                    + "connector service", userId);
            // We will still notify the listeners that playback changed
            notifyListeners(MEDIA_SOURCE_MODE_PLAYBACK, userId);
        }

        // Reset current playback state for the new source, in the case that the app is in an error
        // state (e.g. not signed in). This state will be updated from the app callback registered
        // below, to make sure mCurrentPlaybackState reflects the current source only.
        synchronized (mLock) {
            userMediaContext.mCurrentPlaybackState = PlaybackState.STATE_NONE;
            updateActiveMediaControllerLocked(mMediaSessionManager
                    .getActiveSessionsForUser(null, userHandle), userId);
        }
    }

    private void setBrowseMediaSource(ComponentName browseMediaSource, @UserIdInt int userId) {
        synchronized (mLock) {
            getPrimaryMediaComponentsForUserLocked(userId)[MEDIA_SOURCE_MODE_BROWSE] =
                    browseMediaSource;
        }

        if (browseMediaSource != null && !TextUtils.isEmpty(browseMediaSource.flattenToString())) {
            if (!isUserEphemeral(UserHandle.of(userId))) {
                saveLastMediaSource(browseMediaSource, MEDIA_SOURCE_MODE_BROWSE, userId);
            }
            if (browseMediaSource
                    .equals(getRemovedMediaSourceComponentsForUser(
                            userId)[MEDIA_SOURCE_MODE_BROWSE])) {
                getRemovedMediaSourceComponentsForUser(userId)[MEDIA_SOURCE_MODE_BROWSE] = null;
            }
        }

        notifyListeners(MEDIA_SOURCE_MODE_BROWSE, userId);
    }

    private void notifyListeners(@CarMediaManager.MediaSourceMode int mode, @UserIdInt int userId) {
        synchronized (mLock) {
            UserMediaPlayContext userMediaContext = getOrCreateUserMediaPlayContextLocked(userId);
            RemoteCallbackList<ICarMediaSourceListener> callbackList =
                    userMediaContext.mMediaSourceListeners[mode];
            ComponentName primaryMediaComponent = userMediaContext.mPrimaryMediaComponents[mode];
            int i = callbackList.beginBroadcast();
            if (DEBUG) {
                Slogf.d(TAG, "Notify %d media source listeners for mode %d, user %d",
                        i, mode, userId);
            }
            while (i-- > 0) {
                try {
                    ICarMediaSourceListener callback = callbackList.getBroadcastItem(i);
                    callback.onMediaSourceChanged(primaryMediaComponent);
                } catch (RemoteException e) {
                    Slogf.e(TAG, e, "calling onMediaSourceChanged failed for user %d", userId);
                }
            }
            callbackList.finishBroadcast();
        }
    }

    /**
     * Finds the currently playing media source, then updates the active source if the component
     * name is different.
     */
    private void updatePrimaryMediaSourceWithCurrentlyPlaying(
            List<MediaController> controllers, @UserIdInt int userId) {
        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.isActive()) {
                String newPackageName = controller.getPackageName();
                String newClassName = getClassName(controller);
                if (!matchPrimaryMediaSource(newPackageName, newClassName,
                        MEDIA_SOURCE_MODE_PLAYBACK, userId)) {
                    ComponentName mediaSource =
                            getMediaSource(newPackageName, newClassName, userId);
                    if (Slogf.isLoggable(TAG, Log.INFO)) {
                        if (mediaSource != null) {
                            Slogf.i(TAG,
                                    "MediaController changed, updating media source for user %d "
                                    + "to: %s", userId, mediaSource.flattenToString());
                        } else {
                            // Some apps, like Chrome, have a MediaSession but no
                            // MediaBrowseService. Media Center doesn't consider such apps as
                            // valid media sources.
                            Slogf.i(TAG,
                                    "MediaController changed, but no media browse service for user"
                                            + " %d found in package: %s", userId, newPackageName);
                        }
                    }
                    setPrimaryMediaSource(mediaSource, MEDIA_SOURCE_MODE_PLAYBACK, userId);
                }
                return;
            }
        }
    }

    private boolean matchPrimaryMediaSource(String newPackageName, String newClassName,
            @CarMediaManager.MediaSourceMode int mode, @UserIdInt int userId) {
        synchronized (mLock) {
            ComponentName mediaComponent = getPrimaryMediaComponentsForUserLocked(userId)[mode];
            if (mediaComponent != null
                    && mediaComponent.getPackageName().equals(newPackageName)) {
                // If the class name of currently active source is not specified, only checks
                // package name; otherwise checks both package name and class name.
                if (TextUtils.isEmpty(newClassName)) {
                    return true;
                } else {
                    return newClassName.equals(mediaComponent.getClassName());
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the provided component has a valid {@link MediaBrowserService}.
     */
    @VisibleForTesting
    public boolean isMediaService(ComponentName componentName, @UserIdInt int userId) {
        return getMediaService(componentName, userId) != null;
    }

    /*
     * Gets the media service that matches the componentName for the specified user.
     */
    private ComponentName getMediaService(ComponentName componentName, @UserIdInt int userId) {
        String packageName = componentName.getPackageName();
        String className = componentName.getClassName();

        PackageManager packageManager = mContext.getPackageManager();
        Intent mediaIntent = new Intent();
        mediaIntent.setPackage(packageName);
        mediaIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);
        List<ResolveInfo> mediaServices = packageManager.queryIntentServicesAsUser(mediaIntent,
                PackageManager.GET_RESOLVED_FILTER, UserHandle.of(userId));

        for (ResolveInfo service : mediaServices) {
            String serviceName = service.serviceInfo.name;
            if (!TextUtils.isEmpty(serviceName)
                    // If className is not specified, returns the first service in the package;
                    // otherwise returns the matched service.
                    // TODO(b/136274456): find a proper way to handle the case where there are
                    //  multiple services and the className is not specified.
                    && (TextUtils.isEmpty(className) || serviceName.equals(className))) {
                return new ComponentName(packageName, serviceName);
            }
        }

        if (DEBUG) {
            Slogf.d(TAG, "No MediaBrowseService for user %d with ComponentName: %s",
                    userId, componentName.flattenToString());
        }
        return null;
    }

    /*
     * Gets the component name of the media service.
     */
    @Nullable
    private ComponentName getMediaSource(String packageName, String className,
            @UserIdInt int userId) {
        return getMediaService(new ComponentName(packageName, className), userId);
    }

    private void saveLastMediaSource(ComponentName component, int mode, @UserIdInt int userId) {
        if (!sharedPrefsInitialized()) {
            return;
        }
        String componentName = component.flattenToString();
        String key = getMediaSourceKey(mode, userId);
        String serialized = mSharedPrefs.getString(key, null);
        String modeName = null;
        if (DEBUG) {
            modeName = mediaModeToString(mode);
        }

        if (serialized == null) {
            if (DEBUG) {
                Slogf.d(TAG, "saveLastMediaSource(%s, %s, %d): no value for key %s",
                        componentName, modeName, userId, key);
            }
            getSharedPrefsForWriting(userId).putString(key, componentName).apply();
        } else {
            Deque<String> componentNames = new ArrayDeque<>(getComponentNameList(serialized));
            componentNames.remove(componentName);
            componentNames.addFirst(componentName);
            String newSerialized = serializeComponentNameList(componentNames);
            if (DEBUG) {
                Slogf.d(TAG, "saveLastMediaSource(%s, %s, %d): updating %s from %s to %s",
                        componentName, modeName, userId, key, serialized, newSerialized);
            }
            getSharedPrefsForWriting(userId).putString(key, newSerialized).apply();
        }
    }

    private @NonNull ComponentName getLastMediaSource(int mode, @UserIdInt int userId) {
        if (sharedPrefsInitialized()) {
            String key = getMediaSourceKey(mode, userId);
            String serialized = mSharedPrefs.getString(key, "");
            if (!TextUtils.isEmpty(serialized)) {
                for (String name : getComponentNameList(serialized)) {
                    ComponentName componentName = ComponentName.unflattenFromString(name);
                    if (isMediaService(componentName, userId)) {
                        return componentName;
                    }
                }
            }
        }
        return getDefaultMediaSource(userId);
    }

    private ComponentName getDefaultMediaSource(@UserIdInt int userId) {
        String defaultMediaSource = mContext.getString(R.string.config_defaultMediaSource);
        ComponentName defaultComponent = ComponentName.unflattenFromString(defaultMediaSource);
        if (isMediaService(defaultComponent, userId)) {
            return defaultComponent;
        }
        Slogf.e(TAG, "No media service for user %d in the default component: %s",
                userId, defaultComponent);
        return null;
    }

    private String serializeComponentNameList(Deque<String> componentNames) {
        return String.join(COMPONENT_NAME_SEPARATOR, componentNames);
    }

    private List<String> getComponentNameList(String serialized) {
        // Note that for an empty string, String#split returns an array of size 1 with an empty
        // string as its entry. Instead, we just return an empty list.
        if (TextUtils.isEmpty(serialized)) {
            return Collections.emptyList();
        }
        String[] componentNames = serialized.split(COMPONENT_NAME_SEPARATOR);
        return (Arrays.asList(componentNames));
    }

    private void savePlaybackState(PlaybackState playbackState, @UserIdInt int userId) {
        if (!sharedPrefsInitialized()) {
            return;
        }
        if (isUserEphemeral(UserHandle.of(userId))) {
            return;
        }
        int state = playbackState != null ? playbackState.getState() : PlaybackState.STATE_NONE;
        synchronized (mLock) {
            getOrCreateUserMediaPlayContextLocked(userId).mCurrentPlaybackState = state;
        }

        String key = getPlaybackStateKey(userId);
        if (DEBUG) {
            Slogf.d(TAG, "savePlaybackState() for user %d: %s = %d)", userId, key, state);
        }
        getSharedPrefsForWriting(userId).putInt(key, state).apply();
    }

    /**
     * Builds a string key for saving the playback state for a specific media source (and user)
     */
    private String getPlaybackStateKey(@UserIdInt int userId) {
        ComponentName mediaComponent;
        synchronized (mLock) {
            mediaComponent =
                    getPrimaryMediaComponentsForUserLocked(userId)[MEDIA_SOURCE_MODE_PLAYBACK];
        }
        StringBuilder builder = new StringBuilder().append(PLAYBACK_STATE_KEY).append(userId);
        if (mediaComponent != null) {
            builder.append(mediaComponent.flattenToString());
        }
        return builder.toString();
    }

    private String getMediaSourceKey(int mode, @UserIdInt int userId) {
        return SOURCE_KEY + mode + SOURCE_KEY_SEPARATOR + userId;
    }

    private String getLastUpdateKey(@UserIdInt int userId) {
        return LAST_UPDATE_KEY + userId;
    }

    /**
     * Updates active media controller from the list that has the same component name as the primary
     * media component. Clears callback and resets media controller to null if not found.
     */
    @GuardedBy("mLock")
    private void updateActiveMediaControllerLocked(List<MediaController> mediaControllers,
            @UserIdInt int userId) {
        UserMediaPlayContext userMediaPlayContext = getOrCreateUserMediaPlayContextLocked(userId);
        if (userMediaPlayContext.mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK] == null) {
            return;
        }
        if (userMediaPlayContext.mActiveMediaController != null) {
            userMediaPlayContext.mActiveMediaController.unregisterCallback(
                    userMediaPlayContext.mMediaControllerCallback);
            userMediaPlayContext.mActiveMediaController = null;
        }
        for (MediaController controller : mediaControllers) {
            if (matchPrimaryMediaSource(controller.getPackageName(), getClassName(controller),
                    MEDIA_SOURCE_MODE_PLAYBACK, userId)) {
                userMediaPlayContext.mActiveMediaController = controller;
                PlaybackState state = controller.getPlaybackState();
                savePlaybackState(state, userId);
                // Specify Handler to receive callbacks on, to avoid defaulting to the calling
                // thread; this method can be called from the MediaSessionManager callback.
                // Using the version of this method without passing a handler causes a
                // RuntimeException for failing to create a Handler.
                controller.registerCallback(userMediaPlayContext.mMediaControllerCallback,
                        mHandler);
                return;
            }
        }
    }

    /**
     * Returns whether we should autoplay the current media source
     */
    private boolean shouldStartPlayback(int config, @UserIdInt int userId) {
        switch (config) {
            case AUTOPLAY_CONFIG_NEVER:
                return false;
            case AUTOPLAY_CONFIG_ALWAYS:
                return true;
            case AUTOPLAY_CONFIG_RETAIN_PER_SOURCE:
                if (!sharedPrefsInitialized()) {
                    return false;
                }
                int savedState =
                        mSharedPrefs.getInt(getPlaybackStateKey(userId), PlaybackState.STATE_NONE);
                if (DEBUG) {
                    Slogf.d(TAG, "Getting saved playback state %d for user %d. Last saved on %d",
                            savedState, userId,
                            mSharedPrefs.getLong(getLastUpdateKey(userId), -1));
                }
                return savedState == PlaybackState.STATE_PLAYING;
            case AUTOPLAY_CONFIG_RETAIN_PREVIOUS:
                int currentPlaybackState;
                synchronized (mLock) {
                    currentPlaybackState =
                            getOrCreateUserMediaPlayContextLocked(userId).mCurrentPlaybackState;
                }
                return currentPlaybackState == PlaybackState.STATE_PLAYING;
            default:
                Slogf.e(TAG, "Unsupported playback configuration: " + config);
                return false;
        }
    }

    /**
     * Gets the editor used to update shared preferences.
     */
    private SharedPreferences.Editor getSharedPrefsForWriting(@UserIdInt int userId) {
        long now = System.currentTimeMillis();
        String lastUpdateKey = getLastUpdateKey(userId);
        Slogf.i(TAG, "Updating %s to %d", lastUpdateKey, now);
        return mSharedPrefs.edit().putLong(lastUpdateKey, now);
    }

    @NonNull
    private static String getClassName(MediaController controller) {
        Bundle sessionExtras = controller.getExtras();
        String value =
                sessionExtras == null ? "" : sessionExtras.getString(
                        Car.CAR_EXTRA_BROWSE_SERVICE_FOR_SESSION);
        return value != null ? value : "";
    }

    private static String mediaModeToString(@CarMediaManager.MediaSourceMode int mode) {
        return DebugUtils.constantToString(CarMediaManager.class, "MEDIA_SOURCE_", mode);
    }

    private final class MediaKeyEventListener implements KeyEventListener {

        /**
         * Handles a media key event from {@link CarInputService}.
         *
         * <p>When there are multiple active media sessions, stop after first successful delivery.
         */
        @Override
        public void onKeyEvent(KeyEvent event, int displayType, int seat) {
            if (DEBUG) {
                Slogf.d(TAG, "onKeyEvent(%s, %d, %d)", event, displayType, seat);
            }
            int occupantZoneId = mOccupantZoneService.getOccupantZoneIdForSeat(seat);
            if (occupantZoneId == INVALID_ZONE_ID) {
                Slogf.w(TAG, "Failed to find a valid occupant zone for seat %d."
                        + " Ignoring key event %s", seat, event);
                return;
            }
            int userId = mOccupantZoneService.getUserForOccupant(occupantZoneId);
            if (userId == INVALID_USER_ID) {
                Slogf.w(TAG, "Failed to find a valid user for occupant zone %d."
                        + " Ignoring key event %s", occupantZoneId, event);
                return;
            }
            List<MediaController> mediaControllers = mMediaSessionManager.getActiveSessionsForUser(
                    /* notificationListeners= */ null, UserHandle.of(userId));
            // Send the key event until it is successfully sent to any of the active sessions.
            boolean sent = false;
            for (int i = 0; !sent && i < mediaControllers.size(); i++) {
                sent = mediaControllers.get(i).dispatchMediaButtonEvent(event);
            }

            if (DEBUG) {
                if (sent) {
                    Slogf.d(TAG, "Successfully sent the key event %s to user %d", event, userId);
                } else {
                    Slogf.d(TAG, "No active media session can receive the key event %s for user %d",
                            event, userId);
                }
            }
        }
    }
}
