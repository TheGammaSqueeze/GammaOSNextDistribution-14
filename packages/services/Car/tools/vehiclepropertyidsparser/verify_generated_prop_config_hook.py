#!/usr/bin/python

# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""A script to check whether the generated prop config needs to be updated.

   This script will check whether the current generated prop config is
   consistent with VehiclePropertyIds.java if it is updated. If so, it will
   prompt user to generate a new version of prop config file.

   Usage:

   $ python verify_generated_prop_config_hook.py --android_build_top
   [ANDROID_BUILD_TOP] --preupload_files [MODIFIED_FILES]
"""
import argparse
import filecmp
import os
import sys
import subprocess
import tempfile

VEHICLE_PROPERTY_IDS_PARSER = ('packages/services/Car/tools/'
        'vehiclepropertyidsparser/prebuilt/VehiclePropertyIdsParser.jar')
CAR_LIB = 'packages/services/Car/car-lib/src'
CAR_SVC_PROPS = ('packages/services/Car/car-lib/generated-prop-config'
        '/CarSvcProps.json')
JDK_FOLDER = '/prebuilts/jdk/jdk17/linux-x86'


def createTempFile():
    f = tempfile.NamedTemporaryFile(delete=False);
    f.close();
    return f.name


def main():
    parser = argparse.ArgumentParser(
            description=('Check whether the generated prop config needs to be '
                    'updated based on VehiclePropertyIds.java'))
    parser.add_argument('--android_build_top', required=True,
            help='Path to ANDROID_BUILD_TOP')
    parser.add_argument('--preupload_files', required=True, nargs='*',
            help='modified files')
    args = parser.parse_args()

    needUpdate = False
    for preupload_file in args.preupload_files:
        if preupload_file.endswith('VehiclePropertyIds.java'):
            needUpdate = True
            break
        if preupload_file.endswith('VehiclePropertyIdsParser.jar'):
            # If parser is updated, the generated file needs to be updated.
            needUpdate = True
            break

    if not needUpdate:
        return

    vehiclePropertyIdsParser = os.path.join(args.android_build_top,
            VEHICLE_PROPERTY_IDS_PARSER)
    carLib = os.path.join(args.android_build_top, CAR_LIB)
    javaHomeDir = os.getenv("JAVA_HOME")
    if javaHomeDir is None or javaHomeDir == "":
        if os.path.isdir(args.android_build_top + JDK_FOLDER):
            javaHomeDir = args.android_build_top + JDK_FOLDER
        else:
            print('$JAVA_HOME is not set. Please use source build/envsetup.sh` in '
                    '$ANDROID_BUILD_TOP')
            sys.exit(1)

    try:
        tempCarSvcProps = createTempFile()
        javaBin = os.path.join(javaHomeDir, '/bin/java');
        subprocess.check_call([javaBin, '-jar', vehiclePropertyIdsParser,
                carLib, tempCarSvcProps])
        carSvcProps = os.path.join(args.android_build_top, CAR_SVC_PROPS);
        if not filecmp.cmp(tempCarSvcProps, carSvcProps):
            print('The generated CarSvcProps.json requires update because '
                    'VehiclePropertyIds.java is updated')
            cmd = ' '.join([javaBin, '-jar', vehiclePropertyIdsParser, carLib,
                    carSvcProps])
            print('Run \n\n' + cmd + '\n\nto update')
            sys.exit(1)
    finally:
        os.remove(tempCarSvcProps)


if __name__ == '__main__':
    main()
