(ns xyz.psyclyx.light.web.views.pairing
  "Pairing page. Two distinct flows side-by-side:

   - permit_join — open the network for `n` seconds and watch joined
     devices roll in. The events list updates live via SSE.

   - touchlink   — scan with the dongle physically held next to the
     target device, then identify (blink) or factory-reset+rejoin from
     the result list."
  (:require [xyz.psyclyx.light.web.views.layout :as layout]))

;; -- Permit-join section --------------------------------------------------

(defn- permit-join-status [state]
  (let [pj (:permit-join state)]
    [:div {:id "permit-join-status" :class "status"}
     (cond
       (nil? pj)              [:span.muted "permit_join not requested yet."]
       (= "ok" (:status pj))  [:span.success "permit_join state updated"]
       :else                  [:span.error (str "permit_join failed: " (:status pj))])]))

(defn- join-event-row [{:keys [type data recorded_at]}]
  [:tr
   [:td recorded_at]
   [:td type]
   [:td (or (:friendly_name data) (:ieee_address data))]
   [:td (or (:vendor data) "")]
   [:td (or (:model data) "")]])

(defn- join-events-table [state]
  [:table {:id "join-events" :class "events"}
   [:thead [:tr [:th "When"] [:th "Event"] [:th "Device"] [:th "Vendor"] [:th "Model"]]]
   (into [:tbody]
         (if (seq (:events state))
           (map join-event-row (:events state))
           [[:tr [:td {:colspan 5 :class "empty-state"} "No join activity yet."]]]))])

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

;; -- Touchlink section ----------------------------------------------------

(defn- touchlink-status [state]
  (let [tl (:touchlink state)]
    [:div {:id "touchlink-status" :class "status"}
     (cond
       (nil? tl)
       [:span.muted "Hold the dongle close to the target device, then scan."]

       (= "ok" (:status tl))
       [:span.success (str "Scanned at " (:scanned_at tl) " — "
                           (count (:found tl)) " device(s) found.")]

       :else
       [:span.error (str "Scan failed: " (:status tl))])]))

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

(defn- touchlink-results [state]
  [:table {:id "touchlink-results" :class "events"}
   [:thead [:tr [:th "IEEE address"] [:th "Channel"] [:th "Actions"]]]
   (into [:tbody]
         (let [found (get-in state [:touchlink :found])]
           (if (seq found)
             (map touchlink-row found)
             [[:tr [:td {:colspan 3 :class "empty-state"} "No touchlink results yet."]]])))])

(defn touchlink-section [state]
  [::layout/section {:title "Touchlink"}
   [:p "Aggressive pairing: physically hold the coordinator close to "
       "the target device. The device does not need to be in pairing mode."]
   [:button {:data-on:click "@post('/pairing/touchlink/scan')"} "Scan"]
   (touchlink-status state)
   (touchlink-results state)])

;; -- Page -----------------------------------------------------------------

(defn page [state]
  [::layout/page {:title "Pair"}
   (permit-join-section state)
   (touchlink-section state)
   [:div {:data-on:load "@get('/pairing/events')"}]])
