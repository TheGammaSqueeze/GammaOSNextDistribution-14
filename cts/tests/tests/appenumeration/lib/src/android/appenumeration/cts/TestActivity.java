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

package android.appenumeration.cts;

import static android.appenumeration.cts.Constants.ACTION_CHECK_SIGNATURES;
import static android.appenumeration.cts.Constants.ACTION_GET_INSTALLED_PACKAGES;
import static android.appenumeration.cts.Constants.ACTION_GET_NAMES_FOR_UIDS;
import static android.appenumeration.cts.Constants.ACTION_GET_NAME_FOR_UID;
import static android.appenumeration.cts.Constants.ACTION_GET_PACKAGES_FOR_UID;
import static android.appenumeration.cts.Constants.ACTION_GET_PACKAGE_INFO;
import static android.appenumeration.cts.Constants.ACTION_HAS_SIGNING_CERTIFICATE;
import static android.appenumeration.cts.Constants.ACTION_JUST_FINISH;
import static android.appenumeration.cts.Constants.ACTION_MANIFEST_SERVICE;
import static android.appenumeration.cts.Constants.ACTION_MEDIA_SESSION_MANAGER_IS_TRUSTED_FOR_MEDIA_CONTROL;
import static android.appenumeration.cts.Constants.ACTION_QUERY_ACTIVITIES;
import static android.appenumeration.cts.Constants.ACTION_QUERY_PROVIDERS;
import static android.appenumeration.cts.Constants.ACTION_QUERY_SERVICES;
import static android.appenumeration.cts.Constants.ACTION_SEND_RESULT;
import static android.appenumeration.cts.Constants.ACTION_START_DIRECTLY;
import static android.appenumeration.cts.Constants.ACTION_START_FOR_RESULT;
import static android.appenumeration.cts.Constants.ACTION_START_SENDER_FOR_RESULT;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_INVALID;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGES_AVAILABLE;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGES_SUSPENDED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGES_UNAVAILABLE;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGES_UNSUSPENDED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGE_ADDED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGE_CHANGED;
import static android.appenumeration.cts.Constants.CALLBACK_EVENT_PACKAGE_REMOVED;
import static android.appenumeration.cts.Constants.EXTRA_ACCOUNT;
import static android.appenumeration.cts.Constants.EXTRA_AUTHORITY;
import static android.appenumeration.cts.Constants.EXTRA_CERT;
import static android.appenumeration.cts.Constants.EXTRA_DATA;
import static android.appenumeration.cts.Constants.EXTRA_ERROR;
import static android.appenumeration.cts.Constants.EXTRA_FLAGS;
import static android.appenumeration.cts.Constants.EXTRA_ID;
import static android.appenumeration.cts.Constants.EXTRA_INPUT_METHOD_INFO;
import static android.appenumeration.cts.Constants.EXTRA_PENDING_INTENT;
import static android.appenumeration.cts.Constants.EXTRA_REMOTE_CALLBACK;
import static android.appenumeration.cts.Constants.EXTRA_REMOTE_READY_CALLBACK;
import static android.appenumeration.cts.Constants.SERVICE_CLASS_DUMMY_SERVICE;
import static android.content.Intent.EXTRA_COMPONENT_NAME;
import static android.content.Intent.EXTRA_PACKAGES;
import static android.content.Intent.EXTRA_RETURN_RESULT;
import static android.content.Intent.EXTRA_UID;
import static android.content.pm.PackageManager.CERT_INPUT_RAW_X509;
import static android.os.Process.INVALID_UID;
import static android.os.Process.ROOT_UID;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.PeriodicSync;
import android.content.ServiceConnection;
import android.content.SyncAdapterType;
import android.content.SyncStatusObserver;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller.SessionCallback;
import android.content.pm.PackageInstaller.SessionInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ApplicationInfoFlags;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.SharedLibraryInfo;
import android.database.Cursor;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.PatternMatcher;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.textservice.SpellCheckerInfo;
import android.view.textservice.TextServicesManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 *  A test activity running in the query and target applications.
 */
public class TestActivity extends Activity {

    private final static long TIMEOUT_MS = 3000;

    /**
     * Extending the timeout time of non broadcast receivers, avoid not
     * receiving callbacks in time on some common low-end platforms and
     * do not affect the situation that callback can be received in advance.
     */
    private final static long EXTENDED_TIMEOUT_MS = 5000;

    SparseArray<RemoteCallback> callbacks = new SparseArray<>();

    private Handler mainHandler;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Object syncStatusHandle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mainHandler = new Handler(getMainLooper());
        backgroundThread = new HandlerThread("testBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        onCommandReady(getIntent());
    }

    @Override
    protected void onDestroy() {
        if (syncStatusHandle != null) {
            ContentResolver.removeStatusChangeListener(syncStatusHandle);
        }
        backgroundThread.quitSafely();
        super.onDestroy();
    }

