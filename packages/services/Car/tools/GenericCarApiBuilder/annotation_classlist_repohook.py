#!/usr/bin/env python3
#  Copyright (C) 2022 The Android Open Source Project
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
import re

from tempfile import NamedTemporaryFile
from pathlib import Path

# Helper method that strips out the parameter names of methods. This will allow users to change
# parameter names for hidden apis without mistaking them as having been removed.
# [^ ]* --> Negation set on SPACE character. This wll match everything until a SPACE.
# *?(?=\)) --> This means the character ')' will not be included in the match.
# [^ (]*?(?=\)) --> This will handle the last parameter at the end of a method signature.
# It excludes matching any '(' characters when there are no parameters, i.e. method().
# [^ ]*?(?=,) --> This will handle multiple parameters delimited by commas.
def strip_param_names(api):
    # get the arguments first
    argGroup = re.search("\((.*)\)",api)
    if argGroup is None:
        return api
    arg = argGroup.group(0)
    new_arg = re.sub('[^ (]*?(?=\))|[^ ]*?(?=,)', "", arg)
    return re.sub("\((.*)\)", new_arg, api)


rootDir = os.getenv("ANDROID_BUILD_TOP")
if rootDir is None or rootDir == "":
    # env variable is not set. Then use the arg passed as Git root
    rootDir = sys.argv[1]

javaHomeDir = os.getenv("JAVA_HOME")
if javaHomeDir is None or javaHomeDir == "":
    if Path(rootDir + '/prebuilts/jdk/jdk17/linux-x86').is_dir():
        javaHomeDir = rootDir + "/prebuilts/jdk/jdk17/linux-x86"
    else:
        print("$JAVA_HOME is not set. Please use source build/envsetup.sh` in $ANDROID_BUILD_TOP")
        sys.exit(1)

# This generates a list of all classes.
# Marker is set in GenerateApi.java class and should not be changed.
marker = "Start-"
options = ["--print-classes", "--print-hidden-apis", "--print-all-apis-with-constr",
           "--print-incorrect-requires-api-usage-in-car-service",
           "--print-addedin-without-requires-api-in-car-built-in"]

java_cmd = javaHomeDir + "/bin/java -jar " + rootDir + \
           "/packages/services/Car/tools/GenericCarApiBuilder" \
           "/GenericCarApiBuilder.jar --root-dir " + rootDir + " " + " ".join(options)

all_data = subprocess.check_output(java_cmd, shell=True).decode('utf-8').strip().split("\n")
all_results = []
marker_index = []
for i in range(len(all_data)):
    if all_data[i].replace(marker, "") in options:
        marker_index.append(i)

previous_mark = 0
for mark in marker_index:
    if mark > previous_mark:
        all_results.append(all_data[previous_mark+1:mark])
        previous_mark = mark
all_results.append(all_data[previous_mark+1:])

# Update this line when adding more options
new_class_list, new_hidden_apis, all_apis = all_results[0], all_results[1], all_results[2]
incorrect_requires_api_usage_in_car_service_errors = all_results[3]
incorrect_addedin_api_usage_in_car_built_in_errors = all_results[4]
new_hidden_apis = set(new_hidden_apis)
all_apis = [strip_param_names(i) for i in all_apis]

# Read current class list
existing_car_api_classes_path = rootDir + "/packages/services/Car/tests/carservice_unit_test/" \
                                          "res/raw/car_api_classes.txt"
existing_car_built_in_classes_path = rootDir + "/packages/services/Car/tests/" \
                                               "carservice_unit_test/res/raw/" \
                                               "car_built_in_api_classes.txt"
existing_class_list = []
with open(existing_car_api_classes_path) as f:
    existing_class_list.extend(f.read().splitlines())
with open(existing_car_built_in_classes_path) as f:
    existing_class_list.extend(f.read().splitlines())

# Find the diff in both class list
extra_new_classes = [i for i in new_class_list if i not in existing_class_list]
extra_deleted_classes = [i for i in existing_class_list if i not in new_class_list]

# Print error is there is any class added or removed without changing test
error = ""
if len(extra_deleted_classes) > 0:
    error = error + "Following Classes are deleted \n" + "\n".join(extra_deleted_classes)
if len(extra_new_classes) > 0:
    error = error + "\n\nFollowing new classes are added \n" + "\n".join(extra_new_classes)

if error != "":
    print(error)
    print("\nRun following command to generate classlist for annotation test")
    print("cd $ANDROID_BUILD_TOP && m -j GenericCarApiBuilder && GenericCarApiBuilder "
          "--update-classes")
    print("\nThen run following test to make sure classes are properly annotated")
    print("atest CarServiceUnitTest:android.car.AnnotationTest")
    sys.exit(1)

# read existing hidden APIs
existing_hidden_apis_path = rootDir + "/packages/services/Car/tests/carservice_unit_test/res/raw" \
                             "/car_hidden_apis.txt"

# hidden_apis_previous_releases contains all the cumulative hidden apis added in previous releases.
# If some hidden API was added in T-QPR and removed in master, then one should be able
# to identify it. Accordingly, a new file will need to be generated for each release.
hidden_apis_previous_releases_paths = [
    "/packages/services/Car/tests/carservice_unit_test/res/raw/car_hidden_apis_release_33.3.txt",
    "/packages/services/Car/tests/carservice_unit_test/res/raw/car_hidden_apis_release_33.2.txt",
    "/packages/services/Car/tests/carservice_unit_test/res/raw/car_hidden_apis_release_33.1.txt"
]

