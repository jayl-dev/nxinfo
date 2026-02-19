package com.jl.nxinfo

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class SearchFragment : Fragment() {

    private lateinit var cardDownloadInstructions: MaterialCardView
    private lateinit var buttonDownloadDatabase: MaterialButton
    private lateinit var containerDownloadProgress: View
    private lateinit var progressDownload: ProgressBar
    private lateinit var textDownloadStatus: TextView
    private lateinit var containerSearch: View
    private lateinit var editSearch: TextInputEditText
    private lateinit var textDatabaseInfo: TextView
    private lateinit var recyclerSearchResults: RecyclerView
    private lateinit var containerEmptyState: View
    private lateinit var textEmptyState: TextView
    private lateinit var buttonRedownloadDatabase: MaterialButton

    private val databaseUrl = "https://github.com/jayl-dev/titledb/releases/latest/download/switch_games.json"
    private val databaseFileName = "switch_games.json"
    private lateinit var searchAdapter: GameSearchAdapter

    private val viewModel: SearchViewModel by viewModels()
    private var isRestoringState = false



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        cardDownloadInstructions = view.findViewById(R.id.card_download_instructions)
        buttonDownloadDatabase = view.findViewById(R.id.button_download_database)
        containerDownloadProgress = view.findViewById(R.id.container_download_progress)
        progressDownload = view.findViewById(R.id.progress_download)
        textDownloadStatus = view.findViewById(R.id.text_download_status)
        containerSearch = view.findViewById(R.id.container_search)
        editSearch = view.findViewById(R.id.edit_search)
        textDatabaseInfo = view.findViewById(R.id.text_database_info)
        recyclerSearchResults = view.findViewById(R.id.recycler_search_results)
        containerEmptyState = view.findViewById(R.id.container_empty_state)
        textEmptyState = view.findViewById(R.id.text_empty_state)
        buttonRedownloadDatabase = view.findViewById(R.id.button_redownload_database)

        // Setup RecyclerView
        searchAdapter = GameSearchAdapter(this) { position ->
            viewModel.setExpandedPosition(position)
        }
        recyclerSearchResults.adapter = searchAdapter
        recyclerSearchResults.layoutManager = LinearLayoutManager(requireContext())

        // Setup download button
        buttonDownloadDatabase.setOnClickListener {
            downloadDatabase()
        }

        // Setup re-download button
        buttonRedownloadDatabase.setOnClickListener {
            redownloadDatabase()
        }

        // Setup search
        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isRestoringState) {
                    performSearch(s?.toString() ?: "")
                }
            }
        })

        // Setup ViewModel observers
        setupObservers()

        // Check if database exists or restore state
        if (viewModel.isDatabaseLoaded.value == true) {
            // Database already loaded in ViewModel, restore UI state
            restoreState()
        } else {
            // Check if database exists on disk
            checkDatabaseStatus()
        }
    }

    private fun setupObservers() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            searchAdapter.updateGames(results)

            if (results.isEmpty() && viewModel.searchQuery.value?.isNotBlank() == true) {
                containerEmptyState.visibility = View.VISIBLE
                textEmptyState.text = "No games found for \"${viewModel.searchQuery.value}\""
                buttonRedownloadDatabase.visibility = View.VISIBLE
                recyclerSearchResults.visibility = View.GONE
            } else if (results.isEmpty()) {
                containerEmptyState.visibility = View.VISIBLE
                textEmptyState.text = "Enter a game name to search: English, 简/繁中文"
                buttonRedownloadDatabase.visibility = View.GONE
                recyclerSearchResults.visibility = View.GONE
            } else {
                containerEmptyState.visibility = View.GONE
                recyclerSearchResults.visibility = View.VISIBLE
            }
        }

        viewModel.expandedPosition.observe(viewLifecycleOwner) { position ->
            searchAdapter.setExpandedPosition(position)
        }

        viewModel.allGames.observe(viewLifecycleOwner) { games ->
            if (games.isNotEmpty()) {
                val totalGames = games.size
                val gamesWithChinese = games.count { it.nameZh.isNotBlank() }
                textDatabaseInfo.text = "Database loaded: $totalGames games ($gamesWithChinese 有中文名)"
            }
        }
    }

    private fun restoreState() {
        showSearchInterface()

        isRestoringState = true
        editSearch.setText(viewModel.searchQuery.value ?: "")
        isRestoringState = false

        // Results are automatically updated via observer
    }

    private fun checkDatabaseStatus() {
        val dbFile = File(requireContext().filesDir, databaseFileName)

        if (dbFile.exists()) {
            // Database exists, load it
            loadDatabase()
        } else {
            // Copy from assets
            copyDatabaseFromAssets()
        }
    }

    private fun copyDatabaseFromAssets() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    val dbFile = File(requireContext().filesDir, databaseFileName)
                    requireContext().assets.open(databaseFileName).use { inputStream ->
                        dbFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                loadDatabase()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error copying database: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
                showDownloadInstructions() // Fallback to download
            }
        }
    }

    private fun showDownloadInstructions() {
        cardDownloadInstructions.visibility = View.VISIBLE
        containerDownloadProgress.visibility = View.GONE
        containerSearch.visibility = View.GONE
    }

    private fun showSearchInterface() {
        cardDownloadInstructions.visibility = View.GONE
        containerDownloadProgress.visibility = View.GONE
        containerSearch.visibility = View.VISIBLE
    }

    private fun downloadDatabase() {
        // Hide card and show progress
        cardDownloadInstructions.visibility = View.GONE
        containerDownloadProgress.visibility = View.VISIBLE
        progressDownload.isIndeterminate = true
        textDownloadStatus.text = "Downloading game database..."

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    downloadDatabaseFile()
                }

                if (success) {
                    textDownloadStatus.text = "Download complete! Loading database..."
                    loadDatabase()
                } else {
                    textDownloadStatus.text = "Download failed. Please try again."
                    containerDownloadProgress.visibility = View.GONE
                    cardDownloadInstructions.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Failed to download database", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                containerDownloadProgress.visibility = View.GONE
                cardDownloadInstructions.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Failed to download database: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun downloadDatabaseFile(): Boolean {
        val dbFile = File(requireContext().filesDir, databaseFileName)
        return DownloadHelper.downloadToFile(databaseUrl, dbFile)
    }

    private fun loadDatabase() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val games = withContext(Dispatchers.IO) {
                    val dbFile = File(requireContext().filesDir, databaseFileName)
                    val json = dbFile.readText()
                    val gson = Gson()
                    val type = object : TypeToken<List<GameInfo>>() {}.type
                    gson.fromJson<List<GameInfo>>(json, type)
                }

                viewModel.setAllGames(games)
                viewModel.setDatabaseLoaded(true)
                showSearchInterface()

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error loading database: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
                viewModel.setDatabaseLoaded(false)
                // If loading fails, show download instructions again
                showDownloadInstructions()
            }
        }
    }

    private fun performSearch(query: String) {
        viewModel.setSearchQuery(query)

        if (query.isBlank()) {
            viewModel.setSearchResults(emptyList())
            return
        }

        val allGames = viewModel.allGames.value ?: emptyList()

        CoroutineScope(Dispatchers.Main).launch {
            val results = withContext(Dispatchers.IO) {
                // Fuzzy search implementation
                allGames
                    .map { game -> game to game.matchScore(query) }
                    .filter { it.second > 50 }
                    .sortedByDescending { it.second }
                    .take(100) // Limit to top 100 results
                    .map { it.first }
            }

            viewModel.setSearchResults(results)
        }
    }

    fun redownloadDatabase() {
        // Show confirmation dialog
        val activity = activity ?: return
        MaterialAlertDialogBuilder(activity)
            .setTitle("Re-download Database")
            .setMessage("This will delete the current database and download the latest version. Continue?")
            .setPositiveButton("Yes") { _, _ ->
                // Delete existing database
                val dbFile = File(requireContext().filesDir, databaseFileName)
                if (dbFile.exists()) {
                    dbFile.delete()
                }

                // Clear ViewModel state
                viewModel.setAllGames(emptyList())
                viewModel.setSearchResults(emptyList())
                viewModel.setSearchQuery("")
                viewModel.setDatabaseLoaded(false)
                viewModel.setExpandedPosition(-1)

                // Clear search field
                isRestoringState = true
                editSearch.setText("")
                isRestoringState = false

                // Hide search interface and show download card
                containerSearch.visibility = View.GONE
                cardDownloadInstructions.visibility = View.VISIBLE

                // Auto-start download
                downloadDatabase()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
