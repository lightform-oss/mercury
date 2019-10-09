# Contributing

Don't go just yet! Not interested in contributing code? Skip to [requesting a new feature or module](#requesting-a-new-feature-or-module)!

There are four ways you can contribute to the project, some easier than you might expect!

### Requesting a new feature or module

This is the easiest one! 
Requesting something that doesn't exist makes it easier for contributors to prioritize new work and gives others a place to second your request.  
Just open an issue describing what you're looking for.

### Contribute a feature or bugfix on an existing module

We hope what we have so far is of a good quality. 
But everything can always be refined and improved. 
We welcome pull requests! 
If you have any questions before you get started then feel free to open an issue.

### Contribute a new module

JSON-RPC can work over a lot of different transports, 
and there are a lot of different JSON libraries in the Scala world.
If there's one that we don't currently support we'd love it if you added it.

Even if a transport is already supported, 
but uses a different ecosystem than you're looking for we'd be happy to have both to cover everyone's needs.
For example if we currently support websockets using Akka Streams and `Future`s but your code base already uses fs2 and `IO`s, then we'd welcome the addition of another websocket module.

### Write a module in your own project

It's not all about us! 
If you'd like to add support for a transport or JSON library in your own project outside of this repo then we think that's great too.
We'd be extra pleased if you opened a PR against our readme and added a link to your project so that people can find it.
