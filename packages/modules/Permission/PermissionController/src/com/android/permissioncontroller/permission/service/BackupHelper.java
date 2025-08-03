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

package com.android.permissioncontroller.permission.service;

import static android.content.Context.MODE_PRIVATE;
import static android.content.pm.PackageManager.FLAG_PERMISSION_POLICY_FIXED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_SYSTEM_FIXED;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.util.Xml.newSerializer;

import static com.android.permissioncontroller.Constants.DELAYED_RESTORE_PERMISSIONS_FILE;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.permission.PermissionManager.SplitPermissionInfo;
import android.util.ArraySet;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.BuildCompat;

import com.android.permissioncontroller.Constants;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissions;
import com.android.permissioncontroller.permission.model.Permission;
import com.android.permissioncontroller.permission.utils.CollectionUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper for creating and restoring permission backups.
 */
public class BackupHelper {
    private static final String LOG_TAG = BackupHelper.class.getSimpleName();

    private static final String TAG_PERMISSION_BACKUP = "perm-grant-backup";
    private static final String ATTR_PLATFORM_VERSION = "version";

    private static final String TAG_ALL_GRANTS = "rt-grants";

    private static final String TAG_GRANT = "grant";
    private static final String ATTR_PACKAGE_NAME = "pkg";

    private static final String TAG_SIGNING_INFO = "sign";
    private static final String TAG_CURRENT_CERTIFICATE = "curr-cert";
    private static final String TAG_PAST_CERTIFICATE = "past-cert";
    private static final String ATTR_CERTIFICATE_DIGEST = "digest";

    private static final String TAG_PERMISSION = "perm";
    private static final String ATTR_PERMISSION_NAME = "name";
    private static final String ATTR_IS_GRANTED = "g";
    private static final String ATTR_USER_SET = "set";
    private static final String ATTR_USER_FIXED = "fixed";
    private static final String ATTR_WAS_REVIEWED = "was-reviewed";

    /** Flags of permissions to <u>not</u> back up */
    private static final int SYSTEM_RUNTIME_GRANT_MASK = FLAG_PERMISSION_POLICY_FIXED
            | FLAG_PERMISSION_SYSTEM_FIXED;

    /** Make sure only one user can change the delayed permissions at a time */
    private static final Object sLock = new Object();

    private final Context mContext;

    /**
     * Create a new backup utils for a user.
     *
     * @param context A context to use
     * @param user The user that is backed up / restored
     */
    public BackupHelper(@NonNull Context context, @NonNull UserHandle user) {
        try {
            mContext = context.createPackageContextAsUser(context.getPackageName(), 0, user);
        } catch (PackageManager.NameNotFoundException doesNotHappen) {
            throw new IllegalStateException();
        }
    }

    /**
     * Forward parser and skip everything up to the end of the current tag.
     *
     * @param parser The parser to forward
     */
    private static void skipToEndOfTag(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        int numOpenTags = 1;
        while (numOpenTags > 0) {
            switch (parser.next()) {
                case START_TAG:
                    numOpenTags++;
                    break;
                case END_TAG:
                    numOpenTags--;
                    break;
                case END_DOCUMENT:
                    return;
            }
        }
    }

    /**
     * Forward parser to a given direct sub-tag.
     *
     * @param parser The parser to forward
     * @param tag The tag to search for
     */
    private void skipToTag(@NonNull XmlPullParser parser, @NonNull String tag)
            throws IOException, XmlPullParserException {
        int type;
        do {
            type = parser.next();

            switch (type) {
                case START_TAG:
                    if (!parser.getName().equals(tag)) {
                        skipToEndOfTag(parser);
                    }

                    return;
            }
        } while (type != END_DOCUMENT);
    }

