package cn.zhangyi.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "subscriptions")
data class SubscriptionRow(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "rules", foreignKeys = [ForeignKey(entity = SubscriptionRow::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["subscriptionId", "effectiveFrom"], unique = true)])
data class RuleRow(@PrimaryKey val id: String, val subscriptionId: String, val effectiveFrom: String, val payload: String)
@Entity(tableName = "bills", foreignKeys = [ForeignKey(entity = SubscriptionRow::class, parentColumns = ["id"], childColumns = ["subscriptionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["subscriptionId", "due"])])
data class BillRow(@PrimaryKey val id: String, val subscriptionId: String, val due: String, val payload: String)
@Entity(tableName = "payments", foreignKeys = [ForeignKey(entity = BillRow::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = ForeignKey.CASCADE)])
data class PaymentRow(@PrimaryKey val billId: String, val payload: String)
// Durable cross-store restore journal. Until DataStore catches up, these settings are authoritative.
@Entity(tableName = "preference_restore")
data class PreferenceRestore(@PrimaryKey val id: Int = 1, val payload: String)

@Dao
interface LedgerDao {
    @Query("SELECT * FROM subscriptions") suspend fun subscriptions(): List<SubscriptionRow>
    @Query("SELECT * FROM rules") suspend fun rules(): List<RuleRow>
    @Query("SELECT * FROM bills") suspend fun bills(): List<BillRow>
    @Query("SELECT * FROM payments") suspend fun payments(): List<PaymentRow>
    @Query("SELECT * FROM preference_restore WHERE id = 1") suspend fun pendingPreferences(): PreferenceRestore?
    @Query("SELECT * FROM preference_restore WHERE id = 1") fun observePendingPreferences(): Flow<PreferenceRestore?>
    @Query("SELECT COUNT(*) FROM subscriptions") fun observeChanges(): Flow<Int>
    @Insert suspend fun insertSubscriptions(items: List<SubscriptionRow>)
    @Insert suspend fun insertRules(items: List<RuleRow>)
    @Insert suspend fun insertBills(items: List<BillRow>)
    @Insert suspend fun insertPayments(items: List<PaymentRow>)
    @Upsert suspend fun stagePreferences(item: PreferenceRestore)
    @Query("DELETE FROM preference_restore") suspend fun clearPreferences()
    @Query("DELETE FROM payments") suspend fun clearPayments()
    @Query("DELETE FROM bills") suspend fun clearBills()
    @Query("DELETE FROM rules") suspend fun clearRules()
    @Query("DELETE FROM subscriptions") suspend fun clearSubscriptions()
}

@Database(entities = [SubscriptionRow::class, RuleRow::class, BillRow::class, PaymentRow::class, PreferenceRestore::class], version = 1, exportSchema = true)
abstract class ZhangYiDatabase : RoomDatabase() { abstract fun ledger(): LedgerDao }
