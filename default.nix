# Entry point for consumers (NixOS hosts, dev shell consumers, etc.) that
# want a built psyclight binary.
#
# `system` is threaded explicitly so a host config evaluated on one
# architecture can request a derivation built for another (e.g. building
# x86_64-linux deploys from an aarch64 dev machine).
let
  npins = import ./npins;
  loadFlake = flakeCompat: src: import flakeCompat { inherit src; };
  overlay = import ./overlay.nix;
  mkPackages = pkgs: overlay pkgs pkgs;
in
{
  sources ? npins,
  nixpkgs ? sources.nixpkgs,
  system ? builtins.currentSystem,
  # External deps — one arg each, defaulting to this project's own pins.
  # (`clj-nix` is the clj-nix flake source; `flake-compat` loads it.)
  clj-nix ? sources.clj-nix,
  flake-compat ? sources.flake-compat,
  pkgs ? import nixpkgs {
    inherit system;
    overlays = [ ((loadFlake flake-compat clj-nix).outputs).overlays.default ];
  },
  ...
}:
let
  # Body alias for the pin: `clj-nix` is the flake source, cljNix its loaded
  # output set (previously the local was also called `clj-nix`).
  cljNix = (loadFlake flake-compat clj-nix).outputs;
  # ensure clj-nix + project overlay are present even when pkgs is injected
  finalPkgs = (pkgs.extend cljNix.overlays.default).extend overlay;
in
{
  packages = mkPackages finalPkgs;
  inherit overlay;
  shell = finalPkgs.callPackage ./nix/shell.nix { };
  default = finalPkgs.psyclight;
  nixosModules = {
    psyclight = import ./nix/module.nix;
    default = import ./nix/module.nix;
  };
}
