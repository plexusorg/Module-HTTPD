package dev.plex.ratelimit;

public class TokenBucket
{
    private final double capacity;
    private final double refillPerSecond;
    private double tokens;
    private long lastRefillNanos;
    private volatile long lastActivityMillis;

    public TokenBucket(double capacity, double refillPerSecond)
    {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
        this.lastActivityMillis = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume()
    {
        refill();
        lastActivityMillis = System.currentTimeMillis();
        if (tokens >= 1.0)
        {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    public synchronized long retryAfterSeconds()
    {
        refill();
        double deficit = 1.0 - tokens;
        if (deficit <= 0) return 0;
        return Math.max(1L, (long) Math.ceil(deficit / refillPerSecond));
    }

    public long lastActivityMillis()
    {
        return lastActivityMillis;
    }

    private void refill()
    {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
        lastRefillNanos = now;
    }
}
