(ns sesame-delivery.server
  (:require 
    [compojure.core :refer [defroutes context GET]]
    [compojure.route :as route]
    [ring.middleware.reload :refer [wrap-reload]] 
    [ring.middleware.content-type :refer [wrap-content-type]]
    [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
    [ring.util.response :as r]
    [ring.adapter.jetty :as jetty]
    [clojure.string :as s]

    [sesame-delivery.api.utils :refer [wrap-datomic]]
    [sesame-delivery.api.db :refer [setup-db db-url]]
    [sesame-delivery.api.fixtures :refer [insert-test-data]]
    [sesame-delivery.api.locker :as locker]
    [sesame-delivery.api.parcel :as parcel]
    [sesame-delivery.api.return :as return]
    [sesame-delivery.api.map :as map]
    [sesame-delivery.api.depot :as depot]
    [sesame-delivery.api.vehicle :as vehicle]
    [sesame-delivery.api.plan :as plan])
  (:gen-class))

(def api-routes
  (context "/" []
    (context "/api" []
      (context "/lockers" [] (locker/controller))
      (context "/plans" [] (plan/controller))
      (context "/parcels" [] (parcel/controller))
      (context "/depots" [] (depot/controller))
      (context "/returns" [] (return/controller))
      (context "/vehicles" [] (vehicle/controller))
      (context "/map" [] (map/controller))
      (GET "/reset" [] (fn [_request] (setup-db) (insert-test-data) "done"))
      (route/not-found {:body {:status "error" :message "Not found"}}))))
 
(defroutes api-handler
  (-> api-routes (wrap-datomic db-url) wrap-json-response wrap-json-body wrap-reload))

(defn handler [request]
  (if (s/starts-with? (:uri request) "/api")
    (api-handler request)
    (if (s/starts-with? (:uri request) "/public")
     ((wrap-content-type (constantly (r/file-response (str "resources" (:uri request))))) request)
     (r/file-response "resources/public/index.html")
    )))

(defn -main
  [& args]
  (defonce server
    (do
      (println "Web server listening on port 3000")
      (setup-db)
      (insert-test-data)
      (jetty/run-jetty handler {:port 3000}))))