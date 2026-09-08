Root-cause focus for v2:
- Trash visibility bug likely comes from stale cached media lists not being invalidated/updated when files are moved to trash.
- Trash emptying failure must be traced to its delete path and permission/storage API, not hidden with UI-only changes.
- Zoom overflow/jitter likely comes from simultaneous scale + pan/page gesture ownership and missing post-transform clamping.
- Video landscape is a viewer orientation/state issue and should not trigger gallery rescans.
