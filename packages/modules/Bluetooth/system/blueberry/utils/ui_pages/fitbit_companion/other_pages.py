"""Other pages associated with Fitbit Companion App testing."""
import shlex

from typing import List, Optional
from xml.dom import minidom

from blueberry.utils.ui_pages import errors
from blueberry.utils.ui_pages import ui_core
from blueberry.utils.ui_pages import ui_node
from blueberry.utils.ui_pages.fitbit_companion import constants

# Typing hint alias.
NodeList = List[ui_node.UINode]


class LoginInputPage(ui_core.UIPage):
  """Fitbit Companion App's login input page."""

  PAGE_TEXT = 'Forgot password?'
  _NODE_EMAIL_INPUT_TEXT = f'{constants.PKG_NAME_ID}/login_email'
  _NODE_PASSWORD_INPUT_RID = f'{constants.PKG_NAME_ID}/login_password'
  _NODE_LOGIN_BUTTON_RID = f'{constants.PKG_NAME_ID}/login_button'

  def input(self, account: str, password: str) -> ui_core.UIPage:
    """Inputs login credentials.

    Args:
      account: The account is used to login Fitbit App.
      password: The password is used to login Fitbit App.

    Returns:
      The transformed page.
    """
    self.click_node_by_rid(self._NODE_EMAIL_INPUT_TEXT)
    self.ctx.ad.adb.shell(shlex.split(f'input text "{account}"'))
    self.click_node_by_rid(self._NODE_PASSWORD_INPUT_RID)
    self.ctx.ad.adb.shell(shlex.split(f'input text "{password}"'))
    return self.click_node_by_rid(self._NODE_LOGIN_BUTTON_RID)


class GoogleSmartLockSavePage(ui_core.UIPage):
  """Google SmartLock popup to save input credentials."""

  PAGE_TEXT = 'Save password to Google?'
  _NODE_NO_RID = 'android:id/autofill_save_no'
  _NODE_YES_RID = 'android:id/autofill_save_yes'

  def yes(self) -> ui_core.UIPage:
    """Saves the input credentials in Google SmartLock.

    Returns:
      The transformed page.
    """
    return self.click_node_by_rid(self._NODE_YES_RID)

  def no(self) -> ui_core.UIPage:
    """Skips the request to save input credentials.

    Returns:
      The transformed page.
    """
    return self.click_node_by_rid(self._NODE_NO_RID)


class GoogleSmartLockPage(ui_core.UIPage):
  """Google SmartLock popup from login page."""

  PAGE_TEXT = 'Google Smartlock'
  _NODE_YES_TEXT = 'YES'
  _NODE_NO_TEXT = 'NO'

  def yes(self) -> ui_core.UIPage:
    """Logins by GoogleSmartLock.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self._NODE_YES_TEXT)

  def no(self) -> ui_core.UIPage:
    """Skips GoogleSmartLock.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self._NODE_NO_TEXT)


class LoginPage(ui_core.UIPage):
  """Fitbit Companion App's login page."""

  PAGE_TEXT = 'Log in'

  def login(self,
            account: str,
            password: str,
            allow_google_smartlock: bool = True) -> ui_core.UIPage:
    """Logins the Fitbit Companion App.

    Args:
      account: Login account
      password: Login password
      allow_google_smartlock: True to allow Google SmartLock feature.

    Returns:
      The transformed page.
    """
    self.click_node_by_text(self.PAGE_TEXT)
    self.ctx.expect_pages([GoogleSmartLockPage, LoginInputPage])
    if self.ctx.is_page(GoogleSmartLockPage):
      if allow_google_smartlock:
        return self.ctx.page.yes()
      else:
        self.ctx.page.no()

    self.ctx.expect_page(LoginInputPage)
    return self.ctx.page.input(account, password)


