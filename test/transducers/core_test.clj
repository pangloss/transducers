(ns transducers.core-test
  (:require [clojure.test :refer :all]
            [xn.transducers :refer :all]))

(deftest test-branch
  (is (= [;; for 12:
          \1 \2 ;; (mapcat str)
          "12"  ;; (map str)
          0 1   ;; the composed one part1
          0 1 2 ;; the composed one part2
          ;; for 13:
          \1 \3
          "13"
          0 1
          0 1 2
          0 1 2 3
          ;; grouped-by only produces the result when it's completed:
          {false [12], true [13]}]
        (into [] (branch
                   (mapcat str)
                   (map str)
                   (grouped-by odd?)
                   (comp
                     (mapcat #(range 10 %))
                     (map #(- % 8))
                     (mapcat range)))
          [12 13])))

  (is (= ["2" "0" "3" "1" "4" "2" "5" "3" "6" "4" "7" "5" "8" "6" "9" "7" "10" "8" "11" "9"]
        (into [] (comp
                   (map inc)
                   (branch (map inc) (map dec))
                   (map str))
          (range 10))))

  (is (= '["0" x y z 0
           "1" x y z 1 0
           "2" x y z 2 0 1
           "3" x y z 3 0 1 2
           "4" x y z 4 0 1 2 3
           :this :is :appended
           {false [0 2 4], true [1 3]}
           ([0 2 4] [1 3])]
        (into [] (branch
                   (map str)
                   (mapcat (constantly '[x y z]))
                   (mapcat (constantly []))
                   (append [:this :is :appended])
                   (grouped-by odd?)
                   (grouped-by even? :keys? false)
                   (mapcat range))
          (range 5)))))


(deftest test-cond-branch
  (is (= '[1 (2) 3 (4) 5 (6) 7 (8) 9 (10)]
        (into []
          (comp
            (map inc)
            (cond-branch
              even? (map list)))
          (range 10))))

  (is (= '[[1] (2) [3] (4) [5] (6) [7] (8) [9] (10)]
        (into []
          (comp
            (map inc)
            (cond-branch
              even? (map list)
              odd? (map vector)))
          (range 10))))

  (is (= '["3" "3" (2) "5" "5" (4) "7" "7" (6) "9" "9" (8) "11" "11" (10)]
        (into []
          (comp
            (map inc)
            (cond-branch
              even? (map list)
              ;; else:
              (comp
                (mapcat (fn [x] [x x]))
                (map inc)
                (map inc)
                (map str))))
          (range 10)))))


(deftest test-grouped-by
  (is (= [{true
           [{:even true, :i 0}
            {:even true, :i 2}
            {:even true, :i 4}
            {:even true, :i 6}
            {:even true, :i 8}],
           false
           [{:even false, :i 1}
            {:even false, :i 3}
            {:even false, :i 5}
            {:even false, :i 7}
            {:even false, :i 9}]}]
        (into [] (comp
                   (map (fn [i] {:even (even? i) :i i}))
                   (grouped-by :even))
          (range 10))))

  (is (= [{true [0 2 4 6 8], false [1 3 5 7 9]}]
        (into [] (comp
                   (map (fn [i] {:even (even? i) :i i}))
                   (grouped-by :even :extract :i))

          (range 10))))

  (is (= [[[{:even true, :i 0}
            {:even true, :i 2}
            {:even true, :i 4}
            {:even true, :i 6}
            {:even true, :i 8}]
           [{:even false, :i 1}
            {:even false, :i 3}
            {:even false, :i 5}
            {:even false, :i 7}
            {:even false, :i 9}]]]
        (into [] (comp
                   (map (fn [i] {:even (even? i) :i i}))
                   (grouped-by :even :keys? false))
          (range 10))))

  (is (= [8 6 4 2 0
          9 7 5 3 1]
        (into [] (comp
                   (map (fn [i] {:even (even? i) :i i}))
                   (grouped-by :even :keys? false :extract :i)
                   cat
                   (map #(sort-by - %))
                   cat)
          (range 10))))

  (is (= [8 6 4 2 0
          9 7 5 3 1]
        (into [] (comp
                   (map (fn [i] {:even (even? i) :i i}))
                   (grouped-by :even :keys? false :extract :i :flat? true)
                   (map #(sort-by - %))
                   cat)
          (range 10))))

  (is (= [20 25]
        (into [] (comp
                   (map (fn [i] {:even (even? i) :i i}))
                   (grouped-by :even :keys? false
                     :extract :i
                     :flat? true
                     :on-value #(apply + %)))
          (range 10))))

  (is (= [[20 25]]
        (into [] (comp
                   (map (fn [i] {:even (even? i) :i i}))
                   (grouped-by :even :keys? false
                     :extract :i
                     :on-value #(apply + %)))
          (range 10))))


  (is (= [{true 20 false 25}]
        (into [] (comp
                   (map (fn [i] {:even (even? i) :i i}))
                   (grouped-by :even
                     :extract :i
                     :on-value #(apply + %)))
          (range 10))))

  (is (= [-5]
        (into [] (comp
                   (map (fn [i] {:even (even? i) :i i}))
                   (grouped-by :even
                     :extract :i
                     :on-value #(apply + %)
                     :on-map #(- (% true) (% false))))
          (range 10))))

  (is (= [-5]
        (into [] (comp
                   (grouped-by even?
                     :on-value #(apply + %)
                     :on-map #(- (% true) (% false))))
          (range 10)))))


(deftest test-group-count-variants
  ;; I stringify the sorted ones just to capture their sortedness in the test
  (is (= {false 5 true 4}
        (merged (group-count odd?) (range 9))))
  (is (= {3 1 2 2 1 2 0 1}
        (merged (group-count abs) (range -3 3))))
  (is (= "{0 1, 1 2, 2 2, 3 1}"
        (str (merged (sorted-group-count abs) (range -3 3)))))
  (is (= {1 #{0 1 2 -1 -2 -3}}
        (merged (group-by-count) (range -3 3))))
  (is (= "{2 #{1 2}, 1 #{0}}"
        (str (merged (group-by-count abs) (range -2 3)))))
  (is (= "{1 #{0}, 2 #{1 2}}"
        (str (merged (sorted-group-by-count abs) (range -2 3)))))
  (is (= "{1 #{0 1 -2 -1 -3 2}}"
        (str (merged (sorted-group-by-count) (range -3 3)))))
  (is (= {1 3, 2 3, 3 1, 9 5}
        (merged (group-count) [1 1 1 2 2 2 3 9 9 9 9 9]))))

(deftest test-distinct-by
  (is (= [0 1]
        (into [] (distinct-by even?) (range 10)))))
