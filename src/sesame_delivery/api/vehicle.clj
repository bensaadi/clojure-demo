(ns sesame-delivery.api.vehicle
  (:require 
    [compojure.core :refer [routes GET POST]]
    [datomic.api :as d]
    [sesame-delivery.api.utils :refer :all]
    [sesame-delivery.api.db :refer [db-url]]))

(defn gen-vehicle-canonical-id []
  (->>
    (repeatedly 5 #(rand-nth unambiguous-chars))
    (apply str)
    (str "V")))

(defn get-vehicles []
  (map first
    (q '[:find
         (pull
           ?e [:db/id :vehicle/canonical-id])
         :where [?e :vehicle/canonical-id]])))

(defn list-vehicles [_request]
  (success (format-query-output (get-vehicles))))

(defn controller []
  (routes
    (GET "/" [] list-vehicles)))
