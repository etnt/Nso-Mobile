package se.kruskakli.nso.domain

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.IOException
import okio.buffer
import okio.source
import se.kruskakli.nso.data.NsoApi
import se.kruskakli.nso.data.RetrofitInstance
import se.kruskakli.nso.data.alarms.toAlarmUi
import se.kruskakli.nso.data.debug.allocators.toAllocatorUi
import se.kruskakli.nso.data.debug.ets.toEtsUi
import se.kruskakli.nso.data.devices.toDeviceUi
import se.kruskakli.nso.data.packages.toPackageUi
import se.kruskakli.nso.data.debug.inet.toInetUi
import se.kruskakli.nso.data.debug.processes.toProcessUi
import se.kruskakli.nso.data.releasenote.ReleaseNote
import se.kruskakli.nso.data.releasenote.TextPieceAdapter
import se.kruskakli.nso.data.syscounters.toUiModel
import se.kruskakli.nso.presentation.TabPage

/*
In this ViewModel, I've replaced remember { mutableStateOf(...) } with
MutableStateFlow(...), and I've moved your functions into the ViewModel
as methods. I've also replaced GlobalScope.launch with viewModelScope.launch
to tie the coroutine's lifecycle to the ViewModel's lifecycle.
*/
class MainViewModel : ViewModel() {
    private val _name = MutableStateFlow("Blueberry")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _ipAddress = MutableStateFlow("192.168.1.231")
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    private val _port = MutableStateFlow("9080")
    val port: StateFlow<String> = _port.asStateFlow()

    private val _user = MutableStateFlow("admin")
    val user: StateFlow<String> = _user.asStateFlow()

    private val _passwd = MutableStateFlow("admin")
    val passwd: StateFlow<String> = _passwd.asStateFlow()

    private val _releaseNotes = MutableStateFlow<List<ReleaseNote>>(emptyList())
    val releaseNotes: StateFlow<List<ReleaseNote>> = _releaseNotes.asStateFlow()

    fun loadReleaseNotes(json: String) {
        viewModelScope.launch {
            val notes = parseReleaseNotes(json)
            _releaseNotes.value = notes
        }
    }

