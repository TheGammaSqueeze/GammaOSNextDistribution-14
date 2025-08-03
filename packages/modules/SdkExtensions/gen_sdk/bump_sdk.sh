#!/bin/bash -e
if ! [ -e build/soong ]; then
  echo "This script must be run from the top of the tree"
  exit 1
fi

commandline="$*"

sdk="$1"
if [[ -z "$sdk" ]]; then
  echo "usage: $0 <new-sdk-int> [module1,module2,..] [bug-id]"
  exit 1
fi
shift

if [[ -n $1 ]] && ! [[ $1 =~ [0-9]+ ]]; then
  IFS=',' read -r -a modules <<< "$1"
  modules_arg="--modules $1"
  shift
fi

bug="$1"

SDKEXT="packages/modules/SdkExtensions/"

TARGET_PRODUCT=aosp_arm64 build/soong/soong_ui.bash --make-mode --soong-only gen_sdk
out/soong/host/linux-x86/bin/gen_sdk \
    --database ${SDKEXT}/gen_sdk/extensions_db.textpb \
    --action new_sdk \
    --sdk "$sdk" \
    $modules_arg

message="Bump SDK Extension version to ${sdk}\n"

if [[ "$modules_arg" ]]; then
  message+="\nModules with new APIs:\n"
  for mod in "${modules[@]}"; do
    message+="  - $mod\n"
  done
fi

message+="\nGenerated with:
$ $0 $commandline

Database update generated with:
$ gen_sdk --action new_sdk --sdk $sdk
"
message+=$(test -z "$bug" || echo "\nBug: $bug")
message+="\nTest: presubmit"
message+="\nIgnore-AOSP-first: SDKs are finalized outside of AOSP"

message=$(echo -e "$message") # expand '\n' chars
git -C ${SDKEXT} commit -a -m "$message"
