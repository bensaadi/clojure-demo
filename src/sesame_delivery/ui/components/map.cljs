(ns sesame-delivery.ui.components.map
	(:require
    [reagent.core :as reagent]
    [re-frame.core :as rf]
    [thi.ng.geom.viz.core :as viz]
    [thi.ng.geom.circle :as c]
    [thi.ng.geom.svg.adapter :as adapt]
    [thi.ng.geom.svg.core :as svg]
    [sesame-delivery.ui.utils :refer [map-values mercator]]))

(rf/reg-sub :map-data (fn [db] (:map-data db)))

; map boundaries
(def map-bbox [2.9060 3.2683 36.8370 36.6510])

; image boundaries
(def map-width 1000)
(def map-height 600)

(defn projection [[lat long]]
  (mercator [long lat] map-bbox map-width map-height))

(defn stop-pin [[x y] label]
  (svg/group {:class "locker"}
    (svg/text [(+ x 0) (+ y 14)] label {:text-anchor "middle" :font-size 10})  
    (with-meta (c/circle x y 4) {:fill "#333" :class label :key label})))

(defn gen-assets-map
  [{lockers :lockers
    depots :depots
    polylines :polylines
    admin :admin
    }]
  (adapt/all-as-svg (svg/svg
    {:width map-width :height map-height :font-family "Arial" :font-size 12 :class "assets-map"}
    ; admin boundaries
    (list (map #(svg/polygon % {:stroke "#BBB" :fill "#FFF"}) admin))
    ; locker pins
    (map (fn [[location label]] (stop-pin location label)) lockers))))

(defn map-view []
  (let [fetch-map #(rf/dispatch [:fetch "/api/map/data/assets" :map-data])
        map-data (rf/subscribe [:map-data])]
    (reagent/create-class
      {:display-name "map"

       :component-did-mount fetch-map

       :reagent-render
       (fn []
         [:div
          [:h1 "Map"]
          [gen-assets-map @map-data]
          ])})))