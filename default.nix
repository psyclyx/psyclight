# Entry point for consumers (NixOS hosts, dev shell consumers, etc.) that
# want a built psyclight binary.
#
# `system` is threaded explicitly so a host config evaluated on one
# architecture can request a derivation built for another (e.g. building
# x86_64-linux deploys from an aarch64 dev machine).
{system ? builtins.currentSystem}: let
  sources = import ./npins;
  inherit (sources) nixpkgs;

  loadFlake = src: import sources.flake-compat {inherit src;};
  clj-nix = (loadFlake sources.clj-nix).outputs;

  pkgs = import nixpkgs {
    inherit system;
    overlays = [
      clj-nix.overlays.default
      (import ./overlay.nix)
    ];
  };
in {
  inherit (pkgs) psyclight;
  nixosModules = {
    psyclight = import ./nix/module.nix;
    default = import ./nix/module.nix;
  };
}
