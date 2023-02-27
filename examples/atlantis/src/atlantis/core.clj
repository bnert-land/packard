(ns atlantis.core
  (:gen-class)
  (:require
    [packard.core :as commander]))
 
(def commands
  {:usage "example"
   :desc  "an example cli http client"
   ; Flags can be one of:
   ;
   ;  :bool
   ;  :double
   ;  :float
   ;  :int
   ;  :map  -> `-m key:value --map key:value` -> {:key "value"}
   ;  :set  -> `-s value -s other -s value` -> #{"value" "other"}
   ;  :seq  ->               ""             -> '("value" "other" "value")
   ;  :str
   ;  :vec  -> `-v value -v other -s value` -> ["value" "other" "value"]
   ;
   ; their "types" are signaled using the :as keyword in an optional map.
   ; defualt type is a string (:str)
   :flags {:verbose? [:v :verbose {:as :bool}]}
   :enter (fn [_] (println "example enter")) ; called when entering root command
   :leave (fn [_] (println "example leave")) ; called when exiting root command after all sub commands handled
   :run   (fn [_] (println "example run"))
   :commands
   {:crew {:commands
           {:add {:run #(println "added" (:argv %))}
            :del {:run #(println "removed" (:argv %))}}}
    :ships {:commands
            {:commission    {:run #(println "commisioned ship" (:argv %))}
             :decommission {:run #(println "decomissioned ship" (:argv %))}
             :list         {:run #(println "all ships..." %)}}}
    :events {:flags    {:sensitive? [:s :sensitive {:as :bool}]
                        :recipients [:r :recipient {:as :set}]}
             :commands {:list {:flags {:last [:l :last {:as :int :default 10}]}
                               :run   #(println "last"
                                                (-> % :flags :last)
                                                "events"
                                                (:flags %)
                                                (:argv %))}}}}})

(defn -main [& args]
  (commander/exec commands args))

