(ns sesame-delivery.api.map
  (:require 
    [sesame-delivery.api.utils :refer :all]
    [clojure.pprint :refer [pprint]]
    [ring.util.response :as r]
    [datomic.api :as d]
    [compojure.core :refer [routes GET POST]]
    [clojure.java.io :as io]
    [clojure.core.matrix :as matrix]

    [geocoordinates.core :as geo]
    [geo [geohash :as geohash] [jts :as jts] [spatial :as spatial] [io :as gio]]
    [com.michaelgaare.clojure-polyline :as polyline]

    [thi.ng.geom.circle :as c]
    [thi.ng.geom.core :as g]
    [thi.ng.geom.matrix :refer [M32]]
    [thi.ng.color.core :as col]
    [thi.ng.geom.line :as l]
    [thi.ng.geom.svg.core :as svg]
    [thi.ng.geom.svg.adapter :as adapt]

    [sesame-delivery.api.locker :refer [get-lockers]]
    [sesame-delivery.api.db :refer [db-url]]))


; map boundaries
(def min-lat 36.8366)
(def max-lat 36.6513)
(def min-long 2.9060)
(def max-long 3.2683)

; image boundaries
(def min-x 0)
(def max-x 1000)
(def min-y 0)
(def max-y (* max-x (/ (- min-lat max-lat) (- max-long min-long))))

(def route-colors ["blue" "red"])
(def geodata (gio/read-geojson (slurp "resources/geodata/algiers.geojson")))


(defn projection [[lat long]]
  ((plate-carree [min-lat min-long] [max-lat max-long] [min-x min-y] [max-x max-y])
   [lat long]))

(defn stop-pin [[x y] label]
  (list
    (svg/text [(+ x 0) (+ y 14)] label {:text-anchor "middle" :font-size 10})  
    (with-meta (c/circle x y 4) {:fill "#333" :class label})))

(def admin 
  (geodata->points geodata projection))

(defn get-plan-data [plan-canonical-id]
  (first
    (first
      (d/q
        '[:find
          (pull ?e [:db/id
                    {:plan/itineraries
                     [:db/id
                      {:itinerary/start-depot
                       [:depot/canonical-id
                        {:depot/location [:location/lat :location/long]}]}
                      {:itinerary/end-depot
                       [:depot/canonical-id
                        {:depot/location [:location/lat :location/long]}]}
                      {:itinerary/stops
                       [:stop/arrive-by
                        :stop/depart-by
                        {:stop/locker
                         [:locker/canonical-id
                          {:locker/location [:location/lat :location/long]}
                          ]}]}
                      ]}])
          :in $ ?plan-canonical-id
          :where [?e :plan/canonical-id ?plan-canonical-id ]]
        (d/db (d/connect db-url)) plan-canonical-id))))

(defn get-plan-data [plan-canonical-id]
  (first
    (first
      (d/q
        '[:find
          (pull ?e [:db/id
                    {:plan/itineraries
                     [:db/id
                      {:itinerary/start-depot
                       [:depot/canonical-id
                        {:depot/location [:location/lat :location/long]}]}
                      {:itinerary/end-depot
                       [:depot/canonical-id
                        {:depot/location [:location/lat :location/long]}]}
                      {:itinerary/stops
                       [:stop/arrive-by
                        :stop/depart-by
                        {:stop/locker
                         [:locker/canonical-id
                          {:locker/location [:location/lat :location/long]}
                          ]}]}
                      ]}])
          :in $ ?plan-canonical-id
          :where [?e :plan/canonical-id ?plan-canonical-id ]]
        (d/db (d/connect db-url)) plan-canonical-id))))

