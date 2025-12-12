package com.jl.nxinfo

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.snackbar.Snackbar
import com.jl.nxinfo.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val viewModel: RomInfoViewModel by viewModels()
    private var menu: Menu? = null

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedFile(it)
        }
    }

    private val keysPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleProdKeysFile(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_rom_info, R.id.navigation_cheats, R.id.navigation_search))
        setupActionBarWithNavController(navController, appBarConfiguration)

        val navView = binding.root.findViewById<NavigationBarView>(R.id.navigation_view)
        navView?.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            menu?.findItem(R.id.action_redownload_database)?.isVisible = destination.id == R.id.navigation_search
            menu?.findItem(R.id.action_select_keys)?.isVisible = destination.id == R.id.navigation_rom_info
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        val fileName = uri.lastPathSegment ?: "Unknown"
        val extension = fileName.substringAfterLast('.', "").lowercase()

        if (extension !in listOf("xci", "nsp", "nsz", "xcz")) {
            Snackbar.make(binding.root, "Please select a valid Switch ROM file (.xci, .nsp, .nsz, .xcz)", Snackbar.LENGTH_LONG)
                .show()
            return
        }

        viewModel.setLoading(true)

        CoroutineScope(Dispatchers.Main).launch {
            val romInfo = withContext(Dispatchers.IO) {
                val parser = SwitchRomParser(this@MainActivity)
                parser.parseRom(uri)
            }

            viewModel.setRomInfo(romInfo)
            viewModel.setLoading(false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        this.menu = menu
        // Set initial visibility
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        menu.findItem(R.id.action_redownload_database)?.isVisible = navController.currentDestination?.id == R.id.navigation_search
        menu.findItem(R.id.action_select_keys)?.isVisible = navController.currentDestination?.id == R.id.navigation_rom_info
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_keys -> {
                keysPickerLauncher.launch("*/*")
                true
            }
            R.id.action_redownload_database -> {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                if (currentFragment is SearchFragment) {
                    currentFragment.redownloadDatabase()
                }
                true
            }
            R.id.action_about -> {
                showAboutDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAboutDialog() {

        val pInfo = packageManager.getPackageInfo(packageName, 0)
        val appVersionName = pInfo.versionName

        val device = "${Build.MANUFACTURER} ${Build.MODEL}"
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        val aboutInfo = """
            App Version: $appVersionName
            Device: $device
            Android Version: $androidVersion
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("About nxinfo")
            .setMessage(aboutInfo)
            .setPositiveButton("OK", null)
            .show()
    }

    fun openKeysFilePicker() {
        keysPickerLauncher.launch("*/*")
    }

    private fun handleProdKeysFile(uri: Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                val keysManager = ProdKeysManager.getInstance(this@MainActivity)
                keysManager.saveProdKeysFile(uri)
            }

            val message = when (result) {
                is KeysResult.Success -> {
                    val keysManager = ProdKeysManager.getInstance(this@MainActivity)
                    val summary = keysManager.getKeysSummary()
                    getString(R.string.keys_file_selected) + "\n" + summary
                }
                is KeysResult.InvalidFile -> {
                    getString(R.string.keys_file_invalid)
                }
                is KeysResult.NotFoundInZip -> {
                    getString(R.string.keys_file_not_found_in_zip)
                }
                is KeysResult.Error -> {
                    getString(R.string.keys_file_error, result.message)
                }
            }

            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .show()

            if (result is KeysResult.Success) {
                val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
                val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
                if (currentFragment is FirstFragment) {
                    currentFragment.refreshKeysStatus()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private external fun stringFromJNI(): String

    companion object {
        init {
            System.loadLibrary("nstool")
        }
    }
}