# vendor_gapps

**GApps for android devices**

Build standalone zips
-------------------

You can compile your GApps package with GNU make

_make distclean_
- Remove output directory

_make gapps_arm_
- compile signed flashable GApps for arm

_make gapps_arm64_
- compile signed flashable GApps for arm64

_make gapps_x86_64_
- compile signed flashable GApps for x86_64

Build inline with Android
-------------------
1. Sync this repo to `$GAPPS_PATH` where `$GAPPS_PATH` is the path to this repo
2. Include `$GAPPS_PATH/$ARCH/$ARCH-vendor.mk` where `$ARCH` is arm, arm64, or x86_64 depending on the device's architecture

Explanation of pinned blobs
-------------------
NOTE: All arch specific blobs not specifically explained here are pinned for the sake of being able to extract independently of the architecture of the source device.

AndroidMigratePrebuilt.apk
- This is from a marlin factory image to avoid crashes with the one found in the walleye factory images.

GoogleCalendarSyncAdapter.apk
- This is no longer included in Google system images and is required for syncing Google Calendar accounts with AOSP Calendar.

PrebuiltExchange3Google.apk
- This is no longer included in Google system images and is required for using Exchange accounts in the Gmail app.

PrebuiltGmsCore.apk
- This is a nodpi apk so that it works properly on all devices and updates to the appropriate one. This is generally from APKMirror and is not usually updated between major version updates.

SetupWizard.apk
- This is a non-pixel SetupWizard for better UX and less pixel-specific references.

default-permissions.xml and privapp-permissions-google.xml
- These do not always contain all the necessary permissions for apks which are not from the corresponding factory image, so they must be modified to avoid permission related crashes.

libjni_latinimegoogle.so
- This lib is no longer included in Google system images and is required for swype typing with AOSP LatinIME.

Thanks and Credits
-------------------

aleasto
- Install scripts for 11 with dedicated partitions support

cdesai
- Reminding me that /proc/meminfo is a thing

ciwrl
- Catching a few spelling errors in this file

gmrt
- Initial list for gapps

flex1911, raymanfx, deadman96385, jrior001, haggertk, arco
- Thorough testing

harryyoud
- Thorough testing and Jenkins setup

haggertk
- Suggesting CI integration of privapp-permissions

jrizzoli
- Initial build scripts and build system

luca020400
- Fixing my makefiles

LuK1337
- Setting up custom Docker image for CI, improving scripts, thorough testing

mikeioannina
- The name for MindTheGapps

aleasto, razorloves, raymanfx
- Helping maintain this repo

syphyr
- Showing me how to repack libs in PrebuiltGmsCore
