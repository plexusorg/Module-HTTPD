package dev.plex.ratelimit;

import dev.plex.api.config.ModuleConfiguration;
import dev.plex.logging.Log;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RateLimitFilter implements Filter
{
    private static final long EVICT_INTERVAL_MILLIS = 60_000L;
    private static final long IP_IDLE_TIMEOUT_MILLIS = 5 * 60_000L;
    private static final int IP_BUCKET_HARD_CAP = 50_000;
    private static final long REJECTION_LOG_INTERVAL_MILLIS = 10_000L;

    private final boolean enabled;
    private final Log accessLog;
    private final TokenBucket globalBucket;
    private final TokenBucket assetGlobalBucket;
    private final double ipCapacity;
    private final double ipRefillPerSecond;
    private final double assetIpCapacity;
    private final double assetIpRefillPerSecond;
    private final ConcurrentHashMap<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TokenBucket> assetIpBuckets = new ConcurrentHashMap<>();
    private final AtomicLong nextEvictMillis = new AtomicLong(System.currentTimeMillis() + EVICT_INTERVAL_MILLIS);
    private final AtomicLong nextRejectionLogMillis = new AtomicLong(System.currentTimeMillis() + REJECTION_LOG_INTERVAL_MILLIS);
    private final AtomicLong rejectedGlobal = new AtomicLong();
    private final AtomicLong rejectedPerIp = new AtomicLong();
    private final AtomicLong rejectedAssets = new AtomicLong();

    private final double loginCost;
    private final double callbackCost;
    private final double schematicListCost;
    private final double schematicDownloadCost;
    private final double punishmentsCost;
    private final double streamCost;
    private final double playerListCost;
    private final double staffMutationCost;
    private final double staffReadCost;

    public RateLimitFilter(ModuleConfiguration config, Log accessLog)
    {
        this.accessLog = accessLog;
        this.enabled = config.getBoolean("rate-limit.enabled", true);
        double globalCapacity = config.getDouble("rate-limit.global.capacity", 200.0);
        double globalRate = config.getDouble("rate-limit.global.per-second", 100.0);
        this.globalBucket = new TokenBucket(globalCapacity, globalRate);
        this.ipCapacity = config.getDouble("rate-limit.per-ip.capacity", 30.0);
        this.ipRefillPerSecond = config.getDouble("rate-limit.per-ip.per-second", 10.0);

        double assetGlobalCapacity = config.getDouble("rate-limit.assets.global.capacity", 2000.0);
        double assetGlobalRate = config.getDouble("rate-limit.assets.global.per-second", 1000.0);
        this.assetGlobalBucket = new TokenBucket(assetGlobalCapacity, assetGlobalRate);
        this.assetIpCapacity = config.getDouble("rate-limit.assets.per-ip.capacity", 300.0);
        this.assetIpRefillPerSecond = config.getDouble("rate-limit.assets.per-ip.per-second", 100.0);

        double maximumCost = Math.min(globalCapacity, ipCapacity);
        this.loginCost = boundedCost(config.getDouble("rate-limit.costs.oauth-login", 10.0), maximumCost);
        this.callbackCost = boundedCost(config.getDouble("rate-limit.costs.oauth-callback", 10.0), maximumCost);
        this.schematicListCost = boundedCost(config.getDouble("rate-limit.costs.schematic-list", 10.0), maximumCost);
        this.schematicDownloadCost = boundedCost(config.getDouble("rate-limit.costs.schematic-download", 5.0), maximumCost);
        this.punishmentsCost = boundedCost(config.getDouble("rate-limit.costs.punishments", 5.0), maximumCost);
        this.streamCost = boundedCost(config.getDouble("rate-limit.costs.stream-connect", 10.0), maximumCost);
        this.playerListCost = boundedCost(config.getDouble("rate-limit.costs.player-list", 5.0), maximumCost);
        this.staffMutationCost = boundedCost(config.getDouble("rate-limit.costs.staff-mutation", 10.0), maximumCost);
        this.staffReadCost = boundedCost(config.getDouble("rate-limit.costs.staff-read", 5.0), maximumCost);
    }

    @Override
    public void init(FilterConfig filterConfig) {}

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
    {
        if (!enabled)
        {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String ip = clientIp(httpRequest);
        if (isAssetRequest(httpRequest))
        {
            if (!consumeAssetBudget(httpRequest, httpResponse, ip)) return;
            chain.doFilter(request, response);
            return;
        }

        double cost = requestCost(httpRequest);
        TokenBucket bucket = bucketFor(ipBuckets, ip, ipCapacity, ipRefillPerSecond);
        if (!bucket.tryConsume(cost))
        {
            reject(httpRequest, httpResponse, bucket.retryAfterSeconds(cost), "per-ip");
            return;
        }

        if (!globalBucket.tryConsume(cost))
        {
            bucket.refund(cost);
            reject(httpRequest, httpResponse, globalBucket.retryAfterSeconds(cost), "global");
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isAssetRequest(HttpServletRequest request)
    {
        String path = request.getRequestURI();
        if (path == null) return false;

        return "GET".equalsIgnoreCase(request.getMethod()) && (path.startsWith("/assets/") || path.startsWith("/app/assets/"));
    }

    private boolean consumeAssetBudget(HttpServletRequest request, HttpServletResponse response, String ip) throws IOException
    {
        TokenBucket bucket = bucketFor(assetIpBuckets, ip, assetIpCapacity, assetIpRefillPerSecond);
        if (!bucket.tryConsume(1.0))
        {
            reject(request, response, bucket.retryAfterSeconds(1.0), "assets");
            return false;
        }
        if (!assetGlobalBucket.tryConsume(1.0))
        {
            bucket.refund(1.0);
            reject(request, response, assetGlobalBucket.retryAfterSeconds(1.0), "assets");
            return false;
        }
        return true;
    }

    private TokenBucket bucketFor(ConcurrentHashMap<String, TokenBucket> buckets, String ip, double capacity, double refillPerSecond)
    {
        maybeEvict();
        return buckets.computeIfAbsent(ip, k -> new TokenBucket(capacity, refillPerSecond));
    }

    private void maybeEvict()
    {
        long now = System.currentTimeMillis();
        long next = nextEvictMillis.get();
        if (now < next) return;
        if (!nextEvictMillis.compareAndSet(next, now + EVICT_INTERVAL_MILLIS)) return;
        ipBuckets.entrySet().removeIf(entry -> now - entry.getValue().lastActivityMillis() > IP_IDLE_TIMEOUT_MILLIS);
        assetIpBuckets.entrySet().removeIf(entry -> now - entry.getValue().lastActivityMillis() > IP_IDLE_TIMEOUT_MILLIS);
        if (ipBuckets.size() > IP_BUCKET_HARD_CAP)
        {
            ipBuckets.clear();
        }
        if (assetIpBuckets.size() > IP_BUCKET_HARD_CAP)
        {
            assetIpBuckets.clear();
        }
    }

    private void reject(HttpServletRequest req, HttpServletResponse resp, long retryAfter, String scope) throws IOException
    {
        resp.setStatus(429);
        resp.setHeader("Retry-After", String.valueOf(retryAfter));
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write("{\"error\":\"Too Many Requests\",\"scope\":\"" + scope + "\",\"retry_after\":" + retryAfter + "}");
        switch (scope)
        {
            case "global" -> rejectedGlobal.incrementAndGet();
            case "per-ip" -> rejectedPerIp.incrementAndGet();
            default -> rejectedAssets.incrementAndGet();
        }
        maybeLogRejections(req);
    }

    private void maybeLogRejections(HttpServletRequest sample)
    {
        long now = System.currentTimeMillis();
        long next = nextRejectionLogMillis.get();
        if (now < next || !nextRejectionLogMillis.compareAndSet(next, now + REJECTION_LOG_INTERVAL_MILLIS)) return;

        long global = rejectedGlobal.getAndSet(0L);
        long perIp = rejectedPerIp.getAndSet(0L);
        long assets = rejectedAssets.getAndSet(0L);
        accessLog.log("Rate limit summary: global={0}, per-ip={1}, assets={2}; sample={3} {4} from {5}",
                global, perIp, assets, sample.getMethod(), sample.getRequestURI(), clientIp(sample));
    }

    private double requestCost(HttpServletRequest request)
    {
        String path = request.getRequestURI();
        if (path == null) return 1.0;
        if (path.equals("/oauth2/login")) return loginCost;
        if (path.equals("/oauth2/callback")) return callbackCost;
        if (path.equals("/api/schematics/list")) return schematicListCost;
        if (path.startsWith("/api/schematics/download/")) return schematicDownloadCost;
        if (path.startsWith("/api/punishments/")) return punishmentsCost;
        if (path.endsWith("/stream") || path.contains("/stream/")) return streamCost;
        if (path.startsWith("/api/list/")) return playerListCost;
        if (path.equals("/api/admin/player-action") || path.equals("/api/schematics/upload")) return staffMutationCost;
        if (path.startsWith("/api/player/") || path.startsWith("/api/indefbans/")) return staffReadCost;
        return 1.0;
    }

    private static double boundedCost(double value, double maximum)
    {
        double positive = Double.isFinite(value) && value > 0.0 ? value : 1.0;
        return Math.min(positive, maximum);
    }

    private static String clientIp(HttpServletRequest request)
    {
        String ip = request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }
}
