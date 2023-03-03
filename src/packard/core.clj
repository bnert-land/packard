(ns packard.core
  (:require
    [packard.parsing.flags :refer [->short ->long] :as p.p.flags]
    [packard.spec :as p.spec]))

(def ^:dynamic *stop-execution* (atom false))

(defn- conformed? [cli-spec]
  (true? (:conformed? (meta cli-spec))))

(defn- maybe-conform [cli-spec]
  (if (conformed? cli-spec)
    cli-spec
    (with-meta
      (p.p.flags/compile-flags-specs (p.spec/conform cli-spec))
      {:conformed? true})))

(defn- render-help-vec [help-vec]
  (loop [result    ""
         [h' & r'] help-vec]
    (if-not h'
      result
      (cond
        (string? h')
          (recur (str result h')
                 r')
        (vector? h')
          (recur (reduce str result h') r')))))

(defn- flag->str [v]
  (cond 
    (and (:short v) (not (:long v)))
      (format "  %-24s%s\n" (->short (:short v)) (:desc v))
    (and (not (:short v)) (:long v))
      (format "  %-24s%s\n" (->long (:long v)) (:desc v))
    :else
     (format "  %-24s%s\n"
             (str (->short (:short v)) ","
                  (->long (:long v)))
             (or (:desc v) ""))))

(defn- cli-spec->help
  [{:keys [desc usage flags commands] :as _conformed-cli} context]
  (render-help-vec
    (cond-> []
      usage
        (conj (format "usage:\n  %s\n\n" usage))
      desc
        (conj (format "description:\n  %s\n\n" desc))
      (:flags context)
        (conj "inherited flags:\n")
      (:flags context)
        (conj (mapv flag->str (vals (:flags context))))
      (:flags context)
        (conj "\n")
      flags
        (conj (str "flags:\n"))
      flags
        (conj (mapv flag->str (vals flags)))
      true
        (conj (str "\ncommands:\n"))
      true
        (conj [(format "  %-24s%s\n" "help" "this command")])
      commands
        (conj (mapv (fn [[k v]]
                      (format "  %-24s%s\n"
                              (name k)
                              (or (:desc v) ""))) commands)))))

(defn- exec-for [cli-spec argv context seen-flags]
  (if @*stop-execution*
    nil
    (let [{:keys [commands enter leave run]
         :or   {enter identity
                leave identity
                run   identity}
         :as   conformed} (maybe-conform cli-spec)
        [argv' flags']    (p.p.flags/gather conformed argv)
        context'      (-> context
                          (assoc :argv argv')
                          (update :flags merge flags')
                          (assoc :command conformed))
        next-command-kw (keyword (first argv'))
        next-command? (get commands (keyword (first argv')))]
      (if (= :help next-command-kw)
        (println (cli-spec->help cli-spec {:flags seen-flags}))
        (do
          (enter context')
          (run context')
          ;; should this throw?
          (when (and (seq commands) (not next-command?))
            (throw (ex-info "unrecognized command"
                            {:cause (format "'%s' is not a command" (first argv'))})))
          (when next-command?
            (if-not (conformed? conformed)
              (throw (ex-info "shouldn't happen" {}))
              (exec-for (with-meta next-command? {:conformed? true})
                        (vec (rest argv'))
                        context'
                        (into seen-flags (:flags conformed)))))
          (leave context'))))))

(defn stop []
  (reset! *stop-execution* true))

(defn exec
  "Execute either a pre-compiled (or non compiled) cli spec"
  ([cli-spec]
   (exec cli-spec *command-line-args*))
  ([cli-spec argv]
   (exec cli-spec argv {}))
  ([cli-spec argv & {:keys [re-throw?]
                     :or   {re-throw? false}}]
   (try
     (when-not (p.spec/valid? cli-spec)
       (throw (ex-info "invalid cli"
                       {:cause (p.spec/explain-str cli-spec)})))
     (let [conformed (maybe-conform cli-spec)]
       (if-not (seq argv)
         (println (cli-spec->help conformed nil))
         (exec-for conformed
                   argv
                   {:argv argv :flags {} :command {} :state {}}
                   {})))
     (catch clojure.lang.ExceptionInfo e
       (when-not re-throw?
         (binding [*out* *err*]
           (println (ex-message e))
           (as-> (ex-data e) $
             (println (if (:cause $) (:cause $) $)))))
       (when re-throw?
         (throw e)))
     (catch Exception e
       (when-not re-throw?
         (binding [*out* *err*]
           (println e)))
       (when re-throw?
         (throw e))))))

