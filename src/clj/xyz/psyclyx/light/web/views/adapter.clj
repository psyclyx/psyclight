(ns xyz.psyclyx.light.web.views.adapter
  "Adapter selection page. Until the user picks an adapter, z2m doesn't
   start, so this is the entry point on first run.

   The set of supported `adapter` values is z2m's; we offer the common
   ones for a Sonoff stick (`zstack` for ZBDongle-P, `ember` for the
   Silabs ZBDongle-E / Dongle Plus MG24). Note z2m 2.x dropped the old
   `ezsp` driver in favour of `ember` for Silabs coordinators. Users can
   pick the candidate device path from a select populated by our
   serial-port discovery."
  (:require [xyz.psyclyx.light.web.views.layout :as layout]))

(def ^:private adapter-types
  [{:value "zstack" :label "Z-Stack (Sonoff ZBDongle-P, CC2652P/2531/2530)"}
   {:value "ember"  :label "Ember (Sonoff ZBDongle-E / Dongle Plus MG24, EFR32 Silabs)"}
   {:value "deconz" :label "deCONZ (ConBee II/III, RaspBee)"}
   {:value "zigate" :label "ZiGate"}])

(defn- option [{:keys [value label]} selected]
  [:option (cond-> {:value value}
             (= value selected) (assoc :selected "selected"))
   label])

(defn page [{:keys [adapter ports running?]}]
  [::layout/page {:title "Adapter"}
   [::layout/section {:title "Coordinator"}
    [:p "Select the adapter type and serial port for your Zigbee "
        "coordinator. zigbee2mqtt will (re)start when you save."]
    [:p {:class "status"}
     "z2m: " (if running?
               [:span.success "running"]
               [:span.muted "not running"])]
    [:form {:method "post" :action "/adapter" :class "form"}
     [:label "Adapter type"
      [:select {:name "type"}
       (for [a adapter-types] (option a (:type adapter)))]]

     [:label "Serial port"
      [:input {:type "text"
               :name "port"
               :placeholder "/dev/serial/by-id/..."
               :value (or (:port adapter) "")
               :list  "ports"}]
      [:datalist {:id "ports"}
       (for [p ports]
         [:option {:value p}])]]

     [:label "Baud rate (optional)"
      [:input {:type "number" :name "baudrate"
               :placeholder "default for adapter type"
               :value (str (or (:baudrate adapter) ""))}]]

     [:label.checkbox
      [:input {:type "checkbox" :name "disable_led"
               :checked (= 1 (:adapter_disable_led adapter))
               :value "1"}]
      [:span "Disable adapter LED"]]

     [:div.form-actions
      [:button {:type "submit"} "Save and restart z2m"]]]]])
