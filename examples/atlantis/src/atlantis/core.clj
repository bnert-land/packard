(ns atlantis.core
  (:gen-class)
  (:require
    [packard.core :as commander]))
 
(def commands
  {:usage "atlantis [options] [command]"
   :desc  "we're going on an expedition..."
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
   :flags {:verbose?  [:v :verbose {:as   :bool
                                    :desc "print verbose"}]
           :no-print? [:n :no-print {:as :bool
                                     :desc "override verbose"}]}
   :enter (fn [_] #_(println "example enter")) ; called when entering root command
   :leave (fn [_] #_(println "example leave")) ; called when exiting root command after all sub commands handled
   :run   (fn [_] #_(println "example run"))
   :commands
   {:crew {:usage "atlantis crew [opts] [command]"
           :desc  "modify expedition crew database"
           :commands
           {:add {:usage "atlantis crew add [opts] [crew member]"
                  :desc  "add a crew member"
                  :run   #(println "added" (:argv %))}
            :del {:usage "atlantis crew del [opts] [crew member]"
                  :desc  "delete a crew member (i.e. they died)"
                  :run   #(println "removed" (:argv %))}}}
    :ships {:usage "atlantis ships [opts] [command]"
            :desc  "modify expedition ship db"
            :commands
            {:commission   {:usage "atlantis ships commission [opts] [ship name]"
                            :desc  "commission a new ship into the fleet"
                            :run #(println "commisioned ship" (:argv %))}
             :decommission {:usage "atlantis ships decommission [opts] [ship name]"
                            :desc  "decommission a ship from the fleet"
                            :run #(println "decomissioned ship" (:argv %))}
             :list         {:usage "atlantis ships list [opts]"
                            :desc  "list all ships for this expedition"
                            :run #(println "all ships..." %)}}}
    :events {:usage    "atlantis events [opts] [command]"
             :desc     "manage events of the expedition"
             :flags    {:sensitive? [:s :sensitive {:as :bool}]
                        :recipients [:r :recipient {:as :set}]}
             :commands {:list {:usage "atlantis events list [opts]"
                               :desc  "list events for this expedition"
                               :flags {:last [:l :last {:as :int :default 10
                                                        :desc "last # of entries"}]}
                               :run   #(println "last"
                                                (-> % :flags :last)
                                                "events"
                                                (:flags %)
                                                (:argv %))}}}}})

(defn -main [& args]
  (commander/exec commands args))

