package com.example.finitesource.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.finitesource.R
import com.example.finitesource.data.local.CatalogConfig
import com.example.finitesource.data.local.earthquake.focalplane.FocalPlaneType
import com.example.finitesource.databinding.ActivityMainBinding
import com.example.finitesource.databinding.LegendBottomSheetBinding
import com.example.finitesource.getStatusBarHeight
import com.example.finitesource.isDarkTheme
import com.example.finitesource.lightStatusBar
import com.example.finitesource.ui.mapoverlays.SlipPalette
import com.example.finitesource.ui.persistentbottomsheet.behavior.ViewPagerBottomSheetBehavior
import com.example.finitesource.viewmodels.EarthquakesViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

	private val earthquakesViewModel: EarthquakesViewModel by viewModels()
	private val binding: ActivityMainBinding by lazy {
		ActivityMainBinding.inflate(layoutInflater)
	}
	private var isSearchViewShown: Boolean = false

	// TODO place this in the xml
	private val slipPaletteView by lazy { SlipPalette(this) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		// load the osmdroid configuration
		Configuration.getInstance()
			.load(
				applicationContext,
				PreferenceManager.getDefaultSharedPreferences(applicationContext)
			)
		// inflate the layout and set it as the content view
		setContentView(binding.root)

		// set the toolbar as the default action bar
		setSupportActionBar(binding.toolbar)
		supportActionBar?.hide()

		// change the color of the status bar to match the theme
		lightStatusBar(window, true)

		// handle the toolbar state
		if (savedInstanceState != null) {
			if (savedInstanceState.getBoolean(TOOL_BAR_SHOWN_TAG))
				showToolbar()
			binding.compassButton.rotation =
				savedInstanceState.getFloat(COMPASS_BUTTON_ROTATION_TAG)
		}

		// give the viewmodel to the mapview
		binding.customMapView.earthquakesViewModel = earthquakesViewModel
		// vige the viewmodel to the bottom sheet
		binding.persistentBottomSheet.earthquakesViewModel = earthquakesViewModel

		// set up the various map overlays
		mapOverlaysInit()

		// make the status bar transparent
		transparentStatusBar()

		// set up the search view and bar
		searchInit()

		// set up the bottom sheet
		persistentBottomSheetInit()

		// load the config
		// TODO block the app until the config and the earthquake data are loaded (show a loading screen)
		lifecycleScope.launch {
			CatalogConfig.init(this@MainActivity)
		}

		// do something with the updates
		earthquakesViewModel.getUpdates().observe(this) {
			if (it == null)
				Toast.makeText(
					this@MainActivity,
					"Failed to update earthquakes",    // TODO: use snackbar
					Toast.LENGTH_SHORT
				).show()
			Log.d("MainActivity", "Updates: $it")
		}

		earthquakesViewModel.earthquakes.observe(this) { earthquakes ->
			Log.d("MainActivity", "Earthquakes: ${earthquakes.size}")
			if (earthquakes.isNotEmpty()) {
				binding.customMapView.setEarthquakes(earthquakes)
			} else {
				// show a loading screen or something
			}
		}

		// observe the ui state
		earthquakesViewModel.uiState.observe(this) {
			// if there is a selected earthquake
			if (it.selectedEarthquake != null) {
				// show the back arrow in the search bar
				searchBarBackIcon()
				// set the text of the search bar to the name of the selected earthquake
				binding.searchBar.setText(it.selectedEarthquake.name)
				// show the bottom sheet
				binding.bottomSheetContainer.visibility = View.VISIBLE
				// if the global list is shown
				if (binding.searchResults.isShown || binding.searchErrorContainer.isShown) {
					// hide the global list
					binding.searchView.hide()
					// show the map
					binding.customMapView.visibility = View.VISIBLE
				}
				// handle the focal plane switch
				if (it.selectedEarthquake.focalPlaneCount() == 0 && !binding.focalPlaneSwitch.isShown) {
					// show the focal plane switch
					binding.focalPlaneSwitch.visibility = View.VISIBLE
					// reset the state of the switch
					binding.focalPlaneSwitch.isChecked = true
				}
				// handle the slip alpha slider and the slip palette
				if (it.selectedEarthquake.hasFiniteSource() && !it.loadingState.loading) {
					// show the slip alpha slider
					binding.slipAlphaSliderContainer.visibility = View.VISIBLE
					// reset the alpha
					binding.slipAlphaSlider.progress = 0
					// set the max slip in the palette, if it is 0 don't show the palette (shouldn't happen)
					val maxSlip = it.selectedEarthquake.focalPlaneMaxSlip(it.selectedFocalPlane!!)
					if (maxSlip > 0.0) {
						// set the max slip in the palette
						slipPaletteView.maxSlip = maxSlip
						// show the slip palette
						slipPaletteView.visibility = View.VISIBLE
					} else {
						// hide the slip palette
						slipPaletteView.visibility = View.GONE
					}
				}
			} else {    // if there is no selected earthquake (an event has been deselected)
				// hide the focal plane switch
				binding.focalPlaneSwitch.visibility = View.GONE
				// hide the slip alpha slider
				binding.slipAlphaSliderContainer.visibility = View.INVISIBLE
				// hide the slip palette
				slipPaletteView.visibility = View.GONE
				// if the search bar is showing the back arrow
				searchBarSearchIcon()
				// hide the bottom sheet
				binding.bottomSheetContainer.visibility = View.GONE
			}
		}
	}

	private fun persistentBottomSheetInit() {
		// Initialize and set up the bottom sheet behavior
		val bottomSheetBehavior =
			ViewPagerBottomSheetBehavior.from(binding.persistentBottomSheet).apply {
//				state = savedState
				peekHeight = resources.getDimension(R.dimen.bottom_sheet_peek_height).toInt()
				isHideable = false
				expandedOffset = getStatusBarHeight(resources)
			}

		bottomSheetBehavior.setBottomSheetCallback(object :
			ViewPagerBottomSheetBehavior.BottomSheetCallback() {
			override fun onStateChanged(bottomSheet: View, newState: Int) {}

			override fun onSlide(bottomSheet: View, slideOffset: Float) {
				if (slideOffset >= BOTTOM_SHEET_MAXIMIZED_SLIDE_OFFSET) {
					showToolbar()
					// hide the mapview for performance reasons
					binding.customMapView.visibility = View.GONE
				} else {
					hideToolbar()
					// show the mapview again
					binding.customMapView.visibility = View.VISIBLE
				}
				val peekHeight = bottomSheetBehavior.peekHeight
				val offset =
					(slideOffset * (binding.persistentBottomSheet.height - peekHeight - bottomSheetBehavior.expandedOffset)
							+ peekHeight) + resources.getDimension(R.dimen.scale_bar_bottom_margin)
				binding.customMapView.setScaleBarYOffset(offset.toInt())
			}
		})

		binding.persistentBottomSheet.bottomSheetBehavior = bottomSheetBehavior
	}

	/**
	 * Initializes the map view overlays.
	 *
	 * See [compassButtonInit], [legendButtonInit], [focalPlaneSwitchInit] and [slipAlphaSliderInit].
	 */
	private fun mapOverlaysInit() {
		// TODO fix the whole overlay system
		// right now it is a mess of views and dependencies, it is not scalable and it is not
		// easy to understand
		binding.customMapView.compassButton = binding.compassButton
		legendButtonInit()
		// add the slip palette
		binding.mainContent.addView(slipPaletteView, 3)
		focalPlaneSwitchInit()
		slipAlphaSliderInit()
	}

	/**
	 * Initializes the focal plane switch.
	 */
	private fun focalPlaneSwitchInit() {
		// set the listener for the focal plane switch
		binding.focalPlaneSwitch.setOnCheckedChangeListener { _, isChecked ->
			// TODO fix the focalplane switch resetting to FP1 when the screen is rotated

			// if the selected events has both focal planes (this is a temporary fix)
			if (earthquakesViewModel.uiState.value?.selectedEarthquake?.focalPlaneCount() == 0)
			// call the viewModel
				earthquakesViewModel.selectFocalPlane(
					when (isChecked) {
						true -> FocalPlaneType.FP1
						false -> FocalPlaneType.FP2
					}
				)
		}
	}

	/**
	 * If the toolbar is not visible starts the transition from the search bar to the toolbar, otherwise does nothing.
	 * It also changes the color of the status bar to match the theme.
	 */
	private fun showToolbar() {
		if (!binding.toolbar.isShown) {
			binding.searchBar.expand(binding.toolbar)
			binding.toolBarContainer.background = getColor(R.color.background).toDrawable()
			lightStatusBar(window, !isDarkTheme(this))
		}
	}

	/** If the toolbar is visible starts the transition from the toolbar to the search bar, otherwise does nothing.
	 * It also changes the color of the status bar to transparent.
	 */
	private fun hideToolbar() {
		if (binding.toolbar.isShown) {
			binding.searchBar.collapse(binding.toolbar)
			binding.toolBarContainer.background = Color.TRANSPARENT.toDrawable()
			lightStatusBar(window, true)
		}
	}

	/**
	 * Initializes the legend button.
	 */
	private fun legendButtonInit() {
		// Create a bottom sheet dialog for the legend
		val legendBottomSheetDialog = BottomSheetDialog(this)

		// Set a click listener for the legend button
		binding.legendButton.setOnClickListener {
			// Inflate the layout for the bottom sheet
			val legendBottomSheetBinding = LegendBottomSheetBinding.inflate(layoutInflater)
			legendBottomSheetDialog.setContentView(legendBottomSheetBinding.root)

			// Change the color of the event icon for the "No Finite Source" event
			val noFiniteSourceIcon = ResourcesCompat.getDrawable(
				resources,
				R.drawable.event_circle_icon,
				theme
			)?.mutate() as LayerDrawable
			noFiniteSourceIcon.findDrawableByLayerId(R.id.background).setTint(
				getColor(R.color.no_finite_source)
			)

			// Set the modified icon for the "No Finite Source" event
			legendBottomSheetBinding.legendNoFiniteSourceIcon.setImageDrawable(noFiniteSourceIcon)

			// Show the legend bottom sheet dialog
			legendBottomSheetDialog.show()
		}
	}

	/**
	 * Initializes the slip alpha slider.
	 */
	private fun slipAlphaSliderInit() {
		// set the listener for the slip alpha slider
		binding.slipAlphaSlider.setOnSeekBarChangeListener(object :
			SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
				// set the alpha on the mapview
				binding.customMapView.setSlipAlpha(255 - progress)
			}

			override fun onStartTrackingTouch(p0: SeekBar?) {}
			override fun onStopTrackingTouch(p0: SeekBar?) {}
		})
	}

	/**
	 * Initializes the search view, the global list of events and the navigation drawer.
	 */
	private fun searchInit() {
		// Initialize the global list
		globalListInit()

		// Initialize the navigation drawer
		navigationDrawerInit()

		// Retry button click listener for downloading global events again
		binding.searchErrorRetryButton.setOnClickListener {
			// TODO: Implement the retry button
//			searchViewModel.loadGlobalEvents()
		}

		// Set the icon color
		binding.searchBar.menu.findItem(R.id.navigation_drawer_item).iconTintList =
			ColorStateList.valueOf(getColor(R.color.on_background))

		// Set the tag to false to indicate that the search bar is not showing the back arrow
		binding.searchBar.tag = false

		// Navigation drawer icon click listener
		binding.searchBar.setOnMenuItemClickListener {
			when (it.itemId) {
				R.id.navigation_drawer_item -> {
					// Open the navigation drawer
					binding.navigationDrawer.openDrawer(GravityCompat.START)
					true
				}

				else -> false
			}
		}

		// Observe changes in the global list
		// TODO
//		globalListViewModel.globalListItemLiveData.observe(this) { list ->
//			if (list == null) {
//				// Hide the global list and show the error container
//				binding.searchResults.visibility = View.GONE
//				binding.searchErrorContainer.visibility = View.VISIBLE
//			} else {
//				// Show the global list and hide the error container
//				binding.searchResults.visibility = View.VISIBLE
//				binding.searchErrorContainer.visibility = View.GONE
//			}
//		}


		// Listen to changes in the search view visibility
		binding.searchView.viewTreeObserver.addOnGlobalLayoutListener {
			// If the bottom sheet is not expanded update the search view visibility state
//			if (!binding.persistentBottomSheet.isExpanded()) {
			val isCurrentlyShown =
				binding.searchResults.isShown || binding.searchErrorContainer.isShown
			if (isCurrentlyShown != isSearchViewShown) {
				isSearchViewShown = isCurrentlyShown
				if (isSearchViewShown) {
					// change the color according to the theme
					lightStatusBar(window, !isDarkTheme(this))
					// hide the map for performance reasons
					binding.customMapView.visibility = View.GONE
				} else {
					// the search view is hidden, so we are looking at the map
					lightStatusBar(window, true)
					// show the map again
					binding.customMapView.visibility = View.VISIBLE
				}
			}
//			}
		}

		// Listen to text changes in the search view
		binding.searchView.editText.addTextChangedListener(object : TextWatcher {
			override fun afterTextChanged(s: Editable?) {
				// Post an event to the event channel with the updated search query
				val adapter = binding.searchResults.adapter as GlobalListAdapter
				adapter.filterBySearchQuery(s.toString())
			}

			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
		})
	}

	/**
	 * Initializes the global list of events inside the search view.
	 */
	private fun globalListInit() {
		// Set up the RecyclerView and its adapter
		val layoutManager = LinearLayoutManager(this)
		layoutManager.orientation = LinearLayoutManager.VERTICAL
		binding.searchResults.layoutManager = layoutManager

		// Attach the adapter to the RecyclerView and pass the viewModel
		binding.searchResults.adapter = GlobalListAdapter(earthquakesViewModel, this)

		binding.searchResults.isVerticalScrollBarEnabled = true

		// Hide the keyboard when the RecyclerView is scrolled
		binding.searchResults.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				super.onScrolled(recyclerView, dx, dy)
				// TODO hide the keyboard when the user touches the list
				// not just when the list is scrolled
				if (dy != 0) {
					val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
					imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
				}
			}
		})
	}

	/**
	 * Initializes the navigation drawer.
	 */
	private fun navigationDrawerInit() {
		// Navigation drawer item click listener
		binding.navigationView.setNavigationItemSelectedListener { menuItem ->
			when (menuItem.itemId) {
				R.id.info_item -> {
					// TODO: Implement the info activity
					true
				}

				else -> false
			}
		}

		// Lock the navigation drawer to prevent opening it with a swipe
		binding.navigationDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

		// Drawer listener for handling drawer open/close events
		binding.navigationDrawer.addDrawerListener(object : DrawerLayout.DrawerListener {
			override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
			override fun onDrawerOpened(drawerView: View) {
				// Change status bar color when drawer is opened
				lightStatusBar(window, !isDarkTheme(this@MainActivity))
			}

			override fun onDrawerClosed(drawerView: View) {
				// Change status bar color when drawer is closed
				lightStatusBar(window, true)
			}

			override fun onDrawerStateChanged(newState: Int) {}
		})
	}

	/**
	 * Changes the search bar navigation icon to the back arrow and sets the behavior of the back arrow.
	 */
	private fun searchBarBackIcon() {
		// change the icon of the search bar to the back arrow
		binding.searchBar.setNavigationIcon(R.drawable.back_icon)
		binding.searchBar.tag = true
		// change the behavior of the back arrow
		binding.searchBar.setNavigationOnClickListener {
			// deselect the earthquake event
			earthquakesViewModel.deselectEarthquake()
		}
	}

	/**
	 * Changes the search bar navigation icon to the search icon and removes the behavior of the back arrow.
	 */
	private fun searchBarSearchIcon() {
		// change the icon of the search bar to the search icon
		binding.searchBar.setNavigationIcon(R.drawable.search_icon)
		binding.searchBar.tag = false
		// remove the behavior of the back arrow
		binding.searchBar.setNavigationOnClickListener(null)
		// remove the text of the search bar
		binding.searchBar.setText("")
	}


	private fun transparentStatusBar() {
		// TODO don't use deprecated methods
		window.decorView.systemUiVisibility =
			View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
		window.statusBarColor = Color.TRANSPARENT
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		// save the state of the toolbar
		outState.putBoolean(TOOL_BAR_SHOWN_TAG, binding.toolbar.isShown)
		// save the rotation of the compass button
		outState.putFloat(COMPASS_BUTTON_ROTATION_TAG, binding.compassButton.rotation)
	}
}

const val BOTTOM_SHEET_MAXIMIZED_SLIDE_OFFSET = 0.93f
const val TOOL_BAR_SHOWN_TAG = "toolbarShown"
const val COMPASS_BUTTON_ROTATION_TAG = "compassButtonRotation"


