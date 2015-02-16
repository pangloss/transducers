(ns transducers.core)

(defn xform
  "Apply a transducer to get 1 value from 1 input"
  [f x]
  ((f (fn [_ y] y)) nil x))

(defn doprocess
  "Like dorun for a transducer. Produces no intermediate sequence at all."
  [xform data]
  (transduce xform (constantly nil) nil data))

(defn grouped-by
  "A transducer that acts like (seq (group-by f coll)) or if you instantiate it
   as (grouped-by f :keys? false) then it will act like (vals (group-by f coll))"
  [f & {:keys [keys?] :or {keys? true}}]
  (fn [rf]
    (let [group (volatile! (transient (array-map)))]
      (fn
        ([] (rf))
        ([result]
         (rf
           (if keys?
             (reduce rf result (persistent! @group))
             (reduce rf result (vals (persistent! @group))))))
        ([result x]
         (vswap! group (fn [g]
                         (let [k (f x)]
                           (if-let [v (get g k)]
                             (assoc! g k (conj v x))
                             (assoc! g k [x])))))
         result)))))


(defn multiplex [& xforms]
  (if (seq xforms)
    (fn [rf]
      (let [rfs (into [] (map #(% rf)) xforms)]
        (fn
          ([] (doseq [f rfs] (f)))
          ([result]
           (reduce (fn [result f] (f result)) result rfs))
          ([result input]
           (reduce (fn [result f] (f result input)) result rfs)))))
    (map identity)))

(comment
  (into [] (comp (map inc)
                 (multiplex (map inc) (map dec))
                 (map str))
        (range 10))

  (into [] (multiplex (map str)
                      (mapcat (constantly '[x y z]))
                      (mapcat (constantly []))
                      (grouped-by odd?)
                      (grouped-by even? :keys? false)
                      (mapcat range))
        (range 5)))

(defn branched-xform
  "Will route data down one or another transducer path based on a predicate
   and merge the results."
  [pred true-xform false-xform]
  (fn [rf]
    (let [true-rf (true-xform rf)
          false-rf (false-xform rf)]
      (fn
        ([] (true-rf) (false-rf))
        ([result]
         (true-rf (false-rf result)))
        ([result input]
         (if (pred input)
           (true-rf result input)
           (false-rf result input)))))))

(comment
  (into []
        (comp (map inc)
              (branched-xform even?
                              (map list)
                              (comp (mapcat (fn [x] [x x]))
                                    (map inc)
                                    (map dec)))
              (map str))
        (range 10)))

(defn distinct-by [f]
  (let [seen (volatile! (transient #{}))]
    (filter (fn [x]
              (let [y (f x)]
                (when-not (@seen y)
                  (vswap! seen conj! y)
                  true))))))


(defn lasts-by
  "A transducer that accomplishes the following but more efficiently
   (->> coll
        (group_by f)
        (map (fn [[k vals]] (last vals))))"
  ([f]
   (fn [rf]
     (let [matches (volatile! (transient (array-map)))]
       (fn
         ([] (rf))
         ([result] (rf (reduce rf result (vals (persistent! @matches)))))
         ([result x]
          (vswap! matches assoc! (f x) x)
          result)))))
  ([f coll]
   (sequence (lasts-by f) coll)))


