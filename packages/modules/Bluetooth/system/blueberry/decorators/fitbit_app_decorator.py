"""Android Device decorator to control functionality of the Fitbit companion App."""
import logging
import time
from typing import Any, Dict, Tuple

import immutabledict  # pylint: disable=no-name-in-module,import-error
from mobly import asserts
from mobly import signals
from mobly.controllers import android_device

from blueberry.controllers import derived_bt_device
# Internal import
# Internal import
from blueberry.utils.ui_pages import fitbit_companion  # pylint: disable=no-name-in-module,import-error
from blueberry.utils.ui_pages.fitbit_companion import account_pages  # pylint: disable=no-name-in-module,import-error
from blueberry.utils.ui_pages.fitbit_companion import context  # pylint: disable=no-name-in-module,import-error
from blueberry.utils.ui_pages.fitbit_companion import other_pages  # pylint: disable=no-name-in-module,import-error
from blueberry.utils.ui_pages.fitbit_companion import pairing_pages  # pylint: disable=no-name-in-module,import-error

_FITBIT_PACKAGE_NAME = 'com.fitbit.FitbitMobile'
_LOG_PREFIX_MESSAGE = 'Fitbit Companion App'
_DEBUG_PREFIX_TEMPLATE = f'[{_LOG_PREFIX_MESSAGE}|{{tag}}] {{msg}}'
_MODEL_TO_PRODUCT_NAME_MAPPING = immutabledict.immutabledict({
    'Buzz': 'Luxe',
})
_INVALID_PAIRING_CODE_MESSAGE = "Sorry, this code isn't valid."
_MAX_PAIRING_RETRIES = 10


