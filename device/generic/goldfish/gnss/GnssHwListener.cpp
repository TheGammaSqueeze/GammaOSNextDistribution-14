/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <chrono>
#include <log/log.h>
#include <utils/SystemClock.h>
#include "GnssHwListener.h"

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace implementation {

namespace {
const char* testNmeaField(const char* i, const char* end,
                          const char* v,
                          const char sep) {
    while (i < end) {
        if (*v == 0) {
            return (*i == sep) ? (i + 1) : nullptr;
        } else if (*v == *i) {
            ++v;
            ++i;
        } else {
            return nullptr;
        }
    }

    return nullptr;
}

const char* skipAfter(const char* i, const char* end, const char c) {
    for (; i < end; ++i) {
        if (*i == c) {
            return i + 1;
        }
    }
    return nullptr;
}

double convertDMMF(const int dmm, const int f, int p10) {
    const int d = dmm / 100;
    const int m = dmm % 100;
    int base10 = 1;
    for (; p10 > 0; --p10) { base10 *= 10; }

    return double(d) + (m + (f / double(base10))) / 60.0;
}

double sign(char m, char positive) { return (m == positive) ? 1.0 : -1; }

ElapsedRealtime makeElapsedRealtime(const int64_t timestampNs) {
    return {
        .flags = ElapsedRealtime::HAS_TIMESTAMP_NS |
                 ElapsedRealtime::HAS_TIME_UNCERTAINTY_NS,
        .timestampNs = timestampNs,
        .timeUncertaintyNs = 1000000.0
    };
}

}  // namespace

GnssHwListener::GnssHwListener(IDataSink& sink): mSink(sink) {
    mBuffer.reserve(256);
    mSink.onGnssStatusCb(IGnssCallback::GnssStatusValue::ENGINE_ON);
}

GnssHwListener::~GnssHwListener() {
    mSink.onGnssStatusCb(IGnssCallback::GnssStatusValue::ENGINE_OFF);
}

void GnssHwListener::consume(const char* buf, size_t sz) {
    ALOGD("%s:%s:%d sz=%zu", "GnssHwListener", __func__, __LINE__, sz);

    for (; sz > 0; ++buf, --sz) {
        consume1(*buf);
    }
}

void GnssHwListener::consume1(const char c) {
    if (c == '$' || !mBuffer.empty()) {
        mBuffer.push_back(c);
    }
    if (c == '\n') {
        using namespace std::chrono;

        const int64_t timestampMs = time_point_cast<milliseconds>(
                system_clock::now()).time_since_epoch().count();
        const ElapsedRealtime ert = makeElapsedRealtime(
                ::android::elapsedRealtimeNano());

        if (parse(mBuffer.data() + 1, mBuffer.data() + mBuffer.size() - 2,
                  timestampMs, ert)) {
            mSink.onGnssNmeaCb(timestampMs, std::string(mBuffer.data(), mBuffer.size()));
        } else {
            mBuffer.back() = 0;
            ALOGW("%s:%d: failed to parse an NMEA message, '%s'",
                  __func__, __LINE__, mBuffer.data());
        }
        mBuffer.clear();
    } else if (mBuffer.size() >= 1024) {
        ALOGW("%s:%d buffer was too long, dropped", __func__, __LINE__);
        mBuffer.clear();
    }
}

bool GnssHwListener::parse(const char* begin, const char* end,
                           const int64_t timestampMs,
                           const ElapsedRealtime& ert) {
    if (const char* fields = testNmeaField(begin, end, "GPRMC", ',')) {
        return parseGPRMC(fields, end, timestampMs, ert);
    } else if (const char* fields = testNmeaField(begin, end, "GPGGA", ',')) {
        return parseGPGGA(fields, end, timestampMs, ert);
    } else {
        return false;
    }
}

//        begin                                                          end
// $GPRMC,195206,A,1000.0000,N,10000.0000,E,173.8,231.8,010420,004.2,W*47
//          1    2    3      4    5       6     7     8      9    10 11 12
//      1  195206     Time Stamp
//      2  A          validity - A-ok, V-invalid
//      3  1000.0000  current Latitude
//      4  N          North/South
//      5  10000.0000 current Longitude
//      6  E          East/West
//      7  173.8      Speed in knots
//      8  231.8      True course
//      9  010420     Date Stamp (13 June 1994)
//     10  004.2      Variation
//     11  W          East/West
//     12  *70        checksum
bool GnssHwListener::parseGPRMC(const char* begin, const char*,
                                const int64_t timestampMs,
                                const ElapsedRealtime& ert) {
    double speedKnots = 0;
    double course = 0;
    double variation = 0;
    int latdmm = 0;
    int londmm = 0;
    int latf = 0;
    int lonf = 0;
    int latdmmConsumed = 0;
    int latfConsumed = 0;
    int londmmConsumed = 0;
    int lonfConsumed = 0;
    int hhmmss = -1;
    int ddmoyy = 0;
    char validity = 0;
    char ns = 0;    // north/south
    char ew = 0;    // east/west
    char var_ew = 0;

    if (sscanf(begin, "%06d,%c,%d.%n%d%n,%c,%d.%n%d%n,%c,%lf,%lf,%d,%lf,%c*",
               &hhmmss, &validity,
               &latdmm, &latdmmConsumed, &latf, &latfConsumed, &ns,
               &londmm, &londmmConsumed, &lonf, &lonfConsumed, &ew,
               &speedKnots, &course,
               &ddmoyy,
               &variation, &var_ew) != 13) {
        return false;
    }
    if (validity != 'A') {
        return false;
    }

    const double lat = convertDMMF(latdmm, latf, latfConsumed - latdmmConsumed) * sign(ns, 'N');
    const double lon = convertDMMF(londmm, lonf, lonfConsumed - londmmConsumed) * sign(ew, 'E');
    const double speed = speedKnots * 0.514444;

    GnssLocation loc;
    loc.elapsedRealtime = ert;

    loc.latitudeDegrees = lat;
    loc.longitudeDegrees = lon;
    loc.speedMetersPerSec = speed;
    loc.bearingDegrees = course;
    loc.horizontalAccuracyMeters = 5;
    loc.speedAccuracyMetersPerSecond = .5;
    loc.bearingAccuracyDegrees = 30;
    loc.timestampMillis = timestampMs;

    loc.gnssLocationFlags =
        GnssLocation::HAS_LAT_LONG |
        GnssLocation::HAS_SPEED |
        GnssLocation::HAS_BEARING |
        GnssLocation::HAS_HORIZONTAL_ACCURACY |
        GnssLocation::HAS_SPEED_ACCURACY |
        GnssLocation::HAS_BEARING_ACCURACY;

    if (mAltitude.has_value()) {
        loc.altitudeMeters = mAltitude.value();
        loc.verticalAccuracyMeters = .5;
        loc.gnssLocationFlags |= GnssLocation::HAS_ALTITUDE |
                                 GnssLocation::HAS_VERTICAL_ACCURACY;
    }

    mSink.onGnssLocationCb(loc);

    return true;
}

// $GPGGA,123519,4807.0382,N,12204.9799,W,1,6,,4.2,M,0.,M,,,*47
//    time of fix      123519     12:35:19 UTC
//    latitude         4807.0382  48 degrees, 07.0382 minutes
//    north/south      N or S
//    longitude        12204.9799 122 degrees, 04.9799 minutes
//    east/west        E or W
//    fix quality      1          standard GPS fix
//    satellites       1 to 12    number of satellites being tracked
//    HDOP             <dontcare> horizontal dilution
//    altitude         4.2        altitude above sea-level
//    altitude units   M          to indicate meters
//    diff             <dontcare> height of sea-level above ellipsoid
//    diff units       M          to indicate meters (should be <dontcare>)
//    dgps age         <dontcare> time in seconds since last DGPS fix
//    dgps sid         <dontcare> DGPS station id
bool GnssHwListener::parseGPGGA(const char* begin, const char* end,
                                const int64_t /*timestampMs*/,
                                const ElapsedRealtime& /*ert*/) {
    double altitude = 0;
    int latdmm = 0;
    int londmm = 0;
    int latf = 0;
    int lonf = 0;
    int latdmmConsumed = 0;
    int latfConsumed = 0;
    int londmmConsumed = 0;
    int lonfConsumed = 0;
    int hhmmss = 0;
    int fixQuality = 0;
    int nSatellites = 0;
    int consumed = 0;
    char ns = 0;
    char ew = 0;
    char altitudeUnit = 0;

    if (sscanf(begin, "%06d,%d.%n%d%n,%c,%d.%n%d%n,%c,%d,%d,%n",
               &hhmmss,
               &latdmm, &latdmmConsumed, &latf, &latfConsumed, &ns,
               &londmm, &londmmConsumed, &lonf, &lonfConsumed, &ew,
               &fixQuality,
               &nSatellites,
               &consumed) != 9) {
        return false;
    }

    begin = skipAfter(begin + consumed, end, ',');  // skip HDOP
    if (!begin) {
        return false;
    }
    if (sscanf(begin, "%lf,%c,", &altitude, &altitudeUnit) != 2) {
        return false;
    }
    if (altitudeUnit != 'M') {
        return false;
    }

    mAltitude = altitude;

    std::vector<IGnssCallback::GnssSvInfo> svInfo(nSatellites);
    for (int i = 0; i < nSatellites; ++i) {
        auto* info = &svInfo[i];

        info->svid = i + 3;
        info->constellation = GnssConstellationType::GPS;
        info->cN0Dbhz = 30;
        info->basebandCN0DbHz = 42;
        info->elevationDegrees = 0;
        info->azimuthDegrees = 0;
        info->carrierFrequencyHz = 1.59975e+09;
        info->svFlag = static_cast<int>(IGnssCallback::GnssSvFlags::HAS_CARRIER_FREQUENCY);
    }

    mSink.onGnssSvStatusCb(std::move(svInfo));

    return true;
}

}  // namespace implementation
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
