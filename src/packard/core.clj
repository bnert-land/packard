(ns packard.core
  (:require
    [clojure.string :as str]))

(defn- flag? [s]
  (if-not s
    false
    (or (str/starts-with? s "-")
        (str/starts-with? s "--"))))

(defn- source-flag [a]
  (let [[f v] a]
    (if (str/includes? f "=")
      (let [[f vv] (str/split f #"=")]
        [f vv])
      [f v])))

(defn- take-str-flag [flag argv]
  (let [a (drop-while #(not (str/starts-with? % flag)) argv)]
    (if (< (count a) 1)
      nil
      (let [[f v] (source-flag a)]
        (if (flag? v)
          (throw (ex-info "missing flag arg" {:flag f}))
          v)))))

(defn- prune-argv [flag argv]
  (let [a (drop-while #(not (str/starts-with? % flag)) argv)]
    (if (< (count a) 1)
      []
      (let [[f _] a]
        (if (str/includes? f "=")
          (drop 1 a)
          (drop 2 a))))))

(defn- take-vec-flag
  ([flag argv]
   (take-vec-flag flag argv []))
  ([flag argv result]
   (if-not (seq argv)
     result
     (let [flag-value (take-str-flag flag argv)]
       (recur flag
              (prune-argv flag argv)
              (cond-> result
                flag-value (conj flag-value)))))))

(defn- take-bool-flag [flag argv]
  (if-not (seq (drop-while #(not= % flag) argv))
    nil ; bool flag is missing
    true)) ; bool flag is present)))

(defn- gather-str-flag [spec argv]
  (let [{:keys [s l required? conv default]
         :or   {required? false
                conv      identity
                default   false}} spec
        s  (take-str-flag (str "-" s) argv)
        l  (take-str-flag (str "--" l) argv)
        v  (or s l default)]
    (when (and required? (nil? s) (nil? l))
      (throw (ex-info "missing argument" {:flag {:s s :l l}})))
    (conv v)))

(defn- gather-bool-flag [spec argv]
  (let [{:keys [s l required? conv default]
         :or   {required? false
                conv      identity}} spec
         s        (take-bool-flag (str "-" s) argv)
         l        (take-bool-flag (str "--" l) argv)
         present? (or s l)]
    (when (and required? (nil? s) (nil? l))
      (throw (ex-info "missing bool flag" {:flag {:s s :l l}})))
    (conv
      (if present?
        (not default)
        default))))

(defn- gather-vec-flag [spec argv]
  (let [{:keys [s l required? conv]
         :or   {required? false
                conv      identity}} spec
        sv (take-vec-flag (str "-" s) argv)
        lv (take-vec-flag (str "--" s) argv)
        vv (into [] (concat (or sv []) (or lv [])))]
    (when (and required? (empty? vv))
      (throw (ex-info "missing arguments" {:flag {:s s :l l}})))
    (conv vv)))

(defn- gather-set-flag [spec argv]
  (into #{} (gather-vec-flag spec argv)))

(defn- gather-int-flag [spec argv]
  (Integer. (gather-str-flag spec argv)))

(defn- take-flag [flag-spec argv]
  (let [kind (get flag-spec :as :str)]
    (case kind
      :vec (gather-vec-flag flag-spec argv)
      :set (gather-set-flag flag-spec argv)
      :int (gather-int-flag flag-spec argv)
      :bool (gather-bool-flag flag-spec argv)
      (gather-str-flag flag-spec argv))))

(defn- flag-map-w-opts [s l desc & {:as opts}]
  (cond-> {}
    s    (assoc :s (str s))
    l    (assoc :l (str l))
    desc (assoc :desc desc)
    opts (merge opts)))

(defn- flag-vec->flag-map
  [k v]
  (case (count v)
    0 (flag-map-w-opts (name k)  nil       nil)
    1 (flag-map-w-opts (nth v 0) nil       nil)
    2 (flag-map-w-opts (nth v 0) nil       (nth v 1))
    3 (flag-map-w-opts (nth v 0) (nth v 1) (nth v 2))
    (apply flag-map-w-opts v)))

(defn- gather-flags
  ([cli-spec arg-vec]
   (gather-flags {} cli-spec arg-vec))
  ([parsed cli-spec arg-vec]
   (let [f (or (:flags cli-spec) {})]
     (if-not (seq f)
       parsed
       (let [oflags               (into (sorted-map) f)
             [flag-key flag-spec] (first oflags)
             rest-flags           (rest oflags)
             flag-spec            (flag-vec->flag-map flag-key flag-spec)
             flag-value           (take-flag flag-spec arg-vec)]
         (recur (assoc parsed flag-key flag-value)
                (assoc cli-spec :flags rest-flags)
                arg-vec))))))

(defn- eq-flag? [s]
  (and (flag? s)
       (str/includes? s "=")))

(defn- strip-all-flags [arg-vec]
  (loop [av arg-vec
         rs []]
    (if-not (seq av)
      rs
      (let [f?  (flag? (first av))
            ef? (eq-flag? (first av))]
        ; assuming no '=" seperator for now
        (recur (if (and f? (not ef?)) (drop 2 av) (rest av))
               (cond-> rs (not f?) (conj (first av))))))))

(defn- ->context [ctx flags args]
  (-> ctx
      (assoc :args args)
      (update :flags merge flags)))

(defn cli-spec->commands [c]
  (mapv #(format "\t%s\n" (nth % 0)) (:commands c)))

(defn cli-spec->flags [c]
  (->> (mapv (fn [[k v]] (flag-vec->flag-map k v)) (:flags c))
       (mapv #(format "\t-%s,--%s\t\t%s\n" (:s %) (:l %) (:desc %)))))

(defn cli-help-msg
  ([cli-spec]
   (cli-help-msg cli-spec cli-spec))
  ([cli-spec leaf-spec]
   (if-not (:parent cli-spec)
     (apply str
       (cond-> []
         (:usage leaf-spec)
           (concat ["usage:       " (:usage leaf-spec) "\n"])
         (:desc leaf-spec)
           (concat ["\ndescription: " (:desc leaf-spec) "\n\n"])
         (:commands leaf-spec)
           (concat ["commands:\n"]
                   (cli-spec->commands leaf-spec)
                   ["\n"])
         (:flags cli-spec)
           (concat ["\nflags:\n"])
         (:flags cli-spec)
           (concat (cli-spec->flags cli-spec)
                   ["\n"])))
     (recur (update (:parent cli-spec) :flags concat (:flags cli-spec))
            leaf-spec))))

(defn- print-help [cli-spec]
  (fn [_]
    (println (cli-help-msg cli-spec))))

(defn exec
  ([cli-spec args]
   (let [argv (if (string? args) (str/split args #" ") args)]
     (exec {} cli-spec (strip-all-flags argv) argv)))
  ([ctx cli-spec positional argv]
   (let [{:keys [args run commands]
          :or   {args     (constantly true)
                 commands {}}} cli-spec
         ; auto include help subcommand
         flags   (gather-flags cli-spec argv)
         ctx     (->context ctx flags (vec positional))
         scmds   (merge commands {"help" {:run (print-help cli-spec)}})
         help?   (= "help" (first positional))]
     (when-not (args positional)
       (throw (ex-info "invalid positional" {:cause positional})))
     ; ensure the sub-command which specifies help doesn't run
     ; its handler function.
     (when (and run (not help?))
       (run ctx))
     (when (and (first positional)
                (get scmds (first positional)))
       (recur ctx
              (assoc (get scmds (first positional))
                     :parent cli-spec)
              (rest positional)
              argv)))))

