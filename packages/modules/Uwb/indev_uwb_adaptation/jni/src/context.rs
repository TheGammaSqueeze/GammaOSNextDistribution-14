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

//! Contains a structure defining the JNI context with frequently used helper methods.
use jni::objects::{JList, JObject};
use jni::JNIEnv;

/// Struct containing the JNI environment and a java object. It contains methods
/// that work on the java object using the JNI environment
#[derive(Copy, Clone)]
pub struct JniContext<'a> {
    pub env: JNIEnv<'a>,
    pub obj: JObject<'a>,
}

impl<'a> JniContext<'a> {
    pub fn new(env: JNIEnv<'a>, obj: JObject<'a>) -> Self {
        Self { env, obj }
    }

    pub fn int_getter(&self, method: &str) -> Result<i32, jni::errors::Error> {
        self.env.call_method(self.obj, method, "()I", &[])?.i()
    }

    pub fn long_getter(&self, method: &str) -> Result<i64, jni::errors::Error> {
        self.env.call_method(self.obj, method, "()J", &[])?.j()
    }

    pub fn bool_getter(&self, method: &str) -> Result<bool, jni::errors::Error> {
        self.env.call_method(self.obj, method, "()Z", &[])?.z()
    }

    pub fn byte_arr_getter(&self, method: &str) -> Result<Vec<u8>, jni::errors::Error> {
        let val_obj = self.env.call_method(self.obj, method, "()[B", &[])?.l()?;
        self.env.convert_byte_array(val_obj.into_raw())
    }

    pub fn object_getter(
        &'a self,
        method: &str,
        class: &str,
    ) -> Result<JObject<'a>, jni::errors::Error> {
        self.env.call_method(self.obj, method, class, &[])?.l()
    }

    pub fn list_getter(&'a self, method: &str) -> Result<JList<'a, 'a>, jni::errors::Error> {
        let list_obj = self.env.call_method(self.obj, method, "()Ljava/util/List;", &[])?.l()?;
        JList::from_env(&self.env, list_obj)
    }
}
