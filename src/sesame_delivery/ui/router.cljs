(ns sesame-delivery.ui.router
 	(:require
    [reitit.frontend :as reitit]))

(def router
  (reitit/router
    [["/" :lockers]
     ["/parcels" :parcels]
     ["/plans" :plans]
     ["/map" :map]]))