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
 * An implementation of the {@link Work} interface for expressing a cached result.
 * <p>
 * @author Todd S. Murchison
 */
class CachedWork implements Work {

	private final Request request;
	private final Response cachedResponse;
	private final Priority requestPriority;
	private final CachePriority cachingPriority;
	private final CacheBehavior cachingBehavior;

	/**
	 * Creates an instance of {@link CachedWork}.
	 * 
	 * @param request The {@link Request} this work represents.
	 * @param cachedResponse The cached result for this work.
	 * @param requestPriority The request priority of this work.
	 * @param cachingPriority The caching priority of this work.
	 * @param cachingBehavior The caching behavior of this work.
	 */
	protected CachedWork(
			Request request, 
			Response cachedResponse, 
			Priority requestPriority, 
			CachePriority cachingPriority, 
			CacheBehavior cachingBehavior) 
	{
		// Validate parameters
		if(request == null) { throw(new IllegalArgumentException("'request' cannot be null")); }
		if(cachedResponse == null) { throw(new IllegalArgumentException("'cachedResponse' cannot be null")); }
		if(requestPriority == null) { throw(new IllegalArgumentException("'requestPriority' cannot be null")); }
		if(cachingPriority == null) { throw(new IllegalArgumentException("'cachingPriority' cannot be null")); }
		if(cachingBehavior == null) { throw(new IllegalArgumentException("'cachingBehavior' cannot be null")); }

		this.request = request;
		this.cachedResponse = cachedResponse;
		this.requestPriority = requestPriority;
		this.cachingPriority = cachingPriority;
		this.cachingBehavior = cachingBehavior;
	}

	/** {@inheritDoc} */
	@Override
	public Status getState() { return(Status.COMPLETED); }

	/** {@inheritDoc} */
	@Override
	public int getId() { return(this.request.getId()); }

	/** {@inheritDoc} */
	@Override
	public Response get() throws InterruptedException, ExecutionException { return(this.cachedResponse); }

	/** {@inheritDoc} */
	@Override
	public Response get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return(this.cachedResponse); }

	/** {@inheritDoc} */
	@Override
	public Request getRequest() { return(this.request); }

	/** {@inheritDoc} */
	@Override
	public Priority getRequestPriority() { return(this.requestPriority); }

	/** {@inheritDoc} */
	@Override
	public CachePriority getCachingPriority() { return(this.cachingPriority); }

	/** {@inheritDoc} */
	@Override
	public CacheBehavior getCachingBehavior() { return(this.cachingBehavior); }

	/** {@inheritDoc} */
	@Override
	public boolean isDone() { return(true); }

	/** {@inheritDoc} */
	@Override
	public boolean isCancelled() { return(false); }

	/** This implementation is <b>no-op</b>.  Cached work has already been processed. */
	@Override
	public void setDependentWork(SubmittableWork dependentWork, DependentWorkListener dependentWorkListener) { }

	/** This implementation will always return <b>null</b>.  Cached work has already finished successfully. */
	@Override
	public Exception getException() { return(null); }

}
