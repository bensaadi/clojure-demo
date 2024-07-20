(ns sesame-delivery.ui.components.nav
  (:require
    [reitit.frontend :as reitit]
    [sesame-delivery.ui.router :refer [router]]
    [reagent.session :as session]
    [fontawesome.icons :as icons]))

(def icons {
  :lockers (icons/render (icons/icon :fontawesome.solid/table-cells))
  :plans (icons/render (icons/icon :fontawesome.solid/truck-fast)) 
  :parcels (icons/render (icons/icon :fontawesome.solid/boxes-stacked)) 
  :map (icons/render (icons/icon :fontawesome.solid/map)) })

(defn path-for [route]
  (:path (reitit/match-by-name router route)))

(defn nav-link
  [route label]
  (let [current-route (:current-route (session/get :route))]
    [:li
      [:a {
        :class (if (= current-route route) "current")
        :href (path-for route)} (route icons) label ]]))

(defn nav
  "Dashboard navigation"  
  [props & children]
    [:header
     [:div {:class "nav-wrapper"}
      [:nav
       [:a.brand {:href "/"} [:img {:src "/public/img/brand.svg"}]]
       [:ul {:class "nav-ul"}
        [nav-link :lockers "Lockers"]
        [nav-link :plans "Route plans"]
        [nav-link :parcels "Parcels"]
        [nav-link :map "Map"]]]]])


