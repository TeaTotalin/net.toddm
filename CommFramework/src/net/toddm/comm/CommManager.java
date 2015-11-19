
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CachePriority;
import net.toddm.cache.CacheProvider;
import net.toddm.cache.LoggingProvider;
import net.toddm.comm.Priority.StartingPriority;
import net.toddm.comm.Request.RequestMethod;
import net.toddm.comm.CommWork.Status;

/**
 * This is the main work horse of the communications framework. Instances of this class are used to submit work and provide priority queue 
 * management, result caching, failure and response based retry, and much more.
 * <p>
 * @author Todd S. Murchison
 */
public final class CommManager {

	// TODO: Create sample client for Tales services:  https://github.com/Talvish/Tales/tree/master/product
	//
	// TODO: Pluggable response body handling?

	//------------------------------
	// For CommManager access we are going to use a Builder -> Setters -> Create pattern. Instances of CommManager 
	// are created by first creating an instance of CommManager.Builder, calling setters on that Builder instance, 
	// and then calling create() on that Builder instance, which returns a CommManager instance.
	private CommManager(
			String name, 
			CacheProvider cacheProvider, 
			PriorityManagementProvider priorityManagmentProvider, 
			RetryPolicyProvider retryPolicyProvider, 
			ConfigurationProvider configurationProvider, 
			LoggingProvider loggingProvider) 
	{
		this._cacheProvider = cacheProvider;
		this._priorityManagmentProvider = priorityManagmentProvider;
		this._retryPolicyProvider = retryPolicyProvider;
		this._configurationProvider = configurationProvider;
		this._logger = loggingProvider;

		// TODO: Consider if we want to support "config refresh" or real-time config changes. Currently we "snap-shot" some values, like here.
		// Pull configuration values that we will use to operate
		if(this._configurationProvider.contains(DefaultConfigurationProvider.KeyRedirectLimit)) {
			this._redirectLimit = this._configurationProvider.getInt(DefaultConfigurationProvider.KeyRedirectLimit);
		} else {
			this._redirectLimit = DefaultConfigurationProvider.ValueRedirectLimit;
		}
		if(this._configurationProvider.contains(DefaultConfigurationProvider.KeyMaxSimultaneousRequests)) {
			this._maxSimultaneousRequests = this._configurationProvider.getInt(DefaultConfigurationProvider.KeyMaxSimultaneousRequests);
		} else {
			this._maxSimultaneousRequests = DefaultConfigurationProvider.ValueMaxSimultaneousRequests;
		}
		if(this._configurationProvider.contains(DefaultConfigurationProvider.KeyConnectTimeoutMilliseconds)) {
			this._connectTimeoutMilliseconds = this._configurationProvider.getInt(DefaultConfigurationProvider.KeyConnectTimeoutMilliseconds);
		} else {
			this._connectTimeoutMilliseconds = DefaultConfigurationProvider.ValueConnectTimeoutMilliseconds;
		}
		if(this._configurationProvider.contains(DefaultConfigurationProvider.KeyReadTimeoutMilliseconds)) {
			this._readTimeoutMilliseconds = this._configurationProvider.getInt(DefaultConfigurationProvider.KeyReadTimeoutMilliseconds);
		} else {
			this._readTimeoutMilliseconds = DefaultConfigurationProvider.ValueReadTimeoutMilliseconds;
		}
		if(this._configurationProvider.contains(DefaultConfigurationProvider.KeyDisableSSLCertChecking)) {
			this._disableSSLCertChecking = this._configurationProvider.getBoolean(DefaultConfigurationProvider.KeyDisableSSLCertChecking);
		} else {
			this._disableSSLCertChecking = DefaultConfigurationProvider.ValueDisableSSLCertChecking;
		}
		if(this._configurationProvider.contains(DefaultConfigurationProvider.KeyUseBuiltInHttpURLConnectionRedirectionSupport)) {
			this._useBuiltInHttpURLConnectionRedirectionSupport = this._configurationProvider.getBoolean(DefaultConfigurationProvider.KeyUseBuiltInHttpURLConnectionRedirectionSupport);
		} else {
			this._useBuiltInHttpURLConnectionRedirectionSupport = DefaultConfigurationProvider.ValueUseBuiltInHttpURLConnectionRedirectionSupport;
		}

		this._requestWorkExecutorService = Executors.newFixedThreadPool(this._maxSimultaneousRequests);

		this.startWorking(name);
	}
	//------------------------------

