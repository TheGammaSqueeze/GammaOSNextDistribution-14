// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/// A factory for generating typed identifiers.
///
use std::ops::Add;
pub struct IdFactory<T>
where
    T: Add<Output = T> + Copy,
{
    #[allow(dead_code)]
    start_id: T,
    next_id: T,
    increment: T,
}

impl<T> IdFactory<T>
where
    T: Add<Output = T> + Copy,
{
    pub fn new(start_id: T, increment: T) -> Self {
        Self { start_id, next_id: start_id, increment }
    }
    pub fn next_id(&mut self) -> T {
        let id = self.next_id;
        self.next_id = self.next_id + self.increment;
        id
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::RwLock;
    type BlueIdentifier = u32;

    #[test]
    fn test_blue_id_factory() {
        let ids: RwLock<IdFactory<BlueIdentifier>> = RwLock::new(IdFactory::new(1000, 1));
        assert_eq!(ids.write().unwrap().next_id(), 1000);
        assert_eq!(ids.write().unwrap().next_id(), 1001);
        assert_eq!(ids.write().unwrap().next_id(), 1002);
    }
}
