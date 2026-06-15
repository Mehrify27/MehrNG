package com.v2ray.ang.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.core.SubscriptionManager
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.toast
import com.v2ray.ang.extension.toastError
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : HelperBaseActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()
    private lateinit var adapter: MainRecyclerAdapter
    private var pulseAnimator: android.animation.AnimatorSet? = null

    private val requestVpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            startV2Ray()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupToolbar(binding.toolbar, false, "")

        // Request notification runtime permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            checkAndRequestPermission(com.v2ray.ang.enums.PermissionType.POST_NOTIFICATIONS) {
                // Permission granted
            }
        }

        // Initialize and enforce our hidden subscription group
        SubscriptionManager.ensureSubscriptionExists()

        // Setup recycler view for read-only node list
        adapter = MainRecyclerAdapter(mainViewModel, ActivityAdapterListener())
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        addCustomDividerToRecyclerView(binding.recyclerView, this, R.drawable.custom_divider)
        binding.recyclerView.adapter = adapter
        
        // Setup iOS overscroll bounce effect
        binding.recyclerView.setupIphoneOverscroll()

        // Setup Connect/Disconnect card button click listener
        binding.cardConnect.setOnClickListener {
            handleConnectDisconnect()
        }

        // Setup manual Refresh sync button click listener
        binding.btnRefresh.setOnClickListener {
            triggerSync(force = true)
        }

        // Setup Ping Test button click listener
        binding.btnPing.setOnClickListener {
            binding.btnPing.animate().rotationBy(360f).setDuration(800).start()
            mainViewModel.testAllRealPing()
        }

        // Setup click listener on connection latency state subtitle
        binding.tvTestState.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                setTestState(getString(R.string.connection_test_testing))
                mainViewModel.testCurrentServerRealPing()
            } else {
                toast("Connect to VPN first to test connection speed")
            }
        }

        // Setup Settings button click listener
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Add smooth responsive touch animations to all interactive elements
        binding.cardConnect.addSmoothTouchScale()
        binding.btnRefresh.addSmoothTouchScale()
        binding.btnPing.addSmoothTouchScale()
        binding.tvTestState.addSmoothTouchScale()
        binding.layoutStatusBadge.addSmoothTouchScale()
        binding.btnSettings.addSmoothTouchScale()

        setupViewModel()

        // Sync subscription silently on first launch
        triggerSync(force = false)
    }

    private fun setupViewModel() {
        mainViewModel.updateTestResultAction.observe(this) { setTestState(it) }
        mainViewModel.isRunning.observe(this) { isRunning ->
            applyRunningState(isRunning)
        }
        mainViewModel.updateListAction.observe(this) { index ->
            adapter.setData(mainViewModel.serversCache, index)
        }
        mainViewModel.startListenBroadcast()
        mainViewModel.initAssets(assets)
    }

    private fun triggerSync(force: Boolean) {
        showLoading()
        binding.btnRefresh.isEnabled = false
        binding.btnRefresh.animate().rotationBy(360f).setDuration(1000).start()

        lifecycleScope.launch(Dispatchers.IO) {
            // Force subscription checks and refreshes
            val (success, guids) = SubscriptionManager.refreshSubscription()
            
            withContext(Dispatchers.Main) {
                mainViewModel.reloadServerList()
                hideLoading()
                binding.btnRefresh.isEnabled = true
                
                if (success) {
                    toast(getString(R.string.import_subscription_success))
                } else if (force) {
                    toastError(R.string.import_subscription_failure)
                }

                // If no server was selected and we have servers, select the first one
                if (MmkvManager.getSelectServer().isNullOrEmpty() && mainViewModel.serversCache.isNotEmpty()) {
                    val firstGuid = mainViewModel.serversCache.first().guid
                    MmkvManager.setSelectServer(firstGuid)
                    mainViewModel.reloadServerList()
                }
            }
        }
    }

    private fun handleConnectDisconnect() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        } else if (SettingsManager.isVpnMode()) {
            val intent = VpnService.prepare(this)
            if (intent == null) {
                startV2Ray()
            } else {
                requestVpnPermission.launch(intent)
            }
        } else {
            startV2Ray()
        }
    }

    private fun startV2Ray() {
        var selected = MmkvManager.getSelectServer()
        if (selected.isNullOrEmpty() && mainViewModel.serversCache.isNotEmpty()) {
            selected = mainViewModel.serversCache.first().guid
            MmkvManager.setSelectServer(selected)
            mainViewModel.reloadServerList()
        }

        if (selected.isNullOrEmpty()) {
            toast(R.string.title_file_chooser)
            return
        }

        CoreServiceManager.startVService(this)
    }

    fun restartV2Ray() {
        if (mainViewModel.isRunning.value == true) {
            CoreServiceManager.stopVService(this)
        }
        lifecycleScope.launch {
            delay(500)
            startV2Ray()
        }
    }

    fun importConfigViaSub(): Boolean {
        triggerSync(force = true)
        return true
    }

    private fun setTestState(content: String?) {
        binding.tvTestState.text = content
    }

    private fun applyRunningState(isRunning: Boolean) {
        val appleGreen = ContextCompat.getColor(this, R.color.apple_green)
        val appleGray = ContextCompat.getColor(this, R.color.apple_gray)
        val toggleBgConnected = ContextCompat.getColor(this, R.color.toggle_card_bg_connected)
        val toggleBgDisconnected = ContextCompat.getColor(this, R.color.toggle_card_bg_disconnected)

        if (isRunning) {
            // Connected state
            binding.cardConnect.setStrokeColor(ColorStateList.valueOf(appleGreen))
            binding.cardConnect.setCardBackgroundColor(ColorStateList.valueOf(toggleBgConnected))
            binding.ivPower.setImageResource(R.drawable.ic_power_settings)
            binding.ivPower.imageTintList = ColorStateList.valueOf(appleGreen)
            
            binding.layoutStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_connected)
            binding.viewStatusDot.setBackgroundColor(appleGreen)
            binding.tvStatusTitle.text = "CONNECTED"
            binding.tvStatusTitle.setTextColor(appleGreen)
            
            startPulseAnimation()
            
            // Automatically test real delay
            setTestState(getString(R.string.connection_test_testing))
            mainViewModel.testCurrentServerRealPing()
        } else {
            // Disconnected state
            binding.cardConnect.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.apple_card_stroke)))
            binding.cardConnect.setCardBackgroundColor(ColorStateList.valueOf(toggleBgDisconnected))
            binding.ivPower.setImageResource(R.drawable.ic_power_settings)
            binding.ivPower.imageTintList = ColorStateList.valueOf(appleGray)
            
            binding.layoutStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_disconnected)
            binding.viewStatusDot.setBackgroundColor(appleGray)
            binding.tvStatusTitle.text = "DISCONNECTED"
            binding.tvStatusTitle.setTextColor(appleGray)
            
            stopPulseAnimation()
            
            setTestState("Tap to connect")
        }
    }

    private fun startPulseAnimation() {
        binding.viewPulseRing.animate().cancel()
        binding.viewPulseRing.setBackgroundResource(R.drawable.bg_pulse_circle)
        binding.viewPulseRing.animate()
            .scaleX(1.35f)
            .scaleY(1.35f)
            .alpha(0.4f)
            .setDuration(700)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.8f))
            .start()
    }

    private fun stopPulseAnimation() {
        binding.viewPulseRing.animate().cancel()
        binding.viewPulseRing.setBackgroundResource(R.drawable.bg_pulse_circle_disconnected)
        binding.viewPulseRing.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(0.15f)
            .setDuration(500)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun View.addSmoothTouchScale() {
        setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
                }
            }
            false
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun androidx.recyclerview.widget.RecyclerView.setupIphoneOverscroll() {
        var startY = 0f
        var isOverscrolling = false
        val resistance = 0.45f

        setOnTouchListener { v, event ->
            val rv = v as androidx.recyclerview.widget.RecyclerView
            val canScrollUp = rv.canScrollVertically(-1)
            val canScrollDown = rv.canScrollVertically(1)

            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    isOverscrolling = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (startY == 0f) {
                        startY = event.rawY
                    }
                    val deltaY = event.rawY - startY
                    val isAtTop = !canScrollUp
                    val isAtBottom = !canScrollDown

                    if (isAtTop && deltaY > 0) {
                        if (!isOverscrolling) {
                            isOverscrolling = true
                            startY = event.rawY
                        }
                        val overscrollDelta = event.rawY - startY
                        rv.translationY = overscrollDelta * resistance
                    } else if (isAtBottom && deltaY < 0) {
                        if (!isOverscrolling) {
                            isOverscrolling = true
                            startY = event.rawY
                        }
                        val overscrollDelta = event.rawY - startY
                        rv.translationY = overscrollDelta * resistance
                    } else {
                        if (isOverscrolling) {
                            rv.animate()
                                .translationY(0f)
                                .setDuration(250)
                                .setInterpolator(android.view.animation.DecelerateInterpolator())
                                .start()
                            isOverscrolling = false
                        }
                        startY = event.rawY
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    startY = 0f
                    if (isOverscrolling || rv.translationY != 0f) {
                        rv.animate()
                            .translationY(0f)
                            .setDuration(400)
                            .setInterpolator(android.view.animation.OvershootInterpolator(1.4f))
                            .start()
                        isOverscrolling = false
                    }
                }
            }
            false
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.reloadServerList()
        // Silently refresh subscription on app open
        lifecycleScope.launch(Dispatchers.IO) {
            SubscriptionManager.refreshSubscription()
            withContext(Dispatchers.Main) {
                mainViewModel.reloadServerList()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            moveTaskToBack(false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // No options menu in simplified UI
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    private inner class ActivityAdapterListener : MainAdapterListener {
        override fun onEdit(guid: String, position: Int) {}
        override fun onShare(url: String) {}
        override fun onRefreshData() {}
        override fun onRemove(guid: String, position: Int) {}
        override fun onEdit(guid: String, position: Int, profile: ProfileItem) {}
        
        override fun onSelectServer(guid: String) {
            val selected = MmkvManager.getSelectServer()
            if (guid != selected) {
                MmkvManager.setSelectServer(guid)
                adapter.setSelectServer(mainViewModel.getPosition(selected.orEmpty()), mainViewModel.getPosition(guid))
                if (mainViewModel.isRunning.value == true) {
                    restartV2Ray()
                }
            }
        }

        override fun onShare(guid: String, profile: ProfileItem, position: Int, more: Boolean) {}
    }
}