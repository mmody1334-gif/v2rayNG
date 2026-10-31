package com.v2ray.ang.ui.connectioncheck

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.handler.ConnectionCheckResult
import com.v2ray.ang.handler.IpGeoInfo
import com.v2ray.ang.handler.Ipv6Status
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.ui.compose.SettingsSwitchItem
import java.util.Locale

class ConnectionCheckActivity : BaseComponentActivity() {

    private val viewModel: ConnectionCheckViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.runCheck()
    }

    @Composable
    override fun ScreenContent() {
        ConnectionCheckScreen(viewModel = viewModel, onBackClick = { finish() })
    }
}

@Composable
fun ConnectionCheckScreen(viewModel: ConnectionCheckViewModel, onBackClick: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val result = state.result

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.check_title),
                onBackClick = onBackClick,
                isLoading = isLoading
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val geo = result?.visible
            if (geo?.lat != null && geo.lon != null) {
                IpMap(geo)
            }

            when {
                result == null && isLoading -> Text(stringResource(R.string.check_running))
                state.failed || geo == null -> Text(stringResource(R.string.check_failed))
                else -> ResultCards(result!!, geo!!)
            }

            Button(
                onClick = { viewModel.runCheck() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.check_again)) }

            SettingsSwitchItem(
                icon = painterResource(R.drawable.ic_lock_24dp),
                title = stringResource(R.string.check_block_ipv6),
                summary = stringResource(R.string.check_block_ipv6_summary),
                checked = state.blockIpv6Leak,
                onCheckedChange = { viewModel.setBlockIpv6Leak(it) }
            )
            NavigationBarsSpacer()
        }
    }
}

@Composable
private fun ResultCards(result: ConnectionCheckResult, geo: IpGeoInfo) {
    // 1. Is the VPN working?
    StatusCard(
        ok = result.throughVpn,
        title = stringResource(if (result.throughVpn) R.string.check_vpn_on else R.string.check_vpn_off),
        body = if (result.throughVpn) {
            stringResource(R.string.check_real_ip, result.real?.query ?: "?", countryLabel(result.real))
        } else {
            stringResource(R.string.check_vpn_off_body)
        }
    )

    // 2. What websites see.
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.check_sites_see), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flagOf(geo.countryCode), fontSize = 30.sp)
                Text(
                    "  ${countryLabel(geo)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            InfoLine(R.string.check_ip, geo.query)
            InfoLine(R.string.check_city, listOfNotNull(geo.city, geo.regionName).distinct().joinToString(" · "))
            InfoLine(R.string.check_isp, geo.isp)
            InfoLine(R.string.check_asn, geo.`as`)
            InfoLine(R.string.check_timezone, geo.timezone)
        }
    }

    // 3. Detection checks.
    val flagged = geo.proxy == true || geo.hosting == true
    StatusCard(
        ok = !flagged,
        title = stringResource(if (flagged) R.string.check_flagged else R.string.check_clean),
        body = stringResource(if (flagged) R.string.check_flagged_body else R.string.check_clean_body)
    )
    result.timezoneMatches?.let { match ->
        StatusCard(
            ok = match,
            title = stringResource(if (match) R.string.check_tz_ok else R.string.check_tz_bad),
            body = stringResource(R.string.check_tz_body, result.phoneTimezone, geo.timezone ?: "?")
        )
    }
    val ipv6Ok = result.ipv6 != Ipv6Status.Leaking
    StatusCard(
        ok = ipv6Ok,
        title = stringResource(
            when (result.ipv6) {
                Ipv6Status.NoIpv6 -> R.string.check_ipv6_none
                Ipv6Status.Protected -> R.string.check_ipv6_protected
                Ipv6Status.Leaking -> R.string.check_ipv6_leak
                Ipv6Status.NotConnected -> R.string.check_ipv6_not_connected
            }
        ),
        body = result.realIpv6?.let { stringResource(R.string.check_ipv6_real, it) }
            ?: stringResource(R.string.check_ipv6_no_address)
    )
}

@Composable
private fun StatusCard(ok: Boolean, title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = (if (ok) "✅  " else "⚠️  ") + title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun InfoLine(labelRes: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(stringResource(labelRes) + ": ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun countryLabel(geo: IpGeoInfo?): String {
    val code = geo?.countryCode ?: return "?"
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val name = runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(locale) }
        .getOrNull().orEmpty().ifBlank { geo.country ?: code }
    return name
}

private fun flagOf(code: String?): String =
    if (code == null || code.length != 2) "🏳️"
    else code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }

/** OpenStreetMap + Leaflet, like ip-locator sites: a marker on where the IP appears. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun IpMap(geo: IpGeoInfo) {
    val html = remember(geo) { mapHtml(geo) }
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
        },
        update = { web ->
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL("https://servrati.local/", html, "text/html", "utf-8", null)
            }
        }
    )
}

private fun mapHtml(geo: IpGeoInfo): String {
    fun esc(s: String?) = (s ?: "").replace("\\", "\\\\").replace("'", "\\'").replace("<", "")
    val label = esc(listOfNotNull(geo.query, listOfNotNull(geo.city, geo.country).joinToString(", ")).joinToString("<br>"))
    return """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#m{margin:0;height:100%}</style></head>
<body><div id="m"></div><script>
var m=L.map('m',{zoomControl:true}).setView([${geo.lat},${geo.lon}],8);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'&copy; OpenStreetMap'}).addTo(m);
L.marker([${geo.lat},${geo.lon}]).addTo(m).bindPopup('<b>$label</b>').openPopup();
</script></body></html>
""".trimIndent()
}
