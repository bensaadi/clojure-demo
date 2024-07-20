(ns sesame-delivery.ui.utils
	(:require 
    [reagent.format :refer [date-format]]
    [clojure.string :as s]))

(defn format-keyword [k]
  (s/capitalize (s/replace (if (keyword? k) (name k) k) "-" " ")))

(defn format-time [date]
	(date-format (js/Date. date) "HH:mm"))

(defn format-day 
	([date] (if (= (format-day (js/Date) _) (format-day date _)) "Today" (format-day date _) ))
	([date _] (date-format (js/Date. date) "EEE, MMM d")))