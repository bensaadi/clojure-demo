(ns sesame-delivery.ui.components.lockers
  (:require
    [clojure.string :as s]
    [reagent.core :as reagent]
    [re-frame.core :as rf]
    [sesame-delivery.ui.utils :refer [format-keyword]]
    [sesame-delivery.ui.components.locker-picto :refer [locker-picto]]))

(def compartment-states ["vacant" "pending-order-delivery"
                         "pending-order-pickup" "pending-return-dropoff"
                         "pending-return-pickup"])

(defn count-compartment-states [lockers state]
  (->> lockers
    (map #(:compartments %))
    flatten
    (map #(:state %))
    (filter #(= state %))
    count))

(defn locker-color-key [lockers]
  [:div {:class "locker-color-key well"}
   [:ul {}
    (map-indexed (fn [i state]
                   [:li {:key i}
                    [:span {:class (str "color " state )}]
                    [:span {:class "label"} (format-keyword state)]
                    [:span {:class "count"} (str " (" (count-compartment-states lockers state) ")")]])
      compartment-states)]])

(defn locker-item [data]
  (let [{
         canonical-id :canonical-id 
         name :name
         compartments :compartments } data]
    [:div {:class "locker-picto" :key canonical-id}
     [:span {:class "status-orb online"}]
     [:h4 canonical-id]
     [:h5 name]
     (locker-picto compartments)]))


(rf/reg-sub :lockers (fn [db] (:lockers db)))

(defn lockers []
  (let [fetch-lockers #(rf/dispatch [:fetch "/api/lockers" :lockers])
        lockers (rf/subscribe [:lockers])]
    (reagent/create-class
      {:display-name "lockers"

       :component-did-mount fetch-lockers

       :reagent-render
       (fn []
         [:div
          [:h1 "Lockers"]
          [locker-color-key @lockers]
          [:div {:class "lockers-grid"}
           (map locker-item @lockers)]])})))