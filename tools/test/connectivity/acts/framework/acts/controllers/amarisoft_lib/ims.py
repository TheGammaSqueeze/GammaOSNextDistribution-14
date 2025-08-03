#!/usr/bin/env python3
#
#   Copyright 2022 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import json
import logging
from typing import Any, Mapping, Optional, Union

from acts.controllers.amarisoft_lib import amarisoft_client
from acts.controllers.amarisoft_lib import amarisoft_constants as const


class ImsFunctions():
  """Utilities for Amarisoft's IMS Remote API.

  Attributes:
    remote: An amarisoft client.
  """

  def __init__(self, remote: amarisoft_client.AmariSoftClient):
    self.remote = remote

  def make_call(self,
              impi: str,
              impu: str,
              contact: str,
              sip_file: str = 'mt_call_qos.sdp',
              caller: str = 'Amarisoft',
              duration: int = 30) -> None:
    """Performs MT call from callbox to test device.

    Args:
      impi: IMPI (IP Multimedia Private identity) of user to call.
      impu: IMPU (IP Multimedia Public identity) of user to call.
      contact: Contact SIP uri of user to call.
      sip_file: Define file to use as sdp.
      caller: The number/ID is displayed as the caller.
      duration: If set, call duration in seconds (The server will close the
        dialog).
    """
    msg = {}
    msg['message'] = 'mt_call'
    msg['impi'] = impi
    msg['impu'] = impu
    msg['contact'] = contact
    msg['sip_file'] = sip_file
    msg['caller'] = caller
    msg['duration'] = duration
    dump_msg = json.dumps(msg)
    logging.debug('mt_call dump msg = %s', dump_msg)
    head, body = self.remote.send_message(const.PortNumber.URI_IMS, dump_msg)
    self.remote.verify_response('mt_call', head, body)

  def send_sms(self,
               text: str,
               impi: str,
               sender: Optional[str] = 'Amarisoft') -> None:
    """Sends SMS to assigned device which connect to Amarisoft.

    Args:
      text: SMS text to send.
      impi: IMPI (IP Multimedia Private identity) of user.
      sender: Sets SMS sender.
    """
    msg = {}
    msg['message'] = 'sms'
    msg['text'] = text
    msg['impi'] = impi
    msg['sender'] = sender
    dump_msg = json.dumps(msg)
    logging.debug('send_sms dump msg = %s', dump_msg)
    head, body = self.remote.send_message(const.PortNumber.URI_IMS, dump_msg)
    self.remote.verify_response('sms', head, body)

  def send_mms(self, filename: str, sender: str, receiver: str) -> None:
    """Sends MMS to assigned device which connect to Amarisoft.

    Args:
      filename: File name with absolute path to send. Extensions jpg, jpeg, png,
        gif and txt are supported.
      sender: IMPI (IP Multimedia Private identity) of user.
      receiver: IMPU (IP Multimedia Public identity) of user.
    """
    msg = {}
    msg['message'] = 'mms'
    msg['filename'] = filename
    msg['sender'] = sender
    msg['receiver'] = receiver
    dump_msg = json.dumps(msg)
    logging.debug('send_mms dump msg = %s', dump_msg)
    head, body = self.remote.send_message(const.PortNumber.URI_IMS, dump_msg)
    self.remote.verify_response('mms', head, body)

  def users_get(self, registered_only: bool = True) -> Mapping[str, Any]:
    """Gets users state.

    Args:
      registered_only: If set, only registered user will be dumped.

    Returns:
      The user information.
    """
    msg = {}
    msg['message'] = 'users_get'
    msg['registered_only'] = registered_only
    dump_msg = json.dumps(msg)
    logging.debug('users_get dump msg = %s', dump_msg)
    head, body = self.remote.send_message(const.PortNumber.URI_IMS, dump_msg)
    _, loaded_body = self.remote.verify_response('users_get', head, body)
    return loaded_body

  def get_impu(self, impi) -> Union[str, None]:
    """Obtains the IMPU of the target user according to IMPI.

    Args:
      impi: IMPI (IP Multimedia Private identity) of user to call. ex:
        "310260123456785@ims.mnc260.mcc310.3gppnetwork.org"

    Returns:
      The IMPU of target user.
    """
    body = self.users_get(True)
    for index in range(len(body['users'])):
      if impi in body['users'][index]['impi']:
        impu = body['users'][index]['bindings'][0]['impu'][1]
        return impu
    return None

  def get_uri(self, impi) -> Union[str, None]:
    """Obtains the URI of the target user according to IMPI.

    Args:
      impi: IMPI (IP Multimedia Private identity) of user to call. ex:
        "310260123456785@ims.mnc260.mcc310.3gppnetwork.org"

    Returns:
      The URI of target user.
    """
    body = self.users_get(True)
    for index in range(len(body['users'])):
      if impi in body['users'][index]['impi']:
        uri = body['users'][index]['bindings'][0]['uri']
        return uri
    return None
