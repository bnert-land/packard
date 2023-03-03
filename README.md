<p align="center">
  <img
    src="https://y.yarn.co/5ecac47d-b771-463f-8c80-82fdc0e7c243_text.gif"
    alt="commander..."
  >
</p>

simple, declarative cli's

## Overview
`packard` aims to be a simple approach to building command line applications
with Clojure.

Targeting ClojureScript is not supported at this time, in order to keep
the library simple (and frankly, I hate writing `cljc` files).


## Example

```clojure
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
                                                (:flags %))}}}}})

(defn -main [& args]
  (commander/exec commands args))

```

Then in cli:
```shell
$ clj -M:atlantis crew add milo thatch
example enter
example run
added [milo thatch]
example leave

$ clj -M:atlantis create del gunner #1
example enter
example run
removed [gunner #1]]
example leave

$ clj -M:atlantis -v ships list
example enter
example run
all ships... {:argv [], :flags {:verbose? true}, :command {:run #object[atlantis.core$fn__382 0x325bb9a6 atlantis.core$fn__382@325bb9a6]}, :state {}}
example leave

$ clj -M:atlantis events list -s -r rourke --recipient milo --recipient whitmore -r milo "we should try and sell the leviathan"
example enter
example run
last 10 events {:sensitive? true, :recipients #{whitmore rourke milo}, :last 10} [we should try and sell the leviathan]
example leave
```

## Installation
This will be pushed to clojars once a suitable implementation has been reached


## Features

- [x] nested commands (i.e. sub commands)
- [x] flag parsing
  - [x] bool type
  - [x] int, double, float type
  - [x] map type
  - [x] seq (set, seq, vec)
  - [x] strings
- [x] stop execution (via `packard.core/stop`)
- [x] auto `help` sub command
  - No auto `-h/--help` flag, given `-h` could be useful for different
    commands. May revisit to allow, but for now, this should be fine.
- [ ] auto `tree` sub command
  - [ ] able to print out sub command tree from any sub-command
  - [ ] able to limit depth
- [ ] auto completion generation
  - [ ] bash
  - [ ] fish
  - [ ] zsh

On thing to note, this library is not aiming for POSIX compliance or for
the `-xyz` syntax. That would only complicate the implemntation and subsequently
make cli's more complicated. Short/long or only short are good enough for
almost all instances.


## Examples
  - [basic composition (atlantis)](./examples/atlantis)
  - [http client (aech)](./examples/aech)
  - stdin streaming (not implemented)
  - http server (not implemented)


## Documentation
Will get a docs site up once library has reached a stable point w/ base features.


## Design Outline
`packard` cli's are progressively parsed via a spec on recursively nested maps.
Meaning on each invocation, a cli takes in the argv provided and builds up a context
for sub commands, parsing flags along the way.

This approach has some distinct advantages, namely that we can model a command
line a tree, which simplifies implementation tremendously.

It also has a side effect that predictable structure is enforced for the command line.
The predictable structure is that a flag for a command cannot be put in the context
of a parent command. Which also means that flags can be repeated in different
branches of the tree, allowing for more flexible definition. It also allows for
cli's to be discoverable, and users won't need to remember an entire corpus of
flags, only the flags which are associated within a certain sub command branch.

The downside of the listed approach can also be viewed as a downside. However,
I don't think it is.

