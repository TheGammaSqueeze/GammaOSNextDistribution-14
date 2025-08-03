#!/usr/bin/env python3
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Unit test for pubsub_utils.py"""

from __future__ import print_function
import os
import unittest
from unittest.mock import patch
from unittest.mock import MagicMock

import common

# TODO(crbug.com/1050892): The unittests rely on apiclient in chromite.
import autotest_lib.utils.frozen_chromite  # pylint: disable=unused-import

from apiclient import discovery
from oauth2client.client import ApplicationDefaultCredentialsError
from oauth2client.client import GoogleCredentials
from googleapiclient.errors import UnknownApiNameOrVersion

from autotest_lib.site_utils import pubsub_utils

_TEST_CLOUD_SERVICE_ACCOUNT_FILE = '/tmp/test-credential'


class MockedPubSub(object):
    """A mocked PubSub handle."""
    def __init__(self, test, topic, msg, retry, ret_val=None,
                 raise_except=False):
        self.test = test
        self.topic = topic
        self.msg = msg
        self.retry = retry
        self.ret_val = ret_val
        self.raise_except = raise_except

    def projects(self):
        """Mocked PubSub projects."""
        return self

    def topics(self):
        """Mocked PubSub topics."""
        return self

    def publish(self, topic, body):
        """Mocked PubSub publish method.

        @param topic: PubSub topic string.
        @param body: PubSub notification body.
        """
        self.test.assertEquals(self.topic, topic)
        self.test.assertEquals(self.msg, body['messages'][0])
        return self

    def execute(self, num_retries):
        """Mocked PubSub execute method.

        @param num_retries: Number of retries.
        """
        self.test.assertEquals(self.retry, num_retries)
        if self.raise_except:
            raise Exception()
        return self.ret_val


def _create_sample_message():
    """Creates a sample pubsub message."""
    msg_payload = {'data': 'sample data'}
    msg_attributes = {}
    msg_attributes['var'] = 'value'
    msg_payload['attributes'] = msg_attributes

    return msg_payload


class PubSubTests(unittest.TestCase):
    """Tests for pubsub related functios."""

    def setUp(self):
        patcher = patch.object(os.path, 'isfile')
        self.isfile_mock = patcher.start()
        self.addCleanup(patcher.stop)
        creds_patcher = patch.object(GoogleCredentials, 'from_stream')
        self.creds_mock = creds_patcher.start()
        self.addCleanup(creds_patcher.stop)

    def test_pubsub_with_no_service_account(self):
        """Test getting the pubsub service"""
        with self.assertRaises(pubsub_utils.PubSubException):
            pubsub_utils.PubSubClient()

    def test_pubsub_with_non_existing_service_account(self):
        """Test getting the pubsub service"""
        self.isfile_mock.return_value = False
        with self.assertRaises(pubsub_utils.PubSubException):
            pubsub_utils.PubSubClient(_TEST_CLOUD_SERVICE_ACCOUNT_FILE)
        self.isfile_mock.assert_called_with(_TEST_CLOUD_SERVICE_ACCOUNT_FILE)

    def test_pubsub_with_corrupted_service_account(self):
        """Test pubsub with corrupted service account."""

        self.isfile_mock.return_value = True
        self.creds_mock.side_effect = ApplicationDefaultCredentialsError

        with self.assertRaises(pubsub_utils.PubSubException):
            pubsub_utils.PubSubClient(_TEST_CLOUD_SERVICE_ACCOUNT_FILE)

        self.creds_mock.assert_called_with(_TEST_CLOUD_SERVICE_ACCOUNT_FILE)
        self.isfile_mock.assert_called_with(_TEST_CLOUD_SERVICE_ACCOUNT_FILE)

    def test_pubsub_with_invalid_service_account(self):
        """Test pubsubwith invalid service account."""
        self.isfile_mock.return_value = True
        credentials = MagicMock(GoogleCredentials)
        self.creds_mock.return_value = credentials

        credentials.create_scoped_required.return_value = True
        credentials.create_scoped.return_value = credentials

        with patch.object(discovery, 'build') as discovery_mock:
            discovery_mock.side_effect = UnknownApiNameOrVersion

            with self.assertRaises(pubsub_utils.PubSubException):
                msg = _create_sample_message()
                pubsub_client = pubsub_utils.PubSubClient(
                        _TEST_CLOUD_SERVICE_ACCOUNT_FILE)
                pubsub_client.publish_notifications('test_topic', [msg])

            credentials.create_scoped.assert_called_with(
                    pubsub_utils.PUBSUB_SCOPES)
            discovery_mock.assert_called_with(pubsub_utils.PUBSUB_SERVICE_NAME,
                                              pubsub_utils.PUBSUB_VERSION,
                                              credentials=credentials)
        self.creds_mock.assert_called_with(_TEST_CLOUD_SERVICE_ACCOUNT_FILE)
        self.isfile_mock.assert_called_with(_TEST_CLOUD_SERVICE_ACCOUNT_FILE)

    def test_publish_notifications(self):
        """Test getting the pubsub service"""
        self.isfile_mock.return_value = True
        credentials = MagicMock(GoogleCredentials)
        self.creds_mock.return_value = credentials

        credentials.create_scoped_required.return_value = True
        credentials.create_scoped.return_value = credentials

        with patch.object(discovery, 'build') as discovery_mock:
            msg = _create_sample_message()
            discovery_mock.return_value = MockedPubSub(
                    self,
                    'test_topic',
                    msg,
                    pubsub_utils.DEFAULT_PUBSUB_NUM_RETRIES,
                    # use tuple ('123') instead of list just for easy to
                    # write the test.
                    ret_val={'messageIds': ('123')})

            pubsub_client = pubsub_utils.PubSubClient(
                    _TEST_CLOUD_SERVICE_ACCOUNT_FILE)
            msg_ids = pubsub_client.publish_notifications('test_topic', [msg])
            self.assertEquals(('123'), msg_ids)

            credentials.create_scoped.assert_called_with(
                    pubsub_utils.PUBSUB_SCOPES)
            discovery_mock.assert_called_with(pubsub_utils.PUBSUB_SERVICE_NAME,
                                              pubsub_utils.PUBSUB_VERSION,
                                              credentials=credentials)
        self.creds_mock.assert_called_with(_TEST_CLOUD_SERVICE_ACCOUNT_FILE)
        self.isfile_mock.assert_called_with(_TEST_CLOUD_SERVICE_ACCOUNT_FILE)


if __name__ == '__main__':
    unittest.main()
