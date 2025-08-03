use crate::packets::Builder;

pub struct PayloadAccumulator<T: Builder> {
    curr: usize,
    lim: usize,
    elems: Vec<T>,
}

impl<T: Builder> PayloadAccumulator<T> {
    pub fn new(size: usize) -> Self {
        Self { curr: 0, lim: size * 8, elems: vec![] }
    }

    #[must_use]
    pub fn push(&mut self, builder: T) -> bool {
        // if serialization fails we WANT to continue, to get a clean SerializeError at
        // the end
        let elem_size = builder.size_in_bits().unwrap_or(0);
        if elem_size + self.curr > self.lim {
            return false;
        }
        self.elems.push(builder);
        self.curr += elem_size;
        true
    }

    pub fn into_boxed_slice(self) -> Box<[T]> {
        self.elems.into_boxed_slice()
    }

    pub fn is_empty(&self) -> bool {
        self.elems.is_empty()
    }
}

#[cfg(test)]
mod test {
    use crate::packets::{AttBuilder, AttChild, AttOpcode};

    use super::PayloadAccumulator;

    #[test]
    fn test_empty() {
        let accumulator = PayloadAccumulator::<AttBuilder>::new(0);
        assert!(accumulator.is_empty())
    }
    #[test]
    fn test_nonempty() {
        let mut accumulator = PayloadAccumulator::new(128);

        let ok = accumulator.push(AttBuilder {
            opcode: AttOpcode::WRITE_RESPONSE,
            _child_: AttChild::RawData([1, 2].into()),
        });

        assert!(ok);
        assert!(!accumulator.is_empty())
    }

    #[test]
    fn test_push_serialize() {
        let mut accumulator = PayloadAccumulator::new(128);

        let ok = accumulator.push(AttBuilder {
            opcode: AttOpcode::WRITE_RESPONSE,
            _child_: AttChild::RawData([1, 2].into()),
        });

        assert!(ok);
        assert_eq!(
            accumulator.into_boxed_slice().as_ref(),
            [AttBuilder {
                opcode: AttOpcode::WRITE_RESPONSE,
                _child_: AttChild::RawData([1, 2].into()),
            }]
        );
    }

    #[test]
    fn test_push_past_capacity() {
        let mut accumulator = PayloadAccumulator::new(5);

        // each builder is 3 bytes, so the first should succeed, the second should fail
        let first_ok = accumulator.push(AttBuilder {
            opcode: AttOpcode::WRITE_RESPONSE,
            _child_: AttChild::RawData([1, 2].into()),
        });
        let second_ok = accumulator.push(AttBuilder {
            opcode: AttOpcode::WRITE_RESPONSE,
            _child_: AttChild::RawData([3, 4].into()),
        });

        // assert: the first one is pushed and is correctly output, but the second is
        // dropped
        assert!(first_ok);
        assert!(!second_ok);
        assert_eq!(
            accumulator.into_boxed_slice().as_ref(),
            [AttBuilder {
                opcode: AttOpcode::WRITE_RESPONSE,
                _child_: AttChild::RawData([1, 2].into()),
            }]
        );
    }

    #[test]
    fn test_push_to_capacity() {
        let mut accumulator = PayloadAccumulator::new(5);

        // 3 + 2 bytes = the size, so both should push correctly
        let first_ok = accumulator.push(AttBuilder {
            opcode: AttOpcode::WRITE_RESPONSE,
            _child_: AttChild::RawData([1, 2].into()),
        });
        let second_ok = accumulator.push(AttBuilder {
            opcode: AttOpcode::WRITE_RESPONSE,
            _child_: AttChild::RawData([3].into()),
        });

        // assert: both are pushed and output correctly
        assert!(first_ok);
        assert!(second_ok);
        assert_eq!(
            accumulator.into_boxed_slice().as_ref(),
            [
                AttBuilder {
                    opcode: AttOpcode::WRITE_RESPONSE,
                    _child_: AttChild::RawData([1, 2].into()),
                },
                AttBuilder {
                    opcode: AttOpcode::WRITE_RESPONSE,
                    _child_: AttChild::RawData([3].into()),
                }
            ]
        );
    }
}
