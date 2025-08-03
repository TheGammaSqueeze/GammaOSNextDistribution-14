/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

fn main() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("gpu_probe")
            .with_max_level(log::LevelFilter::Info),
    );

    log::info!("Starting pixel gpu_probe");
    std::panic::set_hook(Box::new(|panic_msg| {
        log::error!("{}", panic_msg);
    }));
    unsafe {
        let gpudataproducer_library =
            libloading::Library::new("/vendor/lib64/libgpudataproducer.so").unwrap();
        let start: libloading::Symbol<fn() -> ()> = gpudataproducer_library.get(b"start").unwrap();
        start();
    };
}
