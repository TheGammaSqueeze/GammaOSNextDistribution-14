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

package com.android.role.persistence;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ApexEnvironment;
import android.os.FileUtils;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.permission.persistence.IoUtils;
import com.android.permission.util.PackageUtils;
import com.android.server.security.FileIntegrity;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Persistence implementation for roles.
 *
 * TODO(b/147914847): Remove @hide when it becomes the default.
 * @hide
 */
public class RolesPersistenceImpl implements RolesPersistence {

    private static final String LOG_TAG = RolesPersistenceImpl.class.getSimpleName();

    private static final String APEX_MODULE_NAME = "com.android.permission";

    private static final String ROLES_FILE_NAME = "roles.xml";
    private static final String ROLES_RESERVE_COPY_FILE_NAME = ROLES_FILE_NAME + ".reservecopy";

    private static final String TAG_ROLES = "roles";
    private static final String TAG_ROLE = "role";
    private static final String TAG_HOLDER = "holder";

    private static final String ATTRIBUTE_VERSION = "version";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_FALLBACK_ENABLED = "fallbackEnabled";
    private static final String ATTRIBUTE_PACKAGES_HASH = "packagesHash";

    @VisibleForTesting
    interface Injector {
        void enableFsVerity(@NonNull File file) throws IOException;
    }

    @NonNull
    private final Injector mInjector;

    RolesPersistenceImpl() {
        this(file -> {
            if (SdkLevel.isAtLeastU() && PackageUtils.isApkVerityEnabled()) {
                FileIntegrity.setUpFsVerity(file);
            }
        });
    }

    @VisibleForTesting
    RolesPersistenceImpl(@NonNull Injector injector) {
        mInjector = injector;
    }

