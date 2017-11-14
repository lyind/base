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

import net.talpidae.base.insect.config.InsectSettings;
import net.talpidae.base.insect.exchange.MessageExchange;
import net.talpidae.base.insect.exchange.MessageQueueControl;
import net.talpidae.base.insect.message.InsectMessage;
import net.talpidae.base.insect.message.payload.Invalidate;
import net.talpidae.base.insect.message.payload.Mapping;
import net.talpidae.base.insect.message.payload.Metrics;
import net.talpidae.base.insect.message.payload.Payload;
import net.talpidae.base.insect.message.payload.Shutdown;
import net.talpidae.base.insect.state.InsectState;
import net.talpidae.base.util.network.NetworkUtil;
import net.talpidae.base.util.random.AtomicXorShiftRandom;

import java.net.InetSocketAddress;
import java.nio.charset.CharacterCodingException;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;


@Slf4j
public abstract class Insect<S extends InsectSettings> extends MessageExchange<InsectMessage> implements CloseableRunnable
{
    private static final InsectCollection EMPTY_ROUTE = new InsectCollection(0L);

    private final boolean onlyTrustedRemotes;

    private final boolean remoteOnLocalHost;

    // route -> Set<InsectState> (we use a map to efficiently lookup insects)
    @Getter(AccessLevel.PROTECTED)
    private final Map<String, InsectCollection> routeToInsects = new ConcurrentHashMap<>();

    @Getter(AccessLevel.PROTECTED)
    private final S settings;

    private final long pulseDelayCutoff;

    private final Deque<OutBoundMessage> outBoundMessages = new ConcurrentLinkedDeque<>();

    @Getter(AccessLevel.PROTECTED)
    protected AtomicXorShiftRandom random = new AtomicXorShiftRandom();

    @Getter
    private long pulseDelayMillies = 0L;

    /**
     * Highest sane timestamp value encountered so far.
     * <p>
     * This timestamp is used for automatic self-preservation.
     */
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private long maximumTimestamp;


    Insect(S settings, boolean onlyTrustedRemotes)
    {
        super(InsectMessage::new, settings);

        this.settings = settings;
        this.onlyTrustedRemotes = onlyTrustedRemotes;

        this.remoteOnLocalHost = settings.getRemotes().stream()
                .map(InetSocketAddress::getAddress)
                .anyMatch(NetworkUtil::isLocalAddress);

        this.pulseDelayCutoff = TimeUnit.MILLISECONDS.toNanos(settings.getPulseDelay() + (settings.getPulseDelay() / 2));
    }

    /**
     * Get an empty route.
     */
    protected InsectCollection emptyRoute()
    {
        return EMPTY_ROUTE;
    }


    @Override
    public void run()
    {
        pulseDelayMillies = getSettings().getPulseDelay();
        routeToInsects.clear();
        outBoundMessages.clear();

        try
        {
            // run MessageExchange loop
            super.run();
        }
        catch (Exception e)
        {
            log.error("insect shutdown because of critical error", e);
        }
    }


    /**
     * Override to perform actions based on a pulse at or below the pulse delay.
     *
     * @param nowNanos The current monotonic clock value as returned by System.nanoTime().
     * @return Maximum time to wait for next pulse in nanoseconds.
     */
    protected long handlePulse(long nowNanos)
    {
        // find and remove all timed out instances
        val deadlineNanos = maximumTimestamp - (pulseDelayCutoff * 2);
        val limitRemovedCount = Math.max(1, routeToInsects.size() / 5);  // remove max 20% of instances in one go

        int removed = 0;
        for (val alternatives : routeToInsects.values())
        {
            removed += alternatives.truncateOlderThan(deadlineNanos, this::handleTimeout);
            if (removed >= limitRemovedCount)
                break;
        }

        // reduce batch size a little by splitting up work over time
        return pulseDelayMillies / 4;
    }


