"""Bluetooth stub class.

This controller offers no direct control to any device. It simply prompts the
user to perform a certain action on the device it is standing in for. For use
in test scripts where no controller for the DUT exists.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

from typing import Any, List

import six


class BtStub(object):
  """Stub for when controller class does not exist for a Bluetooth device.

  This class will simulate semi-automation by prompting user to manually
  perform actions on the Bluetooth device.
  """

  # Connection Commands
  def power_off(self) -> None:
    """Prompt the user to power off the Bluetooth device."""
    six.moves.input('Power Off Bluetooth device, then press enter.')

  def power_on(self) -> None:
    """Prompt the user to power on the Bluetooth device."""
    six.moves.input('Power ON Bluetooth device, then press enter.')

  def activate_pairing_mode(self) -> None:
    """Prompt the user to put the Bluetooth device into pairing mode."""
    six.moves.input('Put Bluetooth device into pairing mode, then press enter.')

  def get_bluetooth_mac_address(self) -> str:
    """Prompt the user to input the Bluetooth MAC address for this device.

    Returns:
      mac_address (str): the string received from user input.
    """
    mac_address = six.moves.input('Enter BT MAC address, then press enter.')
    return mac_address

  def set_device_name(self, device_name: str) -> None:
    """Prompt the user to set the device name (Carkit Only).

    Args:
      device_name: String of device name to be set.

    Returns: None
    """
    six.moves.input(f'Device name is: {device_name}')

  def factory_reset_bluetooth(self) -> None:
    """Prompt the user to factory reset Bluetooth on the device."""
    six.moves.input('Factory reset Bluetooth on the Bluetooth device, '
                    'then press enter.')

  # A2DP: Bluetooth stereo streaming protocol methods.
  def is_audio_playing(self) -> bool:
    """Prompt the user to indicate if the audio is playing.

    Returns:
      A Bool, true is audio is playing, false if not.
    """
    audio_playing = six.moves.input('Indicate if audio is playing: '
                                    'true/false.')
    return bool(audio_playing)

  # AVRCP Commands
  def volume_up(self) -> None:
    """Prompt the user to raise the volume on the Bluetooth device."""
    six.moves.input('Press the Volume Up Button on the Bluetooth device, '
                    'then press enter.')

  def volume_down(self) -> None:
    """Prompt the user to lower the volume on the Bluetooth device."""
    six.moves.input('Press the Volume Down Button on the Bluetooth device, '
                    'then press enter.')

  def track_next(self) -> None:
    """Prompt the user to skip the track on the Bluetooth device."""
    six.moves.input('Press the Skip Track Button on the Bluetooth device, '
                    'then press enter.')

  def track_previous(self) -> None:
    """Prompt the user to rewind the track on the Bluetooth device."""
    six.moves.input('Press the Rewind Track Button on the Bluetooth device, '
                    'then press enter.')

  def play(self) -> None:
    """Prompt the user to press play on the Bluetooth device."""
    six.moves.input('Press the Play Button on the Bluetooth device, '
                    'then press enter.')

  def pause(self) -> None:
    """Prompt the user to press pause on the Bluetooth device."""
    six.moves.input('Press the Pause Button on the Bluetooth device, '
                    'then press enter.')

  def repeat(self) -> None:
    """Prompt the user to set the repeat option on the device."""
    six.moves.input('Press the Repeat Button on the Bluetooth device, '
                    'then press enter.')

  def fast_forward(self) -> None:
    """Prompt the user to press the fast forward option/button on the device.

    Returns: None
    """
    six.moves.input('Press the Fast Forward Button on the Bluetooth device, '
                    'then press enter.')

  def rewind(self) -> None:
    """Prompt the user to press Rewind option on the device.

    Returns: None
    """
    six.moves.input('Press the Rewind option on the Bluetooth device, '
                    'then press enter.')

  # TODO(user): browse_media_content may need more work in terms of input
  # params and value(s) returned
  def browse_media_content(self, directory: str = '') -> List[Any]:
    """Prompt the user to enter to the paired device media folders.

    Args:
      directory: A path to the directory to browse to.

    Returns:
      List - empty
    """
    six.moves.input(f'Navigate to directory: {directory}')
    return []

  def delete_song(self, file_path: str = '') -> None:
    """Prompt the user to delete a song.

    Args:
      file_path (optional): A file path to the song to be deleted.

    Returns: None
    """
    six.moves.input(f'Delete a song {file_path}')

  def shuffle_song(self) -> None:
    """Prompt the user to shuffle a playlist.

    Returns: None
    """
    six.moves.input('Shuffle a playlist')

  # HFP (Hands Free Phone protocol) Commands
  def call_volume_up(self) -> None:
    """Prompt the user to press the volume up button on an active call.

    Returns: None
    """
    six.moves.input('Press the volume up button for an active call.')

  def call_volume_down(self) -> None:
    """Prompt the user to press the volume down button on an active call.

    Returns: None
    """
    six.moves.input('Press the volume down button for an active call.')

  def answer_phone_call(self) -> None:
    """Prompt the user to press the button to answer a phone call..

    Returns: None
    """
    six.moves.input('Press the button to answer the phone call.')

  def hangup_phone_call(self) -> None:
    """Prompt the user to press the button to hang up on an active phone call.

    Returns: None
    """
    six.moves.input('Press the button to hang up on the phone call.')

  def call_contact(self, name: str) -> None:
    """Prompt the user to select a contact from the phonebook and call.

    Args:
      name: string name of contact to call

    Returns: None
    """
    six.moves.input(f'Select contact, {name}, to call.')

  def call_number(self, phone_number: str) -> None:
    """Prompt the user to dial a phone number and call.

    Args:
      phone_number: string of phone number to dial and call

    Returns: None
    """
    six.moves.input(f'Dial phone number and initiate a call. {phone_number}')

  def swap_call(self) -> None:
    """Prompt the user to push the button to swap.

       Function swaps between the primary and secondary calls.  One call will
       be active and the other will be on hold.

    Returns: None
    """
    six.moves.input('Press the button to swap calls.')

  def merge_call(self) -> None:
    """Prompt the user to push the button to merge calls.

       Merges calls between the primary and secondary calls into a conference
       call.

    Returns: None
    """
    six.moves.input('Press the button to merge calls into a conference call.')

  def hold_call(self) -> None:
    """Prompt the user to put the primary call on hold.

    Primary call will be on hold, while the secondary call becomes active.

    Returns: None
    """
    six.moves.input('Press the hold button to put primary call on hold.')

  def mute_call(self) -> None:
    """Prompt the user to mute the ongoing active call.

    Returns: None
    """
    six.moves.input('Press Mute button on active call.')

  def unmute_call(self) -> None:
    """Prompt the user to unmute the ongoing active call.

    Returns: None
    """
    six.moves.input('Press the Unmute button on an active call.')

  def reject_phone_call(self) -> None:
    """Prompt the user to reject an incoming call.

    Returns: None
    """
    six.moves.input('Press the Reject button to reject an incoming call.')

  def answer_voip_call(self) -> None:
    """Prompt the user to press the button to answer a VOIP call.

    Returns: None
    """
    six.moves.input('Press the Answer button on an incoming VOIP phone call.')

  def hangup_voip_call(self) -> None:
    """Prompt the user to press the button to hangup on the active VOIP call.

    Returns: None
    """
    six.moves.input('Press the hangup button on the active VOIP call.')

  def reject_voip_call(self) -> None:
    """Prompt the user to press the Reject button on the incoming VOIP call.

    Returns: None
    """
    six.moves.input('Press the Reject button on the incoming VOIP call.')

  def voice_dial(self) -> None:
    """Prompt user to initiate a voice dial from the phone.

    Returns: None
    """
    six.moves.input('Initiate a voice dial.')

  def last_number_dial(self) -> None:
    """Prompt user to iniate a call to the last number dialed.

    Returns: None
    """
    six.moves.input('Initiate a call to the last number dialed.')

  # TODO(user): does this method need a input parameter?
  def route_call_audio(self) -> None:
    """Prompt user to route a call from AG to HF, and vice versa.

    Returns: None
    """
    six.moves.input('Reroute call audio.')
