#! /bin/bash

set -e

printusage() {
    echo "Usage: ./download-libs.sh <robolectric-version> " >&2
    echo "    -f <old-directory-to-copy-from>" >&2
    exit 1
}

oldVersion=""
roboVersion="$1"
shift

while getopts "f:h" opt; do
    case "$opt" in
        f)
            oldVersion="$OPTARG"
            ;;
        h)
            printusage
            ;;
    esac
done

if [[ -z $roboVersion ]] || [[ -z $oldVersion ]]; then
    printusage
fi

mkdir -p ../"$roboVersion"/PREBUILT
# Copy the scripts into the versioned directory for record
cp download-libs.sh ../"$roboVersion"/PREBUILT/download-libs.sh
cp download-libs.gradle ../"$roboVersion"/PREBUILT/download-libs.gradle

cd ../"$roboVersion"
gradle -b PREBUILT/download-libs.gradle \
    -ProbolectricVersion="$roboVersion" \
    -PshadowsVersion="$roboVersion" \
    -PbuildDir="`pwd`"

COPY_FROM_OLD_VERSION=(
    "java-timeout"
    "list_failed.sh"
    "report-internal.mk"
    "robotest-internal.mk"
    "robotest.sh"
    "run_robotests.mk"
    "wrapper.sh"
    "wrapper_test.sh"
)

JARS=$(ls -1 lib/*.jar | sed 's/^.*$/        "&",/')

for file in "${COPY_FROM_OLD_VERSION[@]}"; do
    cp -n ../"$oldVersion"/$file ./$file
done

cat <<EOF > Android.bp
package {
    default_applicable_licenses: ["Android-Apache-2.0"],
}

java_import {
    name: "platform-robolectric-${roboVersion}-prebuilt",
    sdk_version: "current",
    jars: [
${JARS}
    ],
    exclude_files: [
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
    ],
}

EOF

set +e
