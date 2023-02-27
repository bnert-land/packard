## aech (http client) example

Example `GET` request to httpbin:
```bash
$ clj -M:aech GET https://httpbin.org/get -q msg:echo -h accept:application/json
```

Example `POST` request to httpbin:
```bash
$ clj -M:aech POST https://httpbin.org/post '{:data [{:edn "is better"} {:edn "agrees"}]}'
```
*Note*: the body is the first argument is an edn data structure, which is
converted to json to be sent to httpbin

### GraalVM Compilation
Requires you have the [`native-image`](https://www.graalvm.org/22.0/reference-manual/native-image/) binary on your machine.
```bash
$ clj -e "(compile 'aech.core)" \
&& clj -M:uberjar --main-class aech.core \
&& mkdir -p target/bin/ \
&& native-image -jar target/aech.jar --enable-http --enable-https --no-fallback -o target/bin/aech
```
