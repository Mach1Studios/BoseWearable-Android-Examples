# bosewearable-ktx

`bosewearable-ktx` adds [Kotlin coroutines](https://kotlinlang.org/docs/reference/coroutines/coroutines-guide.html) support to `bosewearable` SDK to replace callback style programming with suspending methods. Running suspending methods from a coroutine allows to write sequential code that suspends its coroutine (without blocking its thread!) and resumes the next line of code once the suspending method completes. Additionally, coroutines provide "structured concurrency", which helps with lifecycle management and ensures automatic cleanup once the connection is closed.
See `HeadingExample` sample application for a complete working example on how to use the library.

## 1. Import `bosewearable-ktx` to your project:
Edit your application's `build.gradle` and add the dependency:
```
dependencies {
    ...
    implementation "com.github.BoseCorp.BoseWearable-Android:bosewearable-ktx:$version"
}
```

## 2. Get a `ScopedSession`
A `ScopedSession` is a `Session` with an attached `CoroutineScope`. All suspending `WearableDevice` methods should run within the scope of `ScopedSession` in order to get them all cancelled automatically when the `Session` closes. If you have another `CoroutineScope` that you'd like to use to control the lifecycle of the `Session`, pass it along with the optional argument `context` (For example, if you use a `ViewModel`, passing its `viewModelScope.coroutineContext` would automatically close the `Session` and cancel all pending `WearableDevice` suspending functions when the `ViewModel` is cleared).

```kotlin
// Get a ScopedSession using the 'scopedSession()' extension method on BluetoothManager
val scopedSession = BoseWearable.getInstance()
    .bluetoothManager()
    .scopedSession(discoveredDevice, context = viewModelScope.coroutineContext)

// Register a callback when the Session is closed
scopedSession.coroutineContext[Job]!!.invokeOnCompletion { handler ->
    // Session was closed.
    if (handler?.cause is DeviceException) {
        // Session closed with an error (and _not_ as a result of the app calling Session.close())
    }
}
```

## 3. Configure sensors and gestures
The extension library provides suspending extension methods to change the sensors and gestures configurations. Running them from `ScopedSession` context ensures that the pending operations get automatically cancelled once the `Session` is closed.

```kotlin
scopedSession.launch {
    val device = scopedSession.device() as WearableDevice
    val sensorConf = device.sensorConfiguration()
        .disableAll()
        .enableSensor(SensorType.ACCELEROMETER, SamplePeriod._20_MS)
        .enableSensor(SensorType.ROTATION_VECTOR, SamplePeriod._20_MS)

    try {
        // Configure sensors and suspend this coroutine until the operation completes.
        val updatedSensorConf = device.configureSensors(sensorConf)
    } catch (e: BoseWearableException) {
        // Sensor configuration failed for some reason
    }
}
```

## 4. Monitor sensors and gestures
The extension library provides suspending extension methods to observe the sensors and gestures. Running them from `ScopedSession` context ensures that the operation gets automatically cancelled once the `Session` is closed.

```kotlin
val monitorSensorsJob = scopedSession.launch {
    val device = scopedSession.device() as WearableDevice
    // Suspend the coroutine until it is cancelled
    device.monitorSensors()
        .collect { sensorValue ->
            // Called for each reported SensorValue
        }
}

// If you want to stop monitoring the sensors before it is cancelled
// automatically when the Session is closed
monitorSensorsJob.cancel()
```

## 5. The complete list of suspending extension methods for `WearableDevice` class:

```kotlin
refreshDeviceInfo(): DeviceInformation
refreshWearableDeviceInfo(): WearableDeviceInformation
refreshDeviceProps(): DeviceProperties

refreshSensorInfo(): SensorInformation
refreshSensors(): SensorConfiguration
configureSensors(config: SensorConfiguration): SensorConfiguration
monitorSensors(): Flow<SensorValue>

refreshGestures(): GestureConfiguration
configureGestures(config: GestureConfiguration): GestureConfiguration
monitorGestures(): Flow<GestureData>

gainFocus(): Unit
monitorFocus(): Flow<Boolean>
monitorSuspension(): Flow<SuspensionState>
```
Additionally, there's an extension method for `Quaternion` that adds multiplication operator:
```kotlin
val TRANSLATION = Quaternion(1.0, 0.0, 0.0, 0.0)
val quaternion = sensorValue.quaternion()!! * TRANSLATION
```
