/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.art;

/** @hide */
interface IArtd {
    // Test to see if the artd service is available.
    boolean isAlive();

    /**
     * Deletes dexopt artifacts and returns the released space, in bytes.
     *
     * Note that this method doesn't delete runtime artifacts. To delete them, call
     * `deleteRuntimeArtifacts`.
     *
     * Throws fatal errors. Logs and ignores non-fatal errors.
     */
    long deleteArtifacts(in com.android.server.art.ArtifactsPath artifactsPath);

    /**
     * Returns the dexopt status of a dex file.
     *
     * Throws fatal and non-fatal errors.
     */
    com.android.server.art.GetDexoptStatusResult getDexoptStatus(
            @utf8InCpp String dexFile, @utf8InCpp String instructionSet,
            @nullable @utf8InCpp String classLoaderContext);

    /**
     * Returns true if the profile exists and contains entries for the given dex file.
     *
     * Throws fatal and non-fatal errors.
     */
    boolean isProfileUsable(in com.android.server.art.ProfilePath profile,
            @utf8InCpp String dexFile);

    /**
     * Copies the profile and rewrites it for the given dex file. Returns `SUCCESS` and fills
     * `dst.profilePath.id` if the operation succeeds and `src` exists and contains entries that
     * match the given dex file.
     *
     * Throws fatal and non-fatal errors, except if the input is a bad profile.
     */
    com.android.server.art.CopyAndRewriteProfileResult copyAndRewriteProfile(
            in com.android.server.art.ProfilePath src,
            inout com.android.server.art.OutputProfile dst, @utf8InCpp String dexFile);

    /**
     * Similar to above. The difference is that the profile is not taken from a separate file but
     * taken from `dexFile` itself. Specifically, if `dexFile` is a zip file, the profile is taken
     * from `assets/art-profile/baseline.prof` in the zip. Returns `NO_PROFILE` if `dexFile` is not
     * a zip file or it doesn't contain a profile.
     */
    com.android.server.art.CopyAndRewriteProfileResult copyAndRewriteEmbeddedProfile(
            inout com.android.server.art.OutputProfile dst, @utf8InCpp String dexFile);

    /**
     * Moves the temporary profile to the permanent location.
     *
     * Throws fatal and non-fatal errors.
     */
    void commitTmpProfile(in com.android.server.art.ProfilePath.TmpProfilePath profile);

    /**
     * Deletes the profile. Does nothing of the profile doesn't exist.
     *
     * Operates on the whole DM file if given one.
     *
     * Throws fatal errors. Logs and ignores non-fatal errors.
     */
    void deleteProfile(in com.android.server.art.ProfilePath profile);

    /**
     * Returns the visibility of the profile.
     *
     * Operates on the whole DM file if given one.
     *
     * Throws fatal and non-fatal errors.
     */
    com.android.server.art.FileVisibility getProfileVisibility(
            in com.android.server.art.ProfilePath profile);

    /**
     * Merges profiles. Both `profiles` and `referenceProfile` are inputs, while the difference is
     * that `referenceProfile` is also used as the reference to calculate the diff. `profiles` that
     * don't exist are skipped, while `referenceProfile`, if provided, must exist. Returns true,
     * writes the merge result to `outputProfile` and fills `outputProfile.profilePath.id` and
     * `outputProfile.profilePath.tmpPath` if a merge has been performed.
     *
     * When `options.forceMerge`, `options.dumpOnly`, or `options.dumpClassesAndMethods` is set,
     * `referenceProfile` must not be set. I.e., all inputs must be provided by `profiles`. This is
     * because the merge will always happen, and hence no reference profile is needed to calculate
     * the diff.
     *
     * Throws fatal and non-fatal errors.
     */
    boolean mergeProfiles(in List<com.android.server.art.ProfilePath> profiles,
            in @nullable com.android.server.art.ProfilePath referenceProfile,
            inout com.android.server.art.OutputProfile outputProfile,
            in @utf8InCpp List<String> dexFiles,
            in com.android.server.art.MergeProfileOptions options);

    /**
     * Returns the visibility of the artifacts.
     *
     * Throws fatal and non-fatal errors.
     */
    com.android.server.art.FileVisibility getArtifactsVisibility(
            in com.android.server.art.ArtifactsPath artifactsPath);

    /**
     * Returns the visibility of the dex file.
     *
     * Throws fatal and non-fatal errors.
     */
    com.android.server.art.FileVisibility getDexFileVisibility(@utf8InCpp String dexFile);

