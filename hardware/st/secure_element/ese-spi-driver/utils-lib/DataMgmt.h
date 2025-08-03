/******************************************************************************
 *
 *  Copyright 2018 NXP
 *  Copyright (C) 2018 ST Microelectronics S.A.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#ifndef _DATAMGMT_H_
#define _DATAMGMT_H_
#include <Tpdu.h>

typedef struct TpduRecvBuff_List {
  /* buffer to be used to store the received payload */
  Tpdu tData;
  /* pointer to the next node present in lined list*/
  struct TpduRecvBuff_List* pNext;
} TpduRecvBuff_List_t;

int DataMgmt_GetData(uint16_t* data_len, uint8_t** pbuff);
int DataMgmt_StoreDataInList(uint16_t data_len, uint8_t* pbuff);

#endif /* _DATAMGMT_H_ */
