(defproject sesame-delivery "0.1.0-SNAPSHOT"
 	:jvm-opts ["-Dclojure.server.repl={:port 5555 :accept clojure.core.server/repl}"]
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"
  :main ^:skip-aot sesame-delivery.server
  :dependencies [
                 ; server-side
  															[org.clojure/clojure "1.11.1"]
                 [org.clojure/math.numeric-tower "0.0.5"]
  															[com.datomic/peer "1.0.7180"]
                 [datomic-schema "1.3.1"]
                 [ring/ring-devel "1.8.0"]
                 [ring/ring-json "0.5.1"]
                 [ring/ring-jetty-adapter "1.8.1"]

                 [compojure "1.7.1"]
                 [com.google.ortools/ortools-java "9.9.3963"]
                 [clojure.java-time "1.4.2"]
                 [thi.ng/geom "1.0.1"]
                 [com.fasterxml.jackson.core/jackson-annotations "2.17.2"]
                 [geocoordinates "0.1.0"]
                 [factual/geo "3.0.1"]
                 [com.michaelgaare/clojure-polyline "0.4.0"]
                 [net.mikera/core.matrix "0.63.0"]

                 ; client-side
                 [org.clojure/clojurescript "1.11.4"]
                 [reagent "1.1.1"  :exclusions [cljsjs/react cljsjs/react-dom cljsjs/react-dom-server]]
                 [reagent-utils "0.3.8"]
                 [re-frame "1.4.3"]
                 [day8.re-frame/http-fx "0.2.4"]
                 [metosin/reitit "0.7.1"]
                 [venantius/accountant "0.2.5"]
                 [no.cjohansen/fontawesome-clj "2024.01.22"]]

  :source-paths ["src"]
  :target-path "target/%s"
  :java-source-paths ["java"]
  :ring {:handler sesame-delivery.server/api-handler}

  :aliases {"fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
  										"fig:prod" ["trampoline" "run" "-m" "figwheel.main" "-bo" "prod"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "sesame-delivery.test-runner"]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.17"]
                                  [org.slf4j/slf4j-nop "1.7.30"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   
                   :resource-paths ["target"]
                   ;; need to add the compiled assets to the :clean-targets
                   :clean-targets ^{:protect false} ["target"]}

             :uberjar {:aot :all}})

