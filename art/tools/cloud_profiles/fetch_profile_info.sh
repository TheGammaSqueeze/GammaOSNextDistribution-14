#!/bin/bash
# Running this script requires "gcert" login.
#
# This script is used to query the PROD database to fetch the info about the ART
# cloud profiles for a given package name and version code (optional)
# For example, `bash fetch_profile.sh com.abc`
# or `bash fetch_profile com.abc 123`
# The output if any will contain a "blob_key" for each profile info and
# it could be used to download the content of the profile via
# "fetch_blob_key.sh".
if [[ "$#" == 1 ]];
then
  PKG=$1
  echo "fetching for $PKG..."
  span sql /span/global/play-gateway:art "select PackageName, VersionCode, DerivedId, SplitName, DeviceType, SdkVersion, ApkSignType, PlayArtProfilePublishStatus.profile_dex_metadata.blob_key FROM AggregatedPlayArtProfileV3 WHERE PlayArtProfilePublishStatus IS NOT NULL AND PackageName='$1';";
  echo "$PKG"
elif [[ "$#" == 2 ]];
then
  PKG=$1
  VERSION=$2
  echo "fetching for $PKG $VERSION..."
  span sql /span/global/play-gateway:art "select PackageName, VersionCode, DerivedId, SplitName, DeviceType, SdkVersion, ApkSignType, PlayArtProfilePublishStatus.profile_dex_metadata.blob_key FROM AggregatedPlayArtProfileV3 WHERE PlayArtProfilePublishStatus IS NOT NULL AND PackageName='$1' and VersionCode=$2;";
else
  echo "Illegal number of parameters. It should be one or two."
fi
