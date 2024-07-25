package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.Theme
import io.nekohasekai.sagernet.widget.AppListPreference
import moe.matsuri.nb4a.Protocols
import moe.matsuri.nb4a.ui.ColorPickerPreference
import moe.matsuri.nb4a.ui.LongClickListPreference
import moe.matsuri.nb4a.ui.MTUPreference
import moe.matsuri.nb4a.ui.SimpleMenuPreference

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var isProxyApps: SwitchPreference
    private lateinit var nekoPlugins: AppListPreference

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.layoutManager = FixedLinearLayoutManager(listView)
    }

    private val reloadListener = Preference.OnPreferenceChangeListener { _, _ ->
        needReload()
        true
    }

    private val restartListener = Preference.OnPreferenceChangeListener { _, _ ->
        needRestart()
        true
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.global_preferences)

        DataStore.routePackages = DataStore.nekoPlugins
        nekoPlugins = findPreference(Key.NEKO_PLUGIN_MANAGED)!!
        nekoPlugins.setOnPreferenceClickListener {
            // borrow from route app settings
            startActivity(Intent(
                context, AppListActivity::class.java
            ).apply { putExtra(Key.NEKO_PLUGIN_MANAGED, true) })
            true
        }

        val appTheme = findPreference<ColorPickerPreference>(Key.APP_THEME)!!
        appTheme.setOnPreferenceChangeListener { _, newTheme ->
            if (DataStore.serviceState.started) {
                SagerNet.reloadService()
            }
            val theme = Theme.getTheme(newTheme as Int)
            app.setTheme(theme)
            requireActivity().apply {
                setTheme(theme)
                ActivityCompat.recreate(this)
            }
            true
        }

        val nightTheme = findPreference<SimpleMenuPreference>(Key.NIGHT_THEME)!!
        nightTheme.setOnPreferenceChangeListener { _, newTheme ->
            Theme.currentNightMode = (newTheme as String).toInt()
            Theme.applyNightTheme()
            true
        }
        val mixedPort = findPreference<EditTextPreference>(Key.MIXED_PORT)!!
        val serviceMode = findPreference<Preference>(Key.SERVICE_MODE)!!
        val allowAccess = findPreference<Preference>(Key.ALLOW_ACCESS)!!
        val appendHttpProxy = findPreference<SwitchPreference>(Key.APPEND_HTTP_PROXY)!!

        val portLocalDns = findPreference<EditTextPreference>(Key.LOCAL_DNS_PORT)!!
        val showDirectSpeed = findPreference<SwitchPreference>(Key.SHOW_DIRECT_SPEED)!!
        val ipv6Mode = findPreference<Preference>(Key.IPV6_MODE)!!
        val trafficSniffing = findPreference<Preference>(Key.TRAFFIC_SNIFFING)!!

        val muxConcurrency = findPreference<EditTextPreference>(Key.MUX_CONCURRENCY)!!
        val tcpKeepAliveInterval = findPreference<EditTextPreference>(Key.TCP_KEEP_ALIVE_INTERVAL)!!
        tcpKeepAliveInterval.isVisible = false
        val uploadSpeed = findPreference<EditTextPreference>(Key.UPLOAD_SPEED)!!
        val downloadSpeed = findPreference<EditTextPreference>(Key.DOWNLOAD_SPEED)!!

        val bypassLan = findPreference<SwitchPreference>(Key.BYPASS_LAN)!!
        val bypassLanInCore = findPreference<SwitchPreference>(Key.BYPASS_LAN_IN_CORE)!!
        val inboundUsername = findPreference<EditTextPreference>(Key.INBOUND_USERNAME)!!
        val inboundPassword = findPreference<EditTextPreference>(Key.INBOUND_PASSWORD)!!

        val remoteDns = findPreference<EditTextPreference>(Key.REMOTE_DNS)!!
        val directDns = findPreference<EditTextPreference>(Key.DIRECT_DNS)!!
        val directDnsClientSubnet =
            findPreference<EditTextPreference>(Key.DIRECT_DNS_CLIENT_SUBNET)!!
        val underlyingDns = findPreference<EditTextPreference>(Key.UNDERLYING_DNS)!!
        val enableDnsRouting = findPreference<SwitchPreference>(Key.ENABLE_DNS_ROUTING)!!
        val dnsMode = findPreference<SimpleMenuPreference>(Key.DNS_MODE)!!

        val logLevel = findPreference<LongClickListPreference>(Key.LOG_LEVEL)!!
        val mtu = findPreference<MTUPreference>(Key.MTU)!!
        val showProxyNum = findPreference<SwitchPreference>(Key.SHOW_PROXY_NUM)!!
        val alwaysShowAddress = findPreference<SwitchPreference>(Key.ALWAYS_SHOW_ADDRESS)!!
        val blurredAddress = findPreference<SwitchPreference>(Key.BLURRED_ADDRESS)!!

        logLevel.dialogLayoutResource = R.layout.layout_loglevel_help
        logLevel.onPreferenceChangeListener = restartListener
        logLevel.setOnLongClickListener {
            if (context == null) return@setOnLongClickListener true

            val view = EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                var size = DataStore.logBufSize
                if (size == 0) size = 50
                setText(size.toString())
            }

            MaterialAlertDialogBuilder(requireContext()).setTitle("Log buffer size (kb)")
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    DataStore.logBufSize = view.text.toString().toInt()
                    if (DataStore.logBufSize <= 0) DataStore.logBufSize = 50
                    needRestart()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        val muxProtocols = findPreference<MultiSelectListPreference>(Key.MUX_PROTOCOLS)!!

        muxProtocols.apply {
            val e = Protocols.getCanMuxList().toTypedArray()
            entries = e
            entryValues = e
        }

        portLocalDns.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        muxConcurrency.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        mixedPort.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

        val metedNetwork = findPreference<Preference>(Key.METERED_NETWORK)!!
        if (Build.VERSION.SDK_INT < 28) {
            metedNetwork.remove()
        }
        isProxyApps = findPreference(Key.PROXY_APPS)!!
        isProxyApps.setOnPreferenceChangeListener { _, newValue ->
            startActivity(Intent(activity, AppManagerActivity::class.java))
            if (newValue as Boolean) DataStore.dirty = true
            newValue
        }

        val profileTrafficStatistics =
            findPreference<SwitchPreference>(Key.PROFILE_TRAFFIC_STATISTICS)!!
        val speedInterval = findPreference<SimpleMenuPreference>(Key.SPEED_INTERVAL)!!
        profileTrafficStatistics.isEnabled = speedInterval.value.toString() != "0"
        showDirectSpeed.isEnabled = speedInterval.value.toString() != "0"
        speedInterval.setOnPreferenceChangeListener { _, newValue ->
            profileTrafficStatistics.isEnabled = newValue.toString() != "0"
            showDirectSpeed.isEnabled = newValue.toString() != "0"
            needReload()
            true
        }
        showDirectSpeed.onPreferenceChangeListener = reloadListener

        serviceMode.setOnPreferenceChangeListener { _, _ ->
            if (DataStore.serviceState.started) SagerNet.stopService()
            true
        }

        val tunImplementation = findPreference<SimpleMenuPreference>(Key.TUN_IMPLEMENTATION)!!
        val resolveDestination = findPreference<SwitchPreference>(Key.RESOLVE_DESTINATION)!!
        val acquireWakeLock = findPreference<SwitchPreference>(Key.ACQUIRE_WAKE_LOCK)!!
        val clashAPIListen = findPreference<EditTextPreference>(Key.CLASH_API_LISTEN)!!
        val enabledCazilla = findPreference<SwitchPreference>(Key.ENABLED_CAZILLA)!!

        mixedPort.onPreferenceChangeListener = reloadListener
        appendHttpProxy.onPreferenceChangeListener = reloadListener
        trafficSniffing.onPreferenceChangeListener = reloadListener
        uploadSpeed.onPreferenceChangeListener = reloadListener
        downloadSpeed.onPreferenceChangeListener = reloadListener
        muxConcurrency.onPreferenceChangeListener = reloadListener
        tcpKeepAliveInterval.onPreferenceChangeListener = reloadListener

        bypassLanInCore.isEnabled = bypassLan.isChecked
        bypassLanInCore.onPreferenceChangeListener = reloadListener
        bypassLan.setOnPreferenceChangeListener { _, newValue ->
            bypassLanInCore.isEnabled = newValue as Boolean
            needReload()
            true
        }

        inboundUsername.onPreferenceChangeListener = reloadListener
        inboundPassword.onPreferenceChangeListener = reloadListener
        mtu.onPreferenceChangeListener = reloadListener
        showProxyNum.onPreferenceChangeListener = reloadListener

        blurredAddress.isEnabled = alwaysShowAddress.isChecked
        alwaysShowAddress.setOnPreferenceChangeListener { _, newValue ->
            blurredAddress.isEnabled = newValue as Boolean
            true
        }

        dnsMode.onPreferenceChangeListener = reloadListener
        remoteDns.onPreferenceChangeListener = reloadListener
        directDns.onPreferenceChangeListener = reloadListener
        directDnsClientSubnet.onPreferenceChangeListener = reloadListener
        underlyingDns.onPreferenceChangeListener = reloadListener
        enableDnsRouting.onPreferenceChangeListener = reloadListener

        portLocalDns.onPreferenceChangeListener = reloadListener
        ipv6Mode.onPreferenceChangeListener = reloadListener
        allowAccess.onPreferenceChangeListener = reloadListener

        resolveDestination.onPreferenceChangeListener = reloadListener
        tunImplementation.onPreferenceChangeListener = reloadListener
        acquireWakeLock.onPreferenceChangeListener = reloadListener

        clashAPIListen.onPreferenceChangeListener = reloadListener
        enabledCazilla.onPreferenceChangeListener = restartListener

    }

    override fun onResume() {
        super.onResume()

        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
        if (::nekoPlugins.isInitialized) {
            nekoPlugins.postUpdate()
        }
    }

}