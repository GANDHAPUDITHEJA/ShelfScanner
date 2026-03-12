# ShelfScanner 🛒

An Android AR shelf scanning app that uses YOLOv8 to detect retail products in real time and marks them with visual tick overlays. Supports both portrait and landscape orientation.

---

## Demo

### 🔵 First Detection — Blue Tick
When a product is seen for the first time, it gets a **blue bounding box** with a **blue tick** mark.

![Blue Tick - First Detection](https://drive.google.com/file/d/1V8puaAYtnAknhogUGKfb8LQgMPNd2kC7/view?usp=sharing)

---

### 🟢 Already Scanned — Green Tick
When you pan away and come back to the same product, it switches to a **green bounding box** with a **green tick** mark.

![Green Tick - Already Scanned](https://drive.google.com/file/d/1zEif6n3Vf1v27HfUWhB-ouejCE3oqP7a/view?usp=sharing)

---

### 🎥 Video Demo
[Watch Demo Video](https://drive.google.com/file/d/1WUQXxrBJi8_L2rsvVsSXgKaNJyiUpWuI/view?usp=sharing)

> Blue = first time detected &nbsp;|&nbsp; Green = already scanned (visited again)

---

## Features

- Real-time product detection using YOLOv8n TFLite
- Blue tick = first time detected, Green tick = already scanned
- Bounding boxes with center tick marks on detected products
- Tracks products across frames — no duplicate counting
- Works in both portrait and landscape orientation
- Reset button to clear all scanned items
- Scanned product counter

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| Camera | CameraX |
| Detection | YOLOv8n TFLite (float32) |
| AR Overlay | Custom Canvas View |
| Tracking | Custom ProductTracker |
| Min SDK | 34 |
| Target SDK | 36 |

---

## Project Structure

```
app/src/main/
├── assets/
│   └── yolov8n_float32.tflite        ← YOLO model
├── java/com/example/shelfscanner/
│   ├── MainActivity.kt               ← Camera + frame processing
│   ├── detection/
│   │   └── YoloDetector.kt           ← TFLite inference
│   ├── tracking/
│   │   └── ProductTracker.kt         ← Object tracking + scan state
│   └── ui/
│       └── AROverlayView.kt          ← Bounding box + tick overlay
└── res/
    ├── layout/
    │   └── activity_main.xml
    └── values/
        └── themes.xml
```

---

## Setup Instructions

### 1. Export YOLOv8 Model (PC)

```bash
pip install ultralytics tensorflow
```

```python
from ultralytics import YOLO
model = YOLO("yolov8n.pt")
model.export(format="tflite", imgsz=320, int8=False)
```

Copy `yolov8n_float32.tflite` to `app/src/main/assets/`

### 2. Android Studio Setup

- Open project in Android Studio
- Sync Gradle
- Connect Android device (API 34+)
- Run ▶️

---

## Dependencies

Add to `build.gradle (Module: app)`:

```gradle
implementation("androidx.camera:camera-camera2:1.3.0")
implementation("androidx.camera:camera-lifecycle:1.3.0")
implementation("androidx.camera:camera-view:1.3.0")
implementation("org.tensorflow:tensorflow-lite:2.13.0")
implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
```

Also add inside the `android` block:

```gradle
aaptOptions {
    noCompress += "tflite"
}
```

---

## Permissions

`AndroidManifest.xml` requires:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />
```

---

## Detection Settings

| Setting | Value |
|---|---|
| Input size | 320x320 |
| Confidence threshold | 0.50 |
| IOU threshold | 0.45 |
| Threads | 4 |
| Frame skip | Every 3rd frame |

---

## Detected Product Categories

- Drinks & containers: bottle, wine glass, cup, bowl
- Food: banana, apple, sandwich, orange, broccoli, carrot, hot dog, pizza, donut, cake
- Electronics: cell phone, laptop, keyboard, mouse, remote, tv
- Bags: backpack, handbag, suitcase
- Other: book, clock, scissors, toothbrush, vase, teddy bear

---

## How Tracking Works

1. Each detected object is matched to an existing tracked object using center distance + IOU score
2. New objects are added to `scannedProducts` list — shown with **Blue tick**
3. When camera pans away and returns, objects matched against `scannedProducts` — shown with **Green tick**
4. Smooth box interpolation (factor 0.4) reduces jitter
5. Objects disappear after 10 undetected frames

---

## Orientation Support

The app handles both portrait and landscape without restarting:

```xml
android:configChanges="orientation|screenSize|keyboardHidden|screenLayout"
```

Camera restarts automatically on rotation via `onConfigurationChanged`.

---

## Known Limitations

- YOLOv8n is trained on COCO dataset — best for common grocery/retail items
- Detection may struggle with custom branded packaging
- Boxes are image-space locked, not world-space locked

---

## License

MIT License — free to use and modify.
