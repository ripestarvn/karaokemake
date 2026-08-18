<div align="center">
<img width="600" height="600" alt="GHBanner" src="https://cdn.discordapp.com/attachments/1490362575085506773/1539182088346009610/MUSTUDIOLOGO.png?ex=6a856286&is=6a841106&hm=9bdf0b6c0ca9bc4790582791ff514b88586770a4126712471598d83d2921ab5a&" />
</div>

# Karaoke Make

**[Tiếng Việt](#tiếng-việt) | [English](#english)**

---

## Tiếng Việt

### Giới thiệu

**Karaoke Make** là ứng dụng Android (viết bằng Kotlin + Jetpack Compose) giúp bạn tự tạo video karaoke có chữ nhảy theo nhịp, từ một bản nhạc MIDI hoặc file audio và phần lời bài hát do bạn nhập vào. Ứng dụng được khởi tạo từ Google AI Studio, dùng Gemini API cho một số tính năng.

### Cách hoạt động

Luồng sử dụng chính của app gồm các bước:

1. **Tạo dự án (Project):** Từ màn hình chính (`HomeScreen`), người dùng tạo một dự án mới với tiêu đề, ca sĩ, lời bài hát — hoặc chọn nhanh từ danh sách bài hát mẫu có sẵn (`PresetSongs`, ví dụ "Bèo Dạt Mây Trôi"). Mỗi dự án được lưu vào cơ sở dữ liệu cục bộ bằng **Room** (`AppDatabase`, `ProjectDao`, `ProjectRepository`).

2. **Nạp âm thanh:** Ứng dụng có thể phát nhạc theo hai cách:
   - **Tổng hợp âm thanh MIDI:** `MidiParser` đọc file `.mid`/`.kar`, tách các nốt nhạc; `AudioSynthesizer` tổng hợp âm thanh từ các nốt đó (có hỗ trợ nạp SoundFont `.sf2` tùy chỉnh).
   - **Nhập file audio có sẵn** (mp3, wav...) do người dùng tải lên.

3. **Đồng bộ lời bài hát (Sync):** Lời bài hát được tách thành từng âm tiết (`TimedSyllable`). Người dùng dùng thao tác "tap sync" (gõ nhịp theo nhạc) để gán thời điểm bắt đầu/kết thúc cho từng âm tiết — tương tự cách làm phụ đề karaoke thủ công. Có thể chỉnh sửa, xoá, hoặc undo từng mốc thời gian.

4. **Chỉnh sửa hiển thị (Editor):** Màn hình `EditorScreen` cho phép tuỳ chỉnh giao diện chữ hát karaoke: font chữ, cỡ chữ, màu chữ (lúc chờ / lúc đang hát), màu viền, đổ bóng, kiểu nền (ô cờ, xanh lá, đen, gradient), bố cục 1 hoặc 2 dòng, căn lề, độ trễ đồng bộ toàn cục, tín hiệu đếm ngược (dấu chấm) trước mỗi câu...

5. **Xem trước & phát lại:** App vẽ dạng sóng âm thanh (waveform) trên timeline, cho phép tua, chỉnh tốc độ phát, xem trực tiếp hiệu ứng chữ chạy theo nhạc.

6. **Xuất kết quả:**
   - **Xuất phụ đề `.srt`** để dùng với các phần mềm video khác.
   - **Xuất video `.mp4`** hoàn chỉnh: app dùng `MediaCodec`/`MediaMuxer` của Android để vẽ từng khung hình (chữ + nền + hiệu ứng) rồi mã hoá thành video, ghép với track âm thanh.

Toàn bộ trạng thái và nghiệp vụ ứng dụng được quản lý tập trung trong `KaraokeViewModel` (theo kiến trúc MVVM), giao diện dựng bằng Jetpack Compose.

### Cấu trúc thư mục `app/`

```
app/src/main/java/com/example/
├── MainActivity.kt          # Điểm khởi động, điều hướng Home ↔ Editor
├── audio/
│   ├── AudioSynthesizer.kt  # Tổng hợp âm thanh từ dữ liệu MIDI
│   └── MidiParser.kt        # Đọc và phân tích file MIDI/.kar
├── data/
│   ├── AppDatabase.kt       # Cơ sở dữ liệu Room
│   ├── KaraokeProject.kt    # Model dữ liệu dự án + âm tiết định thời
│   ├── PresetSongs.kt       # Danh sách bài hát mẫu dựng sẵn
│   ├── ProjectDao.kt        # Truy vấn Room
│   └── ProjectRepository.kt # Lớp trung gian truy cập dữ liệu
├── ui/
│   ├── dialogs/              # Hộp thoại Cài đặt, Hướng dẫn sử dụng
│   ├── editor/                # Màn hình chỉnh sửa + timeline dạng sóng
│   ├── home/                  # Màn hình danh sách dự án
│   ├── settings/              # Lưu trữ tuỳ chọn ứng dụng
│   ├── theme/                 # Màu sắc, kiểu chữ, theme sáng/tối
│   └── util/                  # Đa ngôn ngữ (Localization)
└── viewmodel/
    └── KaraokeViewModel.kt   # Toàn bộ logic nghiệp vụ (phát nhạc, đồng bộ, xuất file)
```

**Công nghệ chính:** Kotlin, Jetpack Compose (Material 3), Room (lưu trữ cục bộ), Moshi (JSON), Coroutines/Flow, MediaCodec/MediaMuxer (xuất video), Retrofit/OkHttp.

### Build ứng dụng

**Yêu cầu:** [Android Studio](https://developer.android.com/studio), minSdk 24, targetSdk 36.

1. Mở Android Studio, chọn **Open**, trỏ tới thư mục dự án này.
2. Để Android Studio tự đồng bộ Gradle và xử lý các phụ thuộc còn thiếu.
3. Tạo file `.env` ở thư mục gốc dự án, thêm dòng `GEMINI_API_KEY=<khoá_api_của_bạn>` (tham khảo `.env.example`).
4. Với bản build debug: giữ nguyên `signingConfig` mặc định (dùng `debug.keystore` có sẵn). Với bản build release: xoá/sửa dòng `signingConfig = signingConfigs.getByName("debugConfig")` trong `app/build.gradle.kts` và cấu hình keystore release riêng (biến môi trường `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`).
5. Build và chạy trên máy ảo hoặc thiết bị thật (**Run ▶**), hoặc build APK bằng dòng lệnh:
   ```
   ./gradlew assembleDebug
   ```
6. Nếu đã từng phát hành app qua AI Studio, xem hướng dẫn [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) trên Google Play Console khi cần đổi khoá ký.

Xem thêm dự án gốc trên AI Studio: https://ai.studio/apps/f2adf75d-0e84-4a69-9c9c-038fb41bea66

---

## English

### Overview

**Karaoke Make** is an Android app (Kotlin + Jetpack Compose) that lets you build karaoke videos with time-synced, line-highlighted lyrics from a MIDI track or audio file plus lyrics you provide. The project was bootstrapped from Google AI Studio and uses the Gemini API for some features.

### How it works

The core workflow is:

1. **Create a project:** From the `HomeScreen`, the user starts a new project with a title, artist, and lyrics — or picks one of the built-in sample songs (`PresetSongs`, e.g. "Bèo Dạt Mây Trôi"). Each project is persisted locally with **Room** (`AppDatabase`, `ProjectDao`, `ProjectRepository`).

2. **Load the audio:** The app supports two audio sources:
   - **MIDI synthesis:** `MidiParser` reads `.mid`/`.kar` files and extracts notes; `AudioSynthesizer` renders audio from those notes (custom `.sf2` SoundFonts can be imported).
   - **Imported audio files** (mp3, wav, etc.) uploaded by the user.

3. **Sync the lyrics:** Lyrics are broken into individual syllables (`TimedSyllable`). The user "tap-syncs" along with the music to assign a start/end timestamp to each syllable — similar to manual karaoke subtitle timing — with support for editing, deleting, or undoing timing marks.

4. **Edit the look (Editor):** `EditorScreen` lets the user customize the on-screen lyrics: font, size, idle/active text color, outline color, shadow, background style (checkerboard, solid green, black, gradient), one- or two-row layout, alignment, a global sync offset, and a countdown "signal" (dots) before each line.

5. **Preview & playback:** The app renders a waveform timeline, supports seeking and adjustable playback speed, and shows the lyrics animation live in sync with the audio.

6. **Export:**
   - **Export `.srt` subtitles** for use in other video tools.
   - **Export a full `.mp4` video:** the app uses Android's `MediaCodec`/`MediaMuxer` to render each frame (lyrics + background + effects), encode it, and mux it with the audio track.

All app state and business logic live in `KaraokeViewModel` (MVVM architecture), with the UI built entirely in Jetpack Compose.

### `app/` directory structure

```
app/src/main/java/com/example/
├── MainActivity.kt          # Entry point, Home ↔ Editor navigation
├── audio/
│   ├── AudioSynthesizer.kt  # Synthesizes audio from parsed MIDI data
│   └── MidiParser.kt        # Reads and parses MIDI/.kar files
├── data/
│   ├── AppDatabase.kt       # Room database
│   ├── KaraokeProject.kt    # Project model + timed-syllable data
│   ├── PresetSongs.kt       # Built-in sample songs
│   ├── ProjectDao.kt        # Room queries
│   └── ProjectRepository.kt # Data access layer
├── ui/
│   ├── dialogs/               # Settings & usage-notes dialogs
│   ├── editor/                 # Editor screen + waveform timeline
│   ├── home/                   # Project list screen
│   ├── settings/               # App settings persistence
│   ├── theme/                  # Colors, typography, light/dark theme
│   └── util/                   # Localization
└── viewmodel/
    └── KaraokeViewModel.kt   # All business logic (playback, sync, export)
```

**Stack:** Kotlin, Jetpack Compose (Material 3), Room (local storage), Moshi (JSON), Coroutines/Flow, MediaCodec/MediaMuxer (video export), Retrofit/OkHttp.

### Building the app

**Prerequisites:** [Android Studio](https://developer.android.com/studio), minSdk 24, targetSdk 36.

1. Open Android Studio, select **Open**, and point it at this project's directory.
2. Let Android Studio sync Gradle and resolve dependencies.
3. Create a `.env` file in the project root and add `GEMINI_API_KEY=<your_api_key>` (see `.env.example`).
4. For a debug build: keep the default `signingConfig` (uses the bundled `debug.keystore`). For a release build: remove/change the line `signingConfig = signingConfigs.getByName("debugConfig")` in `app/build.gradle.kts` and configure your own release keystore via the `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` environment variables.
5. Run on an emulator or physical device (**Run ▶**), or build an APK from the command line:
   ```
   ./gradlew assembleDebug
   ```
6. If you've previously published the app via AI Studio, see [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console if you need to rotate the signing key.

View the original project in AI Studio: https://ai.studio/apps/f2adf75d-0e84-4a69-9c9c-038fb41bea66
