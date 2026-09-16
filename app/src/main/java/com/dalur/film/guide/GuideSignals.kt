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
