# deploy/downloads

**Optional.** By default, `install.sh` fetches the agent binaries directly from GitHub
Releases (public, no auth, always the latest tag) - this directory doesn't need anything
in it for a normal setup.

Only populate this if you want to self-host the binaries instead - e.g. monitored servers
that can't reach github.com. Mounted into central-server at `/opt/sentinel/downloads` (see
`deploy/docker-compose.yml` and `WebConfig`), served at `<central>/downloads/**`. Point
install.sh at it with `--download-base=https://<central>/downloads`. Expected layout:

```
downloads/
  sentinel-agent-linux-amd64
  sentinel-agent-linux-arm64
  sentinel-native-linux-amd64
  sentinel-native-linux-arm64
```

(`install.sh` itself, the systemd units, and the config template are always served
directly from central-server's own bundled static resources - never from here.)
