package com.dalur.film.guide

import kotlin.math.abs

/**
 * GUIDE Phase 1 — Headroom + Subject Position (+ Look Room signal computed
 * here too, ready for Phase 2 to surface it; see the GUIDE phase plan).
 *
 * Signal vs Hint: everything in this file computes a STABLE, PERSISTENT fact
 * about the frame (a Signal). It does not decide what to show on screen or
 * how often — that's the Hint layer's job (EasyCameraScreen today; a proper
 * Evaluator/Priority/Hint state machine in Phase 3). Keeping this file free
 * of UI/display concerns is what lets Phase 3 add Orientation/Movement later
 * without reworking this layer.
 *
 * One raw face reading per analyzed frame, already normalized 0..1 against
 * the frame so callers never touch pixels.
 */
data class FaceMetrics(
    /** face box height / frame height — same ratio [shotScaleForFaceRatio] uses. */
    val heightRatio: Float,
    /** face box horizontal center / frame width. */
    val centerXRatio: Float,
    /** face box top / frame height — negative if ML Kit's box runs above the frame. */
    val topRatio: Float,
    /** ML Kit's headEulerAngleY (left/right head turn), degrees. */
    val yawDegrees: Float,
)

enum class Headroom { TOO_TIGHT, GOOD, TOO_MUCH }
enum class SubjectPosition { LEFT_THIRD, CENTER, RIGHT_THIRD }
enum class LookRoom { LOOK_LEFT, NEUTRAL, LOOK_RIGHT }

// ---- Subject position: rule-of-thirds bucket, with hysteresis ----
// The raw split is at 40%/60%; once settled in a bucket, the reading has to
// cross back past the OTHER side's line to switch — not just re-cross 40/60 —
// so a face sitting right on a boundary doesn't flicker between labels.
private const val POS_LEFT_EXIT = 0.42f
private const val POS_CENTER_EXIT_LOW = 0.38f
private const val POS_CENTER_EXIT_HIGH = 0.62f
private const val POS_RIGHT_EXIT = 0.58f

private fun bucketPosition(x: Float): SubjectPosition = when {
    x < 0.4f -> SubjectPosition.LEFT_THIRD
    x > 0.6f -> SubjectPosition.RIGHT_THIRD
    else -> SubjectPosition.CENTER
}

fun stableSubjectPosition(centerXRatio: Float, previous: SubjectPosition?): SubjectPosition =
    when (previous) {
        null -> bucketPosition(centerXRatio)
        SubjectPosition.LEFT_THIRD ->
            if (centerXRatio > POS_LEFT_EXIT) bucketPosition(centerXRatio) else previous
        SubjectPosition.CENTER ->
            if (centerXRatio < POS_CENTER_EXIT_LOW || centerXRatio > POS_CENTER_EXIT_HIGH)
                bucketPosition(centerXRatio) else previous
        SubjectPosition.RIGHT_THIRD ->
            if (centerXRatio < POS_RIGHT_EXIT) bucketPosition(centerXRatio) else previous
    }

// ---- Headroom: ideal top-of-frame gap depends on shot scale ----
// Heuristic starting bounds (fraction of frame height above the face box);
// tune against real footage once this ships. A CLOSE_UP has almost no room
// to give, a wide/extreme-full shot tolerates a lot more.
private fun headroomBounds(scale: ShotScale): ClosedFloatingPointRange<Float> = when (scale) {
    ShotScale.CLOSE_UP -> 0.02f..0.10f
    ShotScale.BUST -> 0.04f..0.14f
    ShotScale.KNEE -> 0.05f..0.16f
    ShotScale.FULL -> 0.06f..0.18f
    ShotScale.EXTREME_FULL -> 0.06f..0.22f
}
private const val HEADROOM_MARGIN = 0.03f

private fun bucketHeadroom(topRatio: Float, bounds: ClosedFloatingPointRange<Float>): Headroom = when {
    topRatio < bounds.start -> Headroom.TOO_TIGHT
    topRatio > bounds.endInclusive -> Headroom.TOO_MUCH
    else -> Headroom.GOOD
}

fun stableHeadroom(topRatio: Float, scale: ShotScale, previous: Headroom?): Headroom {
    val bounds = headroomBounds(scale)
    return when (previous) {
        null -> bucketHeadroom(topRatio, bounds)
        Headroom.TOO_TIGHT ->
            if (topRatio > bounds.start + HEADROOM_MARGIN) bucketHeadroom(topRatio, bounds) else previous
        Headroom.GOOD ->
            if (topRatio < bounds.start - HEADROOM_MARGIN || topRatio > bounds.endInclusive + HEADROOM_MARGIN)
                bucketHeadroom(topRatio, bounds) else previous
        Headroom.TOO_MUCH ->
            if (topRatio < bounds.endInclusive - HEADROOM_MARGIN) bucketHeadroom(topRatio, bounds) else previous
    }
}

