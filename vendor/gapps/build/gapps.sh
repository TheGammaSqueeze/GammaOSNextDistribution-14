#!/bin/bash
# (c) Joey Rizzoli, 2015
# (c) Paul Keith, 2017
# Released under GPL v2 License

##
# var
#
DATE=$(date -u +%Y%m%d_%H%M%S)
export GAPPS_TOP=$(realpath .)
ANDROIDV=14.0.0
SDKV=34
GARCH=$1
CPUARCH=$GARCH
[ ! -z "$2" ] && CPUARCH=$2
OUT=$GAPPS_TOP/out
BUILD=$GAPPS_TOP/build
METAINF=$BUILD/meta
COMMON=$GAPPS_TOP/common/proprietary
export GLOG=$GAPPS_TOP/gapps_log
ADDOND=$GAPPS_TOP/addond.sh

SIGNAPK=$GAPPS_TOP/build/sign/signapk.jar
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$GAPPS_TOP/build/sign

ZIP_KEY_PK8=$GAPPS_TOP/build/sign/testkey.pk8
ZIP_KEY_PEM=$GAPPS_TOP/build/sign/testkey.x509.pem

##
# functions
#
function clean() {
    echo "Cleaning up..."
    rm -r $OUT/$GARCH
    rm /tmp/$BUILDZIP
    return $?
}

function failed() {
    echo "Build failed, check $GLOG"
    exit 1
}

function create() {
    test -f $GLOG && rm -f $GLOG
    echo "Starting GApps compilation" > $GLOG
    echo "ARCH= $GARCH" >> $GLOG
    echo "OS= $(uname -s -r)" >> $GLOG
    echo "NAME= $(whoami) at $(uname -n)" >> $GLOG
    PREBUILT=$GAPPS_TOP/$GARCH/proprietary
    test -d $OUT || mkdir $OUT;
    test -d $OUT/$GARCH || mkdir -p $OUT/$GARCH
    test -d $OUT/$GARCH/system || mkdir -p $OUT/$GARCH/system
    echo "Build directories are now ready" >> $GLOG
    echo "Compiling RROs"
    $GAPPS_TOP/overlay/build_overlays.sh $GARCH $OUT/$GARCH
    echo "Getting prebuilts..."
    echo "Copying stuff" >> $GLOG
    cp $GAPPS_TOP/toybox-$GARCH $OUT/$GARCH/toybox >> $GLOG
    cp -r $PREBUILT/* $OUT/$GARCH/system >> $GLOG
    cp -r $COMMON/* $OUT/$GARCH/system >> $GLOG
    echo "Generating addon.d script" >> $GLOG
    test -d $OUT/$GARCH/system/addon.d || mkdir -p $OUT/$GARCH/system/addon.d
    cp -f addond_head $OUT/$GARCH/system/addon.d
    cp -f addond_tail $OUT/$GARCH/system/addon.d
    echo "Writing build props..."
    echo "arch=$CPUARCH" > $OUT/$GARCH/build.prop
    echo "version=$SDKV" >> $OUT/$GARCH/build.prop
    echo "version_nice=$ANDROIDV" >> $OUT/$GARCH/build.prop
}

function zipit() {
    BUILDZIP=MindTheGapps-$ANDROIDV-$GARCH-$DATE.zip
    echo "Importing installation scripts..."
    test -d $OUT/$GARCH/META-INF || mkdir $OUT/$GARCH/META-INF;
    cp -r $METAINF/* $OUT/$GARCH/META-INF/ && echo "Meta copied" >> $GLOG
    echo "Creating package..."
    cd $OUT/$GARCH
    zip -r /tmp/$BUILDZIP . >> $GLOG
    rm -rf $OUT/tmp >> $GLOG
    cd $GAPPS_TOP
    if [ -f /tmp/$BUILDZIP ]; then
        echo "Signing zip..."
        java -Xmx2048m -jar $SIGNAPK -w $ZIP_KEY_PEM $ZIP_KEY_PK8 /tmp/$BUILDZIP $OUT/$BUILDZIP >> $GLOG
    else
        echo "Couldn't zip files!"
        echo "Couldn't find unsigned zip file, aborting" >> $GLOG
        return 1
    fi
}

function getsha256() {
    if [ -x $(which sha256sum) ]; then
        echo "sha256sum is installed, getting sha256..." >> $GLOG
        echo "Getting sha256sum..."
        GSHA256=$(sha256sum $OUT/$BUILDZIP)
        echo -e "$GSHA256" > $OUT/$BUILDZIP.sha256sum
        echo "sha256 exported at $OUT/$BUILDZIP.sha256sum"
        return 0
    else
        echo "sha256sum is not installed, aborting" >> $GLOG
        return 1
    fi
}

##
# main
#
if [ -x $(which realpath) ]; then
    echo "Realpath found!" >> $GLOG
else
    GAPPS_TOP=$(cd . && pwd) # some darwin love
    echo "No realpath found!" >> $GLOG
fi

for func in create zipit getsha256 clean; do
    $func
    ret=$?
    if [ "$ret" == 0 ]; then
        continue
    else
        failed
    fi
done

echo "Done!" >> $GLOG
echo "Build completed: $GSHA256"
exit 0
