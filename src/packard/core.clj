(ns packard.core
  (:require
    [packard.parsing.flags :as p.p.flags]
    [packard.spec :as p.spec]))

(defn exec-cmd-chain [compiled-cli-spec commands argv]
  (if-not (seq commands)
    (println "no command provided.") ;; i.e. print help
    (loop [argv'     argv
           spec'     compiled-cli-spec
           commands' commands
           context   {:flags {} :argv argv :command nil}]
      (if-not (seq spec')
        nil
        (let [cmd (get-in spec' [:commands (keyword (first commands'))])]
          (if-not cmd
            (throw (ex-info "invalid command" {:cause  :invalid-command
                                               :reason (name cmd)}))
            (let [{:keys [enter exit run]
                   :or   {enter identity
                          exit  identity
                          run   identity}} cmd
                  [argv'' flags'] (p.p.flags/gather spec' argv')
                  context'        (-> context
                                      (update :flags merge flags')
                                      (assoc :argv (vec (rest commands')))
                                      (assoc :command cmd))]
                (-> (enter context') run exit)
                (recur argv''
                       (get cmd :commands)
                       (vec (rest commands'))
                       context'))))))))

(defn exec
  ([cli-spec]
   (exec cli-spec *command-line-args*))
  ([cli-spec argv]
   (when-not (p.spec/valid? cli-spec)
     (binding [*out* *err*]
       (p.spec/explain cli-spec))
     (System/exit 1))
   (try
     (let [conformed (p.spec/conform cli-spec)
           compiled  (p.p.flags/compile-flags-specs conformed)
           commands  (-> compiled
                         p.p.flags/collect-flags
                         (p.p.flags/strip-from-argv argv))]
       (exec-cmd-chain compiled commands argv))
     (catch Exception e
       (binding [*out* *err*]
         (println e)
         (let [ed (ex-data e)]
           (println (or (:reason ed) (str "failed to execute command: " (.toString e))))
           (System/exit 2)))))))

(comment
  (require '[packard.core :refer :all] :reload-all)
  (require '[packard.parsing.flags :as p.p.f] :reload-all)
  (require '[packard.spec :as p.s] :reload-all)
  (require '[clojure.string :as str])

  (def fspec0
    (p.s/conform {:usage "thing"
                  :desc  "this is a cli thing"
                  :flags {:xyz        [:xyz]
                          :daemonize? ["d" "daemonize" {:as :bool}]
                          :abc        ["a" "abc" {:as :int}]}
                  :commands
                  {:list {:flags {:all ["a" "all" {:as   :bool
                                                   :desc "list all ids"}]}}}}))

  (p.p.f/compile-flags-specs fspec0)
  (try
    (-> fspec0
        p.p.f/compile-flags-specs
        p.p.f/collect-flags
        (p.p.f/strip-from-argv (str/split "-xyz abc --abc=123 list --all -d 1 2 3" #" ")))
    (catch Exception e
      (println (ex-data e))))


  (p.s/valid?
    {:usage "thing"
     :desc  "this is a cli thing"
     :flags {:xyz   [:x :xyz]
             :abc   ["a" "abc" {:as :int}]}})

  (p.s/conform
    {:usage "thing"
     :desc  "this is a cli thing"
     :flags {:xyz   [:xyz]
             :abc   ["a" "abc" {:as :int}]}})
)
