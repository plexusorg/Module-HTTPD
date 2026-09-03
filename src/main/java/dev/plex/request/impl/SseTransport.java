package dev.plex.request.impl;

import jakarta.servlet.AsyncContext;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/** Owns SSE connection accounting, latest-frame backpressure, writing, and disconnect cleanup. */
final class SseTransport<K, M>
{
    private final Map<K, Set<Subscriber<M>>> subscribers = new ConcurrentHashMap<>();
    private final AtomicInteger subscriberCount = new AtomicInteger();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ExecutorService writers;
    private final int maxConnections;
    private final Runnable firstSubscriber;
    private final Consumer<K> emptyKey;

    SseTransport(int maxConnections, int threads, String threadName, Runnable firstSubscriber, Consumer<K> emptyKey)
    {
        this.maxConnections = maxConnections;
        this.firstSubscriber = firstSubscriber;
        this.emptyKey = emptyKey;
        writers = Executors.newFixedThreadPool(Math.max(1, threads), task ->
        {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    boolean atCapacity()
    {
        return !active.get() || subscriberCount.get() >= maxConnections;
    }

    boolean add(K key, AsyncContext context, PrintWriter writer, M metadata)
    {
        if (!active.get()) return false;
        int reservedCount = reserveConnection();
        if (reservedCount < 0) return false;
        Subscriber<M> subscriber = new Subscriber<>(context, writer, metadata);
        Set<Subscriber<M>> keyed = subscribers.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet());
        if (!keyed.add(subscriber))
        {
            subscriberCount.decrementAndGet();
            return false;
        }
        if (!active.get())
        {
            drop(key, keyed, subscriber, false);
            return false;
        }
        if (reservedCount == 1) firstSubscriber.run();
        return true;
    }

    void remove(K key, AsyncContext context)
    {
        Set<Subscriber<M>> keyed = subscribers.get(key);
        if (keyed == null) return;
        for (Subscriber<M> subscriber : keyed)
        {
            if (subscriber.context == context)
            {
                drop(key, keyed, subscriber, false);
                return;
            }
        }
    }

    void publish(K key, Function<M, String> frame)
    {
        Set<Subscriber<M>> keyed = subscribers.get(key);
        if (keyed == null) return;
        for (Subscriber<M> subscriber : keyed)
        {
            subscriber.pendingFrame.set(frame.apply(subscriber.metadata));
            if (subscriber.writing.compareAndSet(false, true)) submit(key, keyed, subscriber);
        }
    }

    boolean hasSubscribers()
    {
        return subscriberCount.get() != 0;
    }

    boolean hasSubscribers(K key)
    {
        Set<Subscriber<M>> keyed = subscribers.get(key);
        return keyed != null && !keyed.isEmpty();
    }

    Set<K> keys()
    {
        return Set.copyOf(subscribers.keySet());
    }

    void shutdown()
    {
        if (!active.compareAndSet(true, false)) return;
        writers.shutdownNow();
        subscribers.values().stream().flatMap(Set::stream).forEach(subscriber -> complete(subscriber.context));
        subscribers.clear();
        subscriberCount.set(0);
    }

    private void submit(K key, Set<Subscriber<M>> keyed, Subscriber<M> subscriber)
    {
        try
        {
            writers.execute(() -> drain(key, keyed, subscriber));
        }
        catch (RejectedExecutionException ignored)
        {
            subscriber.writing.set(false);
            drop(key, keyed, subscriber, true);
        }
    }

    private void drain(K key, Set<Subscriber<M>> keyed, Subscriber<M> subscriber)
    {
        try
        {
            String frame;
            while (keyed.contains(subscriber) && (frame = subscriber.pendingFrame.getAndSet(null)) != null)
            {
                subscriber.writer.write(frame);
                subscriber.writer.flush();
                if (subscriber.writer.checkError())
                {
                    drop(key, keyed, subscriber, true);
                    return;
                }
            }
        }
        finally
        {
            subscriber.writing.set(false);
            if (keyed.contains(subscriber) && subscriber.pendingFrame.get() != null
                    && subscriber.writing.compareAndSet(false, true))
            {
                submit(key, keyed, subscriber);
            }
        }
    }

    private void drop(K key, Set<Subscriber<M>> keyed, Subscriber<M> subscriber, boolean complete)
    {
        subscriber.pendingFrame.set(null);
        if (keyed.remove(subscriber))
        {
            subscriberCount.decrementAndGet();
            if (keyed.isEmpty() && subscribers.remove(key, keyed)) emptyKey.accept(key);
        }
        if (complete) complete(subscriber.context);
    }

    private int reserveConnection()
    {
        int current;
        do
        {
            current = subscriberCount.get();
            if (!active.get() || current >= maxConnections) return -1;
        }
        while (!subscriberCount.compareAndSet(current, current + 1));
        return current + 1;
    }

    private static void complete(AsyncContext context)
    {
        try
        {
            context.complete();
        }
        catch (IllegalStateException ignored)
        {
        }
    }

    private static final class Subscriber<M>
    {
        private final AsyncContext context;
        private final PrintWriter writer;
        private final M metadata;
        private final AtomicReference<String> pendingFrame = new AtomicReference<>();
        private final AtomicBoolean writing = new AtomicBoolean();

        private Subscriber(AsyncContext context, PrintWriter writer, M metadata)
        {
            this.context = context;
            this.writer = writer;
            this.metadata = metadata;
        }
    }
}
