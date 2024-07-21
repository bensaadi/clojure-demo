(ns sesame-delivery.api.utils
  (:require 
    [clojure.string :as s]
    [clojure.walk :refer [postwalk]]
    [datomic.api :as d]
    [geocoordinates.core :as geo]
    [java-time.api :as jt]
    [thi.ng.math.core :as m]))


; constants

(def unambiguous-chars "23456789ABCEFGHJKLMNPRSTUVWXYZ")
(def max-algorithm-stops 10)

; JSend specs

(defn success [data]
  {:body {:status "success" :data data}})

(defn fail [message]
  {:status 400 :body {:status "fail" :message message}})

(defn error [message]
  {:status 500 :body {:status "error" :message message}})


; formatting

(defn strip-namespaces [d]
  (postwalk #(if (keyword? %) (keyword (name %)) %) d))

(defn format-query-output [output]
  (->>
    output
    (postwalk (fn [item] (get item :db/ident item))) ; simplify enums
    strip-namespaces)) ; strip namespaces


;hh:mm to the number of minutes since 00:00 
(defn time->minutes [string]
  (->> string
    (partition 2 3)
    (map s/join)
    (map #(Integer/parseInt %) )
    ((fn [[h m]]
       (+ (* 60 h) m)))))

; parse yyyy-mm-dd dates
(defn parse-date [date]
  (apply jt/zoned-date-time
    (map #(Integer/parseInt %) (s/split date #"-"))))

; format instant into a yyyy-mm-dd string
(defn format-date [date]
  (jt/format (jt/format "yyyy-MM-dd") (.atZone date (jt/zone-id))))


; list processing

(defn map-values
  [f m]
  (zipmap (keys m) (map f (vals m))))

(defn nested-group-by
  [fs coll]
  (if (empty? fs)
    coll
    (map-values
      #(nested-group-by (rest fs) %)
      (group-by (first fs) coll)
      )))

(defn sort-by-list [v sort-list]
  (map #(get v %) sort-list)
  )


; matrix operations

(defn with-zero-at
  "returns a new coll with an appended zero at pos"
  [pos coll]
  (concat (take pos coll) [0] (take-last (- (count coll) pos) coll)))

(defn complete-diagonal
  "returns a square matrix from a matrix that's missing the diagonal"
  [matrix]
  (map-indexed (fn [i row] (with-zero-at i row)) matrix))


; geo math

(defn polar->cartersian [[lat long]]
  (vals
    (geo/latitude-longitude->easting-northing
      {:latitude lat :longitude long} :national-grid)))

(defn points->path [points]
  (-> [:M (first points)]
    (cons (map #(cons :L [%]) (rest points)))
    (cons [[:z]])
    (first)))

(defn plate-carree
  [[min-lat min-long] [max-lat max-long] [min-x min-y] [max-x max-y]]
  (let [
        [min-east min-north] (polar->cartersian [min-lat min-long])
        [max-east max-north] (polar->cartersian [max-lat max-long])
        xscale (/ (- max-east min-east) (- max-x min-x))
        yscale (/ (- max-north min-north) (- max-y min-y))]
    (fn [[lat long]] 
      (let [[east north] (polar->cartersian [lat long])]
        [(/ (- east min-east) xscale) (/ (- north min-north) yscale)]))))

(defn lat-log
  [lat] (Math/log (Math/tan (+ (/ (m/radians lat) 2) m/QUARTER_PI))))

(defn mercator
  [[lon lat] [left right top bottom] w h]
  (let [lon              (m/radians lon)
        left             (m/radians left)
        [lat top bottom] (map lat-log [lat top bottom])]
    [
     (* w (/ (- lon left) (- (m/radians right) left)))
     (* h (/ (- lat top) (- bottom top)))]))

(defn geodata->points [geodata projection]
  (map
    (fn [v]
      (->> v
        :geometry
        .getCoordinates
        (map (fn [coords] [(.getY coords) (.getX coords)]))
        (map projection)
        )) geodata))

; db-related


(def ^{:dynamic true :doc "A Datomic database value used over the life of a Ring request."} *db*)
(def ^{:dynamic true :doc "A Datomic connection bound for the life of a Ring request."} *connection*)

(defn wrap-datomic
  "A Ring middleware that provides a request-consistent database connection and
value for the life of a request."
  [handler uri]
  (fn [request]
    (let [conn (d/connect uri)]
      (binding [*connection* conn
                *db*         (d/db conn)]
        (handler request)))))

(defn transact
  "Run a transaction with a request-consistent connection."
  [tx]
  (d/transact *connection* tx))

(defn q
  "Runs the given query over a request-consistent database as well as
the other given sources."
  [query & sources]
  (apply d/q query *db* sources))

(defn canonical-id->entity [canonical-id]
  (let [dict {"L" "locker"
              "D" "depot"
              "V" "vehicle"
              "B" "parcel"
              "P" "plan"
              "R" "return"
              "C" "compartment"
              "X" "location"}
        k (get dict (str (first canonical-id)))
        k-canonical-id (keyword (str k "/canonical-id"))]
    (->
      (q '[:in $ ?k-canonical-id ?canonical-id
           :find (pull ?e [*])
           :where [?e ?k-canonical-id ?canonical-id ]]
        k-canonical-id canonical-id)
      first
      first)))



