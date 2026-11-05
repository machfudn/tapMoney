# Project Guidelines & Agent Instructions

## Test Files Location
- Semua file code test (unit test, parser test, engine test) WAJIB ditempatkan di direktori root `tests/` (`tests/<TestFileName>.kt`).
- JANGAN tempatkan file test di dalam `android/app/src/test/` atau subdirektori Android default lainnya untuk mencegah duplikasi.
- Konfigurasi Gradle Android (`android/app/build.gradle`) telah disetel untuk membaca test dari root:
  ```groovy
  sourceSets {
      test.java.srcDirs = ["../../tests"]
  }
  ```
- Package header di file test tetap mengikuti namespace produksi (contoh: `package id.tapmoney.nfc.parser`).

## Running Tests
- Eksekusi unit test dari direktori `android/`:
  ```bash
  cmd.exe /c gradlew.bat testDebugUnitTest
  ```
