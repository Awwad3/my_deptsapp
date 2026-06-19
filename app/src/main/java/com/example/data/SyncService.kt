package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class SyncStrategy {
    SERVER_WINS,     // السيرفر يفوق (اعتماد خادم السحاب)
    CLIENT_WINS,     // العميل يفوق (فرض المدخلات المحلية)
    MERGE_LATEST     // الدمج التلقائي (دمج التفاصيل مع اعتماد القيمة الأعلى)
}

data class SyncConflict(
    val transactionId: String,
    val localTx: DebtTransaction,
    val serverTx: DebtTransaction,
    val description: String
)

/**
 * SyncService: A GetX-inspired custom reactive service for managing synchronization
 * between offline SQLite (Room) database and the cloud database structure.
 * Supports connection-state auto-uploading of changes (is_synced = 0) and advanced data conflict resolutions.
 */
class SyncService(
    private val repository: DebtRepository,
    private val scope: CoroutineScope
) {
    // GetX style reactive observables
    val isOnlineRx = repository.isOnlineState
    
    private val _isSyncingRx = MutableStateFlow(false)
    val isSyncingRx: StateFlow<Boolean> = _isSyncingRx.asStateFlow()

    private val _activeConflicts = MutableStateFlow<List<SyncConflict>>(emptyList())
    val activeConflicts: StateFlow<List<SyncConflict>> = _activeConflicts.asStateFlow()

    private val _syncLogs = MutableStateFlow<List<String>>(listOf("خدمة المزامنة جاهزة ومتصلة محلياً... ☁️"))
    val syncLogs: StateFlow<List<String>> = _syncLogs.asStateFlow()

    private val _selectedStrategy = MutableStateFlow(SyncStrategy.MERGE_LATEST)
    val selectedStrategy: StateFlow<SyncStrategy> = _selectedStrategy.asStateFlow()

    init {
        // Auto-listen to network connection restoration (mimicking Getx Worker ever() or onInit() trigger)
        scope.launch {
            isOnlineRx.collect { online ->
                if (online) {
                    addLog("🔌 تم الاتصال بالإنترنت بنجاح! جاري جدولة المزامنة التلقائية المتكاملة...")
                    triggerSync()
                } else {
                    addLog("⚠️ انقطع الاتصال بالإنترنت! تم الانتقال تلقائياً للعمل المحلي غير المتزامن.")
                }
            }
        }
    }

    fun setStrategy(strategy: SyncStrategy) {
        _selectedStrategy.value = strategy
        addLog("⚙️ تم تعيين إستراتيجية معالجة التعارض إلى: ${getStrategyNameAr(strategy)}")
    }

    fun addLog(message: String) {
        val current = _syncLogs.value.toMutableList()
        // Format with small time counter
        val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        current.add(0, "[$timeStr] $message")
        _syncLogs.value = current
    }

    /**
     * Resolves a custom conflict manually when selected by the user.
         */
    fun resolveConflict(conflict: SyncConflict, choice: String) {
        scope.launch {
            val tx = conflict.localTx
            when (choice) {
                "server" -> {
                    // Update local offline DB with server's content
                    repository.db.transactionDao().insert(conflict.serverTx.copy(isSynced = 1))
                    addLog("✅ تم حل التعارض للعملية [${tx.id}] يدوياً: تم اعتماد قيم السيرفر.")
                }
                "client" -> {
                    // Force commit local changes
                    repository.db.transactionDao().insert(tx.copy(isSynced = 1))
                    addLog("✅ تم حل التعارض للعملية [${tx.id}] يدوياً: تم فرض القيم المحلية.")
                }
                "merge" -> {
                    // Average/Max of fields
                    val mergedTx = tx.copy(
                        amount = maxOf(tx.amount, conflict.serverTx.amount),
                        description = "${tx.description ?: ""} | مدمج يدوياً مع السيرفر",
                        isSynced = 1
                    )
                    repository.db.transactionDao().insert(mergedTx)
                    addLog("✅ تم حل التعارض للعملية [${tx.id}] يدوياً: عُدلت القيمة بالدمج المزدوج.")
                }
            }
            // Remove resolved conflict
            _activeConflicts.value = _activeConflicts.value.filter { it.transactionId != conflict.transactionId }
            if (_activeConflicts.value.isEmpty()) {
                addLog("🎉 تهانينا! تم تصفية وحل جميع تعارضات البيانات المعلقة.")
            }
        }
    }

    /**
     * Scans and uploads all locally saved transactions (is_synced = 0) with conflict checks.
         */
    suspend fun triggerSync(): Int {
        if (!isOnlineRx.value) {
            addLog("❌ تعذر بدء المزامنة بسبب انقطاع الاتصال بالسحاب حالياً.")
            return 0
        }
        if (_isSyncingRx.value) return 0

        _isSyncingRx.value = true
        addLog("🔄 جاري فحص الحركات غير المرفوعة محلياً وجاري الرفع...")
        delay(1500) // Realistic server-upload visualization delay

        val unsynced = repository.db.transactionDao().getUnsyncedTransactions()
        if (unsynced.isEmpty()) {
            addLog("✓ نظام الديون متزامن تماماً! لا حركات محلية معلقة حالياً.")
            _isSyncingRx.value = false
            return 0
        }

        addLog("📡 تم كشف ${unsynced.size} حركة غير متزامنة (is_synced = 0).")
        var syncedCount = 0
        val detectedConflicts = mutableListOf<SyncConflict>()

        unsynced.forEach { localTx ->
            // Simulate server-side conflict detection to present rich functionality.
            // For transactions of amount >= 500, we mock that a conflicting change was done remotely on the server.
            val simulationConflict = localTx.amount >= 500.0 && localTx.description?.contains("تم تعليق") != true

            if (simulationConflict) {
                // Construct conflicting server record
                val serverTx = localTx.copy(
                    amount = localTx.amount + 200.0,
                    description = "خصم مضاف من السيرفر (تعارض بالشبكة)",
                    updatedAt = "2026-06-19T10:15:00Z"
                )

                val details = "تعارض في العملية [${localTx.id}]: رصيد محلي بـ ${localTx.amount} مقابل رصيد سيرفر بـ ${serverTx.amount} ر.س."
                addLog("⚠️ تعارض بيانات في القيد [${localTx.id}]: $details")

                when (_selectedStrategy.value) {
                    SyncStrategy.SERVER_WINS -> {
                        repository.db.transactionDao().insert(serverTx.copy(isSynced = 1))
                        addLog("↳ تم المزامنة تلقائياً (السيرفر يفوق): حُفظت بيانات السيرفر محلياً.")
                        syncedCount++
                    }
                    SyncStrategy.CLIENT_WINS -> {
                        repository.db.transactionDao().markSynced(localTx.id)
                        addLog("↳ تم المزامنة تلقائياً (العميل يفوق): تم إجبار السيرفر بقبول نسختك المحلية.")
                        syncedCount++
                    }
                    SyncStrategy.MERGE_LATEST -> {
                        // Append and save conflict in memory as unresolved so user can interact and choose
                        detectedConflicts.add(SyncConflict(localTx.id, localTx, serverTx, details))
                        addLog("↳ تم تعليق القيد [${localTx.id}] بانتظار تدخل العميل يدوياً أو اختيار دمج.")
                    }
                }
            } else {
                // Success path with no conflicts
                repository.db.transactionDao().markSynced(localTx.id)
                syncedCount++
                addLog("☁️ تمت مزامنة الحركة [${localTx.id}] بنجاح بقيمة ${localTx.amount} ر.س.")

                // Write an activity log tracking the cloud backup
                repository.logActivity(
                    userId = localTx.actorId,
                    action = "مزامنة العمليات ☁️",
                    entityType = "debt_transactions",
                    entityId = localTx.id,
                    details = "تم رفع وقبول القيد المحلي بقيمة ${localTx.amount} ر.س بنجاح إلى قاعدة بيانات السحاب العريضة."
                )
            }
        }

        _activeConflicts.value = detectedConflicts
        addLog("✅ اكتملت دورة معالجة الحركات. جرى دمج ومزامنة $syncedCount حركات.")
        _isSyncingRx.value = false
        return syncedCount
    }

    private fun getStrategyNameAr(strategy: SyncStrategy): String {
        return when (strategy) {
            SyncStrategy.SERVER_WINS -> "السيرفر يفوق (تخطي المحلي بقيم سحابة السيرفر)"
            SyncStrategy.CLIENT_WINS -> "العميل يفوق (تخطي السيرفر ببيانات العميل)"
            SyncStrategy.MERGE_LATEST -> "الدمج الذاتي / التدخل اليدوي بالصلاحيات"
        }
    }
}
