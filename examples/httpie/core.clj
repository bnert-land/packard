(ns httpie.core
  (:gen-class)
  (:require
    [clojure.edn :as edn]
    [cheshire.core :as json]
    [clj-http.client :as http]
    [packard.core :as commander]))

(defn print-url [method]
  (fn [{:keys [argv] :as ctx}]
    (println method (nth argv 0))
    ctx))

(defn str->edn [ctx]
  (if-not (get-in ctx [:argv 1])
    ctx
    (update-in ctx [:argv 1] edn/read-string)))

(defn http-delete [ctx]
  (let [[url] (:argv ctx)
        {:keys [headers]} (:flags ctx)]
    (println
      (http/delete url headers))))

(defn http-get [{:keys [argv flags] :as _ctx}]
  (let [[url]                     argv
        {:keys [headers queries]} flags]
    (println
      (http/get url
                (cond-> headers
                  (seq queries)
                    (assoc :query-params queries))))))

(defn http-post [{:keys [argv flags] :as _ctx}]
  (let [[url body?]               argv
        {:keys [headers queries]} flags]
    (println
      (http/post url
                 (cond-> headers
                   body?
                     (assoc :body (json/encode body?))
                   body?
                     (assoc :content-type :application/json)
                   (seq queries)
                     (assoc :query-params queries))))))
 
(def commands
  {:usage       "http"
   :description "an example cli http client"
   :flags       {:basic-auth ["b" "basic"]
                 :headers    ["h" "header" {:as      :map
                                            :default {:accept "application/json"}}]
                 :queries    ["q" "query" {:as :map}]}
   :run         (fn [_] (println "ROOT"))
   :commands    {:DELETE   {:enter (print-url "DELETE")
                            :run   http-delete}
                 :GET      {:enter (print-url "GET")
                            :run   http-get}
                 #_#_:PATCH  http-patch
                 :POST     {:enter str->edn
                            :run   http-post}
                 #_#_:PUT  http-put}})

(defn -main [& args]
  (println "ARGS" args)
  (commander/exec commands args))

