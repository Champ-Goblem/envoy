package org.chromium.net.impl;

import static io.envoyproxy.envoymobile.engine.EnvoyConfiguration.TrustChainVerification.VERIFY_TRUST_CHAIN;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import io.envoyproxy.envoymobile.engine.AndroidEngineImpl;
import io.envoyproxy.envoymobile.engine.AndroidJniLibrary;
import io.envoyproxy.envoymobile.engine.AndroidNetworkMonitor;
import io.envoyproxy.envoymobile.engine.EnvoyConfiguration;
import io.envoyproxy.envoymobile.engine.EnvoyConfiguration.TrustChainVerification;
import io.envoyproxy.envoymobile.engine.EnvoyEngine;
import io.envoyproxy.envoymobile.engine.EnvoyNativeFilterConfig;
import io.envoyproxy.envoymobile.engine.types.EnvoyEventTracker;
import io.envoyproxy.envoymobile.engine.types.EnvoyHTTPFilterFactory;
import io.envoyproxy.envoymobile.engine.types.EnvoyLogger;
import io.envoyproxy.envoymobile.engine.types.EnvoyOnEngineRunning;
import io.envoyproxy.envoymobile.engine.types.EnvoyStringAccessor;
import io.envoyproxy.envoymobile.engine.types.EnvoyKeyValueStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.chromium.net.ExperimentalCronetEngine;
import org.chromium.net.ICronetEngineBuilder;

/**
 * Implementation of {@link ICronetEngineBuilder} that builds native Cronvoy engine.
 */
public class NativeCronvoyEngineBuilderImpl extends CronvoyEngineBuilderImpl {

  // TODO(refactor) move unshared variables into their specific methods.
  private final List<EnvoyNativeFilterConfig> nativeFilterChain = new ArrayList<>();
  private final EnvoyLogger mEnvoyLogger = null;
  private final EnvoyEventTracker mEnvoyEventTracker = null;
  private boolean mAdminInterfaceEnabled = false;
  private String mGrpcStatsDomain = null;
  private int mConnectTimeoutSeconds = 30;
  private int mDnsRefreshSeconds = 60;
  private int mDnsFailureRefreshSecondsBase = 2;
  private int mDnsFailureRefreshSecondsMax = 10;
  private int mDnsQueryTimeoutSeconds = 25;
  private int mDnsMinRefreshSeconds = 60;
  private List<String> mDnsPreresolveHostnames = Collections.emptyList();
  private boolean mEnableDNSCache = false;
  private int mDnsCacheSaveIntervalSeconds = 1;
  private List<String> mDnsFallbackNameservers = Collections.emptyList();
  private boolean mEnableDnsFilterUnroutableFamilies = true;
  private boolean mDnsUseSystemResolver = true;
  private boolean mEnableDrainPostDnsRefresh = false;
  private boolean mEnableGzipDecompression = true;
  private boolean mEnableSocketTag = true;
  private boolean mEnableInterfaceBinding = false;
  private boolean mEnableProxying = false;
  private int mH2ConnectionKeepaliveIdleIntervalMilliseconds = 1;
  private int mH2ConnectionKeepaliveTimeoutSeconds = 10;
  private int mMaxConnectionsPerHost = 7;
  private int mStatsFlushSeconds = 60;
  private int mStreamIdleTimeoutSeconds = 15;
  private int mPerTryIdleTimeoutSeconds = 15;
  private String mAppVersion = "unspecified";
  private String mAppId = "unspecified";
  private TrustChainVerification mTrustChainVerification = VERIFY_TRUST_CHAIN;
  private boolean mEnablePlatformCertificatesValidation = true;
  private String mRtdsLayerName = "";
  private int mRtdsTimeoutSeconds = 0;
  private String mAdsAddress = "";
  private int mAdsPort = 0;
  private String mAdsToken = "";
  private int mAdsTokenLifetime = 0;
  private String mAdsRootCerts = "";
  private String mNodeId = "";
  private String mNodeRegion = "";
  private String mNodeZone = "";
  private String mNodeSubZone = "";
  private String mCdsResourcesLocator = "";
  private int mCdsTimeoutSeconds = 0;
  private boolean mEnableCds = false;