    /**
     * Read a XML file and return the packages stored in it.
     *
     * @param parser The file to read
     *
     * @return The packages in this file
     */
    private @NonNull ArrayList<BackupPackageState> parseFromXml(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        ArrayList<BackupPackageState> pkgStates = new ArrayList<>();

        skipToTag(parser, TAG_PERMISSION_BACKUP);

        int backupPlatformVersion;
        try {
            backupPlatformVersion = Integer.parseInt(
                    parser.getAttributeValue(null, ATTR_PLATFORM_VERSION));
        } catch (NumberFormatException ignored) {
            // Platforms P and before did not store the platform version
            backupPlatformVersion = Build.VERSION_CODES.P;
        }

        skipToTag(parser, TAG_ALL_GRANTS);

        if (parser.getEventType() != START_TAG && !parser.getName().equals(TAG_ALL_GRANTS)) {
            throw new XmlPullParserException("Could not find " + TAG_PERMISSION_BACKUP + " > "
                    + TAG_ALL_GRANTS);
        }

        // Read packages to restore from xml
        int type;
        do {
            type = parser.next();

            switch (type) {
                case START_TAG:
                    switch (parser.getName()) {
                        case TAG_GRANT:
                            try {
                                pkgStates.add(BackupPackageState.parseFromXml(parser, mContext,
                                        backupPlatformVersion));
                            } catch (XmlPullParserException e) {
                                Log.e(LOG_TAG, "Could not parse permissions ", e);
                                skipToEndOfTag(parser);
                            }
                            break;
                        default:
                            // ignore tag
                            Log.w(LOG_TAG, "Found unexpected tag " + parser.getName()
                                    + " during restore");
                            skipToEndOfTag(parser);
                    }
            }
        } while (type != END_DOCUMENT);

        return pkgStates;
    }

    /**
     * Try to restore the permission state from XML.
     *
     * <p>If some apps could not be restored, the leftover apps are written to
     * {@link Constants#DELAYED_RESTORE_PERMISSIONS_FILE}.
     *
     * @param parser The xml to read
     */
    void restoreState(@NonNull XmlPullParser parser) throws IOException, XmlPullParserException {
        ArrayList<BackupPackageState> pkgStates = parseFromXml(parser);

        ArrayList<BackupPackageState> packagesToRestoreLater = new ArrayList<>();
        int numPkgStates = pkgStates.size();
        if (numPkgStates > 0) {
            // Try to restore packages
            for (int i = 0; i < numPkgStates; i++) {
                BackupPackageState pkgState = pkgStates.get(i);

                PackageInfo pkgInfo;
                try {
                    pkgInfo = mContext.getPackageManager().getPackageInfo(pkgState.mPackageName,
                            GET_PERMISSIONS | GET_SIGNING_CERTIFICATES);
                } catch (PackageManager.NameNotFoundException ignored) {
                    packagesToRestoreLater.add(pkgState);
                    continue;
                }

                if (!checkCertificateDigestsMatch(pkgInfo, pkgState)) {
                    continue;
                }

                pkgState.restore(mContext, pkgInfo);
            }
        }

        synchronized (sLock) {
            writeDelayedStorePkgsLocked(packagesToRestoreLater);
        }
    }

