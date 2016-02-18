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

/**
 * An interface implemented by the Comm Framework to publicly express units of work that are being managed by the framework.
 * <p>
 * @author Todd S. Murchison
 */
public interface Work extends SubmittableWork {

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

	/**
	 * Returns an {@link Exception} instance that is relevant to this {@link Work} instance, or <b>null</b> if there is none.
	 * If this work fails without a {@link Response} this can be used to understand why (socket timeout, etc.).
	 */
	public Exception getException();

	/**
	 * Returns true if this work completed. Completion may be due to normal termination, an 
	 * exception, or cancellation. In all of these cases, this method will return true.
	 */
	public boolean isDone();

	/** Returns true if this work was cancelled before it completed normally. */
	public boolean isCancelled();

}
