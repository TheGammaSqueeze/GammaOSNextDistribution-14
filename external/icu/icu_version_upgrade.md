# ICU version upgrade

Upgrade the ICU on Android to the new upstream version. The new upstream versions can be found at
https://github.com/unicode-org/icu/releases.

The below contains the steps and commands in order to upgrade the ICU version in the AOSP.

# Prerequisites
1. Install the prerequisite tools
   * See http://site.icu-project.org/repository
   * [git-lts](https://git-lfs.github.com/) has to be installed to pull binaries correctly from the github.
2. Generate a github read-access token and setup a local ~/.m2/setting.xml
   * See http://cldr.unicode.org/development/maven. This is required to download
     the prebuilt ICU to break the circular dependency between CLDR and icu.
3. Check out aosp/main
   * http://go/repo-init/aosp-main-with-phones

# Steps
1. Create a new branch in AOSP
   * The branch is a staging branch to **rebase** Android-specific patches onto the new upstream versions.
   * See detailed instruction at http://g3doc/company/teams/android-build-team/playbook/create_branch#creating-new-git-branches.
   * **Choose a branch name**
     * Create aosp/icu{version} branch, e.g. `aosp/icu67` in the `external/icu` project
       forked from `aosp/main`
     * For the external/cldr project, we don't need a new branch.
       We use the **`aosp/upstream-release-cldr** as the mirror of the upstream release branch.
       We don’t modify this branch with Android patches, but merge this branch
       into aosp/main where the Android patches are located.
2. Configure the versions and temp directory

   2a. Customize the following environment variables.
       The following example targets the ICU 71.1 and CLDR 41.0 version.
   ```shell
   export ICU_VERSION=71
   export ICU_MINOR_VERSION=1
   export CLDR_VERSION=41
   export ICU_UPGRADE_BUG=1234567890 # buganizer bug
   # Initially empty directory to store upstream source
   export UPSTREAM_CLDR_GIT=/media/user/disk/icu-git/cldr
   export UPSTREAM_ICU_GIT=/media/user/disk/icu-git/icu
   ```

   2b. Build the clean source of AOSP
   ```shell
   source build/envsetup.sh
   lunch sdk_phone_x86_64-userdebug
   m
   ```
3. Copy the CLDR sources into the `upstream-release-cldr` branch

   3a. Copy sources
    ```shell
    export CLDR_UPSTREAM_BRANCH=release-${CLDR_VERSION}

    cd ${ANDROID_BUILD_TOP}/external/cldr
    git fetch aosp upstream-release-cldr
    git branch ${CLDR_UPSTREAM_BRANCH} --track aosp/upstream-release-cldr
    git checkout ${CLDR_UPSTREAM_BRANCH}

    git clone https://github.com/unicode-org/cldr.git ${UPSTREAM_CLDR_GIT}

    git --git-dir=${UPSTREAM_CLDR_GIT}/.git --work-tree=${UPSTREAM_CLDR_GIT} fetch
    git --git-dir=${UPSTREAM_CLDR_GIT}/.git --work-tree=${UPSTREAM_CLDR_GIT} checkout ${CLDR_UPSTREAM_BRANCH}
    rm -rf *
    cp -r ${UPSTREAM_CLDR_GIT}/* .
    git clean -dfX # Remove ignored files
    git add -A
    git commit -F- <<EOF
    Copy upstream ${CLDR_UPSTREAM_BRANCH}

    Bug: ${ICU_UPGRADE_BUG}
    Test: n/a
    EOF
    # Upload this CL to upstream-release-cldr branch
    repo upload --cbr .
    ```

   3b. Merge the upstream sources with patches in `aosp/main`
    ```shell
    export CLDR_BRANCH=cldr${CLDR_VERSION}-main
    git branch ${CLDR_BRANCH} --track aosp/main
    git checkout ${CLDR_BRANCH}
    git merge ${CLDR_UPSTREAM_BRANCH} -m "
    Merge CLDR ${CLDR_VERSION} in upstream-release-cldr into aosp/main

    Bug: ${ICU_UPGRADE_BUG}
    Test: external/icu/tools/updatecldrdata.py
    "
    ```

   3c. Resolve any merge conflicts with the Android-specific patches.
      Continue creating the merge commit
    ```shell
    git merge --continue
    ```

   3d. Upload the CL to main branch
    ```shell
    repo upload --cbr .
    ```

4. Copy ICU upstream sources into external/icu
```shell
cd ${ANDROID_BUILD_TOP}/external/icu
export ICU_BRANCH=icu${ICU_VERSION}
export UPSTREAM_RELEASE_TAG=release-${ICU_VERSION}-${ICU_MINOR_VERSION}
git fetch aosp ${ICU_BRANCH}
git branch ${ICU_BRANCH} --track aosp/${ICU_BRANCH}
git checkout ${ICU_BRANCH}

# Clone the upstream CLDR repo locally to ${UPSTREAM_ICU_GIT}
test -d ${UPSTREAM_ICU_GIT} || git clone https://github.com/unicode-org/icu.git ${UPSTREAM_ICU_GIT}

git --git-dir=${UPSTREAM_ICU_GIT}/.git --work-tree=${UPSTREAM_ICU_GIT} fetch
git --git-dir=${UPSTREAM_ICU_GIT}/.git --work-tree=${UPSTREAM_ICU_GIT} checkout ${UPSTREAM_RELEASE_TAG}
find icu4j/ -type f,d ! -regex ".*/\(Android.mk\|Android.bp\|adjust_icudt_path.mk\|liblayout-jarjar-rules.txt\|.gitignore\|AndroidTest.xml\)" -delete
find icu4c/ -type f,d ! -regex ".*/\(Android.mk\|Android.bp\|.gitignore\|AndroidTest.xml\)" -delete
cp -r ${UPSTREAM_ICU_GIT}/icu4j .
cp -r ${UPSTREAM_ICU_GIT}/icu4c .
git checkout HEAD -- icu4c/.gitignore icu4j/.gitignore # Android has extra .gitignores. Use our version.
rm -r tools/cldr
cp -r ${UPSTREAM_ICU_GIT}/tools/cldr tools/cldr

git add -A
git commit -F- <<EOF
Copy ICU ${UPSTREAM_RELEASE_TAG} into aosp/${ICU_BRANCH}

Copy the files with the following commands:
find icu4j/ -type f,d ! -regex ".*/\(Android.mk\|Android.bp\|adjust_icudt_path.mk\|liblayout-jarjar-rules.txt\|.gitignore\|AndroidTest.xml\)" -delete
find icu4c/ -type f,d ! -regex ".*/\(Android.mk\|Android.bp\|.gitignore\|AndroidTest.xml\)" -delete
cp -r \${UPSTREAM_ICU_GIT}/icu4j .
cp -r \${UPSTREAM_ICU_GIT}/icu4c .
git checkout HEAD -- icu4c/.gitignore icu4j/.gitignore
rm -r tools/cldr
cp -r \${UPSTREAM_ICU_GIT}/tools/cldr tools/cldr
EOF
```

5. Apply Android-specific patches into `external/icu`

   5a. Cherry-pick the patches from the last staging branch. For example using the following query for ICU 71 patches
      * https://r.android.com/q/%2522Android+patch%2522+branch:icu71+status:merged
      * The cherry-pick command is
        ```shell
        git cherry-pick <first_patch_in_the_chain>~1..<last_patch_in_the_chain>
        ```
   5b. Cherry-pick the patches since the ICU upgrade
      * Find the patches with this query.
           https://r.android.com/q/%2522Android+patch%2522+project:platform/external/icu+status:merged+-owner:automerger+-owner:android-build-coastguard-worker%2540google.com+branch:main

6. Regenerate and commit the artifacts

   6a. Update icu source data files
   ```shell
   croot external/icu
   tools/updatecldrdata.py

   git add -A
   git commit  -F- <<EOF
   Regenerated source data files with Android CLDR patches

   Source data files updated using:
   tools/updatecldrdata.py

   Test: n/a
   EOF
   ```

   6b. Update icu binary data files
   ```shell
   tools/updateicudata.py
   git add -A
   git commit -F- <<EOF
   Regenerated binary data files with Android CLDR patches

   Binary data files updated using:
   tools/updateicudata.py

   Test: n/a
   EOF
   ```

   6c. Pin the public API surface temporarily
      * We will later expose the new APIs after submitting the version upgrade CLs.
   ```shell
   ./tools/srcgen/generate_allowlisted_public_api.sh
   git add -A
   git commit -F- <<EOF
   Pin the current API list

   Test: ./tools/srcgen/generate_allowlisted_public_api.sh
   EOF
   ```

   6d. Regenerate android_icu4j/
      * Commands
      ```shell
      tools/srcgen/generate_android_icu4j.sh

      git add -A
      git commit -F- <<EOF
      Regenerate android_icu4j/ from icu4j/

      android_icu4j files updated using:
      tools/srcgen/generate_android_icu4j.sh

      Test: n/a
      EOF
      ```
      * If any java patches are not applied perfectly, remove the .orig and
         .rej files with the following command
      ```shell
      find android_icu4j/ -name *.orig -delete
      # Please manually update and resolve conflict of the java doc in android_icu4j/
      git commit -amend
      # Regenerate the patch files
      ./tools/srcgen/javadoc_patches/create_patches.sh
      git add -A && git commit -F- <<EOF
      Regenerate java doc patches

      Test: n/a
      EOF
      ```

   6e. Re-genereate libandroidicu/ sources
   ```shell
   ./tools/icu4c_srcgen/generate_libandroidicu.py
   git add -A && git commit  -F- <<EOF
   Regenerate libandroidicu

   The command:
   ./tools/icu4c_srcgen/generate_libandroidicu.py

   Test: n/a
   EOF
   ```

   6f. Regenerate libicu/ sources
      * Commands
      ```shell
      ./tools/icu4c_srcgen/generate_ndk.py

      git add -A && git commit -a  -F- <<EOF
      Regenerate libicu.so and ICU4C CTS headers

      The command:
      ./tools/icu4c_srcgen/generate_ndk.py

      Test: n/a
      EOF
      ```
      * Review the changes in libicu/ndk_headers/unicode. Check that
         no ABI change is in the C struct or API signature.
      ```shell
      git diff HEAD~1 ./libicu/ndk_headers/
      ```
      * May need to run this to update .patch files
      ```shell
      # Manually update doxygen doc in the .h headers in libicu/ndk_headers
      ./tools/icu4c_srcgen/doc_patches/create_patches.sh
      git add -A && git commit  -F- <<EOF
      Regenerate patches in libicu headers
      EOF
      ```

   6g. [Not required for every ICU release] Increment Distro major version
      * See https://android.googlesource.com/platform/system/timezone/+/main/README.android
        for details. Usually, it’s needed only when it’s the first ICU upgrade
        in the Android dessert release.

   6h. Generate time zone files
   ```shell
   cd $ANDROID_BUILD_TOP/system/timezone
   repo start ${ICU_BRANCH} .
   ./update-tzdata.py
   git add -A
   git commit -F- <<EOF
   Regenerate data files for ICU ${ICU_VERSION} upgrade

   Binary data files updated using:
   system/timezone/update-tzdata.py

   This updates the ICU version number inside of icu_tzdata.dat but doesn't
   change the timezone data itself.

   Bug: ${ICU_UPGRADE_BUG}
   Test: n/a
   EOF

   # If only the build time in icu4c/source/data/misc/zoneinfo64.txt, this step isn't needed. 
   cd $ANDROID_BUILD_TOP/external/icu
   git add -A
   git commit -F- <<EOF
   Regenerate tz-related data files

   Data files updated using:
   system/timezone/update-tzdata.py

   Bug: ${ICU_UPGRADE_BUG}
   Test: n/a
   EOF
   ```

   6i. Update the version numbers in the METADATA
      1. Update the external/icu/README.version
      2. Update version and upgrade date in external/cldr/METADATA
      3. `git commit` the file change

   6j. Regenerate frameworks/base/libs/androidfw/LocaleDataTables.cpp
   ```shell
   croot frameworks/base
   ./tools/localedata/extract_icu_data.py $ANDROID_BUILD_TOP > libs/androidfw/LocaleDataTables.cpp
   git commit -a -F- <<EOF
   Regenerate LocaleDataTables.cpp due to ICU ${ICU_VERSION} upgrade

   The command:
   ./tools/localedata/extract_icu_data.py \$ANDROID_BUILD_TOP > libs/androidfw/LocaleDataTables.cpp

   Bug: ${ICU_UPGRADE_BUG}
   Test: atest FrameworksCoreTests:android.text.format
   EOF
   ```
7. Build and run test

   7a. Build by `m droid cts`

   7b. Run the device tests by `atest CtsIcu4cTestCases CtsIcuTestCases CtsLibcoreTestCases CtsLibcoreOjTestCases CtsBionicTestCases CtsTextTestCases minikin_tests -- --abi x86_64 # the primary ABI`

   7c. Run the host-side test by
      * ICU4J host-side test `ant check`
      * ICU4C host-side test `make CINTLTST_OPTS=-w INTLTEST_OPTS=-w check`
         (Currently, it has some failing tests. No of failures?)

   7d. If libcore/ tests are changed, verify the change
      * To verify the test change in ART MTS, run `m mts && mts-tradefed run mts-art` on Android S.
      * To verify the test change on LUCI bot, run `art/tools/run-libcore-tests.sh --mode=host`, because LUCI uses older ICU versions.
8. Upload the CLs to gerrit for code reviews from `aosp/icu${ICU_VERSION}` in `external/icu` and `aosp/upstream-release-cldr` in `external/cldr`
```shell
repo upload --cbr -o uploadvalidator~skip --no-verify .
```
9. Merge `aosp/icu*` branch to aosp/main
```shell
cd $ANDROID_BUILD_TOP/external/icu
repo start icu${ICU_VERSION}-main .
git merge --no-ff icu${ICU_VERSION} -m "
Merge branch aosp/icu${ICU_VERSION} into aosp/main

Bug: ${ICU_UPGRADE_BUG}
Test: atest CtsIcu4cTestCases CtsIcuTestCases CtsLibcoreTestCases CtsLibcoreOjTestCases CtsBionicTestCases CtsTextTestCases minikin_tests
"
```
10. Upload and submit changes from external/icu, external/cldr, libcore, frameworks/base, system/timezone

    10a. `repo upload --cbr -o uploadvalidator~skip --no-verify .`

    10b. Code review  the diff in `android_icu4j/src/main/tests/android/icu/extratest/expected_transliteration_id_list.txt`
       * If a transliteration id is removed from the list, it may introduce
          app compatibility issues if the app depends on them. However, app
         has been warned to check the availability before invoking them.
          https://developer.android.com/reference/android/icu/text/Transliterator#getAvailableIDs()
11. After submitting all the CLs to aosp/main, expose the new stable ICU4J APIs to Android SDK
```shell
rm tools/srcgen/allowlisted-public-api.txt
./tools/generate_android_icu4j.sh
# Modify Icu4jTransform.java to allowlist more classes if needed. Check the error message for the details
m update-api droid

git commit -a -F- <<EOF
Expose the new stable APIs from ICU4J ${ICU_VERSION}

According to the upstream API coverage report external/icu/icu4j/coverage-exclusion.txt,
the methods should have API coverage running in the existing CtsIcuTestCases.

Bug: ${ICU_UPGRADE_BUG}
Test: atest CtsIcuTestCases
EOF
```
12. Send email android-libcore@ and icu-team@ to announce this.
    1. Some Android teams may have dependency on the new ICU version for other features.
    2. Email template
   ```text
   Hi, Libcore and ICU team,

   ICU <version> just landed Android AOSP.
   https://android.googlesource.com/platform/external/icu/+/main/README.version
   as well as Android S (or Android 12).
   https://googleplex-android.googlesource.com/platform/external/icu/+/sc-dev/README.version

   Note:
   - Contains bug fixes / build changes with a small set of API methods added.
   - Unicode stays at version 14, and no new version has been published yet.
   ```


## Historic version of this doc
http://g3doc/third_party/icu/g3doc/update/android