(ns xyz.psyclyx.light.event-bus
  "Central pub/sub bus. MQTT messages, internal lifecycle events, and
   UI-relevant changes all flow through here.

   - Producers call `publish!` with an event map containing a :topic.
   - Consumers call `subscribe!` to obtain a channel for one topic, and
     are responsible for releasing it via `unsubscribe!`.

   The bus is intentionally thin: it owns the channel + publication and
   nothing else. Any structure, retention, or state derivation belongs
   in the component that consumes the events."
  (:require [clojure.core.async :as a]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]))

(defrecord EventBus [in-chan publication]
  component/Lifecycle
  (start [this]
    (if in-chan
      this
      (let [ch  (a/chan (a/sliding-buffer 1024))
            pub (a/pub ch :topic)]
        (log/info "Starting event bus")
        (assoc this :in-chan ch :publication pub))))
  (stop [this]
    (when in-chan
      (a/close! in-chan))
    (assoc this :in-chan nil :publication nil)))

(defn event-bus [] (map->EventBus {}))

(defn publish!
  "Publishes an event onto the bus. Non-blocking; if the inbound buffer
   is full the oldest event is dropped (sliding buffer)."
  [{:keys [in-chan]} event]
  (when in-chan
    (a/put! in-chan event))
  nil)

(defn subscribe!
  "Returns a channel receiving events whose :topic equals `topic`. The
   subscriber-side buffer defaults to a 64-element sliding buffer."
  ([bus topic]
   (subscribe! bus topic (a/sliding-buffer 64)))
  ([{:keys [publication]} topic buf-or-n]
   (let [out (a/chan buf-or-n)]
     (a/sub publication topic out)
     out)))

(defn unsubscribe!
  "Detaches `chan` from `topic` and closes it."
  [{:keys [publication]} topic chan]
  (when publication
    (a/unsub publication topic chan))
  (a/close! chan))
