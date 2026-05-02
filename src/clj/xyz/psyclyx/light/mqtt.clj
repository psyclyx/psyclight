(ns xyz.psyclyx.light.mqtt
  "MQTT client to our supervised mosquitto broker.

   - Inbound: subscribes to topics (default \"zigbee2mqtt/#\") and fans
     each message onto the event-bus as a {:topic :mqtt/message ...}
     event with the raw broker topic threaded through.
   - Outbound: `publish!` sends a payload (string or JSON-encodable
     value) to a broker topic.

   The MQTT client component depends on mosquitto so it starts after
   the broker is up, and provides a dependency target for higher-level
   components (devices, pairing) that need a working broker before they
   can usefully exist."
  (:require [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [charred.api :as json]
            [xyz.psyclyx.light.event-bus :as bus])
  (:import [org.eclipse.paho.client.mqttv3
            IMqttDeliveryToken
            MqttCallback MqttClient MqttConnectOptions
            MqttException MqttMessage]
           [org.eclipse.paho.client.mqttv3.persist MemoryPersistence]))

;; -- Helpers --------------------------------------------------------------

(defn- broker-uri [{:keys [host port]}]
  (str "tcp://" host ":" port))

(defn- decode-payload
  "Decodes a paho payload as JSON if possible, otherwise as a UTF-8 string."
  [^MqttMessage msg]
  (let [s (String. (.getPayload msg) "UTF-8")]
    (try (json/read-json s :key-fn keyword)
         (catch Exception _ s))))

(defn- on-message
  "Translates a paho callback into a bus event."
  [event-bus topic ^MqttMessage msg]
  (bus/publish! event-bus
    {:topic      :mqtt/message
     :mqtt-topic topic
     :payload    (decode-payload msg)}))

(defn- mk-callback [event-bus]
  (reify MqttCallback
    (^void connectionLost [_ ^Throwable cause]
      (log/warn cause "MQTT connection lost")
      (bus/publish! event-bus {:topic :mqtt/connection-lost}))
    (^void messageArrived [_ ^String topic ^MqttMessage msg]
      (try (on-message event-bus topic msg)
           (catch Throwable t
             (log/error t "MQTT message handler failed" {:topic topic}))))
    (^void deliveryComplete [_ ^IMqttDeliveryToken _token])))

;; -- Component ------------------------------------------------------------

(defrecord MqttClient* [config event-bus mosquitto subscriptions client]
  component/Lifecycle
  (start [this]
    (let [uri        (broker-uri (:mqtt config))
          client-id  (str "psyclight-" (System/currentTimeMillis))
          c          (MqttClient. uri client-id (MemoryPersistence.))
          opts       (doto (MqttConnectOptions.)
                       (.setAutomaticReconnect true)
                       (.setCleanSession true)
                       (.setKeepAliveInterval 30))]
      (.setCallback c (mk-callback event-bus))
      (log/info "Connecting MQTT to" uri)
      (.connect c opts)
      (doseq [t (or subscriptions ["zigbee2mqtt/#"])]
        (log/info "Subscribing" t)
        (.subscribe c ^String t (int 0)))
      (assoc this :client c)))
  (stop [this]
    (when client
      (try
        (when (.isConnected ^MqttClient client)
          (.disconnect ^MqttClient client))
        (.close ^MqttClient client)
        (catch MqttException e
          (log/warn e "Error disconnecting MQTT"))))
    (assoc this :client nil)))

(defn mqtt
  "Constructs an MQTT client component. `subscriptions` is an optional
   vector of broker topic filters; defaults to [\"zigbee2mqtt/#\"]."
  ([] (mqtt {}))
  ([{:keys [subscriptions]}]
   (map->MqttClient* {:subscriptions subscriptions})))

;; -- Public API -----------------------------------------------------------

(defn publish!
  "Publishes a payload to `topic`. `payload` may be a string or any
   JSON-encodable value. Options: :qos (0|1|2), :retained?."
  ([component topic payload] (publish! component topic payload {}))
  ([{:keys [^MqttClient client]} topic payload {:keys [qos retained?]
                                                :or   {qos 0 retained? false}}]
   (when client
     (let [body (if (string? payload)
                  payload
                  (json/write-json-str payload))
           bs   (.getBytes ^String body "UTF-8")
           msg  (doto (MqttMessage. bs)
                  (.setQos (int qos))
                  (.setRetained retained?))]
       (.publish client ^String topic msg)))))
