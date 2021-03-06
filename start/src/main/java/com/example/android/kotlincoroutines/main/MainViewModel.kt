/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.kotlincoroutines.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.kotlincoroutines.util.singleArgViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainViewModel designed to store and manage UI-related data in a lifecycle conscious way. This
 * allows data to survive configuration changes such as screen rotations. In addition, background
 * work such as fetching network results can continue through configuration changes and deliver
 * results after the new Fragment or Activity is available.
 *
 * @param repository the data source this ViewModel will fetch results from.
 */
class MainViewModel(private val repository: TitleRepository) : ViewModel() {

    companion object {
        /**
         * Factory for creating [MainViewModel]
         *
         * @param arg the repository to pass to [MainViewModel]
         */
        val FACTORY = singleArgViewModelFactory(::MainViewModel)
    }

    /**
     * Request a snackbar to display a string.
     *
     * This variable is private because we don't want to expose MutableLiveData
     *
     * MutableLiveData allows anyone to set a value, and MainViewModel is the only
     * class that should be setting values.
     */
    private val _snackBar = MutableLiveData<String?>()

    /**
     * Request a snackbar to display a string.
     */
    val snackbar: LiveData<String?>
        get() = _snackBar

    /**
     * Update title text via this LiveData
     */
    val title = repository.title

    private val _spinner = MutableLiveData<Boolean>(false)

    /**
     * Show a loading spinner if true
     */
    val spinner: LiveData<Boolean>
        get() = _spinner

    /**
     * Count of taps on the screen
     */
    private var tapCount = 0

    /**
     * LiveData with formatted tap count.
     */
    private val _taps = MutableLiveData<String>("$tapCount taps")

    /**
     * Public view of tap live data.
     */
    val taps: LiveData<String>
        get() = _taps

    /**
     * Respond to onClick events by refreshing the title.
     *
     * The loading spinner will display until a result is returned, and errors will trigger
     * a snackbar.
     */
    fun onMainViewClicked() {
        refreshTitle()
        updateTaps()
    }

    /**
     * Wait one second then update the tap count.
     */
    private fun updateTaps() {
        // launch a coroutine in viewModelScope
        viewModelScope.launch {
            tapCount++
            // suspend this coroutine for one second
            delay(1_000)
            // resume in the main dispatcher
            // _snackbar.value can be called directly from main thread
            _taps.postValue("${tapCount} taps")
        }
    }

    /**
     * Called immediately after the UI shows the snackbar.
     */
    fun onSnackbarShown() {
        _snackBar.value = null
    }

    /**
     * Refresh the title, showing a loading spinner while it refreshes and errors via snackbar.
     */
    fun refreshTitle() {
        launchDataLoad {
            repository.refreshTitle()
        }

//        /**
//         *  This will use Dispatchers.Main which is OK.
//         *  Even though refreshTitle will make a network request and database query
//         *  it can use coroutines to expose a main-safe interface.
//         *
//         *  This means it'll be safe to call it from the main thread.
//         */
//        viewModelScope.launch {
//            try {
//                // UI Spinner's status => show
//                _spinner.value = true
//
//                /**
//                 * However, since refreshTitle is a suspending function,
//                 * it executes differently than a normal function.
//                 *
//                 * We don't have to pass a callback.
//                 * The coroutine will suspend until it is resumed by refreshTitle.
//                 * While it looks just like a regular blocking function call,
//                 * it will automatically wait until the network and database query
//                 * are complete before resuming without blocking the main thread.
//                 */
//                repository.refreshTitle()
//            } catch (error: TitleRefreshError) {
//                /**
//                 *  if you throw an exception out of a coroutine ???
//                 *  that coroutine will cancel it's parent by default.
//                 *  That means it's easy to cancel several related tasks together.
//                 */
//                _snackBar.value = error.message
//            } finally {
//                // UI Spinner's status => hide
//                _spinner.value = false
//            }
//        }
    }

    /**
     * By abstracting the logic around showing a loading spinner and showing errors,
     * we've simplified our actual code needed to load data.
     * Showing a spinner or displaying an error is something
     * that's easy to generalize to any data loading,
     * while the actual data source and destination needs to be specified every time.
     *
     * To build this abstraction,
     * launchDataLoad takes an argument block that is a suspend lambda.
     * A suspend lambda allows you to call suspend functions.
     * That's how Kotlin implements the coroutine builders launch and runBlocking
     * we've been using in this codelab.
     *
     * // "block: suspend () -> Unit" => suspend lambda
     *
     * @param block
     * @receiver
     * @return
     */
    private fun launchDataLoad(block: suspend () -> Unit): Job {
        return viewModelScope.launch {
            try {
                _spinner.value = true
                block()
            } catch (error: TitleRefreshError) {
                _snackBar.value = error.message
            } finally {
                _spinner.value = false
            }
        }
    }
}
