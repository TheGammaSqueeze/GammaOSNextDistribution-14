/******************************************************************************
 *
 *  Copyright (C) 2018 ST Microelectronics S.A.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 ******************************************************************************/
#ifndef UTILS_H_
#define UTILS_H_

#include <sys/time.h>

/**
 * Converts the given char array into its HEX-based string representation.
 *
 * @param array The char array to be converted.
 * @param length The length of the char array.
 * @param hexString Out param where the result will be stored.
 *
 * @return 0 on successful conversion, -1 otherwise.
 */
int Utils_charArrayToHexString(char* array, int length, char* hexString);

char* convert(uint8_t* buf);

/**
 * Returns the difference of time (in ms) between t1 and t2.
 *
 * @param t1 The initial time.
 * @param t2 The final time.
 *
 * @return The difference t2 - t1 in ms.
 */
int Utils_getElapsedTimeInMs(struct timeval t1, struct timeval t2);

/**
 * Returns the difference of time (in us) between t1 and t2.
 *
 * @param t1 The initial time.
 * @param t2 The final time.
 *
 * @return The difference t2 - t1 in ms.
 */
int Utils_getElapsedTimeInUs(struct timeval t1, struct timeval t2);

/**
 * Prints current time to standard log.
 *
 * @param prefix The prefix to be printed before the time.
 */
void Utils_printCurrentTime(char* prefix);

#endif /* UTILS_H_ */
