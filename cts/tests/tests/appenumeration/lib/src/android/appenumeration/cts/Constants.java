/*
 * Copyright (C) 2020 The Android Open Source Project
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

public class Constants {
    public static final String PKG_BASE = "android.appenumeration.";
    public static final String TEST_PKG = "android.appenumeration.cts";
    public static final String MOCK_SPELL_CHECKER_PKG = "com.android.cts.mockspellchecker";
    public static final String MOCK_IME_PKG = "android.inputmethodservice.cts.ime1";
    /** A package that has two split apks. */
    public static final String SPLIT_PKG = "com.android.cts.norestart";

    /** The shared library for getting dependent packages */
    public static final String TEST_SHARED_LIB_NAME = "android.test.runner";
    public static final String TEST_NONEXISTENT_PACKAGE_NAME_1 = "com.android.cts.nonexistent1";
    public static final String TEST_NONEXISTENT_PACKAGE_NAME_2 = "com.android.cts.nonexistent2";

    /** A package that queries for {@link #TARGET_NO_API} package */
    public static final String QUERIES_PACKAGE = PKG_BASE + "queries.pkg";
    /** A package has a provider that queries for {@link #TARGET_NO_API} package */
    public static final String QUERIES_PACKAGE_PROVIDER = PKG_BASE + "queries.pkg.hasprovider";
    /** Queries for the unexported authority in {@link #TARGET_FILTERS} provider */
    public static final String QUERIES_UNEXPORTED_PROVIDER_AUTH =
            PKG_BASE + "queries.provider.authority.unexported";
    /** Queries for the unexported action in {@link #TARGET_FILTERS} provider */
    public static final String QUERIES_UNEXPORTED_PROVIDER_ACTION =
            PKG_BASE + "queries.provider.action.unexported";
    /** Queries for the unexported action in {@link #TARGET_FILTERS} service filter */
    public static final String QUERIES_UNEXPORTED_SERVICE_ACTION =
            PKG_BASE + "queries.service.action.unexported";
    /** Queries for the unexported action in {@link #TARGET_FILTERS} activity filter */
    public static final String QUERIES_UNEXPORTED_ACTIVITY_ACTION =
            PKG_BASE + "queries.activity.action.unexported";
    /** A package that queries for the authority in {@link #TARGET_FILTERS} provider */
    public static final String QUERIES_PROVIDER_AUTH = PKG_BASE + "queries.provider.authority";
    /** A package that queries for the authority in {@link #TARGET_FILTERS} provider */
    public static final String QUERIES_PROVIDER_ACTION = PKG_BASE + "queries.provider.action";
    /** A package that queries for the action in {@link #TARGET_FILTERS} service filter */
    public static final String QUERIES_SERVICE_ACTION = PKG_BASE + "queries.service.action";
    /** A package that queries for the action in {@link #TARGET_FILTERS} activity filter */
    public static final String QUERIES_ACTIVITY_ACTION = PKG_BASE + "queries.activity.action";
    /** A package that has no queries but gets the QUERY_ALL_PACKAGES permission */
    public static final String QUERIES_NOTHING_PERM = PKG_BASE + "queries.nothing.haspermission";
    /** A package that has no queries but has a provider that can be queried */
    public static final String QUERIES_NOTHING_PROVIDER = PKG_BASE + "queries.nothing.hasprovider";
    /** A package that has no queries tag or permissions but targets Q */
    public static final String QUERIES_NOTHING_Q = PKG_BASE + "queries.nothing.q";
    /** A package that has no queries tag or permission to query any specific packages */
    public static final String QUERIES_NOTHING = PKG_BASE + "queries.nothing";
    /** Another package that has no queries tag or permission to query any specific packages */
    public static final String QUERIES_NOTHING_RECEIVES_URI =
            PKG_BASE + "queries.nothing.receives.uri";
    public static final String QUERIES_NOTHING_RECEIVES_PERM_URI =
            PKG_BASE + "queries.nothing.receives.perm.uri";
    public static final String QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI =
            PKG_BASE + "queries.nothing.receives.persistable.uri";
    public static final String QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI =
            PKG_BASE + "queries.nothing.receives.nonpersistable.uri";
    /** Another package that has no queries tag or permission to query any specific packages */
    public static final String QUERIES_NOTHING_SEES_INSTALLER =
            PKG_BASE + "queries.nothing.sees.installer";
    /** A package that queries nothing, but is part of a shared user */
    public static final String QUERIES_NOTHING_SHARED_USER = PKG_BASE + "queries.nothing.shareduid";
    /** A package that queries nothing, but uses a shared library */
    public static final String QUERIES_NOTHING_USES_LIBRARY =
            PKG_BASE + "queries.nothing.useslibrary";
    /** A package that queries nothing, but uses a shared library */
    public static final String QUERIES_NOTHING_USES_OPTIONAL_LIBRARY =
            PKG_BASE + "queries.nothing.usesoptionallibrary";
    /** A package that queries via wildcard action. */
    public static final String QUERIES_WILDCARD_ACTION = PKG_BASE + "queries.wildcard.action";
    /** A package that queries for all BROWSABLE intents. */
    public static final String QUERIES_WILDCARD_BROWSABLE = PKG_BASE + "queries.wildcard.browsable";
    /** A package that queries for all profile / contact targets. */
    public static final String QUERIES_WILDCARD_CONTACTS = PKG_BASE + "queries.wildcard.contacts";
    /** A package that queries for document viewer / editor targets. */
    public static final String QUERIES_WILDCARD_EDITOR = PKG_BASE + "queries.wildcard.editor";
    /** A package that queries for all jpeg share targets. */
    public static final String QUERIES_WILDCARD_SHARE = PKG_BASE + "queries.wildcard.share";
    /** A package that queries for all web intent browsable targets. */
    public static final String QUERIES_WILDCARD_WEB = PKG_BASE + "queries.wildcard.web";
    /** A package that queries for only browser intent targets. */
    public static final String QUERIES_WILDCARD_BROWSER = PKG_BASE + "queries.wildcard.browser";

    /** A package that queries for {@link #TARGET_NO_API} package */
    public static final String TARGET_SHARED_USER = PKG_BASE + "noapi.shareduid";
    /** A package that exposes itself via various intent filters (activities, services, etc.) */
    public static final String TARGET_FILTERS = PKG_BASE + "filters";
    /** A package that declares itself force queryable, making it visible to all other packages.
     *  This is installed as forceQueryable as non-system apps cannot declare themselves as such. */
    public static final String TARGET_FORCEQUERYABLE = PKG_BASE + "forcequeryable";
    /** A package that declares itself force queryable, but is installed normally making it not
     *  visible to other packages */
    public static final String TARGET_FORCEQUERYABLE_NORMAL =
            PKG_BASE + "forcequeryable.normalinstall";
    /** A package with no published API and so isn't queryable by anything but package name */
    public static final String TARGET_NO_API = PKG_BASE + "noapi";
    /** A package with no published API and just for installing/uninstalling during test */
    public static final String TARGET_STUB = PKG_BASE + "stub";
    public static final String TARGET_STUB_SHARED_USER = PKG_BASE + "stub.shareduid";
    /** A package that offers an activity used for opening / editing file types */
    public static final String TARGET_EDITOR = PKG_BASE + "editor.activity";
    /** A package that offers an activity used viewing a contact / profile */
    public static final String TARGET_CONTACTS = PKG_BASE + "contacts.activity";
    /** A package that offers an content sharing activity */
    public static final String TARGET_SHARE = PKG_BASE + "share.activity";
    /** A package that offers an activity that handles browsable web intents for a specific host */
    public static final String TARGET_WEB = PKG_BASE + "web.activity";
    /** A package that offers an activity acts as a browser, but use a prefix wildcard for host */
    public static final String TARGET_PREFIX_WILDCARD_WEB =
            PKG_BASE + "prefix.wildcard.web.activity";
    /** A package that offers an activity acts as a browser with host undefined */
    public static final String TARGET_BROWSER = PKG_BASE + "browser.activity";
    /** A package that offers an activity acts as a browser, but uses a wildcard for host */
    public static final String TARGET_BROWSER_WILDCARD = PKG_BASE + "browser.wildcard.activity";
    /** A package that offers a shared library */
    public static final String TARGET_SHARED_LIBRARY_PACKAGE = "com.android.cts.ctsshim";
    /** A package that exposes itself as a syncadapter. */
    public static final String TARGET_SYNCADAPTER = PKG_BASE + "syncadapter";
    /** A package that exposes itself as a syncadapter with a shared uid. */
    public static final String TARGET_SYNCADAPTER_SHARED_USER = PKG_BASE + "syncadapter.shareduid";
    /** A package that exposes itself as a appwidgetprovider. */
    public static final String TARGET_APPWIDGETPROVIDER = PKG_BASE + "appwidgetprovider";
    /** A package that exposes itself as a appwidgetprovider with a shared uid. */
    public static final String TARGET_APPWIDGETPROVIDER_SHARED_USER =
            PKG_BASE + "appwidgetprovider.shareduid";
    /** A package that offers an activity which handles preferred activity test intent for the
     *  tests of preferred activity. */
    public static final String TARGET_PREFERRED_ACTIVITY = PKG_BASE + "preferred.activity";
    /** An authority that offers a syncadapter. */
    public static final String TARGET_SYNCADAPTER_AUTHORITY = TARGET_SYNCADAPTER + ".authority";

    public static final String AUTHORITY_SUFFIX = ".authority";
    private static final String BASE_PATH = "/data/local/tmp/cts/appenumeration/";
    public static final String TARGET_NO_API_APK = BASE_PATH + "CtsAppEnumerationNoApi.apk";
    public static final String TARGET_STUB_APK = BASE_PATH + "CtsAppEnumerationStub.apk";
    public static final String TARGET_STUB_SHARED_USER_APK =
            BASE_PATH + "CtsAppEnumerationStubSharedUser.apk";
    public static final String TARGET_FILTERS_APK = BASE_PATH + "CtsAppEnumerationFilters.apk";
    public static final String QUERIES_NOTHING_APK =
            BASE_PATH + "CtsAppEnumerationQueriesNothing.apk";
    public static final String QUERIES_NOTHING_PROVIDER_APK =
            BASE_PATH + "CtsAppEnumerationQueriesNothingHasProvider.apk";
    public static final String QUERIES_NOTHING_RECEIVES_PERSISTABLE_URI_APK =
            BASE_PATH + "CtsAppEnumerationQueriesNothingReceivesPersistableUri.apk";
    public static final String QUERIES_NOTHING_RECEIVES_NON_PERSISTABLE_URI_APK =
            BASE_PATH + "CtsAppEnumerationQueriesNothingReceivesNonPersistableUri.apk";
    public static final String QUERIES_NOTHING_RECEIVES_PERM_URI_APK =
            BASE_PATH + "CtsAppEnumerationQueriesNothingReceivesPermissionProtectedUri.apk";
    public static final String QUERIES_NOTHING_RECEIVES_URI_APK =
            BASE_PATH + "CtsAppEnumerationQueriesNothingReceivesUri.apk";
    public static final String QUERIES_NOTHING_SEES_INSTALLER_APK =
            BASE_PATH + "CtsAppEnumerationQueriesNothingSeesInstaller.apk";
    public static final String CTS_MOCK_SPELL_CHECKER_APK = BASE_PATH + "CtsMockSpellChecker.apk";
    public static final String CTS_MOCK_IME_APK = BASE_PATH + "CtsInputMethod1.apk";
    public static final String SPLIT_BASE_APK = BASE_PATH + "CtsNoRestartBase.apk";
    public static final String SPLIT_FEATURE_APK = BASE_PATH + "CtsNoRestartFeature.apk";

    public static final String[] ALL_QUERIES_TARGETING_R_PACKAGES = {
            QUERIES_NOTHING,
            QUERIES_NOTHING_PERM,
            QUERIES_ACTIVITY_ACTION,
            QUERIES_SERVICE_ACTION,
            QUERIES_PROVIDER_AUTH,
            QUERIES_UNEXPORTED_ACTIVITY_ACTION,
            QUERIES_UNEXPORTED_SERVICE_ACTION,
            QUERIES_UNEXPORTED_PROVIDER_AUTH,
            QUERIES_PACKAGE,
            QUERIES_NOTHING_SHARED_USER,
            QUERIES_WILDCARD_ACTION,
            QUERIES_WILDCARD_BROWSABLE,
            QUERIES_WILDCARD_CONTACTS,
            QUERIES_WILDCARD_EDITOR,
            QUERIES_WILDCARD_SHARE,
            QUERIES_WILDCARD_WEB,
    };

    public static final String ACTIVITY_CLASS_TEST = PKG_BASE + "cts.TestActivity";
    public static final String ACTIVITY_CLASS_DUMMY_ACTIVITY = PKG_BASE + "testapp.DummyActivity";
    public static final String ACTIVITY_CLASS_NOT_EXPORTED =
            PKG_BASE + "testapp.DummyActivityNotExported";
    public static final String ACTIVITY_CLASS_PERMISSION_PROTECTED =
            PKG_BASE + "testapp.ActivityPermissionProtected";

    public static final String SERVICE_CLASS_DUMMY_SERVICE = PKG_BASE + "testapp.DummyService";
    public static final String SERVICE_CLASS_SYNC_ADAPTER =
            PKG_BASE + "testapp.MockSyncAdapterService";

    public static final String SERVICE_CLASS_SELF_VISIBILITY_SERVICE =
            PKG_BASE + "cts.TestPmComponentDiscoveryService";

    public static final String ACTION_MANIFEST_ACTIVITY = PKG_BASE + "action.ACTIVITY";
    public static final String ACTION_MANIFEST_UNEXPORTED_ACTIVITY =
            PKG_BASE + "action.ACTIVITY_UNEXPORTED";
    public static final String ACTION_MANIFEST_SERVICE = PKG_BASE + "action.SERVICE";
    public static final String ACTION_MANIFEST_PROVIDER = PKG_BASE + "action.PROVIDER";
    public static final String ACTION_SEND_RESULT = PKG_BASE + "cts.action.SEND_RESULT";
    public static final String ACTION_GET_PACKAGE_INFO = PKG_BASE + "cts.action.GET_PACKAGE_INFO";
    public static final String ACTION_GET_PACKAGES_FOR_UID =
            PKG_BASE + "cts.action.GET_PACKAGES_FOR_UID";
    public static final String ACTION_GET_NAME_FOR_UID =
            PKG_BASE + "cts.action.GET_NAME_FOR_UID";
    public static final String ACTION_GET_NAMES_FOR_UIDS =
            PKG_BASE + "cts.action.GET_NAMES_FOR_UIDS";
    public static final String ACTION_CHECK_SIGNATURES = PKG_BASE + "cts.action.CHECK_SIGNATURES";
    public static final String ACTION_HAS_SIGNING_CERTIFICATE =
            PKG_BASE + "cts.action.HAS_SIGNING_CERTIFICATE";
    public static final String ACTION_START_FOR_RESULT = PKG_BASE + "cts.action.START_FOR_RESULT";
    public static final String ACTION_START_DIRECTLY = PKG_BASE + "cts.action.START_DIRECTLY";
    public static final String ACTION_JUST_FINISH = PKG_BASE + "cts.action.JUST_FINISH";
    public static final String ACTION_AWAIT_PACKAGE_REMOVED =
            PKG_BASE + "cts.action.AWAIT_PACKAGE_REMOVED";
    public static final String ACTION_AWAIT_PACKAGE_ADDED =
            PKG_BASE + "cts.action.AWAIT_PACKAGE_ADDED";
    public static final String ACTION_AWAIT_PACKAGE_FULLY_REMOVED =
            PKG_BASE + "cts.action.AWAIT_PACKAGE_FULLY_REMOVED";
    public static final String ACTION_AWAIT_PACKAGE_DATA_CLEARED =
            PKG_BASE + "cts.action.AWAIT_PACKAGE_DATA_CLEARED";
    public static final String ACTION_QUERY_ACTIVITIES =
            PKG_BASE + "cts.action.QUERY_INTENT_ACTIVITIES";
    public static final String ACTION_QUERY_SERVICES =
            PKG_BASE + "cts.action.QUERY_INTENT_SERVICES";
    public static final String ACTION_QUERY_PROVIDERS =
            PKG_BASE + "cts.action.QUERY_INTENT_PROVIDERS";
    public static final String ACTION_GET_INSTALLED_PACKAGES =
            PKG_BASE + "cts.action.GET_INSTALLED_PACKAGES";
    public static final String ACTION_START_SENDER_FOR_RESULT =
            PKG_BASE + "cts.action.START_SENDER_FOR_RESULT";
    public static final String ACTION_QUERY_RESOLVER =
            PKG_BASE + "cts.action.QUERY_RESOLVER_FOR_VISIBILITY";
    public static final String ACTION_BIND_SERVICE = PKG_BASE + "cts.action.BIND_SERVICE";
    public static final String ACTION_GET_SYNCADAPTER_TYPES =
            PKG_BASE + "cts.action.GET_SYNCADAPTER_TYPES";
    public static final String ACTION_GET_SYNCADAPTER_PACKAGES_FOR_AUTHORITY =
            PKG_BASE + "cts.action.GET_SYNCADAPTER_PACKAGES_FOR_AUTHORITY";
    public static final String ACTION_GET_SYNCADAPTER_CONTROL_PANEL =
            PKG_BASE + "cts.action.GET_SYNCADAPTER_CONTROL_PANEL";
    public static final String ACTION_GET_INSTALLED_APPWIDGET_PROVIDERS =
            PKG_BASE + "cts.action.GET_INSTALLED_APPWIDGET_PROVIDERS";
    public static final String ACTION_REQUEST_SYNC_AND_AWAIT_STATUS =
            PKG_BASE + "cts.action.REQUEST_SYNC_AND_AWAIT_STATUS";
    public static final String ACTION_REQUEST_PERIODIC_SYNC =
            PKG_BASE + "cts.action.REQUEST_PERIODIC_SYNC";
    public static final String ACTION_SET_SYNC_AUTOMATICALLY =
            PKG_BASE + "cts.action.SET_SYNC_AUTOMATICALLY";
    public static final String ACTION_GET_SYNC_AUTOMATICALLY =
            PKG_BASE + "cts.action.GET_SYNC_AUTOMATICALLY";
    public static final String ACTION_GET_IS_SYNCABLE =
            PKG_BASE + "cts.action.GET_IS_SYNCABLE";
    public static final String ACTION_GET_PERIODIC_SYNCS =
            PKG_BASE + "cts.action.GET_PERIODIC_SYNCS";
    public static final String ACTION_AWAIT_PACKAGES_SUSPENDED =
            PKG_BASE + "cts.action.AWAIT_PACKAGES_SUSPENDED";
    public static final String ACTION_LAUNCHER_APPS_IS_ACTIVITY_ENABLED =
            PKG_BASE + "cts.action.LAUNCHER_APPS_IS_ACTIVITY_ENABLED";
    public static final String ACTION_LAUNCHER_APPS_GET_SUSPENDED_PACKAGE_LAUNCHER_EXTRAS =
            PKG_BASE + "cts.action.LAUNCHER_APPS_GET_SUSPENDED_PACKAGE_LAUNCHER_EXTRAS";
    public static final String ACTION_AWAIT_LAUNCHER_APPS_CALLBACK =
            PKG_BASE + "cts.action.AWAIT_LAUNCHER_APPS_CALLBACK";
    public static final String ACTION_GET_SHAREDLIBRARY_DEPENDENT_PACKAGES =
            PKG_BASE + "cts.action.GET_SHAREDLIBRARY_DEPENDENT_PACKAGES";
    public static final String ACTION_GET_PREFERRED_ACTIVITIES =
            PKG_BASE + "cts.action.GET_PREFERRED_ACTIVITIES";
    public static final String ACTION_SET_INSTALLER_PACKAGE_NAME =
            PKG_BASE + "cts.action.SET_INSTALLER_PACKAGE_NAME";
    public static final String ACTION_GET_INSTALLED_ACCESSIBILITYSERVICES_PACKAGES =
            PKG_BASE + "cts.action.GET_INSTALLED_ACCESSIBILITYSERVICES_PACKAGES";
    public static final String ACTION_LAUNCHER_APPS_SHOULD_HIDE_FROM_SUGGESTIONS =
            PKG_BASE + "cts.action.LAUNCHER_APPS_SHOULD_HIDE_FROM_SUGGESTIONS";
    public static final String ACTION_CHECK_URI_PERMISSION =
            PKG_BASE + "cts.action.CHECK_URI_PERMISSION";
    public static final String ACTION_CHECK_CONTENT_URI_PERMISSION_FULL =
            PKG_BASE + "cts.action.CHECK_CONTENT_URI_PERMISSION_FULL";
    public static final String ACTION_TAKE_PERSISTABLE_URI_PERMISSION =
            PKG_BASE + "cts.action.TAKE_PERSISTABLE_URI_PERMISSION";
    public static final String ACTION_CAN_PACKAGE_QUERY =
            PKG_BASE + "cts.action.CAN_PACKAGE_QUERY";
    public static final String ACTION_CAN_PACKAGE_QUERIES =
            PKG_BASE + "cts.action.CAN_PACKAGE_QUERIES";
    public static final String ACTION_GET_ALL_PACKAGE_INSTALLER_SESSIONS =
            PKG_BASE + "cts.action.GET_ALL_PACKAGE_INSTALLER_SESSIONS";
    public static final String ACTION_AWAIT_LAUNCHER_APPS_SESSION_CALLBACK =
            PKG_BASE + "cts.action.AWAIT_LAUNCHER_APPS_SESSION_CALLBACK";
    public static final String ACTION_GET_SESSION_INFO =
            PKG_BASE + "cts.action.GET_SESSION_INFO";
    public static final String ACTION_GET_STAGED_SESSIONS =
            PKG_BASE + "cts.action.GET_STAGED_SESSIONS";
    public static final String ACTION_GET_ALL_SESSIONS =
            PKG_BASE + "cts.action.GET_ALL_SESSIONS";
    public static final String ACTION_PENDING_INTENT_GET_ACTIVITY =
            PKG_BASE + "cts.action.PENDING_INTENT_GET_ACTIVITY";
    public static final String ACTION_PENDING_INTENT_GET_CREATOR_PACKAGE =
            PKG_BASE + "cts.action.PENDING_INTENT_GET_CREATOR_PACKAGE";
    public static final String ACTION_CHECK_PACKAGE =
            PKG_BASE + "cts.action.CHECK_PACKAGE";
    public static final String ACTION_GRANT_URI_PERMISSION =
            PKG_BASE + "cts.action.GRANT_URI_PERMISSION";
    public static final String ACTION_REVOKE_URI_PERMISSION =
            PKG_BASE + "cts.action.REVOKE_URI_PERMISSION";
    public static final String ACTION_AWAIT_PACKAGE_RESTARTED =
            PKG_BASE + "cts.action.AWAIT_PACKAGE_RESTARTED";
    public static final String ACTION_GET_CONTENT_PROVIDER_MIME_TYPE =
            PKG_BASE + "cts.action.GET_CONTENT_PROVIDER_MIME_TYPE";
    public static final String ACTION_APP_ENUMERATION_PREFERRED_ACTIVITY =
            PKG_BASE + "cts.action.APP_ENUMERATION_PREFERRED_ACTIVITY";
    public static final String ACTION_GET_ENABLED_SPELL_CHECKER_INFOS =
            PKG_BASE + "cts.action.GET_ENABLED_SPELL_CHECKER_INFOS";
    public static final String ACTION_GET_INPUT_METHOD_LIST =
            PKG_BASE + "cts.action.GET_INPUT_METHOD_LIST";
    public static final String ACTION_GET_ENABLED_INPUT_METHOD_LIST =
            PKG_BASE + "cts.action.GET_ENABLED_INPUT_METHOD_LIST";
    public static final String ACTION_GET_ENABLED_INPUT_METHOD_SUBTYPE_LIST =
            PKG_BASE + "cts.action.GET_ENABLED_INPUT_METHOD_SUBTYPE_LIST";
    public static final String ACTION_ACCOUNT_MANAGER_GET_AUTHENTICATOR_TYPES =
            PKG_BASE + "cts.action.ACCOUNT_MANAGER_GET_AUTHENTICATOR_TYPES";
    public static final String ACTION_MEDIA_SESSION_MANAGER_IS_TRUSTED_FOR_MEDIA_CONTROL =
            PKG_BASE + "cts.action.MEDIA_SESSION_MANAGER_IS_TRUSTED_FOR_MEDIA_CONTROL";

    public static final String ACCOUNT_NAME = "CtsAppEnumerationTests";
    public static final String ACCOUNT_TYPE = "android.appenumeration.account.type";
    public static final String ACCOUNT_TYPE_SHARED_USER =
            "android.appenumeration.shareduid.account.type";

    public static final String EXTRA_REMOTE_CALLBACK = "remoteCallback";
    public static final String EXTRA_REMOTE_READY_CALLBACK = "remoteReadyCallback";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_FLAGS = "flags";
    public static final String EXTRA_DATA = "data";
    public static final String EXTRA_CERT = "cert";
    public static final String EXTRA_AUTHORITY = "authority";
    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_PENDING_INTENT = "pendingIntent";
    public static final String EXTRA_INPUT_METHOD_INFO = "inputmethodinfo";

    public static final int CALLBACK_EVENT_INVALID = -1;
    public static final int CALLBACK_EVENT_PACKAGE_ADDED = 0;
    public static final int CALLBACK_EVENT_PACKAGE_REMOVED = 1;
    public static final int CALLBACK_EVENT_PACKAGE_CHANGED = 2;
    public static final int CALLBACK_EVENT_PACKAGES_AVAILABLE = 3;
    public static final int CALLBACK_EVENT_PACKAGES_UNAVAILABLE = 4;
    public static final int CALLBACK_EVENT_PACKAGES_SUSPENDED = 5;
    public static final int CALLBACK_EVENT_PACKAGES_UNSUSPENDED = 6;
}
