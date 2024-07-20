(ns sesame-delivery.ui.components.plans
  (:require
    [sesame-delivery.ui.utils :refer [format-keyword format-time]]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [goog.string :as gstr]
    [fontawesome.icons :as icons]))

(rf/reg-sub :plans (fn [db] (:plans db)))

(defn timesheet [itinerary]
  (r/with-let [show? (r/atom false)]
    [:div {:class "timesheet"}
     [:button
      {:on-click #(swap! show? not)}
      (if @show? "Hide schedule" "Show schedule")]
     (when @show?
       [:table
        [:thead
         [:tr 
          [:th "Arrive"]
          [:th "Depart"]
          [:th "Locker"]
          [:th "Parcels"]
          [:th "Returns"]]]
        [:tbody
         (map
           (fn [stop]
             [:tr {:class "itinerary" :key (:id stop)}
              [:td (-> stop :arrive-by format-time)]
              [:td (-> stop :depart-by format-time)]
              [:td
               [:b (-> stop :locker :canonical-id)]
               (gstr/unescapeEntities "	&nbsp; ")
               (-> stop :locker :name)]
              [:td (-> stop :parcels count) " parcels"]
              [:td (-> stop :returns count) " returns"]
              ]) (:stops itinerary))
         ]])])
  )

(defn plan [plan]
  (let [arrow (fn [] [:li {:class "arrow"} (gstr/unescapeEntities "	&#9654;")])
        summary-item (fn [title details]
                       [:li
                        [:b {:class "title"} title]
                        [:span {:class "details"} details]])]

    [:div.plan
     [:div.plan-header
      [:h3 (:canonical-id plan)]
      [:span {:class (str "pill plan-state " (:state plan))}
       (format-keyword (:state plan))]]
     [:div.plan-body
      [:div.plan-info
       (map
         (fn [itinerary]
           [:div.itinerary { :key (:id itinerary)}
            [:h4.vehicle
             (icons/render
               (icons/icon :fontawesome.solid/truck-fast)
               {:size 18 :class "icon"})
             (-> itinerary :vehicle :canonical-id)
             [:span.name (-> itinerary :vehicle :name)]]
            [:ul.itinerary-summary
             (summary-item
               (-> itinerary :start-depot :canonical-id)
               (-> itinerary :start-depot :name))
             (arrow)
             (summary-item
               (str (count (:stops itinerary)) " stops")
               (str
                 (reduce + (map #(count (:parcels %)) (:stops itinerary))) " parcels, "
                 (reduce + (map #(count (:returns %)) (:stops itinerary))) " returns"))
             (arrow)
             (summary-item
               (-> itinerary :end-depot :canonical-id)
               (-> itinerary :end-depot :name))
             ]
            [timesheet itinerary]
            ]) (:itineraries plan))
       ]
      (let [plan-url (str "/api/map/plan-preview/" (:canonical-id plan))]
        [:a.plan-map {:target "blank" :href plan-url}
         [:img { :src plan-url }]])
      ]]))

(defn plans []
  (let [fetch-plans #(rf/dispatch [:fetch "/api/plans" :plans])
        plans (rf/subscribe [:plans])]
    (r/create-class
      {:display-name "plans"

       :component-did-mount fetch-plans

       :reagent-render
       (fn []
         [:div
          [:h1 "Route plans"]
          [:div.plans-list
           (map (fn [d] [:div {:key (:id d)} [plan d]]) @plans)]])})))
