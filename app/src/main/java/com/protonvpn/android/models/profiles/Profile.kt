/*
 * Copyright (c) 2020 Proton Technologies AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.protonvpn.android.models.profiles

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.protonvpn.android.R
import com.protonvpn.android.components.Listable
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.UserData
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.Server
import com.protonvpn.android.utils.ServerManager
import java.io.Serializable
import java.util.Random

data class Profile(val name: String, val color: String, val wrapper: ServerWrapper) : Serializable, Listable {

    private var protocol: String? = null
    private var transmissionProtocol: String? = null

    fun getDisplayName(context: Context): String = if (isPreBakedProfile)
        context.getString(if (wrapper.isPreBakedFastest) R.string.profileFastest else R.string.profileRandom)
    else
        name

    @get:DrawableRes val profileIcon: Int get() = when {
        wrapper.isPreBakedFastest -> R.drawable.ic_fastest
        wrapper.isPreBakedRandom -> R.drawable.ic_random
        else -> R.drawable.ic_location
    }

    val isPreBakedProfile: Boolean
        get() = wrapper.isPreBakedProfile

    val server: Server? get() {
        val server = wrapper.server
        if (server != null && (wrapper.isFastestInCountry || wrapper.isPreBakedFastest)) {
            server.selectedAsFastest = true
        }
        return server
    }

    val isSecureCore get() = wrapper.isSecureCoreServer

    override fun getLabel(context: Context) = getDisplayName(context)

    fun getTransmissionProtocol(userData: UserData): TransmissionProtocol =
        transmissionProtocol?.let { TransmissionProtocol.valueOf(it) } ?: userData.transmissionProtocol

    fun setTransmissionProtocol(value: String?) {
        transmissionProtocol = value
    }

    fun getProtocol(userData: UserData): VpnProtocol =
        protocol?.let { VpnProtocol.valueOf(it) } ?: userData.selectedProtocol

    fun setProtocol(protocol: String?) {
        this.protocol = protocol
    }

    companion object {
        fun getTempProfile(server: Server?, manager: ServerManager?) =
                Profile("", "", ServerWrapper.makeWithServer(server, manager))

        fun getRandomProfileColor(context: Context): String {
            val name = "pickerColor" + (Random().nextInt(18 - 1) + 1)
            val colorRes =
                    context.resources.getIdentifier(name, "color", context.packageName)
            return "#" + Integer.toHexString(ContextCompat.getColor(context, colorRes))
        }
    }
}
