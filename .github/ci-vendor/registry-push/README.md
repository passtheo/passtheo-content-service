# `.github/ci-vendor/registry-push/` (vendored)

This directory is **vendored** by `passtheo/ci-workflows:scripts/sync-to-services.sh`
from `passtheo/ci-workflows:vendor/spring-boot-service/.github/ci-vendor/registry-push/`.
The upstream source of truth is `passtheo/infra:cloudflare-registry/push/`.

Do not hand-edit. To update: edit the script in `passtheo/infra:cloudflare-registry/push/`,
mirror the change into `passtheo/ci-workflows:vendor/spring-boot-service/.github/ci-vendor/registry-push/`,
and re-run `sync-to-services.sh` against the affected services.

## Why vendored

`templates/spring-boot-service.yml` `docker-build` job calls this script in
place of `docker/build-push-action`'s push, because Cloudflare's edge proxy
caps any single request body at 100 MiB on Free/Pro/Business plans, and our
distroless+jlink+Spring-Boot fat-jar layers regularly exceed that. The script
speaks the OCI distribution-spec PATCH-then-PUT chunked-upload flow.
