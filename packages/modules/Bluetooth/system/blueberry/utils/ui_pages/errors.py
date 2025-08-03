"""Module for errors thrown from ui_pages."""

from typing import List, Optional
from xml.dom import minidom
from blueberry.utils.ui_pages import ui_node


class Error(Exception):
  pass


class ContextError(Error):
  """Context related error."""

  def __init__(self, ctx, msg):
    new_msg = f'{ctx.ad}: {msg}'
    super().__init__(new_msg)


class UIError(Error):
  """UI page related error."""
  pass


class UnknownPageError(Error):
  """UI page error for unknown XML content.

  Attributes:
    ui_xml: Parsed XML object.
    clickable_nodes: List of UINode with attribute `clickable="true"`
    enabled_nodes: List of UINode with attribute `enabled="true"`
    all_nodes: List of all UINode
  """

  def __init__(self,
               ui_xml: minidom.Document,
               clickable_nodes: Optional[List[ui_node.UINode]] = None,
               enabled_nodes: Optional[List[ui_node.UINode]] = None,
               all_nodes: Optional[List[ui_node.UINode]] = None):
    new_msg = f'Unknown ui_xml:\n{ui_xml.toxml()}\n'
    self.ui_xml = ui_xml
    self.enabled_nodes = enabled_nodes
    self.clickable_nodes = clickable_nodes
    self.all_nodes = all_nodes
    super().__init__(new_msg)
