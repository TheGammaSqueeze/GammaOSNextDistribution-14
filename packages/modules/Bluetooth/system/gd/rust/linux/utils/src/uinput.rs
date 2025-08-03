//! This library provides access to Linux uinput.

use libc;
use nix;
use std::ffi::CString;
use std::mem;
use std::slice;

// Supported AVRCP Keys
const AVC_PLAY: u8 = 0x44;
const AVC_STOP: u8 = 0x45;
const AVC_PAUSE: u8 = 0x46;
const AVC_REWIND: u8 = 0x48;
const AVC_FAST_FORWAED: u8 = 0x49;
const AVC_FORWARD: u8 = 0x4B;
const AVC_BACKWARD: u8 = 0x4C;

// Supported uinput keys
const KEY_NEXTSONG: libc::c_uint = 163;
const KEY_PREVIOUSSONG: libc::c_uint = 165;
const KEY_STOPCD: libc::c_uint = 166;
const KEY_REWIND: libc::c_uint = 168;
const KEY_PLAYCD: libc::c_uint = 200;
const KEY_PAUSECD: libc::c_uint = 201;
const KEY_FASTFORWAED: libc::c_uint = 208;

// uinput setup constants
const UINPUT_MAX_NAME_SIZE: usize = 80;
const UINPUT_SUFFIX: &str = " (AVRCP)";
const UINPUT_SUFFIX_SIZE: usize = UINPUT_SUFFIX.len();
const ABS_MAX: usize = 0x3F;
const BUS_BLUETOOTH: u16 = 0x05;
const UINPUT_IOCTL_BASE: libc::c_char = 'U' as libc::c_char;

const EV_SYN: libc::c_int = 0x00;
const EV_KEY: libc::c_int = 0x01;
const EV_REL: libc::c_int = 0x02;
const EV_REP: libc::c_int = 0x14;

const SYN_REPORT: libc::c_int = 0;

const UI_DEV_CREATE: libc::c_ulong = nix::request_code_none!(UINPUT_IOCTL_BASE, 1);
const UI_DEV_DESTROY: libc::c_ulong = nix::request_code_none!(UINPUT_IOCTL_BASE, 2);
const UI_SET_EVBIT: libc::c_ulong =
    nix::request_code_write!(UINPUT_IOCTL_BASE, 100, mem::size_of::<libc::c_int>());
const UI_SET_PHYS: libc::c_ulong =
    nix::request_code_write!(UINPUT_IOCTL_BASE, 108, mem::size_of::<libc::c_char>());
const UI_SET_KEYBIT: libc::c_ulong =
    nix::request_code_write!(UINPUT_IOCTL_BASE, 101, mem::size_of::<libc::c_int>());

// Conversion key map from AVRCP keys to uinput keys.
#[allow(dead_code)]
struct KeyMap {
    avc: u8,
    uinput: libc::c_uint,
}

const KEY_MAP: [KeyMap; 7] = [
    KeyMap { avc: AVC_PLAY, uinput: KEY_PLAYCD },
    KeyMap { avc: AVC_STOP, uinput: KEY_STOPCD },
    KeyMap { avc: AVC_PAUSE, uinput: KEY_PAUSECD },
    KeyMap { avc: AVC_REWIND, uinput: KEY_REWIND },
    KeyMap { avc: AVC_FAST_FORWAED, uinput: KEY_FASTFORWAED },
    KeyMap { avc: AVC_FORWARD, uinput: KEY_NEXTSONG },
    KeyMap { avc: AVC_BACKWARD, uinput: KEY_PREVIOUSSONG },
];

#[repr(C, packed)]
struct UInputId {
    bustype: libc::c_ushort,
    vendor: libc::c_ushort,
    product: libc::c_ushort,
    version: libc::c_ushort,
}

impl Default for UInputId {
    fn default() -> Self {
        UInputId { bustype: BUS_BLUETOOTH, vendor: 0, product: 0, version: 0 }
    }
}

