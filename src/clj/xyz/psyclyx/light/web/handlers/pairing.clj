(ns xyz.psyclyx.light.web.handlers.pairing
  "Routes for the pairing flows: permit_join and touchlink.

   Datastar @post serializes the page's signal tree as the JSON body.
   For permit_join, the page declares one signal `permitJoinSeconds`
   bound to the duration input; this handler reads it. Per-row
   touchlink actions encode their target in the URL path (datastar
   has no notion of \"event payload\" — its body is always signals)."
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
    (let [seconds (long (or (:permitJoinSeconds body-params) 0))]
      (pairing/permit-join! (:pairing deps) seconds)
      {:status 204})))

(defn- permit-join-close [deps]
  (fn [_req]
    (pairing/permit-join! (:pairing deps) 0)
    {:status 204}))

(defn- touchlink-scan [deps]
  (fn [_req]
    (pairing/touchlink-scan! (:pairing deps))
    {:status 204}))

(defn- touchlink-reset-nearest [deps]
  (fn [_req]
    (pairing/touchlink-reset-nearest! (:pairing deps))
    {:status 204}))

(defn- target-from-path [{:keys [path-params]}]
  {:ieee_address (:ieee path-params)
   :channel      (Long/parseLong (:channel path-params))})

(defn- touchlink-identify [deps]
  (fn [req]
    (pairing/touchlink-identify! (:pairing deps) (target-from-path req))
    {:status 204}))

(defn- touchlink-factory-reset [deps]
  (fn [req]
    (pairing/touchlink-factory-reset! (:pairing deps) (target-from-path req))
    {:status 204}))

(defn routes [deps]
  [["/pairing"                                       {:get  {:handler (index deps)}}]
   ["/pairing/permit-join"                           {:post {:handler (permit-join deps)}}]
   ["/pairing/permit-join/close"                     {:post {:handler (permit-join-close deps)}}]
   ["/pairing/touchlink/scan"                        {:post {:handler (touchlink-scan deps)}}]
   ["/pairing/touchlink/reset-nearest"               {:post {:handler (touchlink-reset-nearest deps)}}]
   ["/pairing/touchlink/identify/:ieee/:channel"     {:post {:handler (touchlink-identify deps)}}]
   ["/pairing/touchlink/factory-reset/:ieee/:channel"{:post {:handler (touchlink-factory-reset deps)}}]])
