# Mercury, a [JSON-RPC](https://www.jsonrpc.org/specification) library for Scala

[![](https://www.cloud.lightform.com/img/Wordmark%20Black.svg)](https://lightform.com/)

A modular [JSON-RPC 2.0](https://www.jsonrpc.org/specification) library that allows pluggable transport layers, JSON libraries, and effect/async monads.  
Developed at [Lightform](https://lightform.com/) to communicate with embedded devices where the device is acting in the server role.

This project has the following guiding goals:

* Free and open source license
* Allow for swappable transports
* Allow for swappable JSON libraries
* Be practical when transport layer details leak (eg. user ID from HTTP auth request header needs to be available to logic code but isn't in the params section of the request)
* Be usable without code generation (especially client), although we hope to eventually support code generation.

## [Getting Started](GETTING_STARTED.MD)

If something you're looking for isn't listed below, click [here](CONTRIBUTING.MD)!  
Feel free to open an issue with questions.

## Currently supported transports

* [Akka Streams](akka-stream) (WebSockets, TCP with message framing, etc)
* [MQTT](paho)

## Currently supported JSON libraries

* [Play JSON](play-json)

