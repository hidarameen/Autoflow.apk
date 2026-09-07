package com.autoflow.app.engine

import android.content.Context
import android.util.Log
import com.autoflow.app.action.ActionExecutor
import com.autoflow.app.action.ActionFailure
import com.autoflow.app.action.RuleStopped
import com.autoflow.app.data.Rule
import com.autoflow.app.data.RuleRepository
import com.autoflow.app.data.TriggerSpec
import com.autoflow.app.trigger.ScheduleManager
import com.autoflow.app.util.KnownPackages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Matches incoming [TriggerEvent]s against stored rules and runs the ones that fit.
 *
 * A process-wide object because triggers arrive from system services whose lifecycle the
 * app does not own and cannot inject into.
 */
object RuleEngine {

    private const val TAG = "AutoFlowEngine"

    private lateinit var appContext: Context
    private lateinit var repository: RuleRepository
    private lateinit var executor: ActionExecutor
    private lateinit var conditions: ConditionEvaluator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * UI automation drives a single physical screen, so two rules must never run at once
     * or they would fight over the foreground app.
     */
    /**
     * Serial work queue.
     *
     * UI automation drives one physical screen, so two rules must never interleave. A mutex
     * alone was not enough: every trigger spawned its own coroutine and they acquired the
     * lock in arbitrary order, so a burst of triggers ran scrambled. A single-consumer
     * channel keeps strict arrival order and gives the queue a bounded depth, which also
     * stops a notification storm from piling up thousands of pending runs.
     */
    private data class QueuedRun(
        val rule: Rule,
        val event: TriggerEvent,
        val respectCooldown: Boolean,
        val queuedAt: Long = System.currentTimeMillis(),
    )

    private val queue = Channel<QueuedRun>(capacity = QUEUE_CAPACITY)

    /** Observable depth so the UI can show that work is backing up. */
    val pending = MutableStateFlow(0)

    private val lastFired = mutableMapOf<Long, Long>()

    /** Last notification body seen per app, for the ignoreDuplicates filter. */
    private val lastNotificationText = mutableMapOf<String, String>()

    /** Guards against a RunRule cycle taking the process down. */
    private var nestingDepth = 0

    @Volatile
    private var initialised = false

    @Volatile
    private var cachedRules: List<Rule> = emptyList()

    @Volatile
    private var cachedWantsScreenText = false

    @Volatile
    private var cachedWantsChatScan = false

    fun init(context: Context) {
        if (initialised) return
        appContext = context.applicationContext
        repository = RuleRepository.get(appContext)
        conditions = ConditionEvaluator(appContext)
        executor = ActionExecutor(
            context = appContext,
            conditions = conditions,
            runNestedRule = ::runNested,
        )
        initialised = true

        // One collector keeps the hot-path cache current for the life of the process.
        scope.launch {
            // A destructive migration wipes the table silently, so restore before anything
            // else observes an empty rule set.
            runCatching { com.autoflow.app.data.RuleBackup.restoreIfEmpty(appContext, repository) }

            repository.observeRules().collect { rules ->
                cacheRules(rules)
                com.autoflow.app.data.RuleBackup.write(appContext, rules)
            }
        }

        // Exactly one consumer: this is what makes execution serial and ordered.
        scope.launch {
            for (job in queue) {
                pending.value = (pending.value - 1).coerceAtLeast(0)
                // A run that sat in the queue while the user kept typing is stale; drop it
                // rather than acting on a screen that has moved on.
                if (System.currentTimeMillis() - job.queuedAt > STALE_AFTER_MS) {
                    repository.log(
                        job.rule.id, job.rule.name, success = false,
                        message = "Skipped: waited too long in the queue",
                    )
                    continue
                }
                runCatching { execute(job) }
            }
        }
    }