    /**
     * Returns whether the backed up package and the package being restored have compatible signing
     * certificate digests.
     *
     * <p> Permissions should only be restored if the backed up package has the same signing
     * certificate(s) or an ancestor (in the case of certification rotation).
     *
     * <p>If no certificates are found stored for the backed up package, we return true anyway as
     * certificate storage does not exist before {@link Build.VERSION_CODES.TIRAMISU}.
     */
    private boolean checkCertificateDigestsMatch(
            @NonNull PackageInfo packageToRestoreInfo,
            @NonNull BackupPackageState backupPackageState) {
        // No signing information was stored for the backed up app.
        if (backupPackageState.mBackupSigningInfoState == null) {
            return true;
        }

        // The backed up app was unsigned.
        if (backupPackageState.mBackupSigningInfoState.mCurrentCertDigests.isEmpty()) {
            return false;
        }

        // We don't have signing information for the restored app, but the backed up app was signed.
        if (packageToRestoreInfo.signingInfo == null) {
            return false;
        }

        // The restored app is unsigned.
        if (packageToRestoreInfo.signingInfo.getApkContentsSigners() == null
                || packageToRestoreInfo.signingInfo.getApkContentsSigners().length == 0) {
            return false;
        }

        // If the restored app is a system app, we allow permissions to be restored without any
        // certificate checks.
        // System apps are signed with the device's platform certificate, so on
        // different phones the same system app can have different certificates.
        // We perform this check to be consistent with the Backup and Restore feature logic in
        // frameworks/base/services/core/java/com/android/server/backup/BackupUtils.java
        if ((packageToRestoreInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return true;
        }

        // Both backed up app and restored app have signing information, so we check that these are
        // compatible for the purpose of restoring permissions to the restored app.
        return hasCompatibleSignaturesForRestore(packageToRestoreInfo.signingInfo,
                backupPackageState.mBackupSigningInfoState);
    }

    /**
     * Write a xml file for the given packages.
     *
     * @param serializer The file to write to
     * @param pkgs The packages to write
     */
    private static void writePkgsAsXml(@NonNull XmlSerializer serializer,
            @NonNull ArrayList<BackupPackageState> pkgs) throws IOException {
        serializer.startDocument(null, true);

        serializer.startTag(null, TAG_PERMISSION_BACKUP);

        if (BuildCompat.isAtLeastQ()) {
            // STOPSHIP: Remove compatibility code once Q SDK level is declared
            serializer.attribute(null, ATTR_PLATFORM_VERSION,
                    Integer.valueOf(Build.VERSION_CODES.Q).toString());
        } else {
            serializer.attribute(null, ATTR_PLATFORM_VERSION,
                    Integer.valueOf(Build.VERSION.SDK_INT).toString());
        }

        serializer.startTag(null, TAG_ALL_GRANTS);

        int numPkgs = pkgs.size();
        for (int i = 0; i < numPkgs; i++) {
            BackupPackageState packageState = pkgs.get(i);

            if (packageState != null) {
                packageState.writeAsXml(serializer);
            }
        }

        serializer.endTag(null, TAG_ALL_GRANTS);
        serializer.endTag(null, TAG_PERMISSION_BACKUP);

        serializer.endDocument();
    }

    /**
     * Update the {@link Constants#DELAYED_RESTORE_PERMISSIONS_FILE} to contain the
     * {@code packagesToRestoreLater}.
     *
     * @param packagesToRestoreLater The new pkgs in the delayed restore file
     */
    private void writeDelayedStorePkgsLocked(
            @NonNull ArrayList<BackupPackageState> packagesToRestoreLater) {
        try (OutputStream delayedRestoreData = mContext.openFileOutput(
                DELAYED_RESTORE_PERMISSIONS_FILE, MODE_PRIVATE)) {
            XmlSerializer serializer = newSerializer();
            serializer.setOutput(delayedRestoreData, UTF_8.name());

            writePkgsAsXml(serializer, packagesToRestoreLater);
            serializer.flush();
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not remember which packages still need to be restored", e);
        }
    }

    /**
     * Write the state of all packages as XML.
     *
     * @param serializer The xml to write to
     */
    void writeState(@NonNull XmlSerializer serializer) throws IOException {
        List<PackageInfo> pkgs = mContext.getPackageManager().getInstalledPackages(
                GET_PERMISSIONS | GET_SIGNING_CERTIFICATES);
        ArrayList<BackupPackageState> backupPkgs = new ArrayList<>();

        int numPkgs = pkgs.size();
        for (int i = 0; i < numPkgs; i++) {
            BackupPackageState packageState = BackupPackageState.fromAppPermissions(mContext,
                    pkgs.get(i));

            if (packageState != null) {
                backupPkgs.add(packageState);
            }
        }

        writePkgsAsXml(serializer, backupPkgs);
    }

    /**
     * Restore delayed permission state for a package (if delayed during {@link #restoreState}).
     *
     * @param packageName The package to be restored
     *
     * @return {@code true} if there is still delayed backup left
     */
    boolean restoreDelayedState(@NonNull String packageName) {
        synchronized (sLock) {
            ArrayList<BackupPackageState> packagesToRestoreLater;

            try (FileInputStream delayedRestoreData =
                         mContext.openFileInput(DELAYED_RESTORE_PERMISSIONS_FILE)) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(delayedRestoreData, UTF_8.name());

                packagesToRestoreLater = parseFromXml(parser);
            } catch (IOException | XmlPullParserException e) {
                Log.e(LOG_TAG, "Could not parse delayed permissions", e);
                return false;
            }

            PackageInfo pkgInfo = null;
            try {
                pkgInfo = mContext.getPackageManager().getPackageInfo(
                        packageName, GET_PERMISSIONS | GET_SIGNING_CERTIFICATES);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG, "Could not restore delayed permissions for " + packageName, e);
            }

            if (pkgInfo != null) {
                int numPkgs = packagesToRestoreLater.size();
                for (int i = 0; i < numPkgs; i++) {
                    BackupPackageState pkgState = packagesToRestoreLater.get(i);

                    if (pkgState.mPackageName.equals(packageName) && checkCertificateDigestsMatch(
                            pkgInfo, pkgState)) {
                        pkgState.restore(mContext, pkgInfo);
                        packagesToRestoreLater.remove(i);

                        writeDelayedStorePkgsLocked(packagesToRestoreLater);

                        break;
                    }
                }
            }

            return packagesToRestoreLater.size() > 0;
        }
    }

    /**
     * State that needs to be backed up for a permission.
     */
    private static class BackupPermissionState {
        @NonNull
        private final String mPermissionName;
        private final boolean mIsGranted;
        private final boolean mIsUserSet;
        private final boolean mIsUserFixed;
        private final boolean mWasReviewed;

        private BackupPermissionState(@NonNull String permissionName, boolean isGranted,
                boolean isUserSet, boolean isUserFixed, boolean wasReviewed) {
            mPermissionName = permissionName;
            mIsGranted = isGranted;
            mIsUserSet = isUserSet;
            mIsUserFixed = isUserFixed;
            mWasReviewed = wasReviewed;
        }

        /**
         * Parse a package state from XML.
         *
         * @param parser The data to read
         * @param context a context to use
         * @param backupPlatformVersion The platform version the backup was created on
         *
         * @return The state
         */
        @NonNull
        static List<BackupPermissionState> parseFromXml(@NonNull XmlPullParser parser,
                @NonNull Context context, int backupPlatformVersion)
                throws XmlPullParserException {
            String permName = parser.getAttributeValue(null, ATTR_PERMISSION_NAME);
            if (permName == null) {
                throw new XmlPullParserException("Found " + TAG_PERMISSION + " without "
                        + ATTR_PERMISSION_NAME);
            }

            ArrayList<String> expandedPermissions = new ArrayList<>();
            expandedPermissions.add(permName);

            List<SplitPermissionInfo> splitPerms = context.getSystemService(
                    PermissionManager.class).getSplitPermissions();

            // Expand the properties to permissions that were split between the platform version the
            // backup was taken and the current version.
            int numSplitPerms = splitPerms.size();
            for (int i = 0; i < numSplitPerms; i++) {
                SplitPermissionInfo splitPerm = splitPerms.get(i);
                if (backupPlatformVersion < splitPerm.getTargetSdk()
                        && permName.equals(splitPerm.getSplitPermission())) {
                    expandedPermissions.addAll(splitPerm.getNewPermissions());
                }
            }

            ArrayList<BackupPermissionState> parsedPermissions = new ArrayList<>(
                    expandedPermissions.size());
            int numExpandedPerms = expandedPermissions.size();
            for (int i = 0; i < numExpandedPerms; i++) {
                parsedPermissions.add(new BackupPermissionState(expandedPermissions.get(i),
                        "true".equals(parser.getAttributeValue(null, ATTR_IS_GRANTED)),
                        "true".equals(parser.getAttributeValue(null, ATTR_USER_SET)),
                        "true".equals(parser.getAttributeValue(null, ATTR_USER_FIXED)),
                        "true".equals(parser.getAttributeValue(null, ATTR_WAS_REVIEWED))));
            }

            return parsedPermissions;
        }

        /**
         * Is the permission granted, also considering the app-op.
         *
         * <p>This does not consider the review-required state of the permission.
         *
         * @param perm The permission that might be granted
         *
         * @return {@code true} iff the permission and app-op is granted
         */
        private static boolean isPermGrantedIncludingAppOp(@NonNull Permission perm) {
            return perm.isGranted() && (!perm.affectsAppOp() || perm.isAppOpAllowed());
        }

        /**
         * Get the state of a permission to back up.
         *
         * @param perm The permission to back up
         * @param appSupportsRuntimePermissions If the app supports runtimePermissions
         *
         * @return The state to back up or {@code null} if the permission does not need to be
         * backed up.
         */
        @Nullable
        private static BackupPermissionState fromPermission(@NonNull Permission perm,
                boolean appSupportsRuntimePermissions) {
            int grantFlags = perm.getFlags();

            if ((grantFlags & SYSTEM_RUNTIME_GRANT_MASK) != 0) {
                return null;
            }

            if (!perm.isUserSet() && perm.isGrantedByDefault()) {
                return null;
            }

            boolean permissionWasReviewed;
            boolean isNotInDefaultGrantState;
            if (appSupportsRuntimePermissions) {
                isNotInDefaultGrantState = isPermGrantedIncludingAppOp(perm);
                permissionWasReviewed = false;
            } else {
                isNotInDefaultGrantState = !isPermGrantedIncludingAppOp(perm);
                permissionWasReviewed = !perm.isReviewRequired();
            }

            if (isNotInDefaultGrantState || perm.isUserSet() || perm.isUserFixed()
                    || permissionWasReviewed) {
                return new BackupPermissionState(perm.getName(), isPermGrantedIncludingAppOp(perm),
                        perm.isUserSet(), perm.isUserFixed(), permissionWasReviewed);
            } else {
                return null;
            }
        }

        /**
         * Get the states of all permissions of a group to back up.
         *
         * @param group The group of the permissions to back up
         *
         * @return The state to back up. Empty list if no permissions in the group need to be backed
         * up
         */
        @NonNull
        static ArrayList<BackupPermissionState> fromPermissionGroup(
                @NonNull AppPermissionGroup group) {
            ArrayList<BackupPermissionState> permissionsToRestore = new ArrayList<>();
            List<Permission> perms = group.getPermissions();

            boolean appSupportsRuntimePermissions =
                    group.getApp().applicationInfo.targetSdkVersion >= Build.VERSION_CODES.M;

            int numPerms = perms.size();
            for (int i = 0; i < numPerms; i++) {
                BackupPermissionState permState = fromPermission(perms.get(i),
                        appSupportsRuntimePermissions);
                if (permState != null) {
                    permissionsToRestore.add(permState);
                }
            }

            return permissionsToRestore;
        }

        /**
         * Write this state as XML.
         *
         * @param serializer The file to write to
         */
        void writeAsXml(@NonNull XmlSerializer serializer) throws IOException {
            serializer.startTag(null, TAG_PERMISSION);

            serializer.attribute(null, ATTR_PERMISSION_NAME, mPermissionName);

            if (mIsGranted) {
                serializer.attribute(null, ATTR_IS_GRANTED, "true");
            }

            if (mIsUserSet) {
                serializer.attribute(null, ATTR_USER_SET, "true");
            }

            if (mIsUserFixed) {
                serializer.attribute(null, ATTR_USER_FIXED, "true");
            }

            if (mWasReviewed) {
                serializer.attribute(null, ATTR_WAS_REVIEWED, "true");
            }

            serializer.endTag(null, TAG_PERMISSION);
        }

        /**
         * Restore this permission state.
         *
         * @param appPerms The {@link AppPermissions} to restore the state to
         * @param restoreBackgroundPerms if {@code true} only restore background permissions,
         *                               if {@code false} do not restore background permissions
         */
        void restore(@NonNull AppPermissions appPerms, boolean restoreBackgroundPerms) {
            AppPermissionGroup group = appPerms.getGroupForPermission(mPermissionName);
            if (group == null) {
                Log.w(LOG_TAG, "Could not find group for " + mPermissionName + " in "
                        + appPerms.getPackageInfo().packageName);
                return;
            }

            if (restoreBackgroundPerms != group.isBackgroundGroup()) {
                return;
            }

            Permission perm = group.getPermission(mPermissionName);
            if (mWasReviewed) {
                perm.unsetReviewRequired();
            }

            // Don't grant or revoke fixed permission groups
            if (group.isSystemFixed() || group.isPolicyFixed()) {
                return;
            }

            if (!perm.isUserSet()) {
                if (mIsGranted) {
                    group.grantRuntimePermissions(false, mIsUserFixed,
                            new String[]{mPermissionName});
                } else {
                    group.revokeRuntimePermissions(mIsUserFixed,
                            new String[]{mPermissionName});
                }

                perm.setUserSet(mIsUserSet);
            }
        }
    }

    /** Signing certificate information for a backed up package. */
    private static class BackupSigningInfoState {
        @NonNull
        private final Set<byte[]> mCurrentCertDigests;
        @NonNull
        private final Set<byte[]> mPastCertDigests;

        private BackupSigningInfoState(@NonNull Set<byte[]> currentCertDigests,
                @NonNull Set<byte[]> pastCertDigests) {
            mCurrentCertDigests = currentCertDigests;
            mPastCertDigests = pastCertDigests;
        }

        /**
         * Write this state as XML.
         *
         * @param serializer the file to write to
         */
        void writeAsXml(@NonNull XmlSerializer serializer) throws IOException {
            serializer.startTag(null, TAG_SIGNING_INFO);

            for (byte[] digest : mCurrentCertDigests) {
                serializer.startTag(null, TAG_CURRENT_CERTIFICATE);
                serializer.attribute(
                        null, ATTR_CERTIFICATE_DIGEST,
                        Base64.encodeToString(digest, Base64.NO_WRAP));
                serializer.endTag(null, TAG_CURRENT_CERTIFICATE);
            }

            for (byte[] digest : mPastCertDigests) {
                serializer.startTag(null, TAG_PAST_CERTIFICATE);
                serializer.attribute(
                        null, ATTR_CERTIFICATE_DIGEST,
                        Base64.encodeToString(digest, Base64.NO_WRAP));
                serializer.endTag(null, TAG_PAST_CERTIFICATE);
            }

            serializer.endTag(null, TAG_SIGNING_INFO);
        }

        /**
         * Parse the signing information state from XML.
         *
         * @param parser the data to read
         *
         * @return the signing information state
         */
        @NonNull
        static BackupSigningInfoState parseFromXml(@NonNull XmlPullParser parser)
                throws IOException, XmlPullParserException {
            Set<byte[]> currentCertDigests = new HashSet<>();
            Set<byte[]> pastCertDigests = new HashSet<>();

            while (true) {
                switch (parser.next()) {
                    case START_TAG:
                        switch (parser.getName()) {
                            case TAG_CURRENT_CERTIFICATE:
                                String currentCertDigest =
                                        parser.getAttributeValue(
                                                null, ATTR_CERTIFICATE_DIGEST);
                                if (currentCertDigest == null) {
                                    throw new XmlPullParserException(
                                            "Found " + TAG_CURRENT_CERTIFICATE + " without "
                                                    + ATTR_CERTIFICATE_DIGEST);
                                }
                                currentCertDigests.add(
                                        Base64.decode(currentCertDigest, Base64.NO_WRAP));
                                skipToEndOfTag(parser);
                                break;
                            case TAG_PAST_CERTIFICATE:
                                String pastCertDigest =
                                        parser.getAttributeValue(
                                                null, ATTR_CERTIFICATE_DIGEST);
                                if (pastCertDigest == null) {
                                    throw new XmlPullParserException(
                                            "Found " + TAG_PAST_CERTIFICATE + " without "
                                                    + ATTR_CERTIFICATE_DIGEST);
                                }
                                pastCertDigests.add(
                                        Base64.decode(pastCertDigest, Base64.NO_WRAP));
                                skipToEndOfTag(parser);
                                break;
                            default:
                                Log.w(LOG_TAG, "Found unexpected tag " + parser.getName());
                                skipToEndOfTag(parser);
                        }

                        break;
                    case END_TAG:
                        return new BackupSigningInfoState(
                                currentCertDigests,
                                pastCertDigests);
                }
            }
        }

        /**
         * Construct the signing information state from a {@link SigningInfo} instance.
         *
         * @param signingInfo the {@link SigningInfo} instance
         *
         * @return the state
         */
        @NonNull
        static BackupSigningInfoState fromSigningInfo(@NonNull SigningInfo signingInfo) {
            Set<byte[]> currentCertDigests = new HashSet<>();
            Set<byte[]> pastCertDigests = new HashSet<>();

            Signature[] apkContentsSigners = signingInfo.getApkContentsSigners();
            for (int i = 0; i < apkContentsSigners.length; i++) {
                currentCertDigests.add(
                        computeSha256DigestBytes(apkContentsSigners[i].toByteArray()));
            }

            if (signingInfo.hasPastSigningCertificates()) {
                Signature[] signingCertificateHistory = signingInfo.getSigningCertificateHistory();
                for (int i = 0; i < signingCertificateHistory.length; i++) {
                    pastCertDigests.add(
                            computeSha256DigestBytes(signingCertificateHistory[i].toByteArray()));
                }
            }

            return new BackupSigningInfoState(currentCertDigests, pastCertDigests);
        }
    }

    /**
     * State that needs to be backed up for a package.
     */
    private static class BackupPackageState {
        @NonNull
        final String mPackageName;
        @NonNull
        private final ArrayList<BackupPermissionState> mPermissionsToRestore;
        @Nullable
        private final BackupSigningInfoState mBackupSigningInfoState;

        private BackupPackageState(
                @NonNull String packageName,
                @NonNull ArrayList<BackupPermissionState> permissionsToRestore,
                @Nullable BackupSigningInfoState backupSigningInfoState) {
            mPackageName = packageName;
            mPermissionsToRestore = permissionsToRestore;
            mBackupSigningInfoState = backupSigningInfoState;
        }

        /**
         * Parse a package state from XML.
         *
         * @param parser The data to read
         * @param context a context to use
         * @param backupPlatformVersion The platform version the backup was created on
         *
         * @return The state
         */
        @NonNull
        static BackupPackageState parseFromXml(@NonNull XmlPullParser parser,
                @NonNull Context context, int backupPlatformVersion)
                throws IOException, XmlPullParserException {
            String packageName = parser.getAttributeValue(null, ATTR_PACKAGE_NAME);
            if (packageName == null) {
                throw new XmlPullParserException("Found " + TAG_GRANT + " without "
                        + ATTR_PACKAGE_NAME);
            }

            ArrayList<BackupPermissionState> permissionsToRestore = new ArrayList<>();
            BackupSigningInfoState signingInfo = null;

            while (true) {
                switch (parser.next()) {
                    case START_TAG:
                        switch (parser.getName()) {
                            case TAG_PERMISSION:
                                try {
                                    permissionsToRestore.addAll(
                                            BackupPermissionState.parseFromXml(parser, context,
                                                    backupPlatformVersion));
                                } catch (XmlPullParserException e) {
                                    Log.e(LOG_TAG, "Could not parse permission for "
                                            + packageName, e);
                                }

                                skipToEndOfTag(parser);
                                break;
                            case TAG_SIGNING_INFO:
                                try {
                                    signingInfo = BackupSigningInfoState.parseFromXml(parser);
                                } catch (XmlPullParserException e) {
                                    Log.e(LOG_TAG, "Could not parse signing info for "
                                            + packageName, e);
                                    skipToEndOfTag(parser);
                                }

                                break;
                            default:
                                // ignore tag
                                Log.w(LOG_TAG, "Found unexpected tag " + parser.getName()
                                        + " while restoring " + packageName);
                                skipToEndOfTag(parser);
                        }

                        break;
                    case END_TAG:
                        return new BackupPackageState(
                                packageName,
                                permissionsToRestore,
                                signingInfo);
                    case END_DOCUMENT:
                        throw new XmlPullParserException("Could not parse state for "
                                + packageName);
                }
            }
        }

        /**
         * Get the state of a package to back up.
         *
         * @param context A context to use
         * @param pkgInfo The package to back up.
         *
         * @return The state to back up or {@code null} if no permission of the package need to be
         * backed up.
         */
        @Nullable
        static BackupPackageState fromAppPermissions(@NonNull Context context,
                @NonNull PackageInfo pkgInfo) {
            AppPermissions appPerms = new AppPermissions(context, pkgInfo, false, null);

            ArrayList<BackupPermissionState> permissionsToRestore = new ArrayList<>();
            List<AppPermissionGroup> groups = appPerms.getPermissionGroups();

            int numGroups = groups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                AppPermissionGroup group = groups.get(groupNum);

                permissionsToRestore.addAll(BackupPermissionState.fromPermissionGroup(group));

                // Background permissions are in a subgroup that is not part of
                // {@link AppPermission#getPermissionGroups}. Hence add it explicitly here.
                if (group.getBackgroundPermissions() != null) {
                    permissionsToRestore.addAll(BackupPermissionState.fromPermissionGroup(
                            group.getBackgroundPermissions()));
                }
            }

            if (permissionsToRestore.size() == 0) {
                return null;
            }

            BackupSigningInfoState signingInfoState = null;

            if (pkgInfo.signingInfo != null) {
                signingInfoState = BackupSigningInfoState.fromSigningInfo(pkgInfo.signingInfo);
            }

            return new BackupPackageState(
                    pkgInfo.packageName, permissionsToRestore, signingInfoState);
        }

        /**
         * Write this state as XML.
         *
         * @param serializer The file to write to
         */
        void writeAsXml(@NonNull XmlSerializer serializer) throws IOException {
            if (mPermissionsToRestore.size() == 0) {
                return;
            }

            serializer.startTag(null, TAG_GRANT);
            serializer.attribute(null, ATTR_PACKAGE_NAME, mPackageName);

            int numPerms = mPermissionsToRestore.size();
            for (int i = 0; i < numPerms; i++) {
                mPermissionsToRestore.get(i).writeAsXml(serializer);
            }

            if (mBackupSigningInfoState != null) {
                mBackupSigningInfoState.writeAsXml(serializer);
            }

            serializer.endTag(null, TAG_GRANT);
        }

        /**
         * Restore this package state.
         *
         * @param context A context to use
         * @param pkgInfo The package to restore.
         */
        void restore(@NonNull Context context, @NonNull PackageInfo pkgInfo) {
            AppPermissions appPerms = new AppPermissions(context, pkgInfo, false, true, null);

            ArraySet<String> affectedPermissions = new ArraySet<>();
            // Restore background permissions after foreground permissions as for pre-M apps bg
            // granted and fg revoked cannot be expressed.
            int numPerms = mPermissionsToRestore.size();
            for (int i = 0; i < numPerms; i++) {
                mPermissionsToRestore.get(i).restore(appPerms, false);
                affectedPermissions.add(mPermissionsToRestore.get(i).mPermissionName);
            }
            for (int i = 0; i < numPerms; i++) {
                mPermissionsToRestore.get(i).restore(appPerms, true);
            }

            int numGroups = appPerms.getPermissionGroups().size();
            for (int i = 0; i < numGroups; i++) {
                AppPermissionGroup group = appPerms.getPermissionGroups().get(i);

                // Only denied groups can be user fixed
                if (group.areRuntimePermissionsGranted()) {
                    group.setUserFixed(false);
                }

                AppPermissionGroup bgGroup = group.getBackgroundPermissions();
                if (bgGroup != null) {
                    // Only denied groups can be user fixed
                    if (bgGroup.areRuntimePermissionsGranted()) {
                        bgGroup.setUserFixed(false);
                    }
                }
            }

            appPerms.persistChanges(true, affectedPermissions);
        }
    }

    /**
     * Returns whether the signing certificates of the restored app and backed up app are
     * compatible for the restored app to be granted the backed up app's permissions.
     *
     * <p>This returns true when any one of the following is true:
     *
     * <ul>
     *     <li> the backed up app has multiple signing certificates and the restored app
     *     has identical multiple signing certificates
     *     <li> the backed up app has a single signing certificate and it is the current
     *     single signing certificate of the restored app
     *     <li> the backed up app has a single signing certificate and it is present in the
     *     signing certificate history of the restored app
     *     <li> the backed up app has a single signing certificate and signing certificate
     *     history, and the signing certificate of the restored app is present in that history
     * </ul>*
     */
    private boolean hasCompatibleSignaturesForRestore(@NonNull SigningInfo restoredSigningInfo,
            @NonNull BackupSigningInfoState backupSigningInfoState) {
        Set<byte[]> backupCertDigests = backupSigningInfoState.mCurrentCertDigests;
        Set<byte[]> backupPastCertDigests = backupSigningInfoState.mPastCertDigests;
        Signature[] restoredSignatures = restoredSigningInfo.getApkContentsSigners();

        // Check that both apps have the same number of signing certificates. This will be a
        // required check for both the single and multiple certificate cases.
        if (backupCertDigests.size() != restoredSignatures.length) {
            return false;
        }

        Set<byte[]> restoredCertDigests = new HashSet<>();
        for (Signature signature: restoredSignatures) {
            restoredCertDigests.add(computeSha256DigestBytes(signature.toByteArray()));
        }

        // If the backed up app has multiple signing certificates, the restored app should be
        // signed by that exact set of multiple signing certificates.
        if (backupCertDigests.size() > 1) {
            // Check that the restored certificates are a subset of the backed up certificates.
            if (!CollectionUtils.containsSubset(backupCertDigests, restoredCertDigests)) {
                return false;
            }
            // Check that the backed up certificates are a subset of the restored certificates.
            if (!CollectionUtils.containsSubset(restoredCertDigests, backupCertDigests)) {
                return false;
            }
            return true;
        }

        // If both apps have a single signing certificate, we check if they are equal or if one
        // app's certificate is in the signing certificate history of the other.
        byte[] backupCertDigest = backupCertDigests.iterator().next();
        byte[] restoredPastCertDigest = restoredCertDigests.iterator().next();

        // Check if the backed up app and restored app have the same signing certificate.
        if (Arrays.equals(backupCertDigest, restoredPastCertDigest)) {
            return true;
        }

        // Check if the restored app's certificate is in the backed up app's signing certificate
        // history.
        if (CollectionUtils.contains(backupPastCertDigests, restoredPastCertDigest)) {
            return true;
        }

        // Check if the backed up app's certificate is in the restored app's signing certificate
        // history.
        if (restoredSigningInfo.hasPastSigningCertificates()) {
            // The last element in the pastSigningCertificates array is the current signer;
            // since that was verified above, just check all the signers in the lineage.
            for (int i = 0; i < restoredSigningInfo.getSigningCertificateHistory().length - 1;
                    i++) {
                restoredPastCertDigest = computeSha256DigestBytes(
                        restoredSigningInfo.getSigningCertificateHistory()[i].toByteArray());
                if (Arrays.equals(backupCertDigest, restoredPastCertDigest)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Computes the SHA256 digest of the provided {@code byte} array. */
    @Nullable
    private static byte[] computeSha256DigestBytes(@NonNull byte[] data) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA256");
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        messageDigest.update(data);

        return messageDigest.digest();
    }
}
