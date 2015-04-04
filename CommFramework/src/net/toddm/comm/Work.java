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

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.toddm.comm.Priority.StartingPriority;

/**
 * @author Todd S. Murchison
 */
public class Work implements Future<Response> {

	/** An set of possible states that work can be in */
	public enum Status {
		/** The {@link Work} instance has been created, but has not started processing yet */
		CREATED,
		/** The {@link Work} instance is waiting in the pending work queue */
		WAITING,
		/** The {@link Work} instance is actively being processed */
		RUNNING,
		/** There is a pending a retry attempt for the {@link Work} instance */
		RETRYING,
		/** The {@link Work} instance has been cancelled */
		CANCELLED,
		/** The {@link Work} instance has finished without being cancelled */
		COMPLETED
	}

	private Status _state = Status.CREATED;
	private volatile FutureTask<Response> _futureTask = null;
	private final Priority _priority;
	private final Request _request;
	private Response _response = null;

	protected Work(
			URI uri, 
			Request.RequestMethod method, 
			byte[] postData, 
			Map<String, String> headers, 
			StartingPriority priority, 
			boolean cachingAllowed)
	{

		// Validate parameters
		if(uri == null) { throw(new IllegalArgumentException("'uri' can not be NULL")); }
		if(method == null) { throw(new IllegalArgumentException("'method' can not be NULL")); }
		if(priority == null) { throw(new IllegalArgumentException("'priority' can not be NULL")); }
		if((postData != null) && (!Request.RequestMethod.POST.equals(method))) {
			throw(new IllegalArgumentException("'method' must be 'POST' when 'postData' is provided"));
		}

		// Set our data members
		this._state = Status.CREATED;
		this._request = new Request(uri, method, postData, headers, cachingAllowed);
		this._priority = new Priority(this, priority);
	}

	/** Returns the {@link Request} instance associated with this {@link Work} instance. */
	public Request getRequest() { return(this._request); }

	/** Returns the {@link Priority} of this {@link Work} instance. */
	public Priority getPriority() { return(this._priority); }

	/** Returns the current state of this {@link Work} instance */
	public Status getState() { return(this._state); }

	/** Sets the current state of this {@link Work} instance */
	protected void setState(Status state) {
		if(state == null) { throw(new IllegalArgumentException("'state' can not be NULL")); }
		this._state = state;
	}

	/** Returns the {@link Response} that resulted from processing this {@link Work} instance, or NULL if one has not been set. */
	public Response getResponse() { return(this._response); }

	/** Sets the response that resulted from processing this work. NULL is a valid value. */
	public void setResponse(Response response) { this._response = response; }

	/**
	 * Returns the {@link FutureTask} instance that defines the work to do for this  for this {@link Work} 
	 * instance, or NULL if there isn't one. The {@link FutureTask} can be used to check the state of the 
	 * work ({@link FutureTask#isDone()}, etc.), block on the work ({@link FutureTask#get()}), and can be 
	 * submitted to and Executor to begin processing the work.
	 */
	protected FutureTask<Response> getFutureTask() { return(this._futureTask); }

	/**
	 * Sets the {@link FutureTask} for this {@link Work} instance.
	 * See {@link Work#getFutureTask()} for more details.
	 */
	protected void setFutureTask(FutureTask<Response> futureTask) {
		if(futureTask == null) { throw(new IllegalArgumentException("'futureTask' can not be NULL")); }
		this._futureTask = futureTask;
	}

	/**
	 * Returns the ID of this {@link Work} instance. The ID of the underlying 
	 * {@link Request} is used. See {@link Request#getId()} for details.
	 */
	public int getId() {
		return(this._request.getId());
	}

	/**
	 * Returns the hash code of this {@link Work} instance. The hash code of the underlying 
	 * {@link Request} is used. See {@link Request#hashCode()} for details.
	 */
	@Override
	public int hashCode() {
		return(this._request.hashCode());
	}

	/**
	 * Returns TRUE if this {@link Work} instance is equivalent to the given {@link Work} instance. Equality 
	 * of the underlying {@link Request} instances is used. See {@link Request#equals(Object)} for details.
	 */
	@Override
	public boolean equals(Object work) {
		if(work == null) { throw(new IllegalArgumentException("'work' can not be NULL")); }
		if(!(work instanceof Work)) { return(false); }
		return(this._request.equals(((Work)work)._request));
	}

	//--------------------------------------------------------------------------
	// Future interface for wrapping underlying FutureTask

	/** {@inheritDoc} */
	@Override
	public boolean cancel(boolean interruptAllowed) {
		// TODO: Cancel may need to be done via the CommManager...
		return(this._futureTask.cancel(interruptAllowed));
	}

	/** {@inheritDoc} */
	@Override
	public Response get() throws InterruptedException, ExecutionException {
		return(this._futureTask.get());
	}

	/** {@inheritDoc} */
	@Override
	public Response get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return(this._futureTask.get(timeout, unit));
	}

	/** {@inheritDoc} */
	@Override
	public boolean isDone() {
		return((this._state == Status.CANCELLED) || (this._state == Status.COMPLETED));
	}

	/** {@inheritDoc} */
	@Override
	public boolean isCancelled() {
		return(this._state == Status.CANCELLED);
	}

}
