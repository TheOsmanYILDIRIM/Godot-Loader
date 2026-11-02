# Godot Loader diagnostic PCK

Two versions test the same game update workflow **on an actual Android phone**.

1. Install the independent Godot Loader APK.
2. Download the `Godot-Loader-Diagnostic-PCK` GitHub Actions artifact and extract **both** PCKs.
3. Import `Godot-Loader-Test-v1.pck`, tap **OYUNU AÇ**. Verify `save_write`, `save_reopen`, engine, resource and path checks show PASS.
4. Import `Godot-Loader-Test-v2.pck`. Choose **KOPYALA** when asked to migrate saves from the previous pack. Start the game; `migration_from_v1` must show PASS.
5. Repeat with **YENİ KAYIT** instead of KOPYALA (using a fresh test profile). v2 should report `migration_from_v1: FAIL`, demonstrating the negative control.
6. Tap **RAPORU KOPYALA** to share the complete JSON report. The report also resides in Godot `user://osman_loader_diag_report.json`. No automatic network upload occurs.

CI verifies PCK packaging and headless starts, **not** Android-specific SAF, save namespace or device FPS. Godot test files are separate from TAMU and do not change it. The test's FAIL result can be expected when intentionally declining migration.
