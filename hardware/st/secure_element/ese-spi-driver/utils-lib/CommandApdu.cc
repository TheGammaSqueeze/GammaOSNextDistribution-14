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
#include "CommandApdu.h"
#include <string.h>

/*******************************************************************************
**
** Function        CommandApdu_toByteArray
**
** Description     Transforms a CommandApdu into a byte array.
**
** Parameters      cmdApdu          - input APDU struct.
**                 commandApduArray - output array bytes.
**
** Returns         CommandApduArray size.
**
*******************************************************************************/
int CommandApdu_toByteArray(CommandApdu cmdApdu, char* commandApduArray) {
  int commandApduArraySize = CommandApdu_getSize(cmdApdu);

  if (cmdApdu.lc > MAX_CMD_APDU_DATA_LENGTH) {
    return -1;
  }

  commandApduArray[0] = cmdApdu.cla;
  commandApduArray[1] = cmdApdu.ins;
  commandApduArray[2] = cmdApdu.p1;
  commandApduArray[3] = cmdApdu.p2;

  if (cmdApdu.lc > 0) {
    commandApduArray[4] = cmdApdu.lc;
    memcpy(commandApduArray + 5, cmdApdu.data, cmdApdu.lc);
  }

  if (cmdApdu.le >= 0) {
    commandApduArray[commandApduArraySize - 1] = cmdApdu.le;
  }

  return commandApduArraySize;
}

/*******************************************************************************
**
** Function        CommandApdu_getSize
**
** Description     Get the size of a CommandApdu structure
**
** Parameters      cmdApdu          - input APDU struct.
**
** Returns         cmdApdu size.
**
*******************************************************************************/
int CommandApdu_getSize(CommandApdu cmdApdu) {
  // There will be always cla+ins+p1+p2
  int size = 4;

  if (cmdApdu.lc > 0) {
    // Size of lc + data
    size += 1 + cmdApdu.lc;
  }

  if (cmdApdu.le >= 0) {
    size++;
  }

  return size;
}

/*******************************************************************************
**
** Function        CommandApdu_formApduType4
**
** Description     Forms an APDU
**
** Parameters
**
** Returns         -1 if error 0 if ok
**
*******************************************************************************/
int CommandApdu_formApduType4(char cla, char ins, char p1, char p2, char lc,
                              char* cmdData, char le, CommandApdu* cmdApdu) {
  if (lc > MAX_CMD_APDU_DATA_LENGTH) {
    return -1;
  }

  cmdApdu->cla = cla;
  cmdApdu->ins = ins;
  cmdApdu->p1 = p1;
  cmdApdu->p2 = p2;
  cmdApdu->lc = lc;
  if (lc > 0) {
    memcpy(cmdApdu->data, cmdData, lc);
  }
  cmdApdu->le = le;

  return CommandApdu_getSize(*cmdApdu);
}

/*******************************************************************************
**
** Function        CommandApdu_formApduType2
**
** Description     Forms an APDU
**
** Parameters
**
** Returns         -1 if error 0 if ok
**
*******************************************************************************/
int CommandApdu_formApduType2(char cla, char ins, char p1, char p2, char le,
                              CommandApdu* cmdApdu) {
  cmdApdu->cla = cla;
  cmdApdu->ins = ins;
  cmdApdu->p1 = p1;
  cmdApdu->p2 = p2;
  cmdApdu->le = le;
  cmdApdu->lc = 0;

  return CommandApdu_getSize(*cmdApdu);
}
