# s3-archive fork

Komga with libraries that live in S3-compatible object storage, read in place, plus scanner and
metadata options for content-addressed web-comic archives.

## S3 libraries

The `aws-java-nio-spi-for-s3` java.nio provider serves `s3://` (AWS) and `s3x://` (S3-compatible)
paths, and `S3UrlStreamHandlerProvider` lets Komga store them as book and series URLs. Set the
library root to a URI instead of a folder, for example:

    s3x://rustfs.example:9000/bucket/prefix/series/

Configure the provider through the environment (credentials never go in the URI, since the root is
stored in the database):

| Variable | Value |
| --- | --- |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` | read-only credentials; region `us-east-1` for most S3-compatible stores |
| `S3_SPI_FORCE_PATH_STYLE` | `true` for RustFS, MinIO and other stores without virtual-hosted buckets |
| `S3_SPI_ENDPOINT_PROTOCOL` | `http` when the store has no TLS |
| `S3_SPI_READ_MAX_FRAGMENT_SIZE`, `S3_SPI_READ_MAX_FRAGMENT_NUMBER` | read-ahead; `524288` and `2` keep a page read to about 1.5x its size (defaults read several MB ahead) |

Library settings that matter for S3:
- turn off **file hashing** (`hashFiles`): it reads every book in full;
- turn on **force directory modified time** (`scanForceModifiedTime`): S3 folders have no modified time;
- folder watching does not apply; use a scan interval.

Komga analyses each book once when it is added, which reads about the whole file (page dimensions).
Page reads afterwards are ranged requests. Page removal is skipped for books in S3; PDF and RAR books
are read into memory instead of from a local file.

## Archive layout options

Two library options, set through the API (`/api/v1/libraries`), not yet in the web UI:

- `scanSeriesDepth` (integer, default unset): books in folders deeper than this depth below the root
  belong to the series of their ancestor folder at that depth. With the archive layout
  `series/<id>/episodes/<id>/<sha>.cbz` and the root at `series/`, depth `1` makes each `series/<id>`
  one series.
- `scanOneBookPerFolder` (boolean, default `false`): when a folder holds several books, keep only the
  most recently modified one (an episode collected again leaves its older file behind).

`scanDirectoryExclusions` patterns now also apply to file paths, so `.media.zip` excludes multimedia
episode archives that would otherwise be read as comics.

## Archive metadata

`ArchiveSidecarProvider` reads the JSON documents the archive writes next to its files, recognised
by their fields since the files are hash-named:
- book: the episode document in the book's folder (`id`, `title`, `position`, `url`, `source`) gives
  the title, number, sort number and a link;
- series: the newest document in the series' `metadata/` folder (`title`, `description`, `source`)
  gives the title, summary and publisher, and sets the reading direction to webtoon (vertical scroll).

Turn off **import EPUB book metadata** for archive libraries: the archive's EPUBs carry only the
series name as their title, and that provider would otherwise overwrite the episode title.

## Image

    docker build -f docker/s3-archive.Dockerfile -t komga:s3-archive .

Builds both web UIs and the server from source, then runs them on the official `gotson/komga`
image's runtime.

## Homelab deploy

The homelab runs this fork as the slot `komga` (`flake.nix`, `#default`): the server from a pinned
image on a Nix JRE. Pushes and pull requests get a `homelab/build` status from the homelab's Forgejo
(the primary remote; GitHub is its mirror, so push to Forgejo only). Every merge to `s3-archive`
deploys; a failed health check rolls back. To ship a change: merge it, wait for the
`s3-archive image` workflow, then update `imageDigest`, `hash` and `finalImageTag` in `flake.nix`
(the command is in its header) and merge that.
