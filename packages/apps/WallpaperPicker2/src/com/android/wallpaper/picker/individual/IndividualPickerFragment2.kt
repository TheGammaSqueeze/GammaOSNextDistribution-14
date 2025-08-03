/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.wallpaper.picker.individual

import CreativeCategoryHolder
import android.app.Activity
import android.app.ProgressDialog
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM
import android.content.DialogInterface
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.Resources.ID_NULL
import android.graphics.Point
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.text.TextUtils
import android.util.ArraySet
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.wallpaper.R
import com.android.wallpaper.model.Category
import com.android.wallpaper.model.CategoryProvider
import com.android.wallpaper.model.CategoryReceiver
import com.android.wallpaper.model.LiveWallpaperInfo
import com.android.wallpaper.model.WallpaperCategory
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.model.WallpaperRotationInitializer
import com.android.wallpaper.model.WallpaperRotationInitializer.NetworkPreference
import com.android.wallpaper.module.InjectorProvider
import com.android.wallpaper.module.PackageStatusNotifier
import com.android.wallpaper.picker.AppbarFragment
import com.android.wallpaper.picker.FragmentTransactionChecker
import com.android.wallpaper.picker.MyPhotosStarter.MyPhotosStarterProvider
import com.android.wallpaper.picker.RotationStarter
import com.android.wallpaper.picker.StartRotationDialogFragment
import com.android.wallpaper.picker.StartRotationErrorDialogFragment
import com.android.wallpaper.util.ActivityUtils
import com.android.wallpaper.util.LaunchUtils
import com.android.wallpaper.util.SizeCalculator
import com.android.wallpaper.widget.GridPaddingDecoration
import com.android.wallpaper.widget.GridPaddingDecorationCreativeCategory
import com.android.wallpaper.widget.WallpaperPickerRecyclerViewAccessibilityDelegate
import com.android.wallpaper.widget.WallpaperPickerRecyclerViewAccessibilityDelegate.BottomSheetHost
import com.bumptech.glide.Glide
import com.bumptech.glide.MemoryCategory
import java.util.Date
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Displays the Main UI for picking an individual wallpaper image. */
class IndividualPickerFragment2 :
    AppbarFragment(),
    RotationStarter,
    StartRotationErrorDialogFragment.Listener,
    StartRotationDialogFragment.Listener {

    companion object {
        private const val TAG = "IndividualPickerFrag2"

        /**
         * Position of a special tile that doesn't belong to an individual wallpaper of the
         * category, such as "my photos" or "daily rotation".
         */
        private const val SPECIAL_FIXED_TILE_ADAPTER_POSITION = 0

        private const val ARG_CATEGORY_COLLECTION_ID = "category_collection_id"

        private const val UNUSED_REQUEST_CODE = 1
        private const val TAG_START_ROTATION_DIALOG = "start_rotation_dialog"
        private const val TAG_START_ROTATION_ERROR_DIALOG = "start_rotation_error_dialog"
        private const val PROGRESS_DIALOG_INDETERMINATE = true
        private const val KEY_NIGHT_MODE = "IndividualPickerFragment.NIGHT_MODE"
        private const val MAX_CAPACITY_IN_FEWER_COLUMN_LAYOUT = 8
        private val PROGRESS_DIALOG_NO_TITLE = null
        private var isCreativeCategory = false

        fun newInstance(collectionId: String?): IndividualPickerFragment2 {
            val args = Bundle()
            args.putString(ARG_CATEGORY_COLLECTION_ID, collectionId)
            val fragment = IndividualPickerFragment2()
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var imageGrid: RecyclerView
    private var adapter: IndividualAdapter? = null
    private var category: WallpaperCategory? = null
    private var wallpaperRotationInitializer: WallpaperRotationInitializer? = null
    private lateinit var items: MutableList<PickerItem>
    private var packageStatusNotifier: PackageStatusNotifier? = null
    private var isWallpapersReceived = false

    private var appStatusListener: PackageStatusNotifier.Listener? = null
    private var progressDialog: ProgressDialog? = null

    private var loading: ContentLoadingProgressBar? = null
    private var shouldReloadWallpapers = false
    private lateinit var categoryProvider: CategoryProvider
    private var appliedWallpaperIds: Set<String> = setOf()
    private var mIsCreativeWallpaperEnabled = false

    /**
     * Staged error dialog fragments that were unable to be shown when the activity didn't allow
     * committing fragment transactions.
     */
    private var stagedStartRotationErrorDialogFragment: StartRotationErrorDialogFragment? = null

    private var wallpaperManager: WallpaperManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val injector = InjectorProvider.getInjector()
        val appContext = requireContext().applicationContext
        mIsCreativeWallpaperEnabled = injector.getFlags().isAIWallpaperEnabled(appContext)
        wallpaperManager = WallpaperManager.getInstance(appContext)
        packageStatusNotifier = injector.getPackageStatusNotifier(appContext)
        items = ArrayList()

        // Clear Glide's cache if night-mode changed to ensure thumbnails are reloaded
        if (
            savedInstanceState != null &&
                (savedInstanceState.getInt(KEY_NIGHT_MODE) !=
                    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        ) {
            Glide.get(requireContext()).clearMemory()
        }
        categoryProvider = injector.getCategoryProvider(appContext)
        categoryProvider.fetchCategories(
            object : CategoryReceiver {
                override fun onCategoryReceived(category: Category) {
                    // Do nothing.
                }

                override fun doneFetchingCategories() {
                    val fetchedCategory =
                        categoryProvider.getCategory(
                            arguments?.getString(ARG_CATEGORY_COLLECTION_ID)
                        )
                    if (fetchedCategory != null && fetchedCategory !is WallpaperCategory) {
                        return
                    }

                    if (fetchedCategory == null) {
                        // The absence of this category in the CategoryProvider indicates a broken
                        // state, see b/38030129. Hence, finish the activity and return.
                        getIndividualPickerFragmentHost().moveToPreviousFragment()
                        Toast.makeText(
                                context,
                                R.string.collection_not_exist_msg,
                                Toast.LENGTH_SHORT
                            )
                            .show()
                        return
                    }
                    category = fetchedCategory as WallpaperCategory
                    category?.let { onCategoryLoaded(it) }
                }
            },
            false
        )
    }

    fun onCategoryLoaded(category: Category) {
        val fragmentHost = getIndividualPickerFragmentHost()
        if (fragmentHost.isHostToolbarShown) {
            fragmentHost.setToolbarTitle(category.title)
        } else {
            setTitle(category.title)
        }
        wallpaperRotationInitializer = category.wallpaperRotationInitializer
        if (mToolbar != null && isRotationEnabled()) {
            setUpToolbarMenu(R.menu.individual_picker_menu)
        }
        var shouldForceReload = false
        if (category.supportsThirdParty()) {
            shouldForceReload = true
        }
        fetchWallpapers(shouldForceReload)
        registerPackageListener(category)
    }

    private fun fetchWallpapers(forceReload: Boolean) {
        isCreativeCategory = false
        items.clear()
        isWallpapersReceived = false
        updateLoading()
        val context = requireContext()
        val userCreatedWallpapers = mutableListOf<WallpaperInfo>()

        category?.fetchWallpapers(
            context.applicationContext,
            { fetchedWallpapers ->
                if (getContext() == null) {
                    Log.w(TAG, "Null context!!")
                    return@fetchWallpapers
                }
                isWallpapersReceived = true
                updateLoading()
                val supportsUserCreated = category?.supportsUserCreatedWallpapers() == true
                val byGroup = fetchedWallpapers.groupBy { it.getGroupName(context) }.toMutableMap()
                val appliedWallpaperIds =
                    getAppliedWallpaperIds().also { this.appliedWallpaperIds = it }
                val firstEntry = byGroup.keys.firstOrNull()
                val currentHomeWallpaper: android.app.WallpaperInfo? =
                    WallpaperManager.getInstance(context).getWallpaperInfo(FLAG_SYSTEM)
                val currentLockWallpaper: android.app.WallpaperInfo? =
                    WallpaperManager.getInstance(context).getWallpaperInfo(FLAG_LOCK)

                // Handle first group (templates/items that allow to create a new wallpaper)
                if (mIsCreativeWallpaperEnabled && firstEntry != null && supportsUserCreated) {
                    val wallpapers = byGroup.getValue(firstEntry)
                    isCreativeCategory = true

                    if (wallpapers.size > 1 && !TextUtils.isEmpty(firstEntry)) {
                        addItemHeader(firstEntry, items.isEmpty())
                        addTemplates(wallpapers, userCreatedWallpapers)
                        byGroup.remove(firstEntry)
                    }
                }

                // Handle other groups
                if (byGroup.isNotEmpty()) {
                    byGroup.forEach { (groupName, wallpapers) ->
                        if (!TextUtils.isEmpty(groupName)) {
                            addItemHeader(groupName, items.isEmpty())
                        }
                        addWallpaperItems(
                            wallpapers,
                            currentHomeWallpaper,
                            currentLockWallpaper,
                            appliedWallpaperIds
                        )
                    }
                }
                maybeSetUpImageGrid()
                adapter?.notifyDataSetChanged()

                // Finish activity if no wallpapers are found (on phone)
                if (fetchedWallpapers.isEmpty()) {
                    activity?.finish()
                }
            },
            forceReload
        )
    }

    // Add item header based on whether it's the first one or not
    private fun addItemHeader(groupName: String, isFirst: Boolean) {
        items.add(
            if (isFirst) {
                PickerItem.FirstHeaderItem(groupName)
            } else {
                PickerItem.HeaderItem(groupName)
            }
        )
    }

    /**
     * This function iterates through a set of templates, which represent items that users can
     * select to create new wallpapers. For each template, it creates a PickerItem of type
     * CreativeCollection.
     */
    private fun addTemplates(
        wallpapers: List<WallpaperInfo>,
        userCreatedWallpapers: MutableList<WallpaperInfo>
    ) {
        wallpapers.map {
            if (category?.supportsUserCreatedWallpapers() == true) {
                userCreatedWallpapers.add(it)
            }
        }

        if (userCreatedWallpapers.isNotEmpty()) {
            items.add(PickerItem.CreativeCollection(userCreatedWallpapers))
        }
    }

    /**
     * This function iterates through a set of wallpaper items, and creates a PickerItem of type
     * WallpaperItem
     */
    private fun addWallpaperItems(
        wallpapers: List<WallpaperInfo>,
        currentHomeWallpaper: android.app.WallpaperInfo?,
        currentLockWallpaper: android.app.WallpaperInfo?,
        appliedWallpaperIds: Set<String>,
    ) {
        items.addAll(
            wallpapers.map {
                val isApplied =
                    if (it is LiveWallpaperInfo)
                        (it.isApplied(currentHomeWallpaper, currentLockWallpaper))
                    else appliedWallpaperIds.contains(it.wallpaperId)
                PickerItem.WallpaperItem(it, isApplied)
            }
        )
    }

    private fun registerPackageListener(category: Category) {
        if (category.supportsThirdParty()) {
            appStatusListener =
                PackageStatusNotifier.Listener { pkgName: String?, status: Int ->
                    if (
                        status != PackageStatusNotifier.PackageStatus.REMOVED ||
                            category.containsThirdParty(pkgName)
                    ) {
                        fetchWallpapers(true)
                    }
                }
            packageStatusNotifier?.addListener(
                appStatusListener,
                WallpaperService.SERVICE_INTERFACE
            )
        }
    }

    private fun updateLoading() {
        if (isWallpapersReceived) {
            loading?.hide()
        } else {
            loading?.show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(
            KEY_NIGHT_MODE,
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_individual_picker, container, false)
        if (getIndividualPickerFragmentHost().isHostToolbarShown) {
            view.requireViewById<View>(R.id.header_bar).visibility = View.GONE
            setUpArrowEnabled(/* upArrow= */ true)
            if (isRotationEnabled()) {
                getIndividualPickerFragmentHost().setToolbarMenu(R.menu.individual_picker_menu)
            }
        } else {
            setUpToolbar(view)
            if (isRotationEnabled()) {
                setUpToolbarMenu(R.menu.individual_picker_menu)
            }
            setTitle(category?.title)
        }
        imageGrid = view.requireViewById<View>(R.id.wallpaper_grid) as RecyclerView
        loading = view.requireViewById(R.id.loading_indicator)
        updateLoading()
        maybeSetUpImageGrid()
        // For nav bar edge-to-edge effect.
        imageGrid.setOnApplyWindowInsetsListener { v: View, windowInsets: WindowInsets ->
            v.setPadding(
                v.paddingLeft,
                v.paddingTop,
                v.paddingRight,
                windowInsets.systemWindowInsetBottom
            )
            windowInsets.consumeSystemWindowInsets()
        }
        return view
    }

    private fun getIndividualPickerFragmentHost():
        IndividualPickerFragment.IndividualPickerFragmentHost {
        val parentFragment = parentFragment
        return if (parentFragment != null) {
            parentFragment as IndividualPickerFragment.IndividualPickerFragmentHost
        } else {
            activity as IndividualPickerFragment.IndividualPickerFragmentHost
        }
    }

    private fun maybeSetUpImageGrid() {
        // Skip if mImageGrid been initialized yet
        if (!this::imageGrid.isInitialized) {
            return
        }
        // Skip if category hasn't loaded yet
        if (category == null) {
            return
        }
        if (context == null) {
            return
        }

        // Wallpaper count could change, so we may need to change the layout(2 or 3 columns layout)
        val gridLayoutManager = imageGrid.layoutManager as GridLayoutManager?
        val needUpdateLayout = gridLayoutManager?.spanCount != getNumColumns()

        // Skip if the adapter was already created and don't need to change the layout
        if (adapter != null && !needUpdateLayout) {
            return
        }

        // Clear the old decoration
        val decorationCount = imageGrid.itemDecorationCount
        for (i in 0 until decorationCount) {
            imageGrid.removeItemDecorationAt(i)
        }
        val edgePadding = getEdgePadding()

        if (isCreativeCategory) {
            imageGrid.addItemDecoration(
                GridPaddingDecorationCreativeCategory(
                    getGridItemPaddingHorizontal(),
                    getGridItemPaddingBottom(),
                    edgePadding
                )
            )
        } else {
            imageGrid.addItemDecoration(
                GridPaddingDecoration(getGridItemPaddingHorizontal(), getGridItemPaddingBottom())
            )
            imageGrid.setPadding(
                edgePadding,
                imageGrid.paddingTop,
                edgePadding,
                imageGrid.paddingBottom
            )
        }

        val tileSizePx =
            if (isFewerColumnLayout()) {
                SizeCalculator.getFeaturedIndividualTileSize(requireActivity())
            } else {
                SizeCalculator.getIndividualTileSize(requireActivity())
            }
        setUpImageGrid(tileSizePx, checkNotNull(category))
        imageGrid.setAccessibilityDelegateCompat(
            WallpaperPickerRecyclerViewAccessibilityDelegate(
                imageGrid,
                parentFragment as BottomSheetHost?,
                getNumColumns()
            )
        )
    }

    private fun isFewerColumnLayout(): Boolean =
        (!mIsCreativeWallpaperEnabled || category?.supportsUserCreatedWallpapers() == false) &&
            items.count { it is PickerItem.WallpaperItem } <= MAX_CAPACITY_IN_FEWER_COLUMN_LAYOUT

    private fun getGridItemPaddingHorizontal(): Int {
        return if (isFewerColumnLayout()) {
            resources.getDimensionPixelSize(
                R.dimen.grid_item_featured_individual_padding_horizontal
            )
        } else {
            resources.getDimensionPixelSize(R.dimen.grid_item_individual_padding_horizontal)
        }
    }

    private fun getGridItemPaddingBottom(): Int {
        return if (isFewerColumnLayout()) {
            resources.getDimensionPixelSize(R.dimen.grid_item_featured_individual_padding_bottom)
        } else {
            resources.getDimensionPixelSize(R.dimen.grid_item_individual_padding_bottom)
        }
    }

    private fun getEdgePadding(): Int {
        return if (isFewerColumnLayout()) {
            resources.getDimensionPixelSize(R.dimen.featured_wallpaper_grid_edge_space)
        } else {
            resources.getDimensionPixelSize(R.dimen.wallpaper_grid_edge_space)
        }
    }

    /**
     * Create the adapter and assign it to mImageGrid. Both mImageGrid and mCategory are guaranteed
     * to not be null when this method is called.
     */
    private fun setUpImageGrid(tileSizePx: Point, category: Category) {
        adapter =
            IndividualAdapter(
                items,
                category,
                requireActivity(),
                tileSizePx,
                isRotationEnabled(),
                isFewerColumnLayout(),
                getEdgePadding(),
                imageGrid.paddingTop,
                imageGrid.paddingBottom
            )
        imageGrid.adapter = adapter
        val gridLayoutManager = GridLayoutManager(activity, getNumColumns())
        gridLayoutManager.spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (position >= 0 && position < items.size) {
                        when (items[position]) {
                            is PickerItem.CreativeCollection,
                            is PickerItem.FirstHeaderItem,
                            is PickerItem.HeaderItem -> gridLayoutManager.spanCount
                            else -> 1
                        }
                    } else {
                        1
                    }
                }
            }
        imageGrid.layoutManager = gridLayoutManager
    }

    private suspend fun fetchWallpapersIfNeeded() {
        coroutineScope {
            if (isWallpapersReceived && (shouldReloadWallpapers || isAppliedWallpaperChanged())) {
                fetchWallpapers(true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val preferences = InjectorProvider.getInjector().getPreferences(requireActivity())
        preferences.setLastAppActiveTimestamp(Date().time)

        // Reset Glide memory settings to a "normal" level of usage since it may have been lowered
        // in PreviewFragment.
        Glide.get(requireContext()).setMemoryCategory(MemoryCategory.NORMAL)

        // Show the staged 'start rotation' error dialog fragment if there is one that was unable to
        // be shown earlier when this fragment's hosting activity didn't allow committing fragment
        // transactions.
        if (isAdded) {
            stagedStartRotationErrorDialogFragment?.show(
                parentFragmentManager,
                TAG_START_ROTATION_ERROR_DIALOG
            )
            lifecycleScope.launch { fetchWallpapersIfNeeded() }
        }
        stagedStartRotationErrorDialogFragment = null
    }

    override fun onPause() {
        shouldReloadWallpapers = category?.supportsWallpaperSetUpdates() ?: false
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        getIndividualPickerFragmentHost().removeToolbarMenu()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        if (appStatusListener != null) {
            packageStatusNotifier?.removeListener(appStatusListener)
        }
    }

    override fun onStartRotationDialogDismiss(dialog: DialogInterface) {
        // TODO(b/159310028): Refactor fragment layer to make it able to restore from config change.
        // This is to handle config change with StartRotationDialog popup,  the StartRotationDialog
        // still holds a reference to the destroyed Fragment and is calling
        // onStartRotationDialogDismissed on that destroyed Fragment.
    }

    override fun retryStartRotation(@NetworkPreference networkPreference: Int) {
        startRotation(networkPreference)
    }

    override fun startRotation(@NetworkPreference networkPreference: Int) {
        if (!isRotationEnabled()) {
            Log.e(TAG, "Rotation is not enabled for this category " + category?.title)
            return
        }

        val themeResId =
            if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
                R.style.ProgressDialogThemePreL
            } else {
                R.style.LightDialogTheme
            }
        val progressDialog = ProgressDialog(activity, themeResId)
        progressDialog.setTitle(PROGRESS_DIALOG_NO_TITLE)
        progressDialog.setMessage(resources.getString(R.string.start_rotation_progress_message))
        progressDialog.isIndeterminate = PROGRESS_DIALOG_INDETERMINATE
        progressDialog.show()
        this.progressDialog = progressDialog

        val appContext = requireActivity().applicationContext
        wallpaperRotationInitializer?.setFirstWallpaperInRotation(
            appContext,
            networkPreference,
            object : WallpaperRotationInitializer.Listener {
                override fun onFirstWallpaperInRotationSet() {
                    progressDialog?.dismiss()

                    // The fragment may be detached from its containing activity if the user exits
                    // the app before the first wallpaper image in rotation finishes downloading.
                    val activity: Activity? = activity
                    if (wallpaperRotationInitializer!!.startRotation(appContext)) {
                        if (activity != null) {
                            try {
                                Toast.makeText(
                                        activity,
                                        R.string.wallpaper_set_successfully_message,
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            } catch (e: Resources.NotFoundException) {
                                Log.e(TAG, "Could not show toast $e")
                            }
                            activity.setResult(Activity.RESULT_OK)
                            activity.finish()
                            if (!ActivityUtils.isSUWMode(appContext)) {
                                // Go back to launcher home.
                                LaunchUtils.launchHome(appContext)
                            }
                        }
                    } else { // Failed to start rotation.
                        showStartRotationErrorDialog(networkPreference)
                    }
                }

                override fun onError() {
                    progressDialog?.dismiss()
                    showStartRotationErrorDialog(networkPreference)
                }
            }
        )
    }

    private fun showStartRotationErrorDialog(@NetworkPreference networkPreference: Int) {
        val activity = activity as FragmentTransactionChecker?
        if (activity != null) {
            val startRotationErrorDialogFragment =
                StartRotationErrorDialogFragment.newInstance(networkPreference)
            startRotationErrorDialogFragment.setTargetFragment(
                this@IndividualPickerFragment2,
                UNUSED_REQUEST_CODE
            )
            if (activity.isSafeToCommitFragmentTransaction) {
                startRotationErrorDialogFragment.show(
                    parentFragmentManager,
                    TAG_START_ROTATION_ERROR_DIALOG
                )
            } else {
                stagedStartRotationErrorDialogFragment = startRotationErrorDialogFragment
            }
        }
    }

    private fun getNumColumns(): Int {
        val activity = this.activity ?: return 1
        return if (isFewerColumnLayout()) {
            SizeCalculator.getNumFeaturedIndividualColumns(activity)
        } else {
            SizeCalculator.getNumIndividualColumns(activity)
        }
    }

    /** Returns whether rotation is enabled for this category. */
    private fun isRotationEnabled() = wallpaperRotationInitializer != null

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.daily_rotation) {
            showRotationDialog()
            return true
        }
        return super.onMenuItemClick(item)
    }

    /** Popups a daily rotation dialog for the uses to confirm. */
    private fun showRotationDialog() {
        val startRotationDialogFragment: DialogFragment = StartRotationDialogFragment()
        startRotationDialogFragment.setTargetFragment(
            this@IndividualPickerFragment2,
            UNUSED_REQUEST_CODE
        )
        startRotationDialogFragment.show(parentFragmentManager, TAG_START_ROTATION_DIALOG)
    }

    private fun getAppliedWallpaperIds(): Set<String> {
        val prefs = InjectorProvider.getInjector().getPreferences(requireContext())
        val wallpaperInfo = wallpaperManager?.wallpaperInfo
        val appliedWallpaperIds: MutableSet<String> = ArraySet()
        val homeWallpaperId =
            if (wallpaperInfo != null) {
                wallpaperInfo.serviceName
            } else {
                prefs.getHomeWallpaperRemoteId()
            }
        if (!homeWallpaperId.isNullOrEmpty()) {
            appliedWallpaperIds.add(homeWallpaperId)
        }
        val isLockWallpaperApplied =
            wallpaperManager!!.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0
        val lockWallpaperId = prefs.getLockWallpaperRemoteId()
        if (isLockWallpaperApplied && !lockWallpaperId.isNullOrEmpty()) {
            appliedWallpaperIds.add(lockWallpaperId)
        }
        return appliedWallpaperIds
    }

    // TODO(b/277180178): Extract the check to another class for unit testing
    private fun isAppliedWallpaperChanged(): Boolean {
        // Reload wallpapers if the current wallpapers have changed
        getAppliedWallpaperIds().let {
            if (appliedWallpaperIds != it) {
                return true
            }
        }
        return false
    }

    sealed class PickerItem(val title: CharSequence = "") {
        class WallpaperItem(val wallpaperInfo: WallpaperInfo, val isApplied: Boolean) :
            PickerItem()

        class HeaderItem(title: CharSequence) : PickerItem(title)

        class FirstHeaderItem(title: CharSequence) : PickerItem(title)

        class CreativeCollection(val templates: List<WallpaperInfo>) : PickerItem()
    }

    /** RecyclerView Adapter subclass for the wallpaper tiles in the RecyclerView. */
    class IndividualAdapter(
        private val items: List<PickerItem>,
        private val category: Category,
        private val activity: Activity,
        private val tileSizePx: Point,
        private val isRotationEnabled: Boolean,
        private val isFewerColumnLayout: Boolean,
        private val edgePadding: Int,
        private val bottomPadding: Int,
        private val topPadding: Int
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        companion object {
            const val ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER = 2
            const val ITEM_VIEW_TYPE_MY_PHOTOS = 3
            const val ITEM_VIEW_TYPE_HEADER = 4
            const val ITEM_VIEW_TYPE_HEADER_TOP = 5
            const val ITEM_VIEW_TYPE_CREATIVE = 6
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER -> createIndividualHolder(parent)
                ITEM_VIEW_TYPE_MY_PHOTOS -> createMyPhotosHolder(parent)
                ITEM_VIEW_TYPE_CREATIVE -> creativeCategoryHolder(parent)
                ITEM_VIEW_TYPE_HEADER -> createTitleHolder(parent, /* removePaddingTop= */ false)
                ITEM_VIEW_TYPE_HEADER_TOP -> createTitleHolder(parent, /* removePaddingTop= */ true)
                else -> {
                    throw RuntimeException("Unsupported viewType $viewType in IndividualAdapter")
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            // A category cannot have both a "start rotation" tile and a "my photos" tile.
            return if (
                category.supportsCustomPhotos() &&
                    !isRotationEnabled &&
                    position == SPECIAL_FIXED_TILE_ADAPTER_POSITION
            ) {
                ITEM_VIEW_TYPE_MY_PHOTOS
            } else {
                when (items[position]) {
                    is PickerItem.WallpaperItem -> ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER
                    is PickerItem.HeaderItem -> ITEM_VIEW_TYPE_HEADER
                    is PickerItem.FirstHeaderItem -> ITEM_VIEW_TYPE_HEADER_TOP
                    is PickerItem.CreativeCollection -> ITEM_VIEW_TYPE_CREATIVE
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val viewType = getItemViewType(position)) {
                ITEM_VIEW_TYPE_CREATIVE -> bindCreativeCategoryHolder(holder, position)
                ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER -> bindIndividualHolder(holder, position)
                ITEM_VIEW_TYPE_MY_PHOTOS -> (holder as MyPhotosViewHolder?)!!.bind()
                ITEM_VIEW_TYPE_HEADER,
                ITEM_VIEW_TYPE_HEADER_TOP -> {
                    val textView = holder.itemView as TextView
                    val item = items[position]
                    textView.text = item.title
                    textView.contentDescription = item.title
                }
                else -> Log.e(TAG, "Unexpected viewType $viewType in IndividualAdapter")
            }
        }

        override fun getItemCount(): Int {
            return if (category.supportsCustomPhotos()) {
                items.size + 1
            } else {
                items.size
            }
        }

        private fun createIndividualHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(activity)
            val view: View = layoutInflater.inflate(R.layout.grid_item_image, parent, false)
            return PreviewIndividualHolder(activity, tileSizePx.y, view)
        }

        private fun creativeCategoryHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(activity)
            val view: View =
                layoutInflater.inflate(R.layout.creative_category_holder, parent, false)
            if (isCreativeCategory) {
                view.setPadding(edgePadding, topPadding, edgePadding, bottomPadding)
            }
            return CreativeCategoryHolder(
                activity,
                view,
            )
        }

        private fun createMyPhotosHolder(parent: ViewGroup): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(activity)
            val view: View = layoutInflater.inflate(R.layout.grid_item_my_photos, parent, false)
            return MyPhotosViewHolder(
                activity,
                (activity as MyPhotosStarterProvider).myPhotosStarter,
                tileSizePx.y,
                view
            )
        }

        private fun bindCreativeCategoryHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val wallpaperIndex = if (category.supportsCustomPhotos()) position - 1 else position
            val item = items[wallpaperIndex] as PickerItem.CreativeCollection
            (holder as CreativeCategoryHolder).bind(
                item.templates,
                SizeCalculator.getFeaturedIndividualTileSize(activity).y
            )
        }

        private fun createTitleHolder(
            parent: ViewGroup,
            removePaddingTop: Boolean
        ): RecyclerView.ViewHolder {
            val layoutInflater = LayoutInflater.from(activity)
            val view =
                layoutInflater.inflate(R.layout.grid_item_header, parent, /* attachToRoot= */ false)
            var startPadding = view.paddingStart
            if (isCreativeCategory) {
                startPadding += edgePadding
            }
            if (removePaddingTop) {
                view.setPaddingRelative(
                    startPadding,
                    /* top= */ 0,
                    view.paddingEnd,
                    view.paddingBottom
                )
            } else {
                view.setPaddingRelative(
                    startPadding,
                    view.paddingTop,
                    view.paddingEnd,
                    view.paddingBottom
                )
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        private fun bindIndividualHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val wallpaperIndex = if (category.supportsCustomPhotos()) position - 1 else position
            val item = items[wallpaperIndex] as PickerItem.WallpaperItem
            val wallpaper = item.wallpaperInfo
            wallpaper.computeColorInfo(holder.itemView.context)
            (holder as IndividualHolder).bindWallpaper(wallpaper)
            val container = holder.itemView.requireViewById<CardView>(R.id.wallpaper_container)
            val radiusId: Int =
                if (isFewerColumnLayout) {
                    R.dimen.grid_item_all_radius
                } else {
                    R.dimen.grid_item_all_radius_small
                }
            container.radius = activity.resources.getDimension(radiusId)
            showBadge(holder, R.drawable.wallpaper_check_circle_24dp, item.isApplied)
            if (!item.isApplied) {
                showBadge(holder, wallpaper.badgeDrawableRes, wallpaper.badgeDrawableRes != ID_NULL)
            }
        }

        private fun showBadge(
            holder: RecyclerView.ViewHolder,
            @DrawableRes icon: Int,
            show: Boolean
        ) {
            val badge = holder.itemView.requireViewById<ImageView>(R.id.indicator_icon)
            if (show) {
                val margin =
                    if (isFewerColumnLayout) {
                            activity.resources.getDimension(R.dimen.grid_item_badge_margin)
                        } else {
                            activity.resources.getDimension(R.dimen.grid_item_badge_margin_small)
                        }
                        .toInt()
                val layoutParams = badge.layoutParams as RelativeLayout.LayoutParams
                layoutParams.setMargins(margin, margin, margin, margin)
                badge.layoutParams = layoutParams
                badge.setBackgroundResource(icon)
                badge.visibility = View.VISIBLE
            } else {
                badge.visibility = View.GONE
            }
        }
    }

    override fun getToolbarColorId(): Int {
        return android.R.color.transparent
    }

    override fun getToolbarTextColor(): Int {
        return ContextCompat.getColor(requireContext(), R.color.system_on_surface)
    }
}
