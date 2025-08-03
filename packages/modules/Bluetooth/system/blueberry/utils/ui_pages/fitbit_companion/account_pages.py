"""Account pages associated with Fitbit Companion App testing."""
from typing import List

from blueberry.utils.ui_pages import ui_core
from blueberry.utils.ui_pages import ui_node
from blueberry.utils.ui_pages.fitbit_companion import constants


class AccountPage(ui_core.UIPage):
  """Fitbit Companion App's account page."""

  ACTIVITY = f'{constants.PKG_NAME}/com.fitbit.settings.ui.AccountActivity'
  PAGE_RID = f'{constants.PKG_NAME_ID}/account_recycler'
  NODE_UER_AVATOR_RID = f'{constants.PKG_NAME_ID}/userAvatar'
  NODE_BACK_CONTENT_DESC = 'Navigate up'
  NODE_ADD_DEV_RID = f'{constants.PKG_NAME_ID}/add_device_img'
  NODE_DEVICE_RID = f'{constants.PKG_NAME_ID}/device_name'

  def add_device(self) -> ui_core.UIPage:
    """Goes to page to add new device.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get the target node.
    """
    return self.click_node_by_rid(self.NODE_ADD_DEV_RID)

  def back(self) -> ui_core.UIPage:
    """Goes back to Fitbit Companion App's home page.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    return self.click_node_by_content_desc(self.NODE_BACK_CONTENT_DESC)

  def get_paired_devices(self) -> List[ui_node.UINode]:
    """Gets all paired devices.

    Returns:
      The list of node representing the paired device.
    """
    return self.get_all_nodes_by_rid(self.NODE_DEVICE_RID)


class PairedDeviceDetailPage(ui_core.UIPage):
  """Fitbit Companion App's page of paired device."""

  PAGE_RID = f'{constants.PKG_NAME_ID}/unpair'

  def unpair(self) -> ui_core.UIPage:
    """Unpairs device.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to find the target node.
    """
    # TODO(user): We need to consider the situation while device
    #   sync now which cause unpair to fail.
    return self.click_node_by_rid(self.PAGE_RID)


class UnpairConfirmPage(ui_core.UIPage):
  """Fitbit Companion App's page to confirm the action of unpairing."""

  PAGE_TEXT = 'UNPAIR'

  def confirm(self) -> ui_core.UIPage:
    """Confirms the action of unpairing.

    Returns:
      The transformed page.
    """
    self.click_node_by_text(self.PAGE_TEXT)
    self.ctx.expect_page(AccountPage)
    return self.ctx.page
