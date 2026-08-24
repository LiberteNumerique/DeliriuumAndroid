package com.deliriuum.app.ui.screens

import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import com.deliriuum.app.data.GeckoPopupHolder
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class GeckoPopupActivity : ComponentActivity() {

    private var popupSession: GeckoSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val session = GeckoPopupHolder.session

        if (session == null) {
            finish()
            return
        }

        popupSession = session

        /*
         * ContentDelegate spécifique à cette popup.
         *
         * Si TikTok appelle window.close(),
         * on ferme automatiquement cette Activity.
         */
        session.contentDelegate =
            object : GeckoSession.ContentDelegate {

                override fun onCloseRequest(
                    session: GeckoSession
                ) {
                    finish()
                }
            }

        val geckoView =
            GeckoView(this).apply {

                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                setSession(session)

                tag = session
            }

        setContentView(geckoView)
    }

    override fun onDestroy() {
        super.onDestroy()

        /*
         * On ferme la popup si elle est encore ouverte.
         */
        try {
            popupSession?.close()
        } catch (_: Exception) {
        }

        popupSession = null

        GeckoPopupHolder.clear()
    }
}