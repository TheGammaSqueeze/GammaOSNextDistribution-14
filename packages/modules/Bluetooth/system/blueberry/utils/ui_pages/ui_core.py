"""Core components of ui_pages."""
from __future__ import annotations

import abc
import itertools
import logging
import os
import re
import shlex
import time
from typing import Any, Callable, Dict, Generator, Iterable, List, Sequence, NamedTuple, Optional, Tuple, Type
from xml.dom import minidom

from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb

# Internal import
from blueberry.utils.ui_pages import errors
from blueberry.utils.ui_pages import ui_node
from blueberry.utils.ui_pages import utils

# Return type of otpional UINode.
OptUINode = Optional[ui_node.UINode]

# Return type of node generator.
NodeGenerator = Generator[ui_node.UINode, None, None]

# Waiting time of expecting page.
_EXPECT_PAGE_WAIT_TIME_IN_SECOND = 20

# Function to evaluate UINode.
NodeEvaluator = Optional[Callable[[ui_node.UINode], bool]]

# Number of retries in retrieving UI xml file.
_RETRIES_NUM_OF_ADB_COMMAND = 5


# Dataclass for return of UI parsing result.
class ParsedUI(NamedTuple):
  ui_xml: minidom.Document
  clickable_nodes: List[ui_node.UINode]
  enabled_nodes: List[ui_node.UINode]
  all_nodes: List[ui_node.UINode]


