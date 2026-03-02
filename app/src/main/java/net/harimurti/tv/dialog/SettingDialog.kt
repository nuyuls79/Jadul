package net.harimurti.tv.dialog

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.*
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import net.harimurti.tv.MainActivity
import net.harimurti.tv.R
import net.harimurti.tv.databinding.SettingDialogBinding
import net.harimurti.tv.extra.Preferences

class SettingDialog : DialogFragment() {
    val preferences = Preferences()
    // Hanya dua tab: App dan About
    private val tabFragment = arrayOf(SettingAppFragment(), SettingAboutFragment())
    private val tabTitle = arrayOf(R.string.tab_app, R.string.tab_about)
    private var isCancelled = true

    companion object {
        // Tidak perlu isSourcesChanged karena sumber tidak diedit di dialog
    }

    @Suppress("DEPRECATION")
    inner class FragmentAdapter(fragmentManager: FragmentManager?) :
        FragmentPagerAdapter(fragmentManager!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItem(position: Int): Fragment {
            return tabFragment[position]
        }

        override fun getCount(): Int {
            return tabFragment.size
        }

        override fun getPageTitle(position: Int): CharSequence {
            return getString(tabTitle[position])
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AppCompatDialog(activity, R.style.SettingsDialogThemeOverlay).apply {
            setTitle(R.string.settings)
            setCanceledOnTouchOutside(false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = SettingDialogBinding.inflate(inflater, container, false)

        // Inisialisasi nilai dari Preferences untuk fragment App
        SettingAppFragment.launchAtBoot = preferences.launchAtBoot
        SettingAppFragment.playLastWatched = preferences.playLastWatched
        SettingAppFragment.sortFavorite = preferences.sortFavorite
        SettingAppFragment.sortCategory = preferences.sortCategory
        SettingAppFragment.sortChannel = preferences.sortChannel
        SettingAppFragment.optimizePrebuffer = preferences.optimizePrebuffer
        SettingAppFragment.reverseNavigation = preferences.reverseNavigation

        // Tidak ada inisialisasi untuk SettingSourcesFragment

        // ViewPager dengan adapter dua tab
        binding.settingViewPager.adapter = FragmentAdapter(childFragmentManager)
        // TabLayout
        binding.settingTabLayout.setupWithViewPager(binding.settingViewPager)
        // Tombol Cancel
        binding.settingCancelButton.apply {
            setOnClickListener { dismiss() }
        }
        // Tombol OK
        binding.settingOkButton.apply {
            setOnClickListener {
                isCancelled = false
                // Simpan pengaturan dari AppFragment
                preferences.launchAtBoot = SettingAppFragment.launchAtBoot
                preferences.playLastWatched = SettingAppFragment.playLastWatched
                preferences.sortFavorite = SettingAppFragment.sortFavorite
                preferences.sortCategory = SettingAppFragment.sortCategory
                preferences.sortChannel = SettingAppFragment.sortChannel
                preferences.optimizePrebuffer = SettingAppFragment.optimizePrebuffer
                preferences.reverseNavigation = SettingAppFragment.reverseNavigation
                // Tidak ada penyimpanan sumber karena tab dihapus
                dismiss()
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Tidak perlu broadcast karena sumber tidak berubah
        // Tidak ada pembatalan countryId karena tidak ada tab sumber
    }
}