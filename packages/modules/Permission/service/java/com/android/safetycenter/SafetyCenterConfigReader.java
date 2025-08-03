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

package com.android.safetycenter;

import static com.android.safetycenter.UserProfileGroup.PROFILE_TYPE_MANAGED;
import static com.android.safetycenter.UserProfileGroup.PROFILE_TYPE_PRIMARY;
import static com.android.safetycenter.UserProfileGroup.PROFILE_TYPE_PRIVATE;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import android.content.res.Resources;
import android.safetycenter.config.SafetyCenterConfig;
import android.safetycenter.config.SafetySource;
import android.safetycenter.config.SafetySourcesGroup;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.safetycenter.UserProfileGroup.ProfileType;
import com.android.safetycenter.config.ParseException;
import com.android.safetycenter.config.SafetyCenterConfigParser;
import com.android.safetycenter.resources.SafetyCenterResourcesApk;

import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * A class that reads the {@link SafetyCenterConfig} and allows overriding it for tests.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 *
 * @hide
 */
@NotThreadSafe
public final class SafetyCenterConfigReader {

    private static final String TAG = "SafetyCenterConfigReade";

    private final SafetyCenterResourcesApk mSafetyCenterResourcesApk;

    @Nullable private SafetyCenterConfigInternal mConfigInternalFromXml;

    @Nullable private SafetyCenterConfigInternal mConfigInternalOverrideForTests;

    /** Creates a {@link SafetyCenterConfigReader} from a {@link SafetyCenterResourcesApk}. */
    SafetyCenterConfigReader(SafetyCenterResourcesApk safetyCenterResourcesApk) {
        mSafetyCenterResourcesApk = safetyCenterResourcesApk;
    }

    /**
     * Loads the {@link SafetyCenterConfig} from the XML file defined in {@code
     * safety_center_config.xml}; and returns whether this was successful.
     *
     * <p>This method must be called prior to any other call to this class. This call must also be
     * successful; interacting with this class requires checking that the boolean value returned by
     * this method was {@code true}.
     */
    boolean loadConfig() {
        SafetyCenterConfig safetyCenterConfig = loadSafetyCenterConfig();
        if (safetyCenterConfig == null) {
            return false;
        }
        mConfigInternalFromXml = SafetyCenterConfigInternal.from(safetyCenterConfig);
        return true;
    }

    /**
     * Sets an override {@link SafetyCenterConfig} for tests.
     *
     * <p>When set, information provided by this class will be based on the overridden {@link
     * SafetyCenterConfig}.
     */
    void setConfigOverrideForTests(SafetyCenterConfig safetyCenterConfig) {
        mConfigInternalOverrideForTests = SafetyCenterConfigInternal.from(safetyCenterConfig);
    }

    /**
     * Clears the {@link SafetyCenterConfig} override set by {@link
     * #setConfigOverrideForTests(SafetyCenterConfig)}, if any.
     */
    void clearConfigOverrideForTests() {
        mConfigInternalOverrideForTests = null;
    }

    /** Returns the currently active {@link SafetyCenterConfig}. */
    SafetyCenterConfig getSafetyCenterConfig() {
        return getCurrentConfigInternal().getSafetyCenterConfig();
    }

    /** Returns the groups of {@link SafetySource}, in the order expected by the UI. */
    public List<SafetySourcesGroup> getSafetySourcesGroups() {
        return getCurrentConfigInternal().getSafetyCenterConfig().getSafetySourcesGroups();
    }

    /**
     * Returns the groups of {@link SafetySource}, filtering out any sources where {@link
     * SafetySources#isLoggable(SafetySource)} is {@code false} (and any resulting empty groups).
     */
    public List<SafetySourcesGroup> getLoggableSafetySourcesGroups() {
        return getCurrentConfigInternal().getLoggableSourcesGroups();
    }