    private fun parseReleaseNotes(json: String): List<ReleaseNote> {
        val moshi = Moshi.Builder()
            .add(TextPieceAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()

        val jsonAdapter = moshi.adapter<List<ReleaseNote>>(Types.newParameterizedType(List::class.java, ReleaseNote::class.java))

        val relNotes = jsonAdapter.fromJson(json)
        //Log.d("MainViewModel", "Release notes: $relNotes")
        return relNotes ?: emptyList()
    }
    

    private fun applySettings(settingsData: SettingsData) {
        _name.value = settingsData.name
        _ipAddress.value = settingsData.ip
        _port.value = settingsData.port
        _user.value = settingsData.user
        _passwd.value = settingsData.passwd
        _refresh.value = settingsData.refresh
    }

    private val _refresh = MutableStateFlow(true)
    val refresh: StateFlow<Boolean> = _refresh.asStateFlow()

    fun setRefresh(newRefresh: Boolean) {
        _refresh.value = newRefresh
    }

    private fun refreshPage(page: TabPage) {
        when (page) {
            TabPage.Packages -> {
                resetNsoPackages()
                getNsoPackages()
            }
            TabPage.Devices -> {
                resetNsoDevices()
                getNsoDevices()
            }
            TabPage.Alarms -> {
                resetNsoAlarms()
                getNsoAlarms()
            }
            TabPage.Listeners -> {
                resetNsoInet()
                getNsoInet()
            }
            TabPage.EtsTables -> {
                resetNsoEts()
                getNsoEts()
            }
            TabPage.Allocators -> {
                resetNsoAllocators()
                getNsoAllocators()
            }
            TabPage.Processes -> {
                resetNsoProcesses()
                getNsoProcesses()
            }
            TabPage.SysCounters -> {
                resetNsoSysCounters()
                getSysCounters()
            }
            else -> {
                // Do nothing
            }
        }
    }

    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()
    fun clearApiError() {
        _apiError.value = null
    }

    private val _nsoPackages = MutableStateFlow(listOf<PackageUi>())
    val nsoPackages: StateFlow<List<PackageUi>> = _nsoPackages.asStateFlow()

    // To make it possible to enable/disable the debug menus
    private val _nsoDbgEnabled = MutableStateFlow(false)
    val nsoDbgEnabled: StateFlow<Boolean> = _nsoDbgEnabled.asStateFlow()

    fun resetNsoPackages() {
        _nsoPackages.value = emptyList()
        _nsoDbgEnabled.value = false
    }

    private val _nsoDevices = MutableStateFlow(listOf<DeviceUi>())
    val nsoDevices: StateFlow<List<DeviceUi>> = _nsoDevices.asStateFlow()

    fun resetNsoDevices() {
        _nsoDevices.value = emptyList()
    }

    private val _nsoAlarms = MutableStateFlow(listOf<AlarmUi>())
    val nsoAlarms: StateFlow<List<AlarmUi>> = _nsoAlarms.asStateFlow()

    fun resetNsoAlarms() {
        _nsoAlarms.value = emptyList()
    }

    private val _nsoInet = MutableStateFlow(listOf<InetUi>())
    val nsoInet: StateFlow<List<InetUi>> = _nsoInet.asStateFlow()

    fun resetNsoInet() {
        _nsoInet.value = emptyList()
    }

    private val _nsoEts = MutableStateFlow(listOf<EtsUi>())
    val nsoEts: StateFlow<List<EtsUi>> = _nsoEts.asStateFlow()

    fun resetNsoEts() {
        _nsoEts.value = emptyList()
    }

    private val _nsoAllocators = MutableStateFlow<AllocatorUi?>(null)
    val nsoAllocators: StateFlow<AllocatorUi?> = _nsoAllocators.asStateFlow()

    fun resetNsoAllocators() {
        _nsoAllocators.value = null
    }

    private val _nsoProcesses = MutableStateFlow(listOf<ProcessUi>())
    val nsoProcesses: StateFlow<List<ProcessUi>> = _nsoProcesses.asStateFlow()

    fun resetNsoProcesses() {
        _nsoProcesses.value = emptyList()
    }

    private val _nsoSysCounters = MutableStateFlow<SysCountersUi?>(null)
    val nsoSysCounters: StateFlow<SysCountersUi?> = _nsoSysCounters.asStateFlow()

    fun resetNsoSysCounters() {
        _nsoSysCounters.value = null
    }

    /*
        In this code, performApiCall now creates the NsoApi instance and
        passes it to apiCall. The apiCall function now takes an NsoApi
        parameter, which it uses to make the API call. This way, the
        creation of the NsoApi instance is shared between getNsoPackages
        and getNsoDevices, reducing code duplication.
     */
    private suspend fun <T> performApiCall(
        user: String,
        password: String,
        apiCall: suspend (api: NsoApi) -> T,
        onSuccess: (T) -> Unit,
        onError: () -> Unit = {}
    ) {
        try {
            val api = RetrofitInstance.getApi(
                "http://${ipAddress.value}:${port.value}/restconf/data/",
                user,
                password
            )
            val response = apiCall(api)
            withContext(Dispatchers.Main) {
                onSuccess(response)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error: ${e.message}")
            onError()
            _apiError.value = e.message
        }
    }

    val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun getNsoPackages() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            performApiCall(
                user = _user.value,
                password = _passwd.value,
                apiCall = { api -> api.getPackages() },
                onSuccess = { response ->
                    val newPackages = mutableListOf<PackageUi>()
                    response.tailfNcsPackages.nsoPackages.forEach() {
                        //Log.d("MainViewModel", "getNsoPackages BODY: ${it}")
                        val p = it.toPackageUi()
                        newPackages.add(p)
                    }
                    _nsoPackages.value = newPackages
                    _loading.value = false

                    // Check if the nso_dbg package is installed
                    newPackages.forEach { packageUi ->
                        if (packageUi.name == "nso_dbg") {
                            _nsoDbgEnabled.value = true
                            return@forEach
                        }
                    }
                },
                onError = {
                    _loading.value = false
                }
            )
        }
    }

