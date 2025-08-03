# Copyright (c) 2019 LK Trusty Authors. All Rights Reserved.
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

RELEASE_BUILD ?= false

include project/generic-x86_64-inc.mk
include trusty/kernel/kerneltests-inc.mk
include trusty/user/base/usertests-inc.mk

TEST_BUILD := true
