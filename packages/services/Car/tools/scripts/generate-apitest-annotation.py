#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
import sys
import os
import subprocess

# Purpose: This simple script allows one to generate @ApiTest api annotations easily from a full list of apis.
# Usage: python generate-apitest-annotation.py <keyword>
#
# Example Output:
# @ApiTest(apis = {"android.car.VehiclePropertyIds#INVALID",
#                 "android.car.VehiclePropertyIds#INFO_VIN"}

rootDir = os.getenv("ANDROID_BUILD_TOP")

if len(sys.argv) < 2:
    print("Must specify a key word to filter classes on: ex. VehiclePropertyIds")
    sys.exit(1)

filter_keyword = sys.argv[1]

# Generate class list using tool
java_cmd = "java -jar " + rootDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar --print-all-apis " \
                                    "--root-dir " + rootDir
full_api_list = subprocess.check_output(java_cmd, shell=True).decode('utf-8').strip().split("\n")

output = "@ApiTest(apis = {"
for api in full_api_list:
    if filter_keyword in api:
        tokens = api.split()
        output += "\"" + tokens[0] + "." + tokens[1] + "#" + tokens[3]

        # Trim arguments from methods
        if '(' in output:
            output = output[:output.index('(')]

        output += "\","

output = output[:-1] + "})"
print(output)


