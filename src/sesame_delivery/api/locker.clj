(ns sesame-delivery.api.locker
  (:require 
    [sesame-delivery.api.utils :refer :all]
    [datomic.api :as d]
    [compojure.core :refer [routes GET POST]]
    [java-time.api :as jt]

    [sesame-delivery.api.db :refer [db-url]]))

(defn insert-locker [fields]
  (let
    [[canonical-id name lat long] fields
     locker-id (d/tempid :db.part/delivery)
     location-id (d/tempid :db.part/delivery)
     compartment-ids (map (fn [_] (d/tempid :db.part/delivery)) (range 16))
     standard-sizes "mmmsssssssssmmll"]
    (-> db-url
      (d/connect)
      (d/transact
        (concat 
          (map-indexed
            (fn [i compartment-id]
              {
              	:db/id compartment-id
              	:compartment/canonical-id (str "C" canonical-id (when (<= (inc i) 9) "0") (inc i))
              	:compartment/locker locker-id
              	:compartment/size (keyword (str "compartment.size/size-" (get standard-sizes i)))
              	:compartment/state :compartment.state/vacant
              	}) compartment-ids)
          [{
           	:db/id location-id
           	:location/lat lat
           	:location/long long}
          	{
          		:db/id locker-id
          		:locker/name name
          		:locker/canonical-id canonical-id
          		:locker/location location-id
          		:locker/compartments compartment-ids
          		:locker/open-from "09:00"
          		:locker/open-until "18:00"
          		}])))))

(defn get-compartments []
  (map first
    (d/q
      '[:find
        (pull
          ?e [:db/id
              :compartment/canonical-id
              :compartment/locker
              {:compartment/state [:db/ident]}
              {:compartment/size [:db/ident]}])
        :where
        [?e :compartment/canonical-id]
        [?e :compartment/state :compartment.state/vacant]
        ]
      (d/db (d/connect db-url)))))


(defn get-lockers []
  (map
    first
    (d/q
      '[:find
        (pull
          ?e [
              :db/id
              :locker/canonical-id
              :locker/name
              :locker/open-from
              :locker/open-until
              {:locker/location
              	[:location/lat :location/long]}
             	{:locker/compartments
             		[:compartment/canonical-id
              		{:compartment/state [:db/ident]}
              		{:compartment/size [:db/ident]}]}])
        :where
        [?e :locker/canonical-id]]
      (d/db (d/connect db-url)))))


(defn get-lockers-pending-orders [date]
  (let
    [start (jt/java-date (jt/truncate-to date :days))
     end (jt/java-date (jt/truncate-to (jt/plus date (jt/days 1)) :days))]
    (distinct
      (map :parcel/locker
        (map first
          (d/q
            '[:find
              (pull ?e [:db/id {:parcel/locker
                               	[:locker/canonical-id :locker/open-from :locker/open-until]} ])
              :in $ ?start ?end
              :where [?e :parcel/state :parcel.state/labeled ]
              [?e :parcel/deliver-by ?deliver-by]
              [(>= ?deliver-by ?start)]
              [(< ?deliver-by ?end)]]
            (d/db (d/connect db-url)) start end))))))


(defn list-lockers [_request]
  (success (format-query-output (get-lockers))))


(defn controller []
  (routes
    (GET "/" [] list-lockers)
    (GET "/pending" [] "pending")))
