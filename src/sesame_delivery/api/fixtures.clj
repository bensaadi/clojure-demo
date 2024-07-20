(ns sesame-delivery.api.fixtures
  (:require 
    [java-time.api :as jt]
    [sesame-delivery.api.testdata :refer [depots distances lockers]]
    [sesame-delivery.api.depot :refer [get-depots insert-depot]]
    [sesame-delivery.api.parcel :refer [insert-parcel]]
    [sesame-delivery.api.geo :refer [insert-distances]]
    [sesame-delivery.api.return :refer [insert-return]]
    [sesame-delivery.api.utils :refer :all]
    [sesame-delivery.api.locker :refer [insert-locker get-compartments]]))

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
  (println "Inserted " (count (map insert-depot depots)) " depots")
  (println "Inserted " (count (map insert-locker lockers)) " lockers")
  (println "Inserted " (count (first (insert-parcels-returns 480))) " parcels & returns")
		(println "Inserted " (do (insert-distances distances) " distances")))
