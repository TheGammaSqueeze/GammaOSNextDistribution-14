"""Abstraction for interacting with Keysight IMS emulator.

Keysight provided an API Connector application which is a HTTP server
running on the same host as Keysight IMS server app and client app.
It allows IMS simulator/app to be controlled via HTTP request.
"""
import enum
import json
import logging
import time
from typing import List, Optional, Any
import uuid
import requests

from acts_contrib.test_utils.power.cellular.ssh_library import SshLibrary

_LOG = logging.getLogger(__name__)


class ImsAppName(enum.Enum):
  """IMS app name predefined by Keysight."""

  CLIENT = 'client'
  SERVER = 'server'


class ImsApiConnector:
  """A wrapper class for Keysight Ims API Connector.

  This class provides high-level interface to control Keysigt IMS app.
  Each instance of this class conresponding to one IMS app process.

  Attributes:
    log: An logger object.
    api_connector_ip: An IP of host where API Connector reside.
    api_connector_port: A port number of API Connector server.
    ims_app: An ImsAppName enum to specify which emulator/app to control.
    _api_token: An arbitrary and unique string to identify the link between API
      connector and ims app.
    _ims_app_ip: An IP of IMS emulator/app, usually the value of localhost.
    _ims_app_port: Listening port of IMS emulator/app.
    ssh: An ssh connection object that can be used to check app status, close
      and reopen apps.
  """

  _BASE_URL_FORMAT = 'http://{addr}:{port}/ims/api/{app}s/{api_token}'

  _SSH_USERNAME = 'User'
  _APP_BOOT_TIME = 30

  _IMS_CLIENT_IDLE_STATUS = 'Idle'
  _IMS_CLIENT_APP = 'Keysight.ImsSip.Client.exe'
  _IMS_CLIENT_APP_LOC = (
      r'C:\Program Files (x86)\Keysight\C8700201A\IMS-SIP Client\\'
  )
  _IMS_SERVER_APP = 'Keysight.ImsSip.Server.exe'
  _IMS_SERVER_APP_LOC = (
      r'C:\Program Files (x86)\Keysight\C8700201A\IMS-SIP Server\\'
  )
  _IMS_API_APP = "Keysight.ImsSip.ApiConnector.exe"
  _IMS_API_APP_LOC = (
      r"C:\Program Files (x86)\Keysight\C8700201A\IMS-SIP API Connector\\"
  )
  _IMS_APP_DEFAULT_PORT_MAPPING = {
      ImsAppName.CLIENT: 8250,
      ImsAppName.SERVER: 8240,
  }

  _IMS_APP_DEFAULT_IP = '127.0.0.1'

  def __init__(
      self,
      api_connector_ip: str,
      api_connector_port: int,
      ims_app: ImsAppName,
  ):
    self.log = _LOG

    # create ssh connection to host pc
    self.ssh = SshLibrary(api_connector_ip, self._SSH_USERNAME)

    # api connector info
    self.api_connector_ip = api_connector_ip
    self.api_connector_port = api_connector_port

    # ims app info
    self._api_token = str(uuid.uuid4())[0:7]  # api token can't contain '-'
    self.log.debug('API token: %s', self._api_token)
    self.ims_app = ims_app.value
    self._ims_app_ip = self._IMS_APP_DEFAULT_IP
    self._ims_app_port = self._IMS_APP_DEFAULT_PORT_MAPPING[ims_app]

    # construct base url
    self._base_url = self._BASE_URL_FORMAT.format(
        addr=self.api_connector_ip,
        port=self.api_connector_port,
        app=self.ims_app,
        api_token=self._api_token,
    )

    # start server and client if they are not started
    self._start_apps_if_down()
    # create IMS-Client API link
    is_app_linked = self.create_ims_app_link()

    if not is_app_linked:
      raise RuntimeError('Fail to create link to IMS app.')

  def log_response_info(self, r: requests.Response):
    self.log.debug('HTTP request sent:')
    self.log.debug('-> method: %s', str(r.request.method))
    self.log.debug('-> url: %s', str(r.url))
    self.log.debug('-> status_code: %s', str(r.status_code))

  def create_ims_app_link(self) -> bool:
    """Creates link between Keysight API Connector to ims app.

    Returns:
      True if API connector server linked/connected with ims app,
      False otherwise.
    """
    self.log.info('Creating ims_%s link: ', self.ims_app)
    self.log.info(
        '%s:%s:%s', self._api_token, self._ims_app_ip, self._ims_app_port
    )

    request_data = {
        'targetIpAddress': self._ims_app_ip,
        'targetWcfPort': self._ims_app_port,
    }

    r = requests.post(url=self._base_url, json=request_data)
    self.log_response_info(r)

    return r.status_code == requests.codes.created

  def _remove_ims_app_link(self) -> bool:
    """Removes link between Keysight API Connector to ims app.

    Returns:
      True if successfully disconnected/unlinked,
      False otherwise.
    """
    self.log.info('Remove ims_%s link: %s', self.ims_app, self._api_token)

    r = requests.delete(url=self._base_url)
    self.log_response_info(r)

    return r.status_code == requests.codes.ok

  def get_ims_app_property(self, property_name: str) -> Optional[str]:
    """Gets property value of IMS app.

    Args:
      property_name: Name of property to get value.

    Returns:
      Value of property which is inquired.
    """
    self.log.info('Getting ims app property: %s', property_name)

    request_url = self._base_url + '/get_property'
    request_params = {'propertyName': property_name}
    r = requests.get(url=request_url, params=request_params)
    self.log_response_info(r)

    try:
      res_json = r.json()
    except json.JSONDecodeError:
      res_json = {'propertyValue': None}
    prop_value = res_json['propertyValue']

    return prop_value

  def set_ims_app_property(
      self, property_name: str, property_value: Optional[Any]
  ) -> bool:
    """Sets property value of IMS app.

    Args:
      property_name: Name of property to set value.
      property_value: Value to be set.

    Returns:
      True if success, False otherwise.
    """
    self.log.info(
        'Setting ims property: %s = %s', property_name, str(property_value)
    )

    request_url = self._base_url + '/set_property'
    data = {'propertyName': property_name, 'propertyValue': property_value}
    r = requests.post(url=request_url, json=data)
    self.log_response_info(r)

    return r.status_code == requests.codes.ok

  def ims_api_call_method(
      self, method_name: str, method_args: List = []
  ) -> Optional[str]:
    """Call Keysight API to control simulator.

    API Connector allows us to call Keysight Simulators' API without using C#.
    To invoke an API, we are sending post request to API Connector (http
    server).

    Args:
      method_name: A name of method from Keysight API in string.
      method_args: A python-array contains arguments for the called API.

    Returns:
      A string value parse from response.

    Raises:
      HTTPError: Response status code is different than requests.codes.ok.
    """
    self.log.info('Calling Keysight simulator API: %s', method_name)

    if not isinstance(method_args, list):
      method_args = [method_args]
    request_url = self._base_url + '/call_method'
    request_data = {'methodName': method_name, 'arguments': method_args}
    r = requests.post(url=request_url, json=request_data)

    ret_val = None

    if r.status_code == requests.codes.ok:
      return_value_key = 'returnValue'
      if ('Content-Type' in r.headers) and r.headers[
          'Content-Type'
      ] == 'application/json':
        response_body = r.json()
        if response_body:
          ret_val = response_body.get(return_value_key, None)
    else:
      raise requests.HTTPError(r.status_code, r.text)

    self.log_response_info(r)

    return ret_val

  def _is_line_idle(self, call_line_number) -> bool:
    is_line_idle_prop = self.get_ims_app_property(
        f'IVoip.CallLineParams({call_line_number}).SessionState'
    )
    return is_line_idle_prop == self._IMS_CLIENT_IDLE_STATUS

  def _is_ims_client_app_registered(self) -> bool:
    is_registered_prop = self.get_ims_app_property(
        'IComponentControl.IsRegistered'
    )
    self.log.info('Registered: %s', str(is_registered_prop))
    return is_registered_prop == 'True'

  def restart_server(self) -> bool:
    """Restarts the ims server application.

    Returns:
      A boolean representing if the application was successfully started
    """
    self.create_ims_app_link()
    self.log.info('Stopping and starting server')
    self.ims_api_call_method('IServer.StopListeners()')
    result = self.ims_api_call_method('IServer.Start()')
    self.log.info(result)
    return result == 'True'

  def reregister_client(self):
    """Re-registers the ims client with the server.

    Attempts to unregister, then register the client. If this fails, Attempts
    to restart both the client and server apps.
    """
    self.ims_api_call_method('ISipConnection.Unregister()')
    self.ims_api_call_method('ISipConnection.Register()')

    # failed to re-register client, so try restarting server and client
    if not self._is_ims_client_app_registered():
      self._restart_client_server_app()
      self.ims_api_call_method('ISipConnection.Register()')

  def _restart_client_server_app(self):
    """Restarts the client and server app."""
    self.ssh.close_app(self._IMS_CLIENT_APP)
    self.ssh.close_app(self._IMS_SERVER_APP)
    self.ssh.start_app(self._IMS_CLIENT_APP, self._IMS_CLIENT_APP_LOC)
    self.ssh.start_app(self._IMS_SERVER_APP, self._IMS_SERVER_APP_LOC)
    time.sleep(self._APP_BOOT_TIME)
    self.create_ims_app_link()

  def _start_apps_if_down(self):
    """Starts the client, server, api connector app if they are down."""
    started = False

    if not self.ssh.check_app_running(self._IMS_API_APP):
      self.log.info('api connector was not running, starting now')
      self.ssh.start_app(self._IMS_API_APP, self._IMS_API_APP_LOC)
      started = True

    if not self.ssh.check_app_running(self._IMS_CLIENT_APP):
      self.log.info('client was not running, starting now')
      self.ssh.start_app(self._IMS_CLIENT_APP, self._IMS_CLIENT_APP_LOC)
      started = True

    if not self.ssh.check_app_running(self._IMS_SERVER_APP):
      self.log.info('server was not running, starting now')
      self.ssh.start_app(self._IMS_SERVER_APP, self._IMS_SERVER_APP_LOC)
      started = True

    if started:
      time.sleep(self._APP_BOOT_TIME)

  def initiate_call(self, callee_number: str, call_line_idx: int = 0):
    """Dials to callee_number.

    Args:
      callee_number: A string value of number to be dialed to.
      call_line_idx: An inteter index for call line.

    Raises:
      RuntimeError: If ims client is cannot be registered.
      RuntimeError: If ims client is not idle when it attempts to dial.
      RuntimeError: If ims client is still in idle after starting dial.
    """
    sleep_time = 5

    self.log.info('checking if server/client/api connector are registered and running')
    self._start_apps_if_down()

    # Reregister client to server
    self.reregister_client()

    # clear logs
    self.ims_api_call_method('ILogs.ClearResults()')

    # switch to call-line #1 (idx = 0)
    self.log.info('Switching to call-line #1.')
    self.set_ims_app_property('IVoip.SelectedCallLine', call_line_idx)

    # check whether the call-line #1 is ready for dialling
    is_line1_idle = self._is_line_idle(call_line_idx)
    if not is_line1_idle:
      raise RuntimeError('Call-line not is not in indle state.')

    # entering callee number for call-line #1
    self.log.info('Enter callee number: %s.', callee_number)
    self.set_ims_app_property(
        'IVoip.CallLineParams(0).CallLocation', callee_number
    )

    # dial entered callee number
    self.log.info('Dialing call.')
    self.ims_api_call_method('IVoip.Dial()')

    time.sleep(sleep_time)

    # check if dial success (not idle)
    if self._is_line_idle(call_line_idx):
      raise RuntimeError('Fail to dial.')

  def hangup_call(self):
    self.ims_api_call_method('IVoip.HangUp()')
    # get logs
    self.log.info("Call Logs: ")
    call_logs = self.get_ims_app_property('ILogs.Results')
    self.log.info(call_logs)

  def tear_down(self):
    self._remove_ims_app_link()
    self.ssh.close_ssh_connection()
