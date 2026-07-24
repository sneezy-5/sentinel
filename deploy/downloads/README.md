# deploy/downloads

Mounted into central-server at `/opt/sentinel/downloads` (see `deploy/docker-compose.yml`
and `WebConfig`). Served at `<central>/downloads/**` and `<central>/install.sh` - this is
what `install/install.sh` downloads from when you run it on a monitored server.

None of these files are committed to git (built binaries don't belong in source control).
You need to populate this directory yourself before `install.sh` will actually work.
Expected layout:

```
downloads/
  agent/linux/amd64/sentinel-agent          # GraalVM native binary, module `agent`
  agent/linux/arm64/sentinel-agent
  agent-native/linux/amd64/sentinel-native   # C binary, module `agent-native`
  agent-native/linux/arm64/sentinel-native
  install/
    install.sh
    monitoring-agent.service
    monitoring-agent-collector.service
    monitoring-agent.yml.template
```

See the "Deploying to production" section of the root README for exact build commands.
