# PDR Prototype for Android

스마트폰 IMU 센서를 이용해 **Pedestrian Dead Reckoning(PDR)** 을 재현하는 Android Kotlin 프로토타입입니다.

## 1. 프로젝트 목표

GPS가 제대로 동작하지 않는 실내 환경에서 스마트폰 센서로 사용자의 상대적인 이동거리, 방향, 2차원 위치를 추정합니다.

```text
Accelerometer ─┐
Gyroscope ─────┼→ 센서 처리 → 걸음 검출 → 보폭/방향 → PDR → (X,Y) → 2D 궤적
Magnetometer ──┘
```

최종 프로젝트에서는 다음 구조로 확장할 수 있습니다.

```text
GPS → 건물 진입 → Indoor Localization → PDR
                                  ↓
                         Visual Map / Node
                                  ↓
                          ArUco 위치 보정
                                  ↓
                              A* 경로
                                  ↓
                                TTS
```

> 현재 버전은 PDR 개념 검증용 프로토타입이며 정밀한 상용 실내 측위 시스템은 아닙니다.

## 2. PDR이란?

PDR(Pedestrian Dead Reckoning)은 기준점에서 시작하여 사람의 걸음과 이동방향을 계속 누적해 현재 위치를 추정하는 방식입니다.

기본 원리는:

```text
현재 위치 + 이동거리 + 이동방향 → 새 위치
```

현재 코드에서는 걸음 하나를 `0.70 m`로 가정합니다.

```text
ΔX = L × sin(θ)
ΔY = L × cos(θ)

Xnew = Xold + ΔX
Ynew = Yold + ΔY
```

여기서 `L`은 보폭, `θ`는 heading입니다.

## 3. 프로젝트 구조

```text
PDRPrototype/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ README.md
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/example/pdrprototype/
      │  ├─ MainActivity.kt
      │  ├─ PDRProcessor.kt
      │  └─ TrajectoryView.kt
      └─ res/values/
         └─ strings.xml
```

## 4. 파일별 역할

### `MainActivity.kt`

프로그램의 중심입니다.

- `SensorManager` 초기화
- Accelerometer/Gyroscope/Magnetometer 등록
- 센서 이벤트 수신
- 가속도 처리
- 걸음 검출
- heading 계산
- PDRProcessor 호출
- 화면 업데이트
- 시작/정지/초기화 처리

센서 흐름:

```text
onSensorChanged()
├─ Accelerometer → processAcceleration() → detectStep()
├─ Magnetometer → calculateHeading()
└─ Gyroscope → 현재 버전에서는 수집만
```

### `PDRProcessor.kt`

센서와 UI를 분리하고 실제 PDR 위치 계산을 담당합니다.

주요 상태:

```kotlin
x
y
totalDistance
stepCount
heading
```

걸음이 발생하면:

```kotlin
pdr.onStep()
```

을 호출합니다.

내부에서는:

```kotlin
stepCount++

val stepLength = 0.70
val rad = Math.toRadians(heading)

val dx = stepLength * sin(rad)
val dy = stepLength * cos(rad)

x += dx
y += dy
totalDistance += stepLength
```

을 수행합니다.

### `TrajectoryView.kt`

PDR로 계산된 `(X,Y)` 좌표를 2D 화면에 이동 궤적으로 그립니다.

현재 화면에서는 약 `1 m = 100 px` 배율을 사용합니다.

Android 화면 좌표에서는 Y가 아래쪽으로 증가하므로 실제 지도 좌표와 반대가 되도록:

```kotlin
screenY = centerY - mapY * scale
```

형태로 표시합니다.

## 5. Accelerometer 처리

가속도계는 x/y/z 세 방향의 가속도를 제공합니다.

```text
a = √(x² + y² + z²)
```

현재 코드는:

```kotlin
accelerationMagnitude =
    sqrt((x*x + y*y + z*z).toDouble())
```

로 크기를 계산합니다.

정지 상태에서도 중력가속도 약 `9.81 m/s²`가 포함되므로 현재 프로토타입에서는:

