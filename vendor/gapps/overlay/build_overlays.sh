#!/bin/bash

set -e

GARCH=$1
GOUT=$2

APKTOOL=$GAPPS_TOP/build/apktool/apktool_2.8.1.jar
APKSIGNER=$GAPPS_TOP/build/sign/apksigner.jar

APK_KEY_PK8=$GAPPS_TOP/build/sign/testkey.pk8
APK_KEY_PEM=$GAPPS_TOP/build/sign/testkey.x509.pem

SCRIPT_DIR=$(dirname "$0")
cd "$SCRIPT_DIR"

OVERLAYS=$(for dir in $(ls -d */); do echo ${dir%%/}; done)

for OVERLAY in $OVERLAYS; do
    PARTITION=$(grep -Eo "\w+_specific: true" $OVERLAY/Android.bp | sed "s/_specific.*$//")
    OVERLAY_TARGET_DIR="$GOUT/system/$PARTITION/overlay/"
    OVERLAY_TARGET="$OVERLAY_TARGET_DIR/$OVERLAY.apk"
    test -d $OVERLAY_TARGET_DIR || mkdir -p $OVERLAY_TARGET_DIR
    java -Xmx2048m -jar $APKTOOL b $OVERLAY --use-aapt2 >> $GLOG 2>&1
    touch -amt 200901010000.00 \
        $OVERLAY/build/apk/resources.arsc \
        $OVERLAY/build/apk/AndroidManifest.xml
    zip -j $OVERLAY_TARGET -n .arsc \
        $OVERLAY/build/apk/resources.arsc \
        $OVERLAY/build/apk/AndroidManifest.xml >> $GLOG 2>&1
    java -Xmx2048m -jar $APKSIGNER sign --key $APK_KEY_PK8 --cert $APK_KEY_PEM $OVERLAY_TARGET
    rm $OVERLAY_TARGET.idsig
done
