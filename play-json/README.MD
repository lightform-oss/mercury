# Play JSON support for JSON-RPC

Usage:

1. `import com.lightform.mercury.json.playjson._`
2. Any time a `Reader[JsValue, A]` or `Writer[JsValue, A]` is needed as an implicit parameter, just ensure you have a `Reads[A]` or `Writes[A]` in implicit scope.  
   `readsReader` and `writesWriter` defined in `PlayJsonDefinitions` will do the conversions for you.