```text
dynamicAcceleration = |a - 9.81|
```

로 단순 제거합니다.

## 6. 필터

센서 노이즈를 줄이기 위해 간단한 지수 이동 평균 형태를 사용합니다.

```kotlin
filteredAcceleration =
    0.8 * filteredAcceleration +
    0.2 * dynamicAcceleration
```

즉 이전 값에 80%, 현재 값에 20%의 가중치를 둡니다.

## 7. 걸음 검출

현재는 단순 peak detection을 사용합니다.

```kotlin
private val stepThreshold = 1.2
private val minimumStepInterval = 300L
```

다음 조건을 모두 만족하면 한 걸음으로 판단합니다.

```kotlin
acceleration > stepThreshold
```

그리고:

```kotlin
acceleration > previousAcceleration
```

마지막 걸음 이후 최소 300 ms가 지나야 합니다.

```text
가속도
  │       /\        /  │      /  \      /  1.2 ────/────\────/────\── threshold
  │
  └──────────────────────── 시간
          ↑          ↑
        step       step
```

### 한계

현재 방식은 프로토타입용입니다. 휴대폰 위치, 사용자 보행 방식, 계단, 뛰기 등에 따라 오검출/미검출이 발생할 수 있습니다.

향후에는:

```text
Peak + Valley
+ Adaptive Threshold
+ Step Timing
+ Motion State
```

등을 고려하는 것이 좋습니다.

## 8. Heading 계산

현재 버전은 Accelerometer와 Magnetometer를 이용합니다.

```kotlin
SensorManager.getRotationMatrix(
    rotationMatrix,
    null,
    accelerometerData,
    magnetometerData
)
```

이후:

```kotlin
SensorManager.getOrientation(
    rotationMatrix,
    orientation
)
```

로 방위각을 구합니다.

기준은 일반적인 azimuth 기준으로:

```text
0°   북
90°  동
180° 남
270° 서
```

입니다.

## 9. Gyroscope

현재 코드는 Gyroscope 이벤트를 등록하지만 실제 PDR 방향 계산에는 아직 사용하지 않습니다.

```kotlin
Sensor.TYPE_GYROSCOPE -> {
    // 다음 단계에서 센서 융합에 사용
}
```

실제 PDR에서는 자이로를 이용해 회전 변화를 추적하고, 가속도계/자기장 센서 또는 회전 벡터와 결합하여 더 안정적인 자세 추정을 구현하는 것이 좋습니다.

특히 실내에서는 철근, 엘리베이터, 금속문, 전기설비 등에 의해 자기장이 왜곡될 수 있으므로 자기장만으로 장시간 heading을 유지하는 데 한계가 있습니다.

## 10. 보폭

현재는 모든 걸음을:

```kotlin
0.70 m
```

로 처리합니다.

따라서 10걸음이면:

```text
10 × 0.70 = 7.0 m
```

로 계산됩니다.

실제 시스템에서는 사용자별 보폭을 추정해야 합니다.

향후 후보:

```text
사용자 키
+ 걸음 주기
+ 가속도 진폭
+ 보행 속도
→ 동적 보폭
```

## 11. PDR 위치 보정

`PDRProcessor.kt`에는:

```kotlin
fun correctPosition(newX: Double, newY: Double)
```

가 있습니다.

이 함수는 향후 Visual Map/ArUco Node와 연결하기 위한 것입니다.

예:

```text
PDR 추정 위치 = (4.8, 9.7)
실제 Node      = (5.0, 10.0)
```

Node를 확인하면:

```kotlin
pdr.correctPosition(5.0, 10.0)
```

으로 위치를 보정할 수 있습니다.

## 12. Node 기반 구조

현재 프로젝트의 최종 구조에서는:

```text
Node 0 (0,0)
   │
   │ PDR
   ▼
Node 1 (0,5)
   │
   │ PDR
   ▼
Node 2 (5,5)
```

처럼 사용할 수 있습니다.

