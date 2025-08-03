## How to update APK files and DM files in the `res` folder

The source code of the test app is in `../app/SplitApp`. After updating the
source code, you need to update the APK files and DM files in the `res` folder.

### Updating APK files

1. Build the APK from source.

    ```
    m CtsDexMetadataSplitApp
    ```

2. Copy the APK to the `res` folder.

    ```
    cp \
      $ANDROID_BUILD_TOP/out/host/linux-x86/cts/android-cts/testcases/CtsDexMetadataSplitApp/x86_64/CtsDexMetadataSplitApp.apk \
      res/CtsDexMetadataSplitApp.apk
    ```

    Note: You may need to replace `x86_64` with a different ISA, depending on
    the product that you `lunch`-ed.

3. Repeat the steps above for the split APK (`CtsDexMetadataSplitAppFeatureA`).

### Updating DM files

The DM files contain profiles, whose headers have the dex checksums that need to
match the dex files in the APKs. Therefore, after updating the APKs, you must
update the DM files.

1. Create a binary profile from the text profile.

    ```
    profman \
      --create-profile-from=res/CtsDexMetadataSplitApp.prof.txt \
      --apk=res/CtsDexMetadataSplitApp.apk \
      --dex-location=base.apk \
      --reference-profile-file=/tmp/primary.prof \
      --output-profile-type=app
    ```

    Note: `--dex-location` must be set to `base.apk`, regardless of the actual
    APK name.

    Note: `--reference-profile-file` is the output. You can specify a different
    output location, but the filename must be `primary.prof`.

    Tip: If `profman` is not found, run `m profman`.

2. Delete the existing DM file and create the DM file from the binary profile.

    ```
    rm res/CtsDexMetadataSplitApp.dm
    zip -j res/CtsDexMetadataSplitApp.dm /tmp/primary.prof
    ```

3. Repeat the steps above for the split APK (`CtsDexMetadataSplitAppFeatureA`).
