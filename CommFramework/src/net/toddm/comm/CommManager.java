
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CacheProvider;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import net.toddm.comm.Work.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the main work horse of the communications framework. Instances of this class are used to submit work and provide priority queue 
 * management, result caching, failure and response based retry, and much more.
 * <p>
 * The communications framework makes use of SLF4J. To use in Android include SLF4J Android (http://www.slf4j.org/android/) in your Android project.
 * <p>
 * @author Todd S. Murchison
 */
public final class CommManager {

	// TODO: failure policies / off-line modes - should cover graceful recovery from service outage, etc.
	//
	// TODO: https://github.com/Talvish/Tales/tree/master/product
	//
	// TODO: pluggable response body handling?
	//
	// TODO: GZIP support
	//
	// TODO: Support use of end-points with bad SSL certs via configuration to allow or disallow

	private static final Logger _Logger = LoggerFactory.getLogger(CommManager.class.getSimpleName());

	//------------------------------
	// For CommManager access we are going to use a Builder -> Setters -> Create pattern. Instances of CommManager 
	// are created by first creating an instance of CommManager.Builder, calling setters on that Builder instance, 
	// and then calling create() on that Builder instance, which returns a CommManager instance.
	private CommManager(String name, CacheProvider cacheProvider, PriorityManagmentProvider priorityManagmentProvider, RetryPolicyProvider retryPolicyProvider) {
		this._cacheProvider = cacheProvider;
		this._priorityManagmentProvider = priorityManagmentProvider;
		this._retryPolicyProvider = retryPolicyProvider;
		this.startWorking(name);
	}
	//------------------------------

	// TODO: Get configuration values from a config system
	private final int _redirectLimit = 3;
	private final int _maxSimultaneousRequests = 1;
	private final int _connectTimeoutMilliseconds = 30000;
	private final int _readTimeoutMilliseconds = 30000;
	

	private final ExecutorService _requestWorkExecutorService = Executors.newFixedThreadPool(_maxSimultaneousRequests);
	private final LinkedList<Work> _queuedWork = new LinkedList<Work>();
	private final ArrayList<Work> _activeWork = new ArrayList<Work>();
	private final ArrayList<Work> _retryWork= new ArrayList<Work>();

	// Members needed for managing the work thread.
	// This is an always-running management thread, so we do not host it in an ExecutorService.
	private Thread _workThread = null;
	private Object _workThreadLock = new Object();
	private volatile boolean _workThreadStopping = false;
	private Object _workManagmentLock = new Object();

	private final CacheProvider _cacheProvider;
	private final PriorityManagmentProvider _priorityManagmentProvider;
	private final RetryPolicyProvider _retryPolicyProvider; 

	/**
	 * Enters a request into the communications framework for processing. The {@link Work} instance returned can be used
	 * to wait on the request, manage the request, get results, etc.
	 * <p>
	 * @param uri The URI of the request to work on.
	 * @param method The HTTP method of the request to work on.
	 * @param postData <b>[OPTIONAL]</b> Can be NULL. The POST data of the request to work on.
	 * @param headers <b>[OPTIONAL]</b> Can be NULL. The request headers of the request to work on.
	 * @param cachingAllowed If set, the CommManager is allowed to cache the results of this request.
	 */
	public Work enqueueWork(
			URI uri, 
			Request.RequestMethod method, 
			byte[] postData, 
			Map<String, String> headers, 
			StartingPriority priority, 
			boolean cachingAllowed) 
	{
		// This constructor will validate all the arguments
		Work newWork = new Work(uri, method, postData, headers, priority, cachingAllowed);
		Work resultWork = null;
		_Logger.debug("[thread:{}] enqueueWork() start", Thread.currentThread().getId());

		synchronized(_workManagmentLock) {

			// Check if the specified work is already being handled by the communications framework
			Work existingWork = null;
			int index = _queuedWork.indexOf(newWork);
			if(index >= 0) {
				existingWork = _queuedWork.get(index);
			} else {
				index = _activeWork.indexOf(newWork);
				if(index >= 0) {
					existingWork = _activeWork.get(index);
				} else {
					index = _retryWork.indexOf(newWork);
					if(index >= 0) {
						existingWork = _retryWork.get(index);
					}
				}
			}
			
			if(existingWork != null) {

				// TODO: Consider if we want to support updating request priority for already queued work based on clients enqueuing the same request at a different starting priority level.
				// I think we would only want to allow increasing priority to prevent messing with starvation prevention algorithms. For now this is probably a very low priority feature.

				_Logger.info("[thread:{}] enqueueWork() Returning already enqueued work [id:{}]", Thread.currentThread().getId(), existingWork.getId());
				resultWork = existingWork;
			} else {

				// Check cache to see if we already have usable results for this request
				Response cachedResponse = null;
				if((cachingAllowed) && (this._cacheProvider != null)) {

					// TODO: Allowing the use of stale cache entries should come from config, maybe allowing request instance to override
					CacheEntry cacheEntry = this._cacheProvider.get(Integer.toString(newWork.getRequest().getId()), false);
					if((cacheEntry != null) && (cacheEntry.getBytesValue() != null) && (cacheEntry.getBytesValue().length > 0)) {

						ByteArrayInputStream inStream = null;
						ObjectInput inObj = null;
						try {
							inStream = new ByteArrayInputStream(cacheEntry.getBytesValue());
							inObj = new ObjectInputStream(inStream);
							cachedResponse = (Response)inObj.readObject();
						} catch (Exception e) {
							_Logger.error("Response de-serialization from cache failed", e);
						} finally {
							if(inObj != null) { try { inObj.close(); } catch(Exception e) {} } // No-op an exception OK here
							if(inStream != null) { try { inStream.close(); } catch(Exception e) {} } // No-op an exception OK here
						}
					}
				}
				
				if(cachedResponse != null) {

					// If we have a usable cached result use it
					newWork.setResponse(cachedResponse);
					newWork.setState(Work.Status.COMPLETED);
					newWork.addFutureTask(new CachedResponseFuture(cachedResponse));
					_Logger.info("[thread:{}] enqueueWork() Returning cached results [id:{}]", Thread.currentThread().getId(), newWork.getId());
					resultWork = newWork;
				} else {

					// This request is not available from cache and is not already being 
					// managed by this CommManager instance so add it as new work
					newWork.addFutureTask(new FutureTask<Response>(new WorkCallable(newWork)));
					addWorkToQueue(newWork, ManagedQueue.QUEUED);
					newWork.setState(Work.Status.WAITING);
					_Logger.info("[thread:{}] enqueueWork() Added new work [id:{}]", Thread.currentThread().getId(), newWork.getId());
					resultWork = newWork;
	
					// We've added new work, so kick the worker thread
					_workManagmentLock.notify();
				}
			}

		}
		
		return(resultWork);
	}

	/**
	 * This method <b>does not block</b> and should only be carefully used internally by this class from within suitable critical sections.<br>
	 * Adds the given {@link Work} instance to the given queue and ensures that the Work instance is removed from the other queues, as needed.
	 */
	private void addWorkToQueue(Work work, ManagedQueue queue) {

		// Validate arguments
		if(work == null) { throw(new IllegalArgumentException("'work' can not be NULL")); }
		if(queue == null) { throw(new IllegalArgumentException("'queue' can not be NULL")); }

		// Ensure the work is in the correct queue and not in any others
		_queuedWork.remove(work);
		_activeWork.remove(work);
		_retryWork.remove(work);
		switch(queue) {
			case QUEUED:	_queuedWork.add(work);	break;
			case ACTIVE:	_activeWork.add(work);	break;
			case RETRY:		_retryWork.add(work);	break;
			default:
				throw(new IllegalArgumentException(String.format(Locale.US,  "Unsupported queue type [%1$s]", queue.name())));
		}
	}

	/** An enumeration of the queues managed by {@link CommManager}. */
	private enum ManagedQueue {
		QUEUED,
		ACTIVE,
		RETRY
	}

	/** Starts the work thread if it is not already running. It is safe to make this call multiple times. */
	private void startWorking(String name) {
		_Logger.debug("[thread:{}] startWorking()", Thread.currentThread().getId());
		synchronized(_workThreadLock) {
			_workThreadStopping = false;
			if(_workThread == null) {
				_workThread = new Thread(new WorkManagementRunnable(), String.format(Locale.US, "CommManager Work Thread [%1$s]", name));
			}
			if(!_workThread.isAlive()) {
				_workThread.start();
				_Logger.debug("[thread:{}] Thread started", Thread.currentThread().getId());
			} else {
				_Logger.debug("[thread:{}] Thread already running", Thread.currentThread().getId());
			}
		}
	}

	/** Stops the work thread if it is running. It is safe to make this call multiple times. This is a <strong>blocking</strong> method. */
	@SuppressWarnings("unused")
	private void stopWorking() {
		_Logger.debug("[thread:{}] stopWorking()", Thread.currentThread().getId());
		synchronized(_workThreadLock) {

			// Can't stop if we are not running
			if(_workThread == null) {
				_Logger.debug("[thread:{}] Thread already stopped", Thread.currentThread().getId());
				return;
			}

			// Tell the thread to exit and then wake it up to do exit work
			_workThreadStopping = true;
			_Logger.debug("[thread:{}] kicking work thread", Thread.currentThread().getId());
			synchronized(_workManagmentLock) { _workManagmentLock.notify(); }

			// Attempt to guarantee that the thread ends
			try {
				_workThread.join(2000);
				_workThread.interrupt();
				_workThread.join();
			} catch(InterruptedException e) {
				_Logger.error("[thread:{}] Thread received an interrupt", Thread.currentThread().getId());
			} catch(Exception e) {
				_Logger.error(String.format(Locale.US, "[thread:%1$d] failed", Thread.currentThread().getId()), e);
			} finally {
				_workThread = null;
				_Logger.debug("[thread:{}] Thread stopped", Thread.currentThread().getId());
			}
		}
	}
	
	/**
	 * This method calculates and returns the interval, in milliseconds, between now and the next work retry time.
	 * <p>
	 * <b>NOTE</b>: This method is not thread-safe and should be called only from appropriate critical sections.
	 */
	private long getNextRetryInterval() {

		// Default to sleeping indefinitely until notified
		long retryInterval = Long.MAX_VALUE;

		// Examine pending retry work to see if there is a time we should wake up without being notified
		long now = System.currentTimeMillis();
		for(Work work : this._retryWork) {
			long delta = work.getRetryAfterTimestamp() - now;
			if(delta < retryInterval) { retryInterval = delta; }
		}

		// Enforce some sane limits
		if(retryInterval < 20) { retryInterval = 20; }

		// Do some logging
		if(retryInterval == Long.MAX_VALUE) {
			_Logger.trace("[thread:{}] getNextRetryInterval() returning MAX_VALUE", Thread.currentThread().getId());
		} else {
			_Logger.trace("[thread:{}] getNextRetryInterval() returning {} milliseconds", Thread.currentThread().getId(), retryInterval);
		}

		return(retryInterval);
	}

	//------------------------------------------------------------
	// Private helper classes

	/**
	 * An implementation of {@link Runnable} that provides the work needed to manage various aspects of the
	 * {@link CommManager}. This includes (among other things):
	 * <ul>
	 * 	<li>managing the work queue
	 * 	<li>initiating request work
	 * 	<li>managing retry's
	 * </ul>
	 */
	private class WorkManagementRunnable implements Runnable {

		/** Defines the work of the work thread of the comm manager. See {@link WorkManagementRunnable} for details. */
		@Override
		public void run() {
			long sleepTime = Long.MAX_VALUE;
			while(!_workThreadStopping) {
				try {

					if(_workThreadStopping) { break;}

					// Manage all comm work here (queue management, retries, etc.)
					synchronized(_workManagmentLock) {
						_Logger.debug(String.format(Locale.US, "[thread:%1$d] queued:{%2$d} active:{%3$d} retry:{%4$d}", 
								Thread.currentThread().getId(), 
								_queuedWork.size(), 
								_activeWork.size(), 
								_retryWork.size()));

						// Check the retry queue to see if we need to move any work from pending retry to the priority queue
						long now = System.currentTimeMillis();
						ArrayList<Work> retryNowList = new ArrayList<Work>();
						for(Work retryWork : _retryWork) {
							if(retryWork.getRetryAfterTimestamp() <= now) {
								retryNowList.add(retryWork);
							}
						}
						for(Work retryWork : retryNowList) {
							addWorkToQueue(retryWork, ManagedQueue.QUEUED);
							retryWork.setState(Status.WAITING);
							_Logger.debug("[thread:{}] Request {} moved from retry to queue", Thread.currentThread().getId(), retryWork.getId());
						}

						// Check current work to see if we can start more work
						while((_activeWork.size() < _maxSimultaneousRequests) && (_queuedWork.size() > 0)) {

							// Update request priorities as needed using the provided PriorityManagmentProvider implementation
							for(Work work : _queuedWork) { CommManager.this._priorityManagmentProvider.promotePriority(work.getPriority()); }

							// Sort the current priority queue using the provided PriorityManagmentProvider implementation and get the next item
							Collections.sort(_queuedWork, CommManager.this._workComparator);

							// Grab the next request, move it to the active queue, and start the work
							Work workToStart = _queuedWork.removeFirst();
							addWorkToQueue(workToStart, ManagedQueue.ACTIVE);
							workToStart.setState(Work.Status.RUNNING);
							_requestWorkExecutorService.execute(workToStart.getFutureTask());
						}

						// Calculate sleep time based on pending retry work
						sleepTime = CommManager.this.getNextRetryInterval();

						// Sleep until there is more work to do
						_Logger.debug(String.format(
								Locale.US, 
								"[thread:%1$d] Work thread is waiting to be notified [sleepTime:%2$d]", 
								Thread.currentThread().getId(), 
								sleepTime));
						_workManagmentLock.wait(sleepTime);
						_Logger.debug(String.format(Locale.US, "[thread:%1$d] Work thread is awake", Thread.currentThread().getId()));
					}

					if(_workThreadStopping) { break;}

				} catch(Exception e) {
					_Logger.error(String.format(Locale.US, "[thread:%1$d] failure", Thread.currentThread().getId()), e);
					try { Thread.sleep(5000); } catch(Exception f) { }  // No-op OK
				}
			}
			_Logger.debug("[thread:{}] Work Thread exited", Thread.currentThread().getId());
		}

	}

	/** A simple {@link Comparator} implementation for {@link Work} instances that wraps the Comparator provided by the current {@link PriorityManagmentProvider}. */
	private Comparator<Work> _workComparator = new Comparator<Work>() {
		@Override
		public int compare(Work lhs, Work rhs) {
			if(lhs == null) { throw(new IllegalArgumentException("'lhs' can not be NULL")); }
			if(rhs == null) { throw(new IllegalArgumentException("'rhs' can not be NULL")); }
			return(CommManager.this._priorityManagmentProvider.getPriorityComparator().compare(lhs.getPriority(), rhs.getPriority()));
		}
	};

	/** An implementation of {@link Callable} that makes the actual network request and gets the response */
	private class WorkCallable implements Callable<Response> {

		private final Work _work;
		private String _logPrefix = null;

		private WorkCallable(Work work) {
			if(work == null) { throw(new IllegalArgumentException("'work' can not be NULL")); }
			this._work = work;
		}

		@Override
		public Response call() throws Exception {
			this._logPrefix = String.format(Locale.US, "[thread:%1$d][request:%2$d]", Thread.currentThread().getId(), this._work.getId());

			// Do the work
			Response response = this.processesRequest();

			// Log response
			if(response == null) {
				_Logger.error("{} Received a NULL result", this._logPrefix);
			} else {
				if(_Logger.isTraceEnabled()) {
					_Logger.trace("{} Response code: {}", this._logPrefix, response.getResponseCode());
					if((response.getResponseBody() != null) && (response.getResponseBody().length() > 0)) {
						_Logger.trace("{} Response body:\r\n{}", this._logPrefix, response.getResponseBody());
					}
					if((response.getHeaders() != null) && (response.getHeaders().size() > 0)) {
						StringBuilder logMsg = new StringBuilder(this._logPrefix);
						logMsg.append(" Response headers:\r\n");
						for(String name : response.getHeaders().keySet()) {
							for(String value : response.getHeaders().get(name)) {
								logMsg.append(":      ");
								logMsg.append(name);
								logMsg.append(" = ");
								logMsg.append(value);
								logMsg.append("\r\n");
							}
						}
						_Logger.trace("{} {}", this._logPrefix, logMsg.toString());
					}
				}
			}

			// Clean up and return
			this.cleanup();
			return(response);
		}

		/** Does the actual on-the-wire work of the request and returns the results. <p> This method is <b>blocking</b>. */
		private Response processesRequest() {
			long start = System.currentTimeMillis();

			Response response = null;
			InputStream in = null;
			HttpURLConnection urlConnection = null;
			try {

				// Create our connection
				URL url = this._work.getRequest().getUri().toURL();
				urlConnection = (HttpURLConnection) url.openConnection();

				// We will be manually rolling redirection support for consistent behavior across Java 
				// versions and for the ability to make redirection behavior full configurable, etc.
				// TODO: Make the use of built-in HttpURLConnection redirection support a configurable option
				urlConnection.setInstanceFollowRedirects(false);

				// Configure our connection
				urlConnection.setConnectTimeout(CommManager.this._connectTimeoutMilliseconds);
				urlConnection.setReadTimeout(CommManager.this._readTimeoutMilliseconds);
				urlConnection.setDoInput(true);
				urlConnection.setRequestMethod(this._work.getRequest().getMethod().name());
				_Logger.debug("{} Making an HTTP {} request", this._logPrefix, this._work.getRequest().getMethod().name());

				// Add request headers if we have any for this request
				if(this._work.getRequest().getHeaders() != null) {
					for(String name : this._work.getRequest().getHeaders().keySet()) {
						urlConnection.setRequestProperty(name, this._work.getRequest().getHeaders().get(name));
					}
				}

				// Add the POST body if we have one (this must be done before establishing the connection)
				if(	(RequestMethod.POST.equals(this._work.getRequest().getMethod())) && 
					(this._work.getRequest().getPostData() != null) && 
					(this._work.getRequest().getPostData().length > 0) )
				{
					urlConnection.setDoOutput(true);
					OutputStream outStream = null;
					try {
						outStream = urlConnection.getOutputStream();
						outStream.write(this._work.getRequest().getPostData());
						outStream.flush();
						_Logger.debug("{} Sent {} bytes of POST body data", this._logPrefix, this._work.getRequest().getPostData().length);
					} finally {
				        if(outStream != null) { try { outStream.close(); } catch(Exception e) {} }  // Exception suppression is OK here
					}
				}

				// Do the actual work on the wire
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				try {

					//**************************************** 
					// Establish the connection. Any configuration that must be done prior to connecting must go above.
					urlConnection.connect();
					//**************************************** 
	
					// Read the response
					if(urlConnection.getContentLengthLong() > 0) {
						int readCount;
						byte[] data = new byte[512];
						in = urlConnection.getInputStream();
						while((readCount = in.read(data, 0, data.length)) != -1) {
						  buffer.write(data, 0, readCount);
						}
						buffer.flush();
					}
				
				} catch(Exception e) {
					
					// An error occurred while doing the on-the-wire work, check if we should retry the request later
					_Logger.error(this._logPrefix, e);
					RetryProfile retryProfile = CommManager.this._retryPolicyProvider.shouldRetry(this._work.getRequest(), e);
					if(retryProfile.shouldRetry()) {

						// This work should be retried, make it so...
						this._work.updateRetryAfterTimestamp(retryProfile.getRetryAfterMilliseconds());
						this._work.getRequest().incrementRetryCountFromFailure();
						this._work.setState(Status.RETRYING);

						// Add the failed request to the retry list and kick the worker thread so it wakes up to recalculate it's sleep time
						synchronized(_workManagmentLock) {
							addWorkToQueue(this._work, ManagedQueue.RETRY);

							// Add the retry unit of work to the Work instance
							this._work.addFutureTask(new FutureTask<Response>(new WorkCallable(this._work)));
							_workManagmentLock.notify();
						}
					} else {
						this._work.setState(Status.COMPLETED);
					}

					// We are done (for now at least) with this work
					return(null);
				}

				// Construct the response instance
				response = new Response(
						buffer.toByteArray(), 
						urlConnection.getHeaderFields(), 
						urlConnection.getResponseCode(), 
						this._work.getRequest().getId(),
						(int)(System.currentTimeMillis() - start));
				this._work.setResponse(response);
				_Logger.debug("{} Request finished with a {} response code", this._logPrefix, this._work.getResponse().getResponseCode());

				// Check for a response triggered retry
				RetryProfile retryProfile = CommManager.this._retryPolicyProvider.shouldRetry(this._work.getRequest(), response);
				if(retryProfile.shouldRetry()) {

					// This work should be retried, make it so...
					this._work.updateRetryAfterTimestamp(retryProfile.getRetryAfterMilliseconds());
					this._work.getRequest().incrementRetryCountFromResponse();
					this._work.setState(Status.RETRYING);

					// Add the failed request to the retry list and kick the worker thread so it wakes up to recalculate it's sleep time
					synchronized(_workManagmentLock) {
						addWorkToQueue(this._work, ManagedQueue.RETRY);

						// Add the retry unit of work to the Work instance
						this._work.addFutureTask(new FutureTask<Response>(new WorkCallable(this._work)));
						_workManagmentLock.notify();
					}
					
					// We are done (for now at least) with this work
					return(null);
				} else {
					this._work.setState(Status.COMPLETED);
				}

				// Support "3xx Location" response triggered redirects (if HttpURLConnection is not handling them for us already)
				if( ((response.getResponseCode() == 301) || (response.getResponseCode() == 302) || (response.getResponseCode() == 303)) && 
					(this._work.getRequest().getRedirectCount() < CommManager.this._redirectLimit) && 
					(!urlConnection.getInstanceFollowRedirects()) )
				{
					URI targetUri = response.getLocationFromHeaders(this._work.getRequest());
					if(_Logger.isDebugEnabled()) {
						_Logger.debug("{} Redirecting from {} to {}", new Object[] { this._logPrefix, this._work.getRequest().getUri().toString(), targetUri.toString() });
					}
					this._work.getRequest().redirect(targetUri);  // This call increments the request redirect count

					// We process redirects through the retry queue (to be retried immediately)
					this._work.updateRetryAfterTimestamp(0);
					this._work.setState(Status.REDIRECTING);
					synchronized(_workManagmentLock) {
						addWorkToQueue(this._work, ManagedQueue.RETRY);
						this._work.addFutureTask(new FutureTask<Response>(new WorkCallable(this._work)));  // Add the future task for the new unit of work
						_workManagmentLock.notify();
					}

					// We are done (for now at least) with this work
					return(null);
				}

				// Offer the response to cache (guard against negative caching)
				if((this._work.getRequest().isCachingAllowed()) && (CommManager.this._cacheProvider != null) && (response.isSuccessful())) {

					byte[] responseObjBytes = null;
					ByteArrayOutputStream outStream = null;
					ObjectOutput out = null;
					try {

						outStream = new ByteArrayOutputStream();
						out = new ObjectOutputStream(outStream);
						out.writeObject(response);
						responseObjBytes = outStream.toByteArray();
						if((responseObjBytes != null) && (responseObjBytes.length > 0)) {

							// Check the response headers for a caching TTL
							Long ttl = response.getTtlFromHeaders();
							if(ttl == null) { ttl = Long.MAX_VALUE; }  // Long.MAX_VALUE indicates never expiring

							// Check the response headers for an ETag value
							String eTag = response.getETagFromHeaders();

							// TODO: Send eTag request header (when we have a stale cache entry)
							// TODO: Handle 304 "Not Modified" caching related responses (should refresh timestamps on cache entry)

							CommManager.this._cacheProvider.add(
									Integer.toString(this._work.getRequest().getId()), 
									responseObjBytes, 
									ttl, 
									eTag, 
									this._work.getRequest().getUri());
							_Logger.debug("{} Response for request {} added to cache", this._logPrefix, this._work.getRequest().getId());

							// We will go ahead and enforce LRU here as we may have just added a new cache entry
							CommManager.this._cacheProvider.trimLru();
						}
					} catch(Exception e) {
						_Logger.error("Response serialization to cache failed", e);
					} finally {
						if(out != null) { try { out.close(); } catch(Exception e) {} } // No-op on exception OK here
						if(outStream != null) { try { outStream.close(); } catch(Exception e) {} } // No-op on exception OK here
					}
				}

			} catch (MalformedURLException e) {
				throw(new CommException(e));
			} catch (IOException e) {
				throw(new CommException(e));
			} finally {

				// Always clean up
		        if(in != null) { try { in.close(); } catch(Exception e) {} }  // Exception suppression is OK here
		        if(urlConnection != null) { try { urlConnection.disconnect(); } catch(Exception e) {} }  // Exception suppression is OK here
			}

			_Logger.trace("{} Processing took {} milliseconds", this._logPrefix, (System.currentTimeMillis() - start));

			return(response);
		}

		private void cleanup() {

			// Clear the finished work out of the managed queues
			synchronized(_workManagmentLock) {

				_Logger.debug("{} Work completed, doing cleanup [state:{}]", this._logPrefix, this._work.getState());

				// TODO: Ensure that Work state change and queue management both always happen within _workManagmentLock

				switch(this._work.getState()) {
					case CREATED:		// The work has been created, but has not started processing yet
						break;
					case WAITING:		// The work is waiting in the pending work queue
						break;
					case RUNNING:		// The work is actively being processed
						break;
					case RETRYING:		// There is a pending retry attempt for the work
						break;
					case REDIRECTING:	// There is a pending retry attempt for the work
						break;
					case CANCELLED:		// The work has been cancelled
					case COMPLETED:		// The work has finished without being cancelled

						// Ensure finished work is no longer in any of the queues
						if(_queuedWork.remove(this._work)) {
							_Logger.error("{} Finished work has been removed from _queuedWork", this._logPrefix);
						}
						if(_activeWork.remove(this._work)) {
							_Logger.debug("{} Finished work has been removed from _activeWork", this._logPrefix);
						}
						if(_retryWork.remove(this._work)) {
							_Logger.error("{} Finished work has been removed from _retryWork", this._logPrefix);
						}

						// Kick the work thread to check for new work (a spot just opened up!)
						_Logger.trace("{} kicking work thread", this._logPrefix);
						_workManagmentLock.notify();

						break;
				}
			}
		}

	};

	/** This is a factory class used for configuring and then creating instances of {@link CommManager}. */
	public static final class Builder {
		
		private String _name = "default";
		private CacheProvider _cacheProvider = null;
		private PriorityManagmentProvider _priorityManagmentProvider = null;
		private RetryPolicyProvider _retryPolicyProvider = null;

		public Builder() {

			// This is where we configure any sane defaults for pluggable sub-systems like caching provider, 
			// request priority, failure policies, configuration provider, response body handling, etc.

			this._priorityManagmentProvider = new DefaultPriorityManagmentProvider();
			this._retryPolicyProvider = new DefaultRetryPolicyProvider();
		}

		/**
		 * Sets a name used to tag the main processing thread of {@link CommManager} 
		 * instances subsequently created via {@link Builder#create()}.
		 */
		public Builder setName(String name) {
			this._name = name;
			return(this);
		}

		/**
		 * Sets the {@link CacheProvider} instance used by the comm framework for caching results.
		 * The default is <b>null</b> (no caching). The cache provider set here will be used by the 
		 * {@link CommManager} instances subsequently created via {@link Builder#create()}.
		 */
		public Builder setCacheProvider(CacheProvider cacheProvider) {
			// Setting cache provider to NULL is OK and results in no caching
			this._cacheProvider = cacheProvider;
			return(this);
		}

		/**
		 * Sets the {@link PriorityManagmentProvider} instance used by the comm framework for work priority queue management.
		 * The priority provider set here will be used by the {@link CommManager} instances subsequently created via {@link Builder#create()}.
		 * If not set then {@link DefaultPriorityManagmentProvider} is used, which provides a simple age based anti queue starvation implementation.
		 */
		public Builder setPriorityManagmentProvider(PriorityManagmentProvider priorityManagmentProvider) {
			if(priorityManagmentProvider == null) { throw(new IllegalArgumentException("'priorityManagmentProvider' can not be NULL")); }
			this._priorityManagmentProvider = priorityManagmentProvider;
			return(this);
		}

		/**
		 * Sets the {@link RetryPolicyProvider} instance used by the comm framework when deciding if requests should be enqueued for retry.
		 * The retry policy provider set here will be used by the {@link CommManager} instances subsequently created via {@link Builder#create()}.
		 * If not set then {@link DefaultRetryPolicyProvider} is used, which provides a simple error, 503, and 202 based retry implementation.
		 */
		public Builder setRetryPolicyProvider(RetryPolicyProvider retryPolicyProvider) {
			if(retryPolicyProvider == null) { throw(new IllegalArgumentException("'retryPolicyProvider' can not be NULL")); }
			this._retryPolicyProvider = retryPolicyProvider;
			return(this);
		}

		/**
		 * Creates a new {@link CommManager} instance based on the values currently configured on this {@link Builder} instance.
		 * @return A {@link CommManager} instance.
		 */
		public CommManager create() {

			// Create a CommManager instance
			return(new CommManager(this._name, this._cacheProvider, this._priorityManagmentProvider, this._retryPolicyProvider));
		}

	}

}
