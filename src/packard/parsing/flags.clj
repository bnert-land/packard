(ns packard.parsing.flags
  (:require
    [clojure.core.match :refer [match]]
    [clojure.set :refer [union]]
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

(defn ensure-valid-flag? [arg xs]
  (when (and (flag? arg) (not (contains? xs arg)))
    (throw (ex-info "invalid flag"
                    {:cause  :invalid-flag
                     :reason (format "unrecognized flag: %s"
                                     arg)}))))

(defn ensure-flag-arg? [arg arg-meta nxt]
  (when (and (flag? arg)
             (not= :bool (:kind arg-meta))
             (or (flag? nxt) (nil? nxt)))
    (throw (ex-info "missing argument"
                    {:cause  :missing-argument
                     :reason (format "flag '%s' is missing arg of kind '%s'"
                                     arg
                                     (:kind arg-meta))}))))
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

(defn flag-map->set+meta [flag-map]
  (if-not (seq flag-map)
    {:xs #{} :md {}}
    (reduce-kv
      (fn [m _ spec]
        (let [nmd {:kind (get spec :as :str)}]
          (cond-> m
            (:short spec)
              (update :xs conj (->short (:short spec)))
            (:short spec)
              (update :md assoc (->short (:short spec)) nmd)
            (:long spec)
              (update :xs conj (->long (:long spec)))
            (:long spec)
              (update :md assoc (->long (:long spec)) nmd))))
      {:xs #{} :md {}}
      flag-map)))

(defmulti compile-flag-into-verbose
  (fn [kind _] kind))

(defmethod compile-flag-into-verbose :verbose
  [_ v]
  v)

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


; -- main interface

(defn compile-flags-specs
  "Returns a new cli map with flags compiled to the :flags/verbose form."
  [conformed-cli]
  (as-> (update conformed-cli :flags compile-flag-spec) $
    (if-not (seq (:commands conformed-cli))
      $
      (update $
              :commands
              (fn [c]
                (reduce-kv #(assoc %1 %2 (compile-flags-specs %3)) {} c))))))

(defn collect-flags
  "Collects all flag sets into a hash-map"
  [{:keys [flags commands] :as _verbose-cli}]
  (let [flags (flag-map->set+meta flags)]
    (if-not commands
      flags
      (let [flags' (reduce-kv
                     (fn [xs _ command]
                       (union xs (collect-flags command)))
                     #{}
                     commands)]
        (-> flags
            (update :xs union (:xs flags'))
            (update :md merge (:md flags')))))))

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

(defn gather
  "Collects all flags & data, and runs conversion functions to convert to
  desired types. Additionally, will gather any sequence types"
  [{:keys [flags] :as _verbose-cli-command} argv]
  (let [flag-set              (flag-map->set flags)
        flags-indexed-by-flag (index-by-flags flags)]
    (loop [argv'  argv
           result {}]
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

(defn strip-from-argv [{:keys [xs md] :as _flags+meta} argv]
  (loop [argv'  argv
         result []]
    (if-not (seq argv')
      result
      (let [arg      (first argv')]
        (if (eq-flag? arg)
          (recur (concat (parse-eq-flag arg) (rest argv'))
                 result)
          (let[nxt      (second argv')
               arg-meta (get md arg)]
            ;; Not ideal for side-effectful throw, howeverrrr, this
            ;; is a good place to do it to keep cli simple
            ;; and provide some feedback without jacking up the command
            (ensure-valid-flag? arg xs)
            (ensure-flag-arg? arg arg-meta nxt)
            (cond
              (and (flag? arg)
                   (= :bool (:kind arg-meta)))
                (recur (rest argv')
                       result)
              (flag? arg)
                (recur (vec (drop 2 argv'))
                       result)
              :else
                (recur (rest argv')
                       (conj result arg)))))))))

(comment
  (require '[packard.parsing.flags :refer :all] :reload-all)

  (drop-index ["--id" "1" "--id" "2" "-i" "3"] 0 1)
  (subvec ["--id" "1" "--id" "2" "-i" "3"] 0 2)
  (subvec ["--id" "1" "--id" "2" "-i" "3"] 2 4)
  (let [v ["--id" "1" "--id" "2" "-i" "3"]]
    (concat (subvec v 0 2) (subvec v 4))) (let [v ["--id" "1" "--id" "2" "-i" "3"]]
    (drop-index v 2 3))

  (let [v ["id" "1" "id" "2" "-i" "3"]]
    (next-flag-value-index? #{"-i"} v))

  (let [fspec {:flags {:thing {:short "t" :long "thing"}
                       :ids   {:short "i" :long "id"
                               :as :set}
                       :json  {:short "j" :long "json"
                               :as :bool}
                       :pairs {:short "kv" :as :map}
                       :list  {:short "I" :long "item"
                               :as :vec}
                       :id    {:short "ii" :long "idi"
                               :as :int}
                       :price {:short "p" :long "price"
                               :as :double}
                       :rate  {:short "r" :long "rate"
                               :as :float}}}]
    #_(index-by-flags (:flags fspec))
    (gather fspec ["sub" "command"
                   "--json"
                   "-i"     "two"
                   "-i"     "123"
                   "--id"   "345"
                   "-i"   "345"
                   "--item" "one"
                   "--item" "three"
                   "-I"     "three"
                   "-ii"    "345"
                   "-kv"    "key0:value"
                   "-kv"    "key1:value"
                   "-kv"    "key1:value"
                   "--keep" "here"
                   "-p"     "2000.145"
                   "-r"     "12.34"]))

)
