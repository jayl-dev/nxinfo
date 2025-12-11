package com.jl.nxinfo

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import com.jl.nxinfo.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val viewModel: RomInfoViewModel by viewModels()

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

        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        val bottomNav = binding.root.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)
        bottomNav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_rom_info -> {
                    if (navController.currentDestination?.id != R.id.FirstFragment) {
                        navController.navigate(R.id.FirstFragment)
                    }
                    true
                }
                R.id.navigation_cheats -> {
                    if (navController.currentDestination?.id != R.id.SecondFragment) {
                        navController.navigate(R.id.SecondFragment)
                    }
                    true
                }
                else -> false
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.FirstFragment -> bottomNav?.selectedItemId = R.id.navigation_rom_info
                R.id.SecondFragment -> bottomNav?.selectedItemId = R.id.navigation_cheats
            }
        }

        binding.fab.setOnClickListener {
            val keysManager = ProdKeysManager.getInstance(this)
            if (!keysManager.isKeysLoaded()) {
                Snackbar.make(binding.root, "Please import prod.keys file first (use menu button)", Snackbar.LENGTH_LONG)
                    .setAnchorView(R.id.fab)
                    .setAction("Select Keys") {
                        openKeysFilePicker()
                    }
                    .show()
                return@setOnClickListener
            }

            filePickerLauncher.launch("*/*")
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        val fileName = uri.lastPathSegment ?: "Unknown"
        val extension = fileName.substringAfterLast('.', "").lowercase()

        if (extension !in listOf("xci", "nsp", "nsz", "xcz")) {
            Snackbar.make(binding.root, "Please select a valid Switch ROM file (.xci, .nsp, .nsz, .xcz)", Snackbar.LENGTH_LONG)
                .setAnchorView(R.id.fab)
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
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_select_keys -> {
                keysPickerLauncher.launch("*/*")
                true
            }
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
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
                .setAnchorView(R.id.fab)
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