    /**
     * Returns the {@link ExternalSafetySource} associated with the {@code safetySourceId}, if any.
     *
     * <p>The returned {@link SafetySource} can either be associated with the XML or overridden
     * {@link SafetyCenterConfig}; {@link #isExternalSafetySourceActive(String, String)} can be used
     * to check if it is associated with the current {@link SafetyCenterConfig}. This is to continue
     * allowing sources from the XML config to interact with SafetCenter during tests (but their
     * calls will be no-oped).
     *
     * <p>The {@code callingPackageName} can help break the tie when the source is available in both
     * the overridden config and the "real" config. Otherwise, the test config is preferred. This is
     * to support overriding "real" sources in tests while ensuring package checks continue to pass
     * for "real" sources that interact with our APIs.
     */
    @Nullable
    public ExternalSafetySource getExternalSafetySource(
            String safetySourceId, String callingPackageName) {
        SafetyCenterConfigInternal testConfig = mConfigInternalOverrideForTests;
        SafetyCenterConfigInternal xmlConfig = requireNonNull(mConfigInternalFromXml);
        if (testConfig == null) {
            // No override, access source directly.
            return xmlConfig.getExternalSafetySources().get(safetySourceId);
        }

        ExternalSafetySource externalSafetySourceInTestConfig =
                testConfig.getExternalSafetySources().get(safetySourceId);
        ExternalSafetySource externalSafetySourceInRealConfig =
                xmlConfig.getExternalSafetySources().get(safetySourceId);

        if (externalSafetySourceInTestConfig != null
                && Objects.equals(
                        externalSafetySourceInTestConfig.getSafetySource().getPackageName(),
                        callingPackageName)) {
            return externalSafetySourceInTestConfig;
        }

        if (externalSafetySourceInRealConfig != null
                && Objects.equals(
                        externalSafetySourceInRealConfig.getSafetySource().getPackageName(),
                        callingPackageName)) {
            return externalSafetySourceInRealConfig;
        }

        if (externalSafetySourceInTestConfig != null) {
            return externalSafetySourceInTestConfig;
        }

        return externalSafetySourceInRealConfig;
    }

    /**
     * Returns whether the {@code safetySourceId} is associated with an {@link ExternalSafetySource}
     * that is currently active.
     *
     * <p>The source may either be "active" or "inactive". An active source is a source that is
     * currently expected to interact with our API and may affect Safety Center status. An inactive
     * source is expected to interact with Safety Center, but is currently being silenced / no-ops
     * while an override for tests is in place.
     *
     * <p>The {@code callingPackageName} can be used to differentiate a real source being
     * overridden. It could be that a test is overriding a real source and as such the real source
     * should not be able to provide data while its override is in place.
     */
    public boolean isExternalSafetySourceActive(
            String safetySourceId, @Nullable String callingPackageName) {
        ExternalSafetySource externalSafetySourceInCurrentConfig =
                getCurrentConfigInternal().getExternalSafetySources().get(safetySourceId);
        if (externalSafetySourceInCurrentConfig == null) {
            return false;
        }
        if (callingPackageName == null) {
            return true;
        }
        return Objects.equals(
                externalSafetySourceInCurrentConfig.getSafetySource().getPackageName(),
                callingPackageName);
    }

    /**
     * Returns whether the {@code safetySourceId} is associated with an {@link ExternalSafetySource}
     * that is in the real config XML file (i.e. not being overridden).
     */
    public boolean isExternalSafetySourceFromRealConfig(String safetySourceId) {
        return requireNonNull(mConfigInternalFromXml)
                .getExternalSafetySources()
                .containsKey(safetySourceId);
    }

    /**
     * Returns the {@link Broadcast} defined in the {@link SafetyCenterConfig}, with all the sources
     * that they should handle and the profile on which they should be dispatched.
     */
    List<Broadcast> getBroadcasts() {
        return getCurrentConfigInternal().getBroadcasts();
    }

    private SafetyCenterConfigInternal getCurrentConfigInternal() {
        // We require the XML config must be loaded successfully for SafetyCenterManager APIs to
        // function, regardless of whether the config is subsequently overridden.
        requireNonNull(mConfigInternalFromXml);

        if (mConfigInternalOverrideForTests == null) {
            return mConfigInternalFromXml;
        }

        return mConfigInternalOverrideForTests;
    }

