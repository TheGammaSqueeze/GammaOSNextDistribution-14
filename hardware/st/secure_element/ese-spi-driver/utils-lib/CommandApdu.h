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

#ifndef COMMANDAPDU_H_
#define COMMANDAPDU_H_

#define MAX_CMD_APDU_DATA_LENGTH 255

typedef struct CommandApdu {
  char cla;
  char ins;
  char p1;
  char p2;
  char lc;
  char data[MAX_CMD_APDU_DATA_LENGTH];
  char le;
} CommandApdu;

/**
 * Transforms a CommandApdu into a byte array.
 *
 * @param cmdApdu:Apdu Structure (input)
 * @param CommandApduArray: Array of bytes (output)
 *
 * @return size of CommandApduArray
 */
int CommandApdu_toByteArray(CommandApdu cmdApdu, char* CommandApduArray);

/**
 * Get the size of a CommandApdu structure
 * @param comdApdu: Apdu Structure to get the size
 * @return Size of the CommandApdu
 */
int CommandApdu_getSize(CommandApdu cmdApdu);

/**
 * Forms an APDU
 * @param cla
 * @param ins
 * @param p1
 * @param p2
 * @param cmdDataSize
 * @param cmdData
 * @param le
 * @param cmdApdu
 * @return -1 if error 0 if ok
 */
int CommandApdu_formApduType4(char cla, char ins, char p1, char p2, char lc,
                              char* cmdData, char le, CommandApdu* cmdApdu);

/**
 * Forms an APDU
 * @param cla
 * @param ins
 * @param p1
 * @param p2
 * @param le
 * @param cmdApdu
 * @return -1 if error 0 if ok
 */
int CommandApdu_formApduType2(char cla, char ins, char p1, char p2, char le,
                              CommandApdu* cmdApdu);

#endif /* COMMANDAPDU_H_ */
