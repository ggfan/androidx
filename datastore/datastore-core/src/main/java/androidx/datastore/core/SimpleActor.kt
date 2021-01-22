/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.datastore.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

internal class SimpleActor<T>(
    /**
     * The scope in which to consume messages.
     */
    private val scope: CoroutineScope,
    /**
     * Function that will be called when scope is cancelled.
     */
    onComplete: (Throwable?) -> Unit,
    onUndeliveredElement: (T, Throwable?) -> Unit,
    /**
     * Function that will be called once for each message.
     *
     * Must *not* throw an exception (other than CancellationException if scope is cancelled).
     */
    private val consumeMessage: suspend (T) -> Unit
) {
    private val messageQueue = Channel<T>(capacity = UNLIMITED)

    /**
     * Count of the number of remaining messages to process. This is set to Int.MIN_VALUE when
     * the scope is completed in order to signal that no more messages should be added to the
     * queue. When this is set to Int.MIN_VALUE, there is no guarantee that more messages will be
     * processed.
     */
    private val remainingMessages = AtomicInteger(0)

    init {
        // If the scope doesn't have a job, it won't be cancelled, so we don't need to register a
        // callback.
        scope.coroutineContext[Job]?.invokeOnCompletion { ex ->
            onComplete(ex)
            // TODO(rohitsat): replace this with Channel(onUndelievedElement) when it
            // is fixed: https://github.com/Kotlin/kotlinx.coroutines/issues/2435
            remainingMessages.getAndSet(Int.MIN_VALUE)

            var msg = messageQueue.poll()
            while (msg != null) {
                onUndeliveredElement(msg, ex)
                msg = messageQueue.poll()
            }

            messageQueue.cancel()
        }
    }

    /**
     * Sends a message to a message queue to be processed by [consumeMessage] in [scope].
     *
     * If [offer] completes successfully, the msg *will* be processed either by consumeMessage or
     * onUndeliveredElement. If [offer] throws an exception, the message may or may not be
     * processed.
     */
    fun offer(msg: T) {
        /**
         * Possible states:
         * 1) remainingMessages = 0
         *   All messages have been consumed, so there is no active consumer
         * 2) remainingMessages > 0, no active consumer
         *   One of the senders is responsible for triggering the consumer
         * 3) remainingMessages > 0, active consumer
         *   Consumer will continue to consume until remainingMessages is 0
         * 4) remainingMessages < 0, messageQueue not canceled
         *   The invokeOnCompletion callback is currently running. No active consumers. If a
         *   message is sent during this time it may or may not be processed, so the sender
         *   should throw.
         * 5) remainingMessages < 0, messageQueue canceled.
         *   Calls to messageQueue.offer will fail with an exception and no more messages will be
         *   processed.
         */

        // should never return false bc the channel capacity is unlimited
        check(messageQueue.offer(msg))

        // By checking the count here we can be sure that invokeOnCompletion hasn't  started. If
        // it has started, we don't know if [msg] will be processed, so we should throw an
        // exception.
        val origRemainingMessages = remainingMessages.getAndIncrement()
        if (origRemainingMessages < 0) {
            // InvokeOnCompletion has started, we don't know if [msg] will be processed, so we
            // should throw an exception.
            scope.ensureActive()
            error("scope isn't active so this should've thrown")
        }

        // If the number of remaining messages was 0, there is no active consumer, since it quits
        // consuming once remaining messages hits 0. We must kick off a new consumer.
        if (origRemainingMessages == 0) {
            scope.launch {
                // We shouldn't have started a new consumer unless there are remaining messages...
                check(remainingMessages.get() > 0)

                do {
                    // We don't want to try to consume a new message unless we are still active.
                    // If ensureActive throws, the scope is no longer active, so it doesn't
                    // matter that we have remaining messages.
                    scope.ensureActive()

                    consumeMessage(messageQueue.receive())
                } while (remainingMessages.decrementAndGet() != 0)
            }
        }
    }
}