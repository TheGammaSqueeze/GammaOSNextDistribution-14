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

/**
 * @addtogroup NeuralNetworks
 * @{
 */

/**
 * @file NeuralNetworksShim.h
 */

#pragma once

/******************************************************************
 *
 * IMPORTANT NOTICE:
 *
 *   This file is part of Android's set of stable system headers
 *   exposed by the Android NDK (Native Development Kit).
 *
 *   Third-party source AND binary code relies on the definitions
 *   here to be FROZEN ON ALL UPCOMING PLATFORM RELEASES.
 *
 *   - DO NOT MODIFY ENUMS (EXCEPT IF YOU ADD NEW 32-BIT VALUES)
 *   - DO NOT MODIFY CONSTANTS OR FUNCTIONAL MACROS
 *   - DO NOT CHANGE THE SIGNATURE OF FUNCTIONS IN ANY WAY
 *   - DO NOT CHANGE THE LAYOUT OR SIZE OF STRUCTURES
 */

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/cdefs.h>

#include "NeuralNetworksSupportLibraryImpl.h"

__BEGIN_DECLS

/**
 * Result codes.
 */
typedef enum {
    ANNSHIM_NO_ERROR = 0,

    /**
     * Failure caused by failure to load support library driver.
     */
    ANNSHIM_FAILED_TO_LOAD_SL = 1,

    /**
     * Failure caused by failure to register HAL service.
     */
    ANNSHIM_FAILED_TO_REGISTER_SERVICE = 2,

    /**
     * General failure.
     */
    ANNSHIM_GENERAL_ERROR = 3,

    /**
     * Invalid argument
     */
    ANNSHIM_INVALID_ARGUMENT = 4,

} ANeuralNetworksShimResultCode;

/**
 * Supplementary information required to expose NNAPI HAL Service on top of
 * a NNAPI SL Driver.
 */
typedef struct ANeuralNetworksShimDeviceInfo ANeuralNetworksShimDeviceInfo;

/**
 * Additional parameters indicating how to devices should be registered.
 */
typedef struct ANeuralNetworksShimRegistrationParams ANeuralNetworksShimRegistrationParams;

/**
 * Allocate ANeuralNetworksShimDeviceInfo struct with a device name.
 *
 * Available since API level 31.
 *
 * @param deviceInfo The {@link ANeuralNetworksShimDeviceInfo} to be created.
 *                   Set to NULL if unsuccessful.
 * @param deviceName has to match NNAPI Device name exposed by SL Driver.
 * @param serviceName name of the AIDL service backed by this SL Driver device.
 *                    If null, the deviceName will be used as the service name.
 * @return {@link ANeuralNetworksShimResultCode} enum values.
 *         Returns ANNSHIM_NO_ERROR if successful.
 */
int ANeuralNetworksShimDeviceInfo_create(
        ANeuralNetworksShimDeviceInfo* _Nullable* _Nonnull deviceInfo,
        const char* _Nonnull deviceName, const char* _Nullable serviceName) __INTRODUCED_IN(31);

/**
 * Free ANeuralNetworksShimDeviceInfo struct.
 *
 * Available since API level 31.
 *
 * @param deviceInfo The NNAPI shim device info to be destroyed. Passing NULL is acceptable and
 *                   results in no operation.
 */
void ANeuralNetworksShimDeviceInfo_free(ANeuralNetworksShimDeviceInfo* _Nonnull deviceInfo)
        __INTRODUCED_IN(31);

/**
 * Allocate ANeuralNetworksShimRegistrationParams struct.
 *
 * Available since API level 31.
 *
 * @param nnapiSupportLibraryPackage Handle to a NNAPI SL implementation.
 * @param outRegistrationParams The {@link ANeuralNetworksShimRegistrationParams} to be created.
 *                  Set to NULL if unsuccessful.
 * @return {@link ANeuralNetworksShimResultCode} enum values.
 *         Returns ANNSHIM_NO_ERROR if successful.
 */
int ANeuralNetworksShimRegistrationParams_create(
        NnApiSLDriverImpl* _Nonnull nnapiSupportLibraryPackage,
        ANeuralNetworksShimRegistrationParams* _Nullable* _Nonnull outRegistrationParams)
        __INTRODUCED_IN(31);

/**
 * Free ANeuralNetworksShimRegistrationParams struct.
 *
 * Available since API level 31.
 *
 * @param registrationParams The NNAPI shim registration parameters to be destroyed. Passing NULL is
 *                           acceptable and results in no operation.
 */
void ANeuralNetworksShimRegistrationParams_free(
        ANeuralNetworksShimRegistrationParams* _Nonnull registrationParams) __INTRODUCED_IN(31);

