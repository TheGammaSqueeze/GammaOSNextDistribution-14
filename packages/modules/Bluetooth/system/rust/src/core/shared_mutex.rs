//! The motivation for SharedMutex is to guard a resource without having to
//! extend its lifetime using an Rc<> (and potentially create reference cycles)

use std::{future::Future, rc::Rc, sync::Arc};

use tokio::sync::{Mutex, OwnedMutexGuard, Semaphore, TryLockError};

/// A mutex wrapping some contents of type T. When the mutex is dropped,
/// T will be dropped.
///
/// The lifetime of T will be extended only if a client currently holds
/// exclusive access to it, in which case once that client drops its
/// MutexGuard, then T will be dropped.
pub struct SharedMutex<T> {
    lock: Arc<Mutex<T>>,
    on_death: Rc<Semaphore>,
}

impl<T> SharedMutex<T> {
    /// Constructor
    pub fn new(t: T) -> Self {
        Self { lock: Arc::new(Mutex::new(t)), on_death: Rc::new(Semaphore::new(0)) }
    }

    /// Acquire exclusive access to T, or None if SharedMutex<T> is dropped
    /// while waiting to acquire. Unlike Mutex::lock, this method produces a
    /// future with 'static lifetime, so it can be awaited even if the
    /// SharedMutex<> itself is dropped.
    ///
    /// NOTE: if the lifetime of T is extended by the holder of the lock when
    /// the SharedMutex<> itself is dropped, all waiters will still
    /// instantly return None (rather than waiting for the lock to be
    /// released).
    pub fn lock(&self) -> impl Future<Output = Option<OwnedMutexGuard<T>>> {
        let mutex = self.lock.clone();
        let on_death = self.on_death.clone();

        async move {
            tokio::select! {
            biased;
              permit = on_death.acquire() => {
                drop(permit);
                None
              },
              acquired = mutex.lock_owned() => {
                Some(acquired)
              },
            }
        }
    }

    /// Synchronously acquire the lock. This similarly exhibits the
    /// lifetime-extension behavior of Self#lock().
    pub fn try_lock(&self) -> Result<OwnedMutexGuard<T>, TryLockError> {
        self.lock.clone().try_lock_owned()
    }
}

impl<T> Drop for SharedMutex<T> {
    fn drop(&mut self) {
        // mark dead, so all waiters instantly return
        // no one can start to wait after drop
        self.on_death.add_permits(Arc::strong_count(&self.lock) + 1);
    }
}
