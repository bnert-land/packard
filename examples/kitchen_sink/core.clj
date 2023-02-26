(ns kitchen-sink.core
  (:gen-class)
  (:require
    [clojure.string :as str]
    [packard.core :as commander]))

; -- handlers


(defn run-list [ctx]
  (println "LIST" ctx))

(defn run-install [ctx]
  (println "INSTALL" ctx))

(defn root-cli [{:keys [flags]}]
  (when (:version flags)
    (println "version: v0.1.0")
    (System/exit 0)))


; -- commands

(def list-cmd
  {"list" {:usage "packard list [flags]"
           :desc  "list items"
           :flags
           {:items ["i" "item" "an item to filter by"
                    :as      :set
                    :default #{}]

            :itemv ["I" "itemv" "an item as a vector"
                    :as      :vec
                    :default []]
            :only  ["o" "only" "list of values delimited by  ','"
                    :conv    #(str/split (or % "") #"," 16)
                    :default ""]}
           :run   run-list}})

(def install-cmd
  {"install" {:usage "packard install [package] [flags]"
              :desc  "install a package"
              :args  #(<= 1 (count %))
              :flags
              {:dry-run ["d" "dry" "don't commit install"
                         :as      :bool
                         :default false]}
              :run   run-install}})
  
(def greet-cmd
  {"greet"
   {:args #(<= 1 (count %))
    :run  (fn [{:keys [args]}]
            (println (str "greetings, " (str/join " " args))))}})

(def ping-cmd
  {"ping"
   {:args empty?
    :run  (fn [_]
            (println "PONG"))}})

(def root-flags
  {:auth    ["a" "auth" "basic auth" :default nil]
   :version ["v" "version" "cli version" :as :bool]})

(def cli
  {:usage    "cli [command]"
   :desc     "test cli app for packard"
   :flags    root-flags
   :run      root-cli
   :commands (merge list-cmd
                    install-cmd
                    greet-cmd
                    ping-cmd)})


(defn -main [& _]
  (commander/exec cli *command-line-args*))

