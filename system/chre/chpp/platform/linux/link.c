/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "chpp/link.h"

#include <inttypes.h>
#include <stddef.h>
#include <stdint.h>
#include <string.h>

#include "chpp/log.h"
#include "chpp/macros.h"
#include "chpp/notifier.h"
#include "chpp/platform/platform_link.h"
#include "chpp/transport.h"

// The set of signals to use for the linkSendThread.
#define SIGNAL_EXIT UINT32_C(1 << 0)
#define SIGNAL_DATA UINT32_C(1 << 1)
#define SIGNAL_DATA_RX UINT32_C(1 << 2)

struct ChppNotifier gCycleSendThreadNotifier;

void cycleSendThread(void) {
  chppNotifierSignal(&gCycleSendThreadNotifier, 1);
}

/**
 * This thread is used to "send" TX data to the remote endpoint. The remote
 * endpoint is defined by the ChppTransportState pointer, so a loopback link
 * with a single CHPP instance can be supported.
 */
static void *linkSendThread(void *linkContext) {
  struct ChppLinuxLinkState *context =
      (struct ChppLinuxLinkState *)(linkContext);
  while (true) {
    if (context->manualSendCycle) {
      chppNotifierWait(&gCycleSendThreadNotifier);
    }
    uint32_t signal = chppNotifierTimedWait(&context->notifier, CHPP_TIME_MAX);

    if (signal & SIGNAL_EXIT) {
      break;
    }

    if (signal & SIGNAL_DATA) {
      enum ChppLinkErrorCode error;

      chppMutexLock(&context->mutex);

      if (context->remoteLinkState == NULL) {
        CHPP_LOGW("remoteLinkState is NULL");
        error = CHPP_LINK_ERROR_NONE_SENT;

      } else if (!context->linkEstablished) {
        CHPP_LOGE("No (fake) link");
        error = CHPP_LINK_ERROR_NO_LINK;

      } else {
        // Use notifiers only when there are 2 different link layers (i.e. no
        // loopback). Otherwise call chppRxDataCb directly.
        if (context->rxInRemoteEndpointWorker &&
            context->remoteLinkState != context) {
          chppNotifierSignal(&context->remoteLinkState->notifier,
                             SIGNAL_DATA_RX);

          // Wait for the RX thread to consume the buffer before we can modify
          // it.
          chppNotifierTimedWait(&context->rxNotifier, CHPP_TIME_MAX);
        } else if (!chppRxDataCb(context->remoteLinkState->transportContext,
                                 context->buf, context->bufLen)) {
          CHPP_LOGW("chppRxDataCb return state!=preamble (packet incomplete)");
        }
        error = CHPP_LINK_ERROR_NONE_SENT;
      }

      context->bufLen = 0;
      chppLinkSendDoneCb(context->transportContext, error);

      chppMutexUnlock(&context->mutex);
    }

    if (signal & SIGNAL_DATA_RX) {
      CHPP_NOT_NULL(context->transportContext);
      CHPP_NOT_NULL(context->remoteLinkState);
      // Process RX data which are the TX data from the remote link.
      chppRxDataCb(context->transportContext, context->remoteLinkState->buf,
                   context->remoteLinkState->bufLen);
      // Unblock the TX thread when the buffer has been consumed.
      chppNotifierSignal(&context->remoteLinkState->rxNotifier, 0x01);
    }
  }

  return NULL;
}

static void init(void *linkContext,
                 struct ChppTransportState *transportContext) {
  struct ChppLinuxLinkState *context =
      (struct ChppLinuxLinkState *)(linkContext);
  context->bufLen = 0;
  context->transportContext = transportContext;
  chppMutexInit(&context->mutex);
  chppNotifierInit(&context->notifier);
  chppNotifierInit(&context->rxNotifier);
  chppNotifierInit(&gCycleSendThreadNotifier);
  pthread_create(&context->linkSendThread, NULL /* attr */, linkSendThread,
                 context);
  if (context->linkThreadName != NULL) {
    pthread_setname_np(context->linkSendThread, context->linkThreadName);
  }
}

static void deinit(void *linkContext) {
  struct ChppLinuxLinkState *context =
      (struct ChppLinuxLinkState *)(linkContext);
  context->bufLen = 0;
  chppNotifierSignal(&context->notifier, SIGNAL_EXIT);
  if (context->manualSendCycle) {
    // Unblock the send thread so it exits.
    cycleSendThread();
  }
  pthread_join(context->linkSendThread, NULL /* retval */);
  chppNotifierDeinit(&context->notifier);
  chppNotifierDeinit(&context->rxNotifier);
  chppNotifierDeinit(&gCycleSendThreadNotifier);
  chppMutexDeinit(&context->mutex);
}

static enum ChppLinkErrorCode send(void *linkContext, size_t len) {
  struct ChppLinuxLinkState *context =
      (struct ChppLinuxLinkState *)(linkContext);
  bool success = false;
  chppMutexLock(&context->mutex);
  if (context->bufLen != 0) {
    CHPP_LOGE("Failed to send data - link layer busy");
  } else if (!context->isLinkActive) {
    success = false;
  } else {
    success = true;
    context->bufLen = len;
  }
  chppMutexUnlock(&context->mutex);

  if (success) {
    chppNotifierSignal(&context->notifier, SIGNAL_DATA);
  }

  return success ? CHPP_LINK_ERROR_NONE_QUEUED : CHPP_LINK_ERROR_BUSY;
}

static void doWork(void *linkContext, uint32_t signal) {
  UNUSED_VAR(linkContext);
  UNUSED_VAR(signal);
}

static void reset(void *linkContext) {
  struct ChppLinuxLinkState *context =
      (struct ChppLinuxLinkState *)(linkContext);
  deinit(context);
  init(context, context->transportContext);
}

static struct ChppLinkConfiguration getConfig(void *linkContext) {
  UNUSED_VAR(linkContext);
  const struct ChppLinkConfiguration config = {
      .txBufferLen = CHPP_LINUX_LINK_TX_MTU_BYTES,
      .rxBufferLen = CHPP_LINUX_LINK_RX_MTU_BYTES,
  };
  return config;
}

static uint8_t *getTxBuffer(void *linkContext) {
  struct ChppLinuxLinkState *context =
      (struct ChppLinuxLinkState *)(linkContext);
  return &context->buf[0];
}

const struct ChppLinkApi gLinuxLinkApi = {
    .init = &init,
    .deinit = &deinit,
    .send = &send,
    .doWork = &doWork,
    .reset = &reset,
    .getConfig = &getConfig,
    .getTxBuffer = &getTxBuffer,
};

const struct ChppLinkApi *getLinuxLinkApi(void) {
  return &gLinuxLinkApi;
}