    fun getNsoDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            performApiCall(
                user = _user.value,
                password = _passwd.value,
                apiCall = { api -> api.getNsoDevices() },
                onSuccess = { response ->
                    if (response.nsoDevices != null) {
                        val newDevices = mutableListOf<DeviceUi>()
                        response.nsoDevices.devices.forEach() {
                            //Log.d("MainViewModel", "getNsoDevices BODY: ${it}")
                            val p = it.toDeviceUi()
                            newDevices.add(p)
                        }
                        _nsoDevices.value = newDevices
                    }
                    _loading.value = false
                },
                onError = {
                    _loading.value = false
                }
            )
        }
    }

    fun getNsoAlarms() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            performApiCall(
                user = _user.value,
                password = _passwd.value,
                apiCall = { api -> api.getNsoAlarmList() },
                onSuccess = { response ->
                    if (response.nsoAlarmList != null) {
                        val newAlarms = mutableListOf<AlarmUi>()
                        response.nsoAlarmList.alarm.forEach() {
                            //Log.d("MainViewModel", "getNsoDevices BODY: ${it}")
                            val p = it.toAlarmUi()
                            newAlarms.add(p)
                        }
                        _nsoAlarms.value = newAlarms
                    }
                    _loading.value = false
                },
                onError = {
                    _loading.value = false
                }
            )
        }
    }

    fun getNsoInet() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            performApiCall(
                user = _user.value,
                password = _passwd.value,
                apiCall = { api -> api.getNsoInet() },
                onSuccess = { response ->
                    if (response.nsoInet != null) {
                        val newInet = mutableListOf<InetUi>()
                        response.nsoInet.all.forEach() {
                            //Log.d("MainViewModel", "getNsoInet BODY: ${it}")
                            val p = it.toInetUi()
                            newInet.add(p)
                        }
                        _nsoInet.value = newInet
                    }
                    _loading.value = false
                },
                onError = {
                    _loading.value = false
                }
            )
        }
    }

    fun getNsoEts() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            performApiCall(
                user = _user.value,
                password = _passwd.value,
                apiCall = { api -> api.getNsoEts() },
                onSuccess = { response ->
                    if (response.nsoEtsTables != null) {
                        val newEts = mutableListOf<EtsUi>()
                        response.nsoEtsTables.all.forEach() {
                            Log.d("MainViewModel", "getNsoEts BODY: ${it}")
                            val p = it.toEtsUi()
                            newEts.add(p)
                        }
                        _nsoEts.value = newEts
                    }
                    _loading.value = false
                },
                onError = {
                    _loading.value = false
                }
            )
        }
    }

    fun getNsoAllocators() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            performApiCall(
                user = _user.value,
                password = _passwd.value,
                apiCall = { api -> api.getNsoAllocators() },
                onSuccess = { response ->
                    if (response.nsoAllocators != null) {
                        _nsoAllocators.value = response.nsoAllocators.toAllocatorUi()
                    }
                    _loading.value = false
                },
                onError = {
                    _loading.value = false
                }
            )
        }
    }

    fun getNsoProcesses() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            performApiCall(
                user = _user.value,
                password = _passwd.value,
                apiCall = { api -> api.getNsoProcesses() },
                onSuccess = { response ->
                    val newProcesses = mutableListOf<ProcessUi>()
                    response.nsoAllProcesses.forEach() {
                        //Log.d("MainViewModel", "getNsoProcesses BODY: ${it}")
                        val p = it.toProcessUi()
                        newProcesses.add(p)
                    }
                    _nsoProcesses.value = newProcesses
                    _loading.value = false
                },
                onError = {
                    _loading.value = false
                }
            )
        }
    }

    fun getSysCounters() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            performApiCall(
                user = _user.value,
                password = _passwd.value,
                apiCall = { api -> api.getSysCounters() },
                onSuccess = { response ->
                    //Log.d("MainViewModel", "getSysCounters BODY: ${response}")
                    _nsoSysCounters.value = response.sysCounters.toUiModel()
                    //Log.d("MainViewModel", "getSysCounters UI: ${_nsoSysCounters.value}")
                    _loading.value = false
                },
                onError = {
                    _loading.value = false
                }
            )
        }
    }

    fun handleIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.ShowPackages -> {
                if (_refresh.value || _nsoPackages.value.isEmpty()) {
                    resetNsoPackages()
                    getNsoPackages()
                    setRefresh(false)
                }
            }
            is MainIntent.ShowDevices -> {
                if (_refresh.value || _nsoDevices.value.isEmpty()) {
                    resetNsoDevices()
                    getNsoDevices()
                    setRefresh(false)
                }
            }
            is MainIntent.ShowAlarms -> {
                if (_refresh.value || _nsoAlarms.value.isEmpty()) {
                    resetNsoAlarms()
                    getNsoAlarms()
                    setRefresh(false)
                }
            }
            is MainIntent.SaveSettings -> {
                applySettings(intent.settingsData)
            }
            is MainIntent.RefreshPage -> {
                refreshPage(intent.page)
            }
            is MainIntent.ShowInet -> {
                if (_refresh.value || _nsoInet.value.isEmpty()) {
                    resetNsoInet()
                    getNsoInet()
                    setRefresh(false)
                }
            }
            is MainIntent.ShowEts -> {
                if (_refresh.value || _nsoEts.value.isEmpty()) {
                    resetNsoEts()
                    getNsoEts()
                    setRefresh(false)
                }
            }
            is MainIntent.ShowAllocators -> {
                if (_refresh.value || _nsoAllocators.value == null) {
                    resetNsoAllocators()
                    getNsoAllocators()
                    setRefresh(false)
                }
            }
            is MainIntent.ShowProcesses -> {
                if (_refresh.value || _nsoProcesses.value.isEmpty()) {
                    resetNsoProcesses()
                    getNsoProcesses()
                    setRefresh(false)
                }
            }
            is MainIntent.ShowSysCounters -> {
                if (_refresh.value || _nsoSysCounters.value == null) {
                    resetNsoSysCounters()
                    getSysCounters()
                    setRefresh(false)
                }
            }
            is MainIntent.SortData -> {
                when (intent.source) {
                    TabPage.EtsTables -> {
                        Log.d("MainViewModel", "SortData: field=${intent.field} ${intent.type}")
                        val sortedEts = when (intent.field) {
                            "name" -> _nsoEts.value.sortedBy { it.name }
                            "mem" -> _nsoEts.value.sortedBy { it.mem.toIntOrNull() ?: 0 }
                            else -> _nsoEts.value
                        }
                        if (intent.type == SortType.Descending) {
                            _nsoEts.value = sortedEts.reversed()
                        } else {
                            _nsoEts.value = sortedEts
                        }
                    }
                    TabPage.Processes -> {
                        Log.d("MainViewModel", "SortData: field=${intent.field} ${intent.type}")
                        val sortedProcesses = when (intent.field) {
                            "name" -> _nsoProcesses.value.sortedBy { it.name }
                            "reds" -> _nsoProcesses.value.sortedBy { it.reds.toIntOrNull() ?: 0 }
                            "mem" -> _nsoProcesses.value.sortedBy { it.memory.toIntOrNull() ?: 0 }
                            else -> _nsoProcesses.value
                        }
                        if (intent.type == SortType.Descending) {
                            _nsoProcesses.value = sortedProcesses.reversed()
                        } else {
                            _nsoProcesses.value = sortedProcesses
                        }
                    }
                    else -> {
                        // Do nothing
                    }
                }
            }
        }
    }

    

}

