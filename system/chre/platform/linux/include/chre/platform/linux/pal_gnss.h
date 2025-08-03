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

#ifndef CHRE_PLATFORM_LINUX_PAL_GNSS_H_
#define CHRE_PLATFORM_LINUX_PAL_GNSS_H_

/**
 * @return whether the GNSS location session is enabled in the GNSS PAL.
 */
bool chrePalGnssIsLocationEnabled();

/**
 * @return whether the GNSS measurement session is enabled in the GNSS PAL.
 */
bool chrePalGnssIsMeasurementEnabled();

/**
 * @return whether the GNSS passive listener is enabled in the GNSS PAL.
 */
bool chrePalGnssIsPassiveLocationListenerEnabled();

/**
 * Delays sending the location events until
 * chrePalGnssStartSendingLocationEvents is called.
 *
 * Use this if you need to control the timing between when CHRE requests GNSS
 * locations and when the async callback and events are delivered.
 *
 * The default is to start sending events immediately to CHRE.
 */
void chrePalGnssDelaySendingLocationEvents(bool enable);

/**
 * Starts sending the location events after chrePalControlLocationSession has
 * been called with enable set to true.
 *
 * Note: This function must only be called after a call to
 *       chrePalGnssDelaySendingLocationEvents with enable set to true.
 */
void chrePalGnssStartSendingLocationEvents();

#endif  // CHRE_PLATFORM_LINUX_PAL_GNSS_H_
