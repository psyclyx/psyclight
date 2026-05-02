{mkCljBin, ...}:
mkCljBin {
  name = "xyz.psyclyx/psyclight";
  version = "0.1.0";

  projectSrc = ../.;
  main-ns = "xyz.psyclyx.light.main";
  buildCommand = "clj -T:build uber";
}
