#!/bin/bash

if [[ $# -ne 3 ]]
then
    echo "Diff AAOS sysui & notifications projects between two revesions"
    echo "Usage $0 <path_to_android_checkout> <diff_start_hash_or_label> <diff_end_hash_or_label>"
    echo "Example 1: $0 ~/Android/android11-release remotes/aosp/android10-qpr3-release remotes/aosp/android11-release"
    echo "Example 2: $0 ~/Android/master fbec38f2ae804519be36b146c4c5a3772ad0327a 5079993610225086d0c059cb693881d83ed64af9"
    exit 1
fi

# i.e. something like ~/Android/android11-release
ANDROID_CHECKOUT=$1
# i.e. something like remotes/aosp/android10-qpr3-release
# ref: branches https://android.googlesource.com/platform/frameworks/base/+refs
LABEL_START=$2
# i.e. something like remotes/aosp/android11-release
LABEL_END=$3

git_stat_method () {
    FILE_LIST=`eval $1 | cat`
    if [[ -z $FILE_LIST ]]
    then
        echo "Query does not return any files: \"$1\""
    else
        echo "Running:"
        echo "git diff --stat $LABEL_START $LABEL_END -- \`$1\`"
        eval "git diff --stat $LABEL_START $LABEL_END -- \`$1\`" | cat
    fi
}

no_sysui_ext_project() {
    echo "sysui is not overwritten as no project at: $SYSUI_EXT_PROJECT"
    echo "If there is, change SYSUI_EXT_PROJECT in the script."
}

# These values should be updated as necessary.
SYSUI_BASE_PROJECT=frameworks/base
SYSUI_BASE_PATTERN=packages/CarSystemUI
SYSUI_EXT_PROJECT=vendor/auto/embedded
SYSUI_EXT_PATTERN=AAECarSystemUI
NOTIF_PROJECT=packages/apps/Car/Notification
NOTIF_PATTERN=car/notification

OVERLAY_PROJECT=packages/services/Car
OVERLAY_PATTERN=res/values
RRO_PATTERN=/rro/
SYSUI_RAW_PATTERN=SystemUI

echo "Android Checkout:" $ANDROID_CHECKOUT
echo "SysUI Base Pattern:" $SYSUI_BASE_PATTERN
echo "SysUI Base Project:" $SYSUI_BASE_PROJECT
echo "SysUI Ext Pattern:" $SYSUI_EXT_PATTERN
echo "SysUI Ext Project:" $SYSUI_EXT_PROJECT
echo "Notif Pattern:" $NOTIF_PATTERN
echo "Notif Project:" $NOTIF_PROJECT
echo "Overlay Pattern:" $OVERLAY_PATTERN
echo "RRO Pattern:" $RRO_PATTERN
echo "Overlay Project:" $OVERLAY_PROJECT
echo "SysUI Raw Pattern:" $SYSUI_RAW_PATTERN

echo ""
echo ""

echo "----------------------------------------------------------"
echo "Customized Code"
echo "----------------------------------------------------------"
echo "*****************************"
echo "Base SysUI"
echo "*****************************"
cd $ANDROID_CHECKOUT
cd $SYSUI_BASE_PROJECT
echo "Working directory:" `pwd`

git_stat_method "find . -name '*.java' | egrep -v 'test' | egrep '$SYSUI_BASE_PATTERN'"

echo ""

echo "*****************************"
echo "Ext SysUI"
echo "*****************************"
if [[ -e $SYSUI_EXT_PROJECT ]]; then
    cd $ANDROID_CHECKOUT
    cd $SYSUI_EXT_PROJECT
    echo "Working directory:" `pwd`

    git_stat_method "find . -name '*.java' | egrep -v 'test' | egrep '$SYSUI_EXT_PATTERN'"
else
    no_sysui_ext_project
fi

echo ""

echo "*****************************"
echo "Notification"
echo "*****************************"
cd $ANDROID_CHECKOUT
cd $NOTIF_PROJECT
echo "Working directory:" `pwd`

git_stat_method "find . -name '*.java' | egrep -v 'test' | egrep '$NOTIF_PATTERN'"

echo ""
echo ""
echo ""

echo "----------------------------------------------------------"
echo "Customized Configs"
echo "----------------------------------------------------------"
echo "*****************************"
echo "Resource Overlays SysUI"
echo "*****************************"
cd $ANDROID_CHECKOUT
cd $OVERLAY_PROJECT
echo "Working directory:" `pwd`

git_stat_method "find . -name '*.xml' | egrep -v '$RRO_PATTERN' | egrep '$OVERLAY_PATTERN' | egrep '$SYSUI_RAW_PATTERN'"

echo ""

echo "*****************************"
echo "RROs SysUI"
echo "*****************************"
cd $ANDROID_CHECKOUT
cd $OVERLAY_PROJECT
echo "Working directory:" `pwd`

git_stat_method "find . -name '*.xml' | egrep '$RRO_PATTERN' | egrep '$SYSUI_RAW_PATTERN'"

echo ""

echo "*****************************"
echo "Resource Overlays Notification"
echo "*****************************"
cd $ANDROID_CHECKOUT
cd $OVERLAY_PROJECT
echo "Working directory:" `pwd`

git_stat_method "find . -name '*.xml' | egrep -v '$RRO_PATTERN' | egrep '$OVERLAY_PATTERN' | egrep '$NOTIF_PATTERN'"

echo ""

echo "*****************************"
echo "RROs Notification"
echo "*****************************"
cd $ANDROID_CHECKOUT
cd $OVERLAY_PROJECT
echo "Working directory:" `pwd`

git_stat_method "find . -name '*.xml' | egrep '$RRO_PATTERN' | egrep '$NOTIF_PATTERN'"

echo ""
echo ""
echo ""

echo "----------------------------------------------------------"
echo "Customized Tests"
echo "----------------------------------------------------------"
echo "*****************************"
echo "Base SysUI"
echo "*****************************"
cd $ANDROID_CHECKOUT
cd $SYSUI_BASE_PROJECT
echo "Working directory:" `pwd`

git_stat_method "find . -name '*.java' | egrep '*test*' | egrep '$SYSUI_BASE_PATTERN'"

echo ""

echo "*****************************"
echo "Ext SysUI"
echo "*****************************"
if [[ -e $SYSUI_EXT_PROJECT ]]; then
    cd $ANDROID_CHECKOUT
    cd $SYSUI_EXT_PROJECT
    echo "Working directory:" `pwd`

    git_stat_method "find . -name '*.java' | egrep '*test*' | egrep '$SYSUI_EXT_PATTERN'"
else
    no_sysui_ext_project
fi

echo ""

echo "*****************************"
echo "Notification"
echo "*****************************"
cd $ANDROID_CHECKOUT
cd $NOTIF_PROJECT
echo "Working directory:" `pwd`

git_stat_method "find . -name '*.java' | egrep '*test*' | egrep '$NOTIF_PATTERN'"
