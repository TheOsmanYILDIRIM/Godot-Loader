extends Control

# Standalone device diagnostics. No TAMU assets or game code.
const SAVE_FILE = "user://osman_loader_diag_save.json"
const REPORT_FILE = "user://osman_loader_diag_report.json"
const MARKER = "OSMAN-LOADER-DIAGNOSTIC-V1"
var report: Dictionary = {"schema": 1, "tests": [], "environment": {}}
var output: RichTextLabel
var version := "unknown"

func _ready() -> void:
    version = FileAccess.get_file_as_string("res://version.txt").strip_edges()
    report["environment"] = {
        "version": version,
        "engine": Engine.get_version_info().get("string", "unknown"),
        "os": OS.get_name(),
        "user_path": ProjectSettings.globalize_path("user://")
    }
    _build_ui()
    _run_checks()
    if "--smoke" in OS.get_cmdline_user_args():
        print("OSMAN_LOADER_TEST_REPORT=" + JSON.stringify(report))
        get_tree().quit(0 if _all_passed() else 2)

func _build_ui() -> void:
    var pane := VBoxContainer.new()
    pane.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    pane.add_theme_constant_override("separation", 14)
    add_child(pane)
    var heading := Label.new()
    heading.text = "GODOT LOADER TEST • " + version
    pane.add_child(heading)
    output = RichTextLabel.new()
    output.size_flags_vertical = Control.SIZE_EXPAND_FILL
    pane.add_child(output)
    var rerun := Button.new()
    rerun.text = "TESTLERİ YENİDEN ÇALIŞTIR"
    rerun.pressed.connect(_run_checks)
    pane.add_child(rerun)
    var copy := Button.new()
    copy.text = "RAPORU KOPYALA"
    copy.pressed.connect(func(): DisplayServer.clipboard_set(JSON.stringify(report, "  ")))
    pane.add_child(copy)
    var save := Button.new()
    save.text = "RAPORU user:// DOSYASINA KAYDET"
    save.pressed.connect(_write_report)
    pane.add_child(save)

func _result(id: String, passed: bool, detail: String) -> void:
    var tests: Array = report["tests"]
    tests.append({"id": id, "status": "PASS" if passed else "FAIL", "detail": detail})

func _run_checks() -> void:
    report["tests"] = []
    _result("engine_4_6", Engine.get_version_info().get("major", 0) == 4 and Engine.get_version_info().get("minor", 0) == 6, str(Engine.get_version_info().get("string", "")))
    _result("resource_read", FileAccess.file_exists("res://version.txt") and version in ["v1", "v2"], version)
    _result("user_path", ProjectSettings.globalize_path("user://").length() > 3, ProjectSettings.globalize_path("user://"))
    var previous := {}
    if FileAccess.file_exists(SAVE_FILE):
        var parsed: Variant = JSON.parse_string(FileAccess.get_file_as_string(SAVE_FILE))
        if typeof(parsed) == TYPE_DICTIONARY:
            previous = parsed
    if version == "v1":
        var f := FileAccess.open(SAVE_FILE, FileAccess.WRITE)
        if f == null:
            _result("save_write", false, str(FileAccess.get_open_error()))
        else:
            f.store_string(JSON.stringify({"marker": MARKER, "source": "v1", "counter": 1}))
            f.flush()
            f.close()
            _result("save_write", true, "created v1 marker")
        var check: Variant = JSON.parse_string(FileAccess.get_file_as_string(SAVE_FILE))
        _result("save_reopen", typeof(check) == TYPE_DICTIONARY and check.get("marker", "") == MARKER, "read back v1 save")
    else:
        _result("migration_from_v1", previous.get("marker", "") == MARKER and previous.get("source", "") == "v1", "v2 expects explicit copied v1 profile; source=" + str(previous.get("source", "missing")))
        _result("save_read", typeof(previous) == TYPE_DICTIONARY and not previous.is_empty(), "v2 read existing save")
    _write_report()
    var passed := 0
    for item in report["tests"]:
        if item["status"] == "PASS":
            passed += 1
    output.text = "RESULT: %d/%d PASS\n\n" % [passed, report["tests"].size()]
    for item in report["tests"]:
        output.text += "%s  %s\n%s\n\n" % [item["status"], item["id"], item["detail"]]
    print("OSMAN_LOADER_TEST_REPORT=" + JSON.stringify(report))

func _write_report() -> void:
    var f := FileAccess.open(REPORT_FILE, FileAccess.WRITE)
    if f == null:
        push_error("Could not write diagnostic report")
        return
    f.store_string(JSON.stringify(report, "  "))
    f.flush()
    f.close()

func _all_passed() -> bool:
    for item in report["tests"]:
        if item["status"] != "PASS":
            return false
    return true
