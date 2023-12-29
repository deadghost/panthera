(ns panthera.config
  (:require
   [libpython-clj2.python :as py]))

(defn start-python!
  [f]
  (py/initialize!)
  (f))
