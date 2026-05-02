(ns xyz.psyclyx.light.pairing
  "Pairing flows: permit_join (the gentle/normal mode where the
   coordinator listens for nearby devices for a window) and touchlink
   (the aggressive mode where the dongle is physically held next to a
   device to commission/factory-reset it without needing it to be in
   pairing mode).

   The component subscribes to bridge events and maintains an atom of
   recent join events and the most recent touchlink scan results, which
   the web layer renders. Outbound commands are simple MQTT publishes
   to the `zigbee2mqtt/bridge/request/...` topics."
  (:require [clojure.core.async :as a]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [xyz.psyclyx.light.event-bus :as bus]
            [xyz.psyclyx.light.mqtt :as mqtt])
  (:import [java.time Instant]))

;; -- Topic helpers --------------------------------------------------------

(def ^:private z2m "zigbee2mqtt")
(def ^:private req-prefix (str z2m "/bridge/request/"))
(def ^:private rsp-prefix (str z2m "/bridge/response/"))
(def ^:private bridge-event-topic (str z2m "/bridge/event"))

(defn- response-topic [suffix] (str rsp-prefix suffix))

;; -- State derivation -----------------------------------------------------

(defn- now-iso [] (str (Instant/now)))

(defn- join-event? [payload]
  (and (map? payload)
       (#{"device_joined" "device_announced" "device_interview" "device_leave"}
        (or (:type payload) (some-> payload (get "type"))))))

(defn- record-bridge-event [state-atom payload]
  (let [event (assoc payload :recorded_at (now-iso))]
    (swap! state-atom update :events
           (fn [xs] (vec (take 50 (cons event xs)))))))

(defn- record-touchlink-scan [state-atom payload]
  (let [results (or (:found payload) (:result payload) [])
        ts      (now-iso)]
    (swap! state-atom assoc :touchlink {:scanned_at ts
                                        :found      (vec results)
                                        :status     (:status payload)})))

(defn- record-touchlink-action [state-atom action payload]
  (swap! state-atom update :touchlink
         #(assoc (or % {}) :last-action {:action action
                                         :at     (now-iso)
                                         :status (:status payload)
                                         :data   (:data payload)})))

;; -- Inbound dispatch -----------------------------------------------------

(defn- handle-message [state-atom topic payload]
  (cond
    (= topic bridge-event-topic)
    (when (join-event? payload)
      (record-bridge-event state-atom payload)
      :pairing/event-recorded)

    (= topic (response-topic "touchlink/scan"))
    (do (record-touchlink-scan state-atom payload)
        :pairing/touchlink-scan)

    (str/starts-with? topic (response-topic "touchlink/identify"))
    (do (record-touchlink-action state-atom :identify payload)
        :pairing/touchlink-identify)

    (str/starts-with? topic (response-topic "touchlink/factory_reset"))
    (do (record-touchlink-action state-atom :factory-reset payload)
        :pairing/touchlink-factory-reset)

    (str/starts-with? topic (response-topic "permit_join"))
    (do (swap! state-atom assoc :permit-join {:status (:status payload)
                                              :data   (:data payload)
                                              :at     (now-iso)})
        :pairing/permit-join)))

;; -- Component ------------------------------------------------------------

(defn- run-loop! [event-bus state-atom sub-chan]
  (a/go-loop []
    (when-let [{:keys [mqtt-topic payload]} (a/<! sub-chan)]
      (try
        (when-let [changed (handle-message state-atom mqtt-topic payload)]
          (bus/publish! event-bus
            {:topic    :pairing/changed
             :changed  changed
             :snapshot @state-atom}))
        (catch Throwable t
          (log/error t "pairing loop error" {:topic mqtt-topic})))
      (recur))))

(defrecord Pairing [event-bus mqtt state-atom sub-chan]
  component/Lifecycle
  (start [this]
    (let [s  (atom {:events [] :touchlink nil :permit-join nil})
          ch (bus/subscribe! event-bus :mqtt/message)]
      (run-loop! event-bus s ch)
      (assoc this :state-atom s :sub-chan ch)))
  (stop [this]
    (when sub-chan (bus/unsubscribe! event-bus :mqtt/message sub-chan))
    (assoc this :state-atom nil :sub-chan nil)))

(defn pairing [] (map->Pairing {}))

;; -- Public API -----------------------------------------------------------

(defn snapshot [{:keys [state-atom]}]
  (if state-atom @state-atom {:events [] :touchlink nil :permit-join nil}))

(defn permit-join!
  "Opens (or closes) the network for joins. `seconds` is the duration
   in seconds; pass 0 to close."
  [{:keys [mqtt]} seconds]
  (mqtt/publish! mqtt (str req-prefix "permit_join")
                 {:value (pos? seconds)
                  :time  seconds}))

(defn touchlink-scan!
  "Scans for nearby zigbee devices in touchlink range. The dongle must
   be physically close to the target device; a successful scan returns
   a list of {ieee_address, channel} entries on the response topic."
  [{:keys [mqtt]}]
  (mqtt/publish! mqtt (str req-prefix "touchlink/scan") {}))

(defn touchlink-identify!
  "Tells a touchlink-found device to identify (e.g. blink) so the
   operator can confirm they're targeting the right physical light."
  [{:keys [mqtt]} {:keys [ieee_address channel]}]
  (mqtt/publish! mqtt (str req-prefix "touchlink/identify")
                 {:ieee_address ieee_address
                  :channel      channel}))

(defn touchlink-factory-reset!
  "Factory-resets a touchlink-found device. The device leaves whatever
   network it was on and immediately rejoins ours (because we're the
   coordinator it can hear)."
  [{:keys [mqtt]} {:keys [ieee_address channel]}]
  (mqtt/publish! mqtt (str req-prefix "touchlink/factory_reset")
                 {:ieee_address ieee_address
                  :channel      channel}))
