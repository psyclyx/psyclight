(ns xyz.psyclyx.light.web.handlers.devices
  "Routes for the lights dashboard and per-device control."
  (:require [dev.onionpancakes.chassis.core :as h]
            [xyz.psyclyx.light.devices :as devices]
            [xyz.psyclyx.light.web.views.devices :as view]))

(defn- html-response [hiccup]
  {:status  200
   :headers {"content-type" "text/html; charset=utf-8"}
   :body    (str (h/html hiccup))})

(defn- index [deps]
  (fn [_req]
    (html-response (view/page (devices/snapshot (:devices deps))))))

(defn- set-device [deps]
  (fn [{:keys [path-params body-params]}]
    (let [friendly (:friendly path-params)
          delta    (or body-params {})]
      (devices/set-state! (:devices deps) friendly delta)
      {:status  204
       :headers {}})))

(defn routes [deps]
  [["/"                       {:get  {:handler (index deps)}}]
   ["/devices/:friendly/set"  {:post {:handler (set-device deps)}}]])
