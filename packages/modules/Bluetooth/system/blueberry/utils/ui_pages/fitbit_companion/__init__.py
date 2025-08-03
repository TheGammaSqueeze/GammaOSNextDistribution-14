"""Gets context of Fitbit Companion App."""

from mobly.controllers import android_device

from blueberry.utils.ui_pages import ui_core
from blueberry.utils.ui_pages.fitbit_companion import account_pages
from blueberry.utils.ui_pages.fitbit_companion import constants
from blueberry.utils.ui_pages.fitbit_companion import context
from blueberry.utils.ui_pages.fitbit_companion import other_pages
from blueberry.utils.ui_pages.fitbit_companion import pairing_pages


def get_context(ad: android_device.AndroidDevice,
                safe_get: bool = False,
                do_go_home: bool = True) -> context.Context:
  """Gets context of Fitbit Companion App.

  Args:
    ad: The Android device where the UI pages are derived from.
    safe_get: If True, use `safe_get_page` to get the page; otherwise, use
      `get_page`.
    do_go_home: If False the context object will stay in the App's current page.

  Returns:
    Context of Fitbit Companion App.
  """
  ctx = context.Context(ad, safe_get=safe_get, do_go_home=do_go_home)
  ctx.known_pages.extend((
      other_pages.LoginPage,
      other_pages.GooglePlayPage,
      other_pages.AllowLocationPermissionConfirmPopup,
      other_pages.AllowLocationPermissionPopup,
      other_pages.LocationPermissionSync,
      other_pages.PurchaseFail,
      other_pages.AllowNotification,
      other_pages.SettingLocation,
      other_pages.LocationDisabledPage,
      other_pages.LinkConfirmPage,
      account_pages.AccountPage,
      account_pages.PairedDeviceDetailPage,
      account_pages.UnpairConfirmPage,
      pairing_pages.PurchasePage,
      pairing_pages.PairRetryPage,
      pairing_pages.Pairing4DigitPage,
      pairing_pages.PairingConfirmPage,
      pairing_pages.PairingIntroPage,
      pairing_pages.PairAndLinkPage,
      pairing_pages.PremiumPage,
      pairing_pages.PairPrivacyConfirmPage,
      pairing_pages.CancelPairPage,
      pairing_pages.CancelPair2Page,
      pairing_pages.ConfirmReplaceSmartWatchPage,
      pairing_pages.ConfirmChargePage,
      pairing_pages.ChooseTrackerPage,
      pairing_pages.ConfirmDevicePage,
      pairing_pages.SkipInfoPage,
      pairing_pages.UpdateDevicePage,
  ))

  return ctx


def go_google_play_page(ctx: context.Context) -> None:
  """Goes to Google play page of Fitbit companion app.

  This function will leverage adb shell command to launch Fitbit app's
  Google play page by searching the package of it. Then it will confirm
  the result by checking the expected page as `GooglePlayPage` by `ctx`.

  Args:
    ctx: Context object of Fitbit Companion App.

  Raises:
    errors.ContextError: Fail to reach target page.
  """
  ctx.ad.adb.shell(
      'am start -a android.intent.action.VIEW -d market://details?id=com.fitbit.FitbitMobile'
  )
  ctx.expect_page(other_pages.GooglePlayPage)


def remove_all_paired_devices(ctx: context.Context) -> int:
  """Removes all paired devices.

  Args:
    ctx: Context object of Fitbit Companion App.

  Returns:
    The number of paired device being removed.

  Raises:
    errors.ContextError: Fail to reach target page.
    AssertionError: Fail in evaluation after pairing.
  """
  removed_count = 0
  ctx.go_page(account_pages.AccountPage)
  paired_devices = ctx.page.get_paired_devices()
  while paired_devices:
    ctx.page.click(paired_devices[0])
    ctx.expect_page(account_pages.PairedDeviceDetailPage)
    ctx.page.unpair()
    ctx.expect_page(account_pages.UnpairConfirmPage)
    ctx.page.confirm()
    ctx.expect_page(account_pages.AccountPage)
    removed_count += 1
    paired_devices = ctx.page.get_paired_devices()

  return removed_count
