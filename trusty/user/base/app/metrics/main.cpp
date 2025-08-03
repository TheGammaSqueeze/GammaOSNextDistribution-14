/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define TLOG_TAG "metrics-srv"

#include "consumer.h"

#include <lib/shared/binder_discover/binder_discover.h>
#include <lib/tipc/tipc_srv.h>
#include <lk/err_ptr.h>
#include <metrics_consts.h>
#include <stdio.h>
#include <trusty_log.h>

#include <binder/IBinder.h>
#include <binder/RpcServerTrusty.h>
#include <binder/RpcTransportTipcTrusty.h>

#include <android/frameworks/stats/IStats.h>
#include <android/frameworks/stats/VendorAtom.h>
#include <android/trusty/stats/nw/setter/BnStatsSetter.h>
#include <android/trusty/stats/ports.h>
#include <android/trusty/stats/setter/ports.h>
#include <android/trusty/stats/tz/BnStats.h>

using namespace android;
using binder::Status;
using frameworks::stats::VendorAtom;
using frameworks::stats::VendorAtomValue;

class StatsRelayer : public trusty::stats::tz::BnStats {
public:
    class StatsSetterNormalWorld
            : public trusty::stats::nw::setter::BnStatsSetter {
    public:
        StatsSetterNormalWorld(sp<StatsRelayer>&& statsRelayer)
                : mStatsRelayer(std::move(statsRelayer)) {}

        Status setInterface(const sp<frameworks::stats::IStats>& istats) {
            assert(mStatsRelayer.get() != nullptr);

            TLOGD("setInterface from Normal-World Consumer\n");
            // save iStats facet for asynchronous callback
            mStatsRelayer->mIStats = istats;
            return Status::ok();
        };

    private:
        sp<StatsRelayer> mStatsRelayer;
    };

    Status reportVendorAtom(const ::VendorAtom& vendorAtom) {
        if (mIStats) {
            /*
             * when the normal-world consumer initialises its binder session
             * with an incoming thread (setMaxIncomingThreads(1)),
             * its istats facet is accessible after the
             * setInterface returns.
             */
            Status rc = mIStats->reportVendorAtom(vendorAtom);
            if (!rc.isOk()) {
                TLOGD("relaying reportVendorAtom failed=%d.\n",
                      rc.exceptionCode());
                return rc;
            }
        } else {
            TLOGE("Normal-World IStats not initialized: "
                  "VendorAtomId (%d) not relayed\n",
                  vendorAtom.atomId);
        }
        return Status::ok();
    }

private:
    // the normal-world IStats facet, stored for asynchronous callback
    sp<frameworks::stats::IStats> mIStats;
};

int main(void) {
    int rc;
    struct tipc_hset* hset;

    hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("failed (%d) to create handle set\n", PTR_ERR(hset));
        return PTR_ERR(hset);
    }

    auto statsRelayer = sp<StatsRelayer>::make();
    auto statsSetterNormalWorld =
            sp<StatsRelayer::StatsSetterNormalWorld>::make(sp(statsRelayer));

    const auto portAcl_TA = RpcServerTrusty::PortAcl{
            .flags = IPC_PORT_ALLOW_TA_CONNECT,
    };
    const auto portAcl_NS = RpcServerTrusty::PortAcl{
            .flags = IPC_PORT_ALLOW_NS_CONNECT,
    };

    // message size needs to be large enough to cover all messages sent by
    // the IStats and IStatsSetter clients
    constexpr size_t maxMsgSize = 4096;
    TLOGD("Creating Relayer (exposing IStats)\n");
    auto srvIStats = RpcServerTrusty::make(
            hset, METRICS_ISTATS_PORT,
            std::make_shared<const RpcServerTrusty::PortAcl>(portAcl_TA),
            maxMsgSize);
    if (srvIStats == nullptr) {
        return EXIT_FAILURE;
    }
    srvIStats->setRootObject(statsRelayer);

    // Add the relayer to binder_discover so connections within the current TA
    // work without problems
    rc = binder_discover_add_service(METRICS_ISTATS_PORT, statsRelayer);
    if (rc != NO_ERROR) {
        TLOGE("Failed (%d) to add relayer to binder discover\n", rc);
        return rc;
    }

    auto srvIStatsSetter = RpcServerTrusty::make(
            hset, METRICS_ISTATS_SETTER_PORT,
            std::make_shared<const RpcServerTrusty::PortAcl>(portAcl_NS),
            maxMsgSize);
    if (srvIStatsSetter == nullptr) {
        return EXIT_FAILURE;
    }
    srvIStatsSetter->setRootObject(statsSetterNormalWorld);

    rc = add_metrics_consumer_service(hset);
    if (rc != NO_ERROR) {
        TLOGE("failed (%d) to add metrics consumer service\n", rc);
        return rc;
    }

    return tipc_run_event_loop(hset);
}
