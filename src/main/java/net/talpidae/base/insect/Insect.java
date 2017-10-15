/*
 * Copyright (C) 2017  Jonas Zeiger <jonas.zeiger@talpidae.net>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.talpidae.base.insect;

import com.google.common.base.Strings;
import com.google.common.net.HostAndPort;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.talpidae.base.insect.config.InsectSettings;
import net.talpidae.base.insect.exchange.MessageExchange;
import net.talpidae.base.insect.message.InsectMessage;
import net.talpidae.base.insect.message.InsectMessageFactory;
import net.talpidae.base.insect.message.payload.*;
import net.talpidae.base.insect.message.payload.Shutdown;
import net.talpidae.base.insect.state.InsectState;
import net.talpidae.base.util.network.NetworkUtil;

import java.net.InetSocketAddress;
import java.nio.charset.CharacterCodingException;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;


@Slf4j
public abstract class Insect<S extends InsectSettings> implements CloseableRunnable
{
    protected static final InsectCollection EMPTY_ROUTE = new InsectCollection();

    @Getter(AccessLevel.PROTECTED)
    private final MessageExchange<InsectMessage> exchange;

    private final boolean onlyTrustedRemotes;

    private final boolean remoteOnLocalHost;

    // route -> Set<InsectState> (we use a map to efficiently lookup insects)
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, InsectCollection> routeToInsects = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PROTECTED)
    private final S settings;

    @Getter
    private volatile boolean isRunning = false;

    private Thread executingThread;

    @Getter
    private long pulseDelayNanos = 0L;


    Insect(S settings, boolean onlyTrustedRemotes)
    {
        this.settings = settings;
        this.onlyTrustedRemotes = onlyTrustedRemotes;
        this.exchange = new MessageExchange<>(new InsectMessageFactory(), settings);

        this.remoteOnLocalHost = settings.getRemotes().stream()
                .map(InetSocketAddress::getAddress)
                .anyMatch(NetworkUtil::isLocalAddress);
    }

    @Override
    public void run()
    {
        executingThread = Thread.currentThread();
        try
        {
            synchronized (this)
            {
                isRunning = true;
                notifyAll();
            }

            pulseDelayNanos = TimeUnit.MILLISECONDS.toNanos(getSettings().getPulseDelay());
            routeToInsects.clear();

            // spawn exchange thread
            val exchangeWorker = InsectWorker.start(exchange, exchange.getClass().getSimpleName());
            try
            {
                log.info("MessageExchange running on {}", getSettings().getBindAddress().toString());
                long maximumWaitNanos = pulseDelayNanos;
                while (!Thread.interrupted())
                {
                    final InsectMessage message = exchange.take(maximumWaitNanos);
                    if (message != null)
                    {
                        try
                        {
                            handleMessage(message);
                        }
                        catch (Throwable e)
                        {
                            log.error("error handling message from remote {}: {}: {}", message.getRemoteAddress(), e.getClass().getSimpleName(), e.getMessage());
                        }
                        finally
                        {
                            exchange.recycle(message);
                        }
                    }
                    maximumWaitNanos = handlePulse();
                }
            }
            catch (InterruptedException e)
            {
                log.warn("message loop interrupted, shutting down");
            }
            catch (Exception e)
            {
                log.error("insect shutdown because of critical error", e);
            }
            finally
            {
                // shutdown message exchange
                exchange.close();

                // cause worker thread to finish executing
                if (exchangeWorker.shutdown())
                {
                    log.info("MessageExchange was shut down");
                }
            }
        }
        finally
        {
            isRunning = false;
        }
    }

    /**
     * Override to perform actions based on a pulse at or below the pulse delay.
     *
     * @return Maximum time to wait for next pulse in nanoseconds.
     */
    protected long handlePulse()
    {
        return pulseDelayNanos;
    }

    @Override
    public void close()
    {
        prepareShutdown();

        exchange.close();
        if (executingThread != null)
        {
            executingThread.interrupt();
        }
    }

    /**
     * Override if additional actions are required before shutting down.
     */
    protected void prepareShutdown()
    {
    }

    /**
     * Override to implement additional logic after a mapping message has been handled by the default handler.
     */
    protected void postHandleMapping(InsectState state, Mapping mapping, boolean isNewMapping)
    {

    }

    /**
     * Override to do something when new dependencies are published.
     */
    protected void handleDependenciesChanged(InsectState state)
    {

    }

    /**
     * Override to implement remote shutdown.
     */
    protected void handleShutdown()
    {

    }

    /**
     * Override to implement invalidate (slave needs to send critical information again).
     */
    protected void handleInvalidate()
    {

    }

    /**
     * Override to handle metrics (usually not relayed).
     */
    protected void handleMetrics(Metrics metrics)
    {

    }

    /**
     * Pack and send a message with the specified payload to the given destination.
     */
    protected void addMessage(InetSocketAddress destination, Payload payload)
    {
        val exchange = getExchange();
        try
        {
            val message = exchange.allocate();

            message.setPayload(payload, destination);

            exchange.add(message);
        }
        catch (NoSuchElementException e)
        {
            log.error("failed to propagate mapping to {}, reason: {}", destination.toString(), e.getMessage());
        }
    }

    private boolean checkSenderTrust(InetSocketAddress remote)
    {
        return (remote.getAddress().isLoopbackAddress() && remoteOnLocalHost)
                || getSettings().getRemotes().contains(remote);
    }

    private void handleMessage(InsectMessage message)
    {
        val remote = message.getRemoteAddress();
        val isTrustedServer = remote.getAddress().isLoopbackAddress() || getSettings().getRemotes().contains(remote);
        if (isTrustedServer || !onlyTrustedRemotes)
        {
            try
            {
                val payload = message.getPayload();
                switch (payload.getType())
                {
                    case Mapping.TYPE_MAPPING:
                    {
                        // validate client address (avoid message spoofing)
                        if (isTrustedServer || ((Mapping) payload).isAuthorative(remote))
                        {
                            handleMapping((Mapping) payload);
                        }
                        else
                        {
                            log.warn("possible spoofing: remote {} not authorized to send message: {}", remote, payload);
                        }
                        break;
                    }

                    case Invalidate.TYPE_INVALIDATE:
                    {
                        log.debug("received invalidate message from remote {}", remote);
                        handleInvalidate();
                        break;
                    }

                    case Shutdown.TYPE_SHUTDOWN:
                    {
                        log.debug("received shutdown message from remote {}", remote);
                        handleShutdown();
                        break;
                    }

                    case Metrics.TYPE_METRIC:
                    {
                        handleMetrics((Metrics) payload);
                    }
                }
            }
            catch (CharacterCodingException | IndexOutOfBoundsException e)
            {
                log.warn("received malformed message from: {}", remote);
            }
        }
        else
        {
            log.warn("rejected message from untrusted source: {}", remote);
        }
    }

    private void handleMapping(Mapping mapping)
    {
        val alternatives = routeToInsects.computeIfAbsent(mapping.getRoute(), r -> new InsectCollection());

        // do we have an existing entry for this slave?
        val nextState = alternatives.update(mapping.getSocketAddress(), state ->
        {
            val remoteTimestamp = mapping.getTimestamp();
            val nextStateBuilder = InsectState.builder()
                    .name(mapping.getName());

            final InetSocketAddress socketAddress;
            if (state != null)
            {
                // merge new dependency with those already known
                nextStateBuilder.dependencies(state.getDependencies());

                // out-of-service is sticky
                nextStateBuilder.isOutOfService(state.isOutOfService());

                // do not keep resolving the same host:port combo
                val oldHost = state.getSocketAddress().getHostString();
                val oldPort = state.getSocketAddress().getPort();
                socketAddress = (Objects.equals(mapping.getHost(), oldHost) && oldPort == mapping.getPort())
                        ? state.getSocketAddress()
                        : null;

                // perform timestamp calculation magic
                // the point is to use the heartbeat timestamp from the REMOTE
                // as a measure of latency/CPU load on that service instance
                val remoteEpoch = state.getTimestampEpochRemote();
                val localEpoch = state.getTimestampEpochLocal();
                val adjustedTimestamp = localEpoch + (remoteTimestamp - remoteEpoch);
                val previousAdjustedTimestamp = state.getTimestamp();
                if (adjustedTimestamp < previousAdjustedTimestamp || adjustedTimestamp > (previousAdjustedTimestamp + pulseDelayNanos + (pulseDelayNanos >>> 1)))
                {
                    // missed heartbeat package or service restarted, need to reset epoch
                    nextStateBuilder.newEpoch(remoteTimestamp);
                }
                else
                {
                    nextStateBuilder.timestampEpochLocal(localEpoch)
                            .timestampEpochRemote(remoteEpoch)
                            .timestamp(localEpoch + (remoteTimestamp - remoteEpoch));
                }
            }
            else
            {
                socketAddress = null;

                nextStateBuilder.newEpoch(remoteTimestamp);
            }

            // resolve once
            nextStateBuilder.socketAddress((socketAddress != null) ? socketAddress : new InetSocketAddress(mapping.getHost(), mapping.getPort()));

            val newDependency = mapping.getDependency();
            if (!newDependency.isEmpty())
            {
                nextStateBuilder.dependency(newDependency);
            }

            // InsectState is key and value at the same time
            return nextStateBuilder.build();
        });

        // let descendants add more actions
        val isNewMapping = mapping.getTimestamp() == nextState.getTimestampEpochRemote();
        postHandleMapping(nextState, mapping, isNewMapping);

        if (isNewMapping || !Strings.isNullOrEmpty(mapping.getDependency()))
        {
            // inform about changed dependencies
            handleDependenciesChanged(nextState);
        }
    }


    @FunctionalInterface
    protected interface InsectStateUpdaterFunction extends Function<InsectState, InsectState>
    {

    }


    protected static class InsectCollection
    {
        private static final InsectState[] EMPTY = new InsectState[0];

        private static final int ACTION_REPLACE = 0;

        private static final int ACTION_ADD = 1;

        private static final int ACTION_REMOVE = -1;

        private final AtomicReference<InsectState[]> insects = new AtomicReference<>(EMPTY);


        private static int indexOf(InsectState[] array, InetSocketAddress socketAddress)
        {
            for (int i = 0; i < array.length; ++i)
            {
                val otherAddress = array[i].getSocketAddress();
                if (socketAddress.getPort() == otherAddress.getPort())
                {
                    val hostString = socketAddress.getHostString();
                    val otherHostString = otherAddress.getHostString();
                    if (Objects.equals(hostString, otherHostString))
                    {
                        return i;
                    }
                }
            }

            return -1;
        }


        public InsectState[] getInsects()
        {
            return insects.get();
        }


        public void clear()
        {
            insects.set(EMPTY);
        }


        /**
         * Updates this collection of insects.
         *
         * @param key             The address of the insect to update.
         * @param updaterFunction Function that will create a new immutable InsectState value, based on the previous value.
         * @return The new value or null if none exists (the previous item has been removed or didn't exist in the first place).
         */
        InsectState update(InetSocketAddress key, InsectStateUpdaterFunction updaterFunction)
        {
            InsectState[] existing;
            InsectState[] next;
            InsectState nextState;
            do
            {
                existing = insects.get();  // acquire

                val index = indexOf(existing, key);
                nextState = updaterFunction.apply(index >= 0 ? existing[index] : null);

                final int action;
                if (index >= 0)
                {
                    if (nextState != null)
                    {
                        action = ACTION_REPLACE;
                    }
                    else
                    {
                        action = ACTION_REMOVE;
                    }
                }
                else if (nextState != null)
                {
                    // add
                    action = ACTION_ADD;
                }
                else
                {
                    // no-op
                    return null;
                }

                val nextLength = existing.length + action;
                if (nextLength > 0)
                {
                    next = new InsectState[nextLength];
                    System.arraycopy(existing, 0, next, 0, Math.min(existing.length, nextLength));

                    next[action == ACTION_ADD ? existing.length : index] = (action != ACTION_REMOVE) ? nextState : existing[existing.length - 1];
                }
                else
                {
                    next = EMPTY;
                }

                // sort by timestamp descending (to allow faster lookup by slave)
                Arrays.sort(next, (s1, s2) -> -Long.compare(s1.getTimestamp(), s2.getTimestamp()));
            }
            while (!insects.compareAndSet(existing, next));

            return nextState;
        }
    }
}