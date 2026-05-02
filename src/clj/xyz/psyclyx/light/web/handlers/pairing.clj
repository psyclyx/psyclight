(ns xyz.psyclyx.light.web.handlers.pairing
  "Routes for the pairing flows: permit_join and touchlink."
  (:require [dev.onionpancakes.chassis.core :as h]
            [xyz.psyclyx.light.pairing :as pairing]
            [xyz.psyclyx.light.web.views.pairing :as view]))

(defn- html-response [hiccup]
  {:status  200
   :headers {"content-type" "text/html; charset=utf-8"}
   :body    (str (h/html hiccup))})

(defn- index [deps]
  (fn [_req]
    (html-response (view/page (pairing/snapshot (:pairing deps))))))

(defn- permit-join [deps]
  (fn [{:keys [body-params]}]
    (let [seconds (long (or (:seconds body-params) 0))]
      (pairing/permit-join! (:pairing deps) seconds)
      {:status 204})))

(defn- touchlink-scan [deps]
  (fn [_req]
    (pairing/touchlink-scan! (:pairing deps))
    {:status 204}))

(defn- touchlink-identify [deps]
  (fn [{:keys [body-params]}]
    (pairing/touchlink-identify! (:pairing deps) body-params)
    {:status 204}))

(defn- touchlink-factory-reset [deps]
  (fn [{:keys [body-params]}]
    (pairing/touchlink-factory-reset! (:pairing deps) body-params)
    {:status 204}))

(defn- touchlink-reset-nearest [deps]
  (fn [_req]
    (pairing/touchlink-reset-nearest! (:pairing deps))
    {:status 204}))

(defn routes [deps]
  [["/pairing"                            {:get  {:handler (index deps)}}]
   ["/pairing/permit-join"                {:post {:handler (permit-join deps)}}]
   ["/pairing/touchlink/scan"             {:post {:handler (touchlink-scan deps)}}]
   ["/pairing/touchlink/identify"         {:post {:handler (touchlink-identify deps)}}]
   ["/pairing/touchlink/factory-reset"    {:post {:handler (touchlink-factory-reset deps)}}]
   ["/pairing/touchlink/reset-nearest"    {:post {:handler (touchlink-reset-nearest deps)}}]])
