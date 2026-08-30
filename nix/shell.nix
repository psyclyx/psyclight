# Dev shell, callPackage-style: arguments are resolved from the extended pkgs
# set (nixpkgs tools + clj-nix's `deps-lock`). Entered via direnv (`use nix -A
# shell`). `deps-lock` regenerates deps-lock.json after deps.edn changes.
{
  mkShell,
  deps-lock,
  clojure,
  jdk,
  clj-kondo,
  clojure-lsp,
  rlwrap,
  mosquitto,
  zigbee2mqtt,
}:
mkShell {
  packages = [
    deps-lock

    clojure
    jdk
    clj-kondo
    rlwrap
    clojure-lsp

    # Runtime deps the app supervises as child processes. Putting them
    # on PATH in the dev shell lets a non-nix-built REPL launch them
    # without extra plumbing; the NixOS module passes explicit paths
    # via env vars in production.
    mosquitto
    zigbee2mqtt
  ];
}
