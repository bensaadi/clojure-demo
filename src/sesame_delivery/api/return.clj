(ns sesame-delivery.api.return
  (:require 
    [compojure.core :refer [routes GET POST]]
    [datomic.api :as d]
    [java-time.api :as jt]
    [sesame-delivery.api.utils :refer :all]
    [sesame-delivery.api.db :refer [db-url]]))

(defn gen-return-canonical-id []
  (->>
    (repeatedly 6 #(rand-nth unambiguous-chars))
    (apply str)
    (str "R")))

(defn insert-return [fields]
  (let [[locker-id compartment-id depot-id size] fields
        canonical-id (gen-return-canonical-id)
        return-id (d/tempid :db.part/delivery)]
    (-> db-url
      (d/connect)
      (d/transact
        [{:db/id return-id
          :return/depot depot-id
          :return/canonical-id canonical-id
          :return/locker locker-id
          :return/compartment compartment-id
          :return/size size
          :return/state :return.state/returned-to-locker}
         [:db/add compartment-id :compartment/state :compartment.state/pending-return-pickup]]))))

(defn get-returns []
  (map first
    (q '[:find
        (pull
          ?e [:db/id
              :return/canonical-id
              :return/itinerary
              {:return/plan [:plan/canonical-id :plan/start-at ]}
              {:return/size [:db/ident]}
              {:return/state [:db/ident]}])
        :where [?e :return/canonical-id]])))

(defn get-returns-for-locker [locker-id]
  (->>
    locker-id
    (q '[:find
           (pull
             ?e [:db/id])
           :in $ ?locker
           :where
           [?e :return/locker ?locker]
           [?e :return/state :return.state/returned-to-locker]])
    (map first)
    (map #(:db/id %))))

(defn maybe-date [d]
  (if d (format-date (jt/instant d)) "no-date"))

(defn list-returns [_request]
  (->>
    (get-returns)
    format-query-output
    (map #(assoc % :itinerary-id (or ((comp :id :itinerary) %) :no-itinerary) ))
    (map #(assoc % :plan-id (or ((comp :canonical-id :plan) %) :no-plan) ))
    (map #(assoc % :pickup-date ((comp maybe-date :start-at :plan) %) ))
    (nested-group-by [:pickup-date :plan-id :itinerary-id])
    (map-values #(into (sorted-map) %))
    success))

(defn controller []
  (routes
    (GET "/" [] list-returns)))
