/**
 * BreadWallet
 *
 * Created by Drew Carlson <drew.carlson@breadwallet.com> on 9/23/19.
 * Copyright (c) 2019 breadwallet LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.xwallet.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.xwallet.BuildConfig
import com.xwallet.breadbox.BdbAuthInterceptor
import com.xwallet.breadbox.BreadBox
import com.xwallet.breadbox.BreadBoxCloseWorker
import com.xwallet.breadbox.CoreBreadBox
import com.breadwallet.corecrypto.CryptoApiProvider
import com.breadwallet.crypto.CryptoApi
import com.breadwallet.crypto.blockchaindb.BlockchainDb
import com.xwallet.installHooks
import com.xwallet.logger.logDebug
import com.xwallet.logger.logError
import com.xwallet.repository.ExperimentsRepository
import com.xwallet.repository.ExperimentsRepositoryImpl
import com.xwallet.repository.RatesRepository
import com.xwallet.tools.crypto.Base32
import com.xwallet.tools.crypto.CryptoHelper
import com.xwallet.tools.manager.BRClipboardManager
import com.xwallet.tools.manager.BRReportsManager
import com.xwallet.tools.manager.BRSharedPrefs
import com.xwallet.tools.manager.ConnectivityStateProvider
import com.xwallet.tools.manager.InternetManager
import com.xwallet.tools.manager.NetworkCallbacksConnectivityStateProvider
import com.xwallet.tools.manager.RatesFetcher
import com.xwallet.tools.security.BRKeyStore
import com.xwallet.tools.security.BrdUserManager
import com.xwallet.tools.security.BrdUserState
import com.xwallet.tools.security.CryptoUserManager
import com.xwallet.tools.services.BRDFirebaseMessagingService
import com.xwallet.tools.util.BRConstants
import com.xwallet.tools.util.EventUtils
import com.xwallet.tools.util.ServerBundlesHelper
import com.xwallet.tools.util.SupportManager
import com.xwallet.tools.util.TokenUtil
import com.xwallet.ui.uigift.GiftBackup
import com.xwallet.ui.uigift.SharedPrefsGiftBackup
import com.xwallet.util.AddressResolverServiceLocator
import com.xwallet.util.CryptoUriParser
import com.xwallet.util.FioService
import com.xwallet.util.PayIdService
import com.xwallet.util.errorHandler
import com.xwallet.util.isEthereum
import com.xwallet.util.usermetrics.UserMetricsUtil
import com.platform.APIClient
import com.platform.HTTPServer
import com.xwallet.platform.interfaces.AccountMetaDataProvider
import com.platform.interfaces.KVStoreProvider
import com.platform.interfaces.MetaDataManager
import com.platform.interfaces.WalletProvider
import com.platform.sqlite.PlatformSqliteHelper
import com.platform.tools.KVStoreManager
import com.platform.tools.TokenHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.kodein.di.DKodein
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.android.x.androidXModule
import org.kodein.di.direct
import org.kodein.di.erased.bind
import org.kodein.di.erased.instance
import org.kodein.di.erased.singleton
import java.io.File
import java.io.UnsupportedEncodingException
import java.util.Locale
import java.util.regex.Pattern

private const val LOCK_TIMEOUT = 180_000L // 3 minutes in milliseconds
private const val ENCRYPTED_PREFS_FILE = "crypto_shared_prefs"
private const val ENCRYPTED_GIFT_BACKUP_FILE = "gift_shared_prefs"
private const val WALLETKIT_DATA_DIR_NAME = "cryptocore"

@Suppress("TooManyFunctions")
class BreadApp : Application(), KodeinAware, CameraXConfig.Provider {

    companion object {
        private val TAG = BreadApp::class.java.name

        init {
            CryptoApi.initialize(CryptoApiProvider.getInstance())
        }

        // The wallet ID is in the form "xxxx xxxx xxxx xxxx" where x is a lowercase letter or a number.
        private const val WALLET_ID_PATTERN = "^[a-z0-9 ]*$"
        private const val WALLET_ID_SEPARATOR = " "
        private const val NUMBER_OF_BYTES_FOR_SHA256_NEEDED = 10
        private const val SERVER_SHUTDOWN_DELAY_MILLIS = 60000L // 60 seconds

        @SuppressLint("StaticFieldLeak")
        private lateinit var mInstance: BreadApp

        @SuppressLint("StaticFieldLeak")
        private var mCurrentActivity: Activity? = null

        /** [CoroutineScope] matching the lifetime of the application. */
        val applicationScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + errorHandler("applicationScope")
        )

        private val startedScope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default + errorHandler("startedScope")
        )

        fun getBreadBox(): BreadBox = mInstance.direct.instance()

        // TODO: Find better place/means for this
        fun getDefaultEnabledWallets() = when {
            BuildConfig.BITCOIN_TESTNET -> listOf(
                "bitcoin-testnet:__native__",
                "ethereum-ropsten:__native__"
//                "ethereum-ropsten:0x558ec3152e2eb2174905cd19aea4e34a23de9ad6"
            )
            else -> listOf(
                "bitcoin-mainnet:__native__",
                "ethereum-mainnet:__native__"
//                "ethereum-mainnet:0x558ec3152e2eb2174905cd19aea4e34a23de9ad6"
            )
        }

        /**
         * Initialize the wallet id (rewards id), and save it in the SharedPreferences.
         */
        private fun initializeWalletId() {
            applicationScope.launch(Dispatchers.Main) {
                val walletId = getBreadBox()
                    .wallets(false)
                    .mapNotNull { wallets ->
                        wallets.find { it.currency.code.isEthereum() }
                    }
                    .take(1)
                    .map { generateWalletId(it.target.toString()) }
                    .flowOn(Dispatchers.Default)
                    .first()
                if (walletId.isNullOrBlank() || !walletId.matches(WALLET_ID_PATTERN.toRegex())) {
                    val error = IllegalStateException("Generated corrupt walletId: $walletId")
                    BRReportsManager.reportBug(error)
                }
                BRSharedPrefs.putWalletRewardId(id = walletId ?: "")
            }
        }

        /**
         * Generates the wallet id (rewards id) based on the Ethereum address. The format of the id is
         * "xxxx xxxx xxxx xxxx", where x is a lowercase letter or a number.
         *
         * @return The wallet id.
         */
        // TODO: This entire operation should be moved into a separate class.
        @Synchronized
        @Suppress("ReturnCount")
        fun generateWalletId(address: String): String? {
            try {
                // Remove the first 2 characters i.e. 0x
                val rawAddress = address.drop(2)

                // Get the address bytes.
                val addressBytes = rawAddress.toByteArray()

                // Run SHA256 on the address bytes.
                val sha256Address = CryptoHelper.sha256(addressBytes) ?: byteArrayOf()
                if (sha256Address.isEmpty()) {
                    BRReportsManager.reportBug(IllegalAccessException("Failed to generate SHA256 hash."))
                    return null
                }

                // Get the first 10 bytes of the SHA256 hash.
                val firstTenBytes =
                    sha256Address.sliceArray(0 until NUMBER_OF_BYTES_FOR_SHA256_NEEDED)

                // Convert the first 10 bytes to a lower case string.
                val base32String = com.xwallet.tools.crypto.Base32.encode(firstTenBytes).toLowerCase(Locale.ROOT)

                // Insert a space every 4 chars to match the specified format.
                val builder = StringBuilder()
                val matcher = Pattern.compile(".{1,4}").matcher(base32String)
                var separator = ""
                while (matcher.find()) {
                    val piece = base32String.substring(matcher.start(), matcher.end())
                    builder.append(separator + piece)
                    separator = WALLET_ID_SEPARATOR
                }
                return builder.toString()
            } catch (e: UnsupportedEncodingException) {
                logError("Unable to get address bytes.", e)
                return null
            }
        }

        // TODO: Refactor so this does not store the current activity like this.
        @JvmStatic
        @Deprecated("")
        fun getBreadContext(): Context {
            var app: Context? = mCurrentActivity
            if (app == null) {
                app = mInstance
            }
            return app
        }

        // TODO: Refactor so this does not store the current activity like this.
        @JvmStatic
        fun setBreadContext(app: Activity?) {
            mCurrentActivity = app
        }

        /** Provides access to [DKodein]. Meant only for Java compatibility. **/
        @JvmStatic
        fun getKodeinInstance(): DKodein {
            return mInstance.direct
        }
    }

    override val kodein by Kodein.lazy {
        importOnce(androidXModule(this@BreadApp))

        bind<CryptoUriParser>() with singleton {
            CryptoUriParser(instance())
        }

        bind<APIClient>() with singleton {
            APIClient(this@BreadApp, direct.instance(), createHttpHeaders())
        }

        bind<BrdUserManager>() with singleton {
            CryptoUserManager(this@BreadApp, ::createCryptoEncryptedPrefs, instance())
        }

        bind<GiftBackup>() with singleton {
            SharedPrefsGiftBackup(::createGiftBackupEncryptedPrefs)
        }

        bind<KVStoreProvider>() with singleton {
            KVStoreManager(this@BreadApp)
        }

        val metaDataManager by lazy { MetaDataManager(direct.instance()) }

        bind<WalletProvider>() with singleton { metaDataManager }

        bind<AccountMetaDataProvider>() with singleton { metaDataManager }

        bind<OkHttpClient>() with singleton { OkHttpClient() }

        bind<BdbAuthInterceptor>() with singleton {
            val httpClient = instance<OkHttpClient>()
            BdbAuthInterceptor(httpClient, direct.instance())
        }

        bind<BlockchainDb>() with singleton {
            val httpClient = instance<OkHttpClient>()
            val authInterceptor = instance<BdbAuthInterceptor>()
            BlockchainDb(
                httpClient.newBuilder()
                    .addInterceptor(authInterceptor)
                    .build()
            )
        }

        bind<PayIdService>() with singleton {
            PayIdService(instance())
        }

        bind<FioService>() with singleton {
            FioService(instance())
        }

        bind<AddressResolverServiceLocator>() with singleton {
            AddressResolverServiceLocator(
                instance(),
                instance()
            )
        }

        bind<BreadBox>() with singleton {
            CoreBreadBox(
                File(filesDir, WALLETKIT_DATA_DIR_NAME),
                !BuildConfig.BITCOIN_TESTNET,
                instance(),
                instance(),
                instance()
            )
        }

        bind<ExperimentsRepository>() with singleton { ExperimentsRepositoryImpl }

        bind<RatesRepository>() with singleton { RatesRepository.getInstance(this@BreadApp) }

        bind<RatesFetcher>() with singleton {
            RatesFetcher(
                instance(),
                instance(),
                this@BreadApp
            )
        }

        bind<ConversionTracker>() with singleton {
            ConversionTracker(instance())
        }

        bind<ConnectivityStateProvider>() with singleton {
            val connectivityManager =
                this@BreadApp.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NetworkCallbacksConnectivityStateProvider(connectivityManager)
            } else {
                InternetManager(connectivityManager,this@BreadApp)
            }
        }

        bind<SupportManager>() with singleton {
            SupportManager(
                this@BreadApp,
                instance(),
                instance()
            )
        }
    }

    private var accountLockJob: Job? = null

    private val apiClient by instance<APIClient>()
    private val userManager by instance<BrdUserManager>()
    private val ratesFetcher by instance<RatesFetcher>()
    private val accountMetaData by instance<AccountMetaDataProvider>()
    private val conversionTracker by instance<ConversionTracker>()
    private val connectivityStateProvider by instance<ConnectivityStateProvider>()

    override fun onCreate() {
        super.onCreate()
        installHooks()
        mInstance = this

        com.xwallet.tools.security.BRKeyStore.provideContext(this)
        BRClipboardManager.provideContext(this)
        BRSharedPrefs.initialize(this, applicationScope)
        TokenHolder.provideContext(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(ApplicationLifecycleObserver())
        ApplicationLifecycleObserver.addApplicationLifecycleListener { event ->
            logDebug(event.name)
            when (event) {
                Lifecycle.Event.ON_START -> handleOnStart()
                Lifecycle.Event.ON_STOP -> handleOnStop()
                Lifecycle.Event.ON_DESTROY -> handleOnDestroy()
            }
        }

        applicationScope.launch {
            com.xwallet.tools.util.ServerBundlesHelper.extractBundlesIfNeeded(mInstance)
            TokenUtil.initialize(mInstance, false, !BuildConfig.BITCOIN_TESTNET)
        }

        // Start our local server as soon as the application instance is created, since we need to
        // display support WebViews during onboarding.
        HTTPServer.getInstance().startServer(this)
    }

    /**
     * Each time the app resumes, check to see if the device state is valid.
     * Even if the wallet is not initialized, we may need tell the user to enable the password.
     */
    private fun handleOnStart() {
        accountLockJob?.cancel()
        BreadBoxCloseWorker.cancelEnqueuedWork()
        val breadBox = getBreadBox()
        userManager
            .stateChanges()
            .distinctUntilChanged()
            .filterIsInstance<BrdUserState.Enabled>()
            .onEach {
                if (!userManager.isMigrationRequired()) {
                    startWithInitializedWallet(breadBox)
                }
            }
            .launchIn(startedScope)
    }

    private fun handleOnStop() {
        if (userManager.isInitialized()) {
            accountLockJob = applicationScope.launch {
                delay(LOCK_TIMEOUT)
                userManager.lock()
            }
            BreadBoxCloseWorker.enqueueWork()
            applicationScope.launch {
                com.xwallet.tools.util.EventUtils.saveEvents(this@BreadApp)
                com.xwallet.tools.util.EventUtils.pushToServer(this@BreadApp)
            }

        }
        logDebug("Shutting down HTTPServer.")
        HTTPServer.getInstance().stopServer()

        startedScope.coroutineContext.cancelChildren()
    }

    private fun handleOnDestroy() {
        if (HTTPServer.getInstance().isRunning) {
            logDebug("Shutting down HTTPServer.")
            HTTPServer.getInstance().stopServer()
        }

        connectivityStateProvider.close()
        getBreadBox().apply { if (isOpen) close() }
        applicationScope.cancel()
    }

    fun startWithInitializedWallet(breadBox: BreadBox, migrate: Boolean = false) {
        val context = mInstance.applicationContext
        incrementAppForegroundedCounter()

        if (!breadBox.isOpen) {
            val account = checkNotNull(userManager.getAccount()) {
                "Wallet is initialized but Account is null"
            }

            breadBox.open(account)
        }

        initializeWalletId()
        BRDFirebaseMessagingService.initialize(context)
        HTTPServer.getInstance().startServer(this)
        apiClient.updatePlatform(applicationScope)
        applicationScope.launch {
            com.xwallet.util.usermetrics.UserMetricsUtil.makeUserMetricsRequest(context)
        }

        startedScope.launch {
            accountMetaData.recoverAll(migrate)
        }

        ratesFetcher.start(startedScope)
        
        conversionTracker.start(startedScope)
    }

    private fun incrementAppForegroundedCounter() {
        BRSharedPrefs.putInt(
            BRSharedPrefs.APP_FOREGROUNDED_COUNT,
            BRSharedPrefs.getInt(BRSharedPrefs.APP_FOREGROUNDED_COUNT, 0) + 1
        )
    }

    private fun createCryptoEncryptedPrefs(): SharedPreferences? = createEncryptedPrefs(ENCRYPTED_PREFS_FILE)
    private fun createGiftBackupEncryptedPrefs(): SharedPreferences? = createEncryptedPrefs(ENCRYPTED_GIFT_BACKUP_FILE)

    private fun createEncryptedPrefs(fileName: String): SharedPreferences? {
        val masterKeys = runCatching {
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        }.onFailure { e ->
            BRReportsManager.error("Failed to create Master Keys", e)
        }.getOrNull() ?: return null

        return runCatching {
            EncryptedSharedPreferences.create(
                fileName,
                masterKeys,
                this@BreadApp,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.onFailure { e ->
            BRReportsManager.error("Failed to create Encrypted Shared Preferences", e)
        }.getOrNull()
    }

    override fun getCameraXConfig(): CameraXConfig {
        return Camera2Config.defaultConfig()
    }

    @VisibleForTesting
    fun clearApplicationData() {
        runCatching {
            startedScope.coroutineContext.cancelChildren()
            applicationScope.coroutineContext.cancelChildren()
            val breadBox = direct.instance<BreadBox>()
            if (breadBox.isOpen) {
                breadBox.close()
            }
            (userManager as CryptoUserManager).wipeAccount()

            File(filesDir, WALLETKIT_DATA_DIR_NAME).deleteRecursively()

            PlatformSqliteHelper.getInstance(this)
                .writableDatabase
                .delete(PlatformSqliteHelper.KV_STORE_TABLE_NAME, null, null)

            getSharedPreferences(BRSharedPrefs.PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
        }.onFailure { e ->
            logError("Failed to clear application data", e)
        }
    }

    fun createHttpHeaders(): Map<String, String> {
        // Split the default device user agent string by spaces and take the first string.
        // Example user agent string: "Dalvik/1.6.0 (Linux; U;Android 5.1; LG-F320SBuild/KOT49I.F320S22g) Android/9"
        // We only want: "Dalvik/1.6.0"
        val deviceUserAgent =
            (System.getProperty(APIClient.SYSTEM_PROPERTY_USER_AGENT) ?: "")
                .split("\\s".toRegex())
                .firstOrNull()

        // The BRD server expects the following user agent: appName/appVersion engine/engineVersion plaform/plaformVersion
        val brdUserAgent = "${APIClient.UA_APP_NAME}${BuildConfig.VERSION_CODE} $deviceUserAgent ${APIClient.UA_PLATFORM}${Build.VERSION.RELEASE}"

        return mapOf(
            APIClient.HEADER_IS_INTERNAL to if (BuildConfig.IS_INTERNAL_BUILD) BRConstants.TRUE else BRConstants.FALSE,
            APIClient.HEADER_TESTFLIGHT to if (BuildConfig.DEBUG) BRConstants.TRUE else BRConstants.FALSE,
            APIClient.HEADER_TESTNET to if (BuildConfig.BITCOIN_TESTNET) BRConstants.TRUE else BRConstants.FALSE,
            APIClient.HEADER_USER_AGENT to brdUserAgent
        )
    }
}
