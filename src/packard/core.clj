(ns packard.core
  (:require
    [clojure.string :as str]))

(defn- flag? [s]
  (or (str/starts-with? s "-")
      (str/starts-with? s "--")))

(defn- source-flag [a]
  (let [[f v] a]
    (if (str/includes? f "=")
      (let [[f vv] (str/split f #"=")]
        [f vv])
      [f v])))

(defn- take-arg-value [argv flag]
  (let [a (drop-while #(not (str/includes? % flag)) argv)]
    (if (< (count a) 1)
      nil
      (let [[f v] (source-flag a)]
        (if (flag? v)
          (throw (ex-info "missing flag arg" {:flag f}))
          v)))))

(defn- take-flag* [s l arg-vec]
  (let [sv (take-arg-value arg-vec (str "-" s))
        lv (take-arg-value arg-vec (str "--" l))]
    (or sv lv nil)))

(defn- take-flag [flag-spec arg-vec]
  (let [s  (:short flag-spec)
        l  (:long flag-spec)
        c  (or (:conv flag-spec) identity)
        r? (or (:required? flag-spec) false)
        v  (take-flag* s l arg-vec)]
    (when (and r? (not v))
      (throw (ex-info "required-flag-missing" {:cause [s l]})))
    (c v)))

(defn- assoc-flag [flags flag-spec value]
  (let [s (:short flag-spec)
        l (:long  flag-spec)]
    (cond-> flags
      s (assoc s value)
      l (assoc l value))))

(defn- gather-flags
  ([cli-spec arg-vec]
   (gather-flags {} cli-spec arg-vec))
  ([parsed cli-spec arg-vec]
   (let [f (or (:flags cli-spec) [])]
     (if-not (seq f)
       parsed
       (let [[flag-spec & flags]  (:flags cli-spec)
             [flag-value argv] (take-flag flag-spec arg-vec)]
         (recur (assoc-flag parsed flag-spec flag-value)
                (assoc cli-spec :flags flags)
                argv))))))

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
  (mapv #(format "\t%s,%s\t\t%s\n" (:short %) (:long %) (:desc %)) (:flags c)))

(defn cli-help-msg
  [cli-spec]
  (if-not (:parent cli-spec)
    (apply str
      (cond-> []
        (:usage cli-spec)
          (concat ["usage:       " (:usage cli-spec) "\n"])
        (:desc cli-spec)
          (concat ["\ndescription: " (:desc cli-spec) "\n\n"])
        (:commands cli-spec)
          (concat ["commands:\n"]
                  (cli-spec->commands cli-spec)
                  ["\n"])
        (:flags cli-spec)
          (concat ["\nflags:\n"])
        (:flags cli-spec)
          (concat (cli-spec->flags cli-spec)
                  ["\n"])))
    (recur (update (:parent cli-spec) :flags concat (:flags cli-spec)))))

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
         scmds   (merge commands {"help" {:run (print-help cli-spec)}})]
     (when-not (args positional)
       (throw (ex-info "invalid positional" {:cause positional})))
     (when run
       (run ctx))
     (when (and (first positional)
                (get scmds (first positional)))
       (recur ctx
              (assoc (get scmds (first positional))
                     :parent cli-spec)
              (rest positional)
              argv)))))