    /** Entry point for every trigger source. Returns immediately; work happens off-thread. */
    fun submit(event: TriggerEvent) {
        if (!initialised) return

        // Accessibility events arrive dozens of times a second while a list scrolls. Reading
        // the rules from Room on each one made the service unresponsive, and Android then
        // disables an accessibility service that cannot keep up, so the set is cached here
        // and refreshed only when the rules themselves change.
        val rules = cachedRules
        if (rules.isEmpty()) return
        if (rules.none { it.trigger.listensTo(event.source) }) return

        scope.launch {
            rules.filter { matches(it, event) }.forEach { rule -> enqueue(rule, event, true) }
        }
    }

    /** True when any enabled rule cares about on-screen text or chat scanning. */
    fun wantsScreenText(): Boolean = cachedWantsScreenText

    fun wantsChatScan(): Boolean = cachedWantsChatScan

    /** Runs a rule regardless of its trigger. Used by "Run now". */
    fun runManually(rule: Rule) {
        if (!initialised) return
        scope.launch { enqueue(rule, TriggerEvent.manual(), respectCooldown = false) }
    }

    /** Called by the alarm receiver, which already knows exactly which rule to run. */
    fun runScheduled(ruleId: Long, event: TriggerEvent) {
        if (!initialised) return
        scope.launch {
            val rule = repository.rule(ruleId) ?: return@launch
            if (!rule.enabled) return@launch
            // A daily schedule may still be limited to certain days of the week.
            val trigger = rule.trigger
            if (trigger is TriggerSpec.Schedule) {
                val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                if (today !in trigger.days) return@launch
            }
            enqueue(rule, event, true)
        }
    }

    private fun cacheRules(rules: List<Rule>) {
        val enabled = rules.filter { it.enabled }
        cachedRules = enabled
        cachedWantsScreenText = enabled.any { it.trigger is TriggerSpec.ScreenText }
        cachedWantsChatScan = enabled.any { it.trigger is TriggerSpec.ChatMessage }
    }

    /** Re-registers every alarm. Called at startup and whenever rules change. */
    fun syncSchedules(rules: List<Rule>) {
        if (!initialised) return
        rules.forEach { rule ->
            ScheduleManager.cancel(appContext, rule.id)
            if (!rule.enabled) return@forEach
            when (val trigger = rule.trigger) {
                is TriggerSpec.Schedule ->
                    ScheduleManager.scheduleDaily(appContext, rule.id, trigger.hour, trigger.minute)

                is TriggerSpec.Interval ->
                    ScheduleManager.scheduleInterval(appContext, rule.id, trigger.minutes)

                else -> Unit
            }
        }
    }

    private suspend fun runNested(ruleName: String, parent: RunContext) {
        if (nestingDepth >= MAX_NESTING) throw ActionFailure("RunRule nested too deeply")
        val rule = repository.ruleByName(ruleName)
            ?: throw ActionFailure("no rule named \"$ruleName\"")

        nestingDepth++
        try {
            // The nested rule sees the parent's event so variables like {{text}} still work.
            executor.run(rule.actions, RunContext(parent.event))
        } finally {
            nestingDepth--
        }
    }

    // ---- Matching --------------------------------------------------------

