# Attachment playback fixture

`black-white-h264.mp4` is an original generated test asset: ten alternating
black/white 32×32 frames at 10 fps, H.264 in MP4, no audio or personal content.
It was encoded with macOS AVFoundation for this repository and may be used
under the repository's license. It is test-only and not shipped with the app.

The native playback test verifies actual playback advancement, not merely
container recognition. Hosts need their platform's JavaFX media dependencies.
This fixture does not test AAC audio, all H.264 profiles, or packaged artifacts.
