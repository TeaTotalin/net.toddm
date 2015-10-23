// ***************************************************************************
// *  Copyright 2015 Todd S. Murchison
// *
// *  Licensed under the Apache License, Version 2.0 (the "License");
// *  you may not use this file except in compliance with the License.
// *  You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// *  Unless required by applicable law or agreed to in writing, software
// *  distributed under the License is distributed on an "AS IS" BASIS,
// *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// *  See the License for the specific language governing permissions and
// *  limitations under the License.
// ***************************************************************************
package net.toddm.comm;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.toddm.cache.CachePriority;

/**
 * An interface implemented by the Comm Framework to publicly express units of work.
 * <p>
 * @author Todd S. Murchison
 */
public interface Work {

	/**
	 * Returns the ID of this {@link Work} instance. The ID of the underlying 
	 * {@link Request} is used. See {@link Request#getId()} for details.
	 */
	public int getId();

	/**
	 * Waits if necessary for the computation to complete, and then retrieves its result.
	 * <p>
	 * @return The computed result.
	 * @throws InterruptedException If the current thread was interrupted while waiting.
	 * @throws ExecutionException If the computation threw an exception.
	 */
	public Response get() throws InterruptedException, ExecutionException;
	
	/**
	 * Waits if necessary for the computation to complete, and then retrieves its result.
	 * The given timeout is a hint and may only be approximately honored depending on implementation.
	 * <p>
	 * @param timeout A timeout hint that may only be approximately honored depending on implementation.
	 * @param unit The time unit of the timeout argument.
	 * @return The computed result.
	 * @throws InterruptedException If the current thread was interrupted while waiting.
	 * @throws ExecutionException If the computation threw an exception.
	 * @throws TimeoutException If the wait timed out.
	 */
	public Response get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

	/** Returns the {@link Request} instance associated with this {@link Work} instance. */
	public Request getRequest();

	/** Returns the request {@link Priority} of this {@link Work} instance. */
	public Priority getRequestPriority();

	/** Returns the caching {@link CachePriority} of this {@link Work} instance, or <b>null<b> if it had no priority set. */
	public CachePriority getCachingPriority();

	/** Returns the caching {@link CacheBehavior} of this {@link Work} instance, or <b>null<b> if it had no behavior set. */
	public CacheBehavior getCachingBehavior();

	/**
	 * Attempts to cancel execution of this task. This attempt will fail if the task has already completed, has already been 
	 * cancelled, or could not be cancelled for some other reason. If successful, and this task has not started when cancel is 
	 * called, this task should never run. If the task has already started, then the mayInterruptIfRunning parameter determines 
	 * whether the thread executing this task should be interrupted in an attempt to stop the task.
	 * <p>
	 * After this method returns, subsequent calls to isDone will always return true. Subsequent calls to isCancelled will 
	 * always return true if this method returned true.
	 * <p>
	 * @param interruptAllowed True if the thread executing this task should be interrupted; otherwise, in-progress tasks are allowed to complete
	 * @return False if the task could not be cancelled, typically because it has already completed normally; true otherwise
	 */
	public boolean cancel(boolean interruptAllowed);

	/**
	 * Returns true if this work completed. Completion may be due to normal termination, an 
	 * exception, or cancellation. In all of these cases, this method will return true.
	 */
	public boolean isDone();

	/** Returns true if this work was cancelled before it completed normally. */
	public boolean isCancelled();

}