	private final int _redirectLimit;
	private final int _maxSimultaneousRequests;
	private final int _connectTimeoutMilliseconds;
	private final int _readTimeoutMilliseconds;
	private final boolean _disableSSLCertChecking;
	private final boolean _useBuiltInHttpURLConnectionRedirectionSupport;

	private final ExecutorService _requestWorkExecutorService;
	private final LinkedList<CommWork> _queuedWork = new LinkedList<CommWork>();
	private final ArrayList<CommWork> _activeWork = new ArrayList<CommWork>();
	private final ArrayList<CommWork> _retryWork= new ArrayList<CommWork>();

	// Members needed for managing the work thread.
	// This is an always-running management thread, so we do not host it in an ExecutorService.
	private Thread _workThread = null;
	private Object _workThreadLock = new Object();
	private volatile boolean _workThreadStopping = false;
	private Object _workManagmentLock = new Object();

	private final CacheProvider _cacheProvider;
	private final PriorityManagementProvider _priorityManagmentProvider;
	private final RetryPolicyProvider _retryPolicyProvider; 
	private final ConfigurationProvider _configurationProvider;
	private final LoggingProvider _logger;

	/**
	 * Enters a request into the communications framework for processing. The {@link Work} instance returned can be used
	 * to wait on the request, manage the request, get results, etc.
	 * <p>
	 * @param uri The URI of the request to work on.
	 * @param method The HTTP method of the request to work on.
	 * @param postData <b>[OPTIONAL]</b> Can be NULL. The POST data of the request to work on.
	 * @param headers <b>[OPTIONAL]</b> Can be NULL. The request headers of the request to work on.
	 * @param requestPriority The priority of this request work relative to other request work.
	 * @param cachingPriority A hint to the caching provider (if there is one) of the relative priority of the cache entry generated for this response.
	 * @param cachingBehavior Indicates what caching behavior should be used for the results of the enqueued work.
	 */
	public Work enqueueWork(
			URI uri, 
			Request.RequestMethod method, 
			byte[] postData, 
			Map<String, String> headers, 
			StartingPriority requestPriority, 
			CachePriority cachingPriority,
			CacheBehavior cachingBehavior) 
	{
		// The CommWork constructor call below will validate all the arguments
		Work resultWork = null;
		CommWork newWork = new CommWork(uri, method, postData, headers, requestPriority, cachingPriority, cachingBehavior, this._logger);
		if(this._logger != null) { this._logger.debug("[thread:%1$d] enqueueWork() start", Thread.currentThread().getId()); }

		// Check cache to see if we already have usable results for this request
		CacheEntry cacheEntry = null;
		Response cachedResponse = null;
		if((!CacheBehavior.DO_NOT_CACHE.equals(cachingBehavior)) && (this._cacheProvider != null)) {

			// If we have a cached response for the request add it to the work, this will allow us to properly handle eTag and 304 response work later
			cacheEntry = this._cacheProvider.get(Integer.toString(newWork.getRequest().getId()), true);
			if(cacheEntry != null) {
				newWork.setCachedResponse(cacheEntry);

				// TODO: CONFIG: Allowing the use of stale cache entries should come from config, maybe allowing request instance to override
				if((!cacheEntry.hasExpired()) || (!cacheEntry.hasExceededStaleUse())) {
					cachedResponse = getResponseFromCacheEntry(cacheEntry);
				}
			}
		}

		synchronized(_workManagmentLock) {

			// Check if the specified work is already being handled by the communications framework
			CommWork existingWork = this.getExistingWork(newWork);

			if(cachedResponse != null) {
				
				// If we have a usable cached result use it
				resultWork = new CachedWork(newWork.getRequest(), cachedResponse, newWork.getRequestPriority(), newWork.getCachingPriority(), newWork.getCachingBehavior());
				if(this._logger != null) { this._logger.info("[thread:%1$d] enqueueWork() Returning cached results [id:%2$d]", Thread.currentThread().getId(), newWork.getId()); }

				if((cacheEntry != null) && (cacheEntry.hasExpired())) {

					// The cached response has expired, but is still in the "stale use" window, so if we are not already working to update the response start new work to update
					if(existingWork == null) {
						this.addNewWork(newWork);
					}
				} else {

					// The cached response has not yet expired, simply update work state accordingly
					newWork.setResponse(cachedResponse);
					newWork.setState(CommWork.Status.COMPLETED);
					newWork.addFutureTask(new CachedResponseFuture(cachedResponse));
				}
			} else {

				if(existingWork != null) {
	
					// TODO: Consider if we want to support updating request priority for already queued work based on clients enqueuing the same request at a different starting priority level.
					// I think we would only want to allow increasing priority to prevent messing with starvation prevention algorithms. For now this is probably a very low priority feature.
	
					// Return existing work that is currently being processed
					if(this._logger != null) { this._logger.info("[thread:%1$d] enqueueWork() Returning already enqueued work [id:%2$d]", Thread.currentThread().getId(), existingWork.getId()); }
					resultWork = existingWork;
				} else if(CacheBehavior.GET_ONLY_FROM_CACHE.equals(cachingBehavior)) {

					// This request can not be serviced, so return a work handle that indicates this
					newWork.setResponse(null);
					newWork.setState(CommWork.Status.COMPLETED);
					newWork.addFutureTask(new NoResponseFuture());
					if(this._logger != null) { this._logger.info("[thread:%1$d] enqueueWork() Returning null results [id:%2$d]", Thread.currentThread().getId(), newWork.getId()); }
					resultWork = newWork;
				} else {

					// This request is not available from cache and is not already being worked on so add it as new work
					resultWork = newWork;
					this.addNewWork(newWork);
				}
			}
		}

		return(resultWork);
	}

