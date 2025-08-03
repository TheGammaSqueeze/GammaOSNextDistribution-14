"""UI Node is used to compose the UI pages."""
from __future__ import annotations

import collections
from typing import Any, Dict, List, Optional
from xml.dom import minidom

# Internal import


class UINode:
  """UI Node to hold element of UI page.

  If both x and y axis are given in constructor, this node will use (x, y)
  as coordinates. Otherwise, the attribute `bounds` of node will be used to
  calculate the coordinates.

  Attributes:
    node: XML node element.
    x: x point of UI page.
    y: y point of UI page.
  """

  STR_FORMAT = "RID='{rid}'/CLASS='{clz}'/TEXT='{txt}'/CD='{ctx}'"
  PREFIX_SEARCH_IN = 'c:'

  def __init__(self, node: minidom.Element,
               x: Optional[int] = None, y: Optional[int] = None) -> None:
    self.node = node
    if x and y:
      self.x = x
      self.y = y
    else:
      self.x, self.y = adb_ui.find_point_in_bounds(
          self.attributes['bounds'].value)

  def __hash__(self) -> int:
    return id(self.node)

  @property
  def clz(self) -> str:
    """Returns the class of node."""
    return self.attributes['class'].value

  @property
  def text(self) -> str:
    """Gets text of node.

    Returns:
      The text of node.
    """
    return self.attributes['text'].value

  @property
  def content_desc(self) -> str:
    """Gets content description of node.

    Returns:
      The content description of node.
    """
    return self.attributes['content-desc'].value

  @property
  def resource_id(self) -> str:
    """Gets resource id of node.

    Returns:
      The resource id of node.
    """
    return self.attributes['resource-id'].value

  @property
  def attributes(self) -> Dict[str, Any]:
    """Gets attributes of node.

    Returns:
      The attributes of node.
    """
    if hasattr(self.node, 'attributes'):
      return collections.defaultdict(
          lambda: None,
          getattr(self.node, 'attributes'))
    else:
      return collections.defaultdict(lambda: None)

  @property
  def child_nodes(self) -> List[UINode]:
    """Gets child node(s) of current node.

    Returns:
      The child nodes of current node if any.
    """
    return [UINode(n) for n in self.node.childNodes]

  def match_attrs_by_kwargs(self, **kwargs) -> bool:
    """Matches given attribute key/value pair with current node.

    Args:
      **kwargs: Key/value pair as attribute key/value.
        e.g.: resource_id='abc'

    Returns:
      True iff the given attributes match current node.
    """
    if 'clz' in kwargs:
      kwargs['class'] = kwargs['clz']
      del kwargs['clz']

    return self.match_attrs(kwargs)

  def match_attrs(self, attrs: Dict[str, Any]) -> bool:
    """Matches given attributes with current node.

    This method is used to compare the given `attrs` with attributes of
    current node. Only the keys given in `attrs` will be compared. e.g.:
    ```
    # ui_node has attributes {'name': 'john', 'id': '1234'}
    >>> ui_node.match_attrs({'name': 'john'})
    True

    >>> ui_node.match_attrs({'name': 'ken'})
    False
    ```

    If you don't want exact match and want to check if an attribute value
    contain specific substring, you can leverage special prefix
    `PREFIX_SEARCH_IN` to tell this method to use `in` instead of `==` for
    comparison. e.g.:
    ```
    # ui_node has attributes {'name': 'john', 'id': '1234'}
    >>> ui_node.match_attrs({'name': ui_node.PREFIX_SEARCH_IN + 'oh'})
    True

    >>> ui_node.match_attrs({'name': 'oh'})
    False
    ```

    Args:
      attrs: Attributes to compare with.

    Returns:
      True iff the given attributes match current node.
    """
    for k, v in attrs.items():
      if k not in self.attributes:
        return False

      if v and v.startswith(self.PREFIX_SEARCH_IN):
        v = v[len(self.PREFIX_SEARCH_IN):]
        if not v or v not in self.attributes[k].value:
          return False
      elif v != self.attributes[k].value:
        return False

    return True

  def __str__(self) -> str:
    """The string representation of this object.

    Returns:
      The string representation including below information:
      - resource id
      - class
      - text
      - content description.
    """
    rid = self.resource_id.strip()
    clz = self.clz.strip()
    txt = self.text.strip()
    ctx = self.content_desc.strip()
    return f"RID='{rid}'/CLASS='{clz}'/TEXT='{txt}'/CD='{ctx}'"