    private void handleIntent(Intent intent) {
        final RemoteCallback remoteCallback = intent.getParcelableExtra(EXTRA_REMOTE_CALLBACK,
                RemoteCallback.class);
        try {
            final String action = intent.getAction();
            final Intent queryIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            if (ACTION_GET_PACKAGE_INFO.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                sendPackageInfo(remoteCallback, packageName);
            } else if (ACTION_GET_PACKAGES_FOR_UID.equals(action)) {
                final int uid = intent.getIntExtra(EXTRA_UID, INVALID_UID);
                sendPackagesForUid(remoteCallback, uid);
            } else if (ACTION_GET_NAME_FOR_UID.equals(action)) {
                final int uid = intent.getIntExtra(EXTRA_UID, INVALID_UID);
                sendNameForUid(remoteCallback, uid);
            } else if (ACTION_GET_NAMES_FOR_UIDS.equals(action)) {
                final int uid = intent.getIntExtra(EXTRA_UID, INVALID_UID);
                sendNamesForUids(remoteCallback, uid);
            } else if (ACTION_CHECK_SIGNATURES.equals(action)) {
                final int uid1 = getPackageManager().getApplicationInfo(
                        getPackageName(), ApplicationInfoFlags.of(0)).uid;
                final int uid2 = intent.getIntExtra(EXTRA_UID, INVALID_UID);
                sendCheckSignatures(remoteCallback, uid1, uid2);
            } else if (ACTION_HAS_SIGNING_CERTIFICATE.equals(action)) {
                final int uid = intent.getIntExtra(EXTRA_UID, INVALID_UID);
                final byte[] cert = intent.getBundleExtra(EXTRA_DATA).getByteArray(EXTRA_CERT);
                sendHasSigningCertificate(remoteCallback, uid, cert, CERT_INPUT_RAW_X509);
            } else if (ACTION_START_FOR_RESULT.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                int requestCode = RESULT_FIRST_USER + callbacks.size();
                callbacks.put(requestCode, remoteCallback);
                startActivityForResult(
                        new Intent(ACTION_SEND_RESULT).setComponent(
                                new ComponentName(packageName, getClass().getCanonicalName())),
                        requestCode);
                // don't send anything... await result callback
            } else if (ACTION_SEND_RESULT.equals(action)) {
                try {
                    setResult(RESULT_OK,
                            getIntent().putExtra(
                                    Intent.EXTRA_RETURN_RESULT,
                                    getPackageManager().getPackageInfo(getCallingPackage(),
                                            PackageInfoFlags.of(0))));
                } catch (PackageManager.NameNotFoundException e) {
                    setResult(RESULT_FIRST_USER, new Intent().putExtra("error", e));
                }
                finish();
            } else if (ACTION_QUERY_ACTIVITIES.equals(action)) {
                sendQueryIntentActivities(remoteCallback, queryIntent);
            } else if (ACTION_QUERY_SERVICES.equals(action)) {
                sendQueryIntentServices(remoteCallback, queryIntent);
            } else if (ACTION_QUERY_PROVIDERS.equals(action)) {
                sendQueryIntentProviders(remoteCallback, queryIntent);
            } else if (ACTION_START_DIRECTLY.equals(action)) {
                try {
                    startActivity(queryIntent);
                    remoteCallback.sendResult(new Bundle());
                } catch (ActivityNotFoundException e) {
                    sendError(remoteCallback, e);
                }
                finish();
            } else if (ACTION_JUST_FINISH.equals(action)) {
                finish();
            } else if (ACTION_GET_INSTALLED_PACKAGES.equals(action)) {
                sendGetInstalledPackages(remoteCallback, queryIntent.getIntExtra(EXTRA_FLAGS, 0));
            } else if (ACTION_START_SENDER_FOR_RESULT.equals(action)) {
                final PendingIntent pendingIntent = intent.getParcelableExtra(EXTRA_PENDING_INTENT,
                        PendingIntent.class);
                int requestCode = RESULT_FIRST_USER + callbacks.size();
                callbacks.put(requestCode, remoteCallback);
                try {
                    startIntentSenderForResult(pendingIntent.getIntentSender(), requestCode, null,
                            0, 0, 0);
                } catch (IntentSender.SendIntentException e) {
                    sendError(remoteCallback, e);
                }
            } else if (Constants.ACTION_AWAIT_PACKAGE_REMOVED.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                awaitPackageBroadcast(
                        remoteCallback, packageName, Intent.ACTION_PACKAGE_REMOVED, TIMEOUT_MS);
            } else if (Constants.ACTION_AWAIT_PACKAGE_ADDED.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                awaitPackageBroadcast(
                        remoteCallback, packageName, Intent.ACTION_PACKAGE_ADDED, TIMEOUT_MS);
            } else if (Constants.ACTION_AWAIT_PACKAGE_FULLY_REMOVED.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                awaitPackageBroadcast(remoteCallback, packageName,
                        Intent.ACTION_PACKAGE_FULLY_REMOVED, TIMEOUT_MS);
            } else if (Constants.ACTION_AWAIT_PACKAGE_DATA_CLEARED.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                awaitPackageBroadcast(remoteCallback, packageName,
                        Intent.ACTION_PACKAGE_DATA_CLEARED, TIMEOUT_MS);
            } else if (Constants.ACTION_QUERY_RESOLVER.equals(action)) {
                final String authority = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                queryResolverForVisiblePackages(remoteCallback, authority);
            } else if (Constants.ACTION_BIND_SERVICE.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                bindService(remoteCallback, packageName);
            } else if (Constants.ACTION_GET_SYNCADAPTER_TYPES.equals(action)) {
                sendSyncAdapterTypes(remoteCallback);
            } else if (Constants.ACTION_GET_INSTALLED_APPWIDGET_PROVIDERS.equals(action)) {
                sendInstalledAppWidgetProviders(remoteCallback);
            } else if (Constants.ACTION_AWAIT_PACKAGES_SUSPENDED.equals(action)) {
                final String[] awaitPackages = intent.getBundleExtra(EXTRA_DATA)
                        .getStringArray(EXTRA_PACKAGES);
                awaitSuspendedPackagesBroadcast(remoteCallback, Arrays.asList(awaitPackages),
                        Intent.ACTION_PACKAGES_SUSPENDED, TIMEOUT_MS);
            } else if (Constants.ACTION_LAUNCHER_APPS_IS_ACTIVITY_ENABLED.equals(action)) {
                final String componentName = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_COMPONENT_NAME);
                sendIsActivityEnabled(remoteCallback, ComponentName.unflattenFromString(
                        componentName));
            } else if (Constants.ACTION_LAUNCHER_APPS_GET_SUSPENDED_PACKAGE_LAUNCHER_EXTRAS.equals(
                    action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                sendGetSuspendedPackageLauncherExtras(remoteCallback, packageName);
            } else if (Constants.ACTION_GET_SYNCADAPTER_PACKAGES_FOR_AUTHORITY.equals(action)) {
                final String authority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                final int userId = intent.getBundleExtra(EXTRA_DATA)
                        .getInt(Intent.EXTRA_USER);
                sendSyncAdapterPackagesForAuthorityAsUser(remoteCallback, authority, userId);
            } else if (Constants.ACTION_REQUEST_SYNC_AND_AWAIT_STATUS.equals(action)) {
                final String authority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                final Account account = intent.getBundleExtra(EXTRA_DATA)
                        .getParcelable(EXTRA_ACCOUNT, Account.class);
                awaitRequestSyncStatus(remoteCallback, action, account, authority,
                        EXTENDED_TIMEOUT_MS);
            } else if (Constants.ACTION_GET_SYNCADAPTER_CONTROL_PANEL.equals(action)) {
                final String authority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                final Account account = intent.getBundleExtra(EXTRA_DATA)
                        .getParcelable(EXTRA_ACCOUNT, Account.class);
                final ComponentName componentName = intent.getBundleExtra(EXTRA_DATA)
                        .getParcelable(EXTRA_COMPONENT_NAME, ComponentName.class);
                sendGetSyncAdapterControlPanel(remoteCallback, account, authority, componentName);
            } else if (Constants.ACTION_REQUEST_PERIODIC_SYNC.equals(action)) {
                final Account account = intent.getBundleExtra(EXTRA_DATA)
                        .getParcelable(EXTRA_ACCOUNT, Account.class);
                final String authority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                awaitRequestPeriodicSync(remoteCallback, account, authority,
                        TimeUnit.SECONDS.toMillis(15));
            } else if (Constants.ACTION_SET_SYNC_AUTOMATICALLY.equals(action)) {
                final Account account = intent.getBundleExtra(EXTRA_DATA)
                        .getParcelable(EXTRA_ACCOUNT, Account.class);
                final String authority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                setSyncAutomatically(remoteCallback, account, authority);
            } else if (Constants.ACTION_GET_SYNC_AUTOMATICALLY.equals(action)) {
                final String authority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                getSyncAutomatically(remoteCallback, authority);
            } else if (Constants.ACTION_GET_IS_SYNCABLE.equals(action)) {
                final Account account = intent.getBundleExtra(EXTRA_DATA)
                        .getParcelable(EXTRA_ACCOUNT, Account.class);
                final String authority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                getIsSyncable(remoteCallback, account, authority);
            } else if (Constants.ACTION_GET_PERIODIC_SYNCS.equals(action)) {
                final Account account = intent.getBundleExtra(EXTRA_DATA)
                        .getParcelable(EXTRA_ACCOUNT, Account.class);
                final String authority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                getPeriodicSyncs(remoteCallback, account, authority);
            } else if (Constants.ACTION_AWAIT_LAUNCHER_APPS_CALLBACK.equals(action)) {
                final int expectedEventCode = intent.getBundleExtra(EXTRA_DATA)
                        .getInt(EXTRA_FLAGS, CALLBACK_EVENT_INVALID);
                final String[] expectedPackages = intent.getBundleExtra(EXTRA_DATA)
                        .getStringArray(EXTRA_PACKAGES);
                awaitLauncherAppsCallback(remoteCallback, expectedEventCode, expectedPackages,
                        EXTENDED_TIMEOUT_MS);
            } else if (Constants.ACTION_GET_SHAREDLIBRARY_DEPENDENT_PACKAGES.equals(action)) {
                final String sharedLibName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                sendGetSharedLibraryDependentPackages(remoteCallback, sharedLibName);
            } else if (Constants.ACTION_GET_PREFERRED_ACTIVITIES.equals(action)) {
                sendGetPreferredActivities(remoteCallback);
            } else if (Constants.ACTION_SET_INSTALLER_PACKAGE_NAME.equals(action)) {
                final String targetPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                final String installerPackageName = intent.getBundleExtra(EXTRA_DATA)
                        .getString(Intent.EXTRA_INSTALLER_PACKAGE_NAME);
                sendSetInstallerPackageName(remoteCallback, targetPackageName,
                        installerPackageName);
            } else if (Constants.ACTION_GET_INSTALLED_ACCESSIBILITYSERVICES_PACKAGES.equals(
                    action)) {
                sendGetInstalledAccessibilityServicePackages(remoteCallback);
            } else if (Constants.ACTION_LAUNCHER_APPS_SHOULD_HIDE_FROM_SUGGESTIONS.equals(action)) {
                final String targetPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                final int userId = intent.getBundleExtra(EXTRA_DATA).getInt(Intent.EXTRA_USER);
                sendLauncherAppsShouldHideFromSuggestions(remoteCallback, targetPackageName,
                        userId);
            } else if (Constants.ACTION_CHECK_URI_PERMISSION.equals(action)) {
                final String targetPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                final int targetUid = intent.getIntExtra(EXTRA_UID, INVALID_UID);
                final String sourceAuthority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                sendCheckUriPermission(remoteCallback, sourceAuthority, targetPackageName,
                        targetUid);
            } else if (Constants.ACTION_CHECK_CONTENT_URI_PERMISSION_FULL.equals(action)) {
                final String targetPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                final int targetUid = intent.getIntExtra(EXTRA_UID, INVALID_UID);
                final String sourceAuthority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                sendCheckContentUriPermissionFull(remoteCallback, sourceAuthority,
                        targetPackageName, targetUid);
            } else if (Constants.ACTION_TAKE_PERSISTABLE_URI_PERMISSION.equals(action)) {
                final Uri uri = intent.getData();
                final int modeFlags = intent.getFlags() & (Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (uri != null) {
                    getContentResolver().takePersistableUriPermission(uri, modeFlags);
                }
                finish();
            } else if (Constants.ACTION_CAN_PACKAGE_QUERY.equals(action)) {
                final String sourcePackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                final String targetPackageName = intent.getBundleExtra(EXTRA_DATA)
                        .getString(Intent.EXTRA_PACKAGE_NAME);
                sendCanPackageQuery(remoteCallback, sourcePackageName, targetPackageName);
            } else if (Constants.ACTION_CAN_PACKAGE_QUERIES.equals(action)) {
                final String sourcePackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                final String[] targetPackageNames = intent.getBundleExtra(EXTRA_DATA)
                        .getStringArray(EXTRA_PACKAGES);
                sendCanPackageQueries(remoteCallback, sourcePackageName, targetPackageNames);
            } else if (Constants.ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS.equals(action)) {
                final List<SessionInfo> infos = getSystemService(LauncherApps.class)
                        .getAllPackageInstallerSessions();
                sendSessionInfosListResult(remoteCallback, infos);
            } else if (Constants.ACTION_AWAIT_LAUNCHER_APPS_SESSION_CALLBACK.equals(action)) {
                final int expectedEventCode = intent.getBundleExtra(EXTRA_DATA)
                        .getInt(EXTRA_ID, SessionInfo.INVALID_ID);
                awaitLauncherAppsSessionCallback(remoteCallback, expectedEventCode,
                        EXTENDED_TIMEOUT_MS);
            } else if (Constants.ACTION_GET_SESSION_INFO.equals(action)) {
                final int sessionId = intent.getBundleExtra(EXTRA_DATA)
                        .getInt(EXTRA_ID, SessionInfo.INVALID_ID);
                final List<SessionInfo> infos = Arrays.asList(getPackageManager()
                        .getPackageInstaller()
                        .getSessionInfo(sessionId));
                sendSessionInfosListResult(remoteCallback, infos);
            } else if (Constants.ACTION_GET_STAGED_SESSIONS.equals(action)) {
                final List<SessionInfo> infos = getPackageManager().getPackageInstaller()
                        .getStagedSessions();
                sendSessionInfosListResult(remoteCallback, infos);
            } else if (Constants.ACTION_GET_ALL_SESSIONS.equals(action)) {
                final List<SessionInfo> infos = getPackageManager().getPackageInstaller()
                        .getAllSessions();
                sendSessionInfosListResult(remoteCallback, infos);
            } else if (Constants.ACTION_PENDING_INTENT_GET_ACTIVITY.equals(action)) {
                sendPendingIntentGetActivity(remoteCallback);
            } else if (Constants.ACTION_PENDING_INTENT_GET_CREATOR_PACKAGE.equals(action)) {
                sendPendingIntentGetCreatorPackage(remoteCallback,
                        intent.getParcelableExtra(EXTRA_PENDING_INTENT, PendingIntent.class));
            } else if (Constants.ACTION_CHECK_PACKAGE.equals(action)) {
                // Using ROOT_UID as default value here to pass the check in #verifyAndGetBypass,
                // this is intended by design.
                final int uid = intent.getIntExtra(EXTRA_UID, ROOT_UID);
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                sendCheckPackageResult(remoteCallback, packageName, uid);
            } else if (Constants.ACTION_GRANT_URI_PERMISSION.equals(action)) {
                final String targetPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                final String sourceAuthority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                sendGrantUriPermission(remoteCallback, sourceAuthority, targetPackageName);
            } else if (Constants.ACTION_REVOKE_URI_PERMISSION.equals(action)) {
                final String sourceAuthority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                sendRevokeUriPermission(remoteCallback, sourceAuthority);
            } else if (Constants.ACTION_AWAIT_PACKAGE_RESTARTED.equals(action)) {
                final String packageName = intent.getBundleExtra(EXTRA_DATA).getString(
                        Intent.EXTRA_PACKAGE_NAME);
                awaitPackageRestartedBroadcast(remoteCallback, packageName,
                        Intent.ACTION_PACKAGE_RESTARTED, TIMEOUT_MS);
            } else if (Constants.ACTION_GET_CONTENT_PROVIDER_MIME_TYPE.equals(action)) {
                final String authority = intent.getBundleExtra(EXTRA_DATA)
                        .getString(EXTRA_AUTHORITY);
                sendGetContentProviderMimeType(remoteCallback, authority);
            } else if (Constants.ACTION_GET_ENABLED_SPELL_CHECKER_INFOS.equals(action)) {
                sendGetEnabledSpellCheckerInfos(remoteCallback);
            } else if (Constants.ACTION_GET_INPUT_METHOD_LIST.equals(action)) {
                sendGetInputMethodList(remoteCallback);
            } else if (Constants.ACTION_GET_ENABLED_INPUT_METHOD_LIST.equals(action)) {
                sendGetEnabledInputMethodList(remoteCallback);
            } else if (Constants.ACTION_GET_ENABLED_INPUT_METHOD_SUBTYPE_LIST.equals(action)) {
                final InputMethodInfo info = intent.getBundleExtra(EXTRA_DATA)
                        .getParcelable(EXTRA_INPUT_METHOD_INFO, InputMethodInfo.class);
                sendGetEnabledInputMethodSubtypeList(remoteCallback, info);
            } else if (Constants.ACTION_ACCOUNT_MANAGER_GET_AUTHENTICATOR_TYPES.equals(action)) {
                sendAccountManagerGetAuthenticatorTypes(remoteCallback);
            } else if (ACTION_MEDIA_SESSION_MANAGER_IS_TRUSTED_FOR_MEDIA_CONTROL.equals(action)) {
                final String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
                final int uid = intent.getIntExtra(EXTRA_UID, INVALID_UID);
                sendMediaSessionManagerIsTrustedForMediaControl(remoteCallback, packageName, uid);
            } else {
                sendError(remoteCallback, new Exception("unknown action " + action));
            }
        } catch (Exception e) {
            sendError(remoteCallback, e);
        }
    }

    private void sendGetInstalledAccessibilityServicePackages(RemoteCallback remoteCallback) {
        final String[] packages = getSystemService(
                AccessibilityManager.class).getInstalledAccessibilityServiceList().stream().map(
                p -> p.getComponentName().getPackageName()).distinct().toArray(String[]::new);
        final Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, packages);
        remoteCallback.sendResult(result);
        finish();
    }