class FitbitAppDecorator:
  """Decorates Android Device with the Fitbit Companion App's operations.

  Attributes:
    ui_context: The UI context of Fitbit companion App.
  """

  def __init__(self, ad: android_device.AndroidDevice):  # pylint: disable=super-init-not-called
    self._ad = ad
    self._target_device = None
    self.ui_context = fitbit_companion.get_context(
        self._ad, do_go_home=False, safe_get=True)

    if not apk_utils.is_apk_installed(self._ad, _FITBIT_PACKAGE_NAME):
      # Fitbit App is not installed, install it now.
      self.ui_context.log.info('Installing Fitbit App...')
      fitbit_companion.go_google_play_page(self.ui_context)
      self.ui_context.expect_page(other_pages.GooglePlayPage)
      self.ui_context.regr_page_call(other_pages.GoogleSmartLockSavePage, 'no')
      self.ui_context.page.install()
      self.ui_context.expect_page(other_pages.LoginPage)
      fitbit_app_account = self._ad._user_params.get('fitbit_app_account',
                                                     'test')
      self.ui_context.log.info('Login Fitbit App with account=%s...',
                               fitbit_app_account)
      self.ui_context.page.login(
          fitbit_app_account,
          self._ad._user_params.get('fitbit_app_password', 'test'))
      self.ui_context.expect_page(context.HomePage)

  def __getattr__(self, name):
    return getattr(self._ad, name)

  def set_target(self, bt_device: derived_bt_device.BtDevice) -> None:
    """Allows for use to get target device object for target interaction.

    Args:
      bt_device: The testing target.
    """
    self._target_device = bt_device

  def pair_and_connect_bluetooth(self, mac_address: str) -> None:
    """Pairs and connects Android device with Fitbit device.

    Args:
      mac_address: MAC address of the Fitbit device to be paired with.

    Raises:
      signals.TestError: Fail in pairing and connection process.
      AssertionError: Fail in evaluation after pairing.
    """
    log = FitbitCompanionAppLoggerAdapter(logging.getLogger(),
                                          {'tag': mac_address})
    fitbit_device = self._target_device
    target_device_mac_address = fitbit_device.get_bluetooth_mac_address()
    if target_device_mac_address != mac_address:
      raise ValueError(
          (f'Target BT device has MAC address={target_device_mac_address}',
           f'which is different than given MAC address={mac_address} !'))

    self.ui_context.regr_page_call(other_pages.LinkConfirmPage, 'ok')
    self.ui_context.regr_page_call(other_pages.PurchaseFail, 'ok')
    self.ui_context.regr_page_call(pairing_pages.PremiumPage, 'done')
    self.ui_context.regr_page_call(other_pages.PurchaseFail, 'ok')
    self.ui_context.regr_page_call(other_pages.LocationPermissionSync, 'enable')
    self.ui_context.regr_page_call(pairing_pages.UpdateDevicePage,
                                   'update_later')

    log.debug('Start the pair-pin subscription...')
    try:
      fitbit_device._device.bt.pair_pin_start()
    except fitbit_tracker_cli.CliError as err:
      if err and 'Already subscribed on pubsub' in err.output[0]:
        log.warning('Fitbit device already subscribed on pubsub!')
      else:
        raise err

    log.info('Entering account page...')
    self.ui_context.go_page(account_pages.AccountPage)

    log.info('Removed all paired device(s) before testing...')
    removed_count = fitbit_companion.remove_all_paired_devices(self.ui_context)

    log.info('Total %d device(s) being removed!', removed_count)

    fitbit_prod_name = _MODEL_TO_PRODUCT_NAME_MAPPING[fitbit_device.model]
    log.info('Pairing with %s...', fitbit_prod_name)

    def _eval_existence_of_fitbit_product_name(node, name=fitbit_prod_name):
      return name in node.text

    self.ui_context.page.add_device()
    self.ui_context.expect_page(
        pairing_pages.ChooseTrackerPage,
        node_eval=_eval_existence_of_fitbit_product_name)
    self.ui_context.page.select_device(fitbit_prod_name)
    self.ui_context.page.confirm()

    log.info('Accept pairing privacy requirement...')
    self.ui_context.expect_page(pairing_pages.PairPrivacyConfirmPage)
    self.ui_context.page.accept()
    self.ui_context.expect_page(pairing_pages.ConfirmChargePage)
    self.ui_context.page.next()
    if self.ui_context.is_page(other_pages.LocationDisabledPage):
      # Optional page when you are required to enable location
      # permission for Fitbit device.
      log.info('Enabling location permission...')
      self.ui_context.page.enable()
      self.ui_context.expect_page(other_pages.SettingLocation)
      self.ui_context.page.set(True)
      self.ui_context.page.back()

    # TODO(user): Move pairing logic into fitbit_companion package while
    #   it may be used in many places.
    self.ui_context.expect_page(pairing_pages.Pairing4DigitPage, wait_sec=150)
    pins = fitbit_device._device.bt.pair_pin_show()
    log.info('Pairing pins=%s...', pins)
    self.ui_context.page.input_pins(pins)
    pair_retry = 0
    while (self.ui_context.is_page(pairing_pages.Pairing4DigitPage) and
           self.ui_context.page.get_node_by_func(
               lambda n: _INVALID_PAIRING_CODE_MESSAGE in n.text) is not None):
      pair_retry += 1
      if pair_retry >= _MAX_PAIRING_RETRIES:
        raise signals.TestError(
            f'Failed in pairing pins matching after {pair_retry} tries!')
      pins = fitbit_device._device.bt.pair_pin_show()
      log.warning('Retrying on pairing pins=%s...', pins)
      self.ui_context.page.input_pins(pins)
      time.sleep(1)

    pair_retry = 0
    while True:
      self.ui_context.expect_pages([
          pairing_pages.PairRetryPage,
          pairing_pages.PairAndLinkPage,
          pairing_pages.PairingIntroPage,
          pairing_pages.PairingConfirmPage,
          pairing_pages.PairingIntroPage,
          pairing_pages.CancelPairPage,
          pairing_pages.CancelPair2Page,
          other_pages.AllowNotification,
      ],
                                   wait_sec=90)
      if self.ui_context.is_page(pairing_pages.PairingConfirmPage):
        log.info('Accept pairing confirm page...')
        self.ui_context.page.confirm()
      elif self.ui_context.is_page(pairing_pages.PairRetryPage):
        log.warning('Skip pair retry page...')
        self.ui_context.back()
      elif self.ui_context.is_page(pairing_pages.PairAndLinkPage):
        log.warning('Skip pair and link page...')
        self.ui_context.page.cancel()
      elif self.ui_context.is_page(pairing_pages.CancelPairPage):
        log.warning('Skip cancel pair page...')
        self.ui_context.page.yes()
      elif self.ui_context.is_page(other_pages.AllowNotification):
        log.warning('Allow notification page...')
        self.ui_context.page.allow()
      elif self.ui_context.is_page(pairing_pages.PairingIntroPage):
        log.info('Passing through Fitbit introduction pages...')
        break

      pair_retry += 1
      if pair_retry >= _MAX_PAIRING_RETRIES:
        raise signals.TestError(
            f'Failed in pairing process after {pair_retry} tries!')

    self.ui_context.expect_page(pairing_pages.PairingIntroPage)
    while self.ui_context.is_page(pairing_pages.PairingIntroPage):
      self.ui_context.page.next()

    self.ui_context.expect_pages([
        pairing_pages.PremiumPage, other_pages.PurchaseFail,
        account_pages.AccountPage
    ])

    if self.ui_context.is_page(pairing_pages.PremiumPage):
      # Preminum page is optional.
      self.ui_context.page.done()
    elif self.ui_context.is_page(other_pages.PurchaseFail):
      # Optional page observed during manual pairing experiment.
      self.ui_context.page.ok()

    log.info('Completed pairing process and start evaluation process...')
    if self.ui_context.is_page(account_pages.AccountPage):
      paired_device_nodes = self.ui_context.page.get_paired_devices()
      asserts.assert_true(
          len(paired_device_nodes) == 1,
          f'Unexpected paired device nodes={paired_device_nodes}',
      )
      asserts.assert_true(
          paired_device_nodes[0].text == fitbit_prod_name,
          f'Unexpected paired device nodes={paired_device_nodes}',
      )
    else:
      raise signals.TestError('Failed in evaluation of Fitbit pairing result!')

    log.info('Stop the pair-pin subscription...')
    fitbit_device._device.bt.pair_pin_stop()
    log.info('Pairing and connection with %s(%s) is all done!',
             fitbit_prod_name, mac_address)


class FitbitCompanionAppLoggerAdapter(logging.LoggerAdapter):
  """A wrapper class that adds a prefix to each log line.

  Usage:
  .. code-block:: python
    my_log = FitbitCompanionAppLoggerAdapter(logging.getLogger(), {
      'tag': <custom tag>
    })

  Then each log line added by my_log will have a prefix
  '[Fitbit Companion App|<tag>]'
  """

  def process(self, msg: str, kwargs: Dict[Any,
                                           Any]) -> Tuple[str, Dict[Any, Any]]:
    new_msg = _DEBUG_PREFIX_TEMPLATE.format(tag=self.extra['tag'], msg=msg)
    return (new_msg, kwargs)
