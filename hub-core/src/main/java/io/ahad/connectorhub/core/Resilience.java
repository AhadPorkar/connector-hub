package io.ahad.connectorhub.core;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
/** Bounded single-node rate limits, circuit breakers and retry policy. Never retries non-read operations. */
public final class Resilience {
    private final Map<String,Circuit> circuits=new ConcurrentHashMap<>();
    private final Map<String,Window> windows=new ConcurrentHashMap<>();
    private final Clock clock;
    private final long cooldownMillis;
    private final int threshold;
    public Resilience() {
        this(Clock.systemUTC(),30_000,5);
    }
    public Resilience(Clock clock,long cooldownMillis,int threshold) {
        this.clock=clock;
        this.cooldownMillis=cooldownMillis;
        this.threshold=threshold;
    }
    public void rateLimit(String key,int maximum) {
        if(maximum<1||maximum>10_000)throw HubException.invalid();
        if(windows.size()>10_000)windows.entrySet().removeIf(e->clock.millis()-e.getValue().last>120_000);
        if(windows.size()>20_000)throw new HubException("CAPACITY",503);
        Window w=windows.computeIfAbsent(key,k->new Window());
        synchronized(w) {
            long now=clock.millis();
            w.times.removeIf(t->t<=now-60_000);
            w.last=now;
            if(w.times.size()>=maximum)throw new HubException("RATE_LIMIT",429);
            w.times.addLast(now);
        }
    }
    public <T>T execute(String key,boolean retrySafe,int maxAttempts,Supplier<T> call) {
        if(maxAttempts<1||maxAttempts>4)throw HubException.invalid();
        if(!retrySafe)maxAttempts=1;
        Circuit c=circuits.computeIfAbsent(key,k->new Circuit());
        boolean probe=false;
        synchronized(c) {
            if(c.openUntil>clock.millis())throw new HubException("CIRCUIT_OPEN",503);
            if(c.openUntil>0) {
                if(!c.probe.compareAndSet(false,true))throw new HubException("CIRCUIT_OPEN",503);
                probe=true;
            }
        }
        try {
            for(int attempt=1;; attempt++) {
                try {
                    T result=call.get();
                    synchronized(c) {
                        c.failures=0;
                        c.openUntil=0;
                    }
                    return result;
                }
                catch(HubException e) {
                    if(!e.transientFailure())throw e;
                    if(attempt>=maxAttempts) {
                        synchronized(c) {
                            if(++c.failures>=threshold)c.openUntil=clock.millis()+cooldownMillis;
                        }
                        throw e;
                    }
                    try {
                        Thread.sleep(Math.min(1_000,50L*(1L<<(attempt-1)))+ThreadLocalRandom.current().nextLong(26));
                    }
                    catch(InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new HubException("INTERRUPTED",503);
                    }
                }
            }
        }
        finally {
            if(probe)c.probe.set(false);
        }
    }
    public void forget(String prefix) {
        circuits.keySet().removeIf(k->k.startsWith(prefix));
        windows.keySet().removeIf(k->k.startsWith(prefix));
    }
    private static final class Circuit {
        int failures;
        long openUntil;
        AtomicBoolean probe=new AtomicBoolean();
    }
    private static final class Window {
        final Deque<Long> times=new ArrayDeque<>();
        long last;
    }
}
