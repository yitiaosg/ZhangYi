package cn.zhangyi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.zhangyi.ZhangYiApplication
import cn.zhangyi.domain.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as ZhangYiApplication).repository
    val fatalError = MutableStateFlow<String?>(null)
    val data = repository.data.catch { fatalError.value = "本地账本读取失败：${it.message}" }.stateIn(viewModelScope, SharingStarted.Eagerly, LedgerData())
    val settings = repository.settings.catch { fatalError.value = "设置读取失败：${it.message}" }.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())
    val ready = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    init { refresh() }
    fun refresh() { viewModelScope.launch { runCatching { repository.initialize(); repository.ensureThrough(YearMonth.now().plusMonths(1).atEndOfMonth()) }.onSuccess { ready.value = true; fatalError.value = null }.onFailure { fatalError.value = it.message ?: "本地数据库读取失败" } } }
    fun ensureMonth(month: YearMonth) { viewModelScope.launch { runCatching { repository.ensureThrough(maxOf(month.atEndOfMonth(), YearMonth.now().plusMonths(1).atEndOfMonth())) }.onFailure { tell(it.message ?: "账单生成失败") } } }
    fun change(message: String, onDone: () -> Unit = {}, action: (LedgerData) -> LedgerData) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try { repository.update { Ledger.generate(action(it), maxOf(YearMonth.now().plusMonths(1).atEndOfMonth(), it.bills.maxOfOrNull { b -> date(b.due) } ?: LocalDate.now())) }; tell(message); onDone() }
            catch (e: Exception) { tell(e.message ?: "操作失败，原数据已保留") }
            finally { busy.value = false }
        }
    }
    fun setPreferences(p: Preferences) { viewModelScope.launch { runCatching { repository.setPreferences(p) }.onFailure { tell("设置保存失败：${it.message}") } } }
    suspend fun export(): String = repository.backup()
    suspend fun restore(backup: Backup): Boolean {
        busy.value = true
        return try { repository.restore(backup); tell("备份已完整恢复"); true }
        catch (e: Exception) { tell("恢复失败：${e.message}"); false }
        finally { busy.value = false }
    }
    suspend fun tell(message: String) { messages.send(message) }
}
