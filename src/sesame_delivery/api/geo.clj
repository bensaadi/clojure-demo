(ns sesame-delivery.api.geo
  (:require 
    [sesame-delivery.api.utils :refer :all]
    [datomic.api :as d]

    [sesame-delivery.api.db :refer [db-url]]))

(defn insert-distances [distances]
  (let [distance-ids (map (fn [i] (d/tempid :db.part/delivery)) (range (count distances)))]
    (-> db-url
     	(d/connect)
     	(d/transact
        (map-indexed
         	(fn [i [from-canonical-id to-canonical-id meters minutes polyline]]
           	{:db/id (nth distance-ids i)
           		:distance/from-canonical-id from-canonical-id
           		:distance/to-canonical-id to-canonical-id
           		:distance/meters meters
           		:distance/minutes minutes
           		:distance/polyline polyline
           		:distance/day-of-week 0
           		:distance/rush-hour? false
           		}) distances)))))

(defn get-distances-from [from-canonical-id to-canonical-ids]
  (->> from-canonical-id
    (d/q
      '[:find
        (pull
          ?e [:db/id
              :distance/from-canonical-id
              :distance/to-canonical-id
              :distance/minutes])
        :in $ ?from-canonical-id
        :where [?e :distance/from-canonical-id ?from-canonical-id]]
      (d/db (d/connect db-url)) from-canonical-id)
    (map first)
    (filter #(contains? (set to-canonical-ids) (:distance/to-canonical-id  %)))
    ))

(defn make-time-matrix [canonical-ids]
  (->>
    canonical-ids
    (map #(get-distances-from % canonical-ids))
    complete-diagonal
    (map #(map :distance/minutes %))
    (map #(map (fn [n] (or n 0)) %))
    (map #(into [] %))
    (into []) 
    ))