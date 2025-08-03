/**
 * Copyright (C) 2022 The Android Open Source Project
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

/** \addtogroup  RTP_Stack
 *  @{
 */

#ifndef _RtpOsUtil_h_
#define _RtpOsUtil_h_

#include <stdlib.h>
#include <RtpPfDatatypes.h>

/**
 * This is a wrapper class for os defined functions related to timestamp and network.
 */
class RtpOsUtil
{
private:
    RtpOsUtil();
    ~RtpOsUtil();

public:
    /**
     * It gets the Ntp time stamp
     *
     * @param pstNtpTime    Calculated NTP Timestamp
     */
    static RtpDt_Void GetNtpTime(tRTP_NTP_TIME& pstNtpTime);

    /**
     *  Initializes pseudo-random number generator using system time as seed
     */
    static RtpDt_Void Srand();

    /**
     * Generates a pseudo-random integral number using a new seed.
     *
     * @return Random number
     */
    static RtpDt_UInt32 Rand();

    /**
     * converts the unsigned integer from network byte order to host byte order.
     *
     * @param uiNetlong Network byte order number
     * @return host byte order converted number
     */
    static RtpDt_UInt32 Ntohl(RtpDt_UInt32 uiNetlong);

    /**
     * It returns Random number between 0 and 1
     *
     * @return Generated random fraction
     */
    static RtpDt_Double RRand();

};  // end RtpOsUtil

#endif  // _RtpOsUtil_h_

/** @}*/
