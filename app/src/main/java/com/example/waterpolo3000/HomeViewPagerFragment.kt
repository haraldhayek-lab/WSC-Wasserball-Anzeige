package com.example.waterpolo3000

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayoutMediator
import com.example.waterpolo3000.adapters.GAME_PAGE_INDEX
import com.example.waterpolo3000.adapters.PROTOCOL_PAGE_INDEX
import com.example.waterpolo3000.adapters.WaterpoloPagerAdapter
import com.example.waterpolo3000.databinding.FragmentViewPagerBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeViewPagerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentViewPagerBinding.inflate(inflater, container, false)
        val tabLayout = binding.tabs
        val viewPager = binding.viewPager

        viewPager.adapter = WaterpoloPagerAdapter(this)
        viewPager.offscreenPageLimit = 2

        // Set the icon and text for each tab
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.setIcon(getTabIcon(position))
//            tab.text = getTabTitle(position)
        }.attach()

//        (activity as AppCompatActivity).setSupportActionBar(binding.toolbar)

        return binding.root
    }

    private fun getTabIcon(position: Int): Int {
        return when (position) {
            GAME_PAGE_INDEX -> R.drawable.game_tab_selector
            PROTOCOL_PAGE_INDEX -> R.drawable.protocol_tab_selector
            else -> throw IndexOutOfBoundsException()
        }
    }

//    private fun getTabTitle(position: Int): String? {
//        return when (position) {
//            GAME_PAGE_INDEX -> getString(R.string.tab_1)
//            PROTOCOL_PAGE_INDEX -> getString(R.string.tab_2)
//            else -> null
//        }
//    }
}
