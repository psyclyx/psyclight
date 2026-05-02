let
  sources = import ./npins;
  loadFlake = src: import sources.flake-compat {inherit src;};
  clj-nix = (loadFlake sources.clj-nix).outputs;
  pkgs = import sources.nixpkgs {
    overlays = [clj-nix.overlays.default];
  };
in
  pkgs.mkShell {
    packages = [
      pkgs.deps-lock

      pkgs.clojure
      pkgs.jdk
      pkgs.clj-kondo
      pkgs.rlwrap
      pkgs.clojure-lsp

      # Runtime deps the app supervises as child processes. Putting them
      # on PATH in the dev shell lets a non-nix-built REPL launch them
      # without extra plumbing; the NixOS module passes explicit paths
      # via env vars in production.
      pkgs.mosquitto
      pkgs.zigbee2mqtt
    ];
  }
