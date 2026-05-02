# psyclight

Zigbee light controller. Supervises mosquitto + zigbee2mqtt and exposes a
web UI for pairing (permit_join + touchlink) and control.

## Build

    nix-build -A psyclight

The result is `result/bin/psyclight`.

## Run

    PSYCLIGHT_HTTP_HOST=127.0.0.1 \
    PSYCLIGHT_HTTP_PORT=8080 \
    PSYCLIGHT_STATE_DIR=./state \
    PSYCLIGHT_RUNTIME_DIR=./runtime \
    result/bin/psyclight

First boot lands on `/adapter` — pick adapter type and serial port, save,
and zigbee2mqtt starts. The app spawns `mosquitto` and `zigbee2mqtt`
from `$PATH`; override with `PSYCLIGHT_MOSQUITTO_BIN` and
`PSYCLIGHT_ZIGBEE2MQTT_BIN`.

## Dev

    direnv allow
    clj -M:dev

REPL helpers in `dev/user.clj`: `(go)`, `(reset)`, `(stop)`. State and
runtime dirs default to `./state` and `./runtime`.

## Deploy (NixOS)

    imports = [ (import ./psyclight {}).nixosModules.default ];
    services.psyclight.enable = true;

Brings up the whole stack as one systemd unit. `StateDirectory` at
`/var/lib/psyclight`, `RuntimeDirectory` at `/run/psyclight`. The
service user gets `dialout` for serial-port access; override with
`services.psyclight.extraGroups`.

## Stack

Clojure · com.stuartsierra/component · ring-jetty9 · reitit + muuntaja
· chassis · Datastar v1 · paho mqtt v3 · next.jdbc + sqlite · clj-nix.
