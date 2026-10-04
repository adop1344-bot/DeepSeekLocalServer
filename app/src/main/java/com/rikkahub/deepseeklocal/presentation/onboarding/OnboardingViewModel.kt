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
    val bookmarklet: String = "javascript:(function(){var d={url:location.href,time:new Date().toISOString(),cookies:document.cookie,localStorage:{}};for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);d.localStorage[k]=localStorage.getItem(k)}var b=new Blob([JSON.stringify(d,null,2)],{type:'application/json'});var a=document.createElement('a');a.href=URL.createObjectURL(b);a.download='deepseek-storage-'+Date.now()+'.json';document.body.appendChild(a);a.click();document.body.removeChild(a);alert('Saved')})();"

    /** Saves the user-pasted token into encrypted storage. */
    fun saveToken(t: String) { if (t.isNotBlank()) tokenStore.setToken(t.trim()) }

    /** Opens [url] in the user's default browser. */
    fun openUrl(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
