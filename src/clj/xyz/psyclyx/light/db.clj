(ns xyz.psyclyx.light.db
  "SQLite persistence for psyclight. Stores the bits our app uniquely
   owns: adapter selection, network parameters (so the zigbee mesh
   survives a wipe of z2m's data dir), and any user-defined data we
   accrete (rooms, scenes — none yet).

   z2m's own device DB lives under (:zigbee2mqtt :data-dir) and is
   regenerated/owned by the supervised z2m process; we don't touch it
   directly."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [com.stuartsierra.component :as component]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]))

;; -- Migrations -----------------------------------------------------------

(def ^:private migrations
  ["CREATE TABLE IF NOT EXISTS adapter (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      type TEXT,
      port TEXT,
      baudrate INTEGER,
      adapter_disable_led INTEGER NOT NULL DEFAULT 0,
      configured INTEGER NOT NULL DEFAULT 0,
      updated_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
    )"
   "INSERT OR IGNORE INTO adapter (id) VALUES (1)"
   "CREATE TABLE IF NOT EXISTS network (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      pan_id INTEGER,
      ext_pan_id TEXT,
      channel INTEGER NOT NULL DEFAULT 11,
      network_key TEXT,
      updated_at INTEGER NOT NULL DEFAULT (strftime('%s','now'))
    )"
   "INSERT OR IGNORE INTO network (id) VALUES (1)"])

(defn- migrate! [ds]
  (doseq [stmt migrations]
    (jdbc/execute! ds [stmt])))

;; -- Component ------------------------------------------------------------

(defrecord SQLiteDB [config datasource]
  component/Lifecycle
  (start [this]
    (let [{:keys [path]} (:db config)]
      (some-> (.getParent (io/file path)) io/file .mkdirs)
      (let [ds (jdbc/get-datasource {:dbtype "sqlite" :dbname path})]
        (log/info "Opening SQLite at" path)
        (migrate! ds)
        (assoc this :datasource ds))))
  (stop [this]
    (assoc this :datasource nil)))

(defn db [] (map->SQLiteDB {}))

;; -- Query helpers --------------------------------------------------------

(def ^:private opts {:builder-fn rs/as-unqualified-lower-maps})

(defn execute! [{:keys [datasource]} sql-vec]
  (jdbc/execute! datasource sql-vec opts))

(defn execute-one! [{:keys [datasource]} sql-vec]
  (jdbc/execute-one! datasource sql-vec opts))
