# Copyright 2023 The Android Open Source Project
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

#!/bin/bash
#
# Setup repo directory.
REPO=$(dirname "$0")/../../..

# Head into netsim/ui directory
cd $REPO/tools/netsim/ui

# Compile protobuf to TypeScript
npm run tsproto

# Compile TypeScript to JavaScript for static file deployment
npm run build

# Run format_code
cd ..
bash $REPO/tools/netsim/scripts/format_code.sh