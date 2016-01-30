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

	/** An set of possible states that work can be in */
	public enum Status {
		/** The {@link Work} instance has been created, but has not started processing yet */
		CREATED,
		/** The {@link Work} instance is waiting in the pending work queue */
		WAITING,
		/** The {@link Work} instance is actively being processed */
		RUNNING,
		/** There is a pending retry attempt for the {@link Work} instance */
		RETRYING,
		/** There is a pending redirect attempt for the {@link Work} instance */
		REDIRECTING,
		/** The {@link Work} instance has been cancelled */
		CANCELLED,
		/** The {@link Work} instance has finished without being cancelled */
		COMPLETED
	}

	/** Returns the current state of this {@link Work} instance */
	public Status getState();

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
	 * Returns true if this work completed. Completion may be due to normal termination, an 
	 * exception, or cancellation. In all of these cases, this method will return true.
	 */
	public boolean isDone();

	/** Returns true if this work was cancelled before it completed normally. */
	public boolean isCancelled();

}
