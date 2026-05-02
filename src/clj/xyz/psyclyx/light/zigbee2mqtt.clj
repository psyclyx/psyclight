(ns xyz.psyclyx.light.zigbee2mqtt
  "Supervised zigbee2mqtt process.

   The component owns z2m's data directory under our state dir, writes
   `configuration.yaml` on each start (and on every adapter change),
   then spawns the `zigbee2mqtt` binary with `ZIGBEE2MQTT_DATA` pointed
   at that directory.

   Network parameters (network_key, pan_id, ext_pan_id, channel) are
   generated once and persisted in our DB so they survive a wipe of the
   z2m data dir and so a stray system z2m can never accidentally take
   over our mesh.

   Adapter selection is runtime state: until the user picks an adapter
   in the UI, the component is in an idle state with no child process.
   Calling `(reload! component)` re-reads the DB and (re)starts z2m."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [xyz.psyclyx.light.db :as db]
            [xyz.psyclyx.light.process :as process])
  (:import [java.io StringWriter]
           [java.security SecureRandom]
           [java.util LinkedHashMap]
           [org.yaml.snakeyaml Yaml DumperOptions DumperOptions$FlowStyle]))

;; -- Sentinel -------------------------------------------------------------
;;
;; A marker file dropped in z2m's data dir so an unmanaged system z2m
;; can be told (by a human or by our own boot check) that this dir is
;; ours.

(def ^:private sentinel-name ".psyclight-managed")

(defn- ensure-data-dir! [path]
  (let [d (io/file path)]
    (.mkdirs d)
    (let [s (io/file d sentinel-name)]
      (when-not (.exists s)
        (spit s "managed-by=psyclight\n")))
    path))

;; -- Network parameter generation -----------------------------------------

(defn- ^:private secure-random [] (SecureRandom.))

(defn- random-bytes [^long n]
  (let [bs (byte-array n)]
    (.nextBytes (secure-random) bs)
    bs))

