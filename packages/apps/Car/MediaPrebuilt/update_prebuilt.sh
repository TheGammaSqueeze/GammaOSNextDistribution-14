#!/bin/bash
set -eu

# Usage: update_prebuilt.sh BUILD_ID

if [ -z "${1+x}" ]
then
    echo "build id is required"
    exit 1
fi

function downloadApp {
    TARGET=car_apps_gradle-all
    # See go/fetch_artifact for details on fetch_artifact. To install it use:
    # sudo glinux-add-repo android stable && \
    # sudo apt update && \
    # sudo apt install android-fetch-artifact

    CMD="fetch_artifact --bid $1 --target $TARGET --use_oauth2"

    $CMD $2 $3
}


cd $(dirname $0)

downloadApp $1 "CarMediaApp_aaos_cert.apk" "CarMediaApp.apk"

echo "Done. Don't forget to test and commit the new artifact."
