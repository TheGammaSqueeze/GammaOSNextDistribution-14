// Copyright 2019 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//! Generated protobuf bindings.

#[cfg(feature = "plugin")]
pub use crosvm_plugin_proto::plugin;

#[cfg(feature = "composite-disk")]
pub use cdisk_spec_proto::cdisk_spec;
#[cfg(feature = "registered_events")]
pub use registered_events_proto::registered_events;
