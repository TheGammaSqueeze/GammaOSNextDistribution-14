/// Utility to watch the presence of a D-Bus services and interfaces.
use dbus::channel::MatchingReceiver;
use dbus::message::MatchRule;
use dbus::nonblock::stdintf::org_freedesktop_dbus::ObjectManager;
use dbus::nonblock::SyncConnection;
use std::sync::{Arc, Mutex};
use std::time::Duration;

const DBUS_SERVICE: &str = "org.freedesktop.DBus";
const DBUS_INTERFACE: &str = "org.freedesktop.DBus";
const DBUS_OBJMGR_INTERFACE: &str = "org.freedesktop.DBus.ObjectManager";
const DBUS_GET_NAME_OWNER: &str = "GetNameOwner";
const DBUS_NAME_OWNER_CHANGED: &str = "NameOwnerChanged";
const DBUS_INTERFACES_ADDED: &str = "InterfacesAdded";
const DBUS_PATH: &str = "/org/freedesktop/DBus";
const DBUS_TIMEOUT: Duration = Duration::from_secs(2);

struct ServiceWatcherContext {
    // The service name to watch.
    service_name: String,
    // The owner D-Bus address if exists.
    service_owner: Option<String>,
}

impl ServiceWatcherContext {
    fn new(service_name: String) -> Self {
        Self { service_name, service_owner: None }
    }

    fn set_owner(&mut self, owner: String) {
        log::debug!("setting owner of {} to {}", self.service_name, owner);
        self.service_owner = Some(owner);
    }

    fn unset_owner(&mut self) {
        log::debug!("unsetting owner of {}", self.service_name);
        self.service_owner = None;
    }
}

pub struct ServiceWatcher {
    conn: Arc<SyncConnection>,
    service_name: String,
    context: Arc<Mutex<ServiceWatcherContext>>,
}

impl ServiceWatcher {
    pub fn new(conn: Arc<SyncConnection>, service_name: String) -> Self {
        ServiceWatcher {
            conn,
            service_name: service_name.clone(),
            context: Arc::new(Mutex::new(ServiceWatcherContext::new(service_name))),
        }
    }

    // Returns the owner D-Bus address of the named service.
    async fn get_dbus_owner_of_service(&self) -> Option<String> {
        let dbus_proxy =
            dbus::nonblock::Proxy::new(DBUS_SERVICE, DBUS_PATH, DBUS_TIMEOUT, self.conn.clone());

        let service_owner: Result<(String,), dbus::Error> = dbus_proxy
            .method_call(DBUS_INTERFACE, DBUS_GET_NAME_OWNER, (self.service_name.clone(),))
            .await;

        match service_owner {
            Err(e) => {
                log::debug!("Getting service owner failed: {}", e);
                None
            }
            Ok((owner,)) => {
                log::debug!("Got service owner {} = {}", self.service_name, owner);
                Some(owner)
            }
        }
    }

    // Returns the object path if the service exports an object having the specified interface.
    async fn get_path_of_interface(&self, interface: String) -> Option<dbus::Path<'static>> {
        let service_proxy = dbus::nonblock::Proxy::new(
            self.service_name.clone(),
            "/",
            DBUS_TIMEOUT,
            self.conn.clone(),
        );

        let objects = service_proxy.get_managed_objects().await;

