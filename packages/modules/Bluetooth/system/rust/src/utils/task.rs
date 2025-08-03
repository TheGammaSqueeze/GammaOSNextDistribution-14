//! This module provides utilities relating to async tasks, typically for usage
//! only in test

use std::{
    future::{Future, IntoFuture},
    time::Duration,
};

use bt_common::init_logging;
use tokio::{
    runtime::Builder,
    select,
    task::{spawn_local, LocalSet},
};

/// Run the supplied future on a single-threaded runtime
pub fn block_on_locally<T>(f: impl Future<Output = T>) -> T {
    init_logging();
    LocalSet::new().block_on(
        &Builder::new_current_thread().enable_time().start_paused(true).build().unwrap(),
        async move {
            select! {
                t = f => t,
                // NOTE: this time should be LARGER than any meaningful delay in the stack
                _ = tokio::time::sleep(Duration::from_secs(100000)) => {
                    panic!("test appears to be stuck");
                },
            }
        },
    )
}

/// Check if the supplied future immediately resolves.
/// Returns Ok(T) if it resolves, or Err(JoinHandle<T>) if it does not.
/// Correctly handles spurious wakeups (unlike Future::poll).
///
/// Unlike spawn/spawn_local, try_await guarantees that the future has been
/// polled when it returns. In addition, it is safe to drop the returned future,
/// since the underlying future will still run (i.e. it will not be cancelled).
///
/// Thus, this is useful in tests where we want to force a particular order of
/// events, rather than letting spawn_local enqueue a task to the executor at
/// *some* point in the future.
///
/// MUST only be run in an environment where time is mocked.
pub async fn try_await<T: 'static>(
    f: impl IntoFuture<Output = T> + 'static,
) -> Result<T, impl Future<Output = T>> {
    let mut handle = spawn_local(f.into_future());

    select! {
        t = &mut handle => Ok(t.unwrap()),
        // NOTE: this time should be SMALLER than any meaningful delay in the stack
        // since time is frozen in test, we don't need to worry about racing with anything
        _ = tokio::time::sleep(Duration::from_millis(10)) => {
            Err(async { handle.await.unwrap() })
        },
    }
}
