(ns xyz.psyclyx.light.config
  "Environment-driven configuration. No lifecycle: read the world (env
   vars + system properties) once at boot and produce an immutable map.
   The map is placed in the component system under :config and passed
   to other components as a dependency.

   Defaults assume a developer running outside a sandbox: state and
   runtime dirs default to XDG locations and binaries default to bare
   names resolved against $PATH (provided by shell.nix). The NixOS
   module overrides every value via env vars to make the production
   path fully deterministic.")

(defn- env [k default]
  (or (System/getenv k) default))

(defn- xdg-state []
  (or (System/getenv "XDG_STATE_HOME")
      (str (System/getProperty "user.home") "/.local/state")))

(defn- xdg-runtime []
  (or (System/getenv "XDG_RUNTIME_DIR")
      (str "/tmp/" (System/getProperty "user.name") "-runtime")))

(defn from-env
  "Reads config from the environment. Returns an immutable map."
  []
  (let [state-dir   (env "PSYCLIGHT_STATE_DIR"
                        (str (xdg-state) "/psyclight"))
        runtime-dir (env "PSYCLIGHT_RUNTIME_DIR"
                        (str (xdg-runtime) "/psyclight"))]
    {:http        {:host (env "PSYCLIGHT_HTTP_HOST" "127.0.0.1")
                   :port (Long/parseLong (env "PSYCLIGHT_HTTP_PORT" "8080"))}
     :mqtt        {:host (env "PSYCLIGHT_MQTT_HOST" "127.0.0.1")
                   :port (Long/parseLong (env "PSYCLIGHT_MQTT_PORT" "1883"))}
     :state-dir   state-dir
     :runtime-dir runtime-dir
     :db          {:path (str state-dir "/psyclight.db")}
     :mosquitto   {:bin (env "PSYCLIGHT_MOSQUITTO_BIN" "mosquitto")
                   :conf-path (str runtime-dir "/mosquitto.conf")}
     :zigbee2mqtt {:bin      (env "PSYCLIGHT_ZIGBEE2MQTT_BIN" "zigbee2mqtt")
                   :data-dir (str state-dir "/zigbee2mqtt")}}))
