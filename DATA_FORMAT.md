# Data Format

## JSON Schema (Version 1)

The Floor Plan Tool uses a JSON format for saving and loading floor plan data.

### Top-level Document

```json
{
  "version": 1,
  "planWidth": 1200,
  "planHeight": 800,
  "scaleFactor": 0.005,
  "refAnchors": {
    "A": { "x": 100.5, "y": 200.3 },
    "B": { "x": 500.7, "y": 200.1 }
  },
  "crosshairSize": 12,
  "annotations": [...]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `version` | int | yes | Schema version, currently `1` |
| `planWidth` | int | yes | Base plan width in pixels |
| `planHeight` | int | yes | Base plan height in pixels |
| `scaleFactor` | double | yes | Meters per pixel (from calibration) |
| `refAnchors` | object\|null | no | Saved calibration reference points |
| `crosshairSize` | int | yes | Crosshair marker size in pixels (4–40) |
| `annotations` | array | yes | List of annotation objects |

### RefAnchors

```json
{
  "A": { "x": 100.0, "y": 200.0 },
  "B": { "x": 500.0, "y": 200.0 }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `A` | PointD | Reference point A (calibration origin) |
| `B` | PointD | Reference point B |

### Annotation

```json
{
  "x": 150.0,
  "y": 250.0,
  "label": "Outlet",
  "details": "2m above floor\nCat6, to rack A",
  "type": "floor",
  "labelOffset": { "dx": 18.0, "dy": -18.0 }
}
```

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `x` | double | — | X position in plan pixels |
| `y` | double | — | Y position in plan pixels |
| `label` | string | `"Connector"` | Display name |
| `details` | string | `""` | Multi-line details text |
| `type` | enum | `"floor"` | One of: `"floor"`, `"wall"`, `"roof"` |
| `labelOffset` | OffsetD | `{dx:18, dy:-18}` | Label box offset from annotation |

### Type Color Mapping

| Type | Color | Emoji |
|------|-------|-------|
| `floor` | Purple (#800080) | 🟣 |
| `wall` | Blue | 🔵 |
| `roof` | Green | 🟢 |

### PointD

```json
{ "x": 100.0, "y": 200.0 }
```

### OffsetD

```json
{ "dx": 18.0, "dy": -18.0 }
```

## Versioning

- Current version: **1**
- The `version` field enables future format migrations
- Unknown fields are ignored during import (`ignoreUnknownKeys = true`)
- Missing `labelOffset` uses default `{dx: 18.0, dy: -18.0}`
- Missing `type` defaults to `"floor"`
- Missing `label` defaults to `"Connector"`

## Compatibility

The JSON format is compatible with the HTML/JS webapp's export format. Files exported from the webapp can be imported into the Android app and vice versa, with the addition of the `version` field (which the webapp ignores).
