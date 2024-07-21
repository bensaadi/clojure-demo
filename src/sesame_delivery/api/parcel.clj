(ns sesame-delivery.api.parcel
  (:require 
    [compojure.core :refer [routes GET POST]]
    [datomic.api :as d]
    [java-time.api :as jt]
    [sesame-delivery.api.utils :refer :all]
    [sesame-delivery.api.db :refer [db-url]]))

(import java.util.Date)

(defn gen-parcel-canonical-id []
  (str "B" (apply str
             (repeatedly 6 #(rand-nth "123456789ABCEFGHJKLMNPRSTUVWXYZ")))))


(defn insert-parcel [fields]
  (let [[locker-id compartment-id depot-id size deliver-by] fields
        canonical-id (gen-parcel-canonical-id)
        parcel-id (d/tempid :db.part/delivery)]
    (-> db-url
      (d/connect)
      (d/transact
        [
         {:db/id parcel-id
          :parcel/depot depot-id
          :parcel/canonical-id canonical-id
          :parcel/compartment compartment-id
          :parcel/locker locker-id
          :parcel/size size
          :parcel/state :parcel.state/labeled
          :parcel/deliver-by (jt/java-date deliver-by) }
         [:db/add compartment-id :compartment/state :compartment.state/pending-order-delivery]]))))

(defn get-parcels []
  (map first
    (q '[:find
        (pull
          ?e [:db/id
              :parcel/canonical-id
              :parcel/deliver-by
              :parcel/itinerary
              {:parcel/plan [:plan/canonical-id]}
              {:parcel/size [:db/ident]}
              {:parcel/state [:db/ident]}])
        :where [?e :parcel/canonical-id]])))


(defn get-parcels-for-locker [locker-id date]
  (let [start (jt/java-date (jt/truncate-to date :days))
        end (jt/java-date (jt/truncate-to (jt/plus date (jt/days 1)) :days))]
    (map #(:db/id %)
      (map first
        (q
          '[:find
            (pull ?e [:db/id ])
            :in $ ?locker ?start ?end
            :where [?e :parcel/locker ?locker ]
            [?e :parcel/deliver-by ?deliver-by]
            [(>= ?deliver-by ?start)]
            [(< ?deliver-by ?end)]]
          locker-id start end)))))



(defn list-parcels [_request]
  (->>
    (get-parcels)
    format-query-output
    (map #(assoc % :itinerary-id (or ((comp :id :itinerary) %) :no-itinerary) ))
    (map #(assoc % :plan-id (or ((comp :canonical-id :plan) %) :no-plan) ))
    (map #(assoc % :delivery-date ((comp (fn [d] (format-date (jt/instant d))) :deliver-by) %) ))
    (nested-group-by [:delivery-date :plan-id :itinerary-id])
    (into (sorted-map))
    success))

(defn controller []
  (routes
    (GET "/" [] list-parcels)))
