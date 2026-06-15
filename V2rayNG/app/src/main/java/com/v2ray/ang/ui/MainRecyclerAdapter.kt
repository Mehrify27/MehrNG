
package com.v2ray.ang.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.contracts.MainAdapterListener
import com.v2ray.ang.databinding.ItemRecyclerFooterBinding
import com.v2ray.ang.databinding.ItemRecyclerMainBinding
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.ServersCache
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.nullIfBlank
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.ItemTouchHelperAdapter
import com.v2ray.ang.helper.ItemTouchHelperViewHolder
import com.v2ray.ang.viewmodel.MainViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.Collections

class MainRecyclerAdapter(
    private val mainViewModel: MainViewModel,
    private val adapterListener: MainAdapterListener?
) : RecyclerView.Adapter<MainRecyclerAdapter.BaseViewHolder>(), ItemTouchHelperAdapter {
    companion object {
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_FOOTER = 2
    }

    private val doubleColumnDisplay = MmkvManager.decodeSettingsBool(AppConfig.PREF_DOUBLE_COLUMN_DISPLAY, false)
    private var data: MutableList<ServersCache> = mutableListOf()

    @SuppressLint("NotifyDataSetChanged")
    fun setData(newData: MutableList<ServersCache>?, position: Int = -1) {
        data = newData?.toMutableList() ?: mutableListOf()

        if (position >= 0 && position in data.indices) {
            notifyItemChanged(position)
        } else {
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = data.size + 1

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (holder is MainViewHolder) {
            val context = holder.itemMainBinding.root.context
            val guid = data[position].guid
            val profile = data[position].profile

            // Selected vs Normal background card styling
            if (guid == MmkvManager.getSelectServer()) {
                holder.itemMainBinding.itemBg.setBackgroundResource(R.drawable.bg_item_selected)
                holder.itemMainBinding.tvName.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary))
            } else {
                holder.itemMainBinding.itemBg.setBackgroundResource(R.drawable.bg_item_normal)
                holder.itemMainBinding.tvName.setTextColor(ContextCompat.getColor(context, R.color.app_text_primary))
            }

            // Name address
            holder.itemMainBinding.tvName.text = profile.remarks
            holder.itemMainBinding.tvStatistics.text = getAddress(profile)
            holder.itemMainBinding.tvType.text = getProtocolDescription(profile)

            // Dynamic Protocol Acronym Badge
            val badgeText = when (profile.configType) {
                EConfigType.VLESS -> "VL"
                EConfigType.VMESS -> "VM"
                EConfigType.SHADOWSOCKS -> "SS"
                EConfigType.TROJAN -> "TR"
                EConfigType.SOCKS -> "SK"
                EConfigType.HTTP -> "HT"
                EConfigType.WIREGUARD -> "WG"
                EConfigType.HYSTERIA2 -> "H2"
                else -> "PN"
            }
            
            val currentGuid = guid
            val aff = MmkvManager.decodeServerAffiliationInfo(guid)
            val delayVal = aff?.testDelayMillis ?: 0L

            holder.itemMainBinding.cardFlag.visibility = View.GONE
            holder.itemMainBinding.ivFlag.visibility = View.GONE

            // Detect flag emoji in remarks first, otherwise fall back to resolved flag emoji or protocol acronym
            val extractedFlag = extractFlagEmoji(profile.remarks)
            val serverAddress = profile.server.orEmpty()
            val cachedCode = if (serverAddress.isNotBlank()) com.v2ray.ang.utils.CountryResolver.getCachedCountryCode(serverAddress) else null

            val fallbackFlagText = when {
                extractedFlag != null -> extractedFlag
                cachedCode != null -> com.v2ray.ang.utils.CountryResolver.getFlagEmoji(cachedCode)
                else -> badgeText
            }
            holder.itemMainBinding.tvTypeBadge.text = fallbackFlagText

            if (serverAddress.isNotBlank()) {
                if (cachedCode != null) {
                    val cachedBitmap = com.v2ray.ang.utils.CountryResolver.getCachedFlagBitmap(context, cachedCode)
                    if (cachedBitmap != null) {
                        holder.itemMainBinding.ivFlag.setImageBitmap(cachedBitmap)
                        holder.itemMainBinding.ivFlag.visibility = View.VISIBLE
                    } else {
                        mainViewModel.viewModelScope.launch {
                            val bitmap = com.v2ray.ang.utils.CountryResolver.getFlagBitmap(context, cachedCode)
                            if (bitmap != null && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                if (data.getOrNull(holder.bindingAdapterPosition)?.guid == currentGuid) {
                                    holder.itemMainBinding.ivFlag.setImageBitmap(bitmap)
                                    holder.itemMainBinding.ivFlag.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                } else {
                    mainViewModel.viewModelScope.launch {
                        val code = com.v2ray.ang.utils.CountryResolver.resolveCountryCode(serverAddress)
                        if (code != null && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                            val bitmap = com.v2ray.ang.utils.CountryResolver.getFlagBitmap(context, code)
                            if (bitmap != null && holder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                if (data.getOrNull(holder.bindingAdapterPosition)?.guid == currentGuid) {
                                    holder.itemMainBinding.ivFlag.setImageBitmap(bitmap)
                                    holder.itemMainBinding.ivFlag.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }
            }

            // TestResult Latency Pill Styling
            if (delayVal < 0L) {
                holder.itemMainBinding.tvTestResult.text = "Offline"
                holder.itemMainBinding.tvTestResult.setBackgroundResource(R.drawable.bg_pill_red)
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.apple_red))
            } else if (delayVal == 0L) {
                holder.itemMainBinding.tvTestResult.text = "N/A"
                holder.itemMainBinding.tvTestResult.setBackgroundResource(R.drawable.bg_pill_gray)
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.apple_gray))
            } else {
                holder.itemMainBinding.tvTestResult.text = "${delayVal}ms"
                holder.itemMainBinding.tvTestResult.setBackgroundResource(R.drawable.bg_pill_green)
                holder.itemMainBinding.tvTestResult.setTextColor(ContextCompat.getColor(context, R.color.apple_green))
            }

            // layoutIndicator (compatibility, set to invisible/unused)
            holder.itemMainBinding.layoutIndicator.setBackgroundResource(0)

            // subscription remarks (compatibility)
            val subRemarks = getSubscriptionRemarks(profile)
            holder.itemMainBinding.tvSubscription.text = subRemarks
            holder.itemMainBinding.layoutSubscription.visibility = if (subRemarks.isEmpty()) View.GONE else View.VISIBLE

            // layout - read only
            holder.itemMainBinding.layoutShare.visibility = View.GONE
            holder.itemMainBinding.layoutEdit.visibility = View.GONE
            holder.itemMainBinding.layoutRemove.visibility = View.GONE
            holder.itemMainBinding.layoutMore.visibility = View.GONE

            holder.itemMainBinding.infoContainer.setOnClickListener {
                adapterListener?.onSelectServer(guid)
            }
        }

    }

    /**
     * Gets the server address information
     * Hides part of IP or domain information for privacy protection
     * @param profile The server configuration
     * @return Formatted address string
     */
    private fun getAddress(profile: ProfileItem): String {
        return profile.description.nullIfBlank() ?: AngConfigManager.generateDescription(profile)
    }

    /**
     * Gets the subscription remarks information
     * @param profile The server configuration
     * @return Subscription remarks string, or empty string if none
     */
    private fun getSubscriptionRemarks(profile: ProfileItem): String {
        val subRemarks =
            if (mainViewModel.subscriptionId.isEmpty())
                MmkvManager.decodeSubscription(profile.subscriptionId)?.remarks?.firstOrNull()
            else
                null
        return subRemarks?.toString() ?: ""
    }

    private fun getProtocolDescription(profile: ProfileItem): String {
        if (profile.configType.isComplexType()) {
            return profile.configType.name
        }

        val parts = mutableListOf<String>()
        parts.add(profile.configType.name)

        // Transport: hide tcp or blank
        profile.network?.let { net ->
            if (net.isNotBlank() && !net.equals("tcp", ignoreCase = true)) {
                parts.add(net)
            }
        }

        // Security: hide blank or tls
        profile.security?.let { sec ->
            if (sec.isNotBlank() && !sec.equals("tls", ignoreCase = true)) {
                parts.add(sec)
            }
        }

        return parts.joinToString(" / ")
    }

    fun removeServerSub(guid: String, position: Int) {
        val idx = data.indexOfFirst { it.guid == guid }
        if (idx >= 0) {
            data.removeAt(idx)
            notifyItemRemoved(idx)
            notifyItemRangeChanged(idx, data.size - idx)
        }
    }

    fun setSelectServer(fromPosition: Int, toPosition: Int) {
        notifyItemChanged(fromPosition)
        notifyItemChanged(toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            VIEW_TYPE_ITEM ->
                MainViewHolder(ItemRecyclerMainBinding.inflate(LayoutInflater.from(parent.context), parent, false))

            else ->
                FooterViewHolder(ItemRecyclerFooterBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == data.size) {
            VIEW_TYPE_FOOTER
        } else {
            VIEW_TYPE_ITEM
        }
    }

    open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun onItemSelected() {
            itemView.setBackgroundColor(Color.LTGRAY)
        }

        fun onItemClear() {
            itemView.setBackgroundColor(0)
        }
    }

    class MainViewHolder(val itemMainBinding: ItemRecyclerMainBinding) :
        BaseViewHolder(itemMainBinding.root), ItemTouchHelperViewHolder

    class FooterViewHolder(val itemFooterBinding: ItemRecyclerFooterBinding) :
        BaseViewHolder(itemFooterBinding.root)

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mainViewModel.swapServer(fromPosition, toPosition)
        if (fromPosition < data.size && toPosition < data.size) {
            Collections.swap(data, fromPosition, toPosition)
        }
        notifyItemMoved(fromPosition, toPosition)
        return true
    }

    override fun onItemMoveCompleted() {
        // do nothing
    }

    override fun onItemDismiss(position: Int) {
    }

    private fun extractFlagEmoji(text: String): String? {
        if (text.length < 4) return null
        for (i in 0..text.length - 4) {
            val firstCharHigh = text[i]
            val firstCharLow = text[i + 1]
            val secondCharHigh = text[i + 2]
            val secondCharLow = text[i + 3]
            if (firstCharHigh == '\uD83C' && firstCharLow in '\uDDE6'..'\uDDFF' &&
                secondCharHigh == '\uD83C' && secondCharLow in '\uDDE6'..'\uDDFF') {
                return text.substring(i, i + 4)
            }
        }
        return null
    }
}
