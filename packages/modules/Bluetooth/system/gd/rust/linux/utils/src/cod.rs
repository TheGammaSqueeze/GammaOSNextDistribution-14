//! This library provides helper functions to parse cod.

struct Class {
    major: u32,
    minor: u32,
}

impl Class {
    fn new(cod: u32) -> Class {
        Class { major: (cod & 0x1f00) >> 8, minor: (cod & 0xff) }
    }
}

pub fn is_cod_hid_keyboard(cod: u32) -> bool {
    let c = Class::new(cod);
    c.major == 0x05 && ((c.minor >> 6) & 0x03) == 0x01
}

pub fn is_cod_hid_combo(cod: u32) -> bool {
    let c = Class::new(cod);
    c.major == 0x05 && ((c.minor >> 6) & 0x03) == 0x03
}

#[cfg(test)]
mod tests {
    use crate::cod::{is_cod_hid_combo, is_cod_hid_keyboard};

    #[test]
    fn test_cod() {
        let keyboard_gamepad_cod = 0x0548;
        let combo_joystick_cod = 0x05c4;
        let mouse_cod = 0x0580;

        assert_eq!(is_cod_hid_keyboard(keyboard_gamepad_cod), true);
        assert_eq!(is_cod_hid_combo(keyboard_gamepad_cod), false);
        assert_eq!(is_cod_hid_keyboard(combo_joystick_cod), false);
        assert_eq!(is_cod_hid_combo(combo_joystick_cod), true);
        assert_eq!(is_cod_hid_keyboard(mouse_cod), false);
        assert_eq!(is_cod_hid_combo(mouse_cod), false);
    }
}
