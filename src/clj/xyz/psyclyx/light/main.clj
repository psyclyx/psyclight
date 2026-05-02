(ns xyz.psyclyx.light.main
  "Entry point for the psyclight binary. Reads config from the
   environment, starts the component system, installs a shutdown hook,
   and parks until the JVM exits."
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [xyz.psyclyx.light.config :as config]
            [xyz.psyclyx.light.system :as system])
  (:gen-class))

(defn install-uncaught-exception-handler! []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (log/error ex "Uncaught exception on thread" (.getName thread))))))

(defn -main [& _args]
  (install-uncaught-exception-handler!)
  (let [cfg    (config/from-env)
        _      (log/info "Starting psyclight" {:config cfg})
        system (component/start (system/make-system cfg))]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable
                       (fn []
                         (log/info "Shutting down…")
                         (component/stop system))))
    (log/info "psyclight started")
    @(promise)))
