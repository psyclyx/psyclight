(ns xyz.psyclyx.light.web.views.pairing
  "Pairing page. Two distinct flows side-by-side:

   - permit_join — open the network for `n` seconds and watch joined
     devices roll in. The events list updates live via SSE.

   - touchlink   — scan with the dongle physically held next to the
     target device, then identify (blink) or factory-reset+rejoin from
     the result list. Also exposes \"factory-reset nearest device\"
     for stubborn devices that don't surface in a scan (e.g. Hue
     bulbs outside their ~30s post-power-cycle window).

   Each fragment that changes live (status div, events table) has a
   stable element id so SSE patches can morph it in place."
  (:require [xyz.psyclyx.light.web.views.layout :as layout]))

;; -- Permit-join fragments -----------------------------------------------

(defn permit-join-status
  "Stable id `permit-join-status`. Morphed from SSE."
  [{:keys [permit-join]}]
  [:div {:id "permit-join-status" :class "status"}
   (cond
     (nil? permit-join)
     [:span.muted "permit_join not requested yet."]

     (and (:requested permit-join) (not (:status permit-join)))
     [:span.muted (str "permit_join: requested ("
                       (get-in permit-join [:requested :seconds])
                       "s) — waiting for broker response")]

     (= "ok" (:status permit-join))
     [:span.success "permit_join state updated"]

     :else
     [:span.error (str "permit_join failed: "
                       (or (:error permit-join) (:status permit-join)))])])

(defn- join-event-row [{:keys [type data recorded_at]}]
  [:tr
   [:td recorded_at]
   [:td type]
   [:td (or (:friendly_name data) (:ieee_address data))]
   [:td (or (:vendor data) "")]
   [:td (or (:model data) "")]])

(defn join-events-table
  "Stable id `join-events`. Morphed from SSE."
  [{:keys [events]}]
  [:table {:id "join-events" :class "events"}
   [:thead [:tr [:th "When"] [:th "Event"] [:th "Device"] [:th "Vendor"] [:th "Model"]]]
   (into [:tbody]
         (if (seq events)
           (map join-event-row events)
           [[:tr [:td {:colspan 5 :class "empty-state"} "No join activity yet."]]]))])

;; -- Touchlink fragments -------------------------------------------------

(defn touchlink-status
  "Stable id `touchlink-status`. Morphed from SSE."
  [{:keys [touchlink]}]
  [:div {:id "touchlink-status" :class "status"}
   (cond
     (:scanning touchlink)
     [:span.muted (case (:mode touchlink)
                    :reset-nearest "Resetting nearest device — hold the dongle close…"
                    "Scanning — hold the dongle close to the device…")]

     (nil? touchlink)
     [:span.muted "Hold the dongle close to the target device, then scan."]

     (= "ok" (:status touchlink))
     [:span.success (str "Scanned at " (:scanned_at touchlink) " — "
                         (count (:found touchlink)) " device(s) found.")]

     :else
     [:span.error (str "Scan failed: "
                       (or (:error touchlink) (:status touchlink)))])])

(defn- touchlink-row [{:keys [ieee_address channel]}]
  [:tr
   [:td ieee_address]
   [:td (or channel "")]
   [:td.actions
    [:button {:data-on:click
              (str "@post('/pairing/touchlink/identify',"
                   "{contentType:'json',body:{ieee_address:'" ieee_address
                   "',channel:" (or channel 0) "}})")}
     "Identify"]
    [:button {:class "danger"
              :data-on:click
              (str "@post('/pairing/touchlink/factory-reset',"
                   "{contentType:'json',body:{ieee_address:'" ieee_address
                   "',channel:" (or channel 0) "}})")}
     "Factory reset + rejoin"]]])

(defn touchlink-results
  "Stable id `touchlink-results`. Morphed from SSE."
  [{:keys [touchlink]}]
  [:table {:id "touchlink-results" :class "events"}
   [:thead [:tr [:th "IEEE address"] [:th "Channel"] [:th "Actions"]]]
   (into [:tbody]
         (let [found (:found touchlink)]
           (if (seq found)
             (map touchlink-row found)
             [[:tr [:td {:colspan 3 :class "empty-state"} "No touchlink results yet."]]])))])

;; -- Sections (initial render) -------------------------------------------

(defn permit-join-section [state]
  [::layout/section {:title "Normal pairing (permit_join)"}
   [:p "Opens the network for new devices for the chosen window. The "
       "device must already be in pairing mode (factory reset or fresh)."]
   [:form {:class "inline-form"
           :data-on:submit "@post('/pairing/permit-join',{contentType:'json',body:{seconds:Number(document.getElementById('pj-seconds').value)}}); evt.preventDefault()"}
    [:label "Seconds open "
     [:input {:type "number" :id "pj-seconds" :name "seconds"
              :min "0" :max "600" :value "120"}]]
    [:button {:type "submit"} "Open"]
    [:button {:type "button"
              :data-on:click "@post('/pairing/permit-join',{contentType:'json',body:{seconds:0}})"}
     "Close"]]
   (permit-join-status state)
   (join-events-table state)])

(defn touchlink-section [state]
  [::layout/section {:title "Touchlink"}
   [:p "Aggressive pairing: physically hold the coordinator close to "
       "the target device. The device does not need to be in pairing mode."]
   [:p.muted
    "Hue lights gate touchlink to a brief (~30s) window after a power-"
    "cycle. If a scan finds nothing, flick the wall switch off and on, "
    "then immediately Scan or Reset nearest."]
   [:div.button-row
    [:button {:data-on:click "@post('/pairing/touchlink/scan')"} "Scan"]
    [:button {:class "danger"
              :data-on:click "@post('/pairing/touchlink/reset-nearest')"}
     "Factory-reset nearest device"]]
   (touchlink-status state)
   (touchlink-results state)])

;; -- Live fragments ------------------------------------------------------

(defn live-fragments
  "The set of fragments that change as pairing state evolves. Each has
   a stable element id; SSE patches morph them in place."
  [state]
  [(permit-join-status state)
   (join-events-table state)
   (touchlink-status state)
   (touchlink-results state)])

;; -- Page ----------------------------------------------------------------

(defn page [state]
  [::layout/page {:title "Pair"}
   (permit-join-section state)
   (touchlink-section state)
   [:div {:data-on:load "@get('/pairing/events')"}]])
