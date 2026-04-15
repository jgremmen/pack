## Version [0.2.0](https://github.com/jgremmen/pack/releases/tag/0.2.0) (2026-04-14)

### Breaking Changes

The minimum Java version has been raised from 11 to 21. The library is now compiled with Java
21 and uses Java 21 as its language target. Projects that still target Java 11 through 20 must
remain on the 0.1.x release line.

The accepted version ranges for compile-time dependencies have been widened:

| Dependency | Type | 0.1.3 | 0.2.0 |
|---|---|---|---|
| `org.jetbrains:annotations` | compile | `[24.0,26.1)` | `[19.0,26.2)` |
| `org.apache.tika:tika-core` | compile | `[3.1,3.2)` | `[1.19,3.3)` |

The lower bound for `org.jetbrains:annotations` was lowered from 24.0 to 19.0 and the upper
bound was raised from 26.1 to 26.2. The lower bound for `org.apache.tika:tika-core` was
lowered from 3.1 to 1.19 and the upper bound was raised from 3.2 to 3.3. While these changes
are more permissive, they may affect dependency resolution if your project pins specific
versions within the previously excluded ranges.
