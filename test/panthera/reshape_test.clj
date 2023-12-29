(ns panthera.reshape-test
  (:refer-clojure
   :exclude [drop])
  (:require
   [clojure.test :refer :all]
   [libpython-clj2.python :as py]
   [libpython-clj2.require :refer [require-python]]
   [panthera.pandas.utils :as u :reload true]
   [panthera.pandas.generics :as g]
   [panthera.pandas.reshape :as r :reload true]
   [panthera.pandas.math :as m :reload true]
   [panthera.pandas.conversion :as c]))

(require-python '[numpy :as np])

(defn filter-nan
  [d]
  (into [] (comp (mapcat vals) (filter (complement #(.isNaN %)))) d))

(deftest crosstab
  (are [r c o]
       (= (u/->clj (r/crosstab r {:columns c})) o)
    [[]] [[]] []
    [[1 2 2]] [[:a :b :a]] [{:a 1 :b 0} {:a 1 :b 1}]
    (g/series [1 2 3]) [[:a :b :a]] [{:a 1 :b 0} {:a 0 :b 1} {:a 1 :b 0}])
  (are [r d o]
      (= (filter-nan (u/->clj (r/crosstab r d))) o)
    [[1 2 2]] {:columns [[:a :b :b]]
               :values  [10 20 30]
               :aggfunc :mean} [10.0 25.0])
  (is (= (u/->clj
           (r/crosstab [[1 2 2]] {:columns [[:a :b :a]] :margins true}))
        [{:a 1 :b 0 :All 1}
         {:a 1 :b 1 :All 2}
         {:a 2 :b 1 :All 3}])))

(deftest pivot
  (are [d o]
      (= (u/->clj (r/pivot (g/data-frame {:foo [:one :one :one :two :two :two]
                                          :bar [:a :b :c :a :b :c]
                                          :baz [1 2 3 4 5 6]
                                          :zoo [:x :y :z :q :w :t]})
                    d)) o)

    {:columns :bar :index :foo} [{[:baz :a] 1,
                                  [:baz :b] 2,
                                  [:baz :c] 3,
                                  [:zoo :a] "x",
                                  [:zoo :b] "y",
                                  [:zoo :c] "z"}
                                 {[:baz :a] 4,
                                  [:baz :b] 5,
                                  [:baz :c] 6,
                                  [:zoo :a] "q",
                                  [:zoo :b] "w",
                                  [:zoo :c] "t"}]

    {:index :foo :columns :bar :values [:baz :zoo]} [{[:baz :a] 1,
                                                      [:baz :b] 2,
                                                      [:baz :c] 3,
                                                      [:zoo :a] "x",
                                                      [:zoo :b] "y",
                                                      [:zoo :c] "z"}
                                                     {[:baz :a] 4,
                                                      [:baz :b] 5,
                                                      [:baz :c] 6,
                                                      [:zoo :a] "q",
                                                      [:zoo :b] "w",
                                                      [:zoo :c] "t"}]))

(deftest cut
  (is
    (->> (u/->clj (r/cut (g/series [1 7 5 4 6 3]) 3))
      first
      vals
      first
      (m/eq (u/simple-kw-call u/pd "Interval" {:left 0.994 :right 3.0}))))
  (are [b d o]
      (= (u/->clj (r/cut (g/series [1 7 5 4 6 3]) b d)) o)
    3 {:labels false} [{:unnamed 0} {:unnamed 2} {:unnamed 1}
                       {:unnamed 1} {:unnamed 2} {:unnamed 0}]

    3 {:labels [:a :b :c]} [{:unnamed "a"} {:unnamed "c"} {:unnamed "b"}
                            {:unnamed "b"} {:unnamed "c"} {:unnamed "a"}]

    [0 3 5 7] {:labels false} [{:unnamed 0} {:unnamed 2} {:unnamed 1}
                               {:unnamed 1} {:unnamed 2} {:unnamed 0}]))

(deftest qcut
  (is
    (->> (u/->clj (r/cut (g/series (range 5)) 4))
      first
      vals
      first
      (m/eq (u/simple-kw-call u/pd "Interval" {:left -0.004 :right 1.0}))))
  (are [b d o]
      (= (u/->clj (r/cut (g/series (range 5)) b d)) o)
    3 {:labels false} [{:unnamed 0} {:unnamed 0}
                       {:unnamed 1} {:unnamed 2}
                       {:unnamed 2}]

    3 {:labels [:low :medium :high]} [{:unnamed "low"}
                                      {:unnamed "low"}
                                      {:unnamed "medium"}
                                      {:unnamed "high"}
                                      {:unnamed "high"}]))

(deftest merge-ordered
  (let [a (g/data-frame
            {:key    [:a :c :e :a]
             :lvalue [1 2 3 1]
             :group  [:a :a :a :b]})
        b (g/data-frame
            {:key    [:b :c :d]
             :rvalue [1 2 3]})]
    (are [d o]
        (m/same? (r/merge-ordered a b d) (g/data-frame o))
      {} [{:key "a", :lvalue 1.0, :group "a", :rvalue ##NaN}
          {:key "a", :lvalue 1.0, :group "b", :rvalue ##NaN}
          {:key "b", :lvalue ##NaN, :group ##NaN, :rvalue 1.0}
          {:key "c", :lvalue 2.0, :group "a", :rvalue 2.0}
          {:key "d", :lvalue ##NaN, :group ##NaN, :rvalue 3.0}
          {:key "e", :lvalue 3.0, :group "a", :rvalue ##NaN}])))

(deftest merge-asof
  (let [trades (g/data-frame
                 {:time     (c/->datetime ["2016-05-25 13:30:00.023"
                                           "2016-05-25 13:30:00.038"
                                           "2016-05-25 13:30:00.048"
                                           "2016-05-25 13:30:00.048"])
                  :ticker   [:MSFT :MSFT :GOOG :AAPL]
                  :price    [51.95 51.95 720.77 98.00]
                  :quantity [75 155 100 100]})
        quotes (g/data-frame
                 {:time   (c/->datetime ["2016-05-25 13:30:00.023"
                                         "2016-05-25 13:30:00.023"
                                         "2016-05-25 13:30:00.030"
                                         "2016-05-25 13:30:00.048"
                                         "2016-05-25 13:30:00.049"])
                  :ticker [:GOOG :MSFT :MSFT :GOOG :AAPL]
                  :bid    [720.5 51.95 51.97 720.5 97.99]
                  :ask    [720.93 51.96 51.98 720.93 98.01]})]
    (are [d o]
        (m/same? (r/merge-asof trades quotes d) (g/data-frame o))
      {:on       :time
       :suffixes [:-x :-y]} [{:time     (c/->datetime "2016-05-25 13:30:00.023000"),
                              :ticker-x "MSFT",
                              :price    51.95,
                              :quantity 75,
                              :ticker-y "MSFT",
                              :bid      51.95,
                              :ask      51.96}
                             {:time     (c/->datetime "2016-05-25 13:30:00.038000"),
                              :ticker-x "MSFT",
                              :price    51.95,
                              :quantity 155,
                              :ticker-y "MSFT",
                              :bid      51.97,
                              :ask      51.98}
                             {:time     (c/->datetime "2016-05-25 13:30:00.048000"),
                              :ticker-x "GOOG",
                              :price    720.77,
                              :quantity 100,
                              :ticker-y "GOOG",
                              :bid      720.5,
                              :ask      720.93}
                             {:time     (c/->datetime "2016-05-25 13:30:00.048000"),
                              :ticker-x "AAPL",
                              :price    98.0,
                              :quantity 100,
                              :ticker-y "GOOG",
                              :bid      720.5,
                              :ask      720.93}]

      {:on                  :time
       :allow-exact-matches false
       :suffixes            [:-x :-y]} [{:time     (c/->datetime "2016-05-25 13:30:00.023000"),
                                         :ticker-x "MSFT",
                                         :price    51.95,
                                         :quantity 75,
                                         :ticker-y ##NaN,
                                         :bid      ##NaN,
                                         :ask      ##NaN}
                                        {:time     (c/->datetime "2016-05-25 13:30:00.038000"),
                                         :ticker-x "MSFT",
                                         :price    51.95,
                                         :quantity 155,
                                         :ticker-y "MSFT",
                                         :bid      51.97,
                                         :ask      51.98}
                                        {:time     (c/->datetime "2016-05-25 13:30:00.048000"),
                                         :ticker-x "GOOG",
                                         :price    720.77,
                                         :quantity 100,
                                         :ticker-y "MSFT",
                                         :bid      51.97,
                                         :ask      51.98}
                                        {:time     (c/->datetime "2016-05-25 13:30:00.048000"),
                                         :ticker-x "AAPL",
                                         :price    98.0,
                                         :quantity 100,
                                         :ticker-y "MSFT",
                                         :bid      51.97,
                                         :ask      51.98}]

      {:on        :time
       :direction :forward
       :suffixes  [:-x :-y]} [{:time     (c/->datetime "2016-05-25 13:30:00.023000"),
                               :ticker-x "MSFT",
                               :price    51.95,
                               :quantity 75,
                               :ticker-y "GOOG",
                               :bid      720.5,
                               :ask      720.93}
                              {:time     (c/->datetime "2016-05-25 13:30:00.038000"),
                               :ticker-x "MSFT",
                               :price    51.95,
                               :quantity 155,
                               :ticker-y "GOOG",
                               :bid      720.5,
                               :ask      720.93}
                              {:time     (c/->datetime "2016-05-25 13:30:00.048000"),
                               :ticker-x "GOOG",
                               :price    720.77,
                               :quantity 100,
                               :ticker-y "GOOG",
                               :bid      720.5,
                               :ask      720.93}
                              {:time     (c/->datetime "2016-05-25 13:30:00.048000"),
                               :ticker-x "AAPL",
                               :price    98.0,
                               :quantity 100,
                               :ticker-y "GOOG",
                               :bid      720.5,
                               :ask      720.93}]
      {:on       :time
       :by       :ticker
       :suffixes [:-x :-y]}  [{:time     (c/->datetime "2016-05-25 13:30:00.023000"),
                               :ticker   "MSFT",
                               :price    51.95,
                               :quantity 75,
                               :bid      51.95,
                               :ask      51.96}
                              {:time     (c/->datetime "2016-05-25 13:30:00.038000"),
                               :ticker   "MSFT",
                               :price    51.95,
                               :quantity 155,
                               :bid      51.97,
                               :ask      51.98}
                              {:time     (c/->datetime "2016-05-25 13:30:00.048000"),
                               :ticker   "GOOG",
                               :price    720.77,
                               :quantity 100,
                               :bid      720.5,
                               :ask      720.93}
                              {:time     (c/->datetime "2016-05-25 13:30:00.048000"),
                               :ticker   "AAPL",
                               :price    98.0,
                               :quantity 100,
                               :bid      ##NaN,
                               :ask      ##NaN}])))

(deftest concatenate
  (are [d o do]
      (m/same?
        (r/concatenate [(g/data-frame {:a [1 2 3]
                                       :b [4 5 6]})
                        (g/data-frame {:a [2 2 2]
                                       :b [3 3 3]})] d)
        (g/data-frame o do))

    {} [{:a 1, :b 4} {:a 2, :b 5} {:a 3, :b 6}
        {:a 2, :b 3} {:a 2, :b 3} {:a 2, :b 3}] {:index [0 1 2 0 1 2]}

    {:axis 1} [[1 4 2 3] [2 5 2 3] [3 6 2 3]] {:columns [:a :b :a :b]}

    {:axis 1
     :ignore-index true} [{0 1, 1 4, 2 2, 3 3}
                          {0 2, 1 5, 2 2, 3 3}
                          {0 3, 1 6, 2 2, 3 3}] {}))

(deftest aggregate
  (are [v d o]
      (m/same?
        (r/aggregate (g/data-frame [[1, 2, 3],
                                    [4, 5, 6],
                                    [7, 8, 9],
                                    [##NaN, ##NaN, ##NaN]]
                       {:columns [:A :B :C]}) v d)
        o)

    :sum {} (g/series [12.0 15 18] {:index [:A :B :C]})

    [:sum :min] {} (g/data-frame
                     {:A [12.0 1] :B [15.0 2] :C [18.0 3]}
                     {:index [:sum :min]})

    :sum {:axis 1} (g/series [6.0 15 24 0])))

(deftest remap
  (are [in mpgs ign o]
      (m/same?
        (r/remap
          (g/series in)
          mpgs ign)
        o)
    [:a :b :c]    {:a 1 :b 2 :c 3} nil     (g/series [1 2 3])
    [:a :b ##NaN] #(str "Test " %) :ignore (g/series ["Test a" "Test b" ##NaN])))

(deftest groupby
  (are [d f o]
      (m/same?
        (-> (g/data-frame {:animal    [:falcon :falcon :parrot :parrot]
                         :max-speed [380 370 24 26]})
          (r/groupby :animal d)
          f)
        o)

    {} m/mean (g/data-frame {:max-speed [375 25]}
                {:index (g/series [:falcon :parrot] {:name :animal})})

    {:as-index false} m/mean (g/data-frame [{:animal "falcon" :max-speed 375}
                                            {:animal "parrot" :max-speed 25}])

    {} m/std (g/data-frame [{:max-speed 7.0710678118654755}
                            {:max-speed 1.4142135623730951}]
               {:index (g/series [:falcon :parrot] {:name :animal})})))

(deftest rolling
  (are [w d o]
      (m/same?
        (-> (g/data-frame {:b [0 1 2 3 4]}
              {:index
               (panthera.pandas.conversion/->datetime
                 (g/series
                   ["20130101 09:00:00"
                    "20130101 09:00:02"
                    "20130101 09:00:03"
                    "20130101 09:00:05"
                    "20130101 09:00:06"]))})
          (r/rolling w d)
          m/sum)
        (g/data-frame o
          {:index
           (panthera.pandas.conversion/->datetime
             (g/series
               ["20130101 09:00:00"
                "20130101 09:00:02"
                "20130101 09:00:03"
                "20130101 09:00:05"
                "20130101 09:00:06"]))}))
    2 {} [{:b ##NaN} {:b 1.0} {:b 3.0} {:b 5.0} {:b 7.0}]
    :2s {} [{:b 0.0} {:b 1.0} {:b 3.0} {:b 3.0} {:b 7.0}]
    2 {:win-type :triang} [{:b ##NaN} {:b 0.5} {:b 1.5} {:b 2.5} {:b 3.5}]
    2 {:min-periods 1} [{:b 0.0} {:b 1.0} {:b 3.0} {:b 5.0} {:b 7.0}]))

(deftest ewm
  (are [d o]
      (m/same?
        (-> (g/data-frame {:b [0 1 2 ##NaN 4]})
          (r/ewm d)
          m/mean)
        (g/data-frame o))

    {:com 0.5} [{:b 0.0}
                {:b 0.7499999999999999}
                {:b 1.6153846153846152}
                {:b 1.6153846153846152}
                {:b 3.670212765957447}]

    {:span 2} [{:b 0.0}
               {:b 0.7499999999999999}
               {:b 1.6153846153846152}
               {:b 1.6153846153846152}
               {:b 3.670212765957447}]

    {:com 0.5 :ignore-na true} [{:b 0.0}
                                {:b 0.7499999999999999}
                                {:b 1.6153846153846152}
                                {:b 1.6153846153846152}
                                {:b 3.2249999999999996}]))

(deftest dropna
  (are [s o d]
      (m/same?
        (-> (g/series s)
          r/dropna)
        (g/series o d))
    []        []        {}
    [1 nil 2] [1.0 2.0] {:index [0 2]})

  (are [att out opt]
      (m/same?
        (-> (g/data-frame {:name ["Alfred" "Batman" "Robin"]
                           :toy  [nil "Batmobile" "Whip"]
                           :born [nil "1940-04-25" nil]})
          (r/dropna att))
        (g/data-frame out opt))
    {} [{:name "Batman", :toy "Batmobile", :born "1940-04-25"}] {:index [1]}
    {:axis 1} [{:name "Alfred"} {:name "Batman"} {:name "Robin"}] {}
    {:how :all} [{:name "Alfred", :toy nil, :born nil}
                 {:name "Batman", :toy "Batmobile", :born "1940-04-25"}
                 {:name "Robin", :toy "Whip", :born nil}] {}
    {:thresh 2} [{:name "Batman", :toy "Batmobile", :born "1940-04-25"}
                 {:name "Robin", :toy "Whip", :born nil}] {:index [1 2]}
    {:subset [:toy]} [{:name "Batman", :toy "Batmobile", :born "1940-04-25"}
                      {:name "Robin", :toy "Whip", :born nil}] {:index [1 2]}))

(deftest drop
  (are [l d o df]
      (m/same?
        (r/drop
          (g/data-frame
            (py/$a (np/arange 12) np/reshape [3 4])
            {:columns [:A :B :C :D]}) l d)
        (g/data-frame o df))
    [:B :C] {:axis 1} [{:A 0 :D 3} {:A 4 :D 7} {:A 8 :D 11}] {}
    [0 1] {} [{"A" 8 "B" 9 "C" 10 "D" 11}] {:index [2]}))

(deftest melt
  (are [d o df]
      (m/same?
        (r/melt
          (r/transpose
            (g/data-frame [[:a :b :c] [1 3 5] [2 4 6]]
                          {:columns [0 1 2]
                           :index   [:A :B :C]}))
          d)
        (g/data-frame o df))

    {} [{:variable "A", :value "a"}
        {:variable "A", :value "b"}
        {:variable "A", :value "c"}
        {:variable "B", :value 1}
        {:variable "B", :value 3}
        {:variable "B", :value 5}
        {:variable "C", :value 2}
        {:variable "C", :value 4}
        {:variable "C", :value 6}] {}

    {:id-vars [:A]
     :value-vars [:B]} [{:A "a", :variable "B", :value 1}
                        {:A "b", :variable "B", :value 3}
                        {:A "c", :variable "B", :value 5}] {:dtype np/object}))

(deftest assign
  (are [i o d]
      (m/same?
        (-> (g/data-frame [[:a 1 2] [:b 3 4] [:c 5 6]]
                          {:columns [:A :B :C]})
            (r/assign i))
        (g/data-frame o d))

    {:D 3} [{:A "a", :B 1, :C 2, :D 3}
            {:A "b", :B 3, :C 4, :D 3}
            {:A "c", :B 5, :C 6, :D 3}] {}

    {:D [1 2 3]} [{:A "a", :B 1, :C 2, :D 1}
                  {:A "b", :B 3, :C 4, :D 2}
                  {:A "c", :B 5, :C 6, :D 3}] {}

    {:D #(-> (g/subset-cols % :C)
             (m/mul 2))} [{:A "a", :B 1, :C 2, :D 4}
                          {:A "b", :B 3, :C 4, :D 8}
                          {:A "c", :B 5, :C 6, :D 12}] {}))

(deftest stack
  (is (m/same?
        (r/stack
          (g/data-frame [[0 1] [2 3]]
                        {:index [:cat :dog]
                         :columns [:weight :height]}))
        (g/series [0 1 2 3]
                  {:index [[:cat :cat :dog :dog]
                           [:weight :height :weight :height]]}))))

(deftest unstack
  (are [d o df]
      (m/same?
        (r/unstack
          (r/stack
            (g/data-frame [[1 2] [3 4]]
                          {:index   [:one :two]
                           :columns [:a :b]})) d)
        (g/data-frame o df))
    {} [{:a 1 :b 2} {:a 3 :b 4}] {:index [:one :two]}
    {:level 0} [{:one 1, :two 3} {:one 2, :two 4}] {:index [:a :b]}))

(deftest transpose
  (is (m/same?
        (r/transpose (g/data-frame [[1 2 3] [4 5 6] [7 8 9]]))
        (g/data-frame [[1 4 7] [2 5 8] [3 6 9]]))))
