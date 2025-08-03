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

# The following script will parse two source with different car versions and determine if the
# correct @ApiRequirements or @AddedIn min car version was used.
# Example Usage:
# python3 /sdd/master2/packages/services/Car/tools/GenericCarApiBuilder/scripts/check_car_version.py
# "/sdd/tm-qpr-dev" "/sdd/master" UPSIDE_DOWN_CAKE_0
# The third argument specifies the expected car version annotation.
# It will also determine if the minimum car version is missing.

import sys
import os
import subprocess
import re

from tempfile import NamedTemporaryFile

exempt_apis = [
    "toString",
    "equals",
    "hashCode",
    "finalize"
]

def strip_param_names(api):
    argGroup = re.search("\((.*)\)",api)
    if argGroup is None:
        return api
    arg = argGroup.group(0)
    new_arg = re.sub('[^ (]*?(?=\))|[^ ]*?(?=,)', "", arg)
    return re.sub("\((.*)\)", new_arg, api)

# The min car version is separated by a `|` in the string.
def get_version(api):
    return version in api.split("|")[1]

if (len(sys.argv) < 4):
    print("Need three arguments: <old repo location> <new repo location> <car version>")
    sys.exit(1)
oldDir = sys.argv[1]
newDir = sys.argv[2]
version = sys.argv[3]


java_cmd= "java -jar " + newDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                  "/GenericCarApiBuilder.jar " \
                                  "--print-all-apis-with-car-version " \
                                  "--root-dir " + oldDir

java_cmd_2= "java -jar " + newDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar " \
                                    "--print-all-apis-with-car-version " \
                                    "--root-dir " + newDir

processes = []
cmds = [java_cmd, java_cmd_2]
for cmd in cmds:
    f = NamedTemporaryFile()
    p = subprocess.Popen(cmd, shell=True, stdout=f)
    processes.append((p, f))

results = []
for p, f in processes:
    p.wait()
    f.seek(0)
    results.append(f.read().decode('utf-8').strip().split("\n"))
    f.close()

old_apis, new_apis = results[0], results[1]
old_apis = [strip_param_names(i) for i in old_apis]
new_apis = [strip_param_names(i) for i in new_apis]

print("**********THE FOLLOWING APIS DO NOT HAVE " + version + " VERSION ANNOTATION************")
new_apis = [i for i in new_apis if i not in old_apis and not get_version(i) and not any(exempt in i for exempt in exempt_apis)]
print("\n".join(new_apis))
print("\n\n")
