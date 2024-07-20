(ns ^:figwheel-hooks sesame-delivery.core
  (:require
    [react :as react]
    [goog.dom :as gdom]
    [reagent.core :as reagent :refer [atom]]
    [reagent.dom :as rdom]
    [reagent.session :as session]
    [re-frame.core :as rf]
    [reitit.frontend :as reitit]
    [accountant.core :as accountant]
    [day8.re-frame.http-fx] 
    [ajax.core :as ajax]

    [sesame-delivery.ui.db :refer [default-db]]
    [sesame-delivery.ui.events]
    [sesame-delivery.ui.components.layout :refer [layout]]
    [sesame-delivery.ui.router :refer [router]]
    [sesame-delivery.ui.components.index :refer [index]]
    [sesame-delivery.ui.components.parcels :refer [parcels]]
    [sesame-delivery.ui.components.map :refer [map-view]]
    [sesame-delivery.ui.components.plans :refer [plans]]
    [sesame-delivery.ui.components.lockers :refer [lockers]]))


(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

(defn page-for [route]
  (case route
    :index #'index
    :lockers #'lockers
    :parcels #'parcels
    :map #'map-view
    :plans #'plans))

(defn app []
  (fn []
    (let [page (page-for (:current-route (session/get :route)))]
      [layout {} [page {:key "page"}]])))

(rf/reg-event-db
 :initialize
 (fn [_ _] default-db))

(rf/dispatch-sync [:initialize]) 

(defn mount-app-element []
  (when-let [el (gdom/getElement "app")]
    (rdom/render [app] el)))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (let [match (reitit/match-by-path router path)
             current-route (:name (:data  match))
             route-params (:path-params match)]
         (session/put! :route {:current-route current-route
                               :route-params route-params})))
     :path-exists?
     (fn [path]
       (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-app-element))

(init!)

(defn ^:after-load on-reload []
  (mount-app-element)
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
