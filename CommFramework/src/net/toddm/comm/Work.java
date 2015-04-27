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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.toddm.cache.CacheEntry;
import net.toddm.comm.Priority.StartingPriority;

/**
 * Represents a unit of work being managed by the Comm Framework.  This class is the primary way that client code interacts 
 * with work it has submitted to the Comm Framework, including blocking on that work as needed. These units of work represent 
 * everything needed by the Comm Framework to reach a remote end-point and elicit a response.
 * 
 * @author Todd S. Murchison
 */
public class Work implements Future<Response> {

	private static final Logger _Logger = LoggerFactory.getLogger(Work.class.getSimpleName());

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

	private final Request _request;
	private final Priority _priority;
	private final ConcurrentLinkedDeque<FutureTask<Response>> _futureTasks = new ConcurrentLinkedDeque<FutureTask<Response>>();

	private Status _state = Status.CREATED;
	private Response _response = null;
	private CacheEntry _cachedResponse = null;
	private long _retryAfterTimestamp = 0;

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
	protected void setResponse(Response response) { this._response = response; }

	/** If this {@link Work} instance has a cached response this method returns the {@link CacheEntry} that represents that response, or NULL if there is no cached response. */
	protected CacheEntry getCachedResponse() { return(this._cachedResponse); }

	/** Sets a cached response for this {@link Work} instance. NULL is a valid value. */
	protected void setCachedResponse(CacheEntry cachedResponse) { this._cachedResponse = cachedResponse; }

	/** Returns the most recent {@link FutureTask} instance that defines work to do for this {@link Work} instance, or NULL if there isn't one. */
	protected FutureTask<Response> getFutureTask() { return(this._futureTasks.peek()); }

	/**
	 * Adds the given {@link FutureTask} to this {@link Work} instance as the most recent work.
	 * Note that, when blocking on this work, all FutureTasks added here must complete before the wait handle returns.
	 */
	protected void addFutureTask(FutureTask<Response> futureTask) {
		if(futureTask == null) { throw(new IllegalArgumentException("'futureTask' can not be NULL")); }
		this._futureTasks.addFirst(futureTask);
	}
	
	/** Returns the "retry-after" timestamp (as an epoch time in milliseconds) for this {@link Work} */
	protected long getRetryAfterTimestamp() { return(this._retryAfterTimestamp); }

	/** Updates the "retry-after" timestamp for this {@link Work} based on the given delta.
	 * <p>
	 * @param deltaInMilliseconds The amount of time from now, in <b>milliseconds</b>, after which the retry should happen.
	 */
	protected void updateRetryAfterTimestamp(long deltaInMilliseconds) {
		this._retryAfterTimestamp = System.currentTimeMillis() + deltaInMilliseconds;
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
		// TODO: Cancel needs to be done via the CommManager...
		boolean cancelled = true;
		for(FutureTask<Response> future : this._futureTasks) {
			cancelled = cancelled && future.cancel(interruptAllowed);
		}
		return(cancelled);
	}

	/** {@inheritDoc} */
	@Override
	public Response get() throws InterruptedException, ExecutionException {
		try { return(this.getInternal(null, null)); } catch (TimeoutException e) { } // No-op OK
		return(null);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * <b>NOTE</b>: This implementation differers form the standard {@link Future#get(long, TimeUnit)} in that this {@link Work} instance may be 
	 * backed by multiple Futures. Further more, new Futures may be added while waiting on the current collection.  Because of this, the given 
	 * timeout value is used <b>for each Future</b> belonging to this Work and the total blocking time is indeterminate.  The maximum possible total 
	 * wait time before timeout will be the timeout value given times the total number of Futures required by this work over it's lifetime.
	 */
	@Override
	public Response get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		return(this.getInternal(timeout, unit));
	}
	
	private Response getInternal(Long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		
		// Make sure to wait for all work (newer work may have been added while we were waiting)
		int responseCount = 0;
		ArrayList<Response> responses = new ArrayList<Response>();
		while(responseCount < this._futureTasks.size()) {
			responseCount = 0;
			for(FutureTask<Response> future : this._futureTasks) {
				Response response = null;
				if((timeout != null) && (unit != null)) {
					response = future.get(timeout, unit);					
				} else {
					response = future.get();
				}
				responseCount++;
				if((response != null) && (!responses.contains(response))) {
					responses.add(response);
				}
			}
		}

		// Sort responses to ensure we return the newest most relevant one?
		Collections.sort(responses, _ResponseComparator);

		_Logger.debug("Done waiting on Work [responseCount:{} futureTaskCount:{}]", responseCount, this._futureTasks.size());
		if(responses.size() <= 0) {
			return(null);
		} else {
			return(responses.get(0));
		}
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

	/**
	 * A simple comparator to sort responses based on when the instances were created. We base this on instance creation 
	 * time rather than when the response was received to cover cases of serving previously cached responses.
	 */
	private static final Comparator<Response> _ResponseComparator = new Comparator<Response>() {
		@Override
		public int compare(Response lhs, Response rhs) {
			if(lhs == null) { throw(new IllegalArgumentException("'lhs' can not be NULL")); }
			if(rhs == null) { throw(new IllegalArgumentException("'rhs' can not be NULL")); }

			// Calculate order based on when the Response instances where created
			return(Long.compare(rhs.getInstanceCreationTime(), lhs.getInstanceCreationTime()));
		}
	};

}
