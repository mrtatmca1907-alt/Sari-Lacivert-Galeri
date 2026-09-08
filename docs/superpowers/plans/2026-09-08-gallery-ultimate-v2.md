# Gallery Ultimate v2 implementation plan

1. Inspect the current Ultimate v1 source and locate app label, trash UI/actions, delete/move cache invalidation, photo gesture handling, video viewer/orientation, Settings menu, and duplicate-media entry points.
2. Add/extend tests for non-UI behavior (feature flags, translation clamping, cache invalidation helpers where practical).
3. Implement the smallest source changes that satisfy the eight requested fixes while preserving the v1 MediaStore/cache fast path.
4. Build and run unit tests in GitHub Actions.
5. Download the APK artifact, verify APK structure, and deliver it as Gallery Ultimate v2.
