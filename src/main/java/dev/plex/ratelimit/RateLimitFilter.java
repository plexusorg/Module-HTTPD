package dev.plex.ratelimit;

import dev.plex.HTTPDModule;
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

    private final boolean enabled;
    private final TokenBucket globalBucket;
    private final double ipCapacity;
    private final double ipRefillPerSecond;
    private final ConcurrentHashMap<String, TokenBucket> ipBuckets = new ConcurrentHashMap<>();
    private final AtomicLong nextEvictMillis = new AtomicLong(System.currentTimeMillis() + EVICT_INTERVAL_MILLIS);

    public RateLimitFilter()
    {
        this.enabled = HTTPDModule.moduleConfig.getBoolean("rate-limit.enabled", true);
        double globalCapacity = HTTPDModule.moduleConfig.getDouble("rate-limit.global.capacity", 200.0);
        double globalRate = HTTPDModule.moduleConfig.getDouble("rate-limit.global.per-second", 100.0);
        this.globalBucket = new TokenBucket(globalCapacity, globalRate);
        this.ipCapacity = HTTPDModule.moduleConfig.getDouble("rate-limit.per-ip.capacity", 30.0);
        this.ipRefillPerSecond = HTTPDModule.moduleConfig.getDouble("rate-limit.per-ip.per-second", 10.0);
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

        if (!globalBucket.tryConsume())
        {
            reject(httpRequest, httpResponse, globalBucket.retryAfterSeconds(), "global");
            return;
        }

        String ip = clientIp(httpRequest);
        TokenBucket bucket = bucketFor(ip);
        if (!bucket.tryConsume())
        {
            reject(httpRequest, httpResponse, bucket.retryAfterSeconds(), "per-ip");
            return;
        }

        chain.doFilter(request, response);
    }

    private TokenBucket bucketFor(String ip)
    {
        maybeEvict();
        return ipBuckets.computeIfAbsent(ip, k -> new TokenBucket(ipCapacity, ipRefillPerSecond));
    }

    private void maybeEvict()
    {
        long now = System.currentTimeMillis();
        long next = nextEvictMillis.get();
        if (now < next) return;
        if (!nextEvictMillis.compareAndSet(next, now + EVICT_INTERVAL_MILLIS)) return;
        ipBuckets.entrySet().removeIf(entry -> now - entry.getValue().lastActivityMillis() > IP_IDLE_TIMEOUT_MILLIS);
        if (ipBuckets.size() > IP_BUCKET_HARD_CAP)
        {
            ipBuckets.clear();
        }
    }

    private void reject(HttpServletRequest req, HttpServletResponse resp, long retryAfter, String scope) throws IOException
    {
        resp.setStatus(429);
        resp.setHeader("Retry-After", String.valueOf(retryAfter));
        resp.setContentType("application/json; charset=UTF-8");
        resp.getWriter().write("{\"error\":\"Too Many Requests\",\"scope\":\"" + scope + "\",\"retry_after\":" + retryAfter + "}");
        Log.log("Rate limit hit ({0}) for {1} {2} from {3}, retry after {4}s", scope, req.getMethod(), req.getRequestURI(), clientIp(req), retryAfter);
    }

    private static String clientIp(HttpServletRequest request)
    {
        String ip = request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }
}
