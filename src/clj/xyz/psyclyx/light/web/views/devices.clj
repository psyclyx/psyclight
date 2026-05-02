(ns xyz.psyclyx.light.web.views.devices
  "Chassis hiccup for the lights dashboard.

   Each card declares its own signals (`lights.<sid>.{state,brightness,
   colorTemp,color}`) bound two-way to the controls. The page sends
   the full signal tree on @post; the server picks out the relevant
   subtree via the friendly name in the URL. The dl below the controls
   shows server-reported state, so the user sees both their intent
   (button/slider) and the truth (z2m's published state) without
   conflict."
  (:require [clojure.string :as str]
            [xyz.psyclyx.light.web.views.layout :as layout]))

;; -- Helpers --------------------------------------------------------------

(defn- sigid
  "Sanitize a friendly name into a signal identifier that's safe to use
   as a JS property accessor. Datastar evaluates expressions like
   `$lights.<sid>.state` as JS, so the id has to be a valid identifier
   — alnum + underscore, and not starting with a digit (z2m's default
   friendly name is the ieee address, e.g. `0x001788...`, which would
   otherwise parse as a numeric literal)."
  [friendly]
  (let [cleaned (str/replace friendly #"[^A-Za-z0-9]" "_")]
    (if (re-find #"^[A-Za-z_]" cleaned)
      cleaned
      (str "d_" cleaned))))

(defn- device-id [friendly] (str "device-" (sigid friendly)))

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

;; -- Card signals ---------------------------------------------------------
;;
;; chassis serializes attribute values as strings; we hand-build the
;; JS object literal so the runtime evaluates it as JSON.

(defn- initial-signals [sid state]
  (str "{lights:{" sid ":{"
       "state:'"      (or (:state state) "OFF") "',"
       "brightness:"  (long (or (:brightness state) 0)) ","
       "colorTemp:"   (long (or (:color_temp state) 250)) ","
       "color:'"      (or (:color state) "#ffffff") "'"
       "}}}"))

(defn- post-set [friendly]
  (str "@post('/devices/" friendly "/set')"))

;; Datastar v1 attribute syntax is `data-<plugin>:<key>` — colon, not
;; hyphen. `data-on-click` parses as plugin name "on-click" (which
;; isn't registered) and the handler is silently dropped; the `on`
;; plugin matches `data-on:click`.
(def ^:private k-on-click             (keyword "data-on:click"))
(def ^:private k-on-input-debounce150 (keyword "data-on:input__debounce.150ms"))
(def ^:private k-on-input-debounce250 (keyword "data-on:input__debounce.250ms"))

;; -- Controls -------------------------------------------------------------

(defn- power-control [friendly sid]
  (let [path (str "$lights." sid ".state")]
    [:button
     {:class      "power"
      :data-class (str "{on: " path " == 'ON', off: " path " != 'ON'}")
      :data-text  path
      k-on-click  (str path " = " path " == 'ON' ? 'OFF' : 'ON'; " (post-set friendly))}]))

(defn- range-control [friendly sid label sig-name min max]
  [:label.range
   [:span label]
   [:input {:type      "range"
            :min       (str min)
            :max       (str max)
            :step      "1"
            :data-bind (str "lights." sid "." sig-name)
            k-on-input-debounce150 (post-set friendly)}]])

(defn- color-control [friendly sid]
  [:label.range
   [:span "Color"]
   [:input {:type      "color"
            :data-bind (str "lights." sid ".color")
            k-on-input-debounce250 (post-set friendly)}]])

;; -- Cards ----------------------------------------------------------------

(defn device-card [device state]
  (let [friendly (:friendly_name device)
        sid      (sigid friendly)]
    [:article
     {:id           (device-id friendly)
      :class        "card light-card"
      :data-signals (initial-signals sid state)}
     [:header.card-header
      [:div.card-title friendly]
      [:div.card-meta (str (:vendor device) " · " (:model device))]]

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
           (cond-> [(power-control friendly sid)]
             (has-feature? device "brightness") (conj (range-control friendly sid "Brightness" "brightness" 0 254))
             (has-feature? device "color_temp") (conj (range-control friendly sid "Color temp" "colorTemp" 153 500))
             (has-feature? device "color")      (conj (color-control friendly sid))))]))

(defn devices-grid [snapshot]
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
   [:div {:data-init "@get('/events')"}
    [::layout/section {:title "Lights"}
     (devices-grid snapshot)]]])
