"""Metrics reporting module for Blueberry using protobuf.

Internal reference
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import base64
import logging
import time

# Internal import


class BluetoothMetricLogger(object):
  """A class used for gathering metrics from tests and devices.

  This class provides methods to allow test writers to easily export metrics
  from their tests as protobuf messages.

  Attributes:
    _metrics: The Bluetooth test proto message to add metrics to.
  """

  def __init__(self, bluetooth_test_proto_message):
    self._metrics = bluetooth_test_proto_message
    self._start_time = int(time.time())

  def add_primary_device_metrics(self, device):
    """Adds primary device metrics to the test proto message.

    Args:
      device: The Bluetooth device object to gather device metrics from.
    """
    device_message = self._metrics.configuration_data.primary_device
    message_fields = device_message.DESCRIPTOR.fields_by_name.keys()
    try:
      device_metrics_dict = device.get_device_info()
    except AttributeError:
      logging.info(
          'Must implement get_device_info method for this controller in order to upload device metrics.'
      )
      return

    for metric in device_metrics_dict:
      if metric in message_fields:
        setattr(device_message, metric, device_metrics_dict[metric])
      else:
        logging.info('%s is not a valid metric field.', metric)

  def add_connected_device_metrics(self, device):
    """Adds connected device metrics to the test proto message.

    Args:
      device: The Bluetooth device object to gather device metrics from.
    """
    device_message = self._metrics.configuration_data.connected_device
    message_fields = device_message.DESCRIPTOR.fields_by_name.keys()
    try:
      device_metrics_dict = device.get_device_info()
    except AttributeError:
      logging.info(
          'Must implement get_device_info method for this controller in order to upload device metrics.'
      )
      return

    for metric in device_metrics_dict:
      if metric in message_fields:
        setattr(device_message, metric, device_metrics_dict[metric])
      else:
        logging.warning('%s is not a valid metric field.', metric)

  def add_test_metrics(self, test_metrics_dict):
    """Adds test metrics to the test proto message.

    Args:
      test_metrics_dict: A dictionary of metrics to add to the test proto
      message. Metric will only be added if the key exists as a field in the
      test proto message.
    """
    if hasattr(self._metrics, 'configuration_data'):
      self._metrics.configuration_data.test_date_time = self._start_time
    message_fields = self._metrics.DESCRIPTOR.fields_by_name.keys()
    for metric in test_metrics_dict:
      if metric in message_fields:
        metric_value = test_metrics_dict[metric]
        if isinstance(metric_value, (list, tuple)):
          getattr(self._metrics, metric).extend(metric_value)
        else:
          setattr(self._metrics, metric, metric_value)
      else:
        logging.warning('%s is not a valid metric field.', metric)

  def proto_message_to_base64(self):
    """Converts a proto message to a base64 string.

    Returns:
    string, Message formatted as a base64 string.
    """
    return base64.b64encode(self._metrics.SerializeToString()).decode('utf-8')

  def proto_message_to_ascii(self):
    """Converts a proto message to an ASCII string.

    Returns:
      string, Message formatted as an ASCII string. Useful for debugging.
    """
    return text_format.MessageToString(self._metrics)
