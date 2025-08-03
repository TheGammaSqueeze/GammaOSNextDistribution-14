#![allow(unused)]

mod inc {
    use crate::sys::*;
    use crate::types::c_char as char;
    use crate::types::c_int as int;
    use crate::types::c_int16_t as int16_t;
    use crate::types::c_int32_t as int32_t;
    use crate::types::c_int64_t as int64_t;
    use crate::types::c_int8_t as int8_t;
    use crate::types::c_long as long;
    use crate::types::c_uint as uint;
    use crate::types::c_uint16_t as uint16_t;
    use crate::types::c_uint32_t as uint32_t;
    use crate::types::c_uint64_t as uint64_t;
    use crate::types::c_uint8_t as uint8_t;
    use crate::types::c_ulong as ulong;
    use crate::types::c_void as void;

    include!(env!("SYSCALL_INC_FILE"));
}

pub use inc::_trusty_accept as accept;
pub use inc::_trusty_brk as brk;
pub use inc::_trusty_close as close;
pub use inc::_trusty_connect as connect;
pub use inc::_trusty_dup as dup;
pub use inc::_trusty_exit_etc as exit_etc;
pub use inc::_trusty_finish_dma as finish_dma;
pub use inc::_trusty_get_msg as get_msg;
pub use inc::_trusty_gettime as gettime;
pub use inc::_trusty_handle_set_create as handle_set_create;
pub use inc::_trusty_handle_set_ctrl as handle_set_ctrl;
pub use inc::_trusty_ioctl as ioctl;
pub use inc::_trusty_memref_create as memref_create;
pub use inc::_trusty_mmap as mmap;
pub use inc::_trusty_munmap as munmap;
pub use inc::_trusty_nanosleep as nanosleep;
pub use inc::_trusty_port_create as port_create;
pub use inc::_trusty_prepare_dma as prepare_dma;
pub use inc::_trusty_put_msg as put_msg;
pub use inc::_trusty_read_msg as read_msg;
pub use inc::_trusty_readv as readv;
pub use inc::_trusty_send_msg as send_msg;
pub use inc::_trusty_set_cookie as set_cookie;
pub use inc::_trusty_set_user_tls as set_user_tls;
pub use inc::_trusty_wait as wait;
pub use inc::_trusty_wait_any as wait_any;
pub use inc::_trusty_writev as writev;
