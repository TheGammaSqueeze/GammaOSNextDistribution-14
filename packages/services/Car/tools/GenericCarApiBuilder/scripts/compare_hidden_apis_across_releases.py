#!/usr/bin/env python3
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
import re

from tempfile import NamedTemporaryFile

# example Usage: python3 /sdd/master2/packages/services/Car/tools/GenericCarApiBuilder/scripts
# /compare_aded_removed_Apis_across_releases.py "/sdd/tm-qpr-dev" "/sdd/master2"

def strip_param_names(api):
    argGroup = re.search("\((.*)\)",api)
    if argGroup is None:
        return api
    arg = argGroup.group(0)
    new_arg = re.sub('[^ (]*?(?=\))|[^ ]*?(?=,)', "", arg)
    return re.sub("\((.*)\)", new_arg, api)

if (len(sys.argv) < 3):
    print("Need two arguments: <old repo location> <new repo location>")
    sys.exit(1)
oldDir = sys.argv[1]
newDir = sys.argv[2]


java_cmd= "java -jar " + newDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar --print-hidden-apis " \
                                    "--root-dir " + oldDir

java_cmd_2= "java -jar " + newDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar --print-hidden-apis " \
                                    "--root-dir " + newDir

java_cmd_3= "java -jar " + newDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar --print-hidden-apis-with-constr " \
                                    "--root-dir " + oldDir

java_cmd_4= "java -jar " + newDir + "/packages/services/Car/tools/GenericCarApiBuilder" \
                                    "/GenericCarApiBuilder.jar --print-all-apis-with-constr " \
                                    "--root-dir " + newDir

processes = []
cmds = [java_cmd, java_cmd_2, java_cmd_3, java_cmd_4]
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

old_hidden_apis, new_hidden_apis , old_hidden_apis_with_constr, new_hidden_apis_with_constr = results[0], results[1], results[2], results[3]
old_hidden_apis = [strip_param_names(i) for i in old_hidden_apis]
new_hidden_apis = [strip_param_names(i) for i in new_hidden_apis]
old_hidden_apis_with_constr = [strip_param_names(i) for i in old_hidden_apis_with_constr]
new_hidden_apis_with_constr = [strip_param_names(i) for i in new_hidden_apis_with_constr]
extra_new_hidden_apis = [i for i in new_hidden_apis if i not in old_hidden_apis]

tracked_hidden_apis = []
with open( newDir + "/packages/services/Car/tools/GenericCarApiBuilder/scripts/tracked_hidden_apis.txt") as f:
    tracked_hidden_apis.extend(f.read().splitlines())

print("**********Hidden APIs ADDED************")
extra_new_hidden_apis = [i for i in extra_new_hidden_apis if i not in tracked_hidden_apis]
print("\n".join(extra_new_hidden_apis))
print("\n\n")
print("**********Hidden APIs REMOVED************")
removed_hidden_apis = [i for i in old_hidden_apis_with_constr if i not in new_hidden_apis_with_constr]
print("\n".join(removed_hidden_apis))
