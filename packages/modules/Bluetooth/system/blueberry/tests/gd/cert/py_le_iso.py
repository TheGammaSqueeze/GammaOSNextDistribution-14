#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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

import logging

from blueberry.tests.gd.cert.capture import Capture
from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.event_stream import EventStream, IEventStream
from blueberry.tests.gd.cert.event_stream import FilteringEventStream
from blueberry.tests.gd.cert.matchers import IsoMatchers
from blueberry.tests.gd.cert.truth import assertThat
from datetime import timedelta
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.iso import facade_pb2 as iso_facade_pb2


class CisTestParameters():

    def __init__(self, cis_id, nse, max_sdu_m_to_s, max_sdu_s_to_m, max_pdu_m_to_s, max_pdu_s_to_m, phy_m_to_s,
                 phy_s_to_m, bn_m_to_s, bn_s_to_m):
        self.cis_id = cis_id
        self.nse = nse
        self.max_sdu_m_to_s = max_sdu_m_to_s
        self.max_sdu_s_to_m = max_sdu_s_to_m
        self.max_pdu_m_to_s = max_pdu_m_to_s
        self.max_pdu_s_to_m = max_pdu_s_to_m
        self.phy_m_to_s = phy_m_to_s
        self.phy_s_to_m = phy_s_to_m
        self.bn_m_to_s = bn_m_to_s
        self.bn_s_to_m = bn_s_to_m


class PyLeIsoStream(IEventStream):

    def __init__(self, device, cis_handle, iso_data_stream):
        self._device = device
        self._cis_handle = cis_handle
        self._le_iso_data_stream = iso_data_stream
        self._our_le_iso_cis_view = FilteringEventStream(
            self._le_iso_data_stream, IsoMatchers.PacketPayloadWithMatchingCisHandle(self._cis_handle))

    def get_event_queue(self):
        return self._our_le_iso_cis_view.get_event_queue()

    def send(self, payload):
        self._device.iso.SendIsoPacket(iso_facade_pb2.IsoPacket(handle=self._cis_handle, payload=payload))


