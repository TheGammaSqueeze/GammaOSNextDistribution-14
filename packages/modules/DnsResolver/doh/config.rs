/*
 * Copyright (C) 2021 The Android Open Source Project
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

//! Quiche Config support
//!
//! Quiche config objects are needed mutably for constructing a Quiche
//! connection object, but not when they are actually being used. As these
//! objects include a `SSL_CTX` which can be somewhat expensive and large when
//! using a certificate path, it can be beneficial to cache them.
//!
//! This module provides a caching layer for loading and constructing
//! these configurations.

use quiche::{h3, Result};
use std::collections::HashMap;
use std::ops::DerefMut;
use std::sync::{Arc, RwLock, Weak};
use tokio::sync::Mutex;

type WeakConfig = Weak<Mutex<quiche::Config>>;

/// A cheaply clonable `quiche::Config`
#[derive(Clone)]
pub struct Config(Arc<Mutex<quiche::Config>>);

const MAX_INCOMING_BUFFER_SIZE_WHOLE: u64 = 10000000;
const MAX_INCOMING_BUFFER_SIZE_EACH: u64 = 1000000;
const MAX_CONCURRENT_STREAM_SIZE: u64 = 100;
/// Maximum datagram size we will accept.
pub const MAX_DATAGRAM_SIZE: usize = 1350;

impl Config {
    fn from_weak(weak: &WeakConfig) -> Option<Self> {
        weak.upgrade().map(Self)
    }

    fn to_weak(&self) -> WeakConfig {
        Arc::downgrade(&self.0)
    }

    /// Construct a `Config` object from certificate path. If no path
    /// is provided, peers will not be verified.
    pub fn from_key(key: &Key) -> Result<Self> {
        let mut config = quiche::Config::new(quiche::PROTOCOL_VERSION)?;
        config.set_application_protos(h3::APPLICATION_PROTOCOL)?;
        match key.cert_path.as_deref() {
            Some(path) => {
                config.verify_peer(true);
                config.load_verify_locations_from_directory(path)?;
            }
            None => config.verify_peer(false),
        }
        if key.enable_early_data {
            config.enable_early_data();
        }

        // Some of these configs are necessary, or the server can't respond the HTTP/3 request.
        config.set_max_idle_timeout(key.max_idle_timeout);
        config.set_max_recv_udp_payload_size(MAX_DATAGRAM_SIZE);
        config.set_initial_max_data(MAX_INCOMING_BUFFER_SIZE_WHOLE);
        config.set_initial_max_stream_data_bidi_local(MAX_INCOMING_BUFFER_SIZE_EACH);
        config.set_initial_max_stream_data_bidi_remote(MAX_INCOMING_BUFFER_SIZE_EACH);
        config.set_initial_max_stream_data_uni(MAX_INCOMING_BUFFER_SIZE_EACH);
        config.set_initial_max_streams_bidi(MAX_CONCURRENT_STREAM_SIZE);
        config.set_initial_max_streams_uni(MAX_CONCURRENT_STREAM_SIZE);
        config.set_disable_active_migration(true);
        Ok(Self(Arc::new(Mutex::new(config))))
    }

    /// Take the underlying config, usable as `&mut quiche::Config` for use
    /// with `quiche::connect`.
    pub async fn take(&mut self) -> impl DerefMut<Target = quiche::Config> + '_ {
        self.0.lock().await
    }
}

#[derive(Clone, Default)]
struct State {
    // Mapping from cert_path to configs
    key_to_config: HashMap<Key, WeakConfig>,
    // Keep latest config alive to minimize reparsing when flapping
    // If more keep-alive is needed, replace with a LRU LinkedList
    latest: Option<Config>,
}

impl State {
    fn get_config(&self, key: &Key) -> Option<Config> {
        self.key_to_config.get(key).and_then(Config::from_weak)
    }

    fn keep_alive(&mut self, config: Config) {
        self.latest = Some(config);
    }

    fn garbage_collect(&mut self) {
        self.key_to_config.retain(|_, config| config.strong_count() != 0)
    }
}

/// Cache of Quiche Config objects
///
/// Cloning this cache will create another handle to the same cache.
///
/// Loading a config object through this caching layer will only keep the
/// latest config loaded alive directly, but will still act as a cache
/// for any configurations still in use - if the returned `Config` is still
/// live, queries to `Cache` will not reconstruct it.
#[derive(Clone, Default)]
pub struct Cache {
    // Shared state amongst cache handles
    state: Arc<RwLock<State>>,
}

/// Key used for getting an associated Quiche Config from Cache.
#[derive(Clone, PartialEq, Eq, Hash)]
pub struct Key {
    pub cert_path: Option<String>,
    pub max_idle_timeout: u64,
    pub enable_early_data: bool,
}

impl Cache {
    /// Creates a fresh empty cache
    pub fn new() -> Self {
        Default::default()
    }

    /// Behaves as `Config::from_cert_path`, but with a cache.
    /// If any object previously given out by this cache is still live,
    /// a duplicate will not be made.
    pub fn get(&self, key: &Key) -> Result<Config> {
        // Fast path - read-only access to state retrieves config
        if let Some(config) = self.state.read().unwrap().get_config(key) {
            return Ok(config);
        }

        // Unlocked, calculate config. If we have two racing attempts to load
        // the cert path, we'll arbitrate that in the next step, but this
        // makes sure loading a new cert path doesn't block other loads to
        // refresh connections.
        let config = Config::from_key(key)?;

        let mut state = self.state.write().unwrap();
        // We now have exclusive access to the state.
        // If someone else calculated a config at the same time as us, we
        // want to discard ours and use theirs, since it will result in
        // less total memory used.
        if let Some(config) = state.get_config(key) {
            return Ok(config);
        }

        // We have exclusive access and a fresh config. Install it into
        // the cache.
        state.keep_alive(config.clone());
        state.key_to_config.insert(key.clone(), config.to_weak());
        Ok(config)
    }

    /// Purges any config paths which no longer point to a config entry.
    pub fn garbage_collect(&self) {
        self.state.write().unwrap().garbage_collect();
    }
}

#[test]
fn create_quiche_config() {
    assert!(
        Config::from_key(&Key { cert_path: None, max_idle_timeout: 1000, enable_early_data: true })
            .is_ok(),
        "quiche config without cert creating failed"
    );
    assert!(
        Config::from_key(&Key {
            cert_path: Some("data/local/tmp/".to_string()),
            max_idle_timeout: 1000,
            enable_early_data: true,
        })
        .is_ok(),
        "quiche config with cert creating failed"
    );
}

#[test]
fn shared_cache() {
    let cache_a = Cache::new();
    let config_a = cache_a
        .get(&Key { cert_path: None, max_idle_timeout: 1000, enable_early_data: true })
        .unwrap();
    assert_eq!(Arc::strong_count(&config_a.0), 2);
    let _config_b = cache_a
        .get(&Key { cert_path: None, max_idle_timeout: 1000, enable_early_data: true })
        .unwrap();
    assert_eq!(Arc::strong_count(&config_a.0), 3);
}

#[test]
fn different_keys() {
    let cache = Cache::new();
    let key_a = Key { cert_path: None, max_idle_timeout: 1000, enable_early_data: false };
    let key_b =
        Key { cert_path: Some("a".to_string()), max_idle_timeout: 1000, enable_early_data: false };
    let key_c =
        Key { cert_path: Some("a".to_string()), max_idle_timeout: 5000, enable_early_data: false };
    let key_d =
        Key { cert_path: Some("a".to_string()), max_idle_timeout: 5000, enable_early_data: true };
    let config_a = cache.get(&key_a).unwrap();
    let config_b = cache.get(&key_b).unwrap();
    let _config_b = cache.get(&key_b).unwrap();
    let config_c = cache.get(&key_c).unwrap();
    let _config_c = cache.get(&key_c).unwrap();
    let config_d = cache.get(&key_d).unwrap();
    let _config_d = cache.get(&key_d).unwrap();

    assert_eq!(Arc::strong_count(&config_a.0), 1);
    assert_eq!(Arc::strong_count(&config_b.0), 2);
    assert_eq!(Arc::strong_count(&config_c.0), 2);

    // config_d was most recently created, so it should have an extra strong reference due to
    // keep-alive in the cache.
    assert_eq!(Arc::strong_count(&config_d.0), 3);
}

#[test]
fn lifetimes() {
    let cache = Cache::new();
    let key_a =
        Key { cert_path: Some("a".to_string()), max_idle_timeout: 1000, enable_early_data: true };
    let key_b =
        Key { cert_path: Some("b".to_string()), max_idle_timeout: 1000, enable_early_data: true };
    let config_none = cache
        .get(&Key { cert_path: None, max_idle_timeout: 1000, enable_early_data: true })
        .unwrap();
    let config_a = cache.get(&key_a).unwrap();
    let config_b = cache.get(&key_b).unwrap();

    // The first two we created should have a strong count of one - those handles are the only
    // thing keeping them alive.
    assert_eq!(Arc::strong_count(&config_none.0), 1);
    assert_eq!(Arc::strong_count(&config_a.0), 1);

    // If we try to get another handle we already have, it should be the same one.
    let _config_a2 = cache.get(&key_a).unwrap();
    assert_eq!(Arc::strong_count(&config_a.0), 2);

    // config_b was most recently created, so it should have a keep-alive
    // inside the cache.
    assert_eq!(Arc::strong_count(&config_b.0), 2);

    // If we weaken one of the first handles, then drop it, the weak handle should break
    let config_none_weak = Config::to_weak(&config_none);
    assert_eq!(config_none_weak.strong_count(), 1);
    drop(config_none);
    assert_eq!(config_none_weak.strong_count(), 0);
    assert!(Config::from_weak(&config_none_weak).is_none());

    // If we weaken the most *recent* handle, it should keep working
    let config_b_weak = Config::to_weak(&config_b);
    assert_eq!(config_b_weak.strong_count(), 2);
    drop(config_b);
    assert_eq!(config_b_weak.strong_count(), 1);
    assert!(Config::from_weak(&config_b_weak).is_some());
    assert_eq!(config_b_weak.strong_count(), 1);

    // If we try to get a config which is still kept alive by the cache, we should get the same
    // one.
    let _config_b2 = cache.get(&key_b).unwrap();
    assert_eq!(config_b_weak.strong_count(), 2);

    // We broke None, but "a" and "b" should still both be alive. Check that
    // this is still the case in the mapping after garbage collection.
    cache.garbage_collect();
    assert_eq!(cache.state.read().unwrap().key_to_config.len(), 2);
}

#[tokio::test]
async fn quiche_connect() {
    use std::net::{Ipv4Addr, SocketAddr, SocketAddrV4};
    let mut config =
        Config::from_key(&Key { cert_path: None, max_idle_timeout: 10, enable_early_data: true })
            .unwrap();
    let local = SocketAddr::V4(SocketAddrV4::new(Ipv4Addr::new(127, 0, 0, 1), 42));
    let peer = SocketAddr::V4(SocketAddrV4::new(Ipv4Addr::new(127, 0, 0, 1), 41));
    let conn_id = quiche::ConnectionId::from_ref(&[]);

    quiche::connect(None, &conn_id, local, peer, config.take().await.deref_mut()).unwrap();
}