    @Nullable
    private SafetyCenterConfig loadSafetyCenterConfig() {
        InputStream in = mSafetyCenterResourcesApk.getSafetyCenterConfig();
        if (in == null) {
            Log.e(TAG, "Cannot access Safety Center config file");
            return null;
        }

        Resources resources = mSafetyCenterResourcesApk.getResources();
        try {
            SafetyCenterConfig safetyCenterConfig =
                    SafetyCenterConfigParser.parseXmlResource(in, resources);
            Log.d(TAG, "SafetyCenterConfig loaded successfully");
            return safetyCenterConfig;
        } catch (ParseException e) {
            Log.e(TAG, "Cannot parse SafetyCenterConfig", e);
            return null;
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(PrintWriter fout) {
        fout.println("XML CONFIG");
        fout.println("\t" + mConfigInternalFromXml);
        fout.println();
        fout.println("OVERRIDE CONFIG");
        fout.println("\t" + mConfigInternalOverrideForTests);
        fout.println();
    }

    /** A wrapper class around the parsed XML config. */
    private static final class SafetyCenterConfigInternal {

        private final SafetyCenterConfig mConfig;
        private final ArrayMap<String, ExternalSafetySource> mExternalSafetySources;
        private final List<SafetySourcesGroup> mLoggableSourcesGroups;
        private final List<Broadcast> mBroadcasts;

        private SafetyCenterConfigInternal(
                SafetyCenterConfig safetyCenterConfig,
                ArrayMap<String, ExternalSafetySource> externalSafetySources,
                List<SafetySourcesGroup> loggableSourcesGroups,
                List<Broadcast> broadcasts) {
            mConfig = safetyCenterConfig;
            mExternalSafetySources = externalSafetySources;
            mLoggableSourcesGroups = loggableSourcesGroups;
            mBroadcasts = broadcasts;
        }

        private SafetyCenterConfig getSafetyCenterConfig() {
            return mConfig;
        }

        private ArrayMap<String, ExternalSafetySource> getExternalSafetySources() {
            return mExternalSafetySources;
        }

        private List<SafetySourcesGroup> getLoggableSourcesGroups() {
            return mLoggableSourcesGroups;
        }

        private List<Broadcast> getBroadcasts() {
            return mBroadcasts;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SafetyCenterConfigInternal)) return false;
            SafetyCenterConfigInternal configInternal = (SafetyCenterConfigInternal) o;
            return mConfig.equals(configInternal.mConfig);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mConfig);
        }

        @Override
        public String toString() {
            return "SafetyCenterConfigInternal{"
                    + "mConfig="
                    + mConfig
                    + ", mExternalSafetySources="
                    + mExternalSafetySources
                    + ", mLoggableSourcesGroups="
                    + mLoggableSourcesGroups
                    + ", mBroadcasts="
                    + mBroadcasts
                    + '}';
        }

        private static SafetyCenterConfigInternal from(SafetyCenterConfig safetyCenterConfig) {
            return new SafetyCenterConfigInternal(
                    safetyCenterConfig,
                    extractExternalSafetySources(safetyCenterConfig),
                    extractLoggableSafetySourcesGroups(safetyCenterConfig),
                    unmodifiableList(extractBroadcasts(safetyCenterConfig)));
        }

        private static ArrayMap<String, ExternalSafetySource> extractExternalSafetySources(
                SafetyCenterConfig safetyCenterConfig) {
            ArrayMap<String, ExternalSafetySource> externalSafetySources = new ArrayMap<>();
            List<SafetySourcesGroup> safetySourcesGroups =
                    safetyCenterConfig.getSafetySourcesGroups();
            for (int i = 0; i < safetySourcesGroups.size(); i++) {
                SafetySourcesGroup safetySourcesGroup = safetySourcesGroups.get(i);

                List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
                for (int j = 0; j < safetySources.size(); j++) {
                    SafetySource safetySource = safetySources.get(j);

                    if (!SafetySources.isExternal(safetySource)) {
                        continue;
                    }

                    boolean hasEntryInStatelessGroup =
                            safetySource.getType() == SafetySource.SAFETY_SOURCE_TYPE_DYNAMIC
                                    && safetySourcesGroup.getType()
                                            == SafetySourcesGroup
                                                    .SAFETY_SOURCES_GROUP_TYPE_STATELESS;

                    externalSafetySources.put(
                            safetySource.getId(),
                            new ExternalSafetySource(safetySource, hasEntryInStatelessGroup));
                }
            }

            return externalSafetySources;
        }

        private static List<SafetySourcesGroup> extractLoggableSafetySourcesGroups(
                SafetyCenterConfig safetyCenterConfig) {
            List<SafetySourcesGroup> originalGroups = safetyCenterConfig.getSafetySourcesGroups();
            List<SafetySourcesGroup> filteredGroups = new ArrayList<>(originalGroups.size());

            for (int i = 0; i < originalGroups.size(); i++) {
                SafetySourcesGroup originalGroup = originalGroups.get(i);

                SafetySourcesGroup.Builder filteredGroupBuilder =
                        SafetySourcesGroups.copyToBuilderWithoutSources(originalGroup);
                List<SafetySource> originalSources = originalGroup.getSafetySources();
                for (int j = 0; j < originalSources.size(); j++) {
                    SafetySource source = originalSources.get(j);

                    if (SafetySources.isLoggable(source)) {
                        filteredGroupBuilder.addSafetySource(source);
                    }
                }

                SafetySourcesGroup filteredGroup = filteredGroupBuilder.build();
                if (!filteredGroup.getSafetySources().isEmpty()) {
                    filteredGroups.add(filteredGroup);
                }
            }

            return filteredGroups;
        }

