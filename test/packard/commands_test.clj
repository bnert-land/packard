(ns packard.commands-test
  (:require
    [clojure.string :refer [split]]
    [clojure.test :refer [deftest is testing]]
    [packard.core :as commander]))

(def ->argv #(split % #" "))

(def run-enter-exit-noop
  {:run   (fn [_ctx] )
   :enter (fn [_ctx] )
   :exit  (fn [_ctx] )})

(defn mock-command
  [& {:keys [node-start-runners
             node-stop-runners
             root-run
             server-start-runners
             server-stop-runners]
      :or   {node-start-runners   run-enter-exit-noop
             node-stop-runners    run-enter-exit-noop
             root-run             (fn [_v] )
             server-start-runners run-enter-exit-noop
             server-stop-runners  run-enter-exit-noop}}]
  {:usage "mock"
   :flags {:basic    ["A" "basic-auth"]
           :store    ["S" "store" {:as :map}]
           :verbose? ["V" "verbose" {:as :bool}]}
   :run   root-run
   :commands
   {:node   {:usage "mock node"
             :flags {:listen  [:l :listen {:as :set}]
                     :outputs [:o :output {:as :set}]}
             :commands
             {:start node-start-runners
              :stop  node-stop-runners}}
    :server {:usage "mock server"
             :flags {:config-path [:c :config-file]}
             :commands
             {:start (merge server-start-runners
                            {:flags {:listen [:l :listen {:as :int}]}})
              :stop  server-stop-runners}}}})


(deftest exec-command
  (testing "if the lib doesn't crash on empty argv"
    (try
      (commander/exec (mock-command) [] {:re-throw? true})
      (is (true? true))
      (catch Exception e
        (println e)
        (is (false? true))))))

(deftest exec-node-start
  (testing "if the lib actually works"
    (let [seen? (atom false)]
      (commander/exec
        (mock-command
          :node-start-runners
          {:run (fn [{:keys [flags]}]
                  (swap! seen? not)
                  (is (= {:store   {:msg "hi"}
                          :basic   "basic:auth"
                          :listen  #{"tcp/443" "udp/8081"}
                          :outputs #{"https://host@9090"
                                     "unix://var/daemon.fifo"}}
                         flags)))})
        (->argv "node -A basic:auth start -l tcp/443 --listen udp/8081 -o https://host@9090 --output unix://var/daemon.fifo -S msg:hi")
        {:re-throw? true})
      (is (true? @seen?)))))

(deftest exec-server-start
  (testing "if the lib actually works"
    (let [seen? (atom false)]
      (commander/exec
        (mock-command
          :server-start-runners
          {:run (fn [{:keys [flags]}]
                  (swap! seen? not)
                  (is (= {:store   {:msg "hi"}
                          :basic   "basic:auth"
                          :listen  443}
                         flags)))})
        (->argv "server -A basic:auth start -l 443 -S msg:hi --garbage value")
        {:re-throw? true})
      (is (true? @seen?)))))

(deftest exec-node-start-premature-stop
  (testing "if the (stop) function actually works"
    (binding [commander/*stop-execution* (atom false)]
      (let [seen? (atom false)]
        (commander/exec
          (mock-command
            :root-run (fn [_] (commander/stop))
            :node-start-runners
            {:run (fn [{:keys [flags]}]
                    (swap! seen? not)
                    (is (= {:store   {:msg "hi"}
                            :basic   "basic:auth"
                            :listen  #{"tcp/443" "udp/8081"}
                            :outputs #{"https://host@9090"
                                       "unix://var/daemon.fifo"}}
                           flags)))})
          (->argv "node -A basic:auth start -l tcp/443 --listen udp/8081 -o https://host@9090 --output unix://var/daemon.fifo -S msg:hi")
          {:re-throw? true})
        (is (false? @seen?))))))

