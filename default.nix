# Entry point for consumers (NixOS hosts, dev shell consumers, etc.) that
# want a built psyclight binary.
#
# `system` is threaded explicitly so a host config evaluated on one
# architecture can request a derivation built for another (e.g. building
# x86_64-linux deploys from an aarch64 dev machine).
let
  npins = import ./npins;
  loadFlake = src: import npins.flake-compat { inherit src; };
  clj-nix = (loadFlake npins.clj-nix).outputs;
  overlay = import ./overlay.nix;
  mkPackages = pkgs: overlay pkgs pkgs;
in
{
  nixpkgs ? npins.nixpkgs,
  system ? builtins.currentSystem,
  pkgs ? import nixpkgs {
    inherit system;
    overlays = [ clj-nix.overlays.default ];
  },
}:
let
  # ensure clj-nix + project overlay are present even when pkgs is injected
  finalPkgs = (pkgs.extend clj-nix.overlays.default).extend overlay;
in
{
  packages = mkPackages finalPkgs;
  inherit overlay;
  shell = import ./shell.nix { pkgs = finalPkgs; };
  default = finalPkgs.psyclight;
  nixosModules = {
    psyclight = import ./nix/module.nix;
    default = import ./nix/module.nix;
  };
}