/**
 * Add device info to the registration parameters.
 *
 * Available since API level 31.
 *
 * @param registrationParams The NNAPI shim registration parameter struct to be modified.
 * @param devicesToRegister ANeuralNetworksShimDeviceInfo struct, with name and supplementary info
 *                          about NNAPI device to register.
 * @return {@link ANeuralNetworksShimResultCode} enum values.
 *         Returns ANNSHIM_NO_ERROR if successful.
 */
int ANeuralNetworksShimRegistrationParams_addDeviceInfo(
        ANeuralNetworksShimRegistrationParams* _Nonnull registrationParams,
        const ANeuralNetworksShimDeviceInfo* _Nonnull deviceInfo) __INTRODUCED_IN(31);

/**
 * Set the number of listener threads for all registered services.
 *
 * By default, this value is 15, but this default may change in the future. The provided value must
 * be non-zero.
 *
 * Available since API level 31.
 *
 * @param registrationParams The NNAPI shim registration parameter struct to be modified.
 * @param numberOfListenerThreads Number of listener threads for the registered services.
 * @return {@link ANeuralNetworksShimResultCode} enum values.
 *         Returns ANNSHIM_NO_ERROR if successful.
 */
int ANeuralNetworksShimRegistrationParams_setNumberOfListenerThreads(
        ANeuralNetworksShimRegistrationParams* _Nonnull registrationParams,
        uint32_t numberOfListenerThreads) __INTRODUCED_IN(31);

/**
 * Set whether to register the service eagerly or lazily.
 *
 * By default, the service is eagerly registered.
 *
 * Available since API level 31.
 *
 * @param registrationParams The NNAPI shim registration parameter struct to be modified.
 * @param asLazy 'false' if the services should be registered with
 *                {@link AServiceManager_addService}, 'true' if the services should be registered
 *                with {@link AServiceManager_registerLazyService}.
 * @return {@link ANeuralNetworksShimResultCode} enum values.
 *         Returns ANNSHIM_NO_ERROR if successful.
 */
int ANeuralNetworksShimRegistrationParams_registerAsLazyService(
        ANeuralNetworksShimRegistrationParams* _Nonnull registrationParams, bool asLazy)
        __INTRODUCED_IN(31);

/**
 * Specifies whether a minimum support device should be registered in the event a specified driver
 * could not be registered from the NNAPI SL implementation.
 *
 * When called, {@link ANeuralNetworksShim_registerSupportLibraryService} will attempt to register
 * all drivers specified by a {@link ANeuralNetworksShimDeviceInfo}. For some clients of this API
 * (such as the legacy NNAPI vendor drivers), failing to register any driver should be considered
 * an error, and {@link ANeuralNetworksShim_registerSupportLibraryService} should return with an
 * error code. However, for other clients of this API (such as the NNAPI updatable vendor drivers),
 * failing to register one driver should not prevent other drivers from being registered; instead, a
 * driver with minimum support should instead be registered so that the devices registered with the
 * Service Manager matches the service instances listed in the device manifest.
 *
 * By default, {@link ANeuralNetworksShim_registerSupportLibraryService} will immediately return
 * with an error instead of registering a minimum support device.
 *
 * Available since API level 31.
 *
 * @param registrationParams The NNAPI shim registration parameter struct to be modified.
 * @param fallback 'true' if a minimal device should be registered when the actual device is not
 *                 able to be registered, 'false' if
 *                 {@link ANeuralNetworksShim_registerSupportLibraryService} should instead
 *                 immediately fail with an error.
 * @return {@link ANeuralNetworksShimResultCode} enum values.
 *         Returns ANNSHIM_NO_ERROR if successful.
 */
int ANeuralNetworksShimRegistrationParams_fallbackToMinimumSupportDevice(
        ANeuralNetworksShimRegistrationParams* _Nonnull registrationParams, bool fallback)
        __INTRODUCED_IN(31);

/**
 * Register NNAPI support library driver as HAL services.
 *
 * Takes a NNAPI SL implementation and registers each NNAPI Device it exposes as a
 * separate HAL/AIDL service.
 *
 * If loading SL driver is successful, it blocks and never returns. If there's
 * any problem with the support library driver, it returns on error.
 *
 * Available since API level 31.
 *
 * @param registrationParams Additional arguments for how the devices should be registered.
 * @return {@link ANeuralNetworksShimResultCode} enum values.
 *         Blocks forever if successful.
 */
int ANeuralNetworksShim_registerSupportLibraryService(
        const ANeuralNetworksShimRegistrationParams* _Nonnull registrationParams)
        __INTRODUCED_IN(31);

__END_DECLS

/** @} */