// ---- Look room: which way the subject is facing (Phase 2 surfaces this) ----
private const val LOOK_THRESHOLD = 12f
private const val LOOK_MARGIN = 5f

private fun bucketLook(yaw: Float): LookRoom = when {
    yaw > LOOK_THRESHOLD -> LookRoom.LOOK_LEFT
    yaw < -LOOK_THRESHOLD -> LookRoom.LOOK_RIGHT
    else -> LookRoom.NEUTRAL
}

fun stableLookRoom(yawDegrees: Float, previous: LookRoom?): LookRoom = when (previous) {
    null -> bucketLook(yawDegrees)
    LookRoom.NEUTRAL ->
        if (abs(yawDegrees) > LOOK_THRESHOLD + LOOK_MARGIN) bucketLook(yawDegrees) else previous
    LookRoom.LOOK_LEFT ->
        if (yawDegrees < LOOK_THRESHOLD - LOOK_MARGIN) bucketLook(yawDegrees) else previous
    LookRoom.LOOK_RIGHT ->
        if (yawDegrees > -LOOK_THRESHOLD + LOOK_MARGIN) bucketLook(yawDegrees) else previous
}

// ---- GUIDE Phase 4 — Orientation (device tilt, sensor-only, no face needed) ----
enum class CameraAngle { TOP_DOWN, EXTREME_LOW, LOW_ANGLE, EYE_LEVEL, HIGH_ANGLE }

private const val ANGLE_MARGIN = 5f

private fun bucketAngle(pitchDegrees: Float): CameraAngle = when {
    pitchDegrees > 60f -> CameraAngle.TOP_DOWN
    pitchDegrees > 15f -> CameraAngle.HIGH_ANGLE
    pitchDegrees < -60f -> CameraAngle.EXTREME_LOW
    pitchDegrees < -15f -> CameraAngle.LOW_ANGLE
    else -> CameraAngle.EYE_LEVEL
}

/**
 * pitchDegrees: positive = lens tilted DOWN toward the ground, negative =
 * tilted UP toward the sky, from the rotation-vector sensor remapped for a
 * phone held upright like a viewfinder (see EasyCameraScreen's sensor
 * listener). The remap's sign convention is a common Android pattern but
 * hasn't been confirmed against this specific device yet — verify TOP_DOWN
 * really fires when the phone points at the floor before shipping this.
 */
fun stableCameraAngle(pitchDegrees: Float, previous: CameraAngle?): CameraAngle {
    if (previous == null) return bucketAngle(pitchDegrees)
    val staysInBand = when (previous) {
        CameraAngle.TOP_DOWN -> pitchDegrees > 60f - ANGLE_MARGIN
        CameraAngle.HIGH_ANGLE -> pitchDegrees > 15f - ANGLE_MARGIN && pitchDegrees < 60f + ANGLE_MARGIN
        CameraAngle.EYE_LEVEL -> pitchDegrees > -15f - ANGLE_MARGIN && pitchDegrees < 15f + ANGLE_MARGIN
        CameraAngle.LOW_ANGLE -> pitchDegrees < -15f + ANGLE_MARGIN && pitchDegrees > -60f - ANGLE_MARGIN
        CameraAngle.EXTREME_LOW -> pitchDegrees < -60f + ANGLE_MARGIN
    }
    return if (staysInBand) previous else bucketAngle(pitchDegrees)
}

private const val DUTCH_THRESHOLD = 4f
private const val DUTCH_MARGIN = 2f

/** Whether the horizon is tilted enough to call it a Dutch angle, with
 *  hysteresis so a roll sitting right at the threshold doesn't flicker. */
fun stableDutch(rollDegrees: Float, previouslyDutch: Boolean): Boolean =
    if (previouslyDutch) abs(rollDegrees) > DUTCH_THRESHOLD - DUTCH_MARGIN
    else abs(rollDegrees) > DUTCH_THRESHOLD + DUTCH_MARGIN

// ---- GUIDE Phase 5 — Movement Room (face-position tracking) ----
enum class MovementDirection { LEFT, RIGHT, NONE }

