# Mercury, a [JSON-RPC](https://www.jsonrpc.org/specification) library for Scala

[![](https://www.cloud.lightform.com/img/Wordmark%20Black.svg)](https://lightform.com/)

[![Travis (.com)](https://img.shields.io/travis/com/lightform-oss/mercury?style=for-the-badge)](https://travis-ci.com/lightform-oss/mercury)
[![Codacy grade](https://img.shields.io/codacy/grade/125cc8f2617c41a5b8730c6818af3640?style=for-the-badge)](https://app.codacy.com/manual/lightform/mercury)
[![GitHub](https://img.shields.io/github/license/lightform-oss/mercury?style=for-the-badge)](LICENCE.txt)
[![Maven Central](https://img.shields.io/maven-central/v/com.lightform/mercury_2.13?style=for-the-badge)](https://mvnrepository.com/artifact/com.lightform/mercury)

A modular [JSON-RPC 2.0](https://www.jsonrpc.org/specification) library that allows pluggable transport layers, JSON libraries, and effect/async monads.  
Developed at [Lightform](https://lightform.com/) to communicate with embedded devices where the device is acting in the server role.

This project has the following guiding goals:

* Free and open source license
* Allow for swappable transports
* Allow for swappable JSON libraries
* Be practical when transport layer details leak (eg. user ID from HTTP auth request header needs to be available to logic code but isn't in the params section of the request)
* Be usable without code generation (especially client), although we hope to eventually support code generation.

## [Getting Started](GETTING_STARTED.md)

If something you're looking for isn't listed below, click [here](CONTRIBUTING.md)!  
Feel free to open an issue with questions.

## Currently supported transports

* [Streams](akka-stream) (WebSockets, TCP with message framing, etc)
* [HTTP](akka-http)
* [MQTT](paho)

## Currently supported JSON libraries

* [Play JSON](play-json)
