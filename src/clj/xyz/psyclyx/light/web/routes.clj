(ns xyz.psyclyx.light.web.routes
  "Reitit router assembly. Each handler namespace contributes a vector
   of routes; we concatenate, attach middleware (parameters, muuntaja
   for JSON request bodies), and wrap a static resource handler for the
   vendored datastar JS and our stylesheet."
  (:require [clojure.tools.logging :as log]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [xyz.psyclyx.light.web.handlers.adapter :as h-adapter]
            [xyz.psyclyx.light.web.handlers.devices :as h-devices]
            [xyz.psyclyx.light.web.handlers.events :as h-events]
            [xyz.psyclyx.light.web.handlers.pairing :as h-pairing]))

(defn- wrap-log-errors
  "Last-resort middleware: logs any uncaught exception and returns 500
   so a stray throw doesn't kill an SSE stream silently."
  [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (log/error t "Unhandled exception" {:uri (:uri req) :method (:request-method req)})
        {:status 500
         :headers {"content-type" "text/plain; charset=utf-8"}
         :body    "internal error"}))))

(defn build
  "Builds the ring handler from the components passed in `deps`. Deps:
     :db, :event-bus, :devices, :pairing, :zigbee2mqtt"
  [deps]
  (ring/ring-handler
    (ring/router
      (vec (concat (h-devices/routes deps)
                   (h-pairing/routes deps)
                   (h-adapter/routes deps)
                   (h-events/routes deps)))
      {:data {:muuntaja   m/instance
              :middleware [parameters/parameters-middleware
                           muuntaja/format-middleware
                           wrap-log-errors]}})
    (ring/routes
      (ring/create-resource-handler {:path "/" :root "public"})
      (ring/create-default-handler))))
