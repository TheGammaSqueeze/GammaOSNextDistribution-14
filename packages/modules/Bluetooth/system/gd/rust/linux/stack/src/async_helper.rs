use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use tokio::sync::oneshot;
use tokio::time::Duration;

/// Helper for managing an async topshim function. It takes care of calling the function, preparing
/// the channel, waiting for the callback, and returning it in a Result.
///
/// `R` is the type of the return.
pub(crate) struct AsyncHelper<R> {
    // Name of the method that this struct helps. Useful for logging.
    method_name: String,

    // Keeps track of call_id. Increment each time and wrap to 0 when u32 max is reached.
    last_call_id: u32,

    // Keep pending calls' ids and senders.
    senders: Arc<Mutex<HashMap<u32, oneshot::Sender<R>>>>,
}

pub(crate) type CallbackSender<R> = Arc<Mutex<Box<(dyn Fn(u32, R) + Send)>>>;

impl<R: 'static + Send> AsyncHelper<R> {
    pub(crate) fn new(method_name: &str) -> Self {
        Self {
            method_name: String::from(method_name),
            last_call_id: 0,
            senders: Arc::new(Mutex::new(HashMap::new())),
        }
    }

    /// Calls a topshim method that expects the async return to be delivered via a callback.
    pub(crate) async fn call_method<F>(&mut self, f: F, timeout_ms: Option<u64>) -> Result<R, ()>
    where
        F: Fn(u32),
    {
        // Create a oneshot channel to be used by the callback to notify us that the return is
        // available.
        let (tx, rx) = oneshot::channel();

        // Use a unique method call ID so that we know which callback is corresponding to which
        // method call. The actual value of the ID does not matter as long as it's always unique,
        // so a simple increment (and wraps back to 0) is good enough.
        self.last_call_id = self.last_call_id.wrapping_add(1);

        // Keep track of the sender belonging to this call id.
        self.senders.lock().unwrap().insert(self.last_call_id, tx);

        // Call the method. `f` is freely defined by the user of this utility. This must be an
        // operation that expects a callback that will trigger sending of the return via the
        // oneshot channel.
        f(self.last_call_id);

        if let Some(timeout_ms) = timeout_ms {
            let senders = self.senders.clone();
            let call_id = self.last_call_id;
            tokio::spawn(async move {
                tokio::time::sleep(Duration::from_millis(timeout_ms)).await;

                // If the timer expires first before a callback is triggered, we remove the sender
                // which will invalidate the channel which in turn will notify the receiver of
                // an error.
                // If the callback gets triggered first, this does nothing since the entry has been
                // removed when sending the response.
                senders.lock().unwrap().remove(&call_id);
            });
        }

        // Wait for the callback and return when available.
        rx.await.map_err(|_| ())
    }

    /// Returns a function to be invoked when callback is triggered.
    pub(crate) fn get_callback_sender(&self) -> CallbackSender<R> {
        let senders = self.senders.clone();
        let method_name = self.method_name.clone();
        return Arc::new(Mutex::new(Box::new(move |call_id, ret| {
            if let Some(sender) = senders.lock().unwrap().remove(&call_id) {
                sender.send(ret).ok();
            } else {
                log::warn!("AsyncHelper {}: Sender no longer exists.", method_name);
            }
        })));
    }
}
