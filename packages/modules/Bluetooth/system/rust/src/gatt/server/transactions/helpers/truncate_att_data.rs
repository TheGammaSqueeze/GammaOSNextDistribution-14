use crate::packets::AttAttributeDataChild;

pub fn truncate_att_data(data: AttAttributeDataChild, len: usize) -> AttAttributeDataChild {
    // Note: we only truncate RawData, not other children
    // This behavior is non-ideal, but in practice it's OK
    // since anything except for RawData will NEVER exceed an MTU
    // Kept since it makes writing tests way easier
    match data {
        AttAttributeDataChild::RawData(data) => {
            let mut data = Vec::from(data);
            data.truncate(len);
            AttAttributeDataChild::RawData(data.into_boxed_slice())
        }
        _ => data,
    }
}

#[cfg(test)]
mod test {
    use super::*;

    use crate::packets::{GattServiceDeclarationValueBuilder, Serializable, UuidBuilder};

    #[test]
    fn test_unaffected() {
        let data = AttAttributeDataChild::RawData([1, 2, 3].into());
        let mtu = 21;

        let truncated = truncate_att_data(data, mtu);

        assert_eq!(truncated.to_vec().unwrap(), vec![1, 2, 3]);
    }

    #[test]
    fn test_truncated() {
        let data = AttAttributeDataChild::RawData([1, 2, 3].into());
        let mtu = 2;

        let truncated = truncate_att_data(data, mtu);

        assert_eq!(truncated.to_vec().unwrap(), vec![1, 2]);
    }

    #[test]
    fn test_truncated_non_raw() {
        // Verifies that non-Raw data is not truncated
        let data =
            GattServiceDeclarationValueBuilder { uuid: UuidBuilder { data: [1, 2, 3].into() } }
                .into();
        let mtu = 2;

        let truncated = truncate_att_data(data, mtu);

        assert_eq!(truncated.to_vec().unwrap(), vec![1, 2, 3]);
    }
}
