#!/usr/bin/env bash

set -e

# Setup tmp folder
COVERAGE_TMP_FOLDER=/tmp/coverage_robolectric
rm -rf ${COVERAGE_TMP_FOLDER}
mkdir -p ${COVERAGE_TMP_FOLDER}/com/android/server/

# Run test + collect coverage
script -q ${COVERAGE_TMP_FOLDER}/atest_log \
  -c 'atest ServiceBluetoothRoboTests -- \
  --jacocoagent-path gs://tradefed_test_resources/teams/code_coverage/jacocoagent.jar \
  --coverage --coverage-toolchain JACOCO'

COVERAGE_COLLECTED=$(rg 'Test Logs have been saved in ' ${COVERAGE_TMP_FOLDER}/atest_log | sed -e 's/^.* //' -e 's/log.*$/log/')

# Link source into the tmp folder
ln -s "${ANDROID_BUILD_TOP}"/packages/modules/Bluetooth/service/src ${COVERAGE_TMP_FOLDER}/com/android/server/bluetooth

# Extract class coverage and delete unwanted class for the report
unzip "${ANDROID_HOST_OUT}"/testcases/ServiceBluetoothRoboTests/ServiceBluetoothRoboTests.jar "com/android/server/bluetooth*" -d ${COVERAGE_TMP_FOLDER}/ServiceBluetoothRobo_unzip
# shellcheck disable=SC2046
rm -rf $(find ${COVERAGE_TMP_FOLDER}/ServiceBluetoothRobo_unzip -iname "*test*")
# shellcheck disable=SC2016
rm ${COVERAGE_TMP_FOLDER}/ServiceBluetoothRobo_unzip/com/android/server/bluetooth/'BluetoothAdapterState$waitForState$3$invokeSuspend$$inlined$filter$1.class'
rm ${COVERAGE_TMP_FOLDER}/ServiceBluetoothRobo_unzip/com/android/server/bluetooth/R.class

# Generate report:
java -jar "${ANDROID_BUILD_TOP}"/out/dist/jacoco-cli.jar report "${COVERAGE_COLLECTED}"/invocation_*/inv_*/coverage_*.ec --classfiles ${COVERAGE_TMP_FOLDER}/ServiceBluetoothRobo_unzip --html ${COVERAGE_TMP_FOLDER}/coverage --name coverage.html --sourcefiles ${COVERAGE_TMP_FOLDER}

# Start python server and expose URL
printf "Url to connect to the coverage \033[32m http://%s:8000 \033[0m\n" "$(hostname)"
python3 -m http.server --directory ${COVERAGE_TMP_FOLDER}/coverage
