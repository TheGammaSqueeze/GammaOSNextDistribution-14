#! /bin/bash

export RESTART=false

if [ -z "${ANDROID_BUILD_TOP}" ]
then
   echo "ANDROID_BUILD_TOP not set"
   exit 1
fi

function enable_android_feature() {
  FEATURE=$1

  echo -n "Checking if device has feature ${FEATURE}..."

  if adb shell pm list features | grep "feature:${FEATURE}" > /dev/null
  then
    echo "yep"
    return
  fi

  echo "not yet, let's fix that"

  FROM="${ANDROID_BUILD_TOP}/frameworks/native/data/etc/${FEATURE}.xml"
  TO="/vendor/etc/permissions/${FEATURE}.xml"

  echo -n "..Checking if ${TO} exists..."
  if adb shell ls ${TO} > /dev/null 2>&1
  then
    echo "it does"
  else
    echo "not yet"
    echo -n "....Pushing $FROM to $TO"
    if ! adb push $FROM $TO > /dev/null
    then
      echo "FAILED"
      exit 1
    else
      echo "done"
      RESTART=true
    fi
  fi

  # Feature might have been explicitly disabled, in which case it needs to be commented out

  XML_FILE=android.hardware.type.automotive.xml
  GREP_REGEX="^ *<unavailable-feature name=.*${FEATURE}.*>\$"

  for DIR in  "system" "vendor"
  do
    FULL_FILE=/${DIR}/etc/permissions/${XML_FILE}

    echo -n "..Checking if ${FEATURE} is explicitly disabled on ${FULL_FILE}..."

    if adb shell egrep "\"${GREP_REGEX}\"" ${FULL_FILE} > /dev/null
    then
      echo yep
      BKP_FILE=/tmp/${XML_FILE}.$$

      echo "....Creating backup file (${BKP_FILE})"
      adb pull ${FULL_FILE} ${BKP_FILE} > /dev/null || exit 1
      MODIFIED_FILE=/tmp/${XML_FILE}.$$.commented_out

      echo "....Commenting that line out (on ${MODIFIED_FILE})"
      # TODO: figure out how to re-use GREP_REGEX above - sed need to quote the (group) with \
      sed "s/^ *\(<unavailable-feature name=.*${FEATURE}.*>*\)\$/<\!-- \1 -->/g" < ${BKP_FILE} > ${MODIFIED_FILE}

      echo "....Replacing ${FULL_FILE}"
      adb push ${MODIFIED_FILE} ${FULL_FILE} > /dev/null
      RESTART=true
    else
      echo nope
    fi
  done
}

function enable_car_feature() {
  FEATURE=$1

  echo -n "Checking if car feature ${FEATURE} is enabled..."
  if adb shell dumpsys car_service --services CarFeatureController | grep mEnabledFeatures | grep ${FEATURE} > /dev/null
  then
    echo yep
    return
  fi
  echo nope, enabling it
  if ! adb shell cmd car_service enable-feature ${FEATURE}
  then
    echo FAILED
    exit 1
  fi
  RESTART=true
}

enable_android_feature android.software.device_admin
enable_android_feature android.software.managed_users
enable_car_feature experimental_car_user_service

if ${RESTART}
then
  echo "Restarting system (run the command again afterwards to make sure it worked)"
  adb shell stop && adb shell start
else
  echo "Good news, everyone! Everything is ready, no need to restart!"
fi
