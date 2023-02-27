# packard
<img src="https://y.yarn.co/5ecac47d-b771-463f-8c80-82fdc0e7c243_text.gif"
     style="margin: 0 auto;"
     alt="commander...">

simple, declarative cli's

## Overview
`packard` aims to be a simple approach to building command line applications
with Clojure.

Targeting ClojureScript is not supported at this time, in order to keep
the library simple (and frankly, I hate writing `cljc` files).


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


## Installation
This will be pushed to clojars once a suitable implementation has been reached


## Documentation
Will get a docs site up once library has reached a stable point.
