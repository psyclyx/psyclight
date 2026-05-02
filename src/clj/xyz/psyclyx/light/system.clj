(ns xyz.psyclyx.light.system
  "Component system construction. The system is the wiring diagram: it
   lists every component, its dependencies, and nothing else. Behavior
   lives in the components.

   Lifecycle ordering follows the dependency edges:
     mosquitto → mqtt → devices/pairing
     mosquitto + db → zigbee2mqtt
     all of the above → web-server"
  (:require [com.stuartsierra.component :as component]
            [xyz.psyclyx.light.db          :as db]
            [xyz.psyclyx.light.devices     :as devices]
            [xyz.psyclyx.light.event-bus   :as event-bus]
            [xyz.psyclyx.light.mosquitto   :as mosquitto]
            [xyz.psyclyx.light.mqtt        :as mqtt]
            [xyz.psyclyx.light.pairing     :as pairing]
            [xyz.psyclyx.light.zigbee2mqtt :as zigbee2mqtt]
            [xyz.psyclyx.light.web.server  :as web-server]))

(defn make-system
  "Builds the system map from the given immutable `config` map."
  [config]
  (component/system-map
    :config       config
    :event-bus    (event-bus/event-bus)
    :db           (component/using (db/db) {:config :config})
    :mosquitto    (component/using (mosquitto/mosquitto) {:config :config})
    :mqtt         (component/using (mqtt/mqtt)
                                   {:config    :config
                                    :event-bus :event-bus
                                    :mosquitto :mosquitto})
    :zigbee2mqtt  (component/using (zigbee2mqtt/zigbee2mqtt)
                                   {:config    :config
                                    :db        :db
                                    :mosquitto :mosquitto})
    :devices      (component/using (devices/devices)
                                   {:event-bus :event-bus
                                    :mqtt      :mqtt})
    :pairing      (component/using (pairing/pairing)
                                   {:event-bus :event-bus
                                    :mqtt      :mqtt})
    :web-server   (component/using (web-server/web-server)
                                   {:config      :config
                                    :db          :db
                                    :event-bus   :event-bus
                                    :devices     :devices
                                    :pairing     :pairing
                                    :zigbee2mqtt :zigbee2mqtt})))
