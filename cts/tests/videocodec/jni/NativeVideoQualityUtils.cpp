/*
 * Copyright (C) 2023 The Android Open Source Project
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

//#define LOG_NDEBUG 0
#define LOG_TAG "NativeVideoQualityUtils"

#include <jni.h>
#include <log/log.h>

#include <Eigen/Dense>
#include <Eigen/QR>
#include <string>
#include <vector>

// Migrate this method to std::format when C++20 becomes available
template <typename... Args>
std::string StringFormat(const std::string& format, Args... args) {
    auto size = std::snprintf(nullptr, 0, format.c_str(), args...);
    if (size < 0) return {};
    std::vector<char> buffer(size + 1); // Add 1 for terminating null byte
    std::snprintf(buffer.data(), buffer.size(), format.c_str(), args...);
    return std::string(buffer.data(), size); // Exclude the terminating null byte
}

double polyEval(std::vector<double>& coeffs, double x) {
    double y = coeffs[0];
    double xn = x;
    for (int i = 1; i < coeffs.size(); i++) {
        y += (coeffs[i] * xn);
        xn *= x;
    }
    return y;
}

std::vector<double> polyIntegrate(std::vector<double>& coeffs, double coi = 0.0) {
    std::vector<double> integratedCoeffs(coeffs.size() + 1);
    integratedCoeffs[0] = coi; // constant of integration
    for (int i = 1; i < coeffs.size() + 1; i++) {
        integratedCoeffs[i] = coeffs[i - 1] / i;
    }
    return integratedCoeffs;
}

std::vector<double> polyFit(std::vector<double>& rates, std::vector<double>& qualities, int order) {
    // y = X * a, y is vector of qualities, X is vandermonde matrix and a is vector of coeffs.
    Eigen::MatrixXd X(rates.size(), order + 1);
    Eigen::MatrixXd y(qualities.size(), 1);
    for (int i = 0; i < rates.size(); ++i) {
        y(i, 0) = qualities[i];
        double element = 1;
        for (int j = 0; j < order + 1; ++j) {
            X(i, j) = element;
            element *= rates[i];
        }
    }
    // QR decomposition
    Eigen::MatrixXd a = X.colPivHouseholderQr().solve(y);
    std::vector<double> coeffs(order + 1);
    for (int i = 0; i < order + 1; i++) {
        coeffs[i] = a(i, 0);
    }
    return coeffs;
}

double getAvgImprovement(std::vector<double>& xA, std::vector<double>& yA, std::vector<double>& xB,
                         std::vector<double>& yB, int order) {
    std::vector<double> coeffsA = polyFit(xA, yA, order);
    std::vector<double> coeffsB = polyFit(xB, yB, order);
    std::vector<double> integratedCoeffsA = polyIntegrate(coeffsA);
    std::vector<double> integratedCoeffsB = polyIntegrate(coeffsB);
    double minX = std::max(*std::min_element(xA.begin(), xA.end()),
                           *std::min_element(xB.begin(), xB.end()));
    double maxX = std::min(*std::max_element(xA.begin(), xA.end()),
                           *std::max_element(xB.begin(), xB.end()));
    double areaA = polyEval(integratedCoeffsA, maxX) - polyEval(integratedCoeffsA, minX);
    double areaB = polyEval(integratedCoeffsB, maxX) - polyEval(integratedCoeffsB, minX);
    return (areaB - areaA) / (maxX - minX);
}

static jdouble nativeGetBDRate(JNIEnv* env, jobject, jdoubleArray jQualityA, jdoubleArray jRatesA,
                               jdoubleArray jQualityB, jdoubleArray jRatesB, jboolean selBdSnr,
                               jobject jRetMsg) {
    jsize len[4]{env->GetArrayLength(jQualityA), env->GetArrayLength(jRatesA),
                 env->GetArrayLength(jQualityB), env->GetArrayLength(jRatesB)};
    std::string msg;
    if (len[0] != len[1] || len[0] != len[2] || len[0] != len[3]) {
        msg = StringFormat("array length of quality and bit rates for set A/B are not same, "
                           "lengths are %d %d %d %d \n",
                           (int)len[0], (int)len[1], (int)len[2], (int)len[3]);
    } else if (len[0] < 4) {
        msg = StringFormat("too few data-points present for bd rate analysis, count %d \n", len[0]);
    } else {
        std::vector<double> ratesA(len[0]);
        env->GetDoubleArrayRegion(jRatesA, 0, len[0], &ratesA[0]);
        std::vector<double> ratesB(len[0]);
        env->GetDoubleArrayRegion(jRatesB, 0, len[0], &ratesB[0]);
        std::vector<double> qualitiesA(len[0]);
        env->GetDoubleArrayRegion(jQualityA, 0, len[0], &qualitiesA[0]);
        std::vector<double> qualitiesB(len[0]);
        env->GetDoubleArrayRegion(jQualityB, 0, len[0], &qualitiesB[0]);
        // log rate
        for (int i = 0; i < len[0]; i++) {
            ratesA[i] = std::log(ratesA[i]);
            ratesB[i] = std::log(ratesB[i]);
        }
        const int order = 3;
        if (selBdSnr) {
            return getAvgImprovement(ratesA, qualitiesA, ratesB, qualitiesB, order);
        } else {
            double bdRate = getAvgImprovement(qualitiesA, ratesA, qualitiesB, ratesB, order);
            // In really bad formed data the exponent can grow too large clamp it.
            if (bdRate > 200) {
                bdRate = 200;
            }
            bdRate = (std::exp(bdRate) - 1) * 100;
            return bdRate;
        }
    }
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    return 0;
}

int registerAndroidVideoCodecCtsVQUtils(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeGetBDRate", "([D[D[D[DZLjava/lang/StringBuilder;)D", (void*)nativeGetBDRate},
    };
    jclass c = env->FindClass("android/videocodec/cts/VideoEncoderQualityRegressionTestBase");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (registerAndroidVideoCodecCtsVQUtils(env) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
