(ns sesame-delivery.api.schema
  (:require
    [datomic-schema.schema :as s]))

(def db-parts [(s/part "delivery")])

(def db-schema
  [(s/schema location
     (s/fields
       [lat              :double]
       [long             :double]))

   ; used to construct a distance matrix
   ; note: we're using time-distance to compute routes
   ; to account for the day of week and rush hour,
   ; meters distance is provided as reference only
   (s/schema distance
     (s/fields
       [from-canonical-id :string]
       [to-canonical-id   :string]
       [meters            :long]
       [minutes           :long]
       [day-of-week       :long] ; (0=Monday, 1=Tuesday ...)
       [rush-hour?        :boolean]
       [polyline          :string]))


   (s/schema compartment
     (s/fields
       [canonical-id      :string :unique-value] ; C[0-9]{6}
       [locker            :ref]
       [size              :enum [:size-s :size-m :size-l]]
       [state             :enum
        [
         :vacant
         :pending-order-delivery
         :pending-order-pickup
         :pending-return-dropoff
         :pending-return-pickup]]))

   (s/schema locker
     (s/fields
       [name              :string]
       [canonical-id      :string :unique-value]
       [location          :ref]
       [compartments      :ref :many]
       [open-from         :string]    ; hh:mm 24-hour
       [open-until        :string]))  ; hh:mm 24-hour

   (s/schema depot
     (s/fields
       [name              :string]
       [canonical-id      :string :unique-value] ; D[0-9]{4}
       [location          :ref]
       [vehicles          :ref :many]
       [open-from         :string]    ; hh:mm 24-hour
       [open-until        :string]))  ; hh:mm 24-hour


   (s/schema parcel
     (s/fields
       [canonical-id      :string :unique-value] ; B[1-9ABCEFGHJKLMNPRSTUVWXYZ]{6}
       [size              :enum [:size-s :size-m :size-l]]
       [depot             :ref]
       [locker            :ref]
       [compartment       :ref]
       [plan              :ref]
       [itinerary         :ref]
       [deliver-by        :instant]
       [delivered-at      :instant]
       [state             :enum
        [
         :labeled                    ; items have been boxed and labeled
         :ready-to-ship              ; ready to be picked up by driver
         :en-route                   ; in transit to lockers
         :delivered-to-locker        ; currently sitting inside a compartment
         :picked-up-by-customer]]))  ; final package lifecycle step


   (s/schema return
     (s/fields
       [canonical-id      :string :unique-value] ; R[1-9ABCEFGHJKLMNPRSTUVWXYZ]{6}
       [size              :enum [:size-s :size-m :size-l]]
       [depot             :ref]
       [locker            :ref]
       [compartment       :ref]
       [plan              :ref]
       [itinerary         :ref]
       [state             :enum
        [
         :initiated                ; return initiated by customer
         :returned-to-locker       ; item(s) were placed inside a locker compartment
         :scheduled-for-pickup     ; item(s) were scheduled for pickup and are part of a plan
         :en-route                 ; in transit to depot
         :returned-to-depot]]))    ; final return lifecycle step


   (s/schema vehicle
     (s/fields
       [name              :string]
       [canonical-id      :string :unique-value] ; V[0-9]+
       [capacity          :long] ; volume capacity in units of s-size parcels
       [location          :ref]
       [depot             :ref]
       [state             :enum [:parked-in-depot :in-maintenance :in-use]]))

   (s/schema stop
     (s/fields
       [locker             :ref]
       [parcels            :ref :many]
       [returns            :ref :many]
       [arrive-by          :instant]
       [depart-by          :instant]
       [arrived-at         :instant]
       [departed-at        :instant]))

   (s/schema itinerary
     (s/fields
       [state             :enum [:created :scheduled :in-progress :completed]]
       [vehicle           :ref]
       [stops             :ref :many]
       [start-depot       :ref]
       [end-depot         :ref]
       [start-at          :instant]
       [end-at            :instant]
       [started-at        :instant]
       [ended-at          :instant]))

   (s/schema plan
     (s/fields
       [canceled?         :boolean]
       [canonical-id      :string :unique-value] ; P[0-9]{9}   YYMMDD###
       [state             :enum [:created :scheduled :in-progress :completed]]
       [itineraries       :ref :many]
       [start-at          :instant]
       [end-at            :instant]
       [started-at        :instant]
       [ended-at          :instant]))])


; datomic-schema does not currently support composite tuples -- going raw

(def db-schema-raw-parts
  [{:db/ident :distance/from+to+day-of-week+rush-hour
    :db/valueType :db.type/tuple
    :db/tupleAttrs
    [
     :distance/from-canonical-id
     :distance/to-canonical-id
     :distance/day-of-week
     :distance/rush-hour?]
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}])
