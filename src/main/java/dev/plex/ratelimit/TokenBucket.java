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
        if (!Double.isFinite(capacity) || capacity < 1.0)
        {
            throw new IllegalArgumentException("capacity must be finite and at least 1");
        }
        if (!Double.isFinite(refillPerSecond) || refillPerSecond <= 0.0)
        {
            throw new IllegalArgumentException("refillPerSecond must be finite and positive");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
        this.lastActivityMillis = System.currentTimeMillis();
    }

    public synchronized boolean tryConsume(double amount)
    {
        if (!Double.isFinite(amount) || amount <= 0.0 || amount > capacity)
        {
            return false;
        }
        refill();
        lastActivityMillis = System.currentTimeMillis();
        if (tokens >= amount)
        {
            tokens -= amount;
            return true;
        }
        return false;
    }

    public synchronized long retryAfterSeconds(double amount)
    {
        refill();
        double deficit = amount - tokens;
        if (deficit <= 0) return 0;
        return Math.max(1L, (long) Math.ceil(deficit / refillPerSecond));
    }

    public synchronized void refund(double amount)
    {
        if (!Double.isFinite(amount) || amount <= 0.0) return;
        refill();
        tokens = Math.min(capacity, tokens + amount);
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
