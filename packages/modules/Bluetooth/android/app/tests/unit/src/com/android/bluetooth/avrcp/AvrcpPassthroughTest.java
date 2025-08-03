package com.android.bluetooth.avrcp;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAvrcp;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AvrcpPassthroughTest {

  @Test
  public void toKeyCode() {
    AvrcpPassthrough ap = new AvrcpPassthrough();
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_UP))
            .isEqualTo(KeyEvent.KEYCODE_DPAD_UP);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_DOWN))
            .isEqualTo(KeyEvent.KEYCODE_DPAD_DOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_LEFT))
            .isEqualTo(KeyEvent.KEYCODE_DPAD_LEFT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_RIGHT))
            .isEqualTo(KeyEvent.KEYCODE_DPAD_RIGHT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_RIGHT_UP))
            .isEqualTo(KeyEvent.KEYCODE_DPAD_UP_RIGHT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_RIGHT_DOWN))
            .isEqualTo(KeyEvent.KEYCODE_DPAD_DOWN_RIGHT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_LEFT_UP))
            .isEqualTo(KeyEvent.KEYCODE_DPAD_UP_LEFT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_LEFT_DOWN))
            .isEqualTo(KeyEvent.KEYCODE_DPAD_DOWN_LEFT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_0))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_0);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_1))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_1);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_2))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_2);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_3))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_3);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_4))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_4);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_5))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_5);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_6))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_6);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_7))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_7);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_8))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_8);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_9))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_9);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_DOT))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_DOT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_ENTER))
            .isEqualTo(KeyEvent.KEYCODE_NUMPAD_ENTER);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_CLEAR))
            .isEqualTo(KeyEvent.KEYCODE_CLEAR);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_CHAN_DOWN))
            .isEqualTo(KeyEvent.KEYCODE_CHANNEL_DOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_PREV_CHAN))
            .isEqualTo(KeyEvent.KEYCODE_LAST_CHANNEL);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_INPUT_SEL))
            .isEqualTo(KeyEvent.KEYCODE_TV_INPUT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_DISP_INFO))
            .isEqualTo(KeyEvent.KEYCODE_INFO);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_HELP))
            .isEqualTo(KeyEvent.KEYCODE_HELP);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_PAGE_UP))
            .isEqualTo(KeyEvent.KEYCODE_PAGE_UP);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_PAGE_DOWN))
            .isEqualTo(KeyEvent.KEYCODE_PAGE_DOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_POWER))
            .isEqualTo(KeyEvent.KEYCODE_POWER);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_VOL_UP))
            .isEqualTo(KeyEvent.KEYCODE_VOLUME_UP);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_VOL_DOWN))
            .isEqualTo(KeyEvent.KEYCODE_VOLUME_DOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_MUTE))
            .isEqualTo(KeyEvent.KEYCODE_MUTE);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_PLAY))
            .isEqualTo(KeyEvent.KEYCODE_MEDIA_PLAY);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_STOP))
            .isEqualTo(KeyEvent.KEYCODE_MEDIA_STOP);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_PAUSE))
            .isEqualTo(KeyEvent.KEYCODE_MEDIA_PAUSE);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_RECORD))
            .isEqualTo(KeyEvent.KEYCODE_MEDIA_RECORD);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_REWIND))
            .isEqualTo(KeyEvent.KEYCODE_MEDIA_REWIND);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_FAST_FOR))
            .isEqualTo(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_EJECT))
            .isEqualTo(KeyEvent.KEYCODE_MEDIA_EJECT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_FORWARD))
            .isEqualTo(KeyEvent.KEYCODE_MEDIA_NEXT);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_BACKWARD))
            .isEqualTo(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_F1))
            .isEqualTo(KeyEvent.KEYCODE_F1);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_F2))
            .isEqualTo(KeyEvent.KEYCODE_F2);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_F3))
            .isEqualTo(KeyEvent.KEYCODE_F3);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_F4))
            .isEqualTo(KeyEvent.KEYCODE_F4);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_F5))
            .isEqualTo(KeyEvent.KEYCODE_F5);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_SELECT))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_ROOT_MENU))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_SETUP_MENU))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_CONT_MENU))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_FAV_MENU))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_EXIT))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_SOUND_SEL))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_ANGLE))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_SUBPICT))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
    assertThat(ap.toKeyCode(BluetoothAvrcp.PASSTHROUGH_ID_VENDOR))
            .isEqualTo(KeyEvent.KEYCODE_UNKNOWN);
  }
}
