package com.autoflow.app.trigger

import android.service.quicksettings.TileService
import com.autoflow.app.engine.RuleEngine
import com.autoflow.app.engine.TriggerEvent

/**
 * A Quick Settings tile that fires every rule using the QuickTile trigger. Gives the user
 * a one-tap way to run automations without opening the app.
 */
class AutoFlowTileService : TileService() {

    override fun onClick() {
        super.onClick()
        RuleEngine.init(applicationContext)
        RuleEngine.submit(TriggerEvent.of(TriggerEvent.Source.QUICK_TILE))
    }
}