        private static List<Broadcast> extractBroadcasts(SafetyCenterConfig safetyCenterConfig) {
            ArrayMap<String, Broadcast> packageNameToBroadcast = new ArrayMap<>();
            List<Broadcast> broadcasts = new ArrayList<>();
            List<SafetySourcesGroup> safetySourcesGroups =
                    safetyCenterConfig.getSafetySourcesGroups();
            for (int i = 0; i < safetySourcesGroups.size(); i++) {
                SafetySourcesGroup safetySourcesGroup = safetySourcesGroups.get(i);

                List<SafetySource> safetySources = safetySourcesGroup.getSafetySources();
                for (int j = 0; j < safetySources.size(); j++) {
                    SafetySource safetySource = safetySources.get(j);

                    if (!SafetySources.isExternal(safetySource)) {
                        continue;
                    }

                    Broadcast broadcast = packageNameToBroadcast.get(safetySource.getPackageName());
                    if (broadcast == null) {
                        broadcast = new Broadcast(safetySource.getPackageName());
                        packageNameToBroadcast.put(safetySource.getPackageName(), broadcast);
                        broadcasts.add(broadcast);
                    }
                    broadcast.mSourceIdsForProfileParent.add(safetySource.getId());
                    if (safetySource.isRefreshOnPageOpenAllowed()) {
                        broadcast.mSourceIdsForProfileParentOnPageOpen.add(safetySource.getId());
                    }
                    boolean needsManagedProfilesBroadcast =
                            SafetySources.supportsProfileType(safetySource, PROFILE_TYPE_MANAGED);
                    if (needsManagedProfilesBroadcast) {
                        broadcast.mSourceIdsForManagedProfiles.add(safetySource.getId());
                        if (safetySource.isRefreshOnPageOpenAllowed()) {
                            broadcast.mSourceIdsForManagedProfilesOnPageOpen.add(
                                    safetySource.getId());
                        }
                    }

                    // TODO(b/317378205): think about generalising these fields in Broadcast so that
                    // we are not duplicating the code - it can be a source of confusion and errors
                    // in future.
                    boolean needsPrivateProfileBroadcast =
                            SafetySources.supportsProfileType(safetySource, PROFILE_TYPE_PRIVATE);
                    if (needsPrivateProfileBroadcast) {
                        broadcast.mSourceIdsForPrivateProfile.add(safetySource.getId());
                        if (safetySource.isRefreshOnPageOpenAllowed()) {
                            broadcast.mSourceIdsForPrivateProfileOnPageOpen.add(
                                    safetySource.getId());
                        }
                    }
                }
            }

            return broadcasts;
        }
    }

    /**
     * A wrapper class around a {@link SafetySource} that is providing data externally.
     *
     * @hide
     */
    public static final class ExternalSafetySource {
        private final SafetySource mSafetySource;
        private final boolean mHasEntryInStatelessGroup;

        private ExternalSafetySource(SafetySource safetySource, boolean hasEntryInStatelessGroup) {
            mSafetySource = safetySource;
            mHasEntryInStatelessGroup = hasEntryInStatelessGroup;
        }

        /** Returns the external {@link SafetySource}. */
        public SafetySource getSafetySource() {
            return mSafetySource;
        }

        /**
         * Returns whether the external {@link SafetySource} has an entry in a stateless {@link
         * SafetySourcesGroup}.
         */
        public boolean hasEntryInStatelessGroup() {
            return mHasEntryInStatelessGroup;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExternalSafetySource)) return false;
            ExternalSafetySource that = (ExternalSafetySource) o;
            return mHasEntryInStatelessGroup == that.mHasEntryInStatelessGroup
                    && mSafetySource.equals(that.mSafetySource);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSafetySource, mHasEntryInStatelessGroup);
        }

        @Override
        public String toString() {
            return "ExternalSafetySource{"
                    + "mSafetySource="
                    + mSafetySource
                    + ", mHasEntryInStatelessGroup="
                    + mHasEntryInStatelessGroup
                    + '}';
        }
    }

    /** A class that represents a broadcast to be sent to safety sources. */
    static final class Broadcast {

        private final String mPackageName;

        private final List<String> mSourceIdsForProfileParent = new ArrayList<>();
        private final List<String> mSourceIdsForProfileParentOnPageOpen = new ArrayList<>();
        private final List<String> mSourceIdsForManagedProfiles = new ArrayList<>();
        private final List<String> mSourceIdsForManagedProfilesOnPageOpen = new ArrayList<>();
        private final List<String> mSourceIdsForPrivateProfile = new ArrayList<>();
        private final List<String> mSourceIdsForPrivateProfileOnPageOpen = new ArrayList<>();

        private Broadcast(String packageName) {
            mPackageName = packageName;
        }

        /** Returns the package name to dispatch the broadcast to. */
        String getPackageName() {
            return mPackageName;
        }

        /**
         * Returns the safety source ids associated with this broadcast in the given profile type.
         *
         * <p>If this list is empty, there are no sources to dispatch to in the given profile type.
         */
        List<String> getSourceIdsForProfileType(@ProfileType int profileType) {
            switch (profileType) {
                case PROFILE_TYPE_PRIMARY:
                    return unmodifiableList(mSourceIdsForProfileParent);
                case PROFILE_TYPE_MANAGED:
                    return unmodifiableList(mSourceIdsForManagedProfiles);
                case PROFILE_TYPE_PRIVATE:
                    return unmodifiableList(mSourceIdsForPrivateProfile);
                default:
                    Log.w(TAG, "source ids asked for unexpected profile " + profileType);
                    return emptyList();
            }
        }

        /**
         * Returns the safety source ids associated with this broadcast in the given profile type
         * that have refreshOnPageOpenAllowed set to true in the XML config.
         *
         * <p>If this list is empty, there are no sources to dispatch to in the given profile type.
         */
        List<String> getSourceIdsOnPageOpenForProfileType(@ProfileType int profileType) {
            switch (profileType) {
                case PROFILE_TYPE_PRIMARY:
                    return unmodifiableList(mSourceIdsForProfileParentOnPageOpen);
                case PROFILE_TYPE_MANAGED:
                    return unmodifiableList(mSourceIdsForManagedProfilesOnPageOpen);
                case PROFILE_TYPE_PRIVATE:
                    return unmodifiableList(mSourceIdsForPrivateProfileOnPageOpen);
                default:
                    Log.w(TAG, "source ids asked for unexpected profile " + profileType);
                    return emptyList();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Broadcast)) return false;
            Broadcast that = (Broadcast) o;
            return mPackageName.equals(that.mPackageName)
                    && mSourceIdsForProfileParent.equals(that.mSourceIdsForProfileParent)
                    && mSourceIdsForProfileParentOnPageOpen.equals(
                            that.mSourceIdsForProfileParentOnPageOpen)
                    && mSourceIdsForManagedProfiles.equals(that.mSourceIdsForManagedProfiles)
                    && mSourceIdsForManagedProfilesOnPageOpen.equals(
                            that.mSourceIdsForManagedProfilesOnPageOpen)
                    && mSourceIdsForPrivateProfile.equals(that.mSourceIdsForPrivateProfile)
                    && mSourceIdsForPrivateProfileOnPageOpen.equals(
                            that.mSourceIdsForPrivateProfileOnPageOpen);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mPackageName,
                    mSourceIdsForProfileParent,
                    mSourceIdsForProfileParentOnPageOpen,
                    mSourceIdsForManagedProfiles,
                    mSourceIdsForManagedProfilesOnPageOpen,
                    mSourceIdsForPrivateProfile,
                    mSourceIdsForPrivateProfileOnPageOpen);
        }

        @Override
        public String toString() {
            return "Broadcast{"
                    + "mPackageName='"
                    + mPackageName
                    + "', mSourceIdsForProfileParent="
                    + mSourceIdsForProfileParent
                    + ", mSourceIdsForProfileParentOnPageOpen="
                    + mSourceIdsForProfileParentOnPageOpen
                    + ", mSourceIdsForManagedProfiles="
                    + mSourceIdsForManagedProfiles
                    + ", mSourceIdsForManagedProfilesOnPageOpen="
                    + mSourceIdsForManagedProfilesOnPageOpen
                    + ", mSourceIdsForPrivateProfile="
                    + mSourceIdsForPrivateProfile
                    + ", mSourceIdsForPrivateProfileOnPageOpen="
                    + mSourceIdsForPrivateProfileOnPageOpen
                    + '}';
        }
    }
}
