package cn.zhangyi

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.zhangyi.data.*
import cn.zhangyi.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: ZhangYiDatabase
    private lateinit var repository: LedgerRepository
    @Before fun before() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ZhangYiDatabase::class.java).build(); repository = LedgerRepository(db, PreferenceStore(ApplicationProvider.getApplicationContext())) }
    @After fun after() { db.close() }
    private fun sample() = Ledger.add(LedgerData(), Values("测试订阅", 3000, color = Palette.colors.first()), Cycle(), LocalDate.now(), true, true, LocalDate.now())

    @Test fun roomPersistsAndRepeatedRestoreDoesNotDuplicate() = runBlocking {
        val d = sample()
        repository.update { d }
        val raw = repository.backup()
        val backup = BackupCodec.decode(raw)
        repository.restore(backup); repository.restore(backup)
        assertEquals(d, repository.read())
        assertEquals(d.bills.size, repository.read().bills.map { it.id }.distinct().size)
    }
    @Test fun brokenImportNeverChangesExistingData() = runBlocking {
        val d = sample()
        repository.update { d }
        val backup = BackupCodec.decode(repository.backup())
        val corrupt = backup.copy(data = backup.data.copy(payments = listOf(Payment("orphan", LocalDate.now().toString(), 1))))
        try { repository.restore(corrupt); fail("must reject") } catch (_: IllegalArgumentException) {}
        assertEquals(d, repository.read())
    }
    @Test fun databaseTransactionRollsBackOnFailure() = runBlocking {
        val d = sample()
        repository.update { d }
        try { db.withTransaction { db.ledger().clearSubscriptions(); throw java.io.IOException("simulated write failure") } } catch (_: java.io.IOException) {}
        assertEquals(d, repository.read())
    }
    @Test fun pendingSettingsSurviveCommitAndReplayIntoDataStore() = runBlocking {
        val target = Preferences("DARK", false, "JPY")
        db.ledger().stagePreferences(PreferenceRestore(payload = BackupCodec.json.encodeToString(target)))
        assertEquals(target, repository.settings.first())
        repository.initialize()
        assertNull(db.ledger().pendingPreferences())
        assertEquals(target, repository.settings.first())
        repository.setPreferences(Preferences())
    }
}
