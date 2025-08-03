// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

use std::future::Future;
use std::pin::Pin;
use std::task::{Context, Poll};
use std::time::Duration;

use tokio::sync::mpsc::UnboundedReceiver;
use tokio::time::{sleep, Sleep};

/// Pinned Sleep instance. It can be used in tokio::select! macro.
pub(super) struct PinSleep(Pin<Box<Sleep>>);

impl PinSleep {
    pub fn new(duration: Duration) -> Self {
        Self(Box::pin(sleep(duration)))
    }
}

impl Future for PinSleep {
    type Output = ();

    fn poll(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<()> {
        self.0.as_mut().poll(cx)
    }
}

/// Generate the setter method for the field of the struct for the builder pattern.
macro_rules! builder_field {
    ($field:ident, $ty:ty, $wrap:expr) => {
        /// Set the $field field.
        pub fn $field(&mut self, value: $ty) -> &mut Self {
            self.$field = $wrap(value);
            self
        }
    };
    ($field:ident, $ty:ty) => {
        builder_field!($field, $ty, ::std::convert::identity);
    };
}
pub(crate) use builder_field;

/// Generate the setter method for the field of the struct for the consuming builder pattern.
macro_rules! consuming_builder_field {
    ($field:ident, $ty:ty, $wrap:expr) => {
        /// Set the $field field.
        pub fn $field(mut self, value: $ty) -> Self {
            self.$field = $wrap(value);
            self
        }
    };
    ($field:ident, $ty:ty) => {
        consuming_builder_field!($field, $ty, ::std::convert::identity);
    };
}
pub(crate) use consuming_builder_field;

/// Generate the getter method for the field of the struct.
macro_rules! getter_field {
    ($field:ident, $ty:ty) => {
        pub fn $field(&self) -> &$ty {
            &self.$field
        }
    };
}
pub(crate) use getter_field;

/// Clean shutdown a mpsc receiver.
///
/// Call this function before dropping the receiver if the sender is not dropped yet.
pub fn clean_mpsc_receiver<T>(receiver: &mut UnboundedReceiver<T>) {
    receiver.close();
    while receiver.try_recv().is_ok() {}
}

#[cfg(test)]
pub fn init_test_logging() {
    let _ = env_logger::builder().is_test(true).try_init();
}

#[cfg(test)]
mod tests {
    struct Foo {
        value: u32,
    }

    impl Foo {
        pub fn new(value: u32) -> Self {
            Self { value }
        }

        getter_field!(value, u32);
    }

    #[test]
    fn test_getter_field() {
        let foo = Foo::new(5);
        assert_eq!(foo.value(), &5);
    }
}
