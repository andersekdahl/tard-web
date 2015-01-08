(defproject tard-web "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2665"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [secretary "0.4.0"]
                 [sablono "0.2.22"]
                 [om "0.7.0"]
                 [com.taoensso/sente "1.2.0"]
                 [com.taoensso/timbre "3.3.1"]
                 [com.cognitect/transit-cljs "0.8.194"]]

  :node-dependencies [[source-map-support "0.2.8"]]

  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-npm "0.4.0"]]

  :source-paths ["src" "target/classes"]

  :clean-targets ["dist/out/tard_web" "dist/tard_web.js" "dist/tard_web.min.js"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]
              :compiler {
                :output-to "dist/tard_web.js"
                :output-dir "dist/out"
                :optimizations :none
                :cache-analysis true                
                :source-map true}}
             {:id "release"
              :source-paths ["src"]
              :compiler {
                :output-to "dist/tard_web.min.js"
                :pretty-print false              
                :optimizations :advanced}}]})
