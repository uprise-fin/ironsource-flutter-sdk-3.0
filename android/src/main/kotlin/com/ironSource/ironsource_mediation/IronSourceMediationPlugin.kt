package com.ironSource.ironsource_mediation

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.ironsource.adapters.supersonicads.SupersonicConfig
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.ISContainerParams
import com.ironsource.mediationsdk.IronSource
import com.ironsource.mediationsdk.IronSourceBannerLayout
import com.ironsource.mediationsdk.IronSourceSegment
import com.ironsource.mediationsdk.WaterfallConfiguration
import com.ironsource.mediationsdk.config.ConfigFile
import com.ironsource.mediationsdk.integration.IntegrationHelper
import com.ironsource.mediationsdk.model.Placement
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdSize
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.platform.PlatformViewFactory
import java.util.concurrent.Executors
import kotlin.math.abs

/** IronSourceMediationPlugin */
class IronSourceMediationPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, LifecycleObserver {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private var channel: MethodChannel? = null
  private var activity: Activity? = null
  private var context: Context? = null

  // Banner related
  private var mBannerContainer: FrameLayout? = null
  private var mBanner: IronSourceBannerLayout? = null
  private var mBannerVisibility: Int = View.VISIBLE

  // Listeners
  private var mImpressionDataListener: ImpressionDataListener? = null
  private var mInitializationListener: InitializationListener? = null

  // LevelPlay Listeners
  private var mLevelPlayInitListener: LevelPlayInitListener? = null
  private var mLevelPlayRewardedVideoListener: LevelPlayRewardedVideoListener? = null
  private var mLevelPlayInterstitialListener: LevelPlayInterstitialListener? = null
  private var mLevelPlayBannerListener: LevelPlayBannerListener? = null

  // LevelPlay Native Ad
  private var nativeAdViewFactories = hashMapOf<String, PlatformViewFactory>()
  private var pluginBinding: FlutterPluginBinding? = null

  // LevelPlay Ad Object Manager
  private lateinit var levelPlayAdObjectManager: LevelPlayAdObjectManager

  override fun onAttachedToEngine(flutterPluginBinding: FlutterPluginBinding) {
    channel = MethodChannel(flutterPluginBinding.binaryMessenger, "ironsource_mediation")
    context = flutterPluginBinding.applicationContext
    pluginBinding = flutterPluginBinding

    channel?.setMethodCallHandler(this)
    initListeners()

    // Banner ad view registry
    val bannerAdViewFactory = LevelPlayBannerAdViewFactory(pluginBinding!!.binaryMessenger)
    pluginBinding
      ?.platformViewRegistry
      ?.registerViewFactory("levelPlayBannerAdView", bannerAdViewFactory)

    // Native ad view registry
    val nativeAdViewFactory = LevelPlayNativeAdViewFactoryTemplate(pluginBinding!!.binaryMessenger)
    addNativeAdViewFactory("levelPlayNativeAdView", nativeAdViewFactory)

    // Ad object manager registry
    levelPlayAdObjectManager = LevelPlayAdObjectManager(activity, channel!!)
  }

