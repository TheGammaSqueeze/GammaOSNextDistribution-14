# Copyright 2018 - The Android Open Source Project
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
"""Tests for list."""

import unittest

from unittest import mock

from acloud import errors
from acloud.internal.lib import auth
from acloud.internal.lib import cvd_runtime_config
from acloud.internal.lib import driver_test_lib
from acloud.internal.lib import gcompute_client
from acloud.internal.lib import ssh
from acloud.internal.lib import utils
from acloud.internal.lib import adb_tools
from acloud.list import list as list_instance
from acloud.list import instance
from acloud.public import config


class InstanceObject:
    """Mock to store data of instance."""

    def __init__(self, name):
        self.name = name


class ListTest(driver_test_lib.BaseDriverTest):
    """Test list."""

    def setUp(self):
        """Set up the test."""
        super().setUp()
        self.Patch(instance, "_GetElapsedTime", return_value=0)
        self.Patch(instance.RemoteInstance, "_GetZoneName")
        self.Patch(instance.RemoteInstance, "_GetProjectName")
        self.Patch(instance, "GetInstanceIP", return_value=ssh.IP())
        self.Patch(instance.RemoteInstance, "GetAdbVncPortFromSSHTunnel")
        self.Patch(adb_tools, "AdbTools")
        self.Patch(adb_tools.AdbTools, "IsAdbConnected", return_value=False)
        self.Patch(auth, "CreateCredentials")
        self.Patch(gcompute_client, "ComputeClient")
        self.Patch(gcompute_client.ComputeClient, "ListInstances")

    def testGetInstancesFromInstanceNames(self):
        """test get instances from instance names."""
        cfg = mock.MagicMock()
        instance_names = ["alive_instance1", "alive_local_instance"]

        alive_instance1 = InstanceObject("alive_instance1")
        alive_instance2 = InstanceObject("alive_instance2")
        alive_local_instance = InstanceObject("alive_local_instance")

        self.Patch(list_instance, "GetLocalInstancesByNames",
                   return_value=[alive_local_instance])
        self.Patch(list_instance, "GetRemoteInstances",
                   return_value=[alive_instance1, alive_instance2])
        instances_list = list_instance.GetInstancesFromInstanceNames(cfg, instance_names)
        instances_name_in_list = [instance_object.name for instance_object in instances_list]
        self.assertEqual(instances_name_in_list.sort(), instance_names.sort())

        instance_names = ["alive_instance1", "alive_local_instance", "alive_local_instance"]
        instances_list = list_instance.GetInstancesFromInstanceNames(cfg, instance_names)
        instances_name_in_list = [instance_object.name for instance_object in instances_list]
        self.assertEqual(instances_name_in_list.sort(), instance_names.sort())

        # test get instance from instance name error with invalid input.
        instance_names = ["miss2_local_instance", "alive_instance1"]
        miss_instance_names = ["miss2_local_instance"]
        self.assertRaisesRegex(
            errors.NoInstancesFound,
            "Did not find the following instances: %s" % ' '.join(miss_instance_names),
            list_instance.GetInstancesFromInstanceNames,
            cfg=cfg,
            instance_names=instance_names)

    def testChooseOneRemoteInstance(self):
        """test choose one remote instance from instance names."""
        cfg = mock.MagicMock()

        # Test only one instance case
        instance_names = ["cf_instance1"]
        self.Patch(list_instance, "GetCFRemoteInstances", return_value=instance_names)
        expected_instance = "cf_instance1"
        self.assertEqual(list_instance.ChooseOneRemoteInstance(cfg), expected_instance)

        # Test no instance case
        self.Patch(list_instance, "GetCFRemoteInstances", return_value=[])
        with self.assertRaises(errors.NoInstancesFound):
            list_instance.ChooseOneRemoteInstance(cfg)

        # Test two instances case.
        instance_names = ["cf_instance1", "cf_instance2"]
        choose_instance = ["cf_instance2"]
        self.Patch(list_instance, "GetCFRemoteInstances", return_value=instance_names)
        self.Patch(utils, "GetAnswerFromList", return_value=choose_instance)
        expected_instance = "cf_instance2"
        self.assertEqual(list_instance.ChooseOneRemoteInstance(cfg), expected_instance)

    def testGetLocalInstancesByNames(self):
        """test GetLocalInstancesByNames."""
        self.Patch(
            instance, "GetLocalInstanceIdByName",
            side_effect=lambda name: 1 if name == "local-instance-1" else None)
        self.Patch(instance, "GetLocalInstanceConfig",
                   return_value="path1")
        self.Patch(instance, "GetDefaultCuttlefishConfig",
                   return_value="path2")
        mock_cf_ins = mock.Mock()
        mock_cf_ins.name = "local-instance-1"
        mock_get_cf = self.Patch(list_instance,
                                 "_GetLocalCuttlefishInstances",
                                 return_value=[mock_cf_ins])
        mock_gf_ins = mock.Mock()
        mock_gf_ins.name = "local-goldfish-instance-1"
        self.Patch(instance.LocalGoldfishInstance, "GetExistingInstances",
                   return_value=[mock_gf_ins])

        ins_list = list_instance.GetLocalInstancesByNames([
            mock_cf_ins.name, "local-instance-6", mock_gf_ins.name])
        self.assertEqual([mock_cf_ins, mock_gf_ins], ins_list)
        mock_get_cf.assert_called_with([(1, "path1"), (1, "path2")])

    # pylint: disable=attribute-defined-outside-init
    def testFilterInstancesByAdbPort(self):
        """test FilterInstancesByAdbPort."""
        alive_instance1 = InstanceObject("alive_instance1")
        alive_instance1.adb_port = 1111
        alive_instance1.fullname = "device serial: 127.0.0.1:1111 alive_instance1"
        expected_instance = [alive_instance1]
        # Test to find instance by adb port number.
        self.assertEqual(
            expected_instance,
            list_instance.FilterInstancesByAdbPort(expected_instance, 1111))
        # Test for instance can't be found by adb port number.
        with self.assertRaises(errors.NoInstancesFound):
            list_instance.FilterInstancesByAdbPort(expected_instance, 2222)

    # pylint: disable=protected-access
    def testGetLocalCuttlefishInstances(self):
        """test _GetLocalCuttlefishInstances."""
        # Test getting two instance case
        id_cfg_pairs = [(1, "fake_path1"), (2, "fake_path2")]
        mock_isfile = self.Patch(list_instance.os.path, "isfile",
                                 return_value=True)

        mock_lock = mock.Mock()
        mock_lock.Lock.return_value = True
        self.Patch(instance, "GetLocalInstanceLock", return_value=mock_lock)

        local_ins = mock.MagicMock()
        local_ins.CvdStatus.return_value = True
        self.Patch(instance, "GetCuttleFishLocalInstances",
                   return_value=[local_ins])

        ins_list = list_instance._GetLocalCuttlefishInstances(id_cfg_pairs)
        self.assertEqual(2, len(ins_list))
        mock_isfile.assert_called()
        local_ins.CvdStatus.assert_called()
        self.assertEqual(2, mock_lock.Lock.call_count)
        self.assertEqual(2, mock_lock.Unlock.call_count)

        local_ins.CvdStatus.reset_mock()
        mock_lock.Lock.reset_mock()
        mock_lock.Lock.return_value = False
        mock_lock.Unlock.reset_mock()
        ins_list = list_instance._GetLocalCuttlefishInstances(id_cfg_pairs)
        self.assertEqual(0, len(ins_list))
        local_ins.CvdStatus.assert_not_called()
        self.assertEqual(2, mock_lock.Lock.call_count)
        mock_lock.Unlock.assert_not_called()

        mock_lock.Lock.reset_mock()
        mock_lock.Lock.return_value = True
        local_ins.CvdStatus.return_value = False
        ins_list = list_instance._GetLocalCuttlefishInstances(id_cfg_pairs)
        self.assertEqual(0, len(ins_list))
        self.assertEqual(2, mock_lock.Lock.call_count)
        self.assertEqual(2, mock_lock.Unlock.call_count)

    # pylint: disable=no-member
    def testPrintInstancesDetails(self):
        """test PrintInstancesDetails."""
        # Test instance Summary should be called if verbose
        self.Patch(instance.Instance, "Summary")
        cf_config = mock.MagicMock(
            x_res=728,
            y_res=728,
            dpi=240,
            instance_dir="fake_dir",
            adb_ip_port="127.0.0.1:6520",
            root_dir="root/cuttlefish_runtime"
        )
        self.Patch(cvd_runtime_config, "CvdRuntimeConfig",
                   return_value=cf_config)
        self.Patch(instance.LocalInstance, "_GetDevidInfoFromCvdStatus",
                   return_value=None)

        ins = instance.LocalInstance("fake_cf_path")
        list_instance.PrintInstancesDetails([ins], verbose=True)
        instance.Instance.Summary.assert_called_once()

        # Test Summary shouldn't be called if not verbose
        self.Patch(instance.Instance, "Summary")
        list_instance.PrintInstancesDetails([ins], verbose=False)
        instance.Instance.Summary.assert_not_called()

        # Test Summary shouldn't be called if no instance found.
        list_instance.PrintInstancesDetails([], verbose=True)
        instance.Instance.Summary.assert_not_called()

    def testRun(self):
        """test Run."""
        cfg = mock.MagicMock()
        self.Patch(config, "GetAcloudConfig", return_value=cfg)
        args = mock.MagicMock()
        # local instance
        args.local_only = True
        args.verbose = False
        self.Patch(utils, "IsSupportedPlatform", return_value=True)
        self.Patch(list_instance, "PrintInstancesDetails")
        self.Patch(instance, "GetAllLocalInstanceConfigs")
        fake_local_ins1 = "local_ins1"
        fake_local_ins2 = "local_ins2"
        fake_local_gf_ins1 = "local_gf_ins1"
        self.Patch(list_instance, "_GetLocalCuttlefishInstances",
                   return_value=[fake_local_ins1, fake_local_ins2])
        self.Patch(instance.LocalGoldfishInstance, "GetExistingInstances",
                   return_value=[fake_local_gf_ins1])
        list_instance.Run(args)
        list_instance.PrintInstancesDetails.assert_called_with(
            [fake_local_ins1, fake_local_ins2, fake_local_gf_ins1], False)

        # remote instance
        args.local_only = False
        fake_remote_ins1= "remote_ins1"
        fake_remote_ins2 = "remote_ins2"
        self.Patch(list_instance, "GetRemoteInstances",
                   return_value=[fake_remote_ins1, fake_remote_ins2])
        list_instance.Run(args)
        list_instance.PrintInstancesDetails.assert_called_with(
            [fake_local_ins1, fake_local_ins2, fake_local_gf_ins1,
             fake_remote_ins1, fake_remote_ins2], False)

    def testGetRemoteInstances(self):
        """test GetRemoteInstances."""
        fake_remote_ins1 = {"name": "fake_remote_ins1_name",
                            "creationTimestamp": "2021-01-14T13:00:00.000-07:00",
                            "status": "Active"}
        fake_remote_ins2 = {"name": "fake_remote_ins2_name",
                            "creationTimestamp": "2021-01-14T13:00:00.000-07:00",
                            "status": "Active"}
        gcompute_client.ComputeClient(None, None).ListInstances.return_value = [
            fake_remote_ins1, fake_remote_ins2]
        self.assertEqual(list_instance.GetRemoteInstances(None)[0].name,
                         instance.RemoteInstance(fake_remote_ins1).name)
        self.assertEqual(len(list_instance.GetRemoteInstances(None)), 2)

    def testGetCFRemoteInstances(self):
        """test GetCFRemoteInstances."""
        fake_remote_ins1 = {"name": "cf-fake_remote_ins1_name",
                            "avd_type": "cuttlefish",
                            "creationTimestamp": "2021-01-14T13:00:00.000-07:00",
                            "status": "Active"}
        fake_remote_ins2 = {"name": "nonecf-fake_remote_ins2_name",
                            "avd_type": "goldfish",
                            "creationTimestamp": "2021-01-14T13:00:00.000-07:00",
                            "status": "Active"}
        remote_ins_list = [instance.RemoteInstance(fake_remote_ins1),
                           instance.RemoteInstance(fake_remote_ins2)]
        self.Patch(list_instance, "GetRemoteInstances",
                   return_value=remote_ins_list)
        self.assertEqual(len(list_instance.GetCFRemoteInstances(None)), 1)

    def testGetActiveCVD(self):
        """test GetActiveCVD."""
        # get local instance config
        self.Patch(instance, "GetLocalInstanceConfig",
                   return_value="fake_local_cfg_path")
        fake_local_ins = mock.MagicMock()
        self.Patch(instance, "LocalInstance", return_value=fake_local_ins)
        fake_local_ins.CvdStatus.return_value = True
        self.assertEqual(list_instance.GetActiveCVD(1), fake_local_ins)

        fake_local_ins.CvdStatus.return_value = False
        self.Patch(instance, "GetDefaultCuttlefishConfig",
                   return_value=None)
        self.assertEqual(list_instance.GetActiveCVD(1), None)

        # get default cf config
        fake_local_ins.CvdStatus.return_value = True
        self.Patch(instance, "GetLocalInstanceConfig",
                   return_value=None)
        self.Patch(instance, "GetDefaultCuttlefishConfig",
                   return_value="fake_cf_config")
        self.assertEqual(list_instance.GetActiveCVD(1), fake_local_ins)
        self.assertEqual(list_instance.GetActiveCVD(2), None)

        fake_local_ins.CvdStatus.return_value = False
        self.assertEqual(list_instance.GetActiveCVD(1), None)

        fake_local_ins.CvdStatus.return_value = True
        self.Patch(instance, "GetDefaultCuttlefishConfig",
                   return_value=None)
        self.assertEqual(list_instance.GetActiveCVD(1), None)

    def testChooseInstances(self):
        """test ChooseInstances."""
        self.Patch(list_instance, "GetLocalInstances",
                   return_value=["local_ins1", "local_ins2"])
        self.Patch(list_instance, "GetRemoteInstances",
                   return_value=["remote_ins1", "remote_ins2"])
        self.assertEqual(len(list_instance.ChooseInstances(None, True)), 4)

        self.Patch(utils, "GetAnswerFromList", return_value=["remote_ins1"])
        choose_instance = ["remote_ins1"]
        self.assertEqual(list_instance.ChooseInstances(None), choose_instance)

        list_instance.GetLocalInstances.return_value = []
        list_instance.GetRemoteInstances.return_value = ["only_one_ins"]
        self.assertEqual(list_instance.ChooseInstances(None), ["only_one_ins"])

    def testGetLocalInstanceLockByName(self):
        """test GetLocalInstanceLockByName."""
        self.Patch(instance, "GetLocalInstanceIdByName",
                   return_value="local_ins_id")
        self.Patch(instance, "GetLocalInstanceLock")
        list_instance.GetLocalInstanceLockByName("query_name")
        instance.GetLocalInstanceLock.assert_called_once()

        instance.GetLocalInstanceIdByName.return_value = None
        self.Patch(instance.LocalGoldfishInstance,
                   "GetIdByName", return_value="gf_ins_id")
        self.Patch(instance.LocalGoldfishInstance, "GetLockById")
        list_instance.GetLocalInstanceLockByName("query_name")
        instance.LocalGoldfishInstance.GetLockById.assert_called_once()

        self.Patch(instance.LocalGoldfishInstance,
                   "GetIdByName", return_value= None)
        self.assertEqual(
            list_instance.GetLocalInstanceLockByName("query_name"), None)


if __name__ == "__main__":
    unittest.main()
