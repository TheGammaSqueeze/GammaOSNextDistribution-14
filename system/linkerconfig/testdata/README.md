# Run diff with golden output

Simply running `./rundiff.sh` works. When the changes are found, you can accept the changes by running the following command.

    ./rundiff.sh --update

# How to prepare golden input

`testdata/root` is pseudo-root dir for `linkerconfig`.

    testdata/root/
        system/etc/
            sanitizer.libraries.txt
            vndkcorevariant.libraries.txt
        system/apex/
            <apex>/
                apex_manifest.json   # copied from intermediate apex_manifest_full.json
                bin/.keep            # indicates to generate ld.config.txt for this apex
