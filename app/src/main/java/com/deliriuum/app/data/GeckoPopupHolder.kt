package com.deliriuum.app.data

import org.mozilla.geckoview.GeckoSession

object GeckoPopupHolder {

    var session: GeckoSession? = null

    fun clear() {
        session = null
    }
}