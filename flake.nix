{
  # The homelab slot (homelab.slots.komga) runs `bin/komga` from #default and
  # deploys every merge to s3-archive. The server is the one the s3-archive
  # image workflow publishes: to ship a new build, wait for that image, then
  # set image.imageDigest, image.hash and image.finalImageTag below to it
  # (nix-prefetch-docker --image-name ghcr.io/alexcatdad/komga --image-digest
  # <digest> --arch amd64 --os linux) and merge.
  description = "Komga s3-archive for the homelab slot: the image's server on a Nix JRE";

  # The homelab host's nixpkgs, so the JRE and libraries are already in its store.
  inputs.nixpkgs.url = "https://releases.nixos.org/nixos/26.05/nixos-26.05.12400.da6dc0e7b260/nixexprs.tar.xz";

  outputs = { self, nixpkgs }:
    let
      pkgs = nixpkgs.legacyPackages.x86_64-linux;

      image = pkgs.dockerTools.pullImage {
        imageName = "ghcr.io/alexcatdad/komga";
        imageDigest = "sha256:721bede3bac928b00461fd7434acf6529fd4c981e8539c8aa9ccea1a26859730";
        hash = "sha256-boeaKi+1WN40tDo2Jz9fQDOsM4zBLGcZPejbu9P/OHY=";
        finalImageTag = "s3-archive-af607e9";
        os = "linux";
        arch = "amd64";
      };

      # /app (application.jar and lib/) from the image layers in order,
      # applying their whiteouts; nothing else of the image is used.
      app = pkgs.runCommand "komga-app" { nativeBuildInputs = [ pkgs.jq pkgs.gnutar pkgs.gzip ]; } ''
        mkdir image root
        tar -xf ${image} -C image
        for layer in $(jq -r '.[0].Layers[]' image/manifest.json); do
          tar -tf "image/$layer" | grep -E '^(\./)?app(/|$)' > members || true
          [ -s members ] || continue
          { grep '\.wh\.' members || true; } | while IFS= read -r w; do
            w=''${w#./} dir=$(dirname "$w") base=$(basename "$w")
            if [ "$base" = .wh..wh..opq ]; then
              find "root/$dir" -mindepth 1 -delete 2>/dev/null || true
            else
              rm -rf "root/$dir/''${base#.wh.}"
            fi
          done
          grep -v '\.wh\.' members > keep || true
          [ -s keep ] && tar -xf "image/$layer" -C root --no-same-owner --no-same-permissions --no-recursion -T keep
        done
        test -f root/app/application.jar
        cp -r root/app "$out"
      '';

      komga = pkgs.writeShellApplication {
        name = "komga";
        text = ''
          # The image's entrypoint, with the configuration directory from the
          # slot (KOMGA_CONFIGDIR, default the slot's DATA_DIR).
          export KOMGA_CONFIGDIR=''${KOMGA_CONFIGDIR:-''${DATA_DIR:?}}
          export LD_LIBRARY_PATH=${pkgs.lib.makeLibraryPath (with pkgs; [ libjxl libheif libwebp ])}
          exec ${pkgs.jdk25_headless}/bin/java -Dspring.profiles.include=docker --enable-native-access=ALL-UNNAMED \
            -jar ${app}/application.jar --spring.config.additional-location="file:$KOMGA_CONFIGDIR/" "$@"
        '';
      };
    in
    {
      packages.x86_64-linux = {
        default = komga;
        inherit app;
      };
    };
}
