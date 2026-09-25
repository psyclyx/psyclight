{lib, mkCljBin, ...}:
mkCljBin {
  name = "xyz.psyclyx/psyclight";
  version = "0.1.0";

  # Only the files the uberjar build consumes: the deps.edn basis, the
  # sources and resources, plus the deps-lock.json that mkCljBin reads
  # through projectSrc. Entry points (default.nix, overlay.nix, packages/),
  # npins/, nix/, and dev/ are not package inputs, so editing them must not
  # churn the source hash.
  projectSrc = builtins.path {
    # Preserve the store path name the old `projectSrc = ../.` copy had.
    name = "psyclight";
    path = lib.fileset.toSource {
      root = ../.;
      fileset = lib.fileset.unions [
        ../deps.edn
        ../deps-lock.json
        ../resources
        ../src
      ];
    };
  };
  main-ns = "xyz.psyclyx.light.main";
}