    private fun matches(rule: Rule, event: TriggerEvent): Boolean = when (val trigger = rule.trigger) {
        is TriggerSpec.Notification -> {
            event.source == TriggerEvent.Source.NOTIFICATION &&
                (trigger.packageNames.isEmpty() || event.packageName in trigger.packageNames) &&
                trigger.title.matches(event.title) &&
                trigger.text.matches(event.text) &&
                !(trigger.ignoreDuplicates && isDuplicate(event))
        }

        is TriggerSpec.NotificationRemoved ->
            event.source == TriggerEvent.Source.NOTIFICATION_REMOVED &&
                (trigger.packageNames.isEmpty() || event.packageName in trigger.packageNames)

        is TriggerSpec.ScreenText ->
            event.source == TriggerEvent.Source.SCREEN_TEXT &&
                (trigger.packageName.isBlank() || trigger.packageName == event.packageName) &&
                trigger.text.isFilter &&
                trigger.text.matches(event.text)

        is TriggerSpec.AppOpened ->
            event.source == TriggerEvent.Source.APP_OPENED &&
                trigger.packageName == event.packageName

        is TriggerSpec.AppClosed ->
            event.source == TriggerEvent.Source.APP_CLOSED &&
                trigger.packageName == event.packageName

        is TriggerSpec.SmsReceived ->
            event.source == TriggerEvent.Source.SMS &&
                trigger.from.matches(event.title) &&
                trigger.body.matches(event.text)

        is TriggerSpec.BatteryLevel -> {
            event.source == TriggerEvent.Source.BATTERY &&
                if (trigger.below) event.value <= trigger.percent else event.value >= trigger.percent
        }

        is TriggerSpec.Headset ->
            event.source == TriggerEvent.Source.HEADSET &&
                (event.value == 1) == trigger.plugged

        // Schedules are dispatched by id from the alarm receiver, never by broadcast.
        is TriggerSpec.Schedule, is TriggerSpec.Interval -> false

        TriggerSpec.ScreenOn -> event.source == TriggerEvent.Source.SCREEN_ON
        TriggerSpec.ScreenOff -> event.source == TriggerEvent.Source.SCREEN_OFF
        TriggerSpec.DeviceUnlocked -> event.source == TriggerEvent.Source.DEVICE_UNLOCKED
        TriggerSpec.PowerConnected -> event.source == TriggerEvent.Source.POWER_CONNECTED
        TriggerSpec.PowerDisconnected -> event.source == TriggerEvent.Source.POWER_DISCONNECTED
        TriggerSpec.WifiConnected -> event.source == TriggerEvent.Source.WIFI_CONNECTED
        TriggerSpec.WifiDisconnected -> event.source == TriggerEvent.Source.WIFI_DISCONNECTED
        TriggerSpec.ClipboardChanged -> event.source == TriggerEvent.Source.CLIPBOARD
        TriggerSpec.AppInstalled -> event.source == TriggerEvent.Source.APP_INSTALLED
        TriggerSpec.AppUninstalled -> event.source == TriggerEvent.Source.APP_UNINSTALLED
        TriggerSpec.BootCompleted -> event.source == TriggerEvent.Source.BOOT
        TriggerSpec.Shake -> event.source == TriggerEvent.Source.SHAKE
        TriggerSpec.QuickTile -> event.source == TriggerEvent.Source.QUICK_TILE
        is TriggerSpec.TelegramBot ->
            event.source == TriggerEvent.Source.TELEGRAM_BOT &&
                (trigger.chatId.isBlank() || trigger.chatId.trim() == event.tag) &&
                trigger.sender.matches(event.title) &&
                trigger.text.matches(event.text)

        is TriggerSpec.ChatMessage ->
            event.source == TriggerEvent.Source.CHAT_MESSAGE &&
                (trigger.packageNames.isEmpty() || event.packageName in trigger.packageNames) &&
                trigger.sender.matches(event.title) &&
                trigger.text.matches(event.text)

        TriggerSpec.Manual -> false

        is TriggerSpec.WhatsAppMessage -> {
            event.source == TriggerEvent.Source.NOTIFICATION &&
                (event.packageName == KnownPackages.WHATSAPP || event.packageName == KnownPackages.WHATSAPP_BUSINESS) &&
                trigger.sender.matches(event.title) &&
                trigger.text.matches(event.text) &&
                !(trigger.ignoreDuplicates && isDuplicate(event))
        }

        is TriggerSpec.TelegramMessage -> {
            event.source == TriggerEvent.Source.NOTIFICATION &&
                (event.packageName == KnownPackages.TELEGRAM || event.packageName == KnownPackages.TELEGRAM_WEB) &&
                trigger.sender.matches(event.title) &&
                trigger.text.matches(event.text) &&
                !(trigger.ignoreDuplicates && isDuplicate(event))
        }

        is TriggerSpec.InstagramNotification -> {
            event.source == TriggerEvent.Source.NOTIFICATION &&
                event.packageName == KnownPackages.INSTAGRAM &&
                trigger.sender.matches(event.title) &&
                trigger.text.matches(event.text)
        }

        is TriggerSpec.YouTubeNotification -> {
            event.source == TriggerEvent.Source.NOTIFICATION &&
                event.packageName == KnownPackages.YOUTUBE &&
                trigger.channel.matches(event.title) &&
                trigger.title.matches(event.text)
        }

        is TriggerSpec.XNotification -> {
            event.source == TriggerEvent.Source.NOTIFICATION &&
                event.packageName == KnownPackages.X &&
                trigger.sender.matches(event.title) &&
                trigger.text.matches(event.text)
        }
    }

