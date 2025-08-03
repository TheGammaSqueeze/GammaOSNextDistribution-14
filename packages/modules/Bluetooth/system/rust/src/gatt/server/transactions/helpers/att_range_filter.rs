//! This module encapsulates the attribute range filtration logic used
//! in many ATT commands, such as ATT_FIND_INFORMATION_REQ and
//! ATT_FIND_BY_TYPE_VALUE REQ

use crate::gatt::{ids::AttHandle, server::att_database::AttAttribute};

/// Filter a (sorted) iterator of attributes to those that lie within
/// the specified range. If the range is invalid (start = 0, or start > end),
/// return None.
pub fn filter_to_range(
    start_handle: AttHandle,
    end_handle: AttHandle,
    attrs: impl Iterator<Item = AttAttribute> + Clone,
) -> Option<impl Iterator<Item = AttAttribute> + Clone> {
    if start_handle.0 == 0 || end_handle < start_handle {
        return None;
    }
    Some(
        attrs
            .skip_while(move |attr| attr.handle < start_handle)
            .take_while(move |attr| attr.handle <= end_handle),
    )
}

#[cfg(test)]
mod test {
    use crate::gatt::server::{gatt_database::AttPermissions, gatt_database::CHARACTERISTIC_UUID};

    use super::*;

    fn attr(handle: u16) -> AttAttribute {
        AttAttribute {
            handle: AttHandle(handle),
            type_: CHARACTERISTIC_UUID,
            permissions: AttPermissions::READABLE,
        }
    }

    #[test]
    fn test_invalid_start_handle() {
        let res = filter_to_range(AttHandle(0), AttHandle(2), [].into_iter());

        assert!(res.is_none())
    }

    #[test]
    fn test_invalid_range() {
        // call with a range where end < start
        let res = filter_to_range(AttHandle(3), AttHandle(1), [].into_iter());

        assert!(res.is_none())
    }

    #[test]
    fn test_trivial_range() {
        // call with a range where start == end, make sure it gets the relevant
        // attribute
        let res =
            filter_to_range(AttHandle(3), AttHandle(3), [attr(2), attr(3), attr(4)].into_iter())
                .unwrap();

        assert_eq!(res.collect::<Vec<_>>(), vec![attr(3)])
    }

    #[test]
    fn test_nontrivial_range() {
        let res = filter_to_range(
            AttHandle(3),
            AttHandle(4),
            [attr(2), attr(3), attr(4), attr(5)].into_iter(),
        )
        .unwrap();

        assert_eq!(res.collect::<Vec<_>>(), vec![attr(3), attr(4)])
    }
}
