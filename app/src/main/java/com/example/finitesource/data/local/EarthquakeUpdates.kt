package com.example.finitesource.data.local

import com.example.finitesource.data.local.earthquake.Earthquake

data class EarthquakeUpdates(
	val newEarthquakes: List<Earthquake>,
	val finiteSourceUpdated: List<Earthquake>,
	val newProducts: Map<Earthquake, List<Products>>
) {
	// function to check if there are updates
	fun hasUpdates(): Boolean {
		return newEarthquakes.isNotEmpty() || finiteSourceUpdated.isNotEmpty() || newProducts.isNotEmpty()
	}
}