existing_hidden_apis = set()
with open(existing_hidden_apis_path) as f:
    existing_hidden_apis = set(f.read().splitlines())

hidden_apis_previous_releases = set()
for path in hidden_apis_previous_releases_paths:
    with open(rootDir + path) as f:
        hidden_apis = set(f.read().splitlines())
        hidden_apis_previous_releases = hidden_apis_previous_releases.union(hidden_apis)

# All new_hidden_apis should be in previous_hidden_apis. There can be some entry in
# previous_hidden_apis
# which is not in new_hidden_apis. It is okay as some APIs might have been promoted.
modified_or_added_hidden_api = new_hidden_apis - existing_hidden_apis

# TODO(b/266849922): Add a pre-submit test to also check for added or modified hidden apis,
# since one could also bypass the repohook tool using --no-verify.
if len(modified_or_added_hidden_api) > 0:
    print("\nHidden APIs should not be added or modified. The following Hidden APIs were added or modified in this CL:")
    print("\n".join(modified_or_added_hidden_api))
    print(
        "\nIf adding a hidden API is necessary, please create a bug here: go/car-mainline-add-hidden-api."
        "\nYou are responsible for maintaining the hidden API, which may include future deprecation or"
        " upgrade of the hidden API. \nTo learn more about hidden API usage and removal in the Car stack please visit go/car-hidden-api-usage-removal."
        "\nTo add a hidden API, please run the following command after creating the bug:")
    print("\ncd $ANDROID_BUILD_TOP && m -j GenericCarApiBuilder && GenericCarApiBuilder "
          "--update-hidden-apis")
    print("\nPlease do not use \"no-verify\" to bypass this check. Reach out to gargmayank@ or"
          " ethanalee@ if there is any confusion or repo upload is not working for you even after running the previous command.")
    sys.exit(1)

# Hidden APIs should not be removed. Check that any of the previously hidden apis still exist in the remaining apis.
# This is different from hidden APIs that were upgraded to system or public APIs.
removed_hidden_api = []
for api in hidden_apis_previous_releases:
    if strip_param_names(api) not in all_apis:
        removed_hidden_api.append(api)

if len(removed_hidden_api) > 0:
    print("\nHidden APIs cannot be removed as the Car stack is now a mainline module. The following Hidden APIs were removed:")
    print("\n".join(removed_hidden_api))
    print("\nPlease do not use \"no-verify\" to bypass this check. "
          "To learn more about hidden API deprecation and removal visit go/car-hidden-api-usage-removal. "
          "\nReach out to gargmayank@ or ethanalee@ if you have any questions or concerns regarding "
          "removing hidden APIs.")
    sys.exit(1)

# If a hidden API was upgraded to system or public API, the car_hidden_apis.txt should be updated to
# reflect its upgrade.
# Prior to this check, added and removed hidden APIs have been checked. At this point, the set
# difference between existing_hidden_apis and new_hidden_apis indicates that some hidden APIs have
# been upgraded."
upgraded_hidden_apis = existing_hidden_apis - new_hidden_apis
if len(upgraded_hidden_apis) > 0:
    print("\nThe following hidden APIs were upgraded to either system or public APIs.")
    print("\n".join(upgraded_hidden_apis))
    print("\nPlease run the following command to update: ")
    print("\ncd $ANDROID_BUILD_TOP && m -j GenericCarApiBuilder && GenericCarApiBuilder "
          "--update-hidden-apis")
    print("\nReach out to gargmayank@ or ethanalee@ if you have any questions or concerns regarding "
          "upgrading hidden APIs. Visit go/upgrade-hidden-api for more info.")
    print("\n\n")
    sys.exit(1)

# Check if Car Service is throwing platform mismatch exception
folder = rootDir + "/packages/services/Car/service/"
files = [str(v) for v in list(Path(folder).rglob("*.java"))]
errors = []
for f in files:
    with open(f, "r") as tmp_f:
        lines = tmp_f.readlines()
        for i in range(len(lines)):
            if "assertPlatformVersionAtLeast" in lines[i]:
                errors.append("line: " + str(i) + ". assertPlatformVersionAtLeast used.")
            if "PlatformVersionMismatchException" in lines[i]:
                errors.append("line: " + str(i) + ". PlatformVersionMismatchException used.")
if len(errors) > 0:
    print("\nassertPlatformVersionAtLeast or PlatformVersionMismatchException should not be used in"
          " car service. see go/car-mainline-version-assertion")
    print("\n".join(errors))
    sys.exit(1)

if len(incorrect_requires_api_usage_in_car_service_errors) > 0:
    print("\nOnly non-public classes and methods can have RequiresApi annotation. Following public "
          "methods/classes also have requiresAPI annotation which is not allowed. See "
          "go/car-api-version-annotation#using-requiresapi-for-version-check")
    print("\n".join(incorrect_requires_api_usage_in_car_service_errors))
    sys.exit(1)

if len(incorrect_addedin_api_usage_in_car_built_in_errors) > 0:
    print("\nFollowing APIs are missing RequiresAPI annotations. See "
          "go/car-api-version-annotation#using-requiresapi-for-version-check")
    print("\n".join(incorrect_addedin_api_usage_in_car_built_in_errors))
    sys.exit(1)
