package com.romanpanin.advancedalarm.service

import android.content.Context
import android.content.SharedPreferences
import com.romanpanin.advancedalarm.quiz.QuizEngine
import com.romanpanin.advancedalarm.quiz.QuizProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Состояние блокировки — единый источник правды для сервиса, экрана блокировки
 * и службы специальных возможностей. Дублируется в SharedPreferences, чтобы
 * пережить перезапуск процесса и перезагрузку телефона.
 */
object LockState {

    enum class Phase { IDLE, RINGING, LOCKED, QUIZ }

    data class Snapshot(
        val phase: Phase = Phase.IDLE,
        val alarmId: String = "",
        val alarmLabel: String = "",
        val ringOnly: Boolean = false,
        /** Момент начала звонка — от него считается длительность звука. */
        val startedAt: Long = 0L,
        /** Момент, когда истекает время блокировки и показываются задачи. */
        val unlockAt: Long = 0L,
        val quiz: List<QuizProblem> = emptyList(),
        val quizIndex: Int = 0,
        val mistakes: Int = 0
    ) {
        val active: Boolean get() = phase != Phase.IDLE
        val allSolved: Boolean get() = phase == Phase.QUIZ && quizIndex >= quiz.size
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _state.value = read()
    }

    fun current(): Snapshot = _state.value

    fun isActive(): Boolean = _state.value.active

    fun start(
        context: Context,
        alarmId: String,
        label: String,
        ringOnly: Boolean,
        durationMinutes: Int,
        taskCount: Int
    ) {
        init(context)
        val now = System.currentTimeMillis()
        update(
            Snapshot(
                phase = Phase.RINGING,
                alarmId = alarmId,
                alarmLabel = label,
                ringOnly = ringOnly,
                startedAt = now,
                unlockAt = now + durationMinutes * 60_000L,
                quiz = QuizEngine.generate(taskCount),
                quizIndex = 0,
                mistakes = 0
            )
        )
    }

    fun setPhase(phase: Phase) = update(current().copy(phase = phase))

    fun advanceQuiz() = update(current().copy(quizIndex = current().quizIndex + 1))

    fun addMistake() = update(current().copy(mistakes = current().mistakes + 1))

    fun release(context: Context) {
        init(context)
        _state.value = Snapshot()
        prefs?.edit()?.clear()?.apply()
    }

    private fun update(snapshot: Snapshot) {
        _state.value = snapshot
        persist(snapshot)
    }

    private fun persist(snapshot: Snapshot) {
        val json = JSONObject().apply {
            put(KEY_PHASE, snapshot.phase.name)
            put(KEY_ALARM_ID, snapshot.alarmId)
            put(KEY_LABEL, snapshot.alarmLabel)
            put(KEY_RING_ONLY, snapshot.ringOnly)
            put(KEY_STARTED_AT, snapshot.startedAt)
            put(KEY_UNLOCK_AT, snapshot.unlockAt)
            put(KEY_QUIZ_INDEX, snapshot.quizIndex)
            put(KEY_MISTAKES, snapshot.mistakes)
            put(KEY_QUIZ, JSONArray().apply {
                snapshot.quiz.forEach { put(it.toJson()) }
            })
        }
        prefs?.edit()?.putString(KEY_STATE, json.toString())?.apply()
    }

    private fun read(): Snapshot {
        val raw = prefs?.getString(KEY_STATE, null) ?: return Snapshot()
        return runCatching {
            val json = JSONObject(raw)
            val quizArray = json.optJSONArray(KEY_QUIZ) ?: JSONArray()
            val quiz = buildList {
                for (index in 0 until quizArray.length()) {
                    add(QuizProblem.fromJson(quizArray.getJSONObject(index)))
                }
            }
            Snapshot(
                phase = runCatching { Phase.valueOf(json.optString(KEY_PHASE)) }
                    .getOrDefault(Phase.IDLE),
                alarmId = json.optString(KEY_ALARM_ID),
                alarmLabel = json.optString(KEY_LABEL),
                ringOnly = json.optBoolean(KEY_RING_ONLY, false),
                startedAt = json.optLong(KEY_STARTED_AT, 0L),
                unlockAt = json.optLong(KEY_UNLOCK_AT, 0L),
                quiz = quiz,
                quizIndex = json.optInt(KEY_QUIZ_INDEX, 0),
                mistakes = json.optInt(KEY_MISTAKES, 0)
            )
        }.getOrDefault(Snapshot())
    }

    private const val PREFS_NAME = "advanced_alarm_lock_state"
    private const val KEY_STATE = "state"
    private const val KEY_PHASE = "phase"
    private const val KEY_ALARM_ID = "alarm_id"
    private const val KEY_LABEL = "label"
    private const val KEY_RING_ONLY = "ring_only"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_UNLOCK_AT = "unlock_at"
    private const val KEY_QUIZ = "quiz"
    private const val KEY_QUIZ_INDEX = "quiz_index"
    private const val KEY_MISTAKES = "mistakes"
}