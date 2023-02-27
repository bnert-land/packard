(ns packard.parsing.flags
  (:require
    [clojure.string :refer [includes? split starts-with?]]))

(defn ->short [s] (str "-" s))
(defn ->long [s] (str "--" s))

(defn drop-index [v & indicies]
  (let [indicies' (apply hash-set indicies)]
    (->> v
         (map-indexed #(vec [%1 %2]))
         vec
         (filterv #(not (contains? indicies' (nth % 0))))
         (mapv last))))

(defn dparse [d]
  (Double/parseDouble d))

(defn fparse [f]
  (Float/parseFloat f))

(defn iparse [i]
  (.intValue (fparse i)))

(defn str->kv [s]
  (-> (split s #":" 2)
      (update 0 keyword)))

(defn update-fn [flag-value as]
  (case as
    :double (constantly (dparse flag-value))
    :float  (constantly (fparse flag-value))
    :int    (constantly (iparse flag-value))
    :map    (fn [m] (apply assoc (cons (or m {}) (str->kv flag-value))))
    :set    (fn [xs] (conj (or xs #{}) flag-value))
    :seq    (fn [xs] (conj (or xs '()) flag-value))
    :vec    (fn [xs] (conj (or xs []) flag-value))
    (constantly flag-value)))

(defn assoc-flag [result flag-value flag-opts]
  (let [as (:as flag-opts)]
    (update result (:name flag-opts)
            (update-fn flag-value as))))

(defn flag? [s]
  (when s
    (or (starts-with? s "--")
        (starts-with? s "-"))))

(defn eq-flag? [s]
  (when s
    (and (flag? s)
         (includes? s "="))))

(defn parse-eq-flag [s]
  (when s
    (split s #"=" 2)))

(defn flag-map->set [flag-map]
  (if-not (seq flag-map)
    #{}
    (reduce-kv
      (fn [xs _ spec]
        (cond-> xs
          (:short spec) (conj (->short (:short spec)))
          (:long spec) (conj (->long (:long spec)))))
      #{}
      flag-map)))

(defn index-by-flags [flag-map]
  (reduce-kv
    (fn [m k v]
      (cond-> m
        (:short v)
          (assoc (->short (:short v)) (assoc v :name k))
        (:long v)
          (assoc (->long (:long v)) (assoc v :name k))))
    {}
    flag-map))

(defmulti compile-flag-into-verbose
  (fn [kind _] kind))

(defmethod compile-flag-into-verbose :verbose
  [_ v]
  (let [conv #(case (nth % 0)
                :kw (name (nth % 1))
                (nth % 1))]
    (cond-> v
      (:short v)
        (update :short conv)
      (:long v)
        (update :long conv))))

(defmethod compile-flag-into-verbose :v-short
  [_ [flag]]
  {:short (name flag)})

(defmethod compile-flag-into-verbose :v-short-o
  [_ [flag opts]]
  (assoc opts :short (name flag)))

(defmethod compile-flag-into-verbose :short
  [_ [flag-short flag-long]]
  {:long (name flag-long) :short (name flag-short)})

(defmethod compile-flag-into-verbose :short-o
  [_ [flag-short flag-long opts]]
  (-> opts
      (assoc :long (name flag-long))
      (assoc :short (name flag-short))))

(defn compile-flag-spec [flag-spec]
  (reduce-kv
    (fn [m k v]
      (assoc m k (apply compile-flag-into-verbose v)))
    {}
    flag-spec))

(defn next-flag-value-index?
  [flag-set argv]
  (loop [argv' argv
         index 0]
    (if-not (seq argv')
      (if (= (count argv) index)
        nil
        index)
      (if (contains? flag-set (first argv'))
        index
        (recur (rest argv') (inc index))))))

(defn <-defaults [flags]
  (reduce-kv
    (fn [m k v]
      (if (:default v)
        (assoc m k (:default v))
        m))
    {}
    flags))

; -- module interface

(defn compile-flags-specs
  "Returns an updated cli map with flags compiled to the :flags/verbose form."
  [conformed-cli]
  (if-not (:flags conformed-cli)
    conformed-cli
    (as-> (update conformed-cli :flags compile-flag-spec) $
      (if-not (seq (:commands conformed-cli))
        $
        (update $
                :commands
                (fn [c]
                  (reduce-kv #(assoc %1 %2 (compile-flags-specs %3)) {} c)))))))

(defn gather
  "Collects all flags & data, and runs conversion functions to convert to
  desired types. Additionally, will gather any sequence types"
  [{:keys [flags] :as _verbose-cli-command} argv]
  (let [flag-set              (flag-map->set flags)
        flags-indexed-by-flag (index-by-flags flags)]
    (loop [argv'  argv
           result (<-defaults flags)]
      (let [flag-index (next-flag-value-index? flag-set argv')]
        (if-not flag-index
          [argv' result]
          (let [flag (nth argv' flag-index)]
            (if (eq-flag? flag)
              (recur (concat (parse-eq-flag flag) (rest argv'))
                     result)
              (let [flag-opts (get flags-indexed-by-flag flag)]
                (if (= :bool (:as flag-opts))
                  (recur (drop-index argv' flag-index)
                         (assoc result (:name flag-opts)
                                       (not (get flag-opts :default false))))
                  (recur (drop-index argv' flag-index (inc flag-index))
                         (assoc-flag result (nth argv' (inc flag-index)) flag-opts)))))))))))

