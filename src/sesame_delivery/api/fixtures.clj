(ns sesame-delivery.api.fixtures
  (:require 
    [java-time.api :as jt]
    [clojure.pprint :refer [pprint]]
    [sesame-delivery.api.testdata :refer :all]
    [sesame-delivery.api.depot :refer [get-depots insert-depot]]
    [sesame-delivery.api.parcel :refer [insert-parcel]]
    [sesame-delivery.api.geo :refer [insert-distances]]
    [sesame-delivery.api.return :refer [insert-return]]
    [sesame-delivery.api.plan :refer [make-optimized-plan insert-plan]]
    [sesame-delivery.api.utils :refer :all]
    [sesame-delivery.api.db :refer [db-url]]
    [sesame-delivery.api.locker :refer [insert-locker get-lockers get-compartments]]))

(import java.util.Date)

; add a parcel or return into each vacant compartment
(defn insert-parcels-returns [n]
  (let [depots (get-depots)
        compartments (take n (shuffle (get-compartments)))]
    (map (fn [depot]
           (map-indexed
             (fn [i compartment]
               (let [{compartment-id :db/id
                      locker-id :compartment/locker
                      size :compartment/size} compartment
                     {depot-id :db/id} depot]
                 (if (< i 20)
                   (insert-return
                     [locker-id compartment-id depot-id (:db/ident size)])
                   (insert-parcel
                     [locker-id compartment-id depot-id (:db/ident size)
                      (jt/truncate-to
                        (jt/plus (jt/zoned-date-time) (jt/days (mod i 4))) :days)])
                   ))
               ) compartments)
           ) depots)
    ))


(defn insert-test-data []
  (do
    (println "Inserted " (count (map insert-depot depots)) " depots")
    (println "Inserted " (count (map insert-locker lockers)) " lockers")
    (println "Inserted " (count (first (insert-parcels-returns 480))) " parcels & returns")
				(println "Inserted " (do (insert-distances distances) " distances"))))