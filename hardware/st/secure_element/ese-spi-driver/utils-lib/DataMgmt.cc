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
#define LOG_TAG "StEse-DataMgmt"
#include "DataMgmt.h"
#include <stdlib.h>
#include <string.h>
#include "Atp.h"
#include "android_logmsg.h"

static TpduRecvBuff_List_t *head = NULL, *current = NULL;
static uint16_t total_len = 0;

static int DataMgmt_DeletList(TpduRecvBuff_List_t* head);
static int DataMgmt_GetDataFromList(uint16_t* data_len, uint8_t* pbuff);
/******************************************************************************
 * Function         DataMgmt_GetData
 *
 * Description      This function update the len and provided buffer
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
int DataMgmt_GetData(uint16_t* data_len, uint8_t** pbuffer) {
  uint16_t total_data_len = 0;
  uint8_t* pbuff = NULL;

  if (total_len == 0) {
    STLOG_HAL_E("%s total_len = %d", __FUNCTION__, total_len);
    return -1;
  }
  pbuff = (uint8_t*)malloc(total_len);
  if (NULL == pbuff) {
    STLOG_HAL_E("%s Error in malloc ", __FUNCTION__);
    return -1;
  }

  if (0 != DataMgmt_GetDataFromList(&total_data_len, pbuff)) {
    STLOG_HAL_E("%s DataMgmt_GetDataFromList", __FUNCTION__);
    free(pbuff);
    return -1;
  }
  if (total_data_len != total_len) {
    STLOG_HAL_E("%s Mismatch of len total_data_len %d total_len %d",
                __FUNCTION__, total_data_len, total_len);
    free(pbuff);
    return -1;
  }

  *pbuffer = pbuff;
  *data_len = total_data_len;
  DataMgmt_DeletList(head);
  total_len = 0;
  head = NULL;
  current = NULL;

  return 0;
}

/******************************************************************************
 * Function         DataMgmt_StoreDataInList
 *
 * Description      This function stores the received data in linked list
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
int DataMgmt_StoreDataInList(uint16_t data_len, uint8_t* pbuff) {
  TpduRecvBuff_List_t* newNode = NULL;

  if (data_len > ATP.ifsc) {
    return -1;
  }
  newNode = (TpduRecvBuff_List_t*)malloc(sizeof(TpduRecvBuff_List_t));
  if (newNode == NULL) {
    return -1;
  }
  newNode->pNext = NULL;
  newNode->tData.len = data_len;
  newNode->tData.data = (uint8_t*)malloc(ATP.ifsc * sizeof(uint8_t));
  if (newNode->tData.data == NULL) {
    free(newNode);
    return -1;
  }
  memcpy(newNode->tData.data, pbuff, data_len);

  total_len += data_len;
  if (head == NULL) {
    head = newNode;
    current = newNode;
  } else {
    current->pNext = newNode;
    current = newNode;
  }
  return 0;
}

/******************************************************************************
 * Function         DataMgmt_GetDataFromList
 *
 * Description      This function copies all linked list data in provided buffer
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
static int DataMgmt_GetDataFromList(uint16_t* data_len, uint8_t* pbuff) {
  TpduRecvBuff_List_t* new_node;
  uint16_t offset = 0;
  if (head == NULL || pbuff == NULL) {
    return -1;
  }

  new_node = head;
  while (new_node != NULL) {
    if (new_node->tData.len > ATP.ifsc) {
      return -1;
    }
    memcpy((pbuff + offset), new_node->tData.data, new_node->tData.len);
    offset += new_node->tData.len;
    new_node = new_node->pNext;
  }
  *data_len = offset;
  return 0;
}

/******************************************************************************
 * Function         DataMgmt_DeletList
 *
 * Description      This function deletes all nodes from linked list
 *
 * Returns          On Success ESESTATUS_SUCCESS else proper error code
 *
 ******************************************************************************/
static int DataMgmt_DeletList(TpduRecvBuff_List_t* head) {
  TpduRecvBuff_List_t *current, *next;
  current = head;

  if (head == NULL) {
    return -1;
  }

  while (current != NULL) {
    next = current->pNext;
    free(current->tData.data);
    current->tData.data = NULL;
    free(current);
    current = NULL;
    current = next;
  }
  head = NULL;
  return 0;
}