class PyLeIso(Closable):
    """
        Abstraction for iso tasks and GRPC calls
    """

    _iso_event_stream = None

    def __init__(self, device):
        logging.info("DUT: Init")
        self._device = device
        self._device.wait_channel_ready()
        self._iso_event_stream = EventStream(self._device.iso.FetchIsoEvents(empty_proto.Empty()))
        self._iso_data_stream = EventStream(self._device.iso.FetchIsoData(empty_proto.Empty()))

    def close(self):
        if self._iso_event_stream is not None:
            safeClose(self._iso_event_stream)
        else:
            logging.info("DUT: ISO Event Stream is None!")
        if self._iso_data_stream is not None:
            safeClose(self._iso_data_stream)
        else:
            logging.info("DUT: ISO Data Stream is None!")

        logging.info("DUT: close")

    def le_set_cig_parameters(self, cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m, peripherals_clock_accuracy,
                              packing, framing, max_transport_latency_m_to_s, max_transport_latency_s_to_m, cis_id,
                              max_sdu_m_to_s, max_sdu_s_to_m, phy_m_to_s, phy_s_to_m, rtn_m_to_s, rtn_s_to_m):

        resp = self._device.iso.LeSetCigParameters(
            iso_facade_pb2.LeSetCigParametersRequest(
                cig_id=cig_id,
                sdu_interval_m_to_s=sdu_interval_m_to_s,
                sdu_interval_s_to_m=sdu_interval_s_to_m,
                peripherals_clock_accuracy=peripherals_clock_accuracy,
                packing=packing,
                framing=framing,
                max_transport_latency_m_to_s=max_transport_latency_m_to_s,
                max_transport_latency_s_to_m=max_transport_latency_s_to_m,
                cis_id=cis_id,
                max_sdu_m_to_s=max_sdu_m_to_s,
                max_sdu_s_to_m=max_sdu_s_to_m,
                phy_m_to_s=phy_m_to_s,
                phy_s_to_m=phy_s_to_m,
                rtn_m_to_s=rtn_m_to_s,
                rtn_s_to_m=rtn_s_to_m))

    def le_set_cig_parameters_test(self, cig_id, sdu_interval_m_to_s, sdu_interval_s_to_m, ft_m_to_s, ft_s_to_m,
                                   iso_interval, peripherals_clock_accuracy, packing, framing,
                                   max_transport_latency_m_to_s, max_transport_latency_s_to_m, cis_configs):
        configs = []
        for cc in cis_configs:
            configs.append(
                iso_facade_pb2.LeSetCigParametersTestRequest.LeCisParametersTestConfig(
                    cis_id=cc.cis_id,
                    nse=cc.nse,
                    max_sdu_m_to_s=cc.max_sdu_m_to_s,
                    max_sdu_s_to_m=cc.max_sdu_s_to_m,
                    max_pdu_m_to_s=cc.max_pdu_m_to_s,
                    max_pdu_s_to_m=cc.max_pdu_s_to_m,
                    phy_m_to_s=cc.phy_m_to_s,
                    phy_s_to_m=cc.phy_s_to_m,
                    bn_m_to_s=cc.bn_m_to_s,
                    bn_s_to_m=cc.bn_s_to_m,
                ))

        resp = self._device.iso.LeSetCigParametersTest(
            iso_facade_pb2.LeSetCigParametersTestRequest(
                cig_id=cig_id,
                sdu_interval_m_to_s=sdu_interval_m_to_s,
                sdu_interval_s_to_m=sdu_interval_s_to_m,
                ft_m_to_s=ft_m_to_s,
                ft_s_to_m=ft_s_to_m,
                iso_interval=iso_interval,
                peripherals_clock_accuracy=peripherals_clock_accuracy,
                packing=packing,
                framing=framing,
                max_transport_latency_m_to_s=max_transport_latency_m_to_s,
                max_transport_latency_s_to_m=max_transport_latency_s_to_m,
                cis_configs=configs))

    def wait_le_set_cig_parameters_complete(self):
        set_cig_params_complete_capture = PyLeIso.IsoCigComplete(iso_facade_pb2.IsoMsgType.ISO_PARAMETERS_SET_COMPLETE)

        assertThat(self._iso_event_stream).emits(set_cig_params_complete_capture, timeout=timedelta(seconds=5))
        return set_cig_params_complete_capture.get()

    @staticmethod
    def IsoCigComplete(type=None):
        return Capture(lambda event: True if event.message_type == type else False, PyLeIso._extract_cis_handles)

    @staticmethod
    def _extract_cis_handles(event):
        if event is None:
            return None
        return event.cis_handle

    def le_create_cis(self, cis_and_acl_handle_array):
        handles_pairs = []
        for hp_tmp in cis_and_acl_handle_array:
            handles_pairs.append(
                iso_facade_pb2.LeCreateCisRequest.HandlePair(cis_handle=hp_tmp[0], acl_handle=hp_tmp[1]))

        self._device.iso.LeCreateCis(iso_facade_pb2.LeCreateCisRequest(handle_pair=handles_pairs))

    def wait_le_cis_established(self):
        cis_establshed_capture = PyLeIso.IsoCigEstablished(iso_facade_pb2.IsoMsgType.ISO_CIS_ESTABLISHED)
        assertThat(self._iso_event_stream).emits(cis_establshed_capture, timeout=timedelta(seconds=5))
        cis_handle = cis_establshed_capture.get()[0]
        return PyLeIsoStream(self._device, cis_handle, self._iso_data_stream)

    @staticmethod
    def IsoCigEstablished(type):
        return Capture(lambda event: True if event.message_type == type else False, PyLeIso._extract_cis_handles)