	/**
	 * Looks to see if the given work is already being processed by this CommManager instance.
	 * If it is, the existing work instance is returned, otherwise <b>null</b> is returned.
	 */
	private CommWork getExistingWork(CommWork newWork) {
		int index = _queuedWork.indexOf(newWork);
		if(index >= 0) {
			return(_queuedWork.get(index));
		} else {
			index = _activeWork.indexOf(newWork);
			if(index >= 0) {
				return(_activeWork.get(index));
			} else {
				index = _retryWork.indexOf(newWork);
				if(index >= 0) {
					return(_retryWork.get(index));
				}
			}
		}
		return(null);
	}

	/** Adds the given work as new work to be processed by this {@link CommManager} instance. */
	private void addNewWork(CommWork newWork) {

		// Update state for new work
		newWork.addFutureTask(new FutureTask<Response>(new WorkCallable(newWork)));
		addWorkToQueue(newWork, ManagedQueue.QUEUED);
		newWork.setState(CommWork.Status.WAITING);
		if(this._logger != null) { this._logger.info("[thread:%1$d] enqueueWork() Added new work [id:%2$d]", Thread.currentThread().getId(), newWork.getId()); }

		// We've added new work, so kick the worker thread
		_workManagmentLock.notify();
	}

	/**
	 * Deserializes a cached {@link Response} instance from the give {@link CacheEntry} instance and returns it.
	 * If deserialization fails or the cache entry does not contain a response object NULL is returned.
	 */
	private Response getResponseFromCacheEntry(CacheEntry cacheEntry) {

		Response cachedResponse = null;
		if((cacheEntry.getBytesValue() != null) && (cacheEntry.getBytesValue().length > 0)) {
			ByteArrayInputStream inStream = null;
			ObjectInput inObj = null;

			try {
	
				inStream = new ByteArrayInputStream(cacheEntry.getBytesValue());
				inObj = new ObjectInputStream(inStream);
				cachedResponse = (Response)inObj.readObject();
	
			} catch (Exception e) {
				if(this._logger != null) { this._logger.error(e, "Response de-serialization from cache failed"); }
			} finally {
				if(inObj != null) { try { inObj.close(); } catch(Exception e) {} } // No-op an exception OK here
				if(inStream != null) { try { inStream.close(); } catch(Exception e) {} } // No-op an exception OK here
			}
		}
		return(cachedResponse);
	}

	/**
	 * This method <b>does not block</b> and should only be carefully used internally by this class from within suitable critical sections.<br>
	 * Adds the given {@link CommWork} instance to the given queue and ensures that the Work instance is removed from the other queues, as needed.
	 */
	private void addWorkToQueue(CommWork work, ManagedQueue queue) {

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
		if(this._logger != null) { this._logger.debug("[thread:%1$d] startWorking()", Thread.currentThread().getId()); }
		synchronized(_workThreadLock) {
			_workThreadStopping = false;
			if(_workThread == null) {
				_workThread = new Thread(new WorkManagementRunnable(), String.format(Locale.US, "CommManager Work Thread [%1$s]", name));
			}
			if(!_workThread.isAlive()) {
				_workThread.start();
				if(this._logger != null) { this._logger.debug("[thread:%1$d] Thread started", Thread.currentThread().getId()); }
			} else {
				if(this._logger != null) { this._logger.debug("[thread:%1$d] Thread already running", Thread.currentThread().getId()); }
			}
		}
	}

