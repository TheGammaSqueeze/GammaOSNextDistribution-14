/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <errno.h>
#include <linux/if_ether.h>
#include <linux/if_link.h>
#include <linux/rtnetlink.h>
#include <tcutils/tcutils.h>

namespace android {
namespace net {

inline int tcQdiscAddDevClsact(int ifIndex) {
    return doTcQdiscClsact(ifIndex, RTM_NEWQDISC, NLM_F_EXCL | NLM_F_CREATE);
}

inline int tcQdiscReplaceDevClsact(int ifIndex) {
    return doTcQdiscClsact(ifIndex, RTM_NEWQDISC, NLM_F_CREATE | NLM_F_REPLACE);
}

inline int tcQdiscDelDevClsact(int ifIndex) {
    return doTcQdiscClsact(ifIndex, RTM_DELQDISC, 0);
}

}  // namespace net
}  // namespace android