(defn transform-plan-data [raw-data]
  (let [data
        (->> raw-data
          (:plan/itineraries )
          (map (fn [itinerary]
                 (->>
                   itinerary
                   :itinerary/stops

                   (map (fn [stop]
                          [(-> stop :stop/locker :locker/canonical-id)
                           (-> stop :stop/locker :locker/location :location/lat)
                           (-> stop :stop/locker :locker/location :location/long)
                           ]))
                   (map #(split-at 1 %))
                   (apply mapv vector)
                   ))))]
    {
     :routes (map #(map projection (second %)) data)
     :stops (map projection (apply concat (map second data)))
     :stop-labels (map first (apply concat (map first data)))
     :route-labels (into [] (map #(into [] (range 1 (count %))) (map second data)))
     :canonical-ids (map first data) ; used to retrieve polylines
     }))

(defn stops->route [stops]
  (->> stops
    (first)
    (list)
    (concat (apply concat (map #(repeat 2 %) stops))  )
    (drop 1)
    (flatten)
    (partition 2)
    ))

(defn get-path-midpoint [points]
  (get (into [] points) (quot (count points) 2)))

(defn route->polyline [[from to]]
  (:distance/polyline
    (first
      (first
        (d/q
          '[:find
            (pull ?e [:db/id :distance/polyline])
            :in $ ?from-canonical-id ?to-canonical-id
            :where [?e :distance/from-canonical-id ?from-canonical-id]
            [?e :distance/to-canonical-id ?to-canonical-id]
            [?e :distance/rush-hour? false]
            [?e :distance/day-of-week 0]]
          (d/db (d/connect db-url)) from to)))))


(defn gen-plan-map-crow [{
                          stops :stops
                          stop-labels :stop-labels
                          routes :routes
                          route-labels :route-labels
                          canonical-ids :canonical-ids }]
  (svg/svg
    {:width max-x :height max-y :font-family "Arial" :font-size 18}

    ; admin boundaries
    (map #(svg/path % {:stroke "#BBB" :fill "#FFF"})
      (map points->path admin))

    ; stop pins
    (list (mapcat stop-pin stops stop-labels))

    (let [polylines
          (->> canonical-ids
            (map stops->route)
            (map #(map route->polyline %))
            (map #(map (fn [s] (try (polyline/decode s) (catch Exception e "" ) ) ) %))
            (map-indexed
              (fn [i itinerary]
                (->> itinerary
                  (map #(map projection %) )))))]

      ; draw polyline paths

      (list
        (map-indexed
          (fn [i itinerary]
            (map
              (fn [points]
                (list (svg/path (points->path points)
                        {:stroke-width 2
                         :stroke-dasharray "8 4"
                         :stroke-dashoffset (* 8 i)
                         :stroke (get route-colors i) :fill "transparent" :class (str "route-" i) }))
                ) itinerary)) polylines)

        ; draw polyline labels

        (map-indexed
          (fn [i itinerary]
            (map-indexed
              (fn [j points]
                (let [midpoint (get-path-midpoint points)]
                  (svg/group {}
                    (with-meta (c/circle (first midpoint) (second midpoint) 12) {:fill (get route-colors i)})
                    (svg/text [(first midpoint) (+ 4 (second midpoint))] (inc j) {
                                                                                  :fill "#FFF" :font-weight "
         bold" :text-anchor "middle"})
                    )
                  ))  itinerary)) polylines)))))

(defn get-assets-data []
  {:lockers
   (->>
     (get-lockers)
     (map #(vector
             (projection [(get-in % [:locker/location :location/lat] )
                          (get-in % [:locker/location :location/long])])
             (:locker/canonical-id %))))
   :admin (map points->path admin)})


(defn gen-assets-map
  [{lockers :lockers
    admin :admin}]
  (svg/svg
    {:width max-x :height max-y :font-family "Arial" :font-size 12}

    ; admin boundaries
    (map #(svg/path % {:stroke "#BBB" :fill "#FFF"}) admin)

    (map (fn [[location label]] (stop-pin location label)) lockers)))

(defn serve-svg [image]
  {:body image 
   :headers { "content-type" "image/svg+xml"}})

(defn plan-preview [plan-canonical-id]
  (->>
    (get-plan-data plan-canonical-id)
    (transform-plan-data)
    (gen-plan-map-crow)
    (adapt/all-as-svg)
    (svg/serialize)
    (serve-svg)))

(defn assets-map []
  (->>
    (get-assets-data)
    (gen-assets-map)
    (adapt/all-as-svg)
    (svg/serialize)
    (serve-svg)))

(defn controller []
  (routes
    (GET "/assets" [] (assets-map))
    (GET "/data/assets" [] (success (format-query-output (get-assets-data))))
    (GET "/plan-preview/:plan-id" [plan-id] (plan-preview plan-id))))