    private fun isDuplicate(event: TriggerEvent): Boolean {
        val key = event.packageName + "|" + event.title
        val previous = lastNotificationText[key]
        lastNotificationText[key] = event.text
        return previous == event.text
    }

    // ---- Execution -------------------------------------------------------

    private suspend fun enqueue(rule: Rule, event: TriggerEvent, respectCooldown: Boolean) {
        val job = QueuedRun(rule, event, respectCooldown)
        // Never block a system callback: if the queue is full the oldest work matters least.
        val accepted = queue.trySend(job).isSuccess
        if (accepted) {
            pending.value = pending.value + 1
        } else {
            repository.log(
                rule.id, rule.name, success = false,
                message = "Skipped: queue full (${'$'}QUEUE_CAPACITY waiting)",
            )
        }
    }

    private suspend fun execute(job: QueuedRun) {
        val rule = job.rule
        val event = job.event
        val respectCooldown = job.respectCooldown
        val now = System.currentTimeMillis()

        if (respectCooldown) {
            val previous = lastFired[rule.id] ?: rule.lastFiredAt
            if (now - previous < rule.cooldownMs) return
        }

        val run = RunContext(event)

        if (!conditions.evaluateAll(rule.conditions, rule.conditionLogic, run)) return

        if (rule.dailyLimit > 0 && repository.successesSince(rule.id, startOfToday()) >= rule.dailyLimit) {
            repository.log(rule.id, rule.name, success = false, message = "Daily limit reached")
            return
        }

        lastFired[rule.id] = now

        run {
            val startedAt = System.currentTimeMillis()
            try {
                executor.run(rule.actions, run)
                repository.markFired(rule.id, now)
                repository.log(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    success = true,
                    message = summarise(event),
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            } catch (stopped: RuleStopped) {
                repository.markFired(rule.id, now)
                repository.log(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    success = true,
                    message = "Stopped early by the rule",
                    durationMs = System.currentTimeMillis() - startedAt,
                )
            } catch (failure: ActionFailure) {
                repository.log(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    success = false,
                    message = failure.message.orEmpty(),
                    durationMs = System.currentTimeMillis() - startedAt,
                )
                Log.w(TAG, "rule '${rule.name}' failed: ${failure.message}")
            } catch (error: Exception) {
                repository.log(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    success = false,
                    message = "Unexpected: ${error.message}",
                    durationMs = System.currentTimeMillis() - startedAt,
                )
                Log.e(TAG, "rule '${rule.name}' crashed", error)
            }
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun summarise(event: TriggerEvent): String = when (event.source) {
        TriggerEvent.Source.NOTIFICATION -> "From ${event.appLabel}: ${event.text.take(80)}"
        TriggerEvent.Source.SMS -> "SMS from ${event.title}: ${event.text.take(60)}"
        TriggerEvent.Source.SCREEN_TEXT -> "Screen text in ${event.packageName}"
        TriggerEvent.Source.APP_OPENED -> "${event.packageName} opened"
        TriggerEvent.Source.APP_CLOSED -> "${event.packageName} closed"
        TriggerEvent.Source.BATTERY -> "Battery at ${event.value}%"
        TriggerEvent.Source.MANUAL -> "Run manually"
        else -> event.source.name.lowercase().replace('_', ' ')
    }

    private const val MAX_NESTING = 5
    private const val QUEUE_CAPACITY = 64

    /** A queued run older than this is acting on a screen that has already changed. */
    private const val STALE_AFTER_MS = 90_000L
}
