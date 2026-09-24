# Attachment playback fixture

`black-white-h264.mp4` is an original generated test asset: ten alternating
black/white 32×32 frames at 10 fps, H.264 in MP4, no audio or personal content.
It was encoded with macOS AVFoundation for this repository and may be used
under the repository's license. It is test-only and not shipped with the app.

Video support was removed by product decision. This fixture is now used only
to prove that actual video bytes are rejected, including when renamed as PNG
or JPEG. It is never played; tests require no JavaFX media or codec dependency.
