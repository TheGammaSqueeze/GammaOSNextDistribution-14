"""Context of Fitbit Companion App."""
from mobly.controllers import android_device

from blueberry.utils.ui_pages import ui_core
from blueberry.utils.ui_pages.fitbit_companion import constants


class Context(ui_core.Context):
  """Context of Fitbit Companion App.

  Attributes:
    ad: The Android device where the UI pages are derived from.
    safe_get: If True, use `safe_get_page` to get the page;
      otherwise, use `get_page`.
    do_go_home: If False the context object will stay in the
      App's current page.
  """

  def __init__(self, ad: android_device.AndroidDevice,
               safe_get: bool = False,
               do_go_home: bool = True) -> None:
    super().__init__(ad, [HomePage], safe_get=safe_get,
                     do_go_home=do_go_home)

  def go_home_page(self) -> ui_core.UIPage:
    """Goes to Fitbit companion App's home page.

    Returns:
      The home page object.

    Raises:
      errors.ContextError: Fail to reach target page.
    """
    return self.go_page(HomePage)


class HomePage(ui_core.UIPage):
  """Fitbit Companion App's home page."""

  ACTIVITY = f'{constants.PKG_NAME}/com.fitbit.home.ui.HomeActivity'
  PAGE_RID = f'{constants.PKG_NAME_ID}/userAvatar'

  def go_account_page(self) -> ui_core.UIPage:
    """Goes to Fitbit companion App's account page.

    Returns:
      The account page object.

    Raises:
      errors.UIError: Fail to get target node.
    """
    return self.click_node_by_rid(self.PAGE_RID)
