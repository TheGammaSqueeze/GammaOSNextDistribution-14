#!/usr/bin/env python3
#
#   Copyright 2023 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#           http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from acts import logger as acts_logger

logger = acts_logger.create_logger()


def on_emm_registered(callback):
    """Registers a callback to watch for EMM attach events.

    Args:
        callback: a callback to be invoked on EMM attach events.

    Returns:
        cancel: a callback to deregister the event watcher.
    """
    from rs_mrt.testenvironment.signaling.sri.nas.eps.pubsub import EmmAttachPub
    from rs_mrt.testenvironment.signaling.sri.nas.eps import EmmRegistrationState

    def wrapped(msg):
        logger.debug("CMX received EMM registration state: {}".format(
            msg.registration_state))
        if msg.registration_state in (
                EmmRegistrationState.COMBINED_REGISTERED, ):
            callback()

    sub = EmmAttachPub.multi_subscribe(callback=wrapped)

    return lambda: sub.unsubscribe()


def on_mm5g_registered(callback):
    """Registers a callback to watch for MM5G register events.

    Args:
        callback: a callback to be invoked on MM5G register events.

    Returns:
        cancel: a callback to deregister the event watcher.
    """
    from rs_mrt.testenvironment.signaling.sri.nas.fivegs.pubsub import (
        Mm5gRegistrationPub)
    from rs_mrt.testenvironment.signaling.sri.nas.fivegs import (
        Mm5gRegistrationState)

    def wrapped(msg):
        logger.info("CMX received MM registration state: {}".format(
            msg.registration_state))
        if msg.registration_state in (
                Mm5gRegistrationState.REGISTERED_3GPP_ONLY, ):
            callback()

    sub = Mm5gRegistrationPub.multi_subscribe(callback=wrapped)

    return lambda: sub.unsubscribe()


def on_fiveg_pdu_session_activate(callback):
    """Registers a callback to watch for 5G PDU session to become active.

    Args:
        callback: a callback to be invoked on 5G PDU session activate events.

    Returns:
        cancel: a callback to deregister the event watcher.
    """
    from rs_mrt.testenvironment.signaling.sri.nas.fivegs.pubsub import (
        FivegsPduSessionPub)
    from rs_mrt.testenvironment.signaling.sri.nas.fivegs import (
        FivegsPduSessionStatus)

    def wrapped(msg):
        logger.info("CMX received 5G PDU state: {}".format(
            msg.pdu_session_status))
        if msg.pdu_session_status in (FivegsPduSessionStatus.ACTIVE, ):
            callback()

    sub = FivegsPduSessionPub.multi_subscribe(callback=wrapped)

    return lambda: sub.unsubscribe()
