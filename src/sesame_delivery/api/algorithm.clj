(ns sesame-delivery.api.algorithm
  (:require 
    [java-time.api :as jt]
    [sesame-delivery.api.utils :refer :all]
    [sesame-delivery.api.testdata :refer :all]
    [sesame-delivery.api.geo :refer [make-time-matrix]]
    [sesame-delivery.api.locker :refer [get-lockers-pending-orders]]
    [sesame-delivery.api.depot :refer [get-depot-with-time-window]]))

(import [org.sesame.delivery TripPlan])

; duration of each stop in minutes
;; TODO: turn into a fn of # of parcels & returns
(def stop-duration 10)

; index of depot in time matrix
(def depot-index 0)

(defn make-algorithm-input
  [{parcels-delivery-date :parcels-delivery-date
    depot-canonical-id :depot-canonical-id}]
  (->> 
    (get-lockers-pending-orders parcels-delivery-date)
    (take max-algorithm-stops)
    (cons (get-depot-with-time-window depot-canonical-id))
    (map vals)
    (map #(split-at 1 %))
    (apply mapv vector)
    ((fn [[canonical-ids time-windows]]
       (let [canonical-ids (vec (map first canonical-ids))]
         {:canonical-ids canonical-ids
          :time-matrix (make-time-matrix canonical-ids)
          :time-windows (map #(map time->minutes %) time-windows)}))) ))

(defn into-java-long-matrix
  [matrix]
  (into-array
    (map (fn [row] (long-array row)) matrix)))

(defn invoke-algorithm
  [time-matrix time-windows depot-index vehicles-count]
  (TripPlan/makePlan
    (into-java-long-matrix time-matrix)
    (into-java-long-matrix time-windows)
    depot-index
    (int vehicles-count)))

(defn format-algorithm-output [date canonical-ids output]
  (->>
    output
    (map
      (fn [itinerary]
        (map-indexed
          (fn [stop-index [optimal-locker-index start end]]
            [(get canonical-ids optimal-locker-index)
             [(jt/plus date
                (jt/minutes (+ (* stop-duration stop-index) start) ))
              (jt/plus date
                (jt/minutes (+ (* stop-duration (inc stop-index)) end)))]])
          (filter 
            #(not (= (first %) -1))
            itinerary))))
    ; transpose
    (map #(apply mapv vector %))
    ; remove start and end stops
    (map #(vector (butlast (rest (first %))) (second %)))
    ; transpose back
    (apply mapv vector)))

(defn solve-optimized-plan
  "Interop with java to generate optimized routes.
Currently taking into account the time windows for depot
and lockers, the time distance between each location,
and the number of vehicles. Will return an itinerary for
each vehicle. -1 indicates no stop."
  [{date :date
    parcels-delivery-date :parcels-delivery-date
    depot-canonical-id :depot-canonical-id
    vehicles-count :vehicles-count
    :as args}]
  (->> args
    make-algorithm-input
    ((fn [{ canonical-ids :canonical-ids
           time-matrix :time-matrix
           time-windows :time-windows}]
       (if (= 1 (count canonical-ids))
         [] ; no packages to deliver
         (->>
           (invoke-algorithm time-matrix time-windows depot-index vehicles-count)
           (format-algorithm-output date canonical-ids)))))))