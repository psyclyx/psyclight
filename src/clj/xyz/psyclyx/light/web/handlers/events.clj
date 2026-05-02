(ns xyz.psyclyx.light.web.handlers.events
  "Server-Sent Events endpoints that stream live state updates to the
   datastar client. Two endpoints, one per page (lights dashboard and
   pairing) — each subscribes to the bus topics relevant to its page
   and emits patch-elements events that morph specific element ids."
  (:require [xyz.psyclyx.light.devices :as devices]
            [xyz.psyclyx.light.pairing :as pairing]
            [xyz.psyclyx.light.web.sse :as sse]
            [xyz.psyclyx.light.web.views.devices :as devices-view]
            [xyz.psyclyx.light.web.views.pairing :as pairing-view]))

;; -- Lights dashboard stream ---------------------------------------------

(defn- devices-event [{:keys [snapshot]}]
  (sse/patch-elements (devices-view/devices-grid snapshot)))

(defn- devices-prelude [deps]
  [(sse/patch-elements
     (devices-view/devices-grid (devices/snapshot (:devices deps))))])

(defn- devices-stream [deps]
  (fn [_req]
    (sse/response
      (sse/bus-stream (:event-bus deps)
                      [:devices/changed]
                      devices-event
                      {:prelude (devices-prelude deps)}))))

;; -- Pairing stream ------------------------------------------------------

(defn- pairing-event [{:keys [snapshot]}]
  (let [renders [(pairing-view/permit-join-section snapshot)
                 (pairing-view/touchlink-section   snapshot)]]
    (apply str (map sse/patch-elements renders))))

(defn- pairing-prelude [deps]
  [(pairing-event {:snapshot (pairing/snapshot (:pairing deps))})])

(defn- pairing-stream [deps]
  (fn [_req]
    (sse/response
      (sse/bus-stream (:event-bus deps)
                      [:pairing/changed]
                      pairing-event
                      {:prelude (pairing-prelude deps)}))))

;; -- Routes --------------------------------------------------------------

(defn routes [deps]
  [["/events"         {:get {:handler (devices-stream deps)}}]
   ["/pairing/events" {:get {:handler (pairing-stream deps)}}]])
