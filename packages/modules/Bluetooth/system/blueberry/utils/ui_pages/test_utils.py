"""Utility used for unit test."""
from typing import Dict, Sequence


class MockNode:
  """Mock node class."""

  def __init__(self, attrs: Dict[str, str],
               child_attrs: Sequence[Dict[str, str]] = ({},),
               is_child: bool = False):
    self.attributes = attrs
    self.childs = [
        MockNode(attrs, is_child=True) for attrs in child_attrs if attrs]

    self.is_child = is_child
    if 'bounds' not in self.attributes:
      self.attributes['bounds'] = '[0,0][384,384]'

  def __str__(self):
    xml_str_elements = []
    if not self.is_child:
      xml_str_elements.append(
          "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>")
      xml_str_elements.append('<hierarchy rotation="0">')

    xml_str_elements.append('<node index="0"')
    for attr_key, attr_val in self.attributes.items():
      xml_str_elements.append(f' {attr_key}="{attr_val}"')
    xml_str_elements.append('>')

    for child in self.childs:
      xml_str_elements.append(str(child))

    xml_str_elements.append('</node>')

    if not self.is_child:
      xml_str_elements.append('</hierarchy>')

    return ''.join(xml_str_elements)
