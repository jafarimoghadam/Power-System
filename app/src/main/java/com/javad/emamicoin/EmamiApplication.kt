package com.javad.emamicoin

import android.app.Application
import com.javad.emamicoin.data.local.MarketDatabase

class EmamiApplication : Application() {
    val database by lazy { MarketDatabase.get(this) }
}
