# Copyright (c) 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import

import array
import logging
import multiprocessing
import struct
import unittest
from unittest.mock import patch

import common

from autotest_lib.client.cros.cellular.mbim_compliance import mbim_channel
from autotest_lib.client.cros.cellular.mbim_compliance import mbim_errors


class MBIMChannelTestCase(unittest.TestCase):
    """ Test cases for the MBIMChannel class. """

    def setUp(self):
        # Arguments passed to MBIMChannel. Irrelevant for these tests, mostly.
        self._device = None
        self._interface_number = 0
        self._interrupt_endpoint_address = 0x01
        self._in_buffer_size = 100

        self._setup_mock_subprocess()

        patcher = patch('multiprocessing.Queue')
        self._mock_request_queue = patcher.start()
        self.addCleanup(patcher.stop)

        self._channel._request_queue = self._mock_request_queue

        # On the other hand, just grab the real response queue.
        self._response_queue = self._channel._response_queue

        # Decrease timeouts to small values to speed up tests.
        self._channel.FRAGMENT_TIMEOUT_S = 0.2
        self._channel.TRANSACTION_TIMEOUT_S = 0.5


    def tearDown(self):
        self._channel.close()


    def _setup_mock_subprocess(self):
        """
        Setup long-term expectations on the mocked out subprocess.

        These expectations are only met when |self._channel.close| is called in
        |tearDown|.

        """
        patcher = patch.object(multiprocessing, 'Process')
        mock_process = patcher.start()
        self.addCleanup(patcher.stop)
        mock_process.return_value = mock_process

        # Each API call into MBIMChannel results in an aliveness ping to the
        # subprocess.
        # Finally, when |self._channel| is destructed, it will attempt to
        # terminate the |mock_process|, with increasingly drastic actions.
        mock_process.is_alive.return_value = True

        self._channel = mbim_channel.MBIMChannel(
                self._device,
                self._interface_number,
                self._interrupt_endpoint_address,
                self._in_buffer_size,
                mock_process)


    def test_creation(self):
        """ A trivial test that we mocked out the |Process| class correctly. """
        self._setup_mock_subprocess()


    def test_unfragmented_packet_successful(self):
        """ Test that we can synchronously send an unfragmented packet. """
        packet = self._get_unfragmented_packet(1)
        response_packet = self._get_unfragmented_packet(1)
        self._expect_transaction([packet], [response_packet])
        self._verify_transaction_successful([packet], [response_packet])


    def test_unfragmented_packet_timeout(self):
        """ Test the case when an unfragmented packet receives no response. """
        packet = self._get_unfragmented_packet(1)
        self._expect_transaction([packet])
        self._verify_transaction_failed([packet])


    def test_single_fragment_successful(self):
        """ Test that we can synchronously send a fragmented packet. """
        fragment = self._get_fragment(1, 1, 0)
        response_fragment = self._get_fragment(1, 1, 0)
        self._expect_transaction([fragment], [response_fragment])
        self._verify_transaction_successful([fragment], [response_fragment])


    def test_single_fragment_timeout(self):
        """ Test the case when a fragmented packet receives no response. """
        fragment = self._get_fragment(1, 1, 0)
        self._expect_transaction([fragment])
        self._verify_transaction_failed([fragment])


    def test_single_fragment_corrupted_reply(self):
        """ Test the case when the response has a corrupted fragment header. """
        fragment = self._get_fragment(1, 1, 0)
        response_fragment = self._get_fragment(1, 1, 0)
        response_fragment = response_fragment[:len(response_fragment)-1]
        self._expect_transaction([fragment], [response_fragment])
        self._verify_transaction_failed([fragment])


    def test_multiple_fragments_successful(self):
        """ Test that we can send/recieve multi-fragment packets. """
        fragment_0 = self._get_fragment(1, 2, 0)
        fragment_1 = self._get_fragment(1, 2, 1)
        response_fragment_0 = self._get_fragment(1, 2, 0)
        response_fragment_1 = self._get_fragment(1, 2, 1)
        self._expect_transaction([fragment_0, fragment_1],
                                 [response_fragment_0, response_fragment_1])
        self._verify_transaction_successful(
                [fragment_0, fragment_1],
                [response_fragment_0, response_fragment_1])


    def test_multiple_fragments_incorrect_total_fragments(self):
        """ Test the case when one of the fragment reports incorrect total. """
        fragment = self._get_fragment(1, 1, 0)
        response_fragment_0 = self._get_fragment(1, 2, 0)
        # total_fragment should have been 2, but is 99.
        response_fragment_1 = self._get_fragment(1, 99, 1)
        self._expect_transaction([fragment],
                                 [response_fragment_0, response_fragment_1])
        self._verify_transaction_failed([fragment])


    def test_multiple_fragments_reordered_reply_1(self):
        """ Test the case when the first fragemnt reports incorrect index. """
        fragment = self._get_fragment(1, 1, 0)
        # Incorrect first fragment number.
        response_fragment = self._get_fragment(1, 2, 1)
        self._expect_transaction([fragment], [response_fragment])
        self._verify_transaction_failed([fragment])


    def test_multiple_fragments_reordered_reply_2(self):
        """ Test the case when a follow up fragment reports incorrect index. """
        fragment = self._get_fragment(1, 1, 0)
        response_fragment_0 = self._get_fragment(1, 2, 0)
        # Incorrect second fragment number.
        response_fragment_1 = self._get_fragment(1, 2, 99)
        self._expect_transaction([fragment],
                                 [response_fragment_0, response_fragment_1])
        self._verify_transaction_failed([fragment])


    def test_multiple_fragments_insufficient_reply_timeout(self):
        """ Test the case when we recieve only part of the response. """
        fragment = self._get_fragment(1, 1, 0)
        # The second fragment will never arrive.
        response_fragment_0 = self._get_fragment(1, 2, 0)
        self._expect_transaction([fragment], [response_fragment_0])
        self._verify_transaction_successful([fragment], [response_fragment_0])


    def test_unfragmented_packet_notification(self):
        """ Test the case when a notification comes before the response. """
        packet = self._get_unfragmented_packet(1)
        response = self._get_unfragmented_packet(1)
        notification = self._get_unfragmented_packet(0)
        self._expect_transaction([packet], [notification, response])
        self._verify_transaction_successful([packet], [response])
        self.assertEqual([[notification]],
                         self._channel.get_outstanding_packets())


    def test_fragmented_notification(self):
        """ Test the case when a fragmented notification preceeds response. """
        packet_fragment_0 = self._get_fragment(1, 2, 0)
        packet_fragment_1 = self._get_fragment(1, 2, 1)
        response_fragment_0 = self._get_fragment(1, 2, 0)
        response_fragment_1 = self._get_fragment(1, 2, 1)
        notification_0_fragment_0 = self._get_fragment(0, 2, 0)
        notification_0_fragment_1 = self._get_fragment(0, 2, 1)
        notification_1_fragment_0 = self._get_fragment(99, 2, 0)
        notification_1_fragment_1 = self._get_fragment(99, 2, 1)

        self._expect_transaction(
                [packet_fragment_0, packet_fragment_1],
                [notification_0_fragment_0, notification_0_fragment_1,
                 notification_1_fragment_0, notification_1_fragment_1,
                 response_fragment_0, response_fragment_1])
        self._verify_transaction_successful(
                [packet_fragment_0, packet_fragment_1],
                [response_fragment_0, response_fragment_1])
        self.assertEqual(
                [[notification_0_fragment_0, notification_0_fragment_1],
                 [notification_1_fragment_0, notification_1_fragment_1]],
                self._channel.get_outstanding_packets())


    def test_multiple_packets_rollover_notification(self):
        """
        Test the case when we receive incomplete response, followed by
        fragmented notifications.

        We have to be smart enough to realize that the incorrect fragment
        recieved at the end of the response belongs to the next notification
        instead.

        """
        packet = self._get_fragment(1, 1, 0)
        # The second fragment never comes, instead we get a notification
        # fragment.
        response_fragment_0 = self._get_fragment(1, 2, 0)
        notification_0_fragment_0 = self._get_fragment(0, 2, 0)
        notification_0_fragment_1 = self._get_fragment(0, 2, 1)
        notification_1_fragment_0 = self._get_fragment(99, 2, 0)
        notification_1_fragment_1 = self._get_fragment(99, 2, 1)

        self._expect_transaction(
                [packet],
                [response_fragment_0,
                 notification_0_fragment_0, notification_0_fragment_1,
                 notification_1_fragment_0, notification_1_fragment_1])
        self._verify_transaction_successful(
                [packet],
                [response_fragment_0])
        self.assertEqual(
                [[notification_0_fragment_0, notification_0_fragment_1],
                 [notification_1_fragment_0, notification_1_fragment_1]],
                self._channel.get_outstanding_packets())


    def test_data(self):
        """ Test that data is transferred transaperntly. """
        packet = self._get_unfragmented_packet(1)
        packet.fromlist([0xFF, 0xFF, 0xFF, 0xFF, 0xDD, 0xDD, 0xDD, 0xDD])
        response_packet = self._get_unfragmented_packet(1)
        response_packet.fromlist([0xAA, 0xAA, 0xBB, 0xBB])
        self._expect_transaction([packet], [response_packet])
        self._verify_transaction_successful([packet], [response_packet])


    def test_flush_successful(self):
        """ Test that flush clears all queues. """
        packet = self._get_unfragmented_packet(1)
        response = self._get_unfragmented_packet(1)
        notification_1 = self._get_fragment(0, 1, 0)
        self._response_queue.put_nowait(notification_1)
        self._mock_request_queue.qsize.return_value = 1
        self._mock_request_queue.empty.return_value = False

        def put_response():
            """Side effect for mock"""
            self._response_queue.put_nowait(response)

        self._mock_request_queue.empty.side_effect = [None, put_response]
        self._channel.flush()
        self.assertEqual(0, self._response_queue.qsize())


    def test_flush_failed(self):
        """ Test the case when the request queue fails to empty out. """
        packet = self._get_unfragmented_packet(1)
        self._mock_request_queue.qsize.return_value = 1
        self._mock_request_queue.empty.return_value = False
        self.assertRaises(
                mbim_errors.MBIMComplianceChannelError,
                self._channel.flush)


    def _queue_responses(self, responses):
        """ Helper method for |_expect_transaction|. Do not use directly. """
        for response in responses:
            self._response_queue.put_nowait(response)


    def _expect_transaction(self, requests, responses=None):
        """
        Helper method to setup expectations on the queues.

        @param requests: A list of packets to expect on the |_request_queue|.
        @param respones: An optional list of packets to respond with after the
                last request.

        """

        last_request = requests[len(requests) - 1]
        if responses:

            def put_if_last_request(msg):
                """Side effect for mock"""
                if msg == last_request:
                    self._queue_responses(responses)

            self._mock_request_queue.put_nowait.side_effect = put_if_last_request


    def _verify_transaction_successful(self, requests, responses):
        """
        Helper method to assert that the transaction was successful.

        @param requests: List of packets sent.
        @param responses: List of packets expected back.
        """
        self.assertEqual(responses,
                         self._channel.bidirectional_transaction(*requests))


    def _verify_transaction_failed(self, requests):
        """
        Helper method to assert that the transaction failed.

        @param requests: List of packets sent.

        """
        self.assertRaises(mbim_errors.MBIMComplianceChannelError,
                          self._channel.bidirectional_transaction,
                          *requests)


    def _get_unfragmented_packet(self, transaction_id):
        """ Creates a packet that has no fragment header. """
        packet_format = '<LLL' # This does not contain a fragment header.
        packet = self._create_buffer(struct.calcsize(packet_format))
        struct.pack_into(packet_format,
                         packet,
                         0,
                         0x00000000,  # 0x0 does not need fragments.
                         struct.calcsize(packet_format),
                         transaction_id)
        return packet


    def _get_fragment(self, transaction_id, total_fragments, current_fragment):
        """ Creates a fragment with the given fields. """
        fragment_header_format = '<LLLLL'
        message_type = 0x00000003  # MBIM_COMMAND_MSG has fragments.
        fragment = self._create_buffer(struct.calcsize(fragment_header_format))
        struct.pack_into(fragment_header_format,
                         fragment,
                         0,
                         message_type,
                         struct.calcsize(fragment_header_format),
                         transaction_id,
                         total_fragments,
                         current_fragment)
        return fragment


    def _create_buffer(self, size):
        """ Create an array of the give size initialized to 0x00. """
        return array.array('B', b'\x00' * size)


if __name__ == '__main__':
    logging.basicConfig(level=logging.DEBUG)
    unittest.main()
