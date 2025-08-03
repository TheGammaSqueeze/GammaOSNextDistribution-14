#!/usr/bin/env python3
#
# Copyright 2021, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Unittests for atest_gcp_utils."""

import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock

from atest import constants
from atest.logstorage import atest_gcp_utils


class AtestGcpUtilsUnittests(unittest.TestCase):
  """Unit tests for atest_gcp_utils.py"""

  @mock.patch.object(atest_gcp_utils, '_prepare_data')
  @mock.patch.object(atest_gcp_utils, 'fetch_credential')
  def test_do_upload_flow(self, mock_request, mock_prepare):
    """test do_upload_flow method."""
    fake_extra_args = {}
    fake_creds = mock.Mock()
    fake_creds.token_response = {'access_token': 'fake_token'}
    mock_request.return_value = fake_creds
    fake_inv = {'invocationId': 'inv_id'}
    fake_workunit = {'id': 'workunit_id'}
    fake_local_build_id = 'L1234567'
    fake_build_target = 'build_target'
    mock_prepare.return_value = (
        fake_inv,
        fake_workunit,
        fake_local_build_id,
        fake_build_target,
    )
    constants.TOKEN_FILE_PATH = tempfile.NamedTemporaryFile().name
    creds, inv = atest_gcp_utils.do_upload_flow(fake_extra_args)
    self.assertEqual(fake_creds, creds)
    self.assertEqual(fake_inv, inv)
    self.assertEqual(
        fake_extra_args[constants.INVOCATION_ID], fake_inv['invocationId']
    )
    self.assertEqual(
        fake_extra_args[constants.WORKUNIT_ID], fake_workunit['id']
    )
    self.assertEqual(
        fake_extra_args[constants.LOCAL_BUILD_ID], fake_local_build_id
    )
    self.assertEqual(fake_extra_args[constants.BUILD_TARGET], fake_build_target)

    mock_request.return_value = None
    creds, inv = atest_gcp_utils.do_upload_flow(fake_extra_args)
    self.assertEqual(None, creds)
    self.assertEqual(None, inv)

  @mock.patch.object(atest_gcp_utils.GCPHelper, 'get_credential_with_auth_flow')
  def test_fetch_credential_with_request_option(
      self, mock_get_credential_with_auth_flow
  ):
    """test fetch_credential method."""
    constants.CREDENTIAL_FILE_NAME = 'cred_file'
    constants.GCP_ACCESS_TOKEN = 'access_token'
    tmp_folder = tempfile.mkdtemp()
    not_upload_file = os.path.join(tmp_folder, constants.DO_NOT_UPLOAD)

    atest_gcp_utils.fetch_credential(
        tmp_folder, {constants.REQUEST_UPLOAD_RESULT: True}
    )
    self.assertEqual(1, mock_get_credential_with_auth_flow.call_count)
    self.assertFalse(os.path.exists(not_upload_file))

    atest_gcp_utils.fetch_credential(
        tmp_folder, {constants.REQUEST_UPLOAD_RESULT: True}
    )
    self.assertEqual(2, mock_get_credential_with_auth_flow.call_count)
    self.assertFalse(os.path.exists(not_upload_file))

  @mock.patch.object(atest_gcp_utils.GCPHelper, 'get_credential_with_auth_flow')
  def test_fetch_credential_with_disable_option(
      self, mock_get_credential_with_auth_flow
  ):
    """test fetch_credential method."""
    constants.CREDENTIAL_FILE_NAME = 'cred_file'
    constants.GCP_ACCESS_TOKEN = 'access_token'
    tmp_folder = tempfile.mkdtemp()
    not_upload_file = os.path.join(tmp_folder, constants.DO_NOT_UPLOAD)

    atest_gcp_utils.fetch_credential(
        tmp_folder, {constants.DISABLE_UPLOAD_RESULT: True}
    )
    self.assertTrue(os.path.exists(not_upload_file))
    self.assertEqual(0, mock_get_credential_with_auth_flow.call_count)

    atest_gcp_utils.fetch_credential(
        tmp_folder, {constants.DISABLE_UPLOAD_RESULT: True}
    )
    self.assertEqual(0, mock_get_credential_with_auth_flow.call_count)

  @mock.patch.object(atest_gcp_utils.GCPHelper, 'get_credential_with_auth_flow')
  def test_fetch_credential_no_upload_option(
      self, mock_get_credential_with_auth_flow
  ):
    """test fetch_credential method."""
    constants.CREDENTIAL_FILE_NAME = 'cred_file'
    constants.GCP_ACCESS_TOKEN = 'access_token'
    tmp_folder = tempfile.mkdtemp()
    not_upload_file = os.path.join(tmp_folder, constants.DO_NOT_UPLOAD)
    fake_cred_file = os.path.join(tmp_folder, constants.CREDENTIAL_FILE_NAME)

    # mock cred file not exist, and not_upload file exists.
    if os.path.exists(fake_cred_file):
      os.remove(fake_cred_file)
    if os.path.exists(not_upload_file):
      os.remove(not_upload_file)
    Path(not_upload_file).touch()

    atest_gcp_utils.fetch_credential(tmp_folder, dict())
    self.assertEqual(0, mock_get_credential_with_auth_flow.call_count)
    self.assertTrue(os.path.exists(not_upload_file))

    # mock cred file exists, and not_upload_file not exist.
    if os.path.exists(fake_cred_file):
      os.remove(fake_cred_file)
    Path(fake_cred_file).touch()
    if os.path.exists(not_upload_file):
      os.remove(not_upload_file)

    atest_gcp_utils.fetch_credential(tmp_folder, dict())
    # TODO(b/275113186): Change back to assertEqual 1 and assertFalse after
    #  switch back to default not upload.
    self.assertEqual(0, mock_get_credential_with_auth_flow.call_count)
    self.assertTrue(os.path.exists(not_upload_file))
