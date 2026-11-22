package com.leonardogalvao.pontocontrole

import android.app.Application
import com.leonardogalvao.pontocontrole.core.di.AppContainer

class PontoControleApplication : Application() {
    val container: AppContainer by lazy { AppContainer() }
}
