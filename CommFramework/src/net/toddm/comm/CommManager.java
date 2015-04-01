
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
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import net.toddm.cache.CacheEntry;
import net.toddm.cache.CacheProvider;
import net.toddm.comm.Request.RequestMethod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Todd S. Murchison
 */
public final class CommManager {

	// TODO: failure policies / off-line modes
	//			- should cover graceful recovery from service outage, etc.
	//
	// TODO: priority queue and cache should be pluggable implementations
	//
	// Uses SLF4J. To use in Android include SLF4J Android (http://www.slf4j.org/android/) in your Android project.
	//
	// TODO: https://github.com/Talvish/Tales/tree/master/product
	//
	// TODO: pluggable response body handling?
	//
	// TODO: GZIP support
	//
	// TODO: Handle 304 "Not Modified" caching related responses (should refresh timestamps on cache entry)

	private static final Logger _Logger = LoggerFactory.getLogger(CommManager.class.getSimpleName());

	//------------------------------
	// For CommManager access we are going to use a Builder -> Setters -> Create pattern. Instances of CommManager 
	// are created by first creating an instance of CommManager.Builder, calling setters on that Builder instance, 
	// and then calling create() on that Builder instance, which returns a CommManager instance.
	private CommManager(String name, CacheProvider cacheProvider) {
		this._cacheProvider = cacheProvider;
		this.startWorking(name);
	}
	//------------------------------

	// TODO: Get configuration values from a config system
	private final int _maxSimultaneousRequests = 2;
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
			boolean cachingAllowed) 
	{
		// This constructor will validate all the arguments
		Work newWork = new Work(uri, method, postData, headers, cachingAllowed);
		Work resultWork = null;
		_Logger.info("[thread:{}] enqueueWork() start", Thread.currentThread().getId());

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
					newWork.setFutureTask(new CachedResponseFuture(cachedResponse));
					_Logger.info("[thread:{}] enqueueWork() Returning cached results [id:{}]", Thread.currentThread().getId(), newWork.getId());
					resultWork = newWork;
				} else {

					// This request is not available from cache and is not already being 
					// managed by this CommManager instance so add it as new work
					newWork.setFutureTask(new FutureTask<Response>(new WorkCallable(newWork)));
					_queuedWork.add(newWork);
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

	/** Starts the work thread if it is not already running. It is safe to make this call multiple times. */
	private void startWorking(String name) {
		_Logger.info("[thread:{}] startWorking()", Thread.currentThread().getId());
		synchronized(_workThreadLock) {
			_workThreadStopping = false;
			if(_workThread == null) {
				_workThread = new Thread(new WorkManagementRunnable(), String.format(Locale.US, "CommManager Work Thread [%1$s]", name));
			}
			if(!_workThread.isAlive()) {
				_workThread.start();
				_Logger.info("[thread:{}] Thread started", Thread.currentThread().getId());
			} else {
				_Logger.debug("[thread:{}] Thread already running", Thread.currentThread().getId());
			}
		}
	}

	/** Stops the work thread if it is running. It is safe to make this call multiple times. This is a <strong>blocking</strong> method. */
	@SuppressWarnings("unused")
	private void stopWorking() {
		_Logger.info("[thread:{}] stopWorking()", Thread.currentThread().getId());
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
				_Logger.info("[thread:{}] Thread stopped", Thread.currentThread().getId());
			}
		}
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

						// TODO: Implement retry and retry management

						// Check current work to see if can start more work
						while((_activeWork.size() < _maxSimultaneousRequests) && (_queuedWork.size() > 0)) {
							
							// TODO: Deal with request priority. We'd like a pluggable approach to prioritizing 
							// TODO: the queue, but what about guarding against starvation based on age, etc.?
							
							// Grab the next request, move it to the active queue, and start the work
							Work workToStart = _queuedWork.removeFirst();
							_activeWork.add(workToStart);
							workToStart.setState(Work.Status.RUNNING);
							_requestWorkExecutorService.execute(workToStart.getFutureTask());
						}

						// TODO: Other work (result cache management such as LRU?, etc.)

						// TODO: Calculate sleep time based on pending work

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
			_Logger.info("[thread:{}] Work Thread exited", Thread.currentThread().getId());
		}

	}

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

				//**************************************** 
				// Establish the connection. Any configuration that must be done prior to connecting must go above.
				urlConnection.connect();
				//**************************************** 

				// Read the response
				int readCount;
				byte[] data = new byte[512];
				in = urlConnection.getInputStream();
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				while((readCount = in.read(data, 0, data.length)) != -1) {
				  buffer.write(data, 0, readCount);
				}
				buffer.flush();

				// Construct the response instance
				response = new Response(
						buffer.toByteArray(), 
						urlConnection.getHeaderFields(),  // TODO: Serialization of the Map type returned here fails under Android
						urlConnection.getResponseCode(), 
						this._work.getRequest().getId(),
						(int)(System.currentTimeMillis() - start));
				this._work.setResponse(response);
				_Logger.debug("{} Request finished with a {} response code", this._logPrefix, this._work.getResponse().getResponseCode());

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
							Long ttl = ResponseCachingUtility.getTtlFromResponse(response);
							if(ttl == null) { ttl = Long.MAX_VALUE; }  // Long.MAX_VALUE indicates never expiring

							// Check the response headers for an ETag value
							String eTag = ResponseCachingUtility.getETagFromResponse(response);

							CommManager.this._cacheProvider.add(
									Integer.toString(this._work.getRequest().getId()), 
									responseObjBytes, 
									ttl, 
									eTag, 
									this._work.getRequest().getUri());
							_Logger.info("{} Response for request {} added to cache", this._logPrefix, this._work.getRequest().getId());
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

				// TODO: Update the work state based on result of the work?
				this._work.setState(Work.Status.COMPLETED);

				if(_activeWork.remove(this._work)) {
					_Logger.info("{} Finished work has been removed from _ActiveWork", this._logPrefix);
				} else {
					_Logger.info("{} Finished work was not found in _ActiveWork", this._logPrefix);
				}

				if(!Work.Status.RETRYING.equals(this._work.getState())) {
					if(_queuedWork.remove(this._work)) {
						_Logger.error("{} Found finished work in _QueuedWork", this._logPrefix);
					}
					if(_retryWork.remove(this._work)) {
						_Logger.error("{} Found finished work in _RetryWork", this._logPrefix);
					}
				}

				// Kick the work thread to check for new work (a spot just opened up!)
				_Logger.trace("{} kicking work thread", this._logPrefix);
				_workManagmentLock.notify();
			}
		}

	};

	/** This is a factory class used for configuring and then creating instances of {@link CommManager}. */
	public static final class Builder {
		
		private String _name = "default";
		private CacheProvider _cacheProvider = null;

		public Builder() {

			// TODO: This is where we configure any sane defaults for pluggable sub-systems like caching provider, 
			// TODO: request priority, failure policies, configuration provider, response body handling, etc.
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
			this._cacheProvider = cacheProvider;
			return(this);
		}

		/**
		 * Creates a new {@link CommManager} instance based on the values currently configured on this {@link Builder} instance.
		 * @return A {@link CommManager} instance.
		 */
		public CommManager create() {

			// Create a CommManager instance
			return(new CommManager(this._name, this._cacheProvider));
		}

	}

}
