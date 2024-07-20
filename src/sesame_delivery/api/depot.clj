(ns sesame-delivery.api.depot
  (:require 
    [sesame-delivery.api.utils :refer :all]
    [datomic.api :as d]
    [compojure.core :refer [routes GET POST]]
    [sesame-delivery.api.vehicle :refer [gen-vehicle-canonical-id]]
    [sesame-delivery.api.db :refer [db-url]]))

(defn insert-depot [fields]
  (let [[canonical-id name lat long] fields
        depot-id (d/tempid :db.part/delivery)
        location-id (d/tempid :db.part/delivery)
        vehicle-ids (map (fn [_] (d/tempid :db.part/delivery)) (range 2))]
    (-> db-url
      (d/connect)
      (d/transact
        (concat 
          (map
            (fn [vehicle-id]
              {
              	:db/id vehicle-id
              	:vehicle/name "Renault Master L3H2"
              	:vehicle/canonical-id (gen-vehicle-canonical-id)
              	:vehicle/depot depot-id
              	:vehicle/capacity 540
              	:vehicle/state :vehicle.state/parked-in-depot
              	}) vehicle-ids)
          [{
           	:db/id location-id
           	:location/lat lat
           	:location/long long}
          	{
          		:db/id depot-id
          		:depot/name name
          		:depot/canonical-id canonical-id
          		:depot/location location-id
          		:depot/vehicles vehicle-ids
          		:depot/open-from "07:00"
          		:depot/open-until "22:00"
          		}])))))


(defn get-depots []
  (map first
    (d/q '[:find (pull
                   ?e [:db/id :depot/canonical-id])
           :where [?e :depot/canonical-id]]
      (d/db (d/connect db-url)))))

(defn get-depot-with-time-window [canonical-id]
 	(->> canonical-id
    (d/q
      '[:find
        (pull ?e [:depot/canonical-id :depot/open-from :depot/open-until])
        :in $ ?canonical-id
        :where [?e :depot/canonical-id ?canonical-id]]
      (d/db (d/connect db-url)))
    (first)
    (first)
    (into [])))

(defn list-depots [_request]
  (success (format-query-output (get-depots))))

(defn controller []
  (routes
    (GET "/" [] list-depots)))
