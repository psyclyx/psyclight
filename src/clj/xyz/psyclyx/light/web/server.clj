(ns xyz.psyclyx.light.web.server
  "Jetty HTTP server component. Builds a ring handler from the deps
   threaded in by Stuart Sierra's component, then runs jetty9 with
   virtual threads so SSE handlers can hold a thread per connection
   without blowing up the platform thread pool."
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty9 :as jetty]
            [xyz.psyclyx.light.web.routes :as routes]))

(defrecord WebServer [config db event-bus devices pairing zigbee2mqtt server]
  component/Lifecycle
  (start [this]
    (let [{:keys [host port]} (:http config)
          handler (routes/build {:db          db
                                 :event-bus   event-bus
                                 :devices     devices
                                 :pairing     pairing
                                 :zigbee2mqtt zigbee2mqtt})
          srv     (jetty/run-jetty handler
                    {:host             host
                     :port             port
                     :join?            false
                     :virtual-threads? true})]
      (log/info "HTTP listening on" (str host ":" port))
      (assoc this :server srv)))
  (stop [this]
    (when server
      (log/info "Stopping HTTP server")
      (jetty/stop-server server))
    (assoc this :server nil)))

(defn web-server [] (map->WebServer {}))
