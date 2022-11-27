(ns cli.core
  (:gen-class)
  (:require
    [clojure.string :as str]
    [packard.core :as commander]))

(def greet
  {"greet"
   {:args #(<= 1 (count %))
    :run  (fn [{:keys [args]}]
            (println (str "greetings, " (str/join " " args))))}})

(def ping 
  {"ping"
   {:args empty?
    :run  (fn []
            (println "PONG"))}})

(defn root-cli [_]
  (println "in root!"))

(def cli
  {:usage "cli [command]"
   :desc  "test cli app for packard"
   :flags [{:short "v"
            :long  "version"
            :desc  "shows cli version"
            :default "v0.1.0"}]
   :run   root-cli
   :commands (merge greet
                    ping)})


(defn -main [& _]
  (commander/exec cli *command-line-args*))

