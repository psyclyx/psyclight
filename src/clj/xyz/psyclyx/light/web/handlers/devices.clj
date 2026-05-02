(ns xyz.psyclyx.light.web.handlers.devices
  "Routes for the lights dashboard and per-device control.

   Datastar @post sends the full signal tree as the JSON body. Each
   card scopes its signals under `lights.<sid>` (sid = sanitized
   friendly), so the handler picks out the relevant subtree via the
   friendly name in the URL. We translate the camelCase signal names
   (`colorTemp`, `color`) into z2m's expected fields (`color_temp`,
   `color: {hex}`)."
  (:require [clojure.string :as str]
            [dev.onionpancakes.chassis.core :as h]
            [xyz.psyclyx.light.devices :as devices]
            [xyz.psyclyx.light.web.views.devices :as view]))

(defn- html-response [hiccup]
  {:status  200
   :headers {"content-type" "text/html; charset=utf-8"}
   :body    (str (h/html hiccup))})

(defn- index [deps]
  (fn [_req]
    (html-response (view/page (devices/snapshot (:devices deps))))))

(defn- sigid
  "Mirror of the view's sigid — must agree, since the page emits signals
   keyed by sigid and the handler picks them out by sigid."
  [friendly]
  (let [cleaned (str/replace friendly #"[^A-Za-z0-9]" "_")]
    (if (re-find #"^[A-Za-z_]" cleaned)
      cleaned
      (str "d_" cleaned))))

(defn- card-signals
  "Pulls the relevant signal subtree out of the body and translates it
   into z2m's set-payload shape."
  [body friendly]
  (let [sid    (keyword (sigid friendly))
        scoped (get-in body [:lights sid])]
    (cond-> {}
      (contains? scoped :state)      (assoc :state      (:state scoped))
      (contains? scoped :brightness) (assoc :brightness (:brightness scoped))
      (contains? scoped :colorTemp)  (assoc :color_temp (:colorTemp scoped))
      (contains? scoped :color)      (assoc :color      {:hex (:color scoped)}))))

(defn- set-device [deps]
  (fn [{:keys [path-params body-params]}]
    (let [friendly (:friendly path-params)
          delta    (card-signals body-params friendly)]
      (when (seq delta)
        (devices/set-state! (:devices deps) friendly delta))
      {:status 204})))

(defn routes [deps]
  [["/"                       {:get  {:handler (index deps)}}]
   ["/devices/:friendly/set"  {:post {:handler (set-device deps)}}]])