  /**
   * Builder for Native Cronet Engine. Default config enables SPDY, disables QUIC and HTTP cache.
   *
   * @param context Android {@link Context} for engine to use.
   */
  public NativeCronvoyEngineBuilderImpl(Context context) { super(context); }

  /**
   * Indicates to skip the TLS certificate verification.
   *
   * @return the builder to facilitate chaining.
   */
  @VisibleForTesting
  public CronvoyEngineBuilderImpl setMockCertVerifierForTesting() {
    mTrustChainVerification = TrustChainVerification.ACCEPT_UNTRUSTED;
    return this;
  }

  /**
   * Adds url interceptors to the cronetEngine
   *
   * @return the builder to facilitate chaining.
   */
  @VisibleForTesting
  public CronvoyEngineBuilderImpl addUrlInterceptorsForTesting() {
    nativeFilterChain.add(new EnvoyNativeFilterConfig(
        "envoy.filters.http.test_read",
        "{\"@type\": type.googleapis.com/envoymobile.test.integration.filters.http.test_read.TestRead}"));
    return this;
  }

  @Override
  public ExperimentalCronetEngine build() {
    if (getUserAgent() == null) {
      setUserAgent(getDefaultUserAgent());
    }
    return new CronvoyUrlRequestContext(this);
  }

  EnvoyEngine createEngine(EnvoyOnEngineRunning onEngineRunning) {
    AndroidEngineImpl engine = new AndroidEngineImpl(getContext(), onEngineRunning, mEnvoyLogger,
                                                     mEnvoyEventTracker, mEnableProxying);
    AndroidJniLibrary.load(getContext());
    AndroidNetworkMonitor.load(getContext(), engine);
    engine.runWithConfig(createEnvoyConfiguration(), getLogLevel());
    return engine;
  }

  private EnvoyConfiguration createEnvoyConfiguration() {
    List<EnvoyHTTPFilterFactory> platformFilterChain = Collections.emptyList();
    Map<String, EnvoyStringAccessor> stringAccessors = Collections.emptyMap();
    Map<String, EnvoyKeyValueStore> keyValueStores = Collections.emptyMap();
    List<String> statSinks = Collections.emptyList();
    Map<String, Boolean> runtimeGuards = Collections.emptyMap();

    return new EnvoyConfiguration(
        mAdminInterfaceEnabled, mGrpcStatsDomain, mConnectTimeoutSeconds, mDnsRefreshSeconds,
        mDnsFailureRefreshSecondsBase, mDnsFailureRefreshSecondsMax, mDnsQueryTimeoutSeconds,
        mDnsMinRefreshSeconds, mDnsPreresolveHostnames, mEnableDNSCache,
        mDnsCacheSaveIntervalSeconds, mEnableDrainPostDnsRefresh, quicEnabled(),
        mEnableGzipDecompression, brotliEnabled(), mEnableSocketTag, mEnableInterfaceBinding,
        mH2ConnectionKeepaliveIdleIntervalMilliseconds, mH2ConnectionKeepaliveTimeoutSeconds,
        mMaxConnectionsPerHost, mStatsFlushSeconds, mStreamIdleTimeoutSeconds,
        mPerTryIdleTimeoutSeconds, mAppVersion, mAppId, mTrustChainVerification, nativeFilterChain,
        platformFilterChain, stringAccessors, keyValueStores, statSinks, runtimeGuards,
        mEnablePlatformCertificatesValidation, mRtdsLayerName, mRtdsTimeoutSeconds, mAdsAddress,
        mAdsPort, mAdsToken, mAdsTokenLifetime, mAdsRootCerts, mNodeId, mNodeRegion, mNodeZone,
        mNodeSubZone, mCdsResourcesLocator, mCdsTimeoutSeconds, mEnableCds);
  }
}