    @Nullable
    @Override
    public RolesState readForUser(@NonNull UserHandle user) {
        File file = getFile(user);
        try (FileInputStream inputStream = new AtomicFile(file).openRead()) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(inputStream, null);
            return parseXml(parser);
        } catch (FileNotFoundException e) {
            Log.i(LOG_TAG, "roles.xml not found");
            return null;
        } catch (Exception e) {
            File reserveFile = getReserveCopyFile(user);
            Log.wtf(LOG_TAG, "Reading from reserve copy: " + reserveFile, e);
            try (FileInputStream inputStream = new AtomicFile(reserveFile).openRead()) {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(inputStream, null);
                return parseXml(parser);
            } catch (Exception exceptionReadingReserveFile) {
                Log.e(LOG_TAG, "Failed to read reserve copy: " + reserveFile,
                        exceptionReadingReserveFile);
                // Reserve copy failed, rethrow the original exception wrapped as runtime.
                throw new IllegalStateException("Failed to read roles.xml: " + file , e);
            }
        }
    }

    @NonNull
    private static RolesState parseXml(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (depth > innerDepth || type != XmlPullParser.START_TAG) {
                continue;
            }

            if (parser.getName().equals(TAG_ROLES)) {
                return parseRoles(parser);
            }
        }
        throw new IllegalStateException("Missing <" + TAG_ROLES + "> in roles.xml");
    }

    @NonNull
    private static RolesState parseRoles(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        int version = Integer.parseInt(parser.getAttributeValue(null, ATTRIBUTE_VERSION));
        String packagesHash = parser.getAttributeValue(null, ATTRIBUTE_PACKAGES_HASH);

        Map<String, Set<String>> roles = new ArrayMap<>();
        Set<String> fallbackEnabledRoles = new ArraySet<>();
        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (depth > innerDepth || type != XmlPullParser.START_TAG) {
                continue;
            }

            if (parser.getName().equals(TAG_ROLE)) {
                String roleName = parser.getAttributeValue(null, ATTRIBUTE_NAME);
                String fallbackEnabled = parser.getAttributeValue(null, ATTRIBUTE_FALLBACK_ENABLED);
                if (Boolean.parseBoolean(fallbackEnabled)) {
                    fallbackEnabledRoles.add(roleName);
                }
                Set<String> roleHolders = parseRoleHolders(parser);
                roles.put(roleName, roleHolders);
            }
        }

        return new RolesState(version, packagesHash, roles, fallbackEnabledRoles);
    }

    @NonNull
    private static Set<String> parseRoleHolders(@NonNull XmlPullParser parser)
            throws IOException, XmlPullParserException {
        Set<String> roleHolders = new ArraySet<>();
        int type;
        int depth;
        int innerDepth = parser.getDepth() + 1;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && ((depth = parser.getDepth()) >= innerDepth || type != XmlPullParser.END_TAG)) {
            if (depth > innerDepth || type != XmlPullParser.START_TAG) {
                continue;
            }

            if (parser.getName().equals(TAG_HOLDER)) {
                String roleHolder = parser.getAttributeValue(null, ATTRIBUTE_NAME);
                roleHolders.add(roleHolder);
            }
        }
        return roleHolders;
    }

    @Override
    public void writeForUser(@NonNull RolesState roles, @NonNull UserHandle user) {
        File reserveFile = getReserveCopyFile(user);
        reserveFile.delete();

        File file = getFile(user);
        AtomicFile atomicFile = new AtomicFile(file);
        FileOutputStream outputStream = null;
        try {
            outputStream = atomicFile.startWrite();

            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(outputStream, StandardCharsets.UTF_8.name());
            serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            serializer.startDocument(null, true);

            serializeRoles(serializer, roles);

            serializer.endDocument();
            atomicFile.finishWrite(outputStream);
        } catch (Exception e) {
            Log.wtf(LOG_TAG, "Failed to write roles.xml, restoring backup: " + file,
                    e);
            atomicFile.failWrite(outputStream);
            return;
        } finally {
            IoUtils.closeQuietly(outputStream);
        }

        try (FileInputStream in = new FileInputStream(file);
             FileOutputStream out = new FileOutputStream(reserveFile)) {
            FileUtils.copy(in, out);
            out.getFD().sync();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to write reserve copy: " + reserveFile, e);
        }

        if (PackageUtils.isApkVerityEnabled()) {
            try {
                mInjector.enableFsVerity(file);
                mInjector.enableFsVerity(reserveFile);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to verity-protect roles", e);
            }
        }
    }

    private static void serializeRoles(@NonNull XmlSerializer serializer,
            @NonNull RolesState roles) throws IOException {
        serializer.startTag(null, TAG_ROLES);

        int version = roles.getVersion();
        serializer.attribute(null, ATTRIBUTE_VERSION, Integer.toString(version));
        String packagesHash = roles.getPackagesHash();
        if (packagesHash != null) {
            serializer.attribute(null, ATTRIBUTE_PACKAGES_HASH, packagesHash);
        }

        Set<String> fallbackEnabledRoles = roles.getFallbackEnabledRoles();
        for (Map.Entry<String, Set<String>> entry : roles.getRoles().entrySet()) {
            String roleName = entry.getKey();
            Set<String> roleHolders = entry.getValue();
            boolean isFallbackEnabled = fallbackEnabledRoles.contains(roleName);

            serializer.startTag(null, TAG_ROLE);
            serializer.attribute(null, ATTRIBUTE_NAME, roleName);
            serializer.attribute(null, ATTRIBUTE_FALLBACK_ENABLED,
                    Boolean.toString(isFallbackEnabled));
            serializeRoleHolders(serializer, roleHolders);
            serializer.endTag(null, TAG_ROLE);
        }

        serializer.endTag(null, TAG_ROLES);
    }

    private static void serializeRoleHolders(@NonNull XmlSerializer serializer,
            @NonNull Set<String> roleHolders) throws IOException {
        for (String roleHolder : roleHolders) {
            serializer.startTag(null, TAG_HOLDER);
            serializer.attribute(null, ATTRIBUTE_NAME, roleHolder);
            serializer.endTag(null, TAG_HOLDER);
        }
    }

    @Override
    public void deleteForUser(@NonNull UserHandle user) {
        getFile(user).delete();
        getReserveCopyFile(user).delete();
    }

    @VisibleForTesting
    @NonNull
    static File getFile(@NonNull UserHandle user) {
        ApexEnvironment apexEnvironment = ApexEnvironment.getApexEnvironment(APEX_MODULE_NAME);
        File dataDirectory = apexEnvironment.getDeviceProtectedDataDirForUser(user);
        return new File(dataDirectory, ROLES_FILE_NAME);
    }

    @NonNull
    private static File getReserveCopyFile(@NonNull UserHandle user) {
        ApexEnvironment apexEnvironment = ApexEnvironment.getApexEnvironment(APEX_MODULE_NAME);
        File dataDirectory = apexEnvironment.getDeviceProtectedDataDirForUser(user);
        return new File(dataDirectory, ROLES_RESERVE_COPY_FILE_NAME);
    }
}
