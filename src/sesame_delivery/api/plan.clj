(ns sesame-delivery.api.plan
  (:require 
    [clojure.instant]
    [clojure.walk :refer [walk]]
    [compojure.core :refer [routes GET POST]]
    [datomic.api :as d]
    [java-time.api :as jt]
    [sesame-delivery.api.utils :refer :all]
    [sesame-delivery.api.algorithm :refer [solve-optimized-plan]]
    [sesame-delivery.api.depot :refer [get-depots]]
    [sesame-delivery.api.vehicle :refer [get-vehicles]]
    [sesame-delivery.api.locker :refer [get-lockers]]
    [sesame-delivery.api.return :refer [get-returns-for-locker]]
    [sesame-delivery.api.parcel :refer [get-parcels-for-locker]]
    [sesame-delivery.api.db :refer [db-url]]))

(defn _make-itinerary 
  [{
    depot :depot
    vehicle :vehicle
    lockers :lockers
    parcels-delivery-date :parcels-delivery-date
    stop-times :stop-times}]
  (let [{depot-id :db/id} depot
        {vehicle-id :db/id} vehicle
        locker-ids (map #(:db/id %) lockers)]
    {:depot-id depot-id
     :vehicle-id vehicle-id
     :stop-times stop-times
     :lockers (map
                (fn [locker-id]
                  {:locker-id locker-id
                   :parcel-ids (get-parcels-for-locker locker-id parcels-delivery-date)
                   :return-ids (get-returns-for-locker locker-id)}) locker-ids)}))



(defn random-stop-times [date stops-count]
  (into []
    (map (fn [i]
           [
            (jt/plus date (jt/minutes (* 40 i)))
            (jt/plus date (jt/minutes (+ 5 (* 20 (inc i) ))))])
      (range stops-count)))
  )

(defn _make-plan [parcels-delivery-date vehicles-count stop-lockers stop-times]
  (let [depot (first (get-depots))
        vehicles (get-vehicles)]
    (map
      (fn [i]
        (_make-itinerary
          {:depot depot
           :vehicle (nth vehicles i)
           :lockers (nth stop-lockers i)
           :stop-times (nth stop-times i)
           :parcels-delivery-date parcels-delivery-date
           })
        )
      (range vehicles-count))))

(defn make-random-plan [date parcels-delivery-date vehicles-count]
  (let [lockers (shuffle (get-lockers))]
    (_make-plan
      parcels-delivery-date
      vehicles-count
      (partition (- (/ (count lockers) vehicles-count) 3) lockers)
      (repeat vehicles-count
        (random-stop-times date (+ 2 (count lockers)))))))

(defn make-optimized-plan [date parcels-delivery-date vehicles-count]
  (let
    [[locker-canonical-ids stop-times]
     (solve-optimized-plan
       {:date date
        :parcels-delivery-date parcels-delivery-date
        :depot-canonical-id (:depot/canonical-id (first (get-depots)))
        :vehicles-count vehicles-count})

     stop-lockers
    (map
      (fn [canonical-id]
        (map canonical-id->entity canonical-id)) locker-canonical-ids)]
    (when (> (count stop-lockers) 0)
      (_make-plan
        parcels-delivery-date
        vehicles-count
        stop-lockers
        stop-times)
      )))

(defn make-stop-ids [itineraries]
  (map (fn [{lockers :lockers}]
         (map(fn [_]
               (d/tempid :db.part/delivery)) lockers))
    itineraries)
  )

(defn gen-plan-canonical-id [start-at]
  (let [start (jt/java-date (jt/truncate-to start-at :days))
        end (jt/java-date (jt/truncate-to (jt/plus start-at (jt/days 1)) :days))
        last-canonical-id
        (->> (q '[:find
                 (pull ?e [:db/id :plan/canonical-id])
                 :in $ ?start ?end
                 :where
                 [?e :plan/start-at ?start-at]
                 [(>= ?start-at ?start)]
                 [(< ?start-at ?end)]]
               start end)
          (map (comp :plan/canonical-id first) )
          (last)
          )]

    (->> (or last-canonical-id "000")
      (take-last 3)
      (apply str)
      (Integer/parseInt)
      (inc)
      (#(str "P" (jt/format "yyMMdd" start-at)
          (when (< % 100) "0") (when (< % 10) "0") %))
      )))

