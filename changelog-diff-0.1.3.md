## Version [0.1.3](https://github.com/jgremmen/pack/releases/tag/0.1.3) (2025-06-16)

### Bug Fixes

- Fixed `AbstractTikaDetector` throwing exceptions when probing input streams that do not
  contain valid pack data. The detector now catches all exceptions during pack stream parsing
  and falls back to `MediaType.OCTET_STREAM` instead of propagating the error.
