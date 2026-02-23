# Architecture

## Module Structure

```
com.floorplan.tool/
├── MainActivity.kt              # Entry point
├── model/
│   └── Model.kt                 # Data classes (PlanDocument, Annotation, etc.)
├── util/
│   ├── Camera.kt                # Camera state (scale, tx, ty)
│   └── TransformUtil.kt         # Coordinate transforms, re-register, clustering
├── renderer/
│   └── FloorPlanRenderer.kt     # Canvas rendering (on-screen + export)
├── viewmodel/
│   └── FloorPlanViewModel.kt    # MVI-style state container
└── ui/
    ├── FloorPlanScreen.kt       # Main composable with BottomSheetScaffold
    ├── FloorPlanCanvas.kt       # Compose Canvas + gesture handling
    ├── ToolsPanel.kt            # Bottom sheet tools
    ├── Dialogs.kt               # Calibration + Edit dialogs
    ├── QuickActionsBar.kt       # TopAppBar + HUD overlay
    └── theme/
        └── Theme.kt             # Material 3 theme
```

## Key Classes

| Class | Responsibility |
|-------|---------------|
| `PlanDocument` | Serializable document model (matches JSON schema) |
| `Camera` | Pan/zoom state: `scale`, `tx`, `ty` |
| `TransformUtil` | Pure functions: `screenToPlan`, `planToScreen`, `fitToScreen`, `zoomAt`, `applyReRegister`, `clustersFromAnnotations` |
| `FloorPlanRenderer` | Draws all scene elements onto any `android.graphics.Canvas` |
| `FloorPlanViewModel` | Single source of truth (`StateFlow<FloorPlanState>`), handles all events |
| `FloorPlanScreen` | Top-level composable: scaffold, SAF launchers, dialog triggers |
| `FloorPlanCanvas` | Compose Canvas with pinch-zoom, tap, drag, long-press gestures |

## State Handling (MVI)

```
User Gesture → ViewModel (event handler) → _state.update { ... } → StateFlow → Compose recomposition → Canvas redraw
```

- **Immutable state**: `FloorPlanState` is a data class; every change produces a new copy
- **Single StateFlow**: All UI reads from `vm.state.collectAsStateWithLifecycle()`
- **Side effects**: Import/export run in `viewModelScope` on `Dispatchers.IO`
- **Bitmap**: Held as `var baseBitmap` on ViewModel (not in `FloorPlanState` to avoid serialization)

## Rendering Architecture

`FloorPlanRenderer` is agnostic to Compose — it works with `android.graphics.Canvas`:
- **On-screen**: Compose `Canvas` → `drawContext.canvas.nativeCanvas` → `FloorPlanRenderer.drawScene()`
- **Export PNG**: `Bitmap.createBitmap()` → `Canvas(bitmap)` → `FloorPlanRenderer.renderForExport()`
- **Export PDF**: `PdfDocument.startPage().canvas` → `FloorPlanRenderer.renderForExport()`

## Gesture Handling

| Gesture | Action |
|---------|--------|
| Pinch | Zoom via `rememberTransformableState` |
| Two-finger drag | Pan (via transformable panChange) |
| Single-finger drag | Pan in Normal/Pan mode; label drag when starting on a label |
| Tap | Mode-dependent: calibration point, annotation placement, selection |
| Long press | Open edit dialog for annotation label |

## Known Limits

- PDF import uses `android.graphics.pdf.PdfRenderer` which only renders page 1
- Maximum bitmap dimension capped at 4096px to guard memory
- Rubber-band cursor updates require finger movement (no hover on mobile)
- Grid performance may degrade with very small step sizes at high zoom
- No undo/redo support (matches webapp behavior)
