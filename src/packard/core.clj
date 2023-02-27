(ns packard.core
  (:require
    [clojure.spec.alpha :as s]
    [packard.parsing.flags :as p.p.flags]
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

(defn- exec-for [cli-spec argv context]
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
        next-command? (get commands (keyword (first argv')))]
      (enter context')
      (run context')
      ;; should this throw?
      (when (and (seq commands) (not next-command?))
        (throw (ex-info "unrecognized command"
                        {:cause (format "unrecognized command: '%s'" (first argv'))})))
      (when next-command?
        (if-not (conformed? conformed)
          (throw (ex-info "shouldn't happen" {}))
          (exec-for (with-meta next-command? {:conformed? true})
                    (vec (rest argv'))
                    context')))
      (leave context'))))

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
     (if-not (seq argv)
       nil ; todo: add help
       (exec-for cli-spec argv {:argv argv :flags {} :command {} :state {}}))
     (catch clojure.lang.ExceptionInfo e
       (when-not re-throw?
         (binding [*out* *err*]
           (println (ex-message e))
           (as-> (ex-data e) $
             (printf (if (:cause $) (:cause $) $)))))
       (when re-throw?
         (throw e)))
     (catch Exception e
       (when-not re-throw?
         (binding [*out* *err*]
           (println e)))
       (when re-throw?
         (throw e))))))

