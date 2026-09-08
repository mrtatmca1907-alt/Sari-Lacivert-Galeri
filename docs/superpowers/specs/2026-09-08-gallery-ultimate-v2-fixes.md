# Gallery Ultimate v2 fixes

User requested eight changes while preserving the fast-loading Ultimate v1 base:
1. App name becomes `Galeri`.
2. Trash must be emptyable.
3. Deleted photos/folders must disappear from album/media views immediately while existing in trash.
4. Zoom must stay visually contained by the screen/view bounds.
5. Manual pinch zoom must stop jittering.
6. Video viewer must support landscape rotation.
7. Similar/duplicate-media feature is removed from this gallery; it will become a separate app later.
8. Trash entry moves under Settings.

Implementation constraints:
- Do not regress the fast MediaStore/cache path that made v1 open instantly.
- Fix deletion at the data-source/cache layer so UI state and MediaStore state stay consistent.
- Keep photo gestures mutually exclusive where necessary: pinch owns scaling while active; one-finger pan only when scaled; horizontal paging only at base scale.
- Clamp translation after scale/rotation so content cannot drift completely outside the viewport.
- Video rotation should use requested orientation/fullscreen behavior without restarting expensive gallery scans.
