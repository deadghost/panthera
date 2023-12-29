(defproject panthera "0.1-alpha.20"
  :description "Data Frames in Clojure (with Pandas) + NumPy"
  :url "https://github.com/alanmarazzi/panthera"
  :scm {:name "git" :url "https://github.com/alanmarazzi/panthera"}
  :license {:name "EPL-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[clj-python/libpython-clj "2.025"]
                 [org.clojure/core.memoize "0.7.2"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.11.1"]]
                   ;; lein-jupyter causes REPL to not work
                   :plugins [#_[lein-jupyter "0.1.16"]]}})

