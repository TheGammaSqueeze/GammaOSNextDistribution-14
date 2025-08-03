#!/bin/bash
set -eu

# Usage: update_prebuilts.sh BUILD_ID {all|car-apps-common|car-assist-lib|car-media-common|car-messaging-models|car-uxr-client-lib|car-telephony-common|car-ui-lib} [TARGET]

if [ -z "${1+x}" ]
then
    echo "build id is required"
    exit 1
fi

function downloadApp {
    echo "Build: " $1 " Target: " $4
    # See go/fetch_artifact for details on fetch_artifact. To install it use:
    # sudo glinux-add-repo android stable && \
    # sudo apt update && \
    # sudo apt install android-fetch-artifact

    CMD="fetch_artifact --bid $1 --target $4 --use_oauth2"

    $CMD $2 $3
}

LIB_TARGET=${2-all}
BUILD_TARGET=${3-car_apps_gradle-all}

cd $(dirname $0)

shopt -s nocasematch
case $LIB_TARGET in
    all)
        echo "Downloading all the libs"
        echo "Downloading car-apps-common"
        downloadApp $1 "car-apps-common.aar" "car-apps-common.aar" $BUILD_TARGET
        echo "Downloading car-assist-lib"
        downloadApp $1 "car-assist-lib.aar" "car-assist-lib.aar" $BUILD_TARGET
        echo "Downloading car-media-common"
        downloadApp $1 "car-media-common.aar" "car-media-common.aar" $BUILD_TARGET
        echo "Downloading car-media-extensions"
        downloadApp $1 "car-media-extensions.aar" "car-media-extensions.aar" $BUILD_TARGET
	echo "Downloading car-messaging-models"
        downloadApp $1 "car-messaging-models.aar" "car-messaging-models.aar" $BUILD_TARGET
        echo "Downloading car-telephony-common"
        downloadApp $1 "car-telephony-common.aar" "car-telephony-common.aar" $BUILD_TARGET
        echo "Downloading car-ui-lib"
        downloadApp $1 "car-ui-lib.aar" "car-ui-lib.aar" "car_apps-user"
        echo "Downloading car-ui-lib-oem-apis"
        downloadApp $1 "car-ui-lib-oem-apis-source.jar" "car-ui-lib-oem-apis.jar" "car_apps-user"
        echo "Downloading car-uxr-client-lib"
        downloadApp $1 "car-uxr-client-lib.aar" "car-uxr-client-lib.aar" $BUILD_TARGET
        echo "Downloading car-ui-lib-testing-support.aar"
        downloadApp $1 "car-ui-lib-testing-support.aar" "car-ui-lib-testing-support.aar" "car_apps-user"
        echo "Downloading car-ui-lib-no-overlayable.aar"
        downloadApp $1 "car-ui-lib-no-overlayable.aar" "car-ui-lib-no-overlayable.aar" "car_apps-user"
        echo "Downloading oem-token-lib"
        downloadApp $1 "oem-token-lib.aar" "oem-token-lib.aar" "car_apps-user"
        echo "Downloading oem-token-sharedlib"
        downloadApp $1 "token-sharedlibrary.aar" "token-sharedlibrary.aar" "car_apps-user"
        echo "Downloading token-sharedlibrary-res"
        downloadApp $1 "token-sharedlibrary-res.zip" "token-sharedlibrary-res.zip" "car_apps-user"
        ;;
    car-apps-common)
        echo "Downloading car-apps-common"
        downloadApp $1 "car-apps-common.aar" "car-apps-common.aar" $BUILD_TARGET
        ;;
    car-assist-lib)
        echo "Downloading car-assist-lib"
        downloadApp $1 "car-assist-lib.aar" "car-assist-lib.aar" $BUILD_TARGET
        ;;
    car-media-common)
        echo "Downloading car-media-common"
        downloadApp $1 "car-media-common.aar" "car-media-common.aar" $BUILD_TARGET
        echo "Downloading car-media-extensions"
        downloadApp $1 "car-media-extensions.aar" "car-media-extensions.aar" $BUILD_TARGET
        ;;
    car-messaging-models)
        echo "Downloading car-messaging-models"
        downloadApp $1 "car-messaging-models.aar" "car-messaging-models.aar" $BUILD_TARGET
        ;;
    car-uxr-client-lib)
        echo "Downloading car-uxr-client-lib"
        downloadApp $1 "car-uxr-client-lib.aar" "car-uxr-client-lib.aar" $BUILD_TARGET
        ;;
    car-telephony-common)
        echo "Downloading car-telephony-common"
        downloadApp $1 "car-telephony-common.aar" "car-telephony-common.aar" $BUILD_TARGET
        ;;
    car-ui-lib)
        echo "Downloading car-ui-lib"
        downloadApp $1 "car-ui-lib.aar" "car-ui-lib.aar" "car_apps-user"
        echo "Downloading car-ui-lib-oem-apis"
        # TODO(b/258809109): car-ui-lib-oem-apis-jar.jar doesn't have a gradle target yet.
        downloadApp $1 "car-ui-lib-oem-apis-source.jar" "car-ui-lib-oem-apis.jar" "car_apps-user"
        # TODO(b/258809109): car-ui-lib-testing-support doesn't have a gradle target yet.
        echo "Downloading car-ui-lib-testing-support.aar"
        downloadApp $1 "car-ui-lib-testing-support.aar" "car-ui-lib-testing-support.aar" "car_apps-user"
        # TODO(b/258809109): car-ui-lib-no-overlayable doesn't have a gradle target yet.
        echo "Downloading car-ui-lib-no-overlayable.aar"
        downloadApp $1 "car-ui-lib-no-overlayable.aar" "car-ui-lib-no-overlayable.aar" "car_apps-user"
        ;;
    oem-token-lib)
        echo "Downloading oem-token-lib"
        downloadApp $1 "oem-token-lib.aar" "oem-token-lib.aar" "car_apps-user"
        echo "Downloading oem-token-sharedlib"
        downloadApp $1 "token-sharedlibrary.aar" "token-sharedlibrary.aar" "car_apps-user"
        echo "Downloading token-sharedlibrary-res"
        downloadApp $1 "token-sharedlibrary-res.zip" "token-sharedlibrary-res.zip" "car_apps-user"
        ;;
    *)
        echo "Invalid application option {all|car-apps-common|car-assist-lib|car-media-common|car-messaging-models|car-uxr-client-lib|car-telephony-common|car-ui-lib|oem-token-lib}"
        ;;
esac
shopt -u nocasematch

echo "Done. Don't forget to test and commit the new artifacts."
