(ns build
  (:require [clojure.tools.build.api :as b]))

(def ^:private class-dir "target/classes")
(def ^:private uber-file "target/psyclight.jar")

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber
  "Builds the psyclight uberjar. clj -T:build uber"
  [_]
  (let [basis (b/create-basis {:project "deps.edn"})]
    (clean nil)
    (b/copy-dir {:src-dirs   ["src/clj" "resources"]
                 :target-dir class-dir})
    (b/compile-clj {:basis      basis
                    :ns-compile ['xyz.psyclyx.light.main]
                    :class-dir  class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-file
             :basis     basis
             :main      'xyz.psyclyx.light.main})))
