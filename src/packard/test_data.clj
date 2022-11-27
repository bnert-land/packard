(ns packard.test-data
  (:require
    [clojure.string :as str]))

(defn run-root [ctx]
  (println "ROOT" ctx))

(defn run-list [ctx]
  (println "LIST" ctx))

(defn run-install [ctx]
  (println "INSTALL" ctx))

(def commands
  {:usage    "packard [command]"
   :desc     "test cli spec"
   :flags    [{:short   "a"
               :long    "auth"
               :desc    "basic auth params"
               :default nil}]
   :run      run-root
   :commands {"list" {:usage "packard list [flags]"
                      :flags [{:short "o" :long  "only"
                               :desc  "only is a list of ids"
                               :conv  #(str/split (or % "") #"," 16)}]
                      :run   run-list}
              "install" {:usage "packard install [package] [flags]"
                         :args  #(<= 1 (count %))
                         :flags [{:short "d" :long "dry"
                                  :desc    "only perform a dry-run"
                                  :default false}]
                         :run   run-install}}})

