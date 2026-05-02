final: _: let
  packages = import ./packages;
in
  builtins.mapAttrs (_: pkg: final.callPackage pkg {}) packages
