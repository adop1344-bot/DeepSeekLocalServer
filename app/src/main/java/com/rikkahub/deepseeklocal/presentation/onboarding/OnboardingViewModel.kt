package com.rikkahub.deepseeklocal.presentation.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.rikkahub.deepseeklocal.data.local.prefs.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Supplies the bookmarklet and persists the pasted token. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(private val tokenStore: TokenStore) : ViewModel() {
    /** Bookmarklet URL that dumps localStorage and cookies as a JSON download. */
    /** Bookmarklet that grabs the DeepSeek userToken from localStorage and posts it to the local server. */
    val bookmarklet: String =
        "javascript:(function(){try{" +
        "var m=localStorage.getItem('userToken');" +
        "if(!m){var k=Object.keys(localStorage).filter(function(x){return /token/i.test(x)});" +
        "m=k.length?localStorage.getItem(k[0]):null;}" +
        "if(!m){alert('userToken not found');return;}" +
        "m=m.replace(/^\"|\"$/g,'');" +
        "fetch('http://127.0.0.1:8788/token',{method:'POST',headers:{'Content-Type':'application/json'}," +
        "body:JSON.stringify({action:'add',token:m})}).then(function(){alert('Token sent to DeepSeek Local Server');})" +
        ".catch(function(e){alert('Failed: '+e);});" +
        "}catch(e){alert('Error: '+e);}})();"

    /** Saves the user-pasted token into encrypted storage. */
    fun saveToken(t: String) { if (t.isNotBlank()) tokenStore.setToken(t.trim()) }

    /** Opens [url] in the user's default browser. */
    fun openUrl(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