	/** Stops the work thread if it is running. It is safe to make this call multiple times. This is a <strong>blocking</strong> method. */
	@SuppressWarnings("unused")
	private void stopWorking() {
		if(this._logger != null) { this._logger.debug("[thread:%1$d] stopWorking()", Thread.currentThread().getId()); }
		synchronized(_workThreadLock) {

			// Can't stop if we are not running
			if(_workThread == null) {
				if(this._logger != null) { this._logger.debug("[thread:%1$d] Thread already stopped", Thread.currentThread().getId()); }
				return;
			}

			// Tell the thread to exit and then wake it up to do exit work
			_workThreadStopping = true;
			if(this._logger != null) { this._logger.debug("[thread:%1$d] kicking work thread", Thread.currentThread().getId()); }
			synchronized(_workManagmentLock) { _workManagmentLock.notify(); }

			// Attempt to guarantee that the thread ends
			try {
				_workThread.join(2000);
				_workThread.interrupt();
				_workThread.join();
			} catch(InterruptedException e) {
				if(this._logger != null) { this._logger.error("[thread:%1$d] Thread received an interrupt", Thread.currentThread().getId()); }
			} catch(Exception e) {
				if(this._logger != null) { this._logger.error(e, "[thread:%1$d] failed", Thread.currentThread().getId()); }
			} finally {
				_workThread = null;
				if(this._logger != null) { this._logger.debug("[thread:%1$d] Thread stopped", Thread.currentThread().getId()); }
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
		for(CommWork work : this._retryWork) {
			long delta = work.getRetryAfterTimestamp() - now;
			if(delta < retryInterval) { retryInterval = delta; }
		}

		// Enforce some sane limits
		if(retryInterval < 20) { retryInterval = 20; }

		// Do some logging
		if(retryInterval == Long.MAX_VALUE) {
			if(this._logger != null) { this._logger.debug("[thread:%1$d] getNextRetryInterval() returning MAX_VALUE", Thread.currentThread().getId()); }
		} else {
			if(this._logger != null) { this._logger.debug("[thread:%1$d] getNextRetryInterval() returning {} milliseconds", Thread.currentThread().getId(), retryInterval); }
		}

		return(retryInterval);
	}

	//------------------------------------------------------------
	// Private helper classes

	/**
	 * A trust manager that trusts everything. This is for use when enabling HTTPS end-points 
	 * that have bad certificates. This should generally only be used for development and testing.
	 */
	private static final TrustManager[] _TrustAllCertsManagers = new TrustManager[]{
	    new X509TrustManager() {
	        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
	        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
	        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
	    }
	};

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
						if(_logger != null) { 
							_logger.debug(
									"[thread:%1$d] queued:{%2$d} active:{%3$d} retry:{%4$d}", 
									Thread.currentThread().getId(), 
									_queuedWork.size(), 
									_activeWork.size(), 
									_retryWork.size());
						}

						// Check the retry queue to see if we need to move any work from pending retry to the priority queue
						long now = System.currentTimeMillis();
						ArrayList<CommWork> retryNowList = new ArrayList<CommWork>();
						for(CommWork retryWork : _retryWork) {
							if(retryWork.getRetryAfterTimestamp() <= now) {
								retryNowList.add(retryWork);
							}
						}
						for(CommWork retryWork : retryNowList) {
							addWorkToQueue(retryWork, ManagedQueue.QUEUED);
							retryWork.setState(Status.WAITING);
							if(_logger != null) { _logger.debug("[thread:%1$d] Request %2$d moved from retry to queue", Thread.currentThread().getId(), retryWork.getId()); }
						}

						// Check current work to see if we can start more work
						while((_activeWork.size() < _maxSimultaneousRequests) && (_queuedWork.size() > 0)) {

							// Update request priorities as needed using the provided PriorityManagmentProvider implementation
							for(CommWork work : _queuedWork) { CommManager.this._priorityManagmentProvider.promotePriority(work.getRequestPriority()); }

							// Sort the current priority queue using the provided PriorityManagmentProvider implementation and get the next item
							Collections.sort(_queuedWork, CommManager.this._workComparator);

							// Grab the next request, move it to the active queue, and start the work
							CommWork workToStart = _queuedWork.removeFirst();
							addWorkToQueue(workToStart, ManagedQueue.ACTIVE);
							workToStart.setState(CommWork.Status.RUNNING);
							_requestWorkExecutorService.execute(workToStart.getFutureTask());
						}

						// Calculate sleep time based on pending retry work
						sleepTime = CommManager.this.getNextRetryInterval();

						// Sleep until there is more work to do
						if(_logger != null) { 
							_logger.debug("[thread:%1$d] Work thread is waiting to be notified [sleepTime:%2$d]", Thread.currentThread().getId(), sleepTime);
						}
						_workManagmentLock.wait(sleepTime);
						if(_logger != null) { _logger.debug("[thread:%1$d] Work thread is awake", Thread.currentThread().getId()); }
					}

					if(_workThreadStopping) { break;}

				} catch(Exception e) {
					if(_logger != null) { _logger.error(e, "[thread:%1$d] failure", Thread.currentThread().getId()); }
					try { Thread.sleep(5000); } catch(Exception f) { }  // No-op OK
				}
			}
			if(_logger != null) { _logger.debug("[thread:%1$d] Work Thread exited", Thread.currentThread().getId()); }
		}

	}

	/** A simple {@link Comparator} implementation for {@link CommWork} instances that wraps the Comparator provided by the current {@link PriorityManagementProvider}. */
	private Comparator<CommWork> _workComparator = new Comparator<CommWork>() {
		@Override
		public int compare(CommWork lhs, CommWork rhs) {
			if(lhs == null) { throw(new IllegalArgumentException("'lhs' can not be NULL")); }
			if(rhs == null) { throw(new IllegalArgumentException("'rhs' can not be NULL")); }
			return(CommManager.this._priorityManagmentProvider.getPriorityComparator().compare(lhs.getRequestPriority(), rhs.getRequestPriority()));
		}
	};

	/** An implementation of {@link Callable} that makes the actual network request and gets the response */
	private class WorkCallable implements Callable<Response> {

		private final CommWork _work;
		private String _logPrefix = null;

		private WorkCallable(CommWork work) {
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
				if(_logger != null) { _logger.error("%1$s Received a NULL result", this._logPrefix); }
			} else {
				if(_logger != null) {
					_logger.debug("%1$s Response code: %2$d", this._logPrefix, response.getResponseCode());
					if((response.getResponseBody() != null) && (response.getResponseBody().length() > 0)) {
						_logger.debug("%1$s Response body:\r\n%2$s", this._logPrefix, response.getResponseBody());
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
						_logger.debug("%1$s %2$s", this._logPrefix, logMsg.toString());
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

				// Support use of end-points with bad SSL certs via configuration to allow or disallow
				if((urlConnection instanceof HttpsURLConnection) && (CommManager.this._disableSSLCertChecking)) {
					try {
					    SSLContext sslContext = SSLContext.getInstance("SSL");
					    sslContext.init(null, _TrustAllCertsManagers, new java.security.SecureRandom());
						((HttpsURLConnection)urlConnection).setSSLSocketFactory(sslContext.getSocketFactory());
					} catch (Exception e) {
						if(_logger != null) { _logger.error(e, "%1$s Disabling SSL cert checking failed", this._logPrefix); }
					}
				}

				// Configure our connection
				urlConnection.setInstanceFollowRedirects(CommManager.this._useBuiltInHttpURLConnectionRedirectionSupport);
				urlConnection.setConnectTimeout(CommManager.this._connectTimeoutMilliseconds);
				urlConnection.setReadTimeout(CommManager.this._readTimeoutMilliseconds);
				urlConnection.setDoInput(true);
				urlConnection.setRequestMethod(this._work.getRequest().getMethod().name());

				// Add any common request headers. Headers set here will be overridden below if there are duplicates.
				urlConnection.setRequestProperty("Accept-Encoding", "gzip");
				urlConnection.setRequestProperty("Cache-Control", "no-transform");

				// Add request headers if we have any, overriding common headers set above if there are duplicates.
				if(this._work.getRequest().getHeaders() != null) {
					for(String name : this._work.getRequest().getHeaders().keySet()) {
						urlConnection.setRequestProperty(name, this._work.getRequest().getHeaders().get(name));
					}
				}

				// If we have old cache content for this work set the "If-None-Match" header
				CacheEntry cacheEntry = this._work.getCachedResponse();
				if((cacheEntry != null) && (cacheEntry.getEtag() != null) && (cacheEntry.getEtag().length() > 0)) {
					urlConnection.setRequestProperty("If-None-Match", cacheEntry.getEtag());
				}

				// Do some logging
				if(_logger != null) {
					_logger.debug("%1$s Making an HTTP %2$s request to %3$s", this._logPrefix, this._work.getRequest().getMethod().name(), url.toString());
					Map<String, List<String>> requestHeaders = urlConnection.getRequestProperties();
					if(requestHeaders != null) {
						for(String key : requestHeaders.keySet()) {
							StringBuilder logMsg = new StringBuilder(String.format(Locale.US,  "%1$s    Request header '%2$s':", this._logPrefix, key));
							for(String value : requestHeaders.get(key)) {
								logMsg.append(String.format(Locale.US,  " '%1$s'", value));
							}
							_logger.debug(logMsg.toString());
						}
					}
				}

				// Add the POST body if we have one (this must be done before establishing the connection)
				if(	(RequestMethod.POST.equals(this._work.getRequest().getMethod())) && 
					(this._work.getRequest().getPostData() != null) && 
					(this._work.getRequest().getPostData().length > 0) )
				{

					// TODO: CONFIG: Support GZIPing requests based on configuration, size thresholds, etc.
					urlConnection.setDoOutput(true);
					OutputStream outStream = null;
					try {
						outStream = urlConnection.getOutputStream();
						outStream.write(this._work.getRequest().getPostData());
						outStream.flush();
						if(_logger != null) { _logger.debug("%1$s Sending %2$d bytes of POST body data", this._logPrefix, this._work.getRequest().getPostData().length); }
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

				} catch(Exception e) {

					if(_logger != null) { _logger.error(e, this._logPrefix); }

					// Update the work state based on the exception
					this.handleWorkUpdatesOnException(e);

					// We are done (for now at least) with this work
					return(null);
				}

				// Read the response
				try {

					if(urlConnection.getContentLength() > 0) {
						int readCount;
						byte[] data = new byte[512];

						// Support GZIPed responses
						in = urlConnection.getInputStream();
						String contentEncoding = null;
						try {
							contentEncoding = Response.getContentEncoding(urlConnection.getHeaderFields());
						} catch(Exception e) {
							if(_logger != null) { _logger.error(e, "Failed to parse value from 'Content-Encoding' header"); }  // No-op OK
						}
						if ((contentEncoding != null) && (contentEncoding.length() > 0) && (contentEncoding.equalsIgnoreCase("gzip"))) {
						    in = new GZIPInputStream(in);
							if(_logger != null) { _logger.debug("%1$s Received gzipped data", this._logPrefix); }
						}
						else{
							if(_logger != null) { _logger.debug("%1$s Received non-gzipped data", this._logPrefix); }
						}

						while((readCount = in.read(data, 0, data.length)) != -1) {
						  buffer.write(data, 0, readCount);
						}
						buffer.flush();
					}

				} catch(Exception e) {

					// The content length header can lie causing content reading to explode, log and proceed as if there was no content
					if(_logger != null) { _logger.error(e, this._logPrefix); }
				}

				// Construct the response instance
				response = new Response(
						buffer.toByteArray(), 
						urlConnection.getHeaderFields(), 
						urlConnection.getResponseCode(), 
						this._work.getRequest().getId(),
						(int)(System.currentTimeMillis() - start),
						_logger);
				this._work.setResponse(response);
				if(_logger != null) { _logger.debug("%1$s Request finished with a %2$d response code", this._logPrefix, this._work.getResponse().getResponseCode()); }

				// Update the work state based on the response
				this.handleWorkUpdatesOnResponse(response, urlConnection);

			} catch (MalformedURLException e) {
				throw(new CommException(e));
			} catch (IOException e) {
				throw(new CommException(e));
			} finally {

				// Always clean up
		        if(in != null) { try { in.close(); } catch(Exception e) {} }  // Exception suppression is OK here
		        if(urlConnection != null) { try { urlConnection.disconnect(); } catch(Exception e) {} }  // Exception suppression is OK here
				if(_logger != null) { _logger.debug("%1$s Processing took %2$d milliseconds", this._logPrefix, (System.currentTimeMillis() - start)); }
			}

			return(response);
		}

		/** This method updates the current work state based on the given exception.  The work is either queued for retry or marked as completed. */
		private void handleWorkUpdatesOnException(Exception e) {

			// Check for an exception triggered retry
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
		}

		/**
		 * This method updates the current work state based on the given {@link Response}.
		 * This can potentially result in work being queued for the future, cache entries being updated, etc.
		 * <p>
		 * @param response The {@link Response} that resulted from attempting the current work.
		 * @param urlConnection The {@link HttpURLConnection} instance used for the network request.
		 */
		private void handleWorkUpdatesOnResponse(Response response, HttpURLConnection urlConnection) {

			// Check for a response triggered retry or redirect
			RetryProfile retryProfile = CommManager.this._retryPolicyProvider.shouldRetry(this._work.getRequest(), response);
			if(retryProfile.shouldRetry()) {

				// ************ RETRY ************
				// This work should be retried, make it so...
				this._work.updateRetryAfterTimestamp(retryProfile.getRetryAfterMilliseconds());
				this._work.getRequest().incrementRetryCountFromResponse();
				this._work.setState(Status.RETRYING);

				// Update the work for retry and kick the worker thread so it wakes up to recalculate it's sleep time
				this._work.addFutureTask(new FutureTask<Response>(new WorkCallable(this._work)));  // Add the future task for the retry work
				synchronized(_workManagmentLock) {
					addWorkToQueue(this._work, ManagedQueue.RETRY);
					_workManagmentLock.notify();
				}

			} else if(	((response.getResponseCode() == 301) || (response.getResponseCode() == 302) || (response.getResponseCode() == 303)) && 
						(this._work.getRequest().getRedirectCount() < CommManager.this._redirectLimit) && 
						(!urlConnection.getInstanceFollowRedirects()) )
			{

				// ************ REDIRECT ************
				// HttpURLConnection is not handling redirects for us, so support "3xx Location" response triggered redirecting
				URI targetUri = response.getLocationFromHeaders(this._work.getRequest());
				if(_logger != null) { _logger.debug("%1$s Redirecting from %2$s to %3$s", this._logPrefix, this._work.getRequest().getUri().toString(), targetUri.toString()); }
				this._work.getRequest().redirect(targetUri);  // This call increments the request redirect count

				// We process redirects through the retry queue (to be retried immediately)
				this._work.updateRetryAfterTimestamp(0);
				this._work.setState(Status.REDIRECTING);
				this._work.addFutureTask(new FutureTask<Response>(new WorkCallable(this._work)));  // Add the future task for the redirect work
				synchronized(_workManagmentLock) {
					addWorkToQueue(this._work, ManagedQueue.RETRY);
					_workManagmentLock.notify();
				}

			} else if(	(this._work.shouldCache()) && 
						(this._work.getCachedResponse() != null) && 
						(CommManager.this._cacheProvider != null) && 
						(response.getResponseCode() == 304)) 
			{

				// ************ CACHE STILL VALID ************
				// Handle 304 "Not Modified" caching related responses (refreshes timestamps on cache entry)
				CacheEntry cacheEntry = this._work.getCachedResponse();

				// Check for a new TTL value from the response
				Long ttl = response.getTtlFromHeaders();
				if(ttl == null) { ttl = cacheEntry.getTtl(); }

				// Check for a new ETag value from the response
				String eTag = response.getETagFromHeaders();
				if((eTag == null) || (eTag.length() <= 0)) { eTag = cacheEntry.getEtag(); }

				// Check for a new "max-stale" value from the response
				Long maxStale = response.getMaxStaleFromHeaders();
				if(maxStale == null) { maxStale = cacheEntry.getMaxStale(); }

				// Update the cache entry. We will update using the cache entry instance that we saved on the 
				// Work object. The entry in the cache may have already been removed by LRU enforcement, etc.
				CommManager.this._cacheProvider.remove(cacheEntry.getKey());
				CommManager.this._cacheProvider.add(cacheEntry.getKey(), cacheEntry.getBytesValue(), ttl, maxStale, eTag, cacheEntry.getUri(), cacheEntry.getPriority());

				// Add cached response to work
				Response cachedResponse = getResponseFromCacheEntry(cacheEntry);
				this._work.setResponse(cachedResponse);
				this._work.addFutureTask(new CachedResponseFuture(cachedResponse));
				this._work.setState(Status.COMPLETED);
				if(_logger != null) { _logger.info("[thread:%1$d] handleWorkUpdatesOnResponse() Returning cached results post 304 [id:%2$d]", Thread.currentThread().getId(), this._work.getId()); }

			} else if((this._work.shouldCache()) && (CommManager.this._cacheProvider != null) && (response.isSuccessful()) && (!response.shouldNotCacheDueToHeaderDirective())) {

				// ************ CACHE ADD ************
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
						if(ttl == null) { ttl = Long.MAX_VALUE; }  // NULL and Long.MAX_VALUE both indicate never expiring

						// Check the response headers for an ETag value
						String eTag = response.getETagFromHeaders();
						
						// Check the response headers for a "max-stale" value
						Long maxStale = response.getMaxStaleFromHeaders();
						if(maxStale == null) { maxStale = 0L; }  // NULL and zero both indicate never use expired

						CommManager.this._cacheProvider.add(
								Integer.toString(this._work.getRequest().getId()), 
								responseObjBytes, 
								ttl, 
								maxStale, 
								eTag, 
								this._work.getRequest().getUri(),
								this._work.getCachingPriority());
						if(_logger != null) { _logger.debug("%1$s Response for request %2$d added to cache", this._logPrefix, this._work.getRequest().getId()); }

						// We will go ahead and enforce LRU here as we may have just added a new cache entry
						CommManager.this._cacheProvider.trimLru();
					}
				} catch(Exception e) {
					if(_logger != null) { _logger.error(e, "Response serialization to cache failed"); }
				} finally {
					if(out != null) { try { out.close(); } catch(Exception e) {} } // No-op on exception OK here
					if(outStream != null) { try { outStream.close(); } catch(Exception e) {} } // No-op on exception OK here
				}
				this._work.setState(Status.COMPLETED);

			} else {

				// No additional work needed, just update as done
				this._work.setState(Status.COMPLETED);
			}
		}

		private void cleanup() {

			// Clear the finished work out of the managed queues
			synchronized(_workManagmentLock) {

				if(_logger != null) { _logger.debug("%1$s Work completed, doing cleanup [state:%2$s]", this._logPrefix, this._work.getState()); }

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
							if(_logger != null) { _logger.error("%1$s Finished work has been removed from _queuedWork", this._logPrefix); }
						}
						if(_activeWork.remove(this._work)) {
							if(_logger != null) { _logger.debug("%1$s Finished work has been removed from _activeWork", this._logPrefix); }
						}
						if(_retryWork.remove(this._work)) {
							if(_logger != null) { _logger.error("%1$s Finished work has been removed from _retryWork", this._logPrefix); }
						}

						// Kick the work thread to check for new work (a spot just opened up!)
						if(_logger != null) { _logger.debug("%1$s kicking work thread", this._logPrefix); }
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
		private PriorityManagementProvider _priorityManagementProvider = null;
		private RetryPolicyProvider _retryPolicyProvider = null;
		private ConfigurationProvider _configurationProvider = null;
		private LoggingProvider _loggingProvider = null;

		public Builder() {

			// Any sane state-less defaults for pluggable sub-systems like configuration provider, etc. can be set here.
			this._configurationProvider = new DefaultConfigurationProvider();
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
		 * Sets the {@link PriorityManagementProvider} instance used by the comm framework for work priority queue management.
		 * The priority provider set here will be used by the {@link CommManager} instances subsequently created via {@link Builder#create()}.
		 * If not set then {@link DefaultPriorityManagmentProvider} is used, which provides a simple age based anti queue starvation implementation.
		 */
		public Builder setPriorityManagmentProvider(PriorityManagementProvider priorityManagmentProvider) {
			if(priorityManagmentProvider == null) { throw(new IllegalArgumentException("'priorityManagmentProvider' can not be NULL")); }
			this._priorityManagementProvider = priorityManagmentProvider;
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
		 * Sets the {@link ConfigurationProvider} instance used by the comm framework for getting configuration data.
		 * The default is <b>null</b> (resulting in default values being used everywhere). The configuration provider set here will be used by the 
		 * {@link CommManager} instances subsequently created via {@link Builder#create()}.
		 */
		public Builder setConfigurationProvider(ConfigurationProvider configurationProvider) {
			if(configurationProvider == null) {
				this._configurationProvider = new DefaultConfigurationProvider();
			} else {
				this._configurationProvider = configurationProvider;
			}
			return(this);
		}

		/**
		 * Sets the {@link LoggingProvider} instance used by the comm framework for logging.
		 * The default is <b>null</b> (no logging). The logging provider set here will be used by the 
		 * {@link CommManager} instances subsequently created via {@link Builder#create()}.
		 */
		public Builder setLoggingProvider(LoggingProvider loggingProvider) {
			// Setting logging provider to NULL is OK and results in no logging
			this._loggingProvider = loggingProvider;
			return(this);
		}

		/**
		 * Creates a new {@link CommManager} instance based on the values currently configured on this {@link Builder} instance.
		 * @return A {@link CommManager} instance.
		 */
		public CommManager create() {

			// Any sane defaults for pluggable sub-systems like priority management provider, 
			// etc. that relay on state set by the Builder setters can be set here.
			if(this._priorityManagementProvider == null) {
				this._priorityManagementProvider = new DefaultPriorityManagmentProvider(this._loggingProvider);
			}
			if(this._retryPolicyProvider == null) {
				this._retryPolicyProvider = new DefaultRetryPolicyProvider(this._loggingProvider);
			}

			// Create a CommManager instance
			return(new CommManager(
					this._name, 
					this._cacheProvider, 
					this._priorityManagementProvider, 
					this._retryPolicyProvider, 
					this._configurationProvider, 
					this._loggingProvider));
		}

	}

}