#[repr(C, packed)]
struct UInputDevInfo {
    name: [libc::c_char; UINPUT_MAX_NAME_SIZE],
    id: UInputId,
    ff_effects_max: libc::c_int,
    absmax: [libc::c_int; ABS_MAX + 1],
    absmin: [libc::c_int; ABS_MAX + 1],
    absfuzz: [libc::c_int; ABS_MAX + 1],
    absflat: [libc::c_int; ABS_MAX + 1],
}

impl Default for UInputDevInfo {
    fn default() -> Self {
        UInputDevInfo {
            name: [0; UINPUT_MAX_NAME_SIZE],
            id: UInputId::default(),
            ff_effects_max: 0,
            absmax: [0; ABS_MAX + 1],
            absmin: [0; ABS_MAX + 1],
            absfuzz: [0; ABS_MAX + 1],
            absflat: [0; ABS_MAX + 1],
        }
    }
}

impl UInputDevInfo {
    pub fn serialize(&mut self) -> &[u8] {
        unsafe {
            slice::from_raw_parts(
                (self as *const UInputDevInfo) as *const u8,
                mem::size_of::<UInputDevInfo>(),
            )
        }
    }
}

#[repr(C, packed)]
struct UInputEvent {
    time: libc::timeval,
    event_type: libc::c_ushort,
    code: libc::c_ushort,
    value: libc::c_int,
}

impl UInputEvent {
    pub fn serialize(&mut self) -> &[u8] {
        unsafe {
            slice::from_raw_parts(
                (self as *const UInputEvent) as *const u8,
                mem::size_of::<UInputEvent>(),
            )
        }
    }
}

struct UInputDev {
    fd: i32,
    addr: String,
    device: UInputDevInfo,
}

impl Default for UInputDev {
    fn default() -> Self {
        UInputDev {
            fd: -1,
            addr: String::from("00:00:00:00:00:00"),
            device: UInputDevInfo::default(),
        }
    }
}

impl Drop for UInputDev {
    fn drop(&mut self) {
        self.close();
    }
}

impl UInputDev {
    #[allow(temporary_cstring_as_ptr)]
    fn init(&mut self, mut name: String, addr: String) -> Result<(), String> {
        // Truncate the device name if over the max size allowed.
        name.truncate(UINPUT_MAX_NAME_SIZE - UINPUT_SUFFIX_SIZE);
        name.push_str(UINPUT_SUFFIX);
        for (i, ch) in name.chars().enumerate() {
            self.device.name[i] = ch as libc::c_char;
        }

        let mut fd = -1;

        unsafe {
            for path in ["/dev/uinput", "/dev/input/uinput", "/dev/misc/uinput"] {
                fd = libc::open(CString::new(path).unwrap().as_ptr().cast(), libc::O_RDWR);
                if fd >= 0 {
                    break;
                }
            }

            if fd < -1 {
                return Err(format!(
                    "Failed to open uinput for {}: {}",
                    addr,
                    std::io::Error::last_os_error()
                ));
            }

            if libc::write(
                fd,
                self.device.serialize().as_ptr() as *const libc::c_void,
                mem::size_of::<UInputDevInfo>(),
            ) < 0
            {
                libc::close(fd);
                return Err(format!(
                    "Can't write device information for {}: {}",
                    addr,
                    std::io::Error::last_os_error()
                ));
            }

            libc::ioctl(fd, UI_SET_EVBIT, EV_KEY);
            libc::ioctl(fd, UI_SET_EVBIT, EV_REL);
            libc::ioctl(fd, UI_SET_EVBIT, EV_REP);
            libc::ioctl(fd, UI_SET_EVBIT, EV_SYN);
            libc::ioctl(fd, UI_SET_PHYS, addr.clone());

            for key_map in KEY_MAP {
                libc::ioctl(fd, UI_SET_KEYBIT, key_map.uinput);
            }

            if libc::ioctl(fd, UI_DEV_CREATE, 0) < 0 {
                libc::close(fd);
                return Err(format!(
                    "Can't create uinput device for {}: {}",
                    addr,
                    std::io::Error::last_os_error()
                ));
            }
        }

        self.fd = fd;
        self.addr = addr;
        Ok(())
    }

