(ns packard.spec
  (:require
    [clojure.spec.alpha :as s]))

(s/def :context/command
  (s/keys :req-un [:command/usage :command/desc]))

(s/def :context/argv
  (s/coll-of any? :kind vector?))

(s/def :context/flags
  (s/map-of keyword? any?))

(s/def :command/context
  (s/keys :req-un [:context/flags :context/argv :context/command :context/state]))

(s/fdef fn-using-ctx
  :args (s/cat :ctx :command/context))

(s/def :command/enter fn?)

(s/def :command/leave fn?)

(s/def :command/run fn?)

(s/def :flag/desc string?)

(s/def :flag/default any?)

(s/def :flag/as #{:bool :double :float :int :map :set :seq :str :vec})

(s/def :flag/short (s/or :kw  keyword?
                         :str string?))

(s/def :flag/long (s/or :kw  keyword?
                        :str string?))

(s/def :command/flag
  (s/or :verbose  (s/keys :opt-un
                          [:flag/default
                           :flag/desc
                           :flag/as
                           :flag/short
                           :flag/long])
        :v-short   (s/tuple (some-fn string? keyword?))
        :v-short-o (s/tuple (some-fn string? keyword?)
                            (s/keys :opt-un
                                    [:flag/default
                                     :flag/desc
                                     :flag/as]))
        :short     (s/tuple (some-fn string? keyword? nil?)
                            (some-fn string? keyword? nil?))
        :short-o   (s/tuple (some-fn string? keyword? nil?)
                            (some-fn string? keyword? nil?)
                            (s/keys :opt-un
                                    [:flag/default
                                     :flag/desc
                                     :flag/as]))))
(s/def :command/args fn?)

(s/def :command/flags
  (s/map-of keyword? :command/flag))

(s/def :command/usage string?)

(s/def :command/desc string?)

(s/def :command/commands
  (s/map-of keyword? :cli/command))

(s/def :cli/command
  (s/keys :opt-un [:command/args
                   :command/enter
                   :command/leave
                   :command/run        ;; main entry
                   :command/flags      ;; flags for command
                   :command/usage      ;; document usage
                   :command/desc       ;; description
                   :command/commands]));; sub :cli/command

(def valid? (partial s/valid? :cli/command))
(def explain (partial s/explain :cli/command))
(def explain-data (partial s/explain-data :cli/command))
(def explain-str (partial s/explain-str :cli/command))
(def conform (partial s/conform :cli/command))

