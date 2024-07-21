(ns sesame-delivery.ui.utils
	(:require 
    [reagent.format :refer [date-format]]
    [clojure.string :as s]
    [thi.ng.math.core :as m]))


; formatting

(defn format-keyword [k]
  (s/capitalize (s/replace (if (keyword? k) (name k) k) "-" " ")))

(defn format-time [date]
	(date-format (js/Date. date) "HH:mm"))

(defn format-display-date [date]
	(date-format (js/Date. (s/replace (name date) "-" "/")) "EEE, MMM d"))

(defn format-ymd-date [date]
	(date-format (js/Date. (s/replace (name date) "-" "/")) "yyyy-MM-dd"))


; list processing

(defn map-values
  [f m]
  (zipmap (keys m) (map f (vals m))))

; geo

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

