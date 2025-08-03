"""Pages associated with pairing process of Fitbit tracker device."""
import shlex
from typing import List, Optional
from xml.dom import minidom

from blueberry.utils.ui_pages import errors
from blueberry.utils.ui_pages import ui_core
from blueberry.utils.ui_pages import ui_node
from blueberry.utils.ui_pages.fitbit_companion import constants

# Alias for typing convenience.
_NodeList = List[ui_node.UINode]


class PairRetryPage(ui_core.UIPage):
  """Fitbit Companion App's page for retry of pairing."""

  PAGE_RE_TEXT = 'TRY AGAIN'

  def retry(self) -> ui_core.UIPage:
    """Clicks button to retry pairing.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to find the target node.
    """
    return self.click_node_by_text('TRY AGAIN')


class Pairing4DigitPage(ui_core.UIPage):
  """Fitbit Companion App's page to enter 4 digit pins for pairing."""

  PAGE_RID = f'{constants.PKG_NAME_ID}/digits'
  NODE_DIGIT_RIDS = (f'{constants.PKG_NAME_ID}/digit0'
                     f'{constants.PKG_NAME_ID}/digit1'
                     f'{constants.PKG_NAME_ID}/digit2'
                     f'{constants.PKG_NAME_ID}/digit3')

  def input_pins(self, pins: str) -> ui_core.UIPage:
    """Inputs 4 digit pins required in pairing process.

    Args:
      pins: 4 digit pins (e.g.: "1234")

    Returns:
      The transformed page.

    Raises:
      ValueError: Input pins is not valid.
    """
    if len(pins) != 4:
      raise ValueError(f'4 digits required here! (input={pins})')

    for digit in pins:
      self.ctx.ad.adb.shell(shlex.split(f'input text "{digit}"'))

    return self.ctx.page


class PairingConfirmPage(ui_core.UIPage):
  """Fitbit Companion App's page to confirm pairing."""

  NODE_ALLOW_ACCESS_TEXT = 'Allow access to your contacts and call history'
  NODE_PAIR_TEXT = 'Pair'

  @classmethod
  def from_xml(cls, ctx: ui_core.Context, ui_xml: minidom.Document,
               clickable_nodes: _NodeList, enabled_nodes: _NodeList,
               all_nodes: _NodeList) -> Optional[ui_core.UIPage]:
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
      if (node.text == cls.NODE_PAIR_TEXT and
          node.resource_id == 'android:id/button1'):
        return cls(ctx, ui_xml, clickable_nodes, enabled_nodes, all_nodes)

  def confirm(self) -> ui_core.UIPage:
    """Confirms the action of pairing.

    Returns:
      The transformed page.
    """
    self.click_node_by_text(self.NODE_ALLOW_ACCESS_TEXT)

    return self.click_node_by_text(self.NODE_PAIR_TEXT)


class PairingIntroPage(ui_core.UIPage):
  """Fitbit Companion App's pages for introduction of product usage."""

  NODE_TITLE_TEXT_SET = frozenset([
      'All set!',
      'Double tap to wake',
      'Firmly double-tap',
      'How to go back',
      'Swipe down',
      'Swipe left or right',
      'Swipe to navigate',
      'Swipe up',
      'Try it on',
      'Wear & care tips',
  ])
  NODE_NEXT_BTN_RID = f'{constants.PKG_NAME_ID}/btn_next'

  @classmethod
  def from_xml(cls, ctx: ui_core.Context, ui_xml: minidom.Document,
               clickable_nodes: _NodeList, enabled_nodes: _NodeList,
               all_nodes: _NodeList) -> Optional[ui_core.UIPage]:
    """Instantiates page object from XML object.

    The appending punctuation '.' of the text will be ignored during comparison.

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
      node_text = node.text[:-1] if node.text.endswith('.') else node.text
      if node_text in cls.NODE_TITLE_TEXT_SET:
        return cls(ctx, ui_xml, clickable_nodes, enabled_nodes, all_nodes)

  def next(self):
    """Moves to next page."""
    return self.click_node_by_rid(self.NODE_NEXT_BTN_RID)


class PairAndLinkPage(ui_core.UIPage):
  """Fitbit Companion App's landing page for pairing and linking."""

  PAGE_TEXT = 'Bluetooth Pairing and Linking'
  NODE_CANCEL_TEXT = 'Cancel'

  def cancel(self) -> ui_core.UIPage:
    """Cancel pairing process.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self.NODE_CANCEL_TEXT)


class PremiumPage(ui_core.UIPage):
  """Fitbit Companion App's page for Premium information."""

  PAGE_TEXT = 'See all Premium features'
  NODE_EXIT_IMG_BTN_CLASS = 'android.widget.ImageButton'

  def done(self):
    """Completes pairing process.

    Returns:
      The transformed page.
    """
    return self.click_node_by_class(self.NODE_EXIT_IMG_BTN_CLASS)