class GooglePlayPage(ui_core.UIPage):
  """Fitbit Companion App's GooglePlay page."""

  _FITBIT_COMPANION_NAME_TEXT = 'Fitbit, Inc.'
  _NODE_RESOURCE_NAME_RID = 'com.android.vending:id/0_resource_name_obfuscated'
  _NODE_UNINSTALL_BUTTON_TEXT = 'Uninstall'
  _NODE_OPEN_BUTTON_TEXT = 'Open'
  _NODE_INSTALL_BUTTON_TEXT = 'Install'

  @classmethod
  def from_xml(cls, ctx: ui_core.Context, ui_xml: minidom.Document,
               clickable_nodes: NodeList,
               enabled_nodes: NodeList,
               all_nodes: NodeList) -> Optional[ui_core.UIPage]:
    """Instantiates page object from XML object.

    Args:
      ctx: Page context object.
      ui_xml: Parsed XML object.
      clickable_nodes: Clickable node list from page.
      enabled_nodes: Enabled node list from page.
      all_nodes: All node from page.

    Returns:
      UI page object iff the given XML object can be parsed.
    """
    for node in enabled_nodes:
      if (node.text == cls._FITBIT_COMPANION_NAME_TEXT and
          node.resource_id == cls._NODE_RESOURCE_NAME_RID):
        return cls(ctx, ui_xml, clickable_nodes, enabled_nodes, all_nodes)

  def open(self) -> ui_core.UIPage:
    """Opens the Fitbit Companion App.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self._NODE_OPEN_BUTTON_TEXT)

  def install(self, open_app: bool = True) -> ui_core.UIPage:
    """Installs the Fitbit Companion App.

    Args:
      open_app: True to open application after installation.

    Returns:
      The transformed page.
    """
    if self.get_node_by_text(self._NODE_OPEN_BUTTON_TEXT) is None:
      # The app is not installed yet.
      self.click_node_by_text(self._NODE_INSTALL_BUTTON_TEXT)
      self.ctx.expect_page(
          self.__class__,
          wait_sec=120,
          node_eval=lambda node: node.text == self._NODE_OPEN_BUTTON_TEXT)

    if open_app:
      return self.ctx.page.open()
    else:
      return self.ctx.page


class AllowLocationPermissionConfirmPopup(ui_core.UIPage):
  """Page to confirm the location permission request."""

  PAGE_RE_TEXT = 'This app wants to access your location'
  _ALLOW_BUTTON_RESOURCE_ID = 'com.android.permissioncontroller:id/permission_no_upgrade_button'

  def allow(self) -> ui_core.UIPage:
    """Allows the request."""
    return self.click_node_by_rid(self._ALLOW_BUTTON_RESOURCE_ID)


class AllowLocationPermissionPopup(ui_core.UIPage):
  """Page to allow location permission."""

  PAGE_TEXT = 'While using the app'

  def next(self) -> ui_core.UIPage:
    """Allows the permission."""
    return self.click_node_by_text(self.PAGE_TEXT)


class LocationPermissionSync(ui_core.UIPage):
  """Page to require location permission required by Fitbit Companion App."""

  PAGE_TEXT = 'Location permission required to sync'
  _EXIT_IMAGE_CLASS = 'android.widget.ImageButton'
  _LOCATION_PERMISSION_CHECK_BOX_TEXT = 'Location Permission'
  _UPDATE_BUTTON_TEXT = 'Update Settings'

  def enable(self) -> ui_core.UIPage:
    """Enables location permission to app."""
    self.ctx.enable_registered_page_call = False
    self.click_node_by_text(self._LOCATION_PERMISSION_CHECK_BOX_TEXT)
    self.ctx.get_page()
    self.ctx.expect_page(AllowLocationPermissionPopup)
    self.ctx.page.next()
    self.ctx.expect_page(AllowLocationPermissionConfirmPopup)
    self.ctx.page.allow()
    self.click_node_by_text(self._UPDATE_BUTTON_TEXT)
    self.ctx.enable_registered_page_call = True
    return self.click_node_by_class(self._EXIT_IMAGE_CLASS)


class PurchaseFail(ui_core.UIPage):
  """Fitbit Companion App's page to show failure of purchase."""

  PAGE_TEXT = 'Purchase failed!'
  _NODE_OK_TEXT = 'OK'

  def ok(self) -> ui_core.UIPage:
    """Confirms the failure.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self._NODE_OK_TEXT)


class AllowNotification(ui_core.UIPage):
  """Fitbit Companion App's page to allow access of notification."""

  PAGE_TEXT = 'Allow notification access for Fitbit?'
  _NODE_ALLOW_TEXT = 'Allow'
  _NODE_DENY_TEXT = 'Deny'

  def allow(self) -> ui_core.UIPage:
    """Allows the request.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self._NODE_ALLOW_TEXT)

  def deny(self) -> ui_core.UIPage:
    """Denies the request.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self._NODE_DENY_TEXT)


class SettingLocation(ui_core.UIPage):
  """Android location setting page."""

  _NODE_SWITCH_RID = 'com.android.settings:id/switch_widget'
  _NODE_SWITCH_TEXT_RID = 'com.android.settings:id/switch_text'

  @classmethod
  def from_xml(cls, ctx: ui_core.Context, ui_xml: minidom.Document,
               clickable_nodes: NodeList,
               enabled_nodes: NodeList,
               all_nodes: NodeList) -> Optional[ui_core.UIPage]:
    """Instantiates page object from XML object.

    Args:
      ctx: Page context object.
      ui_xml: Parsed XML object.
      clickable_nodes: Clickable node list from page.
      enabled_nodes: Enabled node list from page.
      all_nodes: All node from page.

    Returns:
      UI page object iff the given XML object can be parsed.
    """
    for node in enabled_nodes:
      if (node.text == 'Use location' and
          node.resource_id == cls._NODE_SWITCH_TEXT_RID):
        return cls(ctx, ui_xml, clickable_nodes, enabled_nodes, all_nodes)

  def back(self) -> ui_core.UIPage:
    """Backs to previous page.

    Returns:
      The transformed page.
    """
    self.click_node_by_content_desc('Navigate up')
    return self.swipe_left()

  @property
  def enabled(self) -> bool:
    """Checks the location setting.

    Returns:
      True iff the location is enabled.

    Raises:
      errors.UIError: Fail to find the target node.
    """
    node = self.get_node_by_rid(self._NODE_SWITCH_RID)
    if not node:
      raise errors.UIError('Fail to get switch node!')

    return node.attributes['checked'].value == 'true'

  def set(self, value: bool) -> ui_core.UIPage:
    """Toggles the switch to enable/disable location.

    Args:
      value: True to turn on setting; False to turn off setting.

    Returns:
      The transformed page.
    """
    if value != self.enabled:
      return self.click_node_by_rid(self._NODE_SWITCH_RID)

    return self


class LocationDisabledPage(ui_core.UIPage):
  """Popup page for notification as location is disabled."""

  PAGE_RE_TEXT = 'ENABLE LOCATIONS'
  _NODE_ENABLE_TEXT = 'ENABLE LOCATIONS'

  def enable(self) -> ui_core.UIPage:
    """Enables the location setting.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self._NODE_ENABLE_TEXT)


class LinkConfirmPage(ui_core.UIPage):
  """Popup page to confirm the process of pairing."""

  PAGE_RID = 'com.android.companiondevicemanager:id/buttons'
  _NODE_PAIR_BTN_RID = 'com.android.companiondevicemanager:id/button_pair'

  def ok(self) -> ui_core.UIPage:
    """Confirms pairing process.

    Returns:
      The transformed page.
    """
    return self.click_node_by_rid(self._NODE_PAIR_BTN_RID)