    /**
     * Returns the visibility of the DM file.
     *
     * Throws fatal and non-fatal errors.
     */
    com.android.server.art.FileVisibility getDmFileVisibility(
            in com.android.server.art.DexMetadataPath dmFile);

    /**
     * Returns true if dexopt is needed. `dexoptTrigger` is a bit field that consists of values
     * defined in `com.android.server.art.DexoptTrigger`.
     *
     * Throws fatal and non-fatal errors.
     */
    com.android.server.art.GetDexoptNeededResult getDexoptNeeded(
            @utf8InCpp String dexFile, @utf8InCpp String instructionSet,
            @nullable @utf8InCpp String classLoaderContext, @utf8InCpp String compilerFilter,
            int dexoptTrigger);

    /**
     * Dexopts a dex file for the given instruction set.
     *
     * Throws fatal and non-fatal errors.
     */
    com.android.server.art.ArtdDexoptResult dexopt(
            in com.android.server.art.OutputArtifacts outputArtifacts,
            @utf8InCpp String dexFile, @utf8InCpp String instructionSet,
            @nullable @utf8InCpp String classLoaderContext, @utf8InCpp String compilerFilter,
            in @nullable com.android.server.art.ProfilePath profile,
            in @nullable com.android.server.art.VdexPath inputVdex,
            in @nullable com.android.server.art.DexMetadataPath dmFile,
            com.android.server.art.PriorityClass priorityClass,
            in com.android.server.art.DexoptOptions dexoptOptions,
            in com.android.server.art.IArtdCancellationSignal cancellationSignal);

    /**
     * Returns a cancellation signal which can be used to cancel {@code dexopt} calls.
     */
    com.android.server.art.IArtdCancellationSignal createCancellationSignal();

    /**
     * Deletes all files that are managed by artd, except those specified in the arguments. Returns
     * the size of the freed space, in bytes.
     *
     * For each entry in `artifactsToKeep`, all three kinds of artifacts (ODEX, VDEX, ART) are
     * kept. For each entry in `vdexFilesToKeep`, only the VDEX file will be kept. Note that VDEX
     * files included in `artifactsToKeep` don't have to be listed in `vdexFilesToKeep`.
     *
     * Throws fatal errors. Logs and ignores non-fatal errors.
     */
    long cleanup(in List<com.android.server.art.ProfilePath> profilesToKeep,
            in List<com.android.server.art.ArtifactsPath> artifactsToKeep,
            in List<com.android.server.art.VdexPath> vdexFilesToKeep,
            in List<com.android.server.art.RuntimeArtifactsPath> runtimeArtifactsToKeep);

    /**
     * Returns whether the artifacts of the primary dex files should be in the global dalvik-cache
     * directory.
     *
     * Throws fatal and non-fatal errors.
     */
    boolean isInDalvikCache(@utf8InCpp String dexFile);

    /**
     * Deletes runtime artifacts and returns the released space, in bytes.
     *
     * Throws fatal errors. Logs and ignores non-fatal errors.
     */
    long deleteRuntimeArtifacts(
            in com.android.server.art.RuntimeArtifactsPath runtimeArtifactsPath);

    /**
     * Returns the size of the dexopt artifacts, in bytes, or 0 if they don't exist or a non-fatal
     * error occurred.
     *
     * Throws fatal errors. Logs and ignores non-fatal errors.
     */
    long getArtifactsSize(in com.android.server.art.ArtifactsPath artifactsPath);

    /**
     * Returns the size of the vdex file, in bytes, or 0 if it doesn't exist or a non-fatal error
     * occurred.
     *
     * Throws fatal errors. Logs and ignores non-fatal errors.
     */
    long getVdexFileSize(in com.android.server.art.VdexPath vdexPath);

    /**
     * Returns the size of the runtime artifacts, in bytes, or 0 if they don't exist or a non-fatal
     * error occurred.
     *
     * Throws fatal errors. Logs and ignores non-fatal errors.
     */
    long getRuntimeArtifactsSize(
            in com.android.server.art.RuntimeArtifactsPath runtimeArtifactsPath);

    /**
     * Returns the size of the profile, in bytes, or 0 if it doesn't exist or a non-fatal error
     * occurred.
     *
     * Operates on the whole DM file if given one.
     *
     * Throws fatal errors. Logs and ignores non-fatal errors.
     */
    long getProfileSize(in com.android.server.art.ProfilePath profile);
}