/**
 * Face-position tracking is noisier frame to frame than a single bounding-
 * box reading (ML Kit's box jitters a little even for a still subject), so
 * this needs its OWN smoothing on top of the Subject Position hysteresis
 * above: a direction only counts once it holds for [MOVE_CONFIRM_FRAMES]
 * consecutive readings past a deadzone, not off one frame's delta. Stateful
 * (needs a short history) unlike the stateless stable*() functions above —
 * keep one instance per camera session and call [update] on every new
 * FaceMetrics; call [reset] when the face is lost so a re-appearing face
 * doesn't get credited with a fake jump.
 */
class MovementTracker {
    private var lastX: Float? = null
    private var streakDirection: Int = 0
    private var streakCount: Int = 0

    fun update(centerXRatio: Float): MovementDirection {
        val prev = lastX
        lastX = centerXRatio
        if (prev == null) return MovementDirection.NONE
        val delta = centerXRatio - prev
        val dir = when {
            delta > MOVE_DEADZONE -> 1
            delta < -MOVE_DEADZONE -> -1
            else -> 0
        }
        if (dir != 0 && dir == streakDirection) streakCount++
        else { streakDirection = dir; streakCount = if (dir != 0) 1 else 0 }
        return if (streakCount >= MOVE_CONFIRM_FRAMES) {
            if (streakDirection > 0) MovementDirection.RIGHT else MovementDirection.LEFT
        } else MovementDirection.NONE
    }

    fun reset() {
        lastX = null
        streakDirection = 0
        streakCount = 0
    }

    private companion object {
        const val MOVE_DEADZONE = 0.015f
        const val MOVE_CONFIRM_FRAMES = 3
    }
}

// ---- GUIDE Phase 5 — Stability + camera movement (sensor, differentiated) ----
enum class Stability { STABLE, SHAKY }
enum class CameraMovement { STATIC, PAN, TILT }

private const val SHAKE_ENTER = 25f // combined |Δpitch|+|Δroll|+|Δazimuth|, deg/sec
private const val SHAKE_EXIT = 15f

/** Real technical defect (motion-blurred footage), not a style choice —
 *  unlike Orientation, this one genuinely warrants a persistent warning. */
fun stableStability(combinedRateDegPerSec: Float, previous: Stability?): Stability = when (previous) {
    null, Stability.STABLE -> if (combinedRateDegPerSec > SHAKE_ENTER) Stability.SHAKY else Stability.STABLE
    Stability.SHAKY -> if (combinedRateDegPerSec < SHAKE_EXIT) Stability.STABLE else Stability.SHAKY
}

private const val PAN_TILT_THRESHOLD = 8f // deg/sec

/**
 * Distinguishes a deliberate pan/tilt from holding still, reusing the SAME
 * remapped orientation angles Phase 4 already computes (differentiated over
 * time in EasyCameraScreen's sensor listener) — one sensor pipeline, not a
 * second raw gyroscope reading. Computed for future use; not wired into a
 * Hint yet (PAN/TILT are usually deliberate cinematography, and STATIC would
 * otherwise be on screen almost all the time, which fights "silence is
 * default"). Thresholds and the pan/tilt axis mapping inherit Phase 4's
 * unverified sign convention — recheck together once that's calibrated.
 */
fun classifyCameraMovement(panRateDegPerSec: Float, tiltRateDegPerSec: Float): CameraMovement {
    val panMag = abs(panRateDegPerSec)
    val tiltMag = abs(tiltRateDegPerSec)
    return when {
        panMag < PAN_TILT_THRESHOLD && tiltMag < PAN_TILT_THRESHOLD -> CameraMovement.STATIC
        panMag >= tiltMag -> CameraMovement.PAN
        else -> CameraMovement.TILT
    }
}

// ---- GUIDE Phase 3 (mini) — Evaluator/Priority step ----
// A candidate list is already IN PRIORITY ORDER (index 0 = shown first, per
// the confirmed order: Headroom > Subject Position > Look Room). Each signal
// decides its OWN on-screen timing (Headroom stays up while the problem
// holds; Position/Look Room flash briefly on change — see the
// LaunchedEffect(...)+delay pattern in EasyCameraScreen) by simply being
// null in this list when it has nothing to say right now. This function only
// arbitrates BETWEEN signals — exactly one wins, or none do. That "none do"
// case is the normal state, not a fallback.
fun pickHint(candidatesInPriorityOrder: List<String?>): String? =
    candidatesInPriorityOrder.firstOrNull { it != null }
