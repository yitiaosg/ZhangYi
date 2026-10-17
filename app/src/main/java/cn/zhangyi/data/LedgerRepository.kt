package cn.zhangyi.data

import androidx.room.withTransaction
import cn.zhangyi.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.time.LocalDate

class LedgerRepository(private val db: ZhangYiDatabase, private val preferences: PreferenceStore) {
    private val dao = db.ledger()
    private val lock = Mutex()
    private val json = BackupCodec.json
    val data: Flow<LedgerData> = dao.observeChanges().map { read() }.flowOn(Dispatchers.IO)
    val settings: Flow<Preferences> = combine(preferences.flow, dao.observePendingPreferences()) { p, pending -> pending?.let { json.decodeFromString<Preferences>(it.payload) } ?: p }

    suspend fun read(): LedgerData = db.withTransaction {
        LedgerData(dao.subscriptions().map { json.decodeFromString(it.payload) }, dao.rules().map { json.decodeFromString(it.payload) }, dao.bills().map { json.decodeFromString(it.payload) }, dao.payments().map { json.decodeFromString(it.payload) })
    }
    private suspend fun write(d: LedgerData) {
        dao.clearPayments(); dao.clearBills(); dao.clearRules(); dao.clearSubscriptions()
        dao.insertSubscriptions(d.subscriptions.map { SubscriptionRow(it.id, json.encodeToString(it)) })
        dao.insertRules(d.rules.map { RuleRow(it.id, it.subscriptionId, it.effectiveFrom, json.encodeToString(it)) })
        dao.insertBills(d.bills.map { BillRow(it.id, it.subscriptionId, it.due, json.encodeToString(it)) })
        dao.insertPayments(d.payments.map { PaymentRow(it.billId, json.encodeToString(it)) })
    }
    suspend fun update(action: (LedgerData) -> LedgerData) = withContext(Dispatchers.IO) {
        lock.withLock { db.withTransaction { val old = read(); val next = action(old); if (next != old) write(next) } }
    }
    suspend fun ensureThrough(through: LocalDate) = update { Ledger.generate(it, through) }
    suspend fun backup(): String = withContext(Dispatchers.IO) { lock.withLock { BackupCodec.encode(read(), settings.first()) } }
    suspend fun restore(backup: Backup) = withContext(Dispatchers.IO) {
        BackupCodec.validate(backup)
        val restored = Ledger.generate(backup.data, java.time.YearMonth.now().plusMonths(1).atEndOfMonth())
        lock.withLock {
            db.withTransaction { write(restored); dao.stagePreferences(PreferenceRestore(payload = json.encodeToString(backup.settings))) }
            // DB commit is the restore boundary; journal survives any crash or DataStore IO failure.
            replayPreferences()
        }
    }
    private suspend fun replayPreferences() {
        val pending = dao.pendingPreferences() ?: return
        try {
            preferences.write(json.decodeFromString(pending.payload))
            dao.clearPreferences()
        } catch (_: java.io.IOException) { /* Settings remain available via journal and are retried next launch/edit. */ }
    }
    suspend fun initialize() = withContext(Dispatchers.IO) { lock.withLock { replayPreferences() } }
    suspend fun setPreferences(p: Preferences) = withContext(Dispatchers.IO) {
        lock.withLock {
            db.withTransaction { dao.stagePreferences(PreferenceRestore(payload = json.encodeToString(p))) }
            replayPreferences()
        }
    }
}