(defn- bytes->hex [^bytes bs]
  (apply str (map #(format "%02x" (bit-and % 0xFF)) bs)))

(defn- hex->bytes [^String hex]
  (let [n (/ (count hex) 2)
        bs (byte-array n)]
    (dotimes [i n]
      (let [b (Integer/parseInt (subs hex (* 2 i) (+ (* 2 i) 2)) 16)]
        (aset-byte bs i (unchecked-byte b))))
    bs))

(defn- random-pan-id
  "16-bit PAN ID avoiding 0x0000 (broadcast), 0xFFFF (reserved), and
   0xFFFE (used by some stacks as a default)."
  []
  (loop []
    (let [v (bit-and (.nextInt (secure-random)) 0xFFFF)]
      (if (or (zero? v) (= v 0xFFFF) (= v 0xFFFE))
        (recur)
        v))))

(defn- ensure-network-params!
  "Reads or generates the network params, persisting freshly generated
   values to the DB. Returns the materialized row."
  [db-component]
  (let [row (db/execute-one! db-component ["SELECT * FROM network WHERE id=1"])]
    (if (and (:network_key row) (:ext_pan_id row) (:pan_id row))
      row
      (let [nk      (bytes->hex (random-bytes 16))
            ext-pan (bytes->hex (random-bytes 8))
            pan     (random-pan-id)]
        (log/info "Generating fresh zigbee network params")
        (db/execute! db-component
                     ["UPDATE network SET network_key=?, ext_pan_id=?, pan_id=?,
                                          updated_at=strftime('%s','now')
                       WHERE id=1"
                      nk ext-pan pan])
        (assoc row :network_key nk :ext_pan_id ext-pan :pan_id pan)))))

;; -- Adapter --------------------------------------------------------------

(defn- read-adapter [db-component]
  (db/execute-one! db-component ["SELECT * FROM adapter WHERE id=1"]))

(defn- adapter-configured? [adapter]
  (and adapter (= 1 (:configured adapter)) (:port adapter)))

;; -- YAML rendering -------------------------------------------------------
;;
;; snakeyaml works on java.util.Map; we use LinkedHashMap so the
;; rendered file is stable and diffable.

(defn- linked [pairs]
  (let [m (LinkedHashMap.)]
    (doseq [[k v] pairs]
      (.put m (name k) v))
    m))

(defn- hex->byte-list
  "z2m takes network_key / ext_pan_id as a JSON-array-style list of
   integers. Translate from our hex representation."
  [hex]
  (mapv #(bit-and % 0xFF) (hex->bytes hex)))

(defn- adapter-section [adapter]
  (linked
   (cond-> [[:port (:port adapter)]]
     (:type adapter)
     (conj [:adapter (:type adapter)])

     (:baudrate adapter)
     (conj [:baudrate (int (:baudrate adapter))])

     (= 1 (:adapter_disable_led adapter))
     (conj [:disable_led true]))))

(defn- advanced-section [network]
  (linked
   [[:log_output      ["console"]]
    [:log_level       "info"]
    [:network_key     (hex->byte-list (:network_key network))]
    [:pan_id          (int (:pan_id network))]
    [:ext_pan_id      (hex->byte-list (:ext_pan_id network))]
    [:channel         (int (:channel network))]]))

(defn- mqtt-section [{:keys [host port]}]
  (linked
   [[:base_topic "zigbee2mqtt"]
    [:server     (str "mqtt://" host ":" port)]]))

(defn- root-config [config adapter network]
  (linked
   [[:homeassistant false]
    [:permit_join   false]
    [:mqtt          (mqtt-section (:mqtt config))]
    [:serial        (adapter-section adapter)]
    [:advanced      (advanced-section network)]
    [:frontend      false]]))

(defn- dump-yaml ^String [data]
  (let [opts (doto (DumperOptions.)
               (.setDefaultFlowStyle DumperOptions$FlowStyle/BLOCK)
               (.setIndent 2))
        sw   (StringWriter.)]
    (.dump (Yaml. opts) data sw)
    (str sw)))

(defn- write-config! [data-dir config adapter network]
  (let [path (str data-dir "/configuration.yaml")
        body (str "# Generated by psyclight; do not edit.\n"
                  (dump-yaml (root-config config adapter network)))]
    (spit path body)
    path))

;; -- Spawn ----------------------------------------------------------------

(defn- spawn-z2m! [config data-dir]
  (let [bin (get-in config [:zigbee2mqtt :bin])]
    (process/spawn!
     {:name    "zigbee2mqtt"
      :command [bin]
      :env     {"ZIGBEE2MQTT_DATA" data-dir}})))

;; -- Component ------------------------------------------------------------

(defn- start-if-configured!
  "If the DB has an adapter configured, generates configuration.yaml
   and spawns z2m. Otherwise returns nil — the component remains in an
   idle state until the user configures an adapter."
  [{:keys [config db]}]
  (let [adapter (read-adapter db)]
    (if-not (adapter-configured? adapter)
      (do (log/info "No adapter configured; zigbee2mqtt idle.") nil)
      (let [data-dir (get-in config [:zigbee2mqtt :data-dir])
            _        (ensure-data-dir! data-dir)
            network  (ensure-network-params! db)
            cfg-path (write-config! data-dir config adapter network)]
        (log/info "Wrote z2m config" {:path cfg-path :port (:port adapter)})
        (spawn-z2m! config data-dir)))))

(defrecord Zigbee2Mqtt [config db mosquitto handle-atom]
  component/Lifecycle
  (start [this]
    (let [a      (atom nil)
          handle (start-if-configured! this)]
      (when handle (reset! a handle))
      (assoc this :handle-atom a)))
  (stop [this]
    (when-let [h (and handle-atom @handle-atom)]
      (process/stop! h))
    (some-> handle-atom (reset! nil))
    (assoc this :handle-atom nil)))

(defn zigbee2mqtt [] (map->Zigbee2Mqtt {}))

;; -- Public API -----------------------------------------------------------

(defn running?
  "True when a child z2m process is currently alive."
  [{:keys [handle-atom]}]
  (boolean (some-> handle-atom deref process/alive?)))

(defn reload!
  "Stops the current child (if any), re-reads adapter/network state from
   the DB, and respawns z2m if an adapter is configured. Used after the
   user changes adapter selection in the UI."
  [{:keys [handle-atom] :as component}]
  (when handle-atom
    (when-let [h @handle-atom]
      (process/stop! h)
      (reset! handle-atom nil))
    (when-let [h (start-if-configured! component)]
      (reset! handle-atom h))
    (running? component)))

(defn list-serial-candidates
  "Heuristically lists likely adapter device paths on the host. Used by
   the UI to pre-populate the adapter selection screen."
  []
  (let [by-id (io/file "/dev/serial/by-id")]
    (cond-> []
      (.isDirectory by-id)
      (into (->> (.listFiles by-id)
                 (map #(.getAbsolutePath ^java.io.File %))
                 (sort)))

      :always
      (into (->> (.listFiles (io/file "/dev"))
                 (filter (fn [^java.io.File f]
                           (let [n (.getName f)]
                             (or (str/starts-with? n "ttyUSB")
                                 (str/starts-with? n "ttyACM")))))
                 (map #(str "/dev/" (.getName ^java.io.File %)))
                 (sort))))))
