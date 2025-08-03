#
# Copyright (C) 2021 The Android Open Source Project
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
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cp $SCRIPT_DIR/../../../../../packages/modules/Bluetooth/framework/api/current.txt $SCRIPT_DIR/src/processor/res/apis/bluetooth-current.txt
cp $SCRIPT_DIR/../../../../../packages/modules/Wifi/framework/api/current.txt $SCRIPT_DIR/src/processor/res/apis/wifi-current.txt
cp $SCRIPT_DIR/../../../../../frameworks/base/core/api/current.txt $SCRIPT_DIR/src/processor/res/apis/current.txt
cp $SCRIPT_DIR/../../../../../frameworks/base/core/api/test-current.txt $SCRIPT_DIR/src/processor/res/apis/test-current.txt
cp $SCRIPT_DIR/../../../../../frameworks/base/core/api/system-current.txt $SCRIPT_DIR/src/processor/res/apis/system-current.txt
