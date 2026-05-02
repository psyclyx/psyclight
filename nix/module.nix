# NixOS module for psyclight.
#
# psyclight runs as a single systemd unit. The unit invokes our Clojure
# binary, which in turn supervises mosquitto and zigbee2mqtt as child
# processes — pointing them at config files generated under
# /run/psyclight and persistent state under /var/lib/psyclight.
#
# Adapter selection (port, type, baudrate) is application-level state,
# so the module only deals with bring-up: user, group, device access,
# and the systemd unit itself.
{
  config,
  lib,
  pkgs,
  ...
}: let
  sources = import ../npins;
  loadFlake = src: import sources.flake-compat {inherit src;};
  clj-nix = (loadFlake sources.clj-nix).outputs;

  cfg = config.services.psyclight;
in {
  options.services.psyclight = {
    enable = lib.mkEnableOption "psyclight zigbee light controller";

    package = lib.mkOption {
      type = lib.types.package;
      default = pkgs.psyclight;
      description = "psyclight binary package.";
    };

    host = lib.mkOption {
      type = lib.types.str;
      default = "127.0.0.1";
      description = "Address the HTTP UI binds to.";
    };

    port = lib.mkOption {
      type = lib.types.port;
      default = 8080;
      description = "Port the HTTP UI listens on.";
    };

    user = lib.mkOption {
      type = lib.types.str;
      default = "psyclight";
      description = "System user the service runs as.";
    };

    group = lib.mkOption {
      type = lib.types.str;
      default = "psyclight";
      description = "System group the service runs as.";
    };

    extraGroups = lib.mkOption {
      type = lib.types.listOf lib.types.str;
      default = ["dialout"];
      description = ''
        Supplementary groups for the service user. Defaults to
        `dialout`, which on most distributions owns USB-serial devices
        (the Sonoff dongles included). Override if your system uses
        `uucp` or a custom udev rule.
      '';
    };

    mqtt = {
      host = lib.mkOption {
        type = lib.types.str;
        default = "127.0.0.1";
        description = "Address the supervised mosquitto broker binds to.";
      };
      port = lib.mkOption {
        type = lib.types.port;
        default = 1883;
        description = "Port the supervised mosquitto broker listens on.";
      };
    };

    mosquittoPackage = lib.mkOption {
      type = lib.types.package;
      default = pkgs.mosquitto;
      description = "Mosquitto package supervised by psyclight.";
    };

    zigbee2mqttPackage = lib.mkOption {
      type = lib.types.package;
      default = pkgs.zigbee2mqtt;
      description = "zigbee2mqtt package supervised by psyclight.";
    };
  };

  config = lib.mkIf cfg.enable {
    nixpkgs.overlays = [
      clj-nix.overlays.default
      (import ../overlay.nix)
    ];

    users.users.${cfg.user} = {
      isSystemUser = true;
      group = cfg.group;
      home = "/var/lib/psyclight";
      extraGroups = cfg.extraGroups;
    };
    users.groups.${cfg.group} = {};

    systemd.services.psyclight = {
      description = "psyclight zigbee light controller";
      wantedBy = ["multi-user.target"];
      after = ["network.target"];

      environment = {
        PSYCLIGHT_HTTP_HOST       = cfg.host;
        PSYCLIGHT_HTTP_PORT       = toString cfg.port;
        PSYCLIGHT_MQTT_HOST       = cfg.mqtt.host;
        PSYCLIGHT_MQTT_PORT       = toString cfg.mqtt.port;
        PSYCLIGHT_STATE_DIR       = "/var/lib/psyclight";
        PSYCLIGHT_RUNTIME_DIR     = "/run/psyclight";
        PSYCLIGHT_MOSQUITTO_BIN   = "${cfg.mosquittoPackage}/bin/mosquitto";
        PSYCLIGHT_ZIGBEE2MQTT_BIN = "${cfg.zigbee2mqttPackage}/bin/zigbee2mqtt";
      };

      serviceConfig = {
        ExecStart = "${cfg.package}/bin/psyclight";
        Restart = "on-failure";
        RestartSec = 5;

        User = cfg.user;
        Group = cfg.group;
        SupplementaryGroups = cfg.extraGroups;

        StateDirectory = "psyclight";
        StateDirectoryMode = "0750";
        RuntimeDirectory = "psyclight";
        RuntimeDirectoryMode = "0750";

        # Hardening. PrivateDevices stays off because we need the
        # USB-serial dongle; group membership gates the actual device.
        NoNewPrivileges = true;
        ProtectSystem = "strict";
        ProtectHome = true;
        PrivateTmp = true;
        ProtectKernelTunables = true;
        ProtectKernelModules = true;
        ProtectControlGroups = true;
        RestrictSUIDSGID = true;
        RestrictNamespaces = true;
        LockPersonality = true;
      };
    };
  };
}