        match objects {
            Err(e) => {
                log::debug!("Failed getting managed objects: {}", e);
                None
            }
            Ok(objects) => objects
                .into_iter()
                .find(|(_key, value)| value.contains_key(&interface))
                .map(|(key, _value)| key),
        }
    }

    // Monitors the service appearance or disappearance and keeps the owner address updated.
    async fn monitor_name_owner_changed(
        &self,
        on_available: Box<dyn Fn() + Send>,
        on_unavailable: Box<dyn Fn() + Send>,
    ) {
        let mr = MatchRule::new_signal(DBUS_INTERFACE, DBUS_NAME_OWNER_CHANGED);
        self.conn.add_match_no_cb(&mr.match_str()).await.unwrap();
        let service_name = self.service_name.clone();
        let context = self.context.clone();
        self.conn.start_receive(
            mr,
            Box::new(move |msg, _conn| {
                // We use [`dbus::nonblock::SyncConnection::set_signal_match_mode`] with
                // `match_all` = true,  so we should always return true from this closure to
                // indicate that we have handled the message and not let other handlers handle this
                // signal.
                if let (Some(name), Some(old_owner), Some(new_owner)) =
                    msg.get3::<String, String, String>()
                {
                    // Appearance/disappearance of unrelated service, ignore since we are not
                    // interested.
                    if name != service_name {
                        return true;
                    }

                    if old_owner == "" && new_owner != "" {
                        context.lock().unwrap().set_owner(new_owner.clone());
                        on_available();
                    } else if old_owner != "" && new_owner == "" {
                        context.lock().unwrap().unset_owner();
                        on_unavailable();
                    } else {
                        log::warn!(
                            "Invalid NameOwnerChanged with old_owner = {} and new_owner = {}",
                            old_owner,
                            new_owner
                        );
                    }
                }
                true
            }),
        );
    }

    /// Watches appearance and disappearance of a D-Bus service by the name.
    pub async fn start_watch(
        &self,
        on_available: Box<dyn Fn() + Send>,
        on_unavailable: Box<dyn Fn() + Send>,
    ) {
        if let Some(owner) = self.get_dbus_owner_of_service().await {
            self.context.lock().unwrap().set_owner(owner.clone());
            on_available();
        }

        // Monitor service appearing and disappearing.
        self.monitor_name_owner_changed(
            Box::new(move || {
                on_available();
            }),
            Box::new(move || {
                on_unavailable();
            }),
        )
        .await;
    }

    /// Watches the appearance of an interface of a service, and the disappearance of the service.
    ///
    /// Doesn't take into account the disappearance of the interface itself. At the moment assuming
    /// interfaces do not disappear as long as the service is alive.
    pub async fn start_watch_interface(
        &mut self,
        interface: String,
        on_available: Box<dyn Fn(dbus::Path<'static>) + Send>,
        on_unavailable: Box<dyn Fn() + Send>,
    ) {
        if let Some(owner) = self.get_dbus_owner_of_service().await {
            self.context.lock().unwrap().set_owner(owner.clone());
            if let Some(path) = self.get_path_of_interface(interface.clone()).await {
                on_available(path);
            }
        }

        // Monitor service disappearing.
        self.monitor_name_owner_changed(
            Box::new(move || {
                // Don't trigger on_available() yet because we rely on interface added.
            }),
            Box::new(move || {
                on_unavailable();
            }),
        )
        .await;

        // Monitor interface appearing.
        let mr = MatchRule::new_signal(DBUS_OBJMGR_INTERFACE, DBUS_INTERFACES_ADDED);
        self.conn.add_match_no_cb(&mr.match_str()).await.unwrap();
        let context = self.context.clone();
        self.conn.start_receive(
            mr,
            Box::new(move |msg, _conn| {
                // We use [`dbus::nonblock::SyncConnection::set_signal_match_mode`] with
                // `match_all` = true,  so we should always return true from this closure to
                // indicate that we have handled the message and not let other handlers handle this
                // signal.
                let (object_path, interfaces) =
                    msg.get2::<dbus::Path, dbus::arg::Dict<String, dbus::arg::PropMap, _>>();
                let interfaces: Vec<String> = interfaces.unwrap().map(|e| e.0).collect();
                if interfaces.contains(&interface) {
                    if let (Some(sender), Some(owner)) =
                        (msg.sender(), context.lock().unwrap().service_owner.as_ref())
                    {
                        // The signal does not come from the service we are watching, ignore.
                        if &sender.to_string() != owner {
                            log::debug!(
                                "Detected interface {} added but sender is {}, expected {}.",
                                interface,
                                sender,
                                owner
                            );
                            return true;
                        }
                        on_available(object_path.unwrap().into_static());
                    }
                }

                true
            }),
        );
    }
}