  override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
    channel?.setMethodCallHandler(null)
    channel = null
    detachListeners()
  }

  /**
   * Instantiate and set listeners
   */
  private fun initListeners() {
    channel?.let { channel ->
      // ImpressionData Listener
      if (mImpressionDataListener == null) {
        mImpressionDataListener = ImpressionDataListener(channel)
        IronSource.addImpressionDataListener(mImpressionDataListener!!)
      }
      // Initialization Listener
      if (mInitializationListener == null) {
        mInitializationListener = InitializationListener(channel)
      }
      // LevelPlay Init Listener
      if (mLevelPlayInitListener == null) {
        mLevelPlayInitListener = LevelPlayInitListener(channel)
      }
      // LevelPlay RewardedVideo
      if (mLevelPlayRewardedVideoListener == null) {
        mLevelPlayRewardedVideoListener = LevelPlayRewardedVideoListener(channel)
        IronSource.setLevelPlayRewardedVideoListener(mLevelPlayRewardedVideoListener)
      }
      // LevelPlay Interstitial
      if (mLevelPlayInterstitialListener == null) {
        mLevelPlayInterstitialListener = LevelPlayInterstitialListener(channel)
        IronSource.setLevelPlayInterstitialListener(mLevelPlayInterstitialListener)
      }
      // LevelPlay Banner
      if (mLevelPlayBannerListener == null) {
        mLevelPlayBannerListener = LevelPlayBannerListener(channel)
      }
    }
  }

  /**
   * Listener Reference Clean Up
   */
  private fun detachListeners() {
    // ILR
    mImpressionDataListener?.let { IronSource.removeImpressionDataListener(it) }
    mImpressionDataListener = null
    // Init
    mInitializationListener = null
    // LevelPlay Init
    mLevelPlayInitListener = null
    // LevelPlay RewardedVideo
    mLevelPlayRewardedVideoListener = null
    // LevelPlay Interstitial
    mLevelPlayInterstitialListener = null

    IronSource.setLevelPlayRewardedVideoListener(null)
  }

  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      /** Base API ===============================================================================*/
      "validateIntegration" -> validateIntegration(result)
      "shouldTrackNetworkState" -> shouldTrackNetworkState(call, result)
      "setAdaptersDebug" -> setAdaptersDebug(call, result)
      "setDynamicUserId" -> setDynamicUserId(call, result)
      "getAdvertiserId" -> getAdvertiserId(result)
      "setConsent" -> setConsent(call, result)
      "setSegment" -> setSegment(call, result)
      "setMetaData" -> setMetaData(call, result)
      "setWaterfallConfiguration" -> setWaterfallConfiguration(call, result)
      /**Test Suite API ==========================================================================*/
      "launchTestSuite" -> launchTestSuite(result)
      /** Init API ===============================================================================*/
      "setUserId" -> setUserId(call, result)
      "init" -> initIronSource(call, result)
      /** RewardedVideo API =================================================================================*/
      "showRewardedVideo" -> showRewardedVideo(call, result)
      "getRewardedVideoPlacementInfo" -> getRewardedVideoPlacementInfo(call, result)
      "isRewardedVideoAvailable" -> isRewardedVideoAvailable(result)
      "isRewardedVideoPlacementCapped" -> isRewardedVideoPlacementCapped(call, result)
      "setRewardedVideoServerParams" -> setRewardedVideoServerParams(call, result)
      "clearRewardedVideoServerParams" -> clearRewardedVideoServerParams(result)
      "loadRewardedVideo" -> loadRewardedVideo(result)
      "setLevelPlayRewardedVideoManual" -> setLevelPlayRewardedVideoManual(result)
      /** Interstitial API =================================================================================*/
      "loadInterstitial" -> loadInterstitial(result)
      "showInterstitial" -> showInterstitial(call, result)
      "isInterstitialReady" -> isInterstitialReady(result)
      "isInterstitialPlacementCapped" -> isInterstitialPlacementCapped(call, result)
      /** Banner API =================================================================================*/
      "loadBanner" -> loadBanner(call, result)
      "destroyBanner" -> destroyBanner(result)
      "displayBanner" -> displayBanner(result)
      "hideBanner" -> hideBanner(result)
      "isBannerPlacementCapped" -> isBannerPlacementCapped(call, result)
      "getMaximalAdaptiveHeight" -> getMaximalAdaptiveHeight(call, result)
      /** Config API =============================================================================*/
      "setClientSideCallbacks" -> setClientSideCallbacks(call, result)
      /** Internal Config API ====================================================================*/
      "setPluginData" -> setPluginData(call, result)

      /** LevelPlay Init API ===============================================================================*/
      "initLevelPlay" -> initLevelPlay(call, result)
      /** LevelPlayInterstitialAd API ===============================================================================*/
      "isInterstitialAdPlacementCapped" -> isInterstitialAdPlacementCapped(call, result)
      "createInterstitialAd" -> createInterstitialAd(call, result) // function that assist to create interstital ad object and retrieve the adId
      "loadInterstitialAd" -> loadInterstitialAd(call, result)
      "showInterstitialAd" -> showInterstitialAd(call, result)
      "isInterstitialAdReady" -> isInterstitialAdReady(call, result)
      "disposeAd" -> disposeAd(call, result)
      "disposeAllAds" -> disposeAllAds(result)
      /** LevelPlayAdSize API ===============================================================================*/
      "createAdaptiveAdSize" -> createAdaptiveAdSize(call, result)
      /** LevelPlayRewardedAd API ===============================================================================*/
      "isRewardedAdPlacementCapped" -> isRewardedAdPlacementCapped(call, result)
      "createRewardedAd" -> createRewardedAd(call, result) // function that assist to create a rewarded ad object and retrieve the adId
      "loadRewardedAd" -> loadRewardedAd(call, result)
      "showRewardedAd" -> showRewardedAd(call, result)
      "isRewardedAdReady" -> isRewardedAdReady(call, result)
      else -> result.notImplemented()
    }
  }

  /** region Base API ============================================================================*/

  /**
   * Validates the integration of the SDK.
   *
   * @param result The result to be returned after validating the integration.
   */
  private fun validateIntegration(result: Result) {
    activity?.apply {
      IntegrationHelper.validateIntegration(this)
      return result.success(null)
    } ?: return result.error("ERROR", "Activity is null", null)
  }

  /**
   * Sets whether to track network state for IronSource SDK.
   *
   * @param call The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun shouldTrackNetworkState(call: MethodCall, result: Result) {
    if (activity == null) {
      return result.error("ERROR", "Activity is null", null)
    }
    val isEnabled = call.argument("isEnabled") as Boolean?
      ?: return result.error("ERROR", "isEnabled is null", null)
    IronSource.shouldTrackNetworkState(activity, isEnabled)
    return result.success(null)
  }

  /**
   * Sets whether to enable debug mode for IronSource SDK adapters.
   *
   * @param call The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun setAdaptersDebug(call: MethodCall, result: Result) {
    val isEnabled = call.argument("isEnabled") as Boolean?
      ?: return result.error("ERROR", "isEnabled is null", null)
    IronSource.setAdaptersDebug(isEnabled)
    return result.success(null)
  }

  /**
   * Sets the dynamic user ID for IronSource SDK.
   *
   * @param call The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun setDynamicUserId(call: MethodCall, result: Result) {
    val userId = call.argument("userId") as String?
      ?: return result.error("ERROR", "userId is null", null)

    IronSource.setDynamicUserId(userId)
    return result.success(null)
  }

  /**
   * Retrieves the advertiser ID asynchronously.
   *
   * @param result The result to be returned after processing.
   */
  private fun getAdvertiserId(result: Result) {
    activity?.apply {
      val executer = Executors.newSingleThreadExecutor()
      executer.execute {
        // this API MUST be called on a background thread
        val idStr = IronSource.getAdvertiserId(this)
        runOnUiThread { result.success(idStr) }
      }
    } ?: return result.error("ERROR", "getAdvertiserId called when activity is null", null)
  }

  /**
   * Sets the consent status for the user.
   *
   * @param call The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun setConsent(call: MethodCall, result: Result) {
    val isConsent = call.argument("isConsent") as Boolean?
      ?: return result.error("ERROR", "isConsent is null", null)
    IronSource.setConsent(isConsent)
    return result.success(null)
  }

  /**
   * Sets the segment for the user.
   *
   * @param call The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun setSegment(call: MethodCall, result: Result) {
    val segmentMap = call.argument("segment") as HashMap<String, Any?>?
      ?: return result.error("ERROR", "segment is null", null)
    val iSSegment = IronSourceSegment()
    segmentMap.entries.forEach { entry ->
      when (entry.key) {
        // Dart int is 64bits, so if the value is over 32bits it is parsed into Long else Int
        // Therefore, the number fields must be safely cast
        "segmentName" -> entry.value?.let { iSSegment.segmentName = it as String }
        "age" -> entry.value?.let { iSSegment.age = if (it is Int) it else (it as Long).toInt() }
        "gender" -> entry.value?.let { iSSegment.gender = it as String }
        "level" -> entry.value?.let {
          iSSegment.level = if (it is Int) it else (it as Long).toInt()
        }

        "isPaying" -> entry.value?.let { iSSegment.setIsPaying(it as Boolean) }
        "userCreationDate" -> entry.value?.let { iSSegment.setUserCreationDate(if (it is Long) it else (it as Int).toLong()) }
        "iapTotal" -> entry.value?.let { iSSegment.setIAPTotal(it as Double) }
        else -> entry.value?.let { iSSegment.setCustom(entry.key, it as String) }
      }
    }
    IronSource.setSegment(iSSegment)
    return result.success(null)
  }

  /**
   * Sets meta data for IronSource.
   *
   * @param call The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun setMetaData(call: MethodCall, result: Result) {
    val metaDataMap = call.argument("metaData") as HashMap<String, List<String>>?
      ?: return result.error("ERROR", "metaData is null", null)
    // internally overload function uses setMetaData(key: String, values:List<String>) after all
    metaDataMap.entries.forEach { entry: Map.Entry<String, List<String>> ->
      IronSource.setMetaData(
        entry.key,
        entry.value
      )
    }
    return result.success(null)
  }

  /**
   * Launches the IronSource test suite.
   *
   * @param result The result to be returned after processing.
   */
  private fun launchTestSuite(result: Result) {
    context?.let { IronSource.launchTestSuite(it) }
    return result.success(null)
  }

  /**
   * Sets the waterfall configuration for an ad unit.
   *
   * @param call The method call containing the waterfall configuration data.
   * @param result The result to be returned after setting the waterfall configuration.
   */
  private fun setWaterfallConfiguration(call: MethodCall, result: Result) {
    // Retrieve the waterfall configuration data map from the method call arguments
    val waterfallConfigurationDataMap =
      call.argument("waterfallConfiguration") as HashMap<String, Any>?
        ?: return result.error("ERROR", "waterfallConfiguration is null", null)

    // Retrieve the ceiling and floor values from the waterfall configuration data map
    val ceiling = waterfallConfigurationDataMap["ceiling"] as Double?
    val floor = waterfallConfigurationDataMap["floor"] as Double?

    // Retrieve the ad unit from the waterfall configuration data map
    val adUnit = LevelPlayUtils.getAdUnit(waterfallConfigurationDataMap["adUnit"] as String?)

    // Check if the ad unit is not null
    if (adUnit != null) {
      // If both ceiling and floor values are provided, set the waterfall configuration
      if (ceiling != null && floor != null) {
        IronSource.setWaterfallConfiguration(
          WaterfallConfiguration.builder()
            .setFloor(floor)
            .setCeiling(ceiling)
            .build(),
          adUnit
        )
      }
    }
    // Return success after setting the waterfall configuration
    return result.success(null)
  }
  // endregion

  /** region Init API ============================================================================*/

  /**
   * Sets the user ID for IronSource.
   *
   * @param call The method call containing the user ID as an argument.
   * @param result The result to be returned after processing.
   */
  private fun setUserId(call: MethodCall, result: Result) {
    val userId = call.argument("userId") as String?
      ?: return result.error("ERROR", "userId is null", null)

    IronSource.setUserId(userId)
    return result.success(null)
  }

  /**
   * Initializes IronSource SDK with the provided app key and ad units.
   *
   * @param call The method call containing the app key and ad units as arguments.
   * @param result The result to be returned after processing.
   */
  private fun initIronSource(call: MethodCall, result: Result) {
    if (activity == null) {
      return result.error("ERROR", "Activity is null", null)
    }
    val appKey = call.argument("appKey") as String?
      ?: return result.error("ERROR", "appKey is null", null)
    val adUnits = call.argument("adUnits") as List<String>?

    if (adUnits == null) {
      IronSource.init(activity, appKey, mInitializationListener)
    } else {
      val parsed = adUnits.map {
        when (it) {
          "REWARDED_VIDEO" -> IronSource.AD_UNIT.REWARDED_VIDEO
          "INTERSTITIAL" -> IronSource.AD_UNIT.INTERSTITIAL
          "BANNER" -> IronSource.AD_UNIT.BANNER
          "NATIVE_AD" -> IronSource.AD_UNIT.NATIVE_AD
          else -> return@initIronSource result.error("ERROR", "Unsupported ad unit: $it", null)
        }
      }.toTypedArray()
      IronSource.init(activity, appKey, mInitializationListener, *parsed)
    }

    return result.success(null)
  }

  // endregion

  /** region RewardedVideo API ==============================================================================*/

  /**
   * Shows a rewarded video.
   *
   * @param call The method call containing the placement name as an argument.
   * @param result The result to be returned after processing.
   */
  private fun showRewardedVideo(call: MethodCall, result: Result) {
    activity?.apply {
      // Retrieve placement name from method call arguments
      val placementName = call.argument("placementName") as String?
      // Show rewarded video with or without placement name based on its presence
      placementName?.let { name -> IronSource.showRewardedVideo(name) }
        ?: IronSource.showRewardedVideo()

      // Return success
      return result.success(null)
    } ?: return result.error("ERROR", "showRewardedVideo called when activity is null", null)
  }

  /**
   * Retrieves information about a rewarded video placement.
   *
   * @param call The method call containing the placement name as an argument.
   * @param result The result to be returned after processing.
   */
  private fun getRewardedVideoPlacementInfo(call: MethodCall, result: Result) {
    // Retrieve placement name from method call arguments
    val placementName = call.argument("placementName") as String?
      ?: return result.error("ERROR", "placementName is null", null)
    // Get placement information from IronSource SDK
    val placement: Placement? = IronSource.getRewardedVideoPlacementInfo(placementName)
    // Return placement information as a map or null
    return result.success(placement?.toMap())
  }

  /**
   * Checks if a rewarded video is available for display.
   *
   * @param result The result to be returned after processing.
   */
  private fun isRewardedVideoAvailable(result: Result) {
    // Check if rewarded video is available and return the result
    return result.success(IronSource.isRewardedVideoAvailable())
  }

  /**
   * Checks if a rewarded video placement is capped.
   *
   * @param call The method call containing the placement name as an argument.
   * @param result The result to be returned after processing.
   */
  private fun isRewardedVideoPlacementCapped(call: MethodCall, result: Result) {
    // Retrieve placement name from method call arguments
    val placementName = call.argument("placementName") as String?
      ?: return result.error("ERROR", "placementName is null", null)
    // Check if rewarded video placement is capped and return the result
    val isCapped = IronSource.isRewardedVideoPlacementCapped(placementName)
    return result.success(isCapped)
  }

  /**
   * Sets server parameters for rewarded video.
   *
   * @param call The method call containing the parameters as a hashmap.
   * @param result The result to be returned after processing.
   */
  private fun setRewardedVideoServerParams(call: MethodCall, result: Result) {
    // Retrieve parameters from method call arguments
    val parameters = call.argument("parameters") as HashMap<String, String>?
      ?: return result.error("ERROR", "parameters is null", null)
    // Set rewarded video server parameters
    IronSource.setRewardedVideoServerParameters(parameters)
    // Return success
    return result.success(null)
  }

  /**
   * Clears server parameters for rewarded video.
   *
   * @param result The result to be returned after processing.
   */
  private fun clearRewardedVideoServerParams(result: Result) {
    // Clear rewarded video server parameters
    IronSource.clearRewardedVideoServerParameters()
    // Return success
    return result.success(null)
  }

  /**
   * Sets up manual loading for Rewarded Video.
   * This method must be called before initialization.
   *
   * @param result The result to be returned after processing.
   */
  private fun setLevelPlayRewardedVideoManual(result: Result) {
    // Remove the auto load LevelPlay RewardedVideo listener
    IronSource.setLevelPlayRewardedVideoListener(null)
    // Set the LevelPlay RewardedVideo manual
    IronSource.setLevelPlayRewardedVideoManualListener(mLevelPlayRewardedVideoListener)
    // Return success
    return result.success(null)
  }

  /**
   * Manually loads a Rewarded Video.
   *
   * @param result The result to be returned after processing.
   */
  private fun loadRewardedVideo(result: Result) {
    IronSource.loadRewardedVideo()
    return result.success(null)
  }
  // endregion

  /** region Interstitial API ==============================================================================*/

  /**
   * Loads an Interstitial ad.
   *
   * @param result The result to be returned after processing.
   */
  private fun loadInterstitial(result: Result) {
    IronSource.loadInterstitial()
    return result.success(null)
  }

  /**
   * Shows an Interstitial ad.
   *
   * @param call   The method call containing arguments, such as placementName.
   * @param result The result to be returned after processing.
   */
  private fun showInterstitial(call: MethodCall, result: Result) {
    activity?.apply {
      val placementName = call.argument("placementName") as String?
      placementName?.let { name -> IronSource.showInterstitial(name) }
        ?: IronSource.showInterstitial()

      return result.success(null)
    } ?: return result.error("ERROR", "showInterstitial called when activity is null", null)
  }

  /**
   * Checks if an Interstitial ad is ready to be shown.
   *
   * @param result The result to be returned after processing.
   */
  private fun isInterstitialReady(result: Result) {
    return result.success(IronSource.isInterstitialReady())
  }

  /**
   * Checks if the specified Interstitial placement is capped.
   *
   * @param call   The method call containing arguments, such as placementName.
   * @param result The result to be returned after processing.
   */
  private fun isInterstitialPlacementCapped(call: MethodCall, result: Result) {
    val placementName = call.argument("placementName") as String?
      ?: return result.error("ERROR", "placementName is null", null)
    val isCapped = IronSource.isInterstitialPlacementCapped(placementName)
    return result.success(isCapped)
  }

  // endregion

  /** region Banner API ==============================================================================*/


  /**
   * Loads a banner ad.
   *
   * @param call   The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun loadBanner(call: MethodCall, result: Result) {
    // fallback to BANNER in the case of invalid descriptions
    fun getBannerSize(description: String, width: Int, height: Int): ISBannerSize {
      return when (description) {
        "CUSTOM" -> ISBannerSize(width, height)
        "BANNER" -> ISBannerSize.BANNER
        "SMART" -> ISBannerSize.SMART
        "RECTANGLE" -> ISBannerSize.RECTANGLE
        "LARGE" -> ISBannerSize.LARGE
        else -> ISBannerSize.BANNER
      }
    }

    activity?.apply {
      // args
      // Dart int is 64bits, so if the value is over 32bits, it is parsed into Long
      // Therefore, the Int fields could be passed as Long in some cases and must be safely cast
      val description = call.argument("description") as String?
        ?: return result.error("ERROR", "description is null", null)
      val width =
        (call.argument("width") as Any?)?.let { if (it is Int) it else (it as Long).toInt() }
          ?: return result.error("ERROR", "width is null", null)
      val height =
        (call.argument("height") as Any?)?.let { if (it is Int) it else (it as Long).toInt() }
          ?: return result.error("ERROR", "height is null", null)
      val isAdaptive = call.argument("isAdaptive") as Boolean?
        ?: return result.error("ERROR", "isAdaptive is null", null)
      val containerWidth = call.argument("containerWidth") as Int?
        ?: return result.error("ERROR", "containerWidth is null", null)
      val containerHeight = call.argument("containerHeight") as Int?
        ?: return result.error("ERROR", "containerHeight is null", null)
      val position =
        (call.argument("position") as Any?)?.let { if (it is Int) it else (it as Long).toInt() }
          ?: return result.error("ERROR", "position is null", null)
      val offset =
        (call.argument("offset") as Any?)?.let { if (it is Int) it else (it as Long).toInt() }
          ?: 0
      val placementName = call.argument("placementName") as String?

      runOnUiThread {
        synchronized(this@IronSourceMediationPlugin) {
          try {
            // Create a container
            if (mBannerContainer == null) {
              mBannerContainer = FrameLayout(this).apply {
                fitsSystemWindows = true
                setBackgroundColor(Color.TRANSPARENT)
              }
              mBannerContainer?.visibility = mBannerVisibility
              this.addContentView(
                mBannerContainer, FrameLayout.LayoutParams(
                  FrameLayout.LayoutParams.MATCH_PARENT,
                  FrameLayout.LayoutParams.MATCH_PARENT
                )
              )
            }

            // Create banner if not exists yet
            if (mBanner == null) {
              // Get banner size
              val size = getBannerSize(description, width, height)
              // Set isAdaptive
              size.isAdaptive = isAdaptive
              // Handle banner properties according to isAdaptive value
              if (isAdaptive) {
                // isAdaptive is true
                // Create container params
                val isContainerParams = ISContainerParams(containerWidth, containerHeight)
                // Set container params with width and adaptiveHeight
                size.setContainerParams(isContainerParams)
              }
              // Create the banner layout with/without the adaptive settings
              mBanner = IronSource.createBanner(this, size)

              val gravity = when (position) {
                BannerPosition.Top.value -> Gravity.TOP
                BannerPosition.Center.value -> Gravity.CENTER
                BannerPosition.Bottom.value -> Gravity.BOTTOM
                else -> throw IllegalArgumentException("BannerPosition: $position is not supported.")
              }

              // Banner layout params
              val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                gravity
              ).apply {
                if (offset > 0) {
                  topMargin = abs(offset)
                } else if (offset < 0) {
                  bottomMargin = abs(offset)
                }
              }

              // Add banner to container
              mBannerContainer?.addView(mBanner, 0, layoutParams)

              // Add listeners
              mBanner?.levelPlayBannerListener = mLevelPlayBannerListener
            }

            mBanner?.visibility = mBannerVisibility

            // Load banner
            // if already loaded, console error would be shown by iS SDK
            if (placementName != null) {
              IronSource.loadBanner(mBanner, placementName)
            } else {
              IronSource.loadBanner(mBanner)
            }
            result.success(null)
          } catch (e: Throwable) {
            Log.e(TAG, e.toString())
            result.error("ERROR", "Failed to load banner", e)
          }
        }
      }
    } ?: result.error("ERROR", "loadBanner called when activity is null", null)
  }

  /**
   * Destroys the banner ad.
   *
   * @param result The result to be returned after processing.
   */
  private fun destroyBanner(result: Result) {
    activity?.apply {
      runOnUiThread {
        synchronized(this@IronSourceMediationPlugin) {
          mBannerContainer?.removeAllViews()
          if (mBanner != null) {
            IronSource.destroyBanner(mBanner)
            mBanner = null
            mBannerVisibility = View.VISIBLE // Reset the visibility
          }
          result.success(null)
        }
      }
    } ?: result.error("ERROR", "destroyBanner called when activity is null", null)
  }

  /**
   * Displays the banner ad.
   *
   * @param result The result to be returned after processing.
   */
  private fun displayBanner(result: Result) {
    activity?.apply {
      runOnUiThread {
        synchronized(this@IronSourceMediationPlugin) {
          mBannerVisibility = View.VISIBLE
          mBanner?.visibility = View.VISIBLE
          mBannerContainer?.visibility = View.VISIBLE
          result.success(null)
        }
      }
    } ?: result.error("ERROR", "displayBanner called when activity is null", null)
  }

  /**
   * Hides the banner ad.
   *
   * @param result The result to be returned after processing.
   */
  private fun hideBanner(result: Result) {
    activity?.apply {
      runOnUiThread {
        synchronized(this@IronSourceMediationPlugin) {
          mBannerVisibility = View.GONE
          mBanner?.visibility = View.GONE
          mBannerContainer?.visibility = View.GONE
          result.success(null)
        }
      }
    } ?: result.error("ERROR", "hideBanner called when activity is null", null)
  }

  /**
   * Checks if a banner placement is capped.
   *
   * @param call   The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun isBannerPlacementCapped(call: MethodCall, result: Result) {
    val placementName = call.argument("placementName") as String?
      ?: return result.error("ERROR", "placementName is null", null)
    val isCapped = IronSource.isBannerPlacementCapped(placementName)
    return result.success(isCapped)
  }

  /**
   * Return maximal adaptive height according to given width.
   *
   * @param call   The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun getMaximalAdaptiveHeight(call: MethodCall, result: Result) {
    val width =
      call.argument("width") as Int? ?: return result.error("ERROR", "width is null", null)
    val adaptiveHeight = ISBannerSize.getMaximalAdaptiveHeight(width)
    return result.success(adaptiveHeight)
  }
  // endregion

  /** region LevelPlay Native Ad API ==============================================================================*/

  /**
   * Adds a new native ad view factory to the internal registry and registers it with the Flutter platform view registry.
   *
   * @param viewTypeId           The ID or type of the native ad view factory.
   * @param nativeAdViewFactory  The factory responsible for creating native ad views.
   */
  private fun addNativeAdViewFactory(
    viewTypeId: String,
    nativeAdViewFactory: LevelPlayNativeAdViewFactory
  ) {
    if (nativeAdViewFactories.containsKey(viewTypeId)) {
      Log.e(TAG, "A native ad view factory with ID $viewTypeId already exists.")
      return
    }
    nativeAdViewFactories[viewTypeId] = nativeAdViewFactory
    // Custom Native ad platform view registry
    pluginBinding
      ?.platformViewRegistry
      ?.registerViewFactory(viewTypeId, nativeAdViewFactory)
  }

  /**
   * Removes a native ad view factory from the internal registry based on its viewTypeId ID.
   *
   * @param viewTypeId  The ID of the factory to be removed.
   */
  private fun removeNativeAdViewFactory(viewTypeId: String) {
    nativeAdViewFactories.remove(viewTypeId)
  }

  // endregion

  /** region Config API ==========================================================================*/

  /**
   * Enables or disables client-side callbacks.
   *
   * @param call   The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun setClientSideCallbacks(call: MethodCall, result: Result) {
    val isEnabled = call.argument("isEnabled") as Boolean?
      ?: return result.error("ERROR", "isEnabled is null", null)
    SupersonicConfig.getConfigObj().clientSideCallbacks = isEnabled
    return result.success(null)
  }
  // endregion

  /** region Internal Config API =================================================================*/

  /**
   * Sets plugin data for IronSource mediation.
   * Only called internally in the process of init on the Flutter plugin
   *
   * @param call   The method call containing arguments.
   * @param result The result to be returned after processing.
   */
  private fun setPluginData(call: MethodCall, result: Result) {

    val pluginType = call.argument("pluginType") as String?
      ?: return result.error("ERROR", "pluginType is null", null)
    val pluginVersion = call.argument("pluginVersion") as String?
      ?: return result.error("ERROR", "pluginVersion is null", null)
    val pluginFrameworkVersion = call.argument("pluginFrameworkVersion") as String?

    ConfigFile.getConfigFile().setPluginData(pluginType, pluginVersion, pluginFrameworkVersion)
    return result.success(null)
  }

  // endregion

  /** region LevelPlay Init API =================================================================*/

  private fun initLevelPlay(call: MethodCall, result: Result) {
    if (context == null) {
      return result.error("ERROR", "Context is null", null)
    }
    val appKey =
      call.argument("appKey") as String? ?: return result.error("ERROR", "appKey is null", null)
    val adFormats = call.argument("adFormats") as List<String>? ?: listOf()
    val userId = call.argument("userId") as String?
    val legacyAdFormats: List<LevelPlay.AdFormat> = adFormats.map {
      when (it) {
        "REWARDED" -> LevelPlay.AdFormat.REWARDED
        "INTERSTITIAL" -> LevelPlay.AdFormat.INTERSTITIAL
        "BANNER" -> LevelPlay.AdFormat.BANNER
        "NATIVE_AD" -> LevelPlay.AdFormat.NATIVE_AD
        else -> return@initLevelPlay result.error("ERROR", "Unsupported ad format: $it", null)
      }
    }.toList()
    val requestBuilder = LevelPlayInitRequest.Builder(appKey)
    requestBuilder.withLegacyAdFormats(legacyAdFormats)
    if (userId != null)
      requestBuilder.withUserId(userId)
    val initRequest = requestBuilder.build()

    LevelPlay.init(context!!, initRequest, mLevelPlayInitListener!!)

    return result.success(null)
  }

  // endregion

  /** region LevelPlayInterstitialAd API =================================================================*/
  private fun isInterstitialAdPlacementCapped(call: MethodCall, result: Result) {
    val placementName: String = call.argument("placementName")!!
    val isCapped = LevelPlayInterstitialAd.isPlacementCapped(placementName)
    result.success(isCapped)
  }

  /**
   * Creates a rewarded ad using the provided ad unit ID.
   *
   * @param call The method call from Flutter containing the ad unit ID parameter
   * @param result The result callback to send the created adId back to Flutter
   */

  private fun createInterstitialAd(call: MethodCall, result: Result) {
    val adUnitId: String = call.argument("adUnitId")!!
    // Create interstitial ad through the manager and get its unique adId
    val adId: String = levelPlayAdObjectManager.createInterstitialAd(adUnitId)
    // Return the adId to Flutter
    result.success(adId)
  }

  private fun loadInterstitialAd(call: MethodCall, result: Result) {
    val adId: String = call.argument("adId")!!
    levelPlayAdObjectManager.loadInterstitialAd(adId)
    result.success(null)
  }

  private fun showInterstitialAd(call: MethodCall, result: Result) {
    val adId: String = call.argument("adId")!!
    val placementName: String? = call.argument("placementName")
    levelPlayAdObjectManager.showInterstitialAd(adId, placementName)
    result.success(null)
  }

  private fun isInterstitialAdReady(call: MethodCall, result: Result) {
    val adId: String = call.argument("adId")!!
    val isReady = levelPlayAdObjectManager.isInterstitialAdReady(adId)
    result.success(isReady)
  }


  private fun disposeAd(call: MethodCall, result: Result) {
    val adId: String = call.argument("adId")!!
    levelPlayAdObjectManager.disposeAd(adId)
    result.success(null)
  }

  private fun disposeAllAds(result: Result) {
    levelPlayAdObjectManager.disposeAllAds()
    result.success(null)
  }

  // endregion

  /** region LevelPlayAdSize API =================================================================*/

  private fun createAdaptiveAdSize(call: MethodCall, result: Result) {
    val width = call.argument("width") as Int?
    val size = context?.let {
      LevelPlayAdSize.createAdaptiveAdSize(it, width)
    }
    return result.success(size.toMap())
  }

  // endregion

  /** region LevelPlayRewardedAd API =================================================================*/
  private fun isRewardedAdPlacementCapped(call: MethodCall, result: Result) {
    val placementName: String = call.argument("placementName")!!
    val isCapped = LevelPlayRewardedAd.isPlacementCapped(placementName)
    result.success(isCapped)
  }

  /**
   * Creates a rewarded ad using the provided ad unit ID.
   *
   * @param call The method call from Flutter containing the ad unit ID parameter
   * @param result The result callback to send the created adId back to Flutter
   */

  private fun createRewardedAd(call: MethodCall, result: Result) {
    val adUnitId: String = call.argument("adUnitId")!!
    // Create a rewarded ad through the manager and get its unique adId
    val adId: String = levelPlayAdObjectManager.createRewardedAd(adUnitId)
    // Return the adId to Flutter
    result.success(adId)
  }

    private fun loadRewardedAd(call: MethodCall, result: Result) {
      val adId: String = call.argument("adId")!!
      levelPlayAdObjectManager.loadRewardedAd(adId)
      result.success(null)
    }

    private fun showRewardedAd(call: MethodCall, result: Result) {
      val adId: String = call.argument("adId")!!
      val placementName: String? = call.argument("placementName")
      levelPlayAdObjectManager.showRewardedAd(adId, placementName)
      result.success(null)
    }

    private fun isRewardedAdReady(call: MethodCall, result: Result) {
      val adId: String = call.argument("adId")!!
      val isReady = levelPlayAdObjectManager.isRewardedAdReady(adId)
      result.success(isReady)
    }

    // endregion

    /** region ActivityAware =======================================================================*/
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
      activity = binding.activity
      levelPlayAdObjectManager.activity = binding.activity
      if (activity is FlutterActivity) {
        (activity as FlutterActivity).lifecycle.addObserver(this)
      } else if (activity is FlutterFragmentActivity) {
        (activity as FlutterFragmentActivity).lifecycle.addObserver(this)
      }
    }

    override fun onDetachedFromActivityForConfigChanges() {
      if (activity is FlutterActivity) {
        (activity as FlutterActivity).lifecycle.removeObserver(this)
      } else if (activity is FlutterFragmentActivity) {
        (activity as FlutterFragmentActivity).lifecycle.removeObserver(this)
      }
      activity = null
      levelPlayAdObjectManager.activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
      if (activity is FlutterActivity) {
        activity = binding.activity as FlutterActivity
        (activity as FlutterActivity).lifecycle.addObserver(this)
      } else if (activity is FlutterFragmentActivity) {
        activity = binding.activity as FlutterFragmentActivity
        (activity as FlutterFragmentActivity).lifecycle.addObserver(this)
      }
      levelPlayAdObjectManager.activity = activity
    }

    override fun onDetachedFromActivity() {
      if (activity is FlutterActivity) {
        (activity as FlutterActivity).lifecycle.removeObserver(this)
      } else if (activity is FlutterFragmentActivity) {
        (activity as FlutterFragmentActivity).lifecycle.removeObserver(this)
      }
      activity = null
      levelPlayAdObjectManager.activity = null
    }

    // endregion

    /** region LifeCycleObserver  ==================================================================*/
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
      activity?.apply { IronSource.onResume(this) }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
      activity?.apply { IronSource.onPause(this) }
    }
    // endregion

    companion object {
      val TAG: String = IronSourceMediationPlugin::class.java.simpleName

      /**
       * Registers a native ad view factory with the specified factory ID to be used within the Flutter engine.
       *
       * @param flutterEngine The Flutter engine instance where the native ad view factory will be registered.
       * @param viewType The ID for the native ad view factory.
       * @param nativeAdViewFactory The platform view factory responsible for creating native ad views.
       */
      fun registerNativeAdViewFactory(
        flutterEngine: FlutterEngine,
        viewTypeId: String,
        nativeAdViewFactory: LevelPlayNativeAdViewFactory
      ) {
        try {
          val flutterPlugin =
            flutterEngine.plugins[IronSourceMediationPlugin::class.java] as IronSourceMediationPlugin
          flutterPlugin.addNativeAdViewFactory(viewTypeId, nativeAdViewFactory)
        } catch (e: IllegalStateException) {
          Log.e(TAG, "The plugin may have not been registered.")
        }
      }

      /**
       * Unregisters a previously registered native ad view factory from the Flutter engine.
       *
       * @param flutterEngine The Flutter engine instance from which the native ad view factory will be unregistered.
       * @param viewTypeId The ID of the native ad view factory to be unregistered.
       */
      fun unregisterNativeAdViewFactory(
        flutterEngine: FlutterEngine,
        viewTypeId: String
      ) {
        try {
          val flutterPlugin =
            flutterEngine.plugins[IronSourceMediationPlugin::class.java] as IronSourceMediationPlugin
          flutterPlugin.removeNativeAdViewFactory(viewTypeId)
        } catch (e: IllegalStateException) {
          Log.e(TAG, "The plugin may have not been registered.")
        }
      }
    }

    enum class BannerPosition(val value: Int) {
      Top(0),
      Center(1),
      Bottom(2)
    }
  }