; TODO break down into smaller functions
(defn insert-plan [{ itineraries :itineraries }]
  (when (> (count itineraries) 0)
    (let
      [plan-id (d/tempid :db.part/delivery)
       itinerary-ids (map (fn [_] (d/tempid :db.part/delivery)) itineraries)
       stop-ids-per-itinerary (make-stop-ids itineraries)
       start-at (apply jt/min (map (comp first #(map first %) :stop-times) itineraries))
       end-at (apply jt/max (map (comp last #(map last %) :stop-times) itineraries))]
      (-> db-url
        (d/connect)
        (d/transact
          (concat 
            ; facts about plan
            [{:db/id plan-id
              :plan/start-at (jt/java-date start-at)
              :plan/end-at (jt/java-date end-at)
              :plan/state :plan.state/scheduled
              :plan/itineraries itinerary-ids
              :plan/canceled? false
              :plan/canonical-id (gen-plan-canonical-id start-at)}]

            ; facts about parcels 
            (->>
              (map-indexed
                (fn [i itinerary]
                  (map
                    (fn [locker]
                      (map
                        (fn [parcel-id] 
                          [[:db/add parcel-id :parcel/itinerary (nth itinerary-ids i)]
                           [:db/add parcel-id :parcel/plan plan-id]
                           [:db/add parcel-id :parcel/state :parcel.state/ready-to-ship]])
                        (:parcel-ids locker)))
                    (:lockers itinerary)))
                itineraries)
              (apply concat)
              (apply concat)
              (apply concat))

            ; facts about returns
            (->>
              (map-indexed
                (fn [i itinerary]
                  (map
                    (fn [locker]
                      (map
                        (fn [return-id] 
                          [[:db/add return-id :return/itinerary (nth itinerary-ids i)]
                           [:db/add return-id :return/plan plan-id]
                           [:db/add return-id :return/state :return.state/scheduled-for-pickup]])
                        (:return-ids locker)))
                    (:lockers itinerary)))
                itineraries)
              (apply concat)
              (apply concat)
              (apply concat))

            ; facts about itineraries
            (map-indexed
              (fn [i {depot-id :depot-id vehicle-id :vehicle-id stop-times :stop-times}]
                {:db/id (nth itinerary-ids i)
                 :itinerary/state :itinerary.state/scheduled
                 :itinerary/vehicle vehicle-id
                 :itinerary/start-depot depot-id
                 :itinerary/end-depot depot-id
                 :itinerary/start-at (jt/java-date (last (first stop-times)))
                 :itinerary/end-at (jt/java-date (last (last stop-times)))
                 :itinerary/stops (into [] (nth stop-ids-per-itinerary i))
                 }) itineraries)

            ; facts about stops
            (flatten
              (map-indexed
                (fn [j {lockers :lockers stop-times :stop-times}]
                  (map-indexed
                    (fn [i {locker-id :locker-id parcel-ids :parcel-ids return-ids :return-ids }]
                      [{:db/id (nth (nth stop-ids-per-itinerary j) i)
                        :stop/locker locker-id
                        :stop/parcels parcel-ids
                        :stop/returns return-ids
                        :stop/arrive-by (jt/java-date (first (get stop-times (inc i) )))
                        :stop/depart-by (jt/java-date (last (get stop-times (inc i) )))
                        }]) lockers)) itineraries))))))))


(defn query-plans [db-url]
  (map first
    (q '[:find
        (pull
          ?e [:db/id
              :plan/canonical-id
              {:plan/state [:db/ident]}
              {:plan/itineraries
               [:db/id
                {:itinerary/state [:db/ident]}
                {:itinerary/vehicle
                 [:vehicle/canonical-id :vehicle/name]}
                {:itinerary/start-depot
                 [:depot/canonical-id :depot/name]}
                {:itinerary/end-depot
                 [:depot/canonical-id :depot/name]}
                {:itinerary/stops
                 [:db/id
                  :stop/arrive-by
                  :stop/depart-by
                  {:stop/locker [:locker/canonical-id :locker/name]}
                  {:stop/parcels [
                                  :parcel/canonical-id
                                  {:parcel/size [:db/ident]}]}
                  {:stop/returns [
                                  :return/canonical-id
                                  {:return/size [:db/ident]}]}]}
                ]
               }
              ])
        :where
        [?e :plan/canonical-id]
        [?e :plan/canceled? false]])))

(defn list-plans [_request] 
  (-> db-url
    query-plans
    format-query-output
    success))

(defn list-plans-summary [_request] 
  (->> db-url
    query-plans
    format-query-output
    (map
      #(assoc % :itineraries
         (map-values first (nested-group-by [:id] (:itineraries %))) ))
    (nested-group-by [:canonical-id])
    (map-values first)
    success))

(defn create-plan [request]
  (let [date (parse-date (get-in request [:body "date"]))]
    (try 
      (let [new-plan (insert-plan {:itineraries (make-optimized-plan date date 2)})]
        (if new-plan (success "Plan created") (fail "No plan to create")))
      (catch Throwable t
        (println t)
        (error "Could not create plan")))))

(defn get-plan-parcels [plan-id]
  (map #(:db/id %)
    (map first
      (q '[:find
          (pull ?e [:db/id ])
          :in $ ?plan-id
          :where [?e :parcel/plan ?plan-id]]
        plan-id))))

(defn get-plan-returns [plan-id]
  (map #(:db/id %)
    (map first
      (q '[:find
          (pull ?e [:db/id ])
          :in $ ?plan-id
          :where [?e :return/plan ?plan-id]]
        plan-id))))

(defn cancel-plan [request]
  (let 
    [plan-canonical-id (get-in request [:body "plan-canonical-id"])
     plan-id (:db/id (canonical-id->entity plan-canonical-id))
     parcels (get-plan-parcels plan-id)
     returns (get-plan-returns plan-id)]
    (if
      (-> db-url
        (d/connect)
        (d/transact
          (concat
            [{:plan/canceled? true :db/id plan-id}]
            (->>
              parcels
              (map
                (fn [parcel-id]
                  [[:db/retract parcel-id :parcel/itinerary]
                   [:db/retract parcel-id :parcel/plan]
                   [:db/add parcel-id :parcel/state :parcel.state/labeled]]))
              (apply concat))
            (->>
              returns
              (map
                (fn [return-id]
                  [[:db/retract return-id :return/itinerary]
                   [:db/retract return-id :return/plan]
                   [:db/add return-id :return/state :return.state/returned-to-locker]]))
              (apply concat)
              )))
        (deref)
        (:tx-data)
        (count)
        (> 0))
      (success (str "Cancelled plan " plan-canonical-id))
      (fail "Could not cancel plan"))))

(defn controller []
  (routes
    (GET "/" [] list-plans)
    (POST "/" [] create-plan)
    (POST "/cancel" [] cancel-plan)
    (GET "/summary" [] list-plans-summary)))