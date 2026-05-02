(ns xyz.psyclyx.light.web.views.devices
  "Chassis hiccup for the lights dashboard.

   The page is a grid of light cards. Each card renders the current
   state and exposes simple controls: power toggle, brightness slider,
   color-temp slider, and a hex color picker. Datastar wires them to
   POST endpoints — server is the source of truth, SSE pushes the
   resulting state changes back to all subscribers."
  (:require [clojure.string :as str]
            [xyz.psyclyx.light.web.views.layout :as layout]))

;; -- Helpers --------------------------------------------------------------

(defn- device-id [friendly] (str "device-" (str/replace friendly #"[^a-zA-Z0-9_-]" "_")))

(defn- has-feature?
  "z2m's `exposes` is a tree of feature descriptors. We just stringify
   and search for the feature name; good enough until we want type-
   aware UIs."
  [device feat]
  (str/includes? (pr-str (:exposes device)) (str "\"" feat "\"")))

(defn- light? [{:keys [type] :as device}]
  (or (= "light" (some-> type str str/lower-case))
      (has-feature? device "brightness")
      (has-feature? device "state")))

;; -- Controls -------------------------------------------------------------

(defn- power-control [friendly state]
  (let [on? (= "ON" (some-> (:state state) str/upper-case))]
    [:button {:class (str "power " (if on? "on" "off"))
              :data-on:click
              (str "@post('/devices/" friendly "/set',{contentType:'json',body:{state:'"
                   (if on? "OFF" "ON") "'}})")}
     (if on? "ON" "OFF")]))

(defn- brightness-control [friendly state]
  (when (contains? state :brightness)
    [:label.range
     [:span "Brightness"]
     [:input {:type "range"
              :min  "0" :max "254" :step "1"
              :value (or (:brightness state) 0)
              :data-on:change__debounce.150ms
              (str "@post('/devices/" friendly
                   "/set',{contentType:'json',body:{brightness:Number(evt.target.value)}})")}]]))

(defn- color-temp-control [friendly state]
  (when (contains? state :color_temp)
    [:label.range
     [:span "Color temp (mireds)"]
     [:input {:type "range"
              :min  "153" :max "500" :step "1"
              :value (or (:color_temp state) 250)
              :data-on:change__debounce.150ms
              (str "@post('/devices/" friendly
                   "/set',{contentType:'json',body:{color_temp:Number(evt.target.value)}})")}]]))

(defn- color-control [friendly _state]
  (when true ;; show on every light; harmless on white-only bulbs
    [:label.range
     [:span "Color"]
     [:input {:type "color"
              :data-on:input__debounce.250ms
              (str "@post('/devices/" friendly
                   "/set',{contentType:'json',body:{color:{hex:evt.target.value}}})")}]]))

;; -- Cards ----------------------------------------------------------------

(defn device-card
  "Renders a single light card. Element id is stable so SSE patches can
   morph it in place."
  [device state]
  [:article {:id (device-id (:friendly_name device)) :class "card light-card"}
   [:header.card-header
    [:div.card-title (:friendly_name device)]
    [:div.card-meta (str (:vendor device) " • " (:model device))]]
   [:div.card-state
    (when state
      (into [:dl]
            (mapcat (fn [[k v]]
                      [[:dt (name k)]
                       [:dd (if (map? v) (pr-str v) (str v))]])
                    (sort-by key
                             (filter (fn [[k _]]
                                       (#{:state :brightness :color_temp :color} k))
                                     state)))))]
   (into [:div.card-controls]
         (cond-> []
           :always (conj (power-control (:friendly_name device) state))
           (has-feature? device "brightness") (conj (brightness-control (:friendly_name device) state))
           (has-feature? device "color_temp") (conj (color-temp-control (:friendly_name device) state))
           (has-feature? device "color")      (conj (color-control     (:friendly_name device) state))))])

(defn devices-grid
  "Renders the full grid of lights. Used both for initial page render
   and for SSE patches (target id `devices-grid`)."
  [snapshot]
  (let [devs (->> (vals (:devices snapshot))
                  (filter light?)
                  (sort-by (juxt :friendly_name)))]
    [:div {:id "devices-grid" :class "card-grid"}
     (if (empty? devs)
       [:p.empty-state "No paired lights yet — head over to "
        [:a {:href "/pairing"} "Pair"] " to add one."]
       (for [d devs]
         (device-card d (get (:state snapshot) (:friendly_name d)))))]))

;; -- Page -----------------------------------------------------------------

(defn page [snapshot]
  [::layout/page {:title "Lights"}
   [::layout/section {:title "Lights"}
    (devices-grid snapshot)]
   [:div {:data-init "@get('/events')"}]])
