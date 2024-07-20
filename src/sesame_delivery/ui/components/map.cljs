(ns sesame-delivery.ui.components.map
	(:require
    [reagent.core :as reagent]
    [re-frame.core :as rf]

    [thi.ng.geom.circle :as c]
    [thi.ng.geom.svg.adapter :as adapt]
    [thi.ng.geom.svg.core :as svg]
    ))

(rf/reg-sub :map-data (fn [db] (:map-data db)))


(defn stop-pin [[x y] label]
  (svg/group {:class "locker"}
    (svg/text [(+ x 0) (+ y 14)] label {:text-anchor "middle" :font-size 10})  
    (with-meta (c/circle x y 4) {:fill "#333" :class label})))

(defn gen-assets-map
  [{lockers :lockers
    admin :admin}]
  (adapt/all-as-svg (svg/svg
    {:width 1000 :height 512 :font-family "Arial" :font-size 12 :class "assets-map"}

    ; (map js/console.log (first admin) )

    ; admin boundaries
    (map #(svg/path % {:stroke "#BBB" :fill "#FFF"}) admin)

    (map (fn [[location label]] (stop-pin location label)) lockers)

    )))

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
          [:p "TODO: render an interactive map from tile server"]
          [gen-assets-map @map-data]
          ])})))