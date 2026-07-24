# agent-native

C module: low-level CPU/RAM/disk/network reading via `/proc` and `/sys` (architecture doc,
section 3.1). Intentionally outside the Maven reactor — Maven doesn't know how to build C,
this module is compiled separately:

```
make            # produces bin/sentinel-native
make clean
```

The binary writes a JSON snapshot every 10s to `/run/sentinel/system_stats.json`
(atomic write via a temp file + `rename`). The `agent` module (Java) reads it from
`NativeStatsClient` — see that file's doc comment for why a local file was chosen over
JNI/JNA (no native crash can take the JVM down, independent debugging on both sides).

Final agent packaging (bundling the Java jar + this binary into a single install
artifact, section 5.1) is still undefined — an install script or CI step that runs
`make` and then copies the binary next to the jar is the simplest option.
