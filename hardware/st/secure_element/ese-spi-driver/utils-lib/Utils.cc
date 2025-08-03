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
#include "Utils.h"

#include <stdio.h>
#include "android_logmsg.h"

/*******************************************************************************
**
** Function        Utils_charArrayToHexString
**
** Description     Converts the given char array into its HEX-based string
**                 representation.
**
** Parameters      array     - char array to be converted.
**                 length    - length of the char array.
**                 hexString - Output hex string buffer.
**
** Returns        0 on successful conversion, -1 otherwise.
**
*******************************************************************************/
int Utils_charArrayToHexString(char* array, int length, char* hexString) {
  char* ptr = hexString;
  int i;
  for (i = 0; i < length; i++) {
    ptr += sprintf(ptr, "%02X ", array[i]);
  }
  return 0;
}

/*******************************************************************************
**
** Function        Utils_getElapsedTimeInMs
**
** Description     Returns the difference of time (in ms) between t1 and t2.
**
** Parameters      t1  - initial time.
**                 t2  - final time.
**
** Returns       The difference t2 - t1 in ms.
**
*******************************************************************************/
int Utils_getElapsedTimeInMs(struct timeval t1, struct timeval t2) {
  return (t2.tv_sec - t1.tv_sec) * 1000 + (t2.tv_usec - t1.tv_usec) / 1000;
}

/*******************************************************************************
**
** Function        Utils_getElapsedTimeInUs
**
** Description     Returns the difference of time (in Us) between t1 and t2.
**
** Parameters      t1  - initial time.
**                 t2  - final time.
**
** Returns       The difference t2 - t1 in ms.
**
*******************************************************************************/
int Utils_getElapsedTimeInUs(struct timeval t1, struct timeval t2) {
  return (t2.tv_sec - t1.tv_sec) * 1000000 + (t2.tv_usec - t1.tv_usec);
}

/*******************************************************************************
**
** Function        Utils_printCurrentTime
**
** Description     Prints current time to standard log.
**
** Parameters      prefix- The prefix to be printed before the time.
**
** Returns         void
**
*******************************************************************************/
void Utils_printCurrentTime(char* prefix) {
  struct timeval currentTime;
  gettimeofday(&currentTime, 0);
  STLOG_HAL_V("SpiTiming:  %s: %ld,%ld", prefix, currentTime.tv_sec,
              currentTime.tv_usec);
}
