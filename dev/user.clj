(ns user
  "REPL-driven development entry. `(go)` starts a system; `(reset)` reloads
   namespaces and restarts.

   Default config points state and runtime dirs at ./state and ./runtime
   inside the project (gitignored), so a dev session leaves nothing
   behind in the user's XDG dirs."
  (:require [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [xyz.psyclyx.light.config :as config]
            [xyz.psyclyx.light.system :as system]))

(repl/set-refresh-dirs "src/clj" "dev")

(defonce ^:private system-state (atom nil))

(defn dev-config []
  (-> (config/from-env)
      (assoc :state-dir   (str (System/getProperty "user.dir") "/state")
             :runtime-dir (str (System/getProperty "user.dir") "/runtime"))
      (update :db assoc :path
              (str (System/getProperty "user.dir") "/state/psyclight.db"))
      (update :mosquitto assoc :conf-path
              (str (System/getProperty "user.dir") "/runtime/mosquitto.conf"))
      (update :zigbee2mqtt assoc :data-dir
              (str (System/getProperty "user.dir") "/state/zigbee2mqtt"))))

(defn start []
  (let [s (component/start (system/make-system (dev-config)))]
    (reset! system-state s)
    :started))

(defn stop []
  (when-let [s @system-state]
    (component/stop s)
    (reset! system-state nil))
  :stopped)

(defn go [] (stop) (start))

(defn reset []
  (stop)
  (repl/refresh :after 'user/start))
