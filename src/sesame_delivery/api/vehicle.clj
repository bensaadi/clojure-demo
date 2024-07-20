(ns sesame-delivery.api.vehicle
  (:require 
    [sesame-delivery.api.utils :refer :all]
    [datomic.api :as d]
    [compojure.core :refer [routes GET POST]]
    [sesame-delivery.api.db :refer [db-url]]))

(defn gen-vehicle-canonical-id []
  (->>
    (repeatedly 5 #(rand-nth unambiguous-chars))
    (apply str)
    (str "V")))

(defn get-vehicles []
  (map first (d/q '[:find (pull
                            ?e [:db/id :vehicle/canonical-id])
                    :where [?e :vehicle/canonical-id]]
               (d/db (d/connect db-url)))))

(defn list-vehicles [_request]
  (success (format-query-output (get-vehicles))))

(defn controller []
  (routes
    (GET "/" [] list-vehicles)))
