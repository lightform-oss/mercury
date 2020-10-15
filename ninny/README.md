# ninny support for JSON-RPC

Usage:

1. `import com.lightform.mercury.json.ninny._`
2. Any time a `Reader[JsValue, A]` or `Writer[JsValue, A]` is needed as an implicit parameter, just ensure you have a `FromJson[A]` or `ToJson[A]` in implicit scope.
    `toJsonReader` and `fromJsonWriter` imported from `NinnySupport` will do the conversions for you.