Node를 카메라/ArUco 등으로 인식하면 실제 Node 좌표로 PDR을 보정합니다.

```text
PDR
 ↓
Node 인식
 ↓
실제 Node 좌표
 ↓
PDR 보정
 ↓
다시 PDR
```

이 방식은 PDR의 누적 오차를 주기적으로 줄이는 데 사용할 수 있습니다.

## 13. 현재 버전의 한계

### 고정 보폭

모든 사용자를 0.70 m로 가정합니다.

### 단순 걸음 검출

하나의 threshold에 크게 의존합니다.

### 자기장 왜곡

실내 금속 구조물 등에 의해 heading 오차가 발생할 수 있습니다.

### 누적 오차

PDR은 다음 오차가 누적됩니다.

```text
걸음 검출 오차
+
보폭 오차
+
방향 오차
→
위치 오차
```

따라서 장거리 이동에서는 절대 위치 보정 수단이 필요합니다.

## 14. 권장 개발 순서

```text
1. 현재 프로토타입 실행
        ↓
2. 실제 스마트폰 걸음 검출 테스트
        ↓
3. Adaptive Step Detection
        ↓
4. 사용자별/동적 보폭
        ↓
5. Gyroscope + Rotation Vector 기반 방향 안정화
        ↓
6. PDR 좌표계와 Visual Map 좌표계 연결
        ↓
7. Node/Edge 구축
        ↓
8. ArUco Node 인식
        ↓
9. Node 도착 시 PDR 위치 보정
        ↓
10. A* 실내 경로 탐색
        ↓
11. TTS 음성 안내
```

## 15. 테스트 방법

### 직선 이동

```text
시작 → 10걸음 직진
```

이론적으로:

```text
걸음 ≈ 10
거리 ≈ 7 m
```

가 되어야 합니다.

### 90도 방향 전환

```text
10걸음
  ↓
90° 회전
  ↓
10걸음
```

궤적의 방향이 바뀌는지 확인합니다.

### 사각형

```text
→ 10걸음
↓ 10걸음
← 10걸음
↑ 10걸음
```

이론적으로 시작점 근처로 돌아와야 하지만 실제 PDR에서는 누적 오차 때문에 차이가 발생합니다.

## 16. VS Code 빌드

프로젝트 루트에서:

```powershell
.\gradlew.bat assembleDebug
```

APK는 일반적으로:

```text
app/build/outputs/apk/debug/app-debug.apk
```

에 생성됩니다.

ADB 연결 확인:

```powershell
adb devices
```

설치:

```powershell
.\gradlew.bat installDebug
```

> 현재 프로젝트에는 Gradle Wrapper가 포함되어 있지 않을 수 있습니다. 이 경우 Android SDK/JDK/Gradle 환경을 먼저 구성하거나 Android Studio에서 Wrapper를 생성해야 합니다.

## 17. 최종 프로젝트와의 연결

현재 PDR 프로토타입은 다음 최종 구조의 핵심 위치 추정 모듈로 볼 수 있습니다.

```text
                 Smartphone
                     │
       ┌─────────────┼─────────────┐
       │             │             │
     Camera          IMU           GPS
       │             │             │
     ArUco           PDR         Outdoor
       │             │           Position
       └───────┬─────┘
               ▼
        Position Fusion
               │
               ▼
          Indoor Map DB
          ┌─────┴─────┐
          │           │
        Nodes        Edges
          │           │
          └─────┬─────┘
                ▼
             A* Path
                │
                ▼
           Route Guidance
                │
                ▼
               TTS
```

핵심은 **PDR만으로 최종 위치를 장시간 유지하려고 하지 않는 것**입니다.

현재 프로젝트처럼 Visual Map과 Node를 구축한다면:

```text
PDR = 연속적인 상대 이동 추정
Node/ArUco = 절대 위치 보정
Map = 실제 건물 좌표
A* = 경로 계산
TTS = 사용자 안내
```

로 역할을 분리하는 것이 구조적으로 적합합니다.
