# packard
a simple data driven cli commander  
![commander...](https://y.yarn.co/5ecac47d-b771-463f-8c80-82fdc0e7c243_text.gif)

## example
```clojure
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

(defn root-cli [{:keys [flags]}]
  (if (:version flags)
    (println "version: v0.1.0")
    (System/exit 0)))

(def cli
  {:usage     "cli [command]"
   :desc      "test cli app for packard"
   :flags     {:version ["v" "version" "cli version" :as :bool]}
   :run       root-cli
   :commands (merge greet
                    ping)})

(defn -main [& _]
  (commander/exec cli *command-line-args*))
```
The above automatically adds "help" to each sub command.

A more flushed out example is in `test/cli/core.clj`.
