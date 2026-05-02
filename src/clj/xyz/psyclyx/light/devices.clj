(ns xyz.psyclyx.light.devices
  "Device registry + light control.

   Listens on the MQTT bus for two classes of messages:
     - `zigbee2mqtt/bridge/devices` (retained snapshot of paired devices)
     - `zigbee2mqtt/<friendly_name>` (per-device state updates)

   Maintains an internal atom keyed by `:friendly_name` (z2m's stable
   handle for a device) and republishes change notifications on the bus
   under `:devices/changed`. The web layer subscribes to that topic to
   stream UI updates over SSE.

   Outbound commands are issued through `set!`, which publishes a
   payload onto `zigbee2mqtt/<friendly>/set`. We don't model the Hue
   light's full capability set here — z2m is the canonical translator
   between the wire format and JSON, so we just thread payloads
   through. The web layer is responsible for shaping them."
  (:require [clojure.core.async :as a]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [xyz.psyclyx.light.event-bus :as bus]
            [xyz.psyclyx.light.mqtt :as mqtt]))

;; -- Topic parsing --------------------------------------------------------

(def ^:private base-topic "zigbee2mqtt")

(defn- topic-suffix
  "Returns the part of an MQTT topic after the z2m base, or nil if it
   does not belong to z2m."
  [topic]
  (when (str/starts-with? topic (str base-topic "/"))
    (subs topic (inc (count base-topic)))))

(defn- bridge-topic? [suffix]
  (str/starts-with? suffix "bridge/"))

;; -- Snapshot helpers -----------------------------------------------------

(defn- describe
  "Reduces the verbose z2m bridge/devices entry to the fields the UI
   cares about."
  [d]
  (let [definition (:definition d)]
    {:friendly_name (:friendly_name d)
     :ieee_address  (:ieee_address d)
     :type          (:type d)
     :model         (:model_id d)
     :vendor        (:manufacturer d)
     :description   (:description definition)
     :exposes       (:exposes definition)
     :supported     (:supported d)
     :interviewing  (:interviewing d)}))

(defn- index-devices [snapshot]
  (into {}
        (comp (filter map?)
              (map describe)
              (filter :friendly_name)
              (map (juxt :friendly_name identity)))
        snapshot))

;; -- Internal state derivation --------------------------------------------

(defn- merge-state [state friendly delta]
  (assoc state friendly (merge (get state friendly) delta)))

(defn- handle-message
  "Updates `state-atom` based on a single z2m MQTT message. Returns the
   set of friendly names that changed (so callers can republish per-
   device events)."
  [state-atom topic payload]
  (when-let [suffix (topic-suffix topic)]
    (cond
      (= "bridge/devices" suffix)
      (let [m (index-devices payload)]
        (swap! state-atom assoc :devices m)
        (set (keys m)))

      (bridge-topic? suffix)
      nil

      ;; Per-device state update. z2m posts on
      ;; `zigbee2mqtt/<friendly_name>` with a JSON state payload; some
      ;; devices use slash-segmented friendly names so we use the entire
      ;; suffix as the key.
      :else
      (do (swap! state-atom update :state merge-state suffix payload)
          #{suffix}))))

;; -- Component ------------------------------------------------------------

(defn- run-loop! [event-bus state-atom sub-chan]
  (a/go-loop []
    (when-let [{:keys [mqtt-topic payload]} (a/<! sub-chan)]
      (try
        (when-let [changed (handle-message state-atom mqtt-topic payload)]
          (bus/publish! event-bus
            {:topic    :devices/changed
             :changed  changed
             :snapshot @state-atom}))
        (catch Throwable t
          (log/error t "devices loop error" {:topic mqtt-topic})))
      (recur))))

(defrecord Devices [event-bus mqtt state-atom sub-chan]
  component/Lifecycle
  (start [this]
    (let [s     (atom {:devices {} :state {}})
          ch    (bus/subscribe! event-bus :mqtt/message)]
      (run-loop! event-bus s ch)
      (assoc this :state-atom s :sub-chan ch)))
  (stop [this]
    (when sub-chan (bus/unsubscribe! event-bus :mqtt/message sub-chan))
    (assoc this :state-atom nil :sub-chan nil)))

(defn devices [] (map->Devices {}))

;; -- Public API -----------------------------------------------------------

(defn snapshot
  "Returns the current device registry + state."
  [{:keys [state-atom]}]
  (if state-atom @state-atom {:devices {} :state {}}))

(defn set-state!
  "Publishes a state command to z2m's `<friendly>/set` topic. `delta` is
   a map of fields like {:state \"ON\" :brightness 200 :color {:x ... :y ...}}."
  [{:keys [mqtt]} friendly delta]
  (mqtt/publish! mqtt (str base-topic "/" friendly "/set") delta))
