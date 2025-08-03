/*
 * Copyright (c) 2017-2018, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef __WIFI_HAL_NUDSTATSCOMMAND_H__
#define __WIFI_HAL_NUDSTATSCOMMAND_H__

#include "nud_stats.h"

#ifdef __cplusplus
extern "C"
{
#endif /* __cplusplus */

class NUDStatsCommand: public WifiVendorCommand
{
private:
    static NUDStatsCommand *mNUDStatsCommandInstance;

    pkt_stats_result_handler mHandler;
    nud_stats mStats;
    cmdData *mpktInfo;
    int mnumStats;
    NUDStatsCommand(wifi_handle handle, int id, u32 vendor_id, u32 subcmd);

public:
    static NUDStatsCommand* instance(wifi_handle handle);

    virtual ~NUDStatsCommand();

    // This function implements creation of NUDStats specific Request
    // based on  the request type
    virtual wifi_error create();

    virtual void setSubCmd(u32 subcmd);

    virtual wifi_error requestResponse();

    virtual wifi_error notifyResponse();

    virtual int handleResponse(WifiEvent &reply);

    virtual void setHandler(pkt_stats_result_handler handler);

    void copyStats(nud_stats *stats, cmdData *pktdstats);

    void GetPktInfo(struct nlattr **tbvendor);
};

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif
