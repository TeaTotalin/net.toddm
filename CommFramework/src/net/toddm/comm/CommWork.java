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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CachePriority;
import net.toddm.cache.LoggingProvider;
import net.toddm.comm.Priority.StartingPriority;

/**
 * Represents a unit of work being managed by the Comm Framework.  This class is the primary way that client code interacts 
 * with work it has submitted to the Comm Framework, including blocking on that work as needed. These units of work represent 
 * everything needed by the Comm Framework to reach a remote end-point and elicit a response.
 * <p>
 * @author Todd S. Murchison
 */
class CommWork implements Work {

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
    private final Priority _requestPriority;
    private final CachePriority _cachingPriority;
    private final CacheBehavior _cachingBehavior;
    private final ConcurrentLinkedQueue<FutureTask<Response>> _futureTasks = new ConcurrentLinkedQueue<FutureTask<Response>>();
	private final LoggingProvider _logger;

	private Status _state = Status.CREATED;
	private Response _response = null;
	private CacheEntry _cachedResponse = null;
	private long _retryAfterTimestamp = 0;

	protected CommWork(
			URI uri, 
			Request.RequestMethod method, 
			byte[] postData, 
			Map<String, String> headers, 
			boolean isIdempotent, 
			StartingPriority requestPriority, 
			CachePriority cachingPriority, 
			CacheBehavior cachingBehavior, 
			LoggingProvider loggingProvider)
	{

		// Validate parameters
		if(uri == null) { throw(new IllegalArgumentException("'uri' can not be NULL")); }
		if(method == null) { throw(new IllegalArgumentException("'method' can not be NULL")); }
		if(requestPriority == null) { throw(new IllegalArgumentException("'requestPriority' can not be NULL")); }
		if(cachingPriority == null) { throw(new IllegalArgumentException("'cachingPriority' can not be NULL")); }
		if(cachingBehavior == null) { throw(new IllegalArgumentException("'cachingBehavior' can not be NULL")); }
		if((postData != null) && (!Request.RequestMethod.POST.equals(method))) {
			throw(new IllegalArgumentException("'method' must be 'POST' when 'postData' is provided"));
		}

		// Set our data members
		this._state = Status.CREATED;
		this._request = new Request(uri, method, postData, headers, isIdempotent);
		this._requestPriority = new Priority(this, requestPriority);
		this._cachingPriority = cachingPriority;
		this._cachingBehavior = cachingBehavior;
		this._logger = loggingProvider;
	}

	/** Returns the {@link Request} instance associated with this {@link CommWork} instance. */
	public Request getRequest() { return(this._request); }

	/** Returns the request {@link Priority} of this {@link CommWork} instance. */
	public Priority getRequestPriority() { return(this._requestPriority); }

	/** Returns the caching {@link CachePriority} of this {@link CommWork} instance, or <b>null<b> if it had no priority set. */
	public CachePriority getCachingPriority() { return(this._cachingPriority); }

	/** Returns the caching {@link CacheBehavior} of this {@link CommWork} instance, or <b>null<b> if it had no behavior set. */
	public CacheBehavior getCachingBehavior() { return(this._cachingBehavior); }

	/** Returns TRUE if the result of this work should participate in caching, FALSE otherwise. */
	protected boolean shouldCache() {

		// If the client has requested no caching do not cache
		if(CacheBehavior.DO_NOT_CACHE.equals(this._cachingBehavior)) {
			return(false);
		}

		if(this._response != null) {

			// If the response indicates a "cache still valid" response then we are already implicitly caching
			if(this._response.getResponseCode() == 304) {
				return(true);
			}

			// If the response contains the "no-cache" directive do not cache
			if(this._response.shouldNotCacheDueToNoCacheDirective()) {
				return(false);
			}

			if( (CacheBehavior.SERVER_DIRECTED_CACHE.equals(this._cachingBehavior)) && (this._response.getTtlFromHeaders() == null) ) {

				// We are only supposed to cache as directed by the server and the server has not provided a TTL, so do not cache
				return(false);
			}

		} else if (CacheBehavior.SERVER_DIRECTED_CACHE.equals(this._cachingBehavior)) {

			// We have no response and we are only supposed to cache as directed by the server, so do not cache
			return(false);
		}

		// Default to caching
		return(true);
	}

	/** Returns the current state of this {@link CommWork} instance */
	protected Status getState() { return(this._state); }

	/** Sets the current state of this {@link CommWork} instance */
	protected void setState(Status state) {
		if(state == null) { throw(new IllegalArgumentException("'state' can not be NULL")); }
		this._state = state;
	}

	/** Returns the {@link Response} that resulted from processing this {@link CommWork} instance, or NULL if one has not been set. */
	protected Response getResponse() { return(this._response); }

	/** Sets the response that resulted from processing this work. NULL is a valid value. */
	protected void setResponse(Response response) { this._response = response; }

	/** If this {@link CommWork} instance has a cached response this method returns the {@link CacheEntry} that represents that response, or NULL if there is no cached response. */
	protected CacheEntry getCachedResponse() { return(this._cachedResponse); }

	/** Sets a cached response for this {@link CommWork} instance. NULL is a valid value. */
	protected void setCachedResponse(CacheEntry cachedResponse) { this._cachedResponse = cachedResponse; }

    /**
     * Returns the most recent {@link FutureTask} instance that defines work to do for this {@link CommWork} instance, or NULL if there isn't one.
     */
    protected FutureTask<Response> getFutureTask() {
        FutureTask<Response> lastFuture = null;
        for (FutureTask<Response> future : this._futureTasks) {
            lastFuture = future;
        }
        return (lastFuture);
    }

	/**
	 * Adds the given {@link FutureTask} to this {@link CommWork} instance as the most recent work.
	 * Note that, when blocking on this work, all FutureTasks added here must complete before the wait handle returns.
	 */
	protected void addFutureTask(FutureTask<Response> futureTask) {
		if(futureTask == null) { throw(new IllegalArgumentException("'futureTask' can not be NULL")); }
		this._futureTasks.add(futureTask);
	}
	
	/** Returns the "retry-after" timestamp (as an epoch time in milliseconds) for this {@link CommWork} */
	protected long getRetryAfterTimestamp() { return(this._retryAfterTimestamp); }

	/** Updates the "retry-after" timestamp for this {@link CommWork} based on the given delta.
	 * <p>
	 * @param deltaInMilliseconds The amount of time from now, in <b>milliseconds</b>, after which the retry should happen.
	 */
	protected void updateRetryAfterTimestamp(long deltaInMilliseconds) {
		this._retryAfterTimestamp = System.currentTimeMillis() + deltaInMilliseconds;
	}

	/**
	 * Returns the ID of this {@link CommWork} instance. The ID of the underlying 
	 * {@link Request} is used. See {@link Request#getId()} for details.
	 */
	public int getId() {
		return(this._request.getId());
	}

	/**
	 * Returns the hash code of this {@link CommWork} instance. The hash code of the underlying 
	 * {@link Request} is used. See {@link Request#hashCode()} for details.
	 */
	@Override
	public int hashCode() {
		return(this._request.hashCode());
	}

	/**
	 * Returns TRUE if this {@link CommWork} instance is equivalent to the given {@link CommWork} instance. Equality 
	 * of the underlying {@link Request} instances is used. See {@link Request#equals(Object)} for details.
	 */
	@Override
	public boolean equals(Object work) {
		if(work == null) { throw(new IllegalArgumentException("'work' can not be NULL")); }
		if(!(work instanceof CommWork)) { return(false); }
		return(this._request.equals(((CommWork)work)._request));
	}

	//--------------------------------------------------------------------------
	// Future interface for wrapping underlying FutureTask

	/**
	 * If this {@link CommWork} instance is not already done processing this method updates the state to {@link Status#CANCELLED} 
	 * and attempts to cancel execution of any underlying {@link FutureTask} instances comprising this work. If any of the underlying 
	 * Futures have already started, then the interruptAllowed parameter determines whether the threads executing the work should be 
	 * interrupted in an attempt to stop the work. After this method returns, subsequent calls to isDone and isCancelled will always 
	 * return true.
	 * <p>
	 * <b>NOTE</b>: This method should only be called by the Comm Framework from within a critical section on _workManagmentLock.
	 * <p>
	 * @param interruptAllowed True if any threads executing work should be interrupted, otherwise in-progress work is allowed to complete.
	 */
	void cancel(boolean interruptAllowed) {
		if(!this.isDone()) {
			this.setState(Status.CANCELLED);
			for(FutureTask<Response> future : this._futureTasks) {
				future.cancel(interruptAllowed);
			}
			if(_logger != null) { _logger.debug("[thread:%1$d][request:%2$d] Work has been cancel", Thread.currentThread().getId(), this.getId()); }
		}
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
	 * <b>NOTE</b>: This implementation differers form the standard {@link Future#get(long, TimeUnit)} in that this {@link CommWork} instance may be 
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

		if(this._logger != null) { this._logger.debug("Done waiting on Work [responseCount:%1$d futureTaskCount:%2$d]", responseCount, this._futureTasks.size()); }
		if(responses.size() <= 0) {
			return(null);
		} else {
			return(responses.get(responses.size() -1));
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
            // 0 if lhs = rhs, less than 0 if lhs < rhs, and greater than 0 if lhs > rhs
            return (int)(lhs.getInstanceCreationTime() - rhs.getInstanceCreationTime());
        }
    };

}
