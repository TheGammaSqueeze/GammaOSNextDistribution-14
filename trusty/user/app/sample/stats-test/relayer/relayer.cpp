/*
 * Copyright (C) 2013 The Android Open Source Project
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

#define TLOG_TAG "stats-relayer"

#include <vector>

#include <inttypes.h>
#include <lib/tipc/tipc.h>
#include <lk/err_ptr.h>
#include <relayer_consts.h>
#include <stdio.h>
#include <sys/mman.h>
#include <trusty/sys/mman.h>
#include <trusty/time.h>
#include <trusty_log.h>
#include <uapi/err.h>
#include <uapi/mm.h>

#include <binder/IBinder.h>
#include <binder/RpcServerTrusty.h>
#include <binder/RpcTransportTipcTrusty.h>

#include <android/frameworks/stats/VendorAtom.h>
#include <android/trusty/stats/nw/setter/BnStatsSetter.h>
#include <android/trusty/stats/tz/BnStats.h>
#include <android/trusty/stats/tz/BnStatsSetter.h>

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

    class StatsSetterSecureWorld : public trusty::stats::tz::BnStatsSetter {
    public:
        StatsSetterSecureWorld(sp<StatsRelayer>&& statsRelayer)
                : mStatsRelayer(std::move(statsRelayer)) {}

        Status setInterface(const sp<trusty::stats::tz::IStats>& istats) {
            assert(mStatsRelayer.get() != nullptr);

            TLOGD("setInterface from Secure World Consumer\n");
            // callback into iStats synchronously if any atoms were previously
            // received. Swap so we don't try to report the same atoms twice
            // if we encounter an error.
            std::vector<VendorAtom> pending;
            std::swap(pending, mStatsRelayer->mPendingAtoms);

            for (const VendorAtom& atom : pending) {
                auto rc = istats->reportVendorAtom(atom);
                if (!rc.isOk()) {
                    TLOGE("reportVendorAtom error %d\n", rc.exceptionCode());
                    return rc;
                }
            }
            return Status::ok();
        }

    private:
        sp<StatsRelayer> mStatsRelayer;
    };

    StatsRelayer() : trusty::stats::tz::BnStats(), mPendingAtoms() {}

    Status reportVendorAtom(const VendorAtom& vendorAtom) {
        TLOGD("reportVendorAtom atomId=%d.\n", vendorAtom.atomId);
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
        }

        /*
         * if istats endpoint is in secure-world, the callback path
         * is NOT persistent, hence istats pointer is valid only through the
         * setInterface call, so we record the vendorAtom so it
         * can be shared on a setInterface invocation
         */
        mPendingAtoms.push_back(vendorAtom);
        return Status::ok();
    }

private:
    // the normal-world IStats facet, stored for asynchronous callback
    sp<frameworks::stats::IStats> mIStats;

    // the vendor atom, stored to be shared to the consumer TA
    // via the synchronous callback
    std::vector<VendorAtom> mPendingAtoms;
};

int main(void) {
    TLOGI("Starting StatsRelayer\n");

    tipc_hset* hset = tipc_hset_create();
    if (IS_ERR(hset)) {
        TLOGE("Failed to create handle set (%d)\n", PTR_ERR(hset));
        return EXIT_FAILURE;
    }

    auto statsRelayer = sp<StatsRelayer>::make();
    auto statsSetterNormalWorld =
            sp<StatsRelayer::StatsSetterNormalWorld>::make(sp(statsRelayer));
    auto statsSetterSecureWorld =
            sp<StatsRelayer::StatsSetterSecureWorld>::make(sp(statsRelayer));

    const auto portAcl_TA = RpcServerTrusty::PortAcl{
            .flags = IPC_PORT_ALLOW_TA_CONNECT,
    };
    const auto portAcl_NS = RpcServerTrusty::PortAcl{
            .flags = IPC_PORT_ALLOW_NS_CONNECT,
    };

    // message size needs to be large enough to cover all messages sent by
    // the tests
    constexpr size_t maxMsgSize = 4096;
    TLOGE("Creating Relayer (exposing IStats)\n");
    auto srvIStats = RpcServerTrusty::make(
            hset, RELAYER_PORT_ISTATS,
            std::make_shared<const RpcServerTrusty::PortAcl>(portAcl_TA),
            maxMsgSize);
    if (!srvIStats.ok()) {
        TLOGE("Failed to create RpcServer (%d)\n", srvIStats.error());
        return EXIT_FAILURE;
    }
    (*srvIStats)->setRootObject(statsRelayer);

    // expose the test port for the NW accessible IStatsSetter interface
    // trusty::stats::setter::IStatsSetter
    auto srvIStatsSetterNormalWorld = RpcServerTrusty::make(
            hset, RELAYER_PORT_ISTATS_SETTER_NORMAL_WORLD,
            std::make_shared<const RpcServerTrusty::PortAcl>(portAcl_NS),
            maxMsgSize);
    if (!srvIStatsSetterNormalWorld.ok()) {
        TLOGE("Failed to create RpcServer (%d)\n",
              srvIStatsSetterNormalWorld.error());
        return EXIT_FAILURE;
    }
    (*srvIStatsSetterNormalWorld)->setRootObject(statsSetterNormalWorld);

    auto srvIStatsSetterSecureWorld = RpcServerTrusty::make(
            hset, RELAYER_PORT_ISTATS_SETTER_SECURE_WORLD,
            std::make_shared<const RpcServerTrusty::PortAcl>(portAcl_TA),
            maxMsgSize);
    if (!srvIStatsSetterSecureWorld.ok()) {
        TLOGE("Failed to create RpcServer (%d)\n",
              srvIStatsSetterSecureWorld.error());
        return EXIT_FAILURE;
    }
    (*srvIStatsSetterSecureWorld)->setRootObject(statsSetterSecureWorld);

    int rc = tipc_run_event_loop(hset);

    TLOGE("stats-relayer service died\n");

    return rc;
}
