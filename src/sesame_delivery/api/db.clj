(ns sesame-delivery.api.db
 	(:require
  	 [datomic.api :as d]
  	 [datomic-schema.schema :as s]
  	 [sesame-delivery.api.schema :refer [db-parts db-schema db-schema-raw-parts]]))

(def db-url "datomic:dev://localhost:4334/delivery")

(defn setup-db []
  (d/delete-database db-url)
  (d/create-database db-url)
  (d/transact
    (d/connect db-url)
   	(concat
     	(s/generate-parts db-parts)
     	(s/generate-schema db-schema)
     	db-schema-raw-parts)))