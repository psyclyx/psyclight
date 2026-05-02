(ns xyz.psyclyx.light.web.handlers.adapter
  "Routes for adapter selection. The form posts plain
   application/x-www-form-urlencoded; we update our DB and ask the z2m
   supervisor to reload."
  (:require [dev.onionpancakes.chassis.core :as h]
            [xyz.psyclyx.light.db :as db]
            [xyz.psyclyx.light.zigbee2mqtt :as z2m]
            [xyz.psyclyx.light.web.views.adapter :as view]))

(defn- html-response [hiccup]
  {:status  200
   :headers {"content-type" "text/html; charset=utf-8"}
   :body    (str (h/html hiccup))})

(defn- current [{:keys [db zigbee2mqtt]}]
  {:adapter  (db/execute-one! db ["SELECT * FROM adapter WHERE id=1"])
   :ports    (z2m/list-serial-candidates)
   :running? (z2m/running? zigbee2mqtt)})

(defn- index [deps]
  (fn [_req]
    (html-response (view/page (current deps)))))

(defn- parse-baudrate [s]
  (when (and s (seq (str s)))
    (try (Long/parseLong (str s)) (catch Exception _ nil))))

(defn- save [{:keys [db zigbee2mqtt] :as deps}]
  (fn [{:keys [form-params]}]
    (let [type     (get form-params "type")
          port     (get form-params "port")
          baud     (parse-baudrate (get form-params "baudrate"))
          disable? (= "1" (get form-params "disable_led"))]
      (db/execute! db
        ["UPDATE adapter
          SET type=?, port=?, baudrate=?, adapter_disable_led=?,
              configured=?, updated_at=strftime('%s','now')
          WHERE id=1"
         type port baud (if disable? 1 0)
         (if (and type port (seq port)) 1 0)])
      (z2m/reload! zigbee2mqtt)
      ;; Re-render so the user sees the updated state.
      (html-response (view/page (current deps))))))

(defn routes [deps]
  [["/adapter" {:get  {:handler (index deps)}
                :post {:handler (save deps)}}]])