    private void onCommandReady(Intent intent) {
        final RemoteCallback callback = intent.getParcelableExtra(EXTRA_REMOTE_READY_CALLBACK,
                RemoteCallback.class);
        if (callback != null) {
            callback.sendResult(null);
        }
    }

    private void awaitPackageBroadcast(RemoteCallback remoteCallback, String packageName,
            String action, long timeoutMs) {
        final IntentFilter filter = new IntentFilter(action);
        filter.addDataScheme("package");
        filter.addDataSchemeSpecificPart(packageName, PatternMatcher.PATTERN_LITERAL);
        final Object token = new Object();
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final Bundle result = new Bundle();
                result.putString(EXTRA_DATA, intent.getDataString());
                remoteCallback.sendResult(result);
                mainHandler.removeCallbacksAndMessages(token);
                finish();
            }
        }, filter, Context.RECEIVER_EXPORTED);
        mainHandler.postDelayed(
                () -> sendError(remoteCallback,
                        new MissingBroadcastException(action, timeoutMs)),
                token, timeoutMs);
    }

    private void awaitPackageRestartedBroadcast(RemoteCallback remoteCallback,
            String expectedPkgName, String action, long timeoutMs) {
        final IntentFilter filter = new IntentFilter(action);
        filter.addDataScheme(IntentFilter.SCHEME_PACKAGE);
        final Object token = new Object();
        final Bundle result = new Bundle();
        final Uri expectedData = Uri.fromParts("package", expectedPkgName, null /* fragment */);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String intentData = intent.getDataString();
                if (expectedData.toString().equals(intentData)) {
                    mainHandler.removeCallbacksAndMessages(token);
                    result.putString(Intent.EXTRA_PACKAGE_NAME, expectedPkgName);
                    remoteCallback.sendResult(result);
                    finish();
                }
            }
        }, filter, Context.RECEIVER_EXPORTED);
        mainHandler.postDelayed(() -> remoteCallback.sendResult(result), token, timeoutMs);
    }

    private void awaitSuspendedPackagesBroadcast(RemoteCallback remoteCallback,
            List<String> awaitList, String action, long timeoutMs) {
        final IntentFilter filter = new IntentFilter(action);
        final ArrayList<String> suspendedList = new ArrayList<>();
        final Object token = new Object();
        final Runnable sendResult = () -> {
            final Bundle result = new Bundle();
            result.putStringArray(EXTRA_PACKAGES, suspendedList.toArray(new String[] {}));
            remoteCallback.sendResult(result);
            finish();
        };
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final Bundle extras = intent.getExtras();
                final String[] changedList = extras.getStringArray(
                        Intent.EXTRA_CHANGED_PACKAGE_LIST);
                suspendedList.addAll(Arrays.stream(changedList).filter(
                        p -> awaitList.contains(p)).collect(Collectors.toList()));
                if (suspendedList.size() == awaitList.size()) {
                    mainHandler.removeCallbacksAndMessages(token);
                    sendResult.run();
                }
            }
        }, filter, Context.RECEIVER_EXPORTED);
        mainHandler.postDelayed(() -> sendResult.run(), token, timeoutMs);
    }

    private boolean matchPackageNames(String[] expectedPackages, String[] actualPackages) {
        Arrays.sort(expectedPackages);
        Arrays.sort(actualPackages);
        return Arrays.equals(expectedPackages, actualPackages);
    }

    private void awaitLauncherAppsCallback(RemoteCallback remoteCallback, int expectedEventCode,
            String[] expectedPackages, long timeoutMs) {
        final Object token = new Object();
        final Bundle result = new Bundle();
        final LauncherApps launcherApps = getSystemService(LauncherApps.class);
        final LauncherApps.Callback launcherAppsCallback = new LauncherApps.Callback() {

            private void onPackageStateUpdated(String[] packageNames, int resultCode) {
                if (resultCode != expectedEventCode) {
                    return;
                }
                if (!matchPackageNames(expectedPackages, packageNames)) {
                    return;
                }

                mainHandler.removeCallbacksAndMessages(token);
                result.putStringArray(EXTRA_PACKAGES, packageNames);
                result.putInt(EXTRA_FLAGS, resultCode);
                remoteCallback.sendResult(result);

                launcherApps.unregisterCallback(this);
                finish();
            }

            @Override
            public void onPackageRemoved(String packageName, UserHandle user) {
                onPackageStateUpdated(new String[]{packageName}, CALLBACK_EVENT_PACKAGE_REMOVED);
            }

            @Override
            public void onPackageAdded(String packageName, UserHandle user) {
                onPackageStateUpdated(new String[]{packageName}, CALLBACK_EVENT_PACKAGE_ADDED);
            }

            @Override
            public void onPackageChanged(String packageName, UserHandle user) {
                onPackageStateUpdated(new String[]{packageName}, CALLBACK_EVENT_PACKAGE_CHANGED);
            }

            @Override
            public void onPackagesAvailable(String[] packageNames, UserHandle user,
                    boolean replacing) {
                onPackageStateUpdated(packageNames, CALLBACK_EVENT_PACKAGES_AVAILABLE);
            }

            @Override
            public void onPackagesUnavailable(String[] packageNames, UserHandle user,
                    boolean replacing) {
                onPackageStateUpdated(packageNames, CALLBACK_EVENT_PACKAGES_UNAVAILABLE);
            }

            @Override
            public void onPackagesSuspended(String[] packageNames, UserHandle user) {
                onPackageStateUpdated(packageNames, CALLBACK_EVENT_PACKAGES_SUSPENDED);
                super.onPackagesSuspended(packageNames, user);
            }

            @Override
            public void onPackagesUnsuspended(String[] packageNames, UserHandle user) {
                onPackageStateUpdated(packageNames, CALLBACK_EVENT_PACKAGES_UNSUSPENDED);
                super.onPackagesUnsuspended(packageNames, user);
            }
        };

        launcherApps.registerCallback(launcherAppsCallback);

        mainHandler.postDelayed(() -> {
            result.putStringArray(EXTRA_PACKAGES, new String[]{});
            result.putInt(EXTRA_FLAGS, CALLBACK_EVENT_INVALID);
            remoteCallback.sendResult(result);

            launcherApps.unregisterCallback(launcherAppsCallback);
            finish();
        }, token, timeoutMs);
    }

    private void sendGetInstalledPackages(RemoteCallback remoteCallback, int flags) {
        String[] packages =
                getPackageManager().getInstalledPackages(PackageInfoFlags.of(flags))
                        .stream().map(p -> p.packageName).distinct().toArray(String[]::new);
        Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, packages);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendQueryIntentActivities(RemoteCallback remoteCallback, Intent queryIntent) {
        final String[] resolveInfos = getPackageManager().queryIntentActivities(
                queryIntent, ResolveInfoFlags.of(0)).stream()
                .map(ri -> ri.activityInfo.applicationInfo.packageName)
                .distinct()
                .toArray(String[]::new);
        Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, resolveInfos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendQueryIntentServices(RemoteCallback remoteCallback, Intent queryIntent) {
        final String[] resolveInfos = getPackageManager().queryIntentServices(
                queryIntent, ResolveInfoFlags.of(0)).stream()
                .map(ri -> ri.serviceInfo.applicationInfo.packageName)
                .distinct()
                .toArray(String[]::new);
        Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, resolveInfos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendQueryIntentProviders(RemoteCallback remoteCallback, Intent queryIntent) {
        final String[] resolveInfos = getPackageManager().queryIntentContentProviders(
                queryIntent, ResolveInfoFlags.of(0)).stream()
                .map(ri -> ri.providerInfo.applicationInfo.packageName)
                .distinct()
                .toArray(String[]::new);
        Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, resolveInfos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void queryResolverForVisiblePackages(RemoteCallback remoteCallback, String authority) {
        backgroundHandler.post(() -> {
            Uri queryUri = Uri.parse("content://" + authority + "/test");
            Cursor query = getContentResolver().query(queryUri, null, null, null, null);
            if (query == null || !query.moveToFirst()) {
                sendError(remoteCallback,
                        new IllegalStateException(
                                "Query of " + queryUri + " could not be completed"));
                return;
            }
            ArrayList<String> visiblePackages = new ArrayList<>();
            while (!query.isAfterLast()) {
                visiblePackages.add(query.getString(0));
                query.moveToNext();
            }
            query.close();

            mainHandler.post(() -> {
                Bundle result = new Bundle();
                result.putStringArray(EXTRA_RETURN_RESULT, visiblePackages.toArray(new String[]{}));
                remoteCallback.sendResult(result);
                finish();
            });

        });
    }

    private void sendError(RemoteCallback remoteCallback, Exception failure) {
        Bundle result = new Bundle();
        result.putSerializable(EXTRA_ERROR, failure);
        if (remoteCallback != null) {
            remoteCallback.sendResult(result);
        }
        finish();
    }

    private void sendPackageInfo(RemoteCallback remoteCallback, String packageName) {
        final PackageInfo pi;
        try {
            pi = getPackageManager().getPackageInfo(packageName, PackageInfoFlags.of(0));
        } catch (PackageManager.NameNotFoundException e) {
            sendError(remoteCallback, e);
            return;
        }
        Bundle result = new Bundle();
        result.putParcelable(EXTRA_RETURN_RESULT, pi);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendPackagesForUid(RemoteCallback remoteCallback, int uid) {
        final String[] packages = getPackageManager().getPackagesForUid(uid);
        final Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, packages);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendNameForUid(RemoteCallback remoteCallback, int uid) {
        final String name = getPackageManager().getNameForUid(uid);
        final Bundle result = new Bundle();
        result.putString(EXTRA_RETURN_RESULT, name);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendNamesForUids(RemoteCallback remoteCallback, int uid) {
        final String[] names = getPackageManager().getNamesForUids(new int[]{uid});
        final Bundle result = new Bundle();
        result.putStringArray(EXTRA_RETURN_RESULT, names);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendCheckSignatures(RemoteCallback remoteCallback, int uid1, int uid2) {
        final int signatureResult = getPackageManager().checkSignatures(uid1, uid2);
        final Bundle result = new Bundle();
        result.putInt(EXTRA_RETURN_RESULT, signatureResult);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendHasSigningCertificate(RemoteCallback remoteCallback, int uid, byte[] cert,
            int type) {
        final boolean signatureResult = getPackageManager().hasSigningCertificate(uid, cert, type);
        final Bundle result = new Bundle();
        result.putBoolean(EXTRA_RETURN_RESULT, signatureResult);
        remoteCallback.sendResult(result);
        finish();
    }

    /**
     * Instead of sending a list of package names, this function sends a List of
     * {@link SyncAdapterType}, since the {@link SyncAdapterType#getPackageName()} is a test api
     * which can only be invoked in the instrumentation.
     */
    private void sendSyncAdapterTypes(RemoteCallback remoteCallback) {
        final SyncAdapterType[] types = ContentResolver.getSyncAdapterTypes();
        final ArrayList<Parcelable> parcelables = new ArrayList<>();
        for (SyncAdapterType type : types) {
            parcelables.add(type);
        }
        final Bundle result = new Bundle();
        result.putParcelableArrayList(EXTRA_RETURN_RESULT, parcelables);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendIsActivityEnabled(RemoteCallback remoteCallback, ComponentName componentName) {
        final LauncherApps launcherApps = getSystemService(LauncherApps.class);
        final Bundle result = new Bundle();
        try {
            result.putBoolean(EXTRA_RETURN_RESULT, launcherApps.isActivityEnabled(componentName,
                    Process.myUserHandle()));
        } catch (IllegalArgumentException e) {
        }
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendGetSuspendedPackageLauncherExtras(RemoteCallback remoteCallback,
            String packageName) {
        final LauncherApps launcherApps = getSystemService(LauncherApps.class);
        final Bundle result = new Bundle();
        try {
            result.putBundle(EXTRA_RETURN_RESULT,
                    launcherApps.getSuspendedPackageLauncherExtras(packageName,
                            Process.myUserHandle()));
        } catch (IllegalArgumentException e) {
        }
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendInstalledAppWidgetProviders(RemoteCallback remoteCallback) {
        final AppWidgetManager appWidgetManager = getSystemService(AppWidgetManager.class);
        final List<AppWidgetProviderInfo> providers = appWidgetManager.getInstalledProviders();
        final ArrayList<Parcelable> parcelables = new ArrayList<>();
        for (AppWidgetProviderInfo info : providers) {
            parcelables.add(info);
        }
        final Bundle result = new Bundle();
        result.putParcelableArrayList(EXTRA_RETURN_RESULT, parcelables);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendSyncAdapterPackagesForAuthorityAsUser(RemoteCallback remoteCallback,
            String authority, int userId) {
        final String[] syncAdapterPackages = ContentResolver
                .getSyncAdapterPackagesForAuthorityAsUser(authority, userId);
        final Bundle result = new Bundle();
        result.putStringArray(Intent.EXTRA_PACKAGES, syncAdapterPackages);
        remoteCallback.sendResult(result);
        finish();
    }

    private void awaitRequestSyncStatus(RemoteCallback remoteCallback, String action,
            Account account, String authority, long timeoutMs) {
        ContentResolver.cancelSync(account, authority);
        final Object token = new Object();
        final SyncStatusObserver observer = which -> {
            final Bundle result = new Bundle();
            result.putBoolean(EXTRA_RETURN_RESULT, true);
            remoteCallback.sendResult(result);
            mainHandler.removeCallbacksAndMessages(token);
            finish();
        };
        syncStatusHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                        | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS
                        | ContentResolver.SYNC_OBSERVER_TYPE_PENDING, observer);

        ContentResolver.requestSync(account, authority, new Bundle());
        mainHandler.postDelayed(
                () -> sendError(remoteCallback, new MissingCallbackException(action, timeoutMs)),
                token, timeoutMs);
    }

    private void sendGetSyncAdapterControlPanel(RemoteCallback remoteCallback, Account account,
            String authority, ComponentName componentName) {
        ContentResolver.cancelSync(account, authority);
        ContentResolver.requestSync(account, authority, new Bundle());
        final ActivityManager activityManager = getSystemService(ActivityManager.class);
        final PendingIntent pendingIntent =
                activityManager.getRunningServiceControlPanel(componentName);
        final Bundle result = new Bundle();
        result.putParcelable(EXTRA_RETURN_RESULT, pendingIntent);
        remoteCallback.sendResult(result);
        finish();
    }

    private void awaitRequestPeriodicSync(RemoteCallback remoteCallback, Account account,
            String authority, long timeoutMs) {
        ContentResolver.addPeriodicSync(account, authority, Bundle.EMPTY,
                TimeUnit.HOURS.toSeconds(1));
        final Object token = new Object();
        final Bundle result = new Bundle();
        final Runnable pollingPeriodicSync = new Runnable() {
            @Override
            public void run() {
                if (!ContentResolver.getPeriodicSyncs(account, authority).stream()
                        .anyMatch(sync -> sync.authority.equals(authority))) {
                    mainHandler.postDelayed(this, 100 /* delayMillis */);
                    return;
                }
                mainHandler.removeCallbacksAndMessages(token);
                result.putBoolean(EXTRA_RETURN_RESULT, true);
                remoteCallback.sendResult(result);
                finish();
            }
        };

        mainHandler.post(pollingPeriodicSync);
        mainHandler.postDelayed(() -> {
            mainHandler.removeCallbacks(pollingPeriodicSync);
            result.putBoolean(EXTRA_RETURN_RESULT, false);
            remoteCallback.sendResult(result);
            finish();
        }, token, timeoutMs);
    }

    private void setSyncAutomatically(RemoteCallback remoteCallback, Account account,
            String authority) {
        ContentResolver.setSyncAutomatically(account, authority, true /* sync */);
        remoteCallback.sendResult(null);
        finish();
    }

    private void getSyncAutomatically(RemoteCallback remoteCallback, String authority) {
        final boolean ret = ContentResolver.getSyncAutomatically(null /* account */, authority);
        final Bundle result = new Bundle();
        result.putBoolean(EXTRA_RETURN_RESULT, ret);
        remoteCallback.sendResult(result);
        finish();
    }

    private void getIsSyncable(RemoteCallback remoteCallback, Account account,
            String authority) {
        final int ret = ContentResolver.getIsSyncable(account, authority);
        final Bundle result = new Bundle();
        result.putInt(EXTRA_RETURN_RESULT, ret);
        remoteCallback.sendResult(result);
        finish();
    }

    private void getPeriodicSyncs(RemoteCallback remoteCallback, Account account,
            String authority) {
        final List<PeriodicSync> periodicSyncList =
                ContentResolver.getPeriodicSyncs(account, authority);
        final ArrayList<Parcelable> parcelables = new ArrayList<>();
        for (PeriodicSync sync : periodicSyncList) {
            parcelables.add(sync);
        }
        final Bundle result = new Bundle();
        result.putParcelableArrayList(EXTRA_RETURN_RESULT, parcelables);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendGetSharedLibraryDependentPackages(RemoteCallback remoteCallback,
            String sharedLibName) {
        final List<SharedLibraryInfo> sharedLibraryInfos = getPackageManager()
                .getSharedLibraries(0 /* flags */);
        SharedLibraryInfo sharedLibraryInfo = sharedLibraryInfos.stream().filter(
                info -> sharedLibName.equals(info.getName())).findAny().orElse(null);
        final String[] dependentPackages = sharedLibraryInfo == null ? null
                : sharedLibraryInfo.getDependentPackages().stream()
                        .map(versionedPackage -> versionedPackage.getPackageName())
                        .distinct().collect(Collectors.toList()).toArray(new String[]{});
        final Bundle result = new Bundle();
        result.putStringArray(Intent.EXTRA_PACKAGES, dependentPackages);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendGetPreferredActivities(RemoteCallback remoteCallback) {
        final List<IntentFilter> filters = new ArrayList<>();
        final List<ComponentName> activities = new ArrayList<>();
        getPackageManager().getPreferredActivities(filters, activities, null /* packageName*/);
        final String[] packages = activities.stream()
                .map(componentName -> componentName.getPackageName()).distinct()
                .collect(Collectors.toList()).toArray(new String[]{});
        final Bundle result = new Bundle();
        result.putStringArray(Intent.EXTRA_PACKAGES, packages);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendSetInstallerPackageName(RemoteCallback remoteCallback,
            String targetPackageName, String installerPackageName) {
        try {
            getPackageManager().setInstallerPackageName(targetPackageName, installerPackageName);
            remoteCallback.sendResult(null);
            finish();
        } catch (Exception e) {
            sendError(remoteCallback, e);
        }
    }

    private void sendLauncherAppsShouldHideFromSuggestions(RemoteCallback remoteCallback,
            String targetPackageName, int userId) {
        final LauncherApps launcherApps = getSystemService(LauncherApps.class);
        final boolean hideFromSuggestions = launcherApps.shouldHideFromSuggestions(
                targetPackageName, UserHandle.of(userId));
        final Bundle result = new Bundle();
        result.putBoolean(EXTRA_RETURN_RESULT, hideFromSuggestions);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendCheckUriPermission(RemoteCallback remoteCallback, String sourceAuthority,
            String targetPackageName, int targetUid) {
        Uri uri = getContentUriAndGrantRead(sourceAuthority, targetPackageName);
        final int permissionResult = checkUriPermission(uri, 0 /* pid */, targetUid,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        revokeReadFromUriAndSendPermissionResult(uri, permissionResult, remoteCallback);
    }

    private void sendCheckContentUriPermissionFull(RemoteCallback remoteCallback,
            String sourceAuthority, String targetPackageName, int targetUid) {
        Uri uri = getContentUriAndGrantRead(sourceAuthority, targetPackageName);
        final int permissionResult = checkContentUriPermissionFull(uri, 0 /* pid */, targetUid,
                Intent.FLAG_GRANT_READ_URI_PERMISSION);
        revokeReadFromUriAndSendPermissionResult(uri, permissionResult, remoteCallback);
    }

    private Uri getContentUriAndGrantRead(String sourceAuthority, String targetPackageName) {
        final Uri uri = Uri.parse("content://" + sourceAuthority);
        grantUriPermission(targetPackageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return uri;
    }

    private void revokeReadFromUriAndSendPermissionResult(Uri uri, int permissionResult,
            RemoteCallback remoteCallback) {
        revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final Bundle result = new Bundle();
        result.putInt(EXTRA_RETURN_RESULT, permissionResult);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendGrantUriPermission(RemoteCallback remoteCallback, String sourceAuthority,
            String targetPackageName) {
        final Uri uri = Uri.parse("content://" + sourceAuthority);
        grantUriPermission(targetPackageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        remoteCallback.sendResult(null);
        finish();
    }

    private void sendRevokeUriPermission(RemoteCallback remoteCallback, String sourceAuthority) {
        final Uri uri = Uri.parse("content://" + sourceAuthority);
        revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        remoteCallback.sendResult(null);
        finish();
    }

    private void sendCanPackageQuery(RemoteCallback remoteCallback, String sourcePackageName,
            String targetPackageName) {
        try {
            final boolean visibility = getPackageManager().canPackageQuery(sourcePackageName,
                    targetPackageName);
            final Bundle result = new Bundle();
            result.putBoolean(EXTRA_RETURN_RESULT, visibility);
            remoteCallback.sendResult(result);
            finish();
        } catch (PackageManager.NameNotFoundException e) {
            sendError(remoteCallback, e);
        }
    }

    private void sendCanPackageQueries(RemoteCallback remoteCallback, String sourcePackageName,
            String[] targetPackageNames) {
        try {
            final boolean[] visibilities = getPackageManager().canPackageQuery(sourcePackageName,
                    targetPackageNames);
            final Bundle result = new Bundle();
            result.putBooleanArray(EXTRA_RETURN_RESULT, visibilities);
            remoteCallback.sendResult(result);
            finish();
        } catch (PackageManager.NameNotFoundException e) {
            sendError(remoteCallback, e);
        }
    }

    private void sendSessionInfosListResult(RemoteCallback remoteCallback,
            List<SessionInfo> infos) {
        final ArrayList<Parcelable> parcelables = new ArrayList<>(infos);
        for (SessionInfo info : infos) {
            parcelables.add(info);
        }
        final Bundle result = new Bundle();
        result.putParcelableArrayList(EXTRA_RETURN_RESULT, parcelables);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendGetContentProviderMimeType(RemoteCallback remoteCallback, String authority) {
        final Uri uri = Uri.parse("content://" + authority);
        final ContentResolver resolver = getContentResolver();
        final String mimeType = resolver.getType(uri);
        final Bundle result = new Bundle();
        result.putString(EXTRA_RETURN_RESULT, mimeType);
        remoteCallback.sendResult(result);
        finish();
    }

    private void awaitLauncherAppsSessionCallback(RemoteCallback remoteCallback,
            int expectedSessionId, long timeoutMs) {
        final Object token = new Object();
        final Bundle result = new Bundle();
        final LauncherApps launcherApps = getSystemService(LauncherApps.class);
        final SessionCallback sessionCallback = new SessionCallback() {

            @Override
            public void onCreated(int sessionId) {
                // No-op
            }

            @Override
            public void onBadgingChanged(int sessionId) {
                // No-op
            }

            @Override
            public void onActiveChanged(int sessionId, boolean active) {
                // No-op
            }

            @Override
            public void onProgressChanged(int sessionId, float progress) {
                // No-op
            }

            @Override
            public void onFinished(int sessionId, boolean success) {
                if (sessionId != expectedSessionId) {
                    return;
                }

                mainHandler.removeCallbacksAndMessages(token);
                result.putInt(EXTRA_ID, sessionId);
                remoteCallback.sendResult(result);

                launcherApps.unregisterPackageInstallerSessionCallback(this);
                finish();
            }
        };

        launcherApps.registerPackageInstallerSessionCallback(this.getMainExecutor(),
                sessionCallback);

        mainHandler.postDelayed(() -> {
            result.putInt(EXTRA_ID, SessionInfo.INVALID_ID);
            remoteCallback.sendResult(result);

            launcherApps.unregisterPackageInstallerSessionCallback(sessionCallback);
            finish();
        }, token, timeoutMs);
    }

    private void sendPendingIntentGetActivity(RemoteCallback remoteCallback) {
        final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* requestCode */,
                new Intent(this, TestActivity.class), PendingIntent.FLAG_IMMUTABLE);
        final Bundle result = new Bundle();
        result.putParcelable(EXTRA_PENDING_INTENT, pendingIntent);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendPendingIntentGetCreatorPackage(RemoteCallback remoteCallback,
            PendingIntent pendingIntent) {
        final Bundle result = new Bundle();
        result.putString(Intent.EXTRA_PACKAGE_NAME, pendingIntent.getCreatorPackage());
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendCheckPackageResult(RemoteCallback remoteCallback, String packageName,
            int uid) {
        try {
            getSystemService(AppOpsManager.class).checkPackage(uid, packageName);
            final Bundle result = new Bundle();
            result.putBoolean(EXTRA_RETURN_RESULT, true);
            remoteCallback.sendResult(result);
            finish();
        } catch (SecurityException e) {
            sendError(remoteCallback, e);
        }
    }

    private void sendGetEnabledSpellCheckerInfos(RemoteCallback remoteCallback) {
        final TextServicesManager tsm = getSystemService(TextServicesManager.class);
        final ArrayList<SpellCheckerInfo> infos =
                new ArrayList<>(tsm.getEnabledSpellCheckerInfos());
        final Bundle result = new Bundle();
        result.putParcelableArrayList(EXTRA_RETURN_RESULT, infos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendGetInputMethodList(RemoteCallback remoteCallback) {
        final InputMethodManager inputMethodManager = getSystemService(InputMethodManager.class);
        final ArrayList<InputMethodInfo> infos =
                new ArrayList<>(inputMethodManager.getInputMethodList());
        final Bundle result = new Bundle();
        result.putParcelableArrayList(EXTRA_RETURN_RESULT, infos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendGetEnabledInputMethodList(RemoteCallback remoteCallback) {
        final InputMethodManager inputMethodManager = getSystemService(InputMethodManager.class);
        final ArrayList<InputMethodInfo> infos =
                new ArrayList<>(inputMethodManager.getEnabledInputMethodList());
        final Bundle result = new Bundle();
        result.putParcelableArrayList(EXTRA_RETURN_RESULT, infos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendGetEnabledInputMethodSubtypeList(RemoteCallback remoteCallback,
            InputMethodInfo targetImi) {
        final InputMethodManager inputMethodManager = getSystemService(InputMethodManager.class);
        final ArrayList<InputMethodSubtype> infos = new ArrayList<>(
                inputMethodManager.getEnabledInputMethodSubtypeList(targetImi, true));
        final Bundle result = new Bundle();
        result.putParcelableArrayList(EXTRA_RETURN_RESULT, infos);
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendAccountManagerGetAuthenticatorTypes(RemoteCallback remoteCallback) {
        final AccountManager accountManager = AccountManager.get(this);
        final Bundle result = new Bundle();
        result.putParcelableArray(EXTRA_RETURN_RESULT, accountManager.getAuthenticatorTypes());
        remoteCallback.sendResult(result);
        finish();
    }

    private void sendMediaSessionManagerIsTrustedForMediaControl(RemoteCallback remoteCallback,
            String packageName, int uid) {
        final MediaSessionManager mediaSessionManager =
                getSystemService(MediaSessionManager.class);
        final MediaSessionManager.RemoteUserInfo userInfo =
                new MediaSessionManager.RemoteUserInfo(packageName, 0 /* pid */, uid);
        final boolean isTrusted = mediaSessionManager.isTrustedForMediaControl(userInfo);
        final Bundle result = new Bundle();
        result.putBoolean(EXTRA_RETURN_RESULT, isTrusted);
        remoteCallback.sendResult(result);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final RemoteCallback remoteCallback = callbacks.get(requestCode);
        if (resultCode != RESULT_OK) {
            final Exception e = data.getSerializableExtra(EXTRA_ERROR, Exception.class);
            sendError(remoteCallback, e == null ? new Exception("Result was " + resultCode) : e);
            return;
        }
        final Bundle result = new Bundle();
        result.putParcelable(EXTRA_RETURN_RESULT, data.getParcelableExtra(EXTRA_RETURN_RESULT));
        remoteCallback.sendResult(result);
        finish();
    }

    private void bindService(RemoteCallback remoteCallback, String packageName) {
        final Intent intent = new Intent(ACTION_MANIFEST_SERVICE);
        intent.setClassName(packageName, SERVICE_CLASS_DUMMY_SERVICE);
        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                // No-op
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                // No-op
            }

            @Override
            public void onBindingDied(ComponentName name) {
                // Remote service die
                finish();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                // Since the DummyService doesn't implement onBind, it returns null and
                // onNullBinding would be called. Use postDelayed to keep this service
                // connection alive for 3 seconds.
                mainHandler.postDelayed(() -> {
                    unbindService(this);
                    finish();
                }, TIMEOUT_MS);
            }
        };

        final boolean bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        final Bundle result = new Bundle();
        result.putBoolean(EXTRA_RETURN_RESULT, bound);
        remoteCallback.sendResult(result);
        // Don't invoke finish() right here if service is bound successfully to keep the service
        // connection alive since the ServiceRecord would be removed from the ServiceMap once no
        // client is binding the service.
        if (!bound) finish();
    }
}
