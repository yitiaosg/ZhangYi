package cn.zhangyi

import android.app.Application
import androidx.room.Room
import cn.zhangyi.data.*

class ZhangYiApplication : Application() {
    val repository: LedgerRepository by lazy {
        // Never use fallbackToDestructiveMigration. Every future schema bump must ship explicit migrations.
        val db = Room.databaseBuilder(this, ZhangYiDatabase::class.java, "zhangyi.db").build()
        LedgerRepository(db, PreferenceStore(this))
    }
}
