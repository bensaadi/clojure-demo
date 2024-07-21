(ns sesame-delivery.ui.components.parcels
 	(:require
    [sesame-delivery.ui.utils :refer [format-keyword format-display-date format-ymd-date]]
    [reagent.core :as reagent]
    [re-frame.core :as rf]))

(rf/reg-sub :parcels (fn [db] (:parcels db)))
(rf/reg-sub :returns (fn [db] (:returns db)))
(rf/reg-sub :plans-summary (fn [db] (:plans-summary db)))
(rf/reg-sub :processing? (fn [db] (:processing? db)))
(rf/reg-sub :new-plan-status (fn [db] (:new-plan-status db)))
(rf/reg-sub :cancel-plan-status (fn [db] (:cancel-plan-status db)))

(defn parcel []
 	"Parcel")

(defn parcels []
  (let [fetch-parcels #(rf/dispatch [:fetch "/api/parcels" :parcels])
        fetch-returns #(rf/dispatch [:fetch "/api/returns" :returns])
        fetch-plans-summary #(rf/dispatch [:fetch "/api/plans/summary" :plans-summary])
        create-plan #(rf/dispatch [:post "/api/plans" :new-plan-status {:date %}])
        cancel-plan #(rf/dispatch [:post "/api/plans/cancel" :cancel-plan-status {:plan-canonical-id %}])

        processing? (rf/subscribe [:processing?])
        new-plan-status (rf/subscribe [:new-plan-status])
        cancel-plan-status (rf/subscribe [:cancel-plan-status])
        parcels (rf/subscribe [:parcels])
        returns (rf/subscribe [:returns])
        plans-summary (rf/subscribe [:plans-summary])]

    (reagent/create-class
      {:display-name "parcels"

       :component-did-mount
       #(do (fetch-parcels) (fetch-returns) (fetch-plans-summary))

       :component-did-update
 						#(do (fetch-parcels) (fetch-returns) (fetch-plans-summary))

       :reagent-render
       (fn []
         (deref processing?)
         (deref new-plan-status)
         (deref cancel-plan-status)
         (deref parcels)
         (deref returns)
         (deref plans-summary)
         [:div
          [:h1 "Parcels"]
          (doall (for [[day day-parcels] @parcels]
            [:div.parcels-list {:key (str "parcels-list-" day)}
             [:h2 (format-display-date day)]
             [:div.itinerary-boxes
              (for [[plan-id plan-parcels] day-parcels]
                [:div.plan {:key plan-id :class (when (= :no-plan plan-id) "no-plan")}
                 [:div.plan-header
                  (if (= :no-plan plan-id)
                    [:button
                     {
                     	:disabled @processing?
                     	:on-click #(create-plan (format-ymd-date day))}
                    	(if @processing? "New plan..." "+ New plan" )]
                    [:div
                    	[:h3 plan-id]
                    	[:button.cancel
                     	{
                     		:disabled @processing?
                     		:on-click #(when (js/confirm "Cancel this plan?") (cancel-plan plan-id))
                     		} "X"]]
                    )
                  [:p.info
                   ]]
                 [:div.plan-content
                  (for [[itinerary-id box-parcels] plan-parcels]
                    [:div.itinerary {:key (str "itinerary-" itinerary-id)}
                     [:div.container {:key itinerary-id}
                      (map
                        (fn [parcel]
                          [:div.parcel
                           {
                           	:key (str "parcel-" (:canonical-id parcel))
                           	:class (str (:size parcel) " " (:state parcel))
                            :title (:canonical-id parcel)}]
                          ) box-parcels)
                      (map
                        (fn [ret]
                          [:div.return
                           {
                           	:key (str "return-" (:canonical-id ret))
                           	:class (str (:size ret) " " (:state ret))
                            :title (:canonical-id ret)}])
                        (if (and
                      								(= plan-id :no-plan)
                              (= (first (keys @parcels)) day) )
                      				(:no-itinerary (:no-plan (:no-date @returns)))
                         	(itinerary-id (plan-id (day @returns)))))
                      ]
                      
                     [:div.itinerary-info
                      (when-let [stops (-> @plans-summary plan-id :itineraries itinerary-id :stops)]
                        [:div
                         [:div (str (count stops)) " stops"]
                         [:div (str (reduce + (map #(count (:parcels %)) stops)) " parcels")]
                         [:div (str (reduce + (map #(count (:returns %)) stops)) " returns")]]
                        )
                      ]])]])
              ]
             ]
            ))

          ])})))