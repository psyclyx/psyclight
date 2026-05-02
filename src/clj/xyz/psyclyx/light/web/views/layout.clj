(ns xyz.psyclyx.light.web.views.layout
  "Page shell + chassis aliases for the global structural elements.
   Service-specific views compose `[::layout/page {:title \"...\"} ...]`."
  (:require [dev.onionpancakes.chassis.core :as h]))

(defmethod h/resolve-alias ::page
  [_ {:keys [title]} content]
  [h/doctype-html5
   [:html {:lang "en"}
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
     [:title (str title " — psyclight")]
     [:link {:rel "stylesheet" :href "/style.css"}]]
    [:body
     [:header.topbar
      [:a.brand {:href "/"} "psyclight"]
      [:nav
       [:a {:href "/"}        "Lights"]
       [:a {:href "/pairing"} "Pair"]
       [:a {:href "/adapter"} "Adapter"]]]
     (into [:main] content)
     [:script {:type "module" :src "/js/datastar.js"}]]]])

(defmethod h/resolve-alias ::section
  [_ {:keys [title id]} content]
  [:section (cond-> {:class "section"}
              id (assoc :id id))
   (when title [:h2 title])
   (into [:div.section-body] content)])
