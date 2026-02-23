# Test Plan

## Automated Tests

### Unit Tests (JVM — no device required)

Run: `./gradlew test`

#### TransformUtilTest

| # | Test | Verifies |
|---|------|----------|
| 1 | `planToScreen then screenToPlan returns original` | Round-trip with non-trivial camera |
| 2 | `screenToPlan then planToScreen returns original` | Reverse round-trip |
| 3 | `round trip with identity camera` | Baseline identity transform |
| 4 | `fitToScreen computes correct scale and centering` | 0.98x fit + centering offsets |
| 5 | `zoomAt preserves point under cursor` | Zoom anchor invariant |
| 6 | `zoomAt clamps scale to bounds` | Min 0.1, max 20 |
| 7 | `reRegister identity transform preserves positions` | No-op transform |
| 8 | `reRegister with pure translation` | Translate (50,50) |
| 9 | `reRegister with scale factor 2` | 2x scale at origin |
| 10 | `reRegister with 90 degree rotation` | 90° CCW rotation |
| 11 | `reRegister returns null for zero distance` | Error guard |
| 12 | `reRegister updates saved anchors` | Anchor bookkeeping |
| 13 | `clustersFromAnnotations groups by rounded coords` | Clustering logic |
| 14 | `isCalibrated requires all three conditions` | Three-way guard |

#### JsonRoundTripTest

| # | Test | Verifies |
|---|------|----------|
| 1 | `empty document round trip` | Minimal document |
| 2 | `full document round trip with annotations and anchors` | All fields preserved |
| 3 | `AnnoType serializes as lowercase string` | Enum format |
| 4 | `null refAnchors round trip` | Nullable field |
| 5 | `labelOffset defaults are applied when missing` | Default deserialization |
| 6 | `refAnchors A and B use correct JSON keys` | `@SerialName` correctness |

### Instrumented Tests (requires emulator or device)

Run: `./gradlew connectedAndroidTest`

| # | Test | Flow |
|---|------|------|
| 1 | `loadBitmapAndFitToScreen` | Create bitmap → set as base → fitToScreen → verify camera |
| 2 | `calibrationSetsScaleFactorAndEnablesGrid` | CALIBRATION mode → 2 taps → confirm dialog → verify scaleFactor + grid |
| 3 | `addAnnotationIncrementsCount` | ANNOTATION mode → tap → verify count |
| 4 | `editAnnotationUpdatesLabel` | Add → openEditor → change → save → verify |
| 5 | `deleteSelectedRemovesAnnotation` | Add 2 → select → delete → verify 1 |
| 6 | `jsonExportToCache` | Add annotation → serialize → write to cache → verify content |
| 7 | `pngExportToCache` | Load bitmap → renderForExport → compress → verify file |
| 8 | `pdfExportToCache` | PdfDocument → renderForExport → writeTo → verify file |
| 9 | `reRegisterTransformsAnnotations` | Setup anchors → REREGISTER → 2 taps → verify transform |
| 10 | `modeSwitchingWorks` | Switch through modes → verify state |

## Manual Tests

These require a physical device or emulator with the app installed.

### Multi-touch Zoom
1. Open app, import an image
2. Place two fingers on canvas and pinch inward/outward
3. **Expected**: Smooth zoom centered between fingers

### Pan in Different Modes
1. Switch to Pan mode → drag with one finger → canvas pans
2. Switch to Normal mode → drag → canvas pans (no annotation placed)
3. Switch to Annotation mode → drag → no pan, updates cursor
4. **Expected**: Pan only works in Pan/Normal modes

### Label Drag
1. Add an annotation
2. Drag the label box (touch and drag)
3. Export JSON → check `labelOffset` changed
4. **Expected**: Label follows finger, offset persists

### Calibration + Grid
1. Switch to Calibration mode
2. Tap two points, enter distance (e.g., 5.0m)
3. Grid should appear automatically
4. Grid origin should be at Ref A
5. Adjust grid major/minor in settings
6. **Expected**: Grid lines update with settings, labels show meters

### Import/Export
1. Import a PDF → verify first page renders
2. Import JSON with annotations → verify they appear
3. Export JSON → reimport → all annotations match
4. Export PNG with "include details" → verify file in gallery
5. Export PDF → verify file opens in PDF viewer
6. **Expected**: All import/export operations complete without errors

### Re-register
1. Calibrate with two points
2. Add several annotations
3. Switch to Re-register mode
4. Tap two new reference points
5. **Expected**: All annotations shift/rotate/scale to match new reference frame