class Context(abc.ABC):
  """Context of UI page.

  Attributes:
    ad: The Android device where the UI pages are derived from.
    page: The last obtained UI page object.
    regr_page_calls: Key as page class; value as registered method of page class
      to call when meeting it.
    root_node: The root node of parsed XML file dumped from adb.
    known_pages: List of UIPage objects used to represent the current page.
    log: The logger object.
    safe_get: The method `safe_get_page` will be used to get page iff True.
      Otherwise the method `strict_get_page` will be used instead as default.
    enable_registered_page_call: True to enable the phrase of executing
      registered page action(s). It is used to avoid infinite loop situations.
  """

  def __init__(self,
               ad: android_device.AndroidDevice,
               known_pages: List[Type[UIPage]],
               do_go_home: bool = True,
               safe_get: bool = False) -> None:
    self.log = logging.getLogger(self.__class__.__name__)
    self.ad = ad
    self.page = None
    self.regr_page_calls = {}
    self.root_node = None
    self.known_pages = known_pages
    self.safe_get = safe_get
    self.enable_registered_page_call = True
    self.log.debug('safe_get=%s; do_go_home=%s', self.safe_get, do_go_home)
    if do_go_home:
      self.unlock_screen()
      self.go_home_page()
    else:
      self.get_page()

  def regr_page_call(self, page_class: Type[UIPage], method_name: str) -> None:
    """Registers a page call.

    This method is used to register a fixed method call on registered
    page class. Whenever the current page is of regisered page class,
    the registered method name will be called automatically.

    Args:
      page_class: The page class to register for callback.
      method_name: Name of method from `page_class` to be registered.
    """
    self.regr_page_calls[page_class] = method_name

  def get_regr_page_call(self, page_obj: UIPage) -> Optional[str]:
    """Gets the registered method name.

    We use method `self.regr_page_call` to register the subclass of UIPage
    as specific page and its method name. Then this method is used to
    retrieve the registered method name according to the input page object.

    Args:
      page_obj: The page object to search for registered method name.

    Returns:
      The registered method name of given page object iff registered.
      Otherwise, None is returned.
    """
    for page_class, method_name in self.regr_page_calls.items():
      if isinstance(page_obj, page_class):
        return method_name

    return None

  @abc.abstractmethod
  def go_home_page(self) -> UIPage:
    """Goes to home page.

    This is a abtract method to be implemented in subclass.
    Different App will have different home page and this method
    is implemented in the context of each App.

    Returns:
      The home page object.
    """
    pass

  def go_page(self, page_class: Type[UIPage]) -> UIPage:
    """Goes to target page.

    Args:
      page_class: The class of target page to go to.

    Returns:
      The corresponding UIPage of given page class.

    Raises:
      errors.ContextError: Fail to reach target page.
    """
    if self.is_page(page_class):
      return self.page

    self.ad.adb.shell(f'am start -n {page_class.ACTIVITY}')
    self.get_page()
    self.expect_page(page_class)

    return self.page

  def send_keycode(self, keycode: str) -> None:
    """Sends keycode.

    Args:
      keycode: Key code to be sent. e.g.: "BACK"
    """
    self.ad.adb.shell(f'input keyevent KEYCODE_{keycode}')

  def back(self) -> UIPage:
    """Sends keycode 'BACK'.

    Returns:
      The transformed page object.
    """
    self.send_keycode('BACK')
    return self.get_page()

  def send_keycode_number_pad(self, number: str) -> None:
    """Sends keycode of number pad.

    Args:
      number: The number pad to be sent.
    """
    self.send_keycode(f'NUMPAD_{number}')

  def get_my_current_focus_app(self) -> str:
    """Gets the current focus application.

    Returns:
      The current focus app activity name iff it works.
      Otherwise, empty string is returned.
    """
    output = self.ad.adb.shell(
        'dumpsys activity | grep -E mFocusedApp').decode()
    if any([
        not output, 'not found' in output, "Can't find" in output,
        'mFocusedApp=null' in output
    ]):
      self.log.warning(
          'Fail to obtain the current app activity with output: %s', output)
      return ''

    # The output may look like:
    # ActivityRecord{... FitbitMobile/com.fitbit.home.ui.HomeActivity t93}
    # and we want to extract 'FitbitMobile/com.fitbit.home.ui.HomeActivity'
    result = output.split(' ')[-2]
    self.log.debug('Current focus app activity is %s', result)
    return result

  def unlock_screen(self) -> UIPage:
    """Unlocks the screen.

    This method will assume that the device is not protected
    by password under testing.

    Returns:
      The page object after unlock.
    """
    # Bring device to SLEEP so that unlock process can start fresh.
    self.send_keycode('SLEEP')
    time.sleep(1)
    self.send_keycode('WAKEUP')
    self.get_page()
    self.page.swipe_down()
    return self.page

  def is_page(self, page_class: Type[UIPage]) -> bool:
    """Checks the current page is of expected page.

    Args:
      page_class: The class of expected page.

    Returns:
      True iff the current page is of expected page.
    """
    return isinstance(self.page, page_class)

  @retry.logged_retry_on_exception(
      retry_value=(adb.Error, errors.ContextError),
      retry_intervals=retry.FuzzedExponentialIntervals(
          initial_delay_sec=1,
          num_retries=_RETRIES_NUM_OF_ADB_COMMAND,
          factor=1.1))
  def get_ui_xml(self, xml_out_dir: str = '/tmp/') -> minidom.Document:
    """Gets the XML object of current UI.

    Args:
      xml_out_dir: The host directory path to store the dumped UI XML file.

    Returns:
      The parsed XML object of current UI page.

    Raises:
      errors.ContextError: Fail to dump UI xml from adb.
    """
    # Clean exist dump xml file in host if any to avoid
    # parsing the previous dumped xml file.
    dump_xml_name = 'window_dump.xml'
    xml_path = os.path.join(xml_out_dir, dump_xml_name)
    if os.path.isfile(xml_path):
      os.remove(xml_path)

    dump_xml_path = f'/sdcard/{dump_xml_name}'
    self.ad.adb.shell(
        f"test -f {dump_xml_path} && rm {dump_xml_path} || echo 'no dump xml'")
    self.ad.adb.shell('uiautomator dump')
    self.ad.adb.pull(shlex.split(f'{dump_xml_path} {xml_out_dir}'))

    if not os.path.isfile(xml_path):
      raise errors.ContextError(self, f'Fail to dump UI xml to {xml_path}!')

    return minidom.parse(xml_path)

  def parse_ui(self, xml_path: Optional[str] = None) -> ParsedUI:
    """Parses the current UI page.

    Args:
      xml_path: Target XML file path. If this argument is given, this method
        will parse this XML file instead of dumping XML file through adb.

    Returns:
      Parsed tuple as [
        <UI XML object>,
        <List of clickable nodes>,
        <List of enabled nodes>,
      ]

    Raises:
      errors.ContextError: Fail to dump UI XML from adb.
    """
    if xml_path and os.path.isfile(xml_path):
      ui_xml = minidom.parse(xml_path)
    else:
      ui_xml = self.get_ui_xml()

    root = ui_xml.documentElement
    self.root_node = ui_node.UINode(root.childNodes[0])

    clickable_nodes = []
    enabled_nodes = []
    all_nodes = []

    # TODO(user): Avoid the usage of nested functions.
    def _get_node_attribute(node: ui_node.UINode, name: str) -> Optional[str]:
      """Gets the attribute of give node by name if exist."""
      attribute = node.attributes.get(name)
      if attribute:
        return attribute.value
      else:
        return None

    def _search_node(node: ui_node.UINode) -> None:
      """Searches node(s) with desired attribute name to be true by DFS.

      Args:
        node: The current node to process.
      """
      rid = node.resource_id.strip()
      clz = node.clz.strip()

      if rid or clz:
        if _get_node_attribute(node, 'clickable') == 'true':
          clickable_nodes.append(node)
        if _get_node_attribute(node, 'enabled') == 'true':
          enabled_nodes.append(node)

      all_nodes.append(node)

      for child_node in node.child_nodes:
        _search_node(child_node)

    # TODO(user): Store nodes information in a dataclass.
    _search_node(self.root_node)
    return ParsedUI(ui_xml, clickable_nodes, enabled_nodes, all_nodes)

  def expect_pages(self,
                   page_class_list: Sequence[Type[UIPage]],
                   wait_sec: int = _EXPECT_PAGE_WAIT_TIME_IN_SECOND,
                   node_eval: NodeEvaluator = None) -> None:
    """Waits for expected pages for certain time.

    Args:
      page_class_list: The list of expected page class.
      wait_sec: The waiting time.
      node_eval: Function to search the node. Here it is used to confirm that
        the target page contain the desired node.

    Raises:
      errors.ContextError: Fail to reach expected page.
    """
    end_time = time.monotonic() + wait_sec
    while time.monotonic() < end_time:
      page = self.safe_get_page()
      if any((isinstance(page, page_class) for page_class in page_class_list)):
        if node_eval is not None and page.get_node_by_func(node_eval) is None:
          continue

        return

    raise errors.ContextError(
        self,
        f'Fail to reach page(s): {page_class_list} (current page={page})!')

  def expect_page(self,
                  page_class: Type[UIPage],
                  wait_sec: int = _EXPECT_PAGE_WAIT_TIME_IN_SECOND,
                  node_eval: NodeEvaluator = None) -> None:
    """Waits for expected page for certain time."""
    self.expect_pages([page_class], wait_sec=wait_sec, node_eval=node_eval)

  def get_page(self,
               wait_sec: int = 1,
               xml_path: Optional[str] = None) -> UIPage:
    if self.safe_get:
      return self.safe_get_page(wait_sec=wait_sec, xml_path=xml_path)
    else:
      return self.strict_get_page(wait_sec=wait_sec, xml_path=xml_path)

  def safe_get_page(self,
                    wait_sec: int = 1,
                    xml_path: Optional[str] = None) -> UIPage:
    """Gets the represented UIPage object of current UI page safely.

    Args:
      wait_sec: Wait in second before actions.
      xml_path: Target XML file path. If this argument is given, this method
        will parse this XML file instead of dumping XML file from adb.

    Returns:
      The focused UIPage object will be returned iff the XML object can
      be obtained and recognized. Otherwise, NonePage is returned.
    """
    try:
      self.strict_get_page(wait_sec=wait_sec, xml_path=xml_path)
    except errors.UnknownPageError as err:
      self.ad.log.warning(str(err))
      self.page = NonePage(
          self,
          ui_xml=err.ui_xml,
          clickable_nodes=err.clickable_nodes,
          enabled_nodes=err.enabled_nodes,
          all_nodes=err.all_nodes)

    return self.page

  def strict_get_page(self,
                      wait_sec: int = 1,
                      xml_path: Optional[str] = None) -> Optional[UIPage]:
    """Gets the represented UIPage object of current UI page.

    This method will use adb command to dump UI XML file into local and use
    the content of the dumped XML file to decide the proper page class and
    instantiate it to self.page

    Args:
      wait_sec: Wait in second before actions.
      xml_path: Target XML file path. If this argument is given, this method
        will parse this XML file instead of dumping XML file from adb.

    Returns:
      The focused UIPage object will be returned iff the XML object can
      be obtained and recognized.

    Raises:
      errors.UnknownPageError: Fail to recognize the content of
        current UI page.
      errors.ContextError: Fail to dump UI xml file.
    """
    time.sleep(wait_sec)
    ui_xml, clickable_nodes, enabled_nodes, all_nodes = self.parse_ui(xml_path)

    for page_class in self.known_pages:
      page_obj = page_class.from_xml(self, ui_xml, clickable_nodes,
                                     enabled_nodes, all_nodes)

      if page_obj:
        if self.page is not None and isinstance(page_obj, self.page.__class__):
          self.log.debug('Refreshing page %s...', self.page)
          self.page.refresh(page_obj)
        else:
          self.page = page_obj

        if self.enable_registered_page_call:
          regr_method_name = self.get_regr_page_call(self.page)
          if regr_method_name:
            return getattr(self.page, regr_method_name)()

        return self.page

    raise errors.UnknownPageError(ui_xml, clickable_nodes, enabled_nodes,
                                  all_nodes)

  def get_display_size(self) -> Tuple[int, int]:
    """Gets the display size of the device.

    Returns:
      tuple(width, height) of the display size.

    Raises:
      errors.ContextError: Obtained unexpected output of
        display size from adb.
    """
    # e.g.: Physical size: 384x384
    output = self.ad.adb.shell(shlex.split('wm size')).decode()
    size_items = output.rsplit(' ', 1)[-1].split('x')
    if len(size_items) == 2:
      return (int(size_items[0]), int(size_items[1]))

    raise errors.ContextError(self, f'Illegal output of display size: {output}')