    @Override
    public void close()
    {
        prepareShutdown();

        super.close();
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
    protected void postHandleMapping(InsectState state, Mapping mapping, boolean isNewMapping, boolean isDependencyMapping)
    {

    }


    /**
     * Override to do something when new dependencies are published.
     */
    protected void handleDependenciesChanged(InsectState state)
    {

    }


    /**
     * Override to do something when an insect timed out and got removed from the pool.
     */
    protected void handleTimeout(InsectState timedOutState)
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
        val queueControl = getQueueControl();
        if (queueControl != null)
        {
            // general case:
            // message was created from within processMessages() context (same thread)
            ((InsectMessage) queueControl.addOutbound(destination)).setPayload(payload);
        }
        else
        {
            // rare case:
            // queue outbound message from other thread before construction in processMessages()
            outBoundMessages.offerLast(new OutBoundMessage(destination, payload));

            // schedule call to processMessages() which actually sends our message
            wakeup();
        }
    }


    private boolean checkSenderTrust(InetSocketAddress remote)
    {
        return (remote.getAddress().isLoopbackAddress() && remoteOnLocalHost)
                || getSettings().getRemotes().contains(remote);
    }


    private void handleMessage(long nowNanos, InsectMessage message)
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
                            handleMapping(nowNanos, (Mapping) payload);
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

