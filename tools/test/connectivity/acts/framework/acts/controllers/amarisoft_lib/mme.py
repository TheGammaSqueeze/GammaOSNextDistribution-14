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

from acts.controllers.amarisoft_lib import amarisoft_constants as const
from acts.controllers.amarisoft_lib import amarisoft_client


class MmeFunctions():
  """Utilities for Amarisoft's MME Remote API.

  Attributes:
    remote: An amarisoft client.
  """

  def __init__(self, remote: amarisoft_client.AmariSoftClient):
    self.remote = remote

  def pws_write(self, local_id: str, n50: bool = False):
    """Broadcasts emergency alert message.

    Args:
      local_id: ID of the message as defined by local identifier in MME
        configuration file.
      n50: If True, N50 interface is used, otherwise SBC interface is used. (see TS 23.041)
    """
    msg = {}
    msg['message'] = 'pws_write'
    msg['local_id'] = local_id
    msg['nf'] = n50
    dump_msg = json.dumps(msg)
    logging.debug('pws_write dump msg = %s', dump_msg)
    head, body = self.remote.send_message(const.PortNumber.URI_MME, dump_msg)
    self.remote.verify_response('pws_write', head, body)

  def pws_kill(self, local_id: str, n50: bool = False):
    """Stops broadcasts emergency alert message.

    Args:
      local_id: ID of the message as defined by local identifier in MME
        configuration file.
      n50: If True, N50 interface is used, otherwise SBC interface is used. (see TS 23.041)
    """
    msg = {}
    msg['message'] = 'pws_kill'
    msg['local_id'] = local_id
    msg['nf'] = n50
    dump_msg = json.dumps(msg)
    logging.debug('pws_kill dump msg = %s', dump_msg)
    head, body = self.remote.send_message(const.PortNumber.URI_MME, dump_msg)
    self.remote.verify_response('pws_kill', head, body)

  def ue_del(self, imsi: str):
    """Remove UE from the UE database and force disconnect if necessary.

    Args:
      imsi: IMSI of the UE to delete.
    """
    msg = {}
    msg['message'] = 'ue_del'
    msg['imsi'] = imsi
    dump_msg = json.dumps(msg)
    logging.debug('ue_del dump msg = %s', dump_msg)
    head, body = self.remote.send_message(const.PortNumber.URI_MME, dump_msg)
    self.remote.verify_response('ue_del', head, body)
