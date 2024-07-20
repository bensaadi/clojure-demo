(ns sesame-delivery.api.algorithm
  (:require 
    [sesame-delivery.api.utils :refer :all]
    [java-time.api :as jt]

    [sesame-delivery.api.testdata :refer :all]
    [sesame-delivery.api.geo :refer [make-time-matrix]]
    [sesame-delivery.api.locker :refer [get-lockers-pending-orders]]
    [sesame-delivery.api.depot :refer [get-depot-with-time-window]]))

(import [org.sesame.delivery TripPlan])


; duration of each stop in minutes
;; TODO: adjust in termsresource of # of parcels & returns
(def stop-duration 10)

(defn into-2d-array
  [matrix]
  (into-array
    (map (fn [array] (long-array array)) matrix)))

(defn solve-optimized-plan
  [{
    date :date
    parcels-delivery-date :parcels-delivery-date
    depot-canonical-id :depot-canonical-id
    vehicles-count :vehicles-count
    }]
  (->> 
    (get-lockers-pending-orders parcels-delivery-date)
    (take max-algorithm-stops)
    (cons (get-depot-with-time-window depot-canonical-id))
    (map vals)
    (map #(split-at 1 %))
    (apply mapv vector)
    ((fn [d]
       (let [canonical-ids (into [] (map first (first d)))
             time-matrix (make-time-matrix canonical-ids)
             time-windows (map #(map time->minutes %) (second d))]

         (println canonical-ids time-matrix time-windows)
         ; no packages to deliver 
         (if (= 1 (count canonical-ids))
           [[] []]
           (do
             (println "About to call algorithm with arguments" time-matrix time-windows)
             (->>
               (TripPlan/makePlan
                 (into-2d-array time-matrix)
                 (into-2d-array time-windows)
                 (int 0)
                 (int vehicles-count)
                 )
               (map-indexed
                 (fn [itinerary-index itinerary]
                   (println "Algorithm returned" itinerary)
                   (map-indexed
                     (fn [stop-index [optimal-locker-index start end]]
                       [resource
                        (get canonical-ids optimal-locker-index)
                        [(jt/plus date (jt/minutes (+ (* stop-duration stop-index) start) ))
                         (jt/plus date (jt/minutes (+ (* stop-duration (inc stop-index)) end)))]]

                       ) (filter 
                           #(not (= (first %) -1))
                           itinerary)))
                 )
               (map #(apply mapv vector %))
               (map #(vector (butlast (rest (first %))) (second %)))
               (apply mapv vector)))))))))

 