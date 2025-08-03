import os
import re

from acts import utils
from acts import signals
from acts.base_test import BaseTestClass
from acts_contrib.test_utils.tel.tel_defines import EventSmsSentSuccess
from acts_contrib.test_utils.tel.tel_test_utils import get_iccid_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import is_sim_ready_by_adb


class GnssSimInventoryTest(BaseTestClass):
    """ GNSS SIM Inventory Tests"""

    def setup_class(self):
        super().setup_class()
        self.ad = self.android_devices[0]

    def check_device_status(self):
        if int(self.ad.adb.shell("settings get global airplane_mode_on")) != 0:
            self.ad.log.info("Force airplane mode off")
            utils.force_airplane_mode(self.ad, False)
        if not is_sim_ready_by_adb(self.ad.log, self.ad):
            raise signals.TestFailure("SIM card is not loaded and ready.")

    def get_imsi(self):
        self.ad.log.info("Get imsi from netpolicy.xml")
        try:
            tmp_imsi = self.ad.adb.shell("cat /data/system/netpolicy.xml")
            imsi = re.compile(r'(\d{15})').search(tmp_imsi).group(1)
            return imsi
        except Exception as e:
            raise signals.TestFailure("Fail to get imsi : %s" % e)

    def get_iccid(self):
        iccid = str(get_iccid_by_adb(self.ad))
        if not isinstance(iccid, int):
            self.ad.log.info("Unable to get iccid via adb. Changed to isub.")
            tmp_iccid = self.ad.adb.shell("dumpsys isub | grep iccid")
            iccid = re.compile(r'(\d{20})').search(tmp_iccid).group(1)
            return iccid
        raise signals.TestFailure("Fail to get iccid")

    def test_gnss_sim_inventory(self):
        sim_inventory_recipient = "0958787507"
        self.check_device_status()
        sms_message = "imsi: %s, iccid: %s, ldap: %s, model: %s, sn: %s" % (
            self.get_imsi(), self.get_iccid(), os.getlogin(), self.ad.model,
            self.ad.serial)
        self.ad.log.info(sms_message)
        try:
            self.ad.log.info("Send SMS by SL4A.")
            self.ad.droid.smsSendTextMessage(sim_inventory_recipient,
                                             sms_message, True)
            self.ad.ed.pop_event(EventSmsSentSuccess, 10)
        except Exception as e:
            raise signals.TestFailure(e)
