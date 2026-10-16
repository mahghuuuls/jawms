# Frozen API 1.6 oracle

These MIT JAWMS-only artifacts were retained unchanged from release-ready source
`1259b6d4fe5b45d35155955ba960936bd00926ae` before the candidate version changed.
They contain no Wizardry or other dependency implementation. `build.gradle`
checks all four SHA-256 identities and compares the production API descriptors.
Never rebuild these consumers against candidate classes and call them old-binary proof.

The `jawms-api-fixture-1.0` binaries are the existing public-only consumer at that
baseline. Use the production fixture for packaged launches and its deobfuscated
counterpart for development linkage. Never load it alongside the rebuilt fixture
with the same `jawms_api_fixture` mod ID. The new cast-cost fixture has a separate
mod ID and source set. Frozen JARs are verification inputs, not runtime shipping
dependencies or a claim that 1.1.0 has been published.