class PairPrivacyConfirmPage(ui_core.UIPage):
  """Fitbit Companion App's page to confirm the privacy befoe pairing."""

  PAGE_RID = f'{constants.PKG_NAME_ID}/gdpr_scroll_view'
  _NODE_ACCEPT_BTN_TEXT = 'ACCEPT'
  _SWIPE_RETRY = 5

  def accept(self) -> ui_core.UIPage:
    """Accepts the privacy policy.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    self.swipe_down()
    for i in range(self._SWIPE_RETRY):
      node = self.get_node_by_text(self._NODE_ACCEPT_BTN_TEXT)
      if node is None:
        raise errors.UIError(
            f'Fail to find the node with text={self._NODE_ACCEPT_BTN_TEXT}')

      atr_obj = node.attributes.get('enabled')
      if atr_obj is not None and atr_obj.value == 'true':
        return self.click_node_by_text(self._NODE_ACCEPT_BTN_TEXT)

      self.log.debug('swipe down to browse the privacy info...%d', i + 1)
      self.swipe_down()
      self.ctx.get_page()

    raise errors.UIError(
        'Fail to wait for the enabled button to confirm the privacy!')


class CancelPairPage(ui_core.UIPage):
  """Fitbit Companion App's page to confirm the cancel of pairing."""

  PAGE_TEXT = ('Canceling this process may result in poor connectivity with '
               'your Fitbit device.')
  NODE_YES_TEXT = 'YES'
  NODE_NO_TEXT = 'NO'

  def yes(self) -> ui_core.UIPage:
    """Cancels the pairing process.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    return self.click_node_by_text(self.NODE_YES_TEXT)

  def no(self) -> ui_core.UIPage:
    """Continues the pairing process.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    return self.click_node_by_text(self.NODE_NO_TEXT)


class CancelPair2Page(ui_core.UIPage):
  """Fitbit Companion App's page to confirm the cancel of pairing."""

  PAGE_TEXT = (
      'Are you sure you want to cancel pairing?'
      ' You can set up your Fitbit Device later on the Devices screen.')

  _NODE_YES_TEXT = 'CANCEL'

  def yes(self) -> ui_core.UIPage:
    """Cancels the pairing process.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    return self.click_node_by_text(self._NODE_YES_TEXT)


class ConfirmReplaceSmartWatchPage(ui_core.UIPage):
  """Fitbit Companion App's page to confirm the replacement of tracker device.

  When you already have one paired tracker device and you try to pair a
  new one, this page will show up.
  """
  NODE_SWITCH_BTN_TEXT = 'SWITCH TO'
  PAGE_TEXT = 'Switching?'

  def confirm(self) -> ui_core.UIPage:
    """Confirms the switching.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """

    def _search_switch_btn_node(node: ui_node.UINode) -> bool:
      if node.text.startswith(self.NODE_SWITCH_BTN_TEXT):
        return True

      return False

    node = self.get_node_by_func(_search_switch_btn_node)
    if node is None:
      raise errors.UIError(
          'Failed to confirm the switching of new tracker device!')

    return self.click(node)


class ConfirmChargePage(ui_core.UIPage):
  """Fitbit Companion App's page to confirm the charge condition."""

  PAGE_RE_TEXT = 'Let your device charge during setup'

  def next(self) -> ui_core.UIPage:
    """Forwards to pairing page.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    return self.click_node_by_text('NEXT')


class ChooseTrackerPage(ui_core.UIPage):
  """Fitbit Companion App's page to select device model for pairing."""

  ACTIVITY = f'{constants.PKG_NAME}/com.fitbit.device.ui.setup.choose.ChooseTrackerActivity'
  PAGE_RID = f'{constants.PKG_NAME_ID}/choose_tracker_title_container'

  def select_device(self, name: str) -> ui_core.UIPage:
    """Selects tracker device.

    Args:
      name: The name of device. (e.g.: 'Buzz')

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    return self.click_node_by_text(name)


class ConfirmDevicePage(ui_core.UIPage):
  """Fitbit Companion App's page to confirm the selected tracker device."""
  PAGE_TEXT = 'SET UP'
  ACTIVITY = f'{constants.PKG_NAME}/com.fitbit.device.ui.setup.choose.ConfirmDeviceActivity'

  def confirm(self) -> ui_core.UIPage:
    """Confirms the selection.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    return self.click_node_by_text(self.PAGE_TEXT)


class SkipInfoPage(ui_core.UIPage):
  """Fitbit Companion App's page to skip the 'not working' page."""

  PAGE_TEXT = 'Skip Information Screens'
  NODE_SKIP_TEXT = 'SKIP'
  NODE_CONTINUE_TEXT = 'CONTINUE'

  def skip(self) -> ui_core.UIPage:
    """Skips the information screens.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self.NODE_SKIP_TEXT)


class UpdateDevicePage(ui_core.UIPage):
  """Fitbit Companion App's page to update device."""

  PAGE_TEXT = 'INSTALL UPDATE NOW'
  NODE_UPDATE_LATER_BTN_TEXT = 'UPDATE LATER'

  def update_later(self) -> ui_core.UIPage:
    """Cancels the update.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self.NODE_UPDATE_LATER_BTN_TEXT)


class PurchasePage(ui_core.UIPage):
  """Fitbit Companion App's page to purchase merchandise."""

  PAGE_RE_TEXT = 'Protect Your New Device'
  NODE_SKIP_BTN_TEXT = 'NOT NOW'

  def skip(self) -> ui_core.UIPage:
    """Skips the purchase action.

    Returns:
      The transformed page.
    """
    return self.click_node_by_text(self.NODE_SKIP_BTN_TEXT)
