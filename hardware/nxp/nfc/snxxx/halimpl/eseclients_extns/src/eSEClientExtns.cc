/******************************************************************************
 *
 *  Copyright 2019 NXP
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
#include "eSEClientExtns.h"

#include <dlfcn.h>
#include <hidl/LegacySupport.h>
#include <unistd.h>

void* HalLibnfc_handle = NULL;

/*******************************************************************************
**
** Function         initializeEseClient()
**
** Description      Loads the module dynamically to access the functions.
**
** Parameters       none
**
** Returns          void
*******************************************************************************/
void initializeEseClient() {
  // Getting pointer to Ese Client  module
  HalLibnfc_handle = dlopen("/system/vendor/lib64/hal_libnfc.so", RTLD_NOW);

  if (HalLibnfc_handle == NULL) {
    /*fail to load the library*/
    ALOGE("Error : opening (/system/vendor/lib64/hal_libnfc.so) !!");
    return;
  }
}
/*******************************************************************************
**
** Function         checkEseClientUpdate()
**
** Description      Interface API to dynamically invoke  checkEseClientUpdate
**                  method.
**
** Parameters       none
**
** Returns          void
*******************************************************************************/
void checkEseClientUpdate() {
  if (HalLibnfc_handle == NULL) return;

  fpCheckEseClientUpdate_t fpCheckEseClientUpdate = NULL;

  /*find the address of function and data objects*/
  fpCheckEseClientUpdate =
      (fpCheckEseClientUpdate_t)dlsym(HalLibnfc_handle, "checkEseClientUpdate");

  if (!fpCheckEseClientUpdate) {
    ALOGE("Error while linking (checkEseClientUpdate) %s!!", dlerror());
  } else {
    ALOGD("Success: while linking (checkEseClientUpdate) !!");
    /* invoke function*/
    fpCheckEseClientUpdate();
  }
}
/*******************************************************************************
**
** Function         perform_eSEClientUpdate()
**
** Description      Interface API to dynamically invoke  perform_eSEClientUpdate
**                  method.
**
** Parameters       none
**
** Returns          void
*******************************************************************************/
void perform_eSEClientUpdate() {
  if (HalLibnfc_handle == NULL) return;

  fpPerformEseClientUpdate_t fpPerformEseClientUpdate = NULL;

  /*find the address of function and data objects*/
  fpPerformEseClientUpdate = (fpPerformEseClientUpdate_t)dlsym(
      HalLibnfc_handle, "perform_eSEClientUpdate");

  if (!fpPerformEseClientUpdate) {
    ALOGE("Error while linking (perform_eSEClientUpdate) !!");
  } else {
    ALOGD("Success while linking (perform_eSEClientUpdate) !!");
    fpPerformEseClientUpdate();
  }
}
/*******************************************************************************
**
** Function         eSEClientUpdate_NFC_Thread
**
** Description      Interface API to dynamically invoke
**                  eSEClientUpdate_NFC_Thread method.
**
** Parameters       none
**
** Returns          void
*******************************************************************************/
void eSEClientUpdate_NFC_Thread() {
  if (HalLibnfc_handle == NULL) return;

  fpEseClientUpdate_Nfc_Thread_t fpEseClientUpdate_Nfc_Thread = NULL;

  /*find the address of function and data objects*/
  fpEseClientUpdate_Nfc_Thread = (fpEseClientUpdate_Nfc_Thread_t)dlsym(
      HalLibnfc_handle, "eSEClientUpdate_NFC_Thread");

  if (!fpEseClientUpdate_Nfc_Thread) {
    ALOGE("Error while linking (eSEClientUpdate_NFC_Thread) !!");
  } else {
    ALOGD("Success while linking (eSEClientUpdate_NFC_Thread) !!");
    /* invoke function*/
    fpEseClientUpdate_Nfc_Thread();
  }
}
/*******************************************************************************
**
** Function         seteSEClientState
**
** Description      Interface API to dynamically invoke  seteSEClientState
**                  method.
**
** Parameters       int
**
** Returns          none
*******************************************************************************/
void seteSEClientState(uint8_t state) {
  if (HalLibnfc_handle == NULL) return;

  fpSeteSEClientState_t fpSeteSEClientState = NULL;

  ALOGD("seteSEClientState state %d", state);

  /*find the address of function and data objects*/
  fpSeteSEClientState =
      (fpSeteSEClientState_t)dlsym(HalLibnfc_handle, "seteSEClientState");

  if (!fpSeteSEClientState) {
    ALOGE("Error while linking (seteSEClientState) !!");
  } else {
    ALOGD("Success while linking (seteSEClientState) !!");
    /* invoke function, passing value of integer as a parameter */
    fpSeteSEClientState(state);
  }
}
/*******************************************************************************
**
** Function         deinitializeEseClient(void)
**
** Description      Resets the module handle & all the function pointers
**
** Parameters       none
**
** Returns          void
*******************************************************************************/
void deinitializeEseClient() {
  if (HalLibnfc_handle != NULL) {
    ALOGD("closing hal_libnfc.so");
    dlclose(HalLibnfc_handle);
    HalLibnfc_handle = NULL;
  }
}
