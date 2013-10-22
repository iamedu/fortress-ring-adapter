# fortress-ring-adapter

Fortress is a set of libraries to start building a clojure app with 
a complete infrastructure.

It is very heavily opinionated, it has a lot of functionality (hopefully
put together in a simple way)

This library is a ring adapter for netty 4, with the following properties:

* Reduced GC overhead (https://blog.twitter.com/2013/netty-4-at-twitter-reduced-gc-overhead)
* Supports SPDY

This is heavily based on Rally Software's netty adapter. (check it out here https://github.com/RallySoftware/netty-ring-adapter)

## Usage

FIXME, please do!

## TODO

We need to implement a few things:

* Websockets
* Keep keepalive with zero-copy files (works with ssl and spdy but not with plain old http)

## License

Copyright © 2013 Eduardo Díaz

Distributed under the Eclipse Public License, the same as Clojure.