    private void handleMapping(long nowNanos, Mapping mapping)
    {
        val alternatives = routeToInsects.computeIfAbsent(mapping.getRoute(), r -> new InsectCollection(pulseDelayCutoff));

        // do we have an existing entry for this slave?
        val nextState = alternatives.compute(mapping.getSocketAddress(), state ->
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
                val pulseDelayNanos = TimeUnit.MILLISECONDS.toNanos(getPulseDelayMillies());
                if (adjustedTimestamp < previousAdjustedTimestamp || adjustedTimestamp > (previousAdjustedTimestamp + pulseDelayNanos + (pulseDelayNanos >>> 1)))
                {
                    // missed heartbeat package or service restarted, need to reset epoch
                    nextStateBuilder.newEpoch(nowNanos, remoteTimestamp);
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

                nextStateBuilder.newEpoch(nowNanos, remoteTimestamp);
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
        final boolean isNewMapping;
        if (nextState != null)
        {
            maximumTimestamp = nextState.getTimestamp();
            isNewMapping = mapping.getTimestamp() == nextState.getTimestampEpochRemote();
        }
        else
        {
            isNewMapping = false;
        }

        val isDependencyMapping = !Strings.isNullOrEmpty(mapping.getDependency());
        postHandleMapping(nextState, mapping, isNewMapping, isDependencyMapping);

        if (isNewMapping || isDependencyMapping)
        {
            // inform about changed dependencies
            handleDependenciesChanged(nextState);
        }
    }

    /**
     * Handle inbound and send outbound messages.
     */
    @Override
    protected long processMessages(MessageQueueControl<InsectMessage> control)
    {
        // add already queued up messages
        OutBoundMessage outBoundMessage;
        while ((outBoundMessage = outBoundMessages.poll()) != null)
        {
            addMessage(outBoundMessage.getDestination(), outBoundMessage.getPayload());
        }

        val nowNanos = System.nanoTime();
        InsectMessage inBoundMessage;
        while ((inBoundMessage = control.pollInbound()) != null)
        {
            try
            {
                handleMessage(nowNanos, inBoundMessage);
            }
            catch (Throwable e)
            {
                log.error("error handling message from remote {}: {}: {}", inBoundMessage.getRemoteAddress(), e.getClass().getSimpleName(), e.getMessage());
            }
        }

        // call handlePulse() as soon as the last queued inbound message has been processed
        return handlePulse(nowNanos);
    }


    @FunctionalInterface
    protected interface InsectStateUpdateFunction extends Function<InsectState, InsectState>
    {

    }


    /**
     * Holder for queued up outbound message content.
     */
    @Value
    private static class OutBoundMessage
    {
        private final InetSocketAddress destination;

        private final Payload payload;
    }


    private static final class InsectPool
    {
        private static final InsectPool EMPTY = new InsectPool(new InsectState[0], 0);

        private final InsectState[] insects;

        private final List<InsectState> allInsects;

        private final List<InsectState> activeInsects;


        private InsectPool(InsectState[] insects, int activeEndIndex)
        {
            this.insects = insects;
            this.allInsects = Arrays.asList(insects);
            this.activeInsects = allInsects.subList(0, activeEndIndex);
        }
    }


    protected static final class InsectCollection
    {
        private static final int ACTION_REPLACE = 0;

        private static final int ACTION_ADD = 1;

        private static final int ACTION_REMOVE = -1;

        private final AtomicReference<InsectPool> insects = new AtomicReference<>(InsectPool.EMPTY);

        private final long pulseDelayCutoff;


        private InsectCollection(long pulseDelayCutoff)
        {
            this.pulseDelayCutoff = pulseDelayCutoff;
        }


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


        public List<InsectState> getActive()
        {
            return insects.get().activeInsects;
        }


        public List<InsectState> getAll()
        {
            return insects.get().allInsects;
        }


        void clear()
        {
            insects.set(InsectPool.EMPTY);
        }


        /**
         * Truncate the collection, removing all insects whose timestamp is older than the specified deadline.
         *
         * @return Number of timed-out instances that have been removed.
         */
        int truncateOlderThan(long deadlineNanos, Consumer<InsectState> removedConsumer)
        {
            InsectPool pool;
            InsectPool nextPool;

            int count = 0;
            do
            {
                pool = insects.get();  // acquire
                val activeEndIndex = pool.activeInsects.size();
                val existing = pool.insects;

                int i = existing.length - 1;
                while (i >= 0 && existing[i].getTimestamp() < deadlineNanos)
                {
                    --i;
                }

                val nextLength = i + 1;
                count = existing.length - nextLength;
                if (nextLength == existing.length)
                {
                    // nothing to do
                    break;
                }
                else if (nextLength > 0)
                {
                    val next = new InsectState[nextLength];
                    System.arraycopy(existing, 0, next, 0, Math.min(existing.length, nextLength));

                    nextPool = new InsectPool(next, activeEndIndex);
                }
                else
                {
                    nextPool = InsectPool.EMPTY;
                }
            }
            while (!insects.compareAndSet(pool, nextPool));

            // notify about removed instances
            for (int i = pool.insects.length - 1; i >= pool.insects.length - count; --i)
            {
                removedConsumer.accept(pool.insects[i]);
            }

            return count;
        }


        /**
         * Updates this collection of insects.
         *
         * @param key            The address of the insect to compute.
         * @param updateFunction Function that will create a new immutable InsectState value, based on the previous value.
         * @return The new value or null if none exists (the previous item has been removed or didn't exist in the first place).
         */
        InsectState compute(InetSocketAddress key, InsectStateUpdateFunction updateFunction)
        {
            InsectPool pool;
            InsectPool nextPool;
            InsectState nextState;
            do
            {
                pool = insects.get();  // acquire

                val existing = pool.insects;
                val index = indexOf(existing, key);
                nextState = updateFunction.apply(index >= 0 ? existing[index] : null);

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
                    val next = new InsectState[nextLength];
                    System.arraycopy(existing, 0, next, 0, Math.min(existing.length, nextLength));
                    next[action == ACTION_ADD ? existing.length : index] = (action != ACTION_REMOVE) ? nextState : existing[existing.length - 1];

                    // copy of last array has been updated now

                    // sort by timestamp descending (to allow faster lookup by slave)
                    Arrays.sort(next, (s1, s2) -> -Long.compare(s1.getTimestamp(), s2.getTimestamp()));

                    // find first timed-out insect
                    val timestampCutOff = next[0].getTimestamp() - pulseDelayCutoff;
                    int i;
                    for (i = 1; i < next.length; ++i)
                    {
                        if (next[i].getTimestamp() < timestampCutOff)
                        {
                            // cutoff reached
                            break;
                        }
                    }

                    nextPool = new InsectPool(next, i);
                }
                else
                {
                    nextPool = InsectPool.EMPTY;
                }
            }
            while (!insects.compareAndSet(pool, nextPool));

            return nextState;
        }
    }
}