    fn close(&mut self) {
        unsafe {
            libc::ioctl(self.fd, UI_DEV_DESTROY, 0);
            libc::close(self.fd);
        }
    }

    fn send_event(
        &mut self,
        event_type: libc::c_ushort,
        code: libc::c_ushort,
        value: libc::c_int,
    ) -> i32 {
        let mut event = UInputEvent {
            time: libc::timeval { tv_sec: 0, tv_usec: 0 },
            event_type: event_type,
            code: code,
            value: value,
        };

        unsafe {
            libc::write(
                self.fd,
                event.serialize().as_ptr() as *const libc::c_void,
                mem::size_of::<UInputDevInfo>(),
            )
            .try_into()
            .unwrap()
        }
    }

    fn send_key(&mut self, key: u8, value: u8) -> Result<(), String> {
        let mut uinput_key: libc::c_ushort = 0;

        for key_map in KEY_MAP {
            if key_map.avc == key {
                uinput_key = key_map.uinput.try_into().unwrap();
            }
        }

        if uinput_key == 0 {
            return Err(format!("AVRCP key: {} is not supported for device: {}", key, self.addr));
        }

        if self.send_event(EV_KEY.try_into().unwrap(), uinput_key, value.into()) < 0
            || self.send_event(EV_SYN.try_into().unwrap(), SYN_REPORT.try_into().unwrap(), 0) < 0
        {
            return Err(format!(
                "Failed to send uinput event: {} for device: {}",
                std::io::Error::last_os_error(),
                self.addr
            ));
        }
        Ok(())
    }
}

pub struct UInput {
    /// A vector that holds uinput objects.
    devices: Vec<UInputDev>,
    /// The address of current active device.
    active_device: String,
}

impl Drop for UInput {
    fn drop(&mut self) {
        for device in self.devices.iter_mut() {
            device.close();
        }
    }
}

impl UInput {
    fn get_device(&mut self, addr: String) -> Option<&mut UInputDev> {
        for device in self.devices.iter_mut() {
            if device.addr == addr {
                return Some(device);
            }
        }
        None
    }

    /// Create a new UInput struct that holds a vector of uinput objects.
    pub fn new() -> Self {
        UInput {
            devices: Vec::<UInputDev>::new(),
            active_device: String::from("00:00:00:00:00:00"),
        }
    }

    /// Initialize a uinput device with kernel.
    pub fn create(&mut self, name: String, addr: String) -> Result<(), String> {
        if self.get_device(addr.clone()).is_some() {
            return Ok(());
        }

        let mut device = UInputDev::default();
        match device.init(name, addr) {
            Ok(()) => {
                self.devices.push(device);
                Ok(())
            }
            Err(e) => Err(e),
        }
    }

    /// Close the specified uinput device with kernel if created.
    pub fn close(&mut self, addr: String) {
        if addr == self.active_device {
            self.active_device = String::from("00:00:00:00:00:00");
        }

        // Remove the device from the list. uinput will get closed with the kernel through device
        // Drop trait.
        if let Some(pos) = self.devices.iter().position(|device| device.addr == addr) {
            self.devices.remove(pos);
        }
    }

    /// Set a device to be AVRCP active.
    pub fn set_active_device(&mut self, addr: String) {
        self.active_device = addr;
    }

    /// Send key event to the active uinput.
    pub fn send_key(&mut self, key: u8, value: u8) -> Result<(), String> {
        match self.active_device.as_str() {
            "00:00:00:00:00:00" => Err(format!("Active device is not specified")),
            _ => match self.get_device(self.active_device.clone()) {
                Some(device) => device.send_key(key, value),
                None => Err(format!("uinput: {} is not initialized", self.active_device)),
            },
        }
    }
}