class UIPage:
  """Object to represent the current UI page.

  Attributes:
    ctx: The context object to hold the current page.
    ui_xml: Parsed XML object.
    clickable_nodes: List of UINode with attribute `clickable="true"`
    enabled_nodes: List of UINode with attribute `enabled="true"`
    all_nodes: List of all UINode
    log: Logger object.
  """

  # Defined in subclass
  ACTIVITY = None

  # Defined in subclass
  PAGE_RID = None

  # Defined in subclass
  PAGE_TEXT = None

  # Defined in subclass
  PAGE_RE_TEXT = None

  # Defined in subclass
  PAGE_TITLE = None

  def __init__(self, ctx: Context, ui_xml: Optional[minidom.Document],
               clickable_nodes: List[ui_node.UINode],
               enabled_nodes: List[ui_node.UINode],
               all_nodes: List[ui_node.UINode]) -> None:
    self.ctx = ctx
    self.ui_xml = ui_xml
    self.clickable_nodes = clickable_nodes
    self.enabled_nodes = enabled_nodes
    self.all_nodes = all_nodes
    self.log = logging.getLogger(self.__class__.__name__)

  @classmethod
  def from_xml(cls, ctx: Context, ui_xml: minidom.Document,
               clickable_nodes: List[ui_node.UINode],
               enabled_nodes: List[ui_node.UINode],
               all_nodes: List[ui_node.UINode]) -> Optional[UIPage]:
    """Instantiates page object from XML object.

    Args:
      ctx: Page context object.
      ui_xml: Parsed XML object.
      clickable_nodes: Clickable node list from page.
      enabled_nodes: Enabled node list from page.
      all_nodes: All node list from the page.

    Returns:
      UI page object iff the given XML object can be parsed.

    Raises:
      errors.UIError: The page class doesn't provide signature
        for matching.
    """
    if cls.PAGE_RID is not None:
      for node in enabled_nodes + clickable_nodes:
        if node.resource_id == cls.PAGE_RID:
          return cls(ctx, ui_xml, clickable_nodes, enabled_nodes, all_nodes)
    elif cls.PAGE_TEXT is not None:
      for node in enabled_nodes + clickable_nodes:
        if node.text == cls.PAGE_TEXT:
          return cls(ctx, ui_xml, clickable_nodes, enabled_nodes, all_nodes)
    elif cls.PAGE_RE_TEXT is not None:
      for node in enabled_nodes + clickable_nodes:
        if re.search(cls.PAGE_RE_TEXT, node.text):
          return cls(ctx, ui_xml, clickable_nodes, enabled_nodes, all_nodes)
    elif cls.PAGE_TITLE is not None:
      for node in enabled_nodes + clickable_nodes:
        if all([
            node.resource_id == 'android:id/title', node.text == cls.PAGE_TITLE
        ]):
          return cls(ctx, ui_xml, clickable_nodes, enabled_nodes, all_nodes)
    else:
      raise errors.UIError(f'Illegal UI Page class: {cls}')

  def refresh(self, new_page: UIPage) -> UIPage:
    """Refreshes current page with obtained latest page.

    Args:
      new_page: The page with latest data for current page to be refreshed.

    Returns:
      The current refreshed UI page object.
    """
    self.ui_xml = new_page.ui_xml
    self.clickable_nodes = new_page.clickable_nodes
    self.enabled_nodes = new_page.enabled_nodes
    return self

  def _get_node_search_space(
      self, from_all: bool = False) -> Iterable[ui_node.UINode]:
    """Gets the search space of node."""
    if from_all:
      return self.all_nodes
    else:
      return itertools.chain(self.clickable_nodes, self.enabled_nodes)

  def get_node_by_content_desc(self,
                               content_desc: str,
                               from_all: bool = False) -> OptUINode:
    """Gets the first node with desired content description.

    Args:
      content_desc: Content description used for search.
      from_all: True to search from all nodes; False to search only the
        clickable or enabled nodes.

    Returns:
      Return the first node found with expected content description
      iff it exists. Otherwise, None is returned.
    """
    # TODO(user): Redesign APIs for intuitive usage.
    for node in self._get_node_search_space(from_all):
      if node.content_desc == content_desc:
        return node

    return None

  def get_node_by_func(self,
                       func: Callable[[ui_node.UINode], bool],
                       from_all: bool = False) -> OptUINode:
    """Gets the first node found by given function.

    Args:
      func: The function to search target node.
      from_all: True to search from all nodes; False to search only the
        clickable or enabled nodes.

    Returns:
      The node found by given function.
    """
    for node in self._get_node_search_space(from_all):
      if func(node):
        return node

    return None

  def get_node_by_text(self, text: str, from_all: bool = False) -> OptUINode:
    """Gets the first node with desired text.

    Args:
      text: Text used for search.
      from_all: True to search from all nodes; False to search only the
        clickable or enabled nodes.

    Returns:
      Return the first node found with expected text iff it exists.
      Otherwise, None is returned.
    """
    for node in self._get_node_search_space(from_all):
      if node.text == text:
        return node

    return None

  def _yield_node_by_rid(self,
                         rid: str,
                         from_all: bool = False) -> NodeGenerator:
    """Generates node with desired resource id."""
    for node in self._get_node_search_space(from_all):
      if ('resource-id' in node.attributes and
          node.attributes['resource-id'].value == rid):
        yield node

  def get_all_nodes_by_rid(self,
                           rid: str,
                           from_all: bool = False) -> List[ui_node.UINode]:
    """Gets all nodes with desired resource id.

    Args:
      rid: Resource id used for search.
      from_all: True to search from all nodes; False to search only the
        clickable or enabled nodes.

    Returns:
      The list of nodes found with expected resource id.
    """
    found_node_set = set(self._yield_node_by_rid(rid, from_all))
    return list(found_node_set)

  def get_node_by_rid(self,
                      rid: str,
                      from_all: bool = False) -> Optional[ui_node.UINode]:
    """Gets the first node with desired resource id.

    Args:
      rid: Resource id used for search.
      from_all: True to search from all nodes; False to search only the
        clickable or enabled nodes.

    Returns:
      Return the first node found with expected resource id iff it exists.
      Otherwise, None.
    """
    try:
      return next(self._yield_node_by_rid(rid, from_all))
    except StopIteration:
      return None

  def get_node_by_class(self,
                        class_name: str,
                        from_all: bool = False) -> Optional[ui_node.UINode]:
    """Gets the first node with desired class.

    Args:
      class_name: Name of class as attribute.
      from_all: True to search from all nodes; False to search only the
        clickable or enabled nodes.

    Returns:
      Return the first node found with desired class iff it exists.
      Otherwise, None.
    """
    for node in self._get_node_search_space(from_all):
      if node.clz == class_name:
        return node

    return None

  def get_node_by_attrs(self,
                        attrs: Dict[str, Any],
                        from_all: bool = False) -> OptUINode:
    """Gets the first node with the given attributes.

    Args:
      attrs: Attributes used to search target node.
      from_all: True to search from all nodes; False to search only the
        clickable or enabled nodes.

    Returns:
      Return the first UI node with expected attributes iff it exists.
      Otherwise, None is returned.
    """
    for node in self._get_node_search_space(from_all):
      if node.match_attrs(attrs):
        return node

    return None

  @utils.dr_wakeup_before_op
  def swipe(self,
            start_x: int,
            start_y: int,
            end_x: int,
            end_y: int,
            duration_ms: int,
            swipes: int = 1) -> UIPage:
    """Performs the swipe from one coordinate to another coordinate.

    Args:
      start_x: The starting X-axis coordinate.
      start_y: The starting Y-axis coordinate.
      end_x: The ending X-axis coordinate.
      end_y: The ending Y-axis coordinate.
      duration_ms: The millisecond of duration to drag.
      swipes: How many swipe to carry on.

    Returns:
      The transformed UI page.
    """
    for _ in range(swipes):
      self.ctx.ad.adb.shell(
          shlex.split(
              f'input swipe {start_x} {start_y} {end_x} {end_y} {duration_ms}'))

    return self.ctx.get_page()

  def swipe_left(self,
                 duration_ms: int = 1000,
                 x_start: float = 0.2,
                 x_end: float = 0.9,
                 swipes: int = 1) -> UIPage:
    """Performs the swipe left action.

    Args:
      duration_ms: Number of milliseconds to swipe from start point to end
        point.
      x_start: The range of width as start position
      x_end: The range of width as end position
      swipes: Round to conduct the swipe action.

    Returns:
      The transformed UI page.
    """
    width, height = self.ctx.get_display_size()
    self.log.info('Page size=(%d, %d)', width, height)
    return self.swipe(
        width * x_start,
        height * 0.5,
        width * x_end,
        height * 0.5,
        duration_ms=duration_ms,
        swipes=swipes)

  def swipe_right(self,
                  duration_ms: int = 1000,
                  x_start: float = 0.9,
                  x_end: float = 0.2,
                  swipes: int = 1) -> UIPage:
    """Performs the swipe right action.

    Args:
      duration_ms: Number of milliseconds to swipe from start point to end
        point.
      x_start: The range of width as start position
      x_end: The range of width as end position
      swipes: Round to conduct the swipe action.

    Returns:
      The transformed UI page.
    """
    width, height = self.ctx.get_display_size()
    return self.swipe(
        width * x_start,
        height * 0.5,
        width * x_end,
        height * 0.5,
        duration_ms=duration_ms,
        swipes=swipes)

  def swipe_down(self, duration_ms: int = 1000, swipes: int = 1) -> UIPage:
    """Performs the swipe down action.

    Args:
      duration_ms: Number of milliseconds to swipe from start point to end
        point.
      swipes: Round to conduct the swipe action.

    Returns:
      The transformed UI page.
    """
    width, height = self.ctx.get_display_size()
    return self.swipe(
        width * 0.5,
        height * 0.7,
        width * 0.5,
        height * 0.2,
        duration_ms=duration_ms,
        swipes=swipes)

  def swipe_up(self, duration_ms: int = 1000, swipes: int = 1) -> UIPage:
    """Performs the swipe up action.

    Args:
      duration_ms: Number of milliseconds to swipe from start point to end
        point.
      swipes: Round to conduct the swipe action.

    Returns:
      The transformed UI page.
    """
    width, height = self.ctx.get_display_size()
    return self.swipe(
        width * 0.5,
        height * 0.2,
        width * 0.5,
        height * 0.7,
        duration_ms=duration_ms,
        swipes=swipes)

  def click_on(self, x: int, y: int) -> None:
    """Clicks on the given X/Y coordinates.

    Args:
      x: X-axis coordinate
      y: Y-axis coordinate

    Raises:
      acts.controllers.adb.AdbError: If the adb shell command
        failed to execute.
    """
    self.ctx.ad.adb.shell(shlex.split(f'input tap {x} {y}'))

  @utils.dr_wakeup_before_op
  def click(self,
            node: ui_node.UINode,
            do_get_page: bool = True) -> Optional[UIPage]:
    """Clicks the given UI node.

    Args:
      node: Node to click on.
      do_get_page: Gets the latest page after clicking iff True.

    Returns:
      The transformed UI page is returned iff `do_get_page` is True.
      Otherwise, None is returned.
    """
    self.click_on(node.x, node.y)
    if do_get_page:
      return self.ctx.get_page()

  def click_node_by_rid(self,
                        node_rid: str,
                        do_get_page: bool = True) -> UIPage:
    """Clicks on node its resource id.

    Args:
      node_rid: Resource ID of node to search and click on.
      do_get_page: Gets the latest page after clicking iff True.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    node = self.get_node_by_rid(node_rid)
    if node is None:
      raise errors.UIError(f'Fail to find the node with resource id={node_rid}')

    return self.click(node, do_get_page)

  def click_node_by_text(self, text: str, do_get_page: bool = True) -> UIPage:
    """Clicks on node by its text.

    Args:
      text: Text of node to search and click on.
      do_get_page: Gets the latest page after clicking iff True.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    node = self.get_node_by_text(text)
    if node is None:
      raise errors.UIError(f'Fail to find the node with text={text}')

    return self.click(node, do_get_page)

  def click_node_by_content_desc(self,
                                 text: str,
                                 do_get_page: bool = True) -> UIPage:
    """Clicks on node by its content description.

    Args:
      text: Content description of node to search and click on.
      do_get_page: Gets the latest page after clicking iff True.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    node = self.get_node_by_content_desc(text)
    if node is None:
      raise errors.UIError(
          f'Fail to find the node with content description={text}')

    return self.click(node, do_get_page)

  def click_node_by_class(self,
                          class_value: str,
                          do_get_page: bool = True) -> UIPage:
    """Clicks on node by its class attribute value.

    Args:
      class_value: Value of class attribute.
      do_get_page: Gets the latest page after clicking iff True.

    Returns:
      The transformed page.

    Raises:
      errors.UIError: Fail to get target node.
    """
    node = self.get_node_by_class(class_value)
    if node is None:
      raise errors.UIError(f'Fail to find the node with class={class_value}')

    return self.click(node, do_get_page)


class NonePage(UIPage):
  """None page to handle the context when we fail to dump UI xml file